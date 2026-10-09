package org.akshara.ime.ime

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.InlineSuggestion
import android.widget.*
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.akshara.ime.BuildConfig
import org.akshara.ime.data.ClipboardHistoryStore
import org.akshara.ime.data.EmojiRepository
import org.akshara.ime.engine.InputMode
import org.akshara.ime.engine.SinhalaEngine
import org.akshara.ime.settings.EmojiButtonPlacement
import org.akshara.ime.settings.KeyboardPreferences
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

enum class KeyboardLayer { LETTERS, NUMBERS, SYMBOLS, EMOJI, CLIPBOARD }
enum class EditorLayout { TEXT, ASCII, EMAIL, URI, NUMBER, SIGNED_NUMBER, DECIMAL, SIGNED_DECIMAL, PHONE, DATETIME }

interface KeyboardActions {
    fun onCharacter(value: String)
    fun onBackspace(word: Boolean = false)
    fun onSpace()
    fun onSpaceLongPress() {}
    fun onLanguageSwitch() {}
    fun onEnter()
    fun onCandidate(value: String)
    fun onGlobe()
    fun onModeRequested(mode: InputMode)
    fun onHide()
    fun onCursorDelta(delta: Int)
    fun onSettings() {}
    fun onClipboardOpen() {}
    fun onClipboardPreviewPaste() {}
    fun onEmojiPicked(value: String) = onCharacter(value)
    fun onPasteText(value: String) = onCharacter(value)
    fun onPressFeedback() {}
    fun languageScoreForKey(output: String): Float = 0f
    fun onPreviewDelete(clusters: Int) {}
    fun onCommitPreviewDelete() {}
    fun onCancelPreviewDelete() {}
    fun onSpaceSwipe(up: Boolean) {}
}

@SuppressLint("ViewConstructor")
class KeyboardView(
    context: Context,
    private val actions: KeyboardActions,
    private val prefs: KeyboardPreferences
) : LinearLayout(context) {
    private var mode = prefs.mode
    private var layer = KeyboardLayer.LETTERS
    private val shiftLatch = ShiftLatch()
    private var autoShift = false
    private var suppressAutoShift = false
    private val shifted get() = shiftLatch.shifted || shiftLatch.capsLock || (autoShift && !suppressAutoShift)
    private val capsLock get() = shiftLatch.capsLock
    private var enterLabel = "↵"
    private var editorLayout = EditorLayout.TEXT
    private var offerGlobe = false
    private var animateSpaceLabel = false
    private var candidates = emptyList<String>()
    private var emojiCandidates = emptyList<String>()
    private var clipboardRecent = emptyList<String>()
    private var clipboardPinned = emptyList<String>()
    private var clipboardHistoryEnabled = prefs.clipboardHistory
    private var clipboardPreviewLabel: String? = null
    private var clipboardPreviewIsImage = false
    private var recentEmoji = emptyList<String>()
    private val emojiRepo = EmojiRepository(context)
    private val clipboardStore = ClipboardHistoryStore(context)
    private var emojiSearch = false
    private val searchQuery = EmojiSearchQuery()
    private val emojiQuery get() = searchQuery.text
    private var searchShift = false
    private var searchLayer = KeyboardLayer.LETTERS
    private var searchPanel: KeyboardPanel? = null
    private var searchLabel: TextView? = null
    private var searchClear: View? = null
    private var searchResults: androidx.recyclerview.widget.RecyclerView? = null
    private var searchEmpty: View? = null
    private var searchGeneration = 0
    private var searchFuture: java.util.concurrent.Future<*>? = null
    private var searchExecutor: java.util.concurrent.ScheduledExecutorService? = null
    private var emojiCategoryIndex = 0
    private var emojiCatalog: EmojiCatalogView? = null
    private val emojiTabs = mutableListOf<ImageButton>()
    private var englishOneWord = false
    private var persistentEnglish = false
    private var clipboardExtraPx = 0
    private var clipboardDragActive = false
    private var clipboardDragTracking = false
    private var clipboardDragStartRawY = 0f
    private var clipboardDragStartExtra = 0
    private var clipboardVelocity: VelocityTracker? = null
    private val clipboardHandlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val handler = Handler(Looper.getMainLooper())
    private val theme = KeyboardThemes.resolve(context, prefs.theme, prefs.highContrast)
    private val bg = theme.background
    private val ink = theme.ink
    private val rail = SuggestionRail(
        context,
        ink,
        { acceptCandidate(it) },
        {
            actions.onClipboardOpen()
            leaveClipboardOrToggle()
        },
        {
            layer = KeyboardLayer.EMOJI
            render()
        },
        { actions.onClipboardPreviewPaste() },
        { actions.onEmojiPicked(it) },
        { actions.onSettings() }
    )
    private val railHost = FrameLayout(context).apply {
        clipChildren = false
        clipToPadding = false
    }
    private val inlineAutofill = HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        visibility = GONE
    }
    private val inlineRow = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        setPadding(dp(8), 0, dp(8), 0)
    }
    private val inlinePinned = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, 0, dp(8), 0)
        visibility = GONE
    }
    private var inlineAutofillGeneration = 0
    private val clipboardHandle = View(context).apply {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        isClickable = true
        isFocusable = true
        contentDescription = "Expand clipboard"
        setOnClickListener { toggleClipboardExpanded() }
    }
    private val body = LinearLayout(context)
    private val homePad = View(context)
    private val popups = KeyPopups(context)
    private val panel = KeyboardPanel(
        context, prefs, popups, object : KeyboardActions {
            override fun onCharacter(value: String) {
                val hadAutoShift = autoShift
                autoShift = false
                suppressAutoShift = false
                if (value in listOf("😀", "😂", "❤️", "👍", "🙏", "🔥", "✨", "🎉", "🇱🇰", "😊")) actions.onEmojiPicked(value)
                else actions.onCharacter(value)
                if (shiftLatch.shifted && !shiftLatch.capsLock) {
                    shiftLatch.consumeOneShot()
                    bindTyping()
                }
                if (persistentEnglish && hadAutoShift) bindTyping()
            }
            override fun onBackspace(word: Boolean) = actions.onBackspace(word)
            override fun onSpace() {
                val returnToLetters = layer == KeyboardLayer.NUMBERS || layer == KeyboardLayer.SYMBOLS
                actions.onSpace()
                if (returnToLetters) {
                    layer = KeyboardLayer.LETTERS
                    render()
                }
            }
            override fun onSpaceLongPress() = actions.onSpaceLongPress()
            override fun onLanguageSwitch() = actions.onLanguageSwitch()
            override fun onSpaceSwipe(up: Boolean) {
                if (layer == KeyboardLayer.LETTERS) actions.onSpaceSwipe(up)
            }
            override fun onEnter() = actions.onEnter()
            override fun onCandidate(value: String) = acceptCandidate(value)
            override fun onGlobe() = actions.onGlobe()
            override fun onModeRequested(mode: InputMode) = actions.onModeRequested(mode)
            override fun onHide() = actions.onHide()
            override fun onCursorDelta(delta: Int) = actions.onCursorDelta(delta)
            override fun onSettings() = actions.onSettings()
            override fun onClipboardPreviewPaste() = actions.onClipboardPreviewPaste()
            override fun onEmojiPicked(value: String) = actions.onEmojiPicked(value)
            override fun onPressFeedback() = actions.onPressFeedback()
            override fun languageScoreForKey(output: String) = actions.languageScoreForKey(output)
            override fun onPreviewDelete(clusters: Int) = actions.onPreviewDelete(clusters)
            override fun onCommitPreviewDelete() = actions.onCommitPreviewDelete()
            override fun onCancelPreviewDelete() = actions.onCancelPreviewDelete()
        },
        theme, prefs.keyHints,
        onLayer = { next -> layer = next; render() },
        onShift = { updateShift() }
    )
    private var sliverPanel: KeyboardPanel? = null
    var learningEnabled = true
        set(value) {
            field = value
            panel.learningEnabled = value && editorLayout == EditorLayout.TEXT
        }

    init {
        orientation = VERTICAL; setBackgroundColor(bg)
        blockForceDark()
        clipChildren = false
        clipToPadding = false
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val types = WindowInsetsCompat.Type.navigationBars() or
                WindowInsetsCompat.Type.mandatorySystemGestures() or
                WindowInsetsCompat.Type.tappableElement()
            val system = insets.getInsetsIgnoringVisibility(types).bottom
            val resource = navigationBarFallback()
            val bottom = maxOf(system + dp(2), resource + dp(2), dp(KeyboardGeometry.BOTTOM_PAD_DP))
                .coerceAtMost(dp(64))
            val params = homePad.layoutParams as LayoutParams
            if (params.height != bottom) {
                params.height = bottom
                homePad.layoutParams = params
            }
            insets
        }
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        setPadding(0, dp(KeyboardGeometry.TOP_PAD_DP), 0, 0)
        rail.keySliver = suggestionKeySliver()
        railHost.addView(rail, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        inlineAutofill.addView(inlineRow, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
        railHost.addView(inlineAutofill, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        railHost.addView(inlinePinned, FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT, Gravity.END))
        addView(railHost, LayoutParams(LayoutParams.MATCH_PARENT, suggestionRailHeight()))
        body.orientation = VERTICAL
        body.clipChildren = true
        body.clipToPadding = true
        addView(body, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(homePad, LayoutParams(LayoutParams.MATCH_PARENT, dp(KeyboardGeometry.BOTTOM_PAD_DP)))
        addView(clipboardHandle, LayoutParams(LayoutParams.MATCH_PARENT, 0))
        clipboardHandlePaint.color = ColorUtils.setAlphaComponent(ink, 90)
        clipboardHandlePaint.style = Paint.Style.FILL
        updateClipboardHandle()
        render()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ViewCompat.requestApplyInsets(this)
    }

    override fun onDetachedFromWindow() {
        popups.dismiss()
        cancelEmojiSearch()
        searchExecutor?.shutdownNow()
        searchExecutor = null
        handler.removeCallbacksAndMessages(null)
        sliverPanel = null
        releaseClipboardVelocity()
        super.onDetachedFromWindow()
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        layoutClipboardHandle()
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (layer != KeyboardLayer.CLIPBOARD) return
        val widthPx = dp(KeyboardGeometry.CLIPBOARD_HANDLE_WIDTH_DP).toFloat()
        val heightPx = dp(KeyboardGeometry.CLIPBOARD_HANDLE_HEIGHT_DP).toFloat()
        val left = (width - widthPx) / 2f
        val top = (paddingTop - heightPx) / 2f
        val radius = heightPx / 2f
        canvas.drawRoundRect(left, top, left + widthPx, top + heightPx, radius, radius, clipboardHandlePaint)
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (layer != KeyboardLayer.CLIPBOARD) return super.onInterceptTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (inClipboardHandleZone(event.x, event.y)) {
                    clipboardDragTracking = true
                    clipboardDragActive = false
                    clipboardDragStartRawY = event.rawY
                    clipboardDragStartExtra = clipboardExtraPx
                    obtainClipboardVelocity().addMovement(event)
                    return false
                }
                clipboardDragTracking = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (clipboardDragTracking && !clipboardDragActive) {
                    obtainClipboardVelocity().addMovement(event)
                    val dy = abs(event.rawY - clipboardDragStartRawY)
                    if (dy >= dp(KeyboardGeometry.CLIPBOARD_DRAG_SLOP_DP)) {
                        clipboardDragActive = true
                        parent?.requestDisallowInterceptTouchEvent(true)
                        return true
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (clipboardDragTracking && !clipboardDragActive) {
                    clipboardDragTracking = false
                    releaseClipboardVelocity()
                }
            }
        }
        return clipboardDragActive || super.onInterceptTouchEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (layer != KeyboardLayer.CLIPBOARD || (!clipboardDragTracking && !clipboardDragActive)) {
            return super.onTouchEvent(event)
        }
        obtainClipboardVelocity().addMovement(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                if (!clipboardDragActive) {
                    val dy = abs(event.rawY - clipboardDragStartRawY)
                    if (dy >= dp(KeyboardGeometry.CLIPBOARD_DRAG_SLOP_DP)) {
                        clipboardDragActive = true
                        parent?.requestDisallowInterceptTouchEvent(true)
                    } else {
                        return true
                    }
                }
                val delta = (clipboardDragStartRawY - event.rawY).roundToInt()
                setClipboardExtra(clipboardDragStartExtra + delta)
                return true
            }
            MotionEvent.ACTION_UP -> {
                val moved = abs(event.rawY - clipboardDragStartRawY)
                if (!clipboardDragActive && moved < dp(KeyboardGeometry.CLIPBOARD_DRAG_SLOP_DP)) {
                    toggleClipboardExpanded()
                } else {
                    snapClipboardExpanded(event)
                }
                endClipboardDrag()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                snapClipboardExpanded(event)
                endClipboardDrag()
                return true
            }
        }
        return true
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (usesTypingPanel() && inSuggestionSliver(event.y)) {
                    sliverPanel = panel
                    return dispatchToPanel(event)
                }
            }
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (sliverPanel != null) {
                    val handled = dispatchToPanel(event)
                    if (event.actionMasked != MotionEvent.ACTION_MOVE) sliverPanel = null
                    return handled
                }
            }
        }
        return super.dispatchTouchEvent(event)
    }

    fun configure(
        mode: InputMode,
        offerGlobe: Boolean,
        enter: String,
        editor: EditorLayout = EditorLayout.TEXT,
        playSpaceIntro: Boolean = false,
        english: Boolean = false
    ) {
        this.mode = mode; enterLabel = enter; editorLayout = editor; this.offerGlobe = offerGlobe
        clipboardHistoryEnabled = KeyboardPreferences(context).clipboardHistory
        shiftLatch.reset(); layer = KeyboardLayer.LETTERS
        autoShift = false
        suppressAutoShift = false
        clipboardExtraPx = 0
        endClipboardDrag()
        englishOneWord = false
        persistentEnglish = english
        animateSpaceLabel = playSpaceIntro
        val width = if (prefs.oneHanded == "center") LayoutParams.MATCH_PARENT else (resources.displayMetrics.widthPixels * .82f).toInt()
        (body.layoutParams as LayoutParams).apply { this.width = width; gravity = when (prefs.oneHanded) { "left" -> Gravity.START; "right" -> Gravity.END; else -> Gravity.CENTER } }
        panel.learningEnabled = learningEnabled && editor == EditorLayout.TEXT
        updateClipboardHandle()
        render()
    }
    fun setCandidates(values: List<String>, emoji: List<String> = emptyList()) {
        candidates = values.take(3)
        emojiCandidates = emoji.filter { it.isNotBlank() }.distinct().take(2)
        bindRail(true)
    }
    fun setInlineAutofillSuggestions(suggestions: List<InlineSuggestion>) {
        val generation = ++inlineAutofillGeneration
        inlineRow.removeAllViews()
        inlinePinned.removeAllViews()
        inlineAutofill.visibility = GONE
        inlinePinned.visibility = GONE
        updateRailHeight()
        bindRail(false)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || suggestions.isEmpty()) {
            return
        }
        val visible = suggestions.take(3)
        val pinned = visible.filter { it.info.isPinned }
        val regular = visible.filterNot { it.info.isPinned }
        val pinnedSpace = if (pinned.isEmpty()) 0 else pinned.size * dp(56) + dp(8)
        (inlineAutofill.layoutParams as FrameLayout.LayoutParams).apply {
            marginEnd = pinnedSpace
            inlineAutofill.layoutParams = this
        }
        val regularSpace = resources.displayMetrics.widthPixels - pinnedSpace
        inlineRow.minimumWidth = if (regular.size == 1) regularSpace else 0
        val regularWidth = minOf(dp(200), regularSpace - dp(16)).coerceAtLeast(dp(48))
        val height = suggestionRailHeight()
        val executor = java.util.concurrent.Executor { handler.post(it) }
        fun inflateInto(suggestion: InlineSuggestion, row: LinearLayout, width: Int, isPinned: Boolean) {
            val size = android.util.Size(width, height)
            // Inflation is asynchronous, so reserve each suggestion's place before callbacks arrive.
            val slot = FrameLayout(context)
            row.addView(slot, LinearLayout.LayoutParams(width, height).apply {
                if (isPinned) marginStart = dp(8) else marginEnd = dp(8)
            })
            suggestion.inflate(context, size, executor) { view ->
                if (generation != inlineAutofillGeneration || view == null) return@inflate
                // The inflated surface reports no intrinsic width on some Android builds.
                slot.addView(view, FrameLayout.LayoutParams(width, height))
                if (isPinned) inlinePinned.visibility = VISIBLE else inlineAutofill.visibility = VISIBLE
                updateRailHeight()
                bindRail(false)
            }
        }
        regular.forEach { inflateInto(it, inlineRow, regularWidth, false) }
        pinned.forEach { inflateInto(it, inlinePinned, dp(48), true) }
    }
    fun setClipboardItems(recent: List<String>, pinned: List<String> = emptyList()) {
        clipboardRecent = recent
        clipboardPinned = pinned
        rail.setClipboardVisible(showClipboardButton())
        if (layer == KeyboardLayer.CLIPBOARD) render()
    }
    fun setRecentEmoji(values: List<String>) {
        recentEmoji = values
        // Refresh recents on the next opening, without moving the grid under the user’s finger.
    }
    fun setClipboardPreview(label: String?, image: Boolean = false) {
        clipboardPreviewLabel = label
        clipboardPreviewIsImage = image
        rail.setClipboardPreview(label, image)
    }

    internal fun keyNameAt(x: Float, y: Float): String? {
        if (!usesTypingPanel() || panel.parent !== body) return null
        return panel.keyAt(x - body.left - panel.left, y - body.top - panel.top)?.id
    }

    internal fun typingLayout(): KeyboardLayout? = if (usesTypingPanel()) panel.layout else null

    private fun render() {
        cancelEmojiSearch()
        searchPanel = null
        popups.dismiss()
        sliverPanel = null
        if (layer != KeyboardLayer.CLIPBOARD) clipboardExtraPx = 0
        updateRailHeight()
        bindRail(false)
        updateClipboardHandle()
        if (editorLayout in numericEditors) {
            body.removeAllViews()
            renderNativePad()
            return
        }
        when (layer) {
            KeyboardLayer.LETTERS, KeyboardLayer.NUMBERS, KeyboardLayer.SYMBOLS -> bindTyping()
            KeyboardLayer.EMOJI -> { body.removeAllViews(); renderEmoji() }
            KeyboardLayer.CLIPBOARD -> bindClipboard()
        }
    }

    private fun bindTyping() {
        if (body.childCount != 1 || body.getChildAt(0) !== panel) {
            body.removeAllViews()
            body.addView(panel, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }
        val spaceLabel = spaceCaption()
        val rows = KeyboardLayoutFactory.typingRows(
            mode, layer, shifted, capsLock, editorLayout,
            prefs.topRow, prefs.emojiButtonPlacement == EmojiButtonPlacement.KEYBOARD, enterLabel, spaceLabel, false, languageSwitchLabel(), prefs.spacePunctuationKeys,
            englishOneWord || persistentEnglish
        )
        val rowHeight = KeyboardGeometry.rowHeightPx(prefs.keyboardSize, isLandscape(), resources.displayMetrics.density, rows.size)
        panel.debug = BuildConfig.DEBUG && prefs.debugOverlay
        panel.playSpaceIntro = animateSpaceLabel
        animateSpaceLabel = false
        panel.bind(rows, rowHeight)
    }

    private fun bindRail(animated: Boolean) {
        val show = layer == KeyboardLayer.LETTERS && editorLayout == EditorLayout.TEXT
        // The collapsed rail does not clip, so its pinned actions would draw over the emoji board.
        if (inlineAutofill.visibility == VISIBLE || inlinePinned.visibility == VISIBLE || !keepSuggestionRail()) {
            rail.visibility = GONE
            return
        }
        rail.visibility = VISIBLE
        rail.setEmptyTitle("")
        rail.setClipboardVisible(showClipboardButton())
        rail.setEmojiVisible(prefs.emojiButtonPlacement == EmojiButtonPlacement.TOOLBAR && editorLayout == EditorLayout.TEXT && layer == KeyboardLayer.LETTERS)
        rail.setClipboardPreview(clipboardPreviewLabel, clipboardPreviewIsImage)
        rail.setSuggestions(if (show) candidates else emptyList(), animated && show, if (show) emojiCandidates else emptyList())
    }

    private fun languageSwitchLabel(): String? = when {
        editorLayout !in setOf(EditorLayout.TEXT, EditorLayout.URI) -> null
        persistentEnglish -> "සිං"
        else -> "EN"
    }

    private fun updateRailHeight() {
        val height = if (keepSuggestionRail()) suggestionRailHeight() else 0
        val params = railHost.layoutParams as LayoutParams
        if (params.height != height) {
            params.height = height
            railHost.layoutParams = params
        }
    }

    private fun keepSuggestionRail() = inlineAutofill.visibility == VISIBLE || inlinePinned.visibility == VISIBLE ||
        editorLayout in setOf(EditorLayout.TEXT, EditorLayout.URI, EditorLayout.EMAIL) && layer in setOf(
            KeyboardLayer.LETTERS, KeyboardLayer.NUMBERS, KeyboardLayer.SYMBOLS, KeyboardLayer.CLIPBOARD
        )
    private fun spaceCaption() = when {
        englishOneWord -> "Akshara - English · one word"
        persistentEnglish -> "Akshara - English"
        editorLayout !in setOf(EditorLayout.TEXT, EditorLayout.URI) -> "Akshara - English"
        else -> "Akshara - ${mode.title}"
    }

    fun setEnglishOneWord(active: Boolean) {
        if (englishOneWord == active) return
        englishOneWord = active
        if (layer == KeyboardLayer.LETTERS && usesTypingPanel()) bindTyping()
    }

    fun setPersistentEnglish(active: Boolean) {
        if (persistentEnglish == active) return
        persistentEnglish = active
        englishOneWord = false
        // Avoid carrying a Sinhala one-shot Shift across a fast language switch.
        shiftLatch.reset()
        if (layer == KeyboardLayer.LETTERS && usesTypingPanel()) bindTyping()
    }

    fun setAutoCapitalization(active: Boolean) {
        if (!active) suppressAutoShift = false
        if (autoShift == active) return
        autoShift = active
        if (layer == KeyboardLayer.LETTERS) bindTyping()
    }

    private fun renderNativePad() {
        val rows = when (editorLayout) {
            EditorLayout.PHONE -> listOf(listOf("1","2","3"), listOf("4","5","6"), listOf("7","8","9"), listOf("+","0","#"))
            EditorLayout.DATETIME -> listOf(listOf("1","2","3"), listOf("4","5","6"), listOf("7","8","9"), listOf("/","0",":"))
            else -> listOf(listOf("1","2","3"), listOf("4","5","6"), listOf("7","8","9"), buildList {
                if (editorLayout == EditorLayout.SIGNED_NUMBER || editorLayout == EditorLayout.SIGNED_DECIMAL) add("−")
                add("0")
                if (editorLayout == EditorLayout.DECIMAL || editorLayout == EditorLayout.SIGNED_DECIMAL) add(".")
            })
        }
        val pad = LinearLayout(context).apply { orientation = HORIZONTAL }
        val digits = LinearLayout(context).apply { orientation = VERTICAL }
        rows.forEachIndexed { rowIndex, values ->
            val row = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER }
            values.forEach { value ->
                val weight = if (rowIndex == 3 && value == "0") 4 - values.size.toFloat() else 1f
                row.addView(button(value, KeyRole.LETTER, value) { actions.onCharacter(if (value == "−") "-" else value) }, LayoutParams(0, keyHeight(), weight).keyMargins())
            }
            digits.addView(row, LayoutParams(LayoutParams.MATCH_PARENT, rowHeight()))
        }
        pad.addView(digits, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 3.2f))
        val actionsColumn = LinearLayout(context).apply { orientation = VERTICAL }
        actionsColumn.addView(backspaceButton(), LayoutParams(LayoutParams.MATCH_PARENT, rowHeight() * 2).keyMargins())
        actionsColumn.addView(enterButton(), LayoutParams(LayoutParams.MATCH_PARENT, rowHeight() * 2).keyMargins())
        pad.addView(actionsColumn, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, .8f))
        body.addView(pad, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        if (editorLayout == EditorLayout.PHONE) {
            val extras = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER }
            extras.addView(button("*", KeyRole.FUNCTION, "Asterisk") { actions.onCharacter("*") }, LayoutParams(0, dp(46), 1f).keyMargins())
            extras.addView(button("(", KeyRole.FUNCTION, "Left parenthesis") { actions.onCharacter("(") }, LayoutParams(0, dp(46), 1f).keyMargins())
            extras.addView(button(")", KeyRole.FUNCTION, "Right parenthesis") { actions.onCharacter(")") }, LayoutParams(0, dp(46), 1f).keyMargins())
            body.addView(extras, LayoutParams(LayoutParams.MATCH_PARENT, dp(52)))
        }
    }

    private fun renderEmoji() {
        body.clipChildren = true
        body.clipToPadding = true
        if (emojiSearch) {
            showEmojiSearch()
            return
        }
        val sections = listOf("Recent emoji" to recentEmoji) + emojiRepo.categories.map { it.name to it.emoji }
        if (recentEmoji.isEmpty() && emojiCategoryIndex == 0) emojiCategoryIndex = 1
        val catalog = EmojiCatalogView(context, sections, ink, prefs.skinTone,
            onCategory = { index -> emojiCategoryIndex = index; updateEmojiTabs() },
            onPick = { actions.onEmojiPicked(it) })
        emojiCatalog = catalog
        body.addView(emojiCategoryBar(), LayoutParams(LayoutParams.MATCH_PARENT, dp(44)))
        val height = emojiPickerHeight() - dp(44 + 48)
        body.addView(catalog, LayoutParams(LayoutParams.MATCH_PARENT, height))
        body.addView(emojiBottomBar(), LayoutParams(LayoutParams.MATCH_PARENT, dp(48)))
        catalog.showCategory(emojiCategoryIndex)
    }

    private fun closeEmoji() {
        searchQuery.clear()
        emojiSearch = false
        emojiCatalog = null
        layer = KeyboardLayer.LETTERS
        render()
    }

    private fun emojiCategoryBar() = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(8), 0, dp(4), 0)
        addView(iconButton(org.akshara.ime.R.drawable.ic_nav_back, KeyRole.FUNCTION, "Letters") { closeEmoji() }.apply {
            background = emojiPill(theme.function)
            setPadding(dp(6), dp(6), dp(6), dp(6))
        }, LayoutParams(dp(34), dp(34)).apply { marginEnd = dp(8) })
        val scroll = HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false }
        val tabs = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val search = TextView(context).apply {
            text = "Search"
            textSize = 14f
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(ink)
            setPadding(dp(10), 0, dp(14), 0)
            setCompoundDrawablesWithIntrinsicBounds(org.akshara.ime.R.drawable.ic_key_search, 0, 0, 0)
            compoundDrawables[0].mutate().setTint(ink)
            compoundDrawables[0].setBounds(0, 0, dp(18), dp(18))
            setCompoundDrawables(compoundDrawables[0], null, null, null)
            compoundDrawablePadding = dp(6)
            contentDescription = "Search emoji"
            background = emojiPill(theme.function)
            isFocusable = true
            setOnClickListener {
                searchQuery.setLanguage(mode, persistentEnglish || englishOneWord)
                searchLayer = KeyboardLayer.LETTERS
                searchShift = false
                emojiSearch = true
                render()
            }
        }
        tabs.addView(search, LayoutParams(dp(110), dp(34)).apply { marginEnd = dp(4) })
        val icons = listOf(org.akshara.ime.R.drawable.ic_emoji_recent, org.akshara.ime.R.drawable.ic_key_emoji,
            org.akshara.ime.R.drawable.ic_emoji_nature, org.akshara.ime.R.drawable.ic_emoji_food,
            org.akshara.ime.R.drawable.ic_emoji_activity, org.akshara.ime.R.drawable.ic_emoji_travel,
            org.akshara.ime.R.drawable.ic_emoji_objects, org.akshara.ime.R.drawable.ic_heart,
            org.akshara.ime.R.drawable.ic_emoji_flags)
        val names = listOf("Recent") + emojiRepo.categories.map { it.name }
        emojiTabs.clear()
        names.forEachIndexed { index, name ->
            val tab = iconButton(icons[index], KeyRole.GHOST, name) {
                emojiCategoryIndex = index
                emojiCatalog?.showCategory(index)
                updateEmojiTabs()
            }.apply { setPadding(dp(10), dp(10), dp(10), dp(10)) }
            emojiTabs += tab
            if (index == 0 && recentEmoji.isEmpty()) tab.visibility = GONE
            tabs.addView(tab, LayoutParams(dp(40), dp(36)))
        }
        scroll.addView(tabs)
        addView(scroll, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        updateEmojiTabs()
    }

    private fun updateEmojiTabs() {
        emojiTabs.forEachIndexed { index, tab ->
            val selected = index == emojiCategoryIndex
            tab.isSelected = selected
            tab.setColorFilter(if (selected) bg else ColorUtils.setAlphaComponent(ink, 160))
            tab.background = emojiPill(if (selected) ink else Color.TRANSPARENT)
        }
    }

    private fun emojiPill(color: Int) = GradientDrawable().apply {
        cornerRadius = dp(24).toFloat()
        setColor(color)
    }

    private fun emojiBottomBar() = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(8), dp(4), dp(8), dp(4))
        addView(button("ABC", KeyRole.GHOST, "Letters") { closeEmoji() }.apply { typeface = android.graphics.Typeface.DEFAULT; textSize = 16f }, LayoutParams(dp(48), dp(40)))
        addView(Space(context), LayoutParams(0, 1, 1f))
        addView(iconButton(org.akshara.ime.R.drawable.ic_key_emoji, KeyRole.FUNCTION, "Emoji picker active") { }.apply {
            setColorFilter(bg)
            background = emojiPill(ink)
            isSelected = true
        }, LayoutParams(dp(78), dp(38)))
        addView(Space(context), LayoutParams(0, 1, 1f))
        addView(backspaceButton().apply { background = null }, LayoutParams(dp(48), dp(40)))
    }

    private fun emojiPickerHeight(): Int {
        if (isLandscape()) return auxiliaryHeight()
        val desired = maxOf(auxiliaryHeight(), dp(400))
        return maxOf(auxiliaryHeight(), minOf(desired, (resources.displayMetrics.heightPixels * .55f).toInt()))
    }

    private fun showEmojiSearch() {
        emojiCatalog = null
        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), 0, dp(8), 0)
        }
        header.addView(iconButton(org.akshara.ime.R.drawable.ic_nav_back, KeyRole.FUNCTION, "Back to emoji") {
            emojiSearch = false; render()
        }.apply {
            background = emojiPill(theme.function)
            setPadding(dp(6), dp(6), dp(6), dp(6))
        }, LayoutParams(dp(34), dp(34)))
        header.addView(textView("Search emoji", 18f).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }, LayoutParams(0, dp(44), 1f))
        body.addView(header, LayoutParams(LayoutParams.MATCH_PARENT, dp(48)))
        val searchHeight = dp(KeyboardGeometry.keyAreaDp(prefs.keyboardSize, isLandscape()))
        val gridHeight = if (isLandscape()) dp(50) else dp(104)
        val resultsCard = LinearLayout(context).apply {
            orientation = VERTICAL
            background = GradientDrawable().apply { cornerRadius = dp(16).toFloat(); setColor(theme.surface) }
            clipToOutline = true
        }
        val grid = emojiScroller(emptyList())
        searchResults = grid
        val empty = textView("No emoji found", 13f).apply { visibility = GONE }
        searchEmpty = empty
        resultsCard.addView(FrameLayout(context).apply {
            addView(grid, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            addView(empty, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }, LayoutParams(LayoutParams.MATCH_PARENT, gridHeight))
        resultsCard.addView(View(context).apply { setBackgroundColor(ColorUtils.setAlphaComponent(ink, 24)) },
            LayoutParams(LayoutParams.MATCH_PARENT, dp(1)))
        val query = textView(emojiQuery.ifEmpty { "Search in English or Sinhala" }, 14f).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), 0, dp(14), 0)
            setTextColor(if (emojiQuery.isEmpty()) ColorUtils.setAlphaComponent(ink, 150) else ink)
            setSingleLine(true)
            ellipsize = android.text.TextUtils.TruncateAt.START
            contentDescription = "Emoji search: ${emojiQuery.ifEmpty { "Search in English or Sinhala" }}"
            setCompoundDrawablesWithIntrinsicBounds(org.akshara.ime.R.drawable.ic_key_search, 0, 0, 0)
            compoundDrawables[0].mutate().setTint(ink)
            compoundDrawables[0].setBounds(0, 0, dp(18), dp(18))
            setCompoundDrawables(compoundDrawables[0], null, null, null)
            compoundDrawablePadding = dp(12)
        }
        val queryRow = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        queryRow.addView(query, LayoutParams(0, dp(36), 1f))
        searchLabel = query
        val clear = button("×", KeyRole.GHOST, "Clear emoji search") {
            searchQuery.clear(); updateEmojiSearchResults()
        }
        searchClear = clear
        queryRow.addView(clear, LayoutParams(dp(40), dp(36)))
        resultsCard.addView(queryRow, LayoutParams(LayoutParams.MATCH_PARENT, dp(36)))
        body.addView(resultsCard, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            setMargins(dp(8), 0, dp(8), dp(8))
        })
        val searchActions = object : KeyboardActions by actions {
            override fun onCharacter(value: String) {
                searchQuery.append(value, searchLayer == KeyboardLayer.LETTERS)
                if (searchShift) { searchShift = false; bindEmojiSearchKeys() }
                updateEmojiSearchResults()
            }
            override fun onBackspace(word: Boolean) { searchQuery.delete(word); updateEmojiSearchResults() }
            override fun onSpace() { searchQuery.append(" ", false); updateEmojiSearchResults() }
            override fun onLanguageSwitch() {
                searchQuery.setLanguage(mode, !searchQuery.english)
                searchShift = false
                bindEmojiSearchKeys()
                updateEmojiSearchResults()
            }
            override fun onSpaceLongPress() = onLanguageSwitch()
            override fun onEnter() { emojiSearch = false; render() }
            override fun onCursorDelta(delta: Int) = Unit
            override fun onPreviewDelete(clusters: Int) = Unit
            override fun onCommitPreviewDelete() = Unit
            override fun onCancelPreviewDelete() = Unit
            override fun onSpaceSwipe(up: Boolean) = Unit
            override fun languageScoreForKey(output: String) = 0f
        }
        val keyboard = KeyboardPanel(context, prefs, popups, searchActions,
            theme, hints = true,
            onLayer = { searchLayer = it; bindEmojiSearchKeys() },
            onShift = { searchShift = !searchShift; bindEmojiSearchKeys() })
        keyboard.learningEnabled = false
        searchPanel = keyboard
        bindEmojiSearchKeys()
        body.addView(keyboard, LayoutParams(LayoutParams.MATCH_PARENT, searchHeight))
        updateEmojiSearchResults()
    }

    private fun bindEmojiSearchKeys() {
        val rows = KeyboardLayoutFactory.typingRows(mode, searchLayer, searchShift, false,
            EditorLayout.TEXT, "none", false, "Done",
            if (searchQuery.english) "Akshara - English" else "Akshara - ${mode.title}", false,
            if (searchQuery.english) "සිං" else "EN", true, searchQuery.english)
        searchPanel?.bind(rows, KeyboardGeometry.rowHeightPx(prefs.keyboardSize, isLandscape(), resources.displayMetrics.density))
    }

    private fun cancelEmojiSearch() {
        searchGeneration++
        searchFuture?.cancel(false)
        searchFuture = null
    }

    private fun updateEmojiSearchResults() {
        val query = emojiQuery
        searchLabel?.apply {
            text = query.ifEmpty { "Search in English or Sinhala" }
            contentDescription = "Emoji search: $text"
            setTextColor(if (query.isEmpty()) ColorUtils.setAlphaComponent(ink, 150) else ink)
        }
        searchClear?.visibility = if (query.isEmpty()) INVISIBLE else VISIBLE
        cancelEmojiSearch()
        val generation = searchGeneration
        val grid = searchResults ?: return
        val adapter = grid.adapter as EmojiAdapter
        fun publish(values: List<String>) {
            if (generation != searchGeneration || layer != KeyboardLayer.EMOJI || !emojiSearch) return
            adapter.submit(values)
            grid.scrollToPosition(0)
            searchEmpty?.visibility = if (values.isEmpty()) VISIBLE else GONE
        }
        if (query.isBlank()) {
            publish((recentEmoji + emojiRepo.categories.first().emoji).distinct().take(36))
            return
        }
        // Keep input responsive: coalesce rapid typing and match away from the UI thread.
        val executor = searchExecutor ?: java.util.concurrent.Executors.newSingleThreadScheduledExecutor { task ->
            Thread(task, "emoji-search").apply { isDaemon = true }
        }.also { searchExecutor = it }
        searchFuture = executor.schedule({
            val sinhala = SinhalaEngine.transliterate(query, InputMode.SMART_PHONETIC)
            val values = (emojiRepo.search(query, 64) +
                if (sinhala != query) emojiRepo.search(sinhala, 64) else emptyList()).distinct().take(80)
            handler.post { publish(values) }
        }, 35, java.util.concurrent.TimeUnit.MILLISECONDS)
    }

    private fun emojiScroller(values: List<String>) =
        EmojiBoard.scroller(context, values, ink, prefs.skinTone) { actions.onEmojiPicked(it) }
    private fun showClipboardButton() =
        clipboardHistoryEnabled && editorLayout in setOf(EditorLayout.TEXT, EditorLayout.URI, EditorLayout.EMAIL) &&
            layer in setOf(KeyboardLayer.LETTERS, KeyboardLayer.CLIPBOARD)

    private fun bindClipboard() {
        body.clipChildren = true
        body.clipToPadding = true
        val existing = body.getChildAt(0) as? ClipboardBoard
        if (existing != null && body.childCount == 1) {
            existing.configure(clipboardRecent, clipboardPinned)
            applyClipboardHeight()
            return
        }
        body.removeAllViews()
        val board = ClipboardBoard(
            context,
            theme,
            onPaste = { clip ->
                actions.onPasteText(clip)
                resetClipboardExpansion()
                layer = KeyboardLayer.LETTERS
                render()
            },
            onBack = {
                resetClipboardExpansion()
                layer = KeyboardLayer.LETTERS
                render()
            },
            onSettings = { actions.onSettings() },
            onClearRecent = {
                clipboardStore.clearHistory()
                refreshClipboardFromStore()
            },
            onPinRecent = { index ->
                clipboardStore.pin(index)
                refreshClipboardFromStore()
            },
            onRemoveRecent = { index ->
                clipboardStore.remove(index)
                refreshClipboardFromStore()
            },
            onRemovePinned = { index ->
                clipboardStore.removePinned(index)
                refreshClipboardFromStore()
            }
        )
        board.configure(clipboardRecent, clipboardPinned)
        body.addView(board, LayoutParams(LayoutParams.MATCH_PARENT, clipboardBoardHeight()))
        applyClipboardHeight()
    }

    private fun leaveClipboardOrToggle() {
        if (layer == KeyboardLayer.CLIPBOARD) {
            resetClipboardExpansion()
            layer = KeyboardLayer.LETTERS
        } else {
            layer = KeyboardLayer.CLIPBOARD
        }
        render()
    }

    private fun resetClipboardExpansion() {
        clipboardExtraPx = 0
        endClipboardDrag()
    }

    private fun clipboardBoardHeight() = clipboardBaseHeight() + clipboardExtraPx

    private fun clipboardBaseHeight() = auxiliaryHeight() - suggestionRailHeight()

    private fun clipboardMaxExtra(): Int {
        val fraction = if (isLandscape()) {
            KeyboardGeometry.CLIPBOARD_EXPAND_FRACTION_LANDSCAPE
        } else {
            KeyboardGeometry.CLIPBOARD_EXPAND_FRACTION_PORTRAIT
        }
        val screen = resources.displayMetrics.heightPixels.coerceAtLeast(1)
        val collapsed = collapsedClipboardKeyboardHeight()
        val fromFraction = (screen * fraction).roundToInt()
        val withMinimum = max(fromFraction, collapsed + dp(KeyboardGeometry.CLIPBOARD_MIN_EXTRA_DP))
        val capped = minOf(withMinimum, (screen * KeyboardGeometry.CLIPBOARD_EXPAND_CAP_FRACTION).roundToInt())
        return max(0, capped - collapsed)
    }

    private fun collapsedClipboardKeyboardHeight(): Int {
        val top = dp(KeyboardGeometry.TOP_PAD_DP)
        val rail = if (keepSuggestionRail()) suggestionRailHeight() else 0
        val board = clipboardBaseHeight()
        val bottom = (homePad.layoutParams as? LayoutParams)?.height ?: dp(KeyboardGeometry.BOTTOM_PAD_DP)
        return top + rail + board + bottom
    }

    private fun applyClipboardHeight() {
        val board = body.getChildAt(0) as? ClipboardBoard ?: return
        val params = board.layoutParams as LayoutParams
        val target = clipboardBoardHeight()
        if (params.height != target) {
            params.height = target
            board.layoutParams = params
        }
        updateClipboardHandle()
        requestLayout()
    }

    private fun setClipboardExtra(extra: Int) {
        val clamped = extra.coerceIn(0, clipboardMaxExtra())
        if (clamped == clipboardExtraPx) return
        clipboardExtraPx = clamped
        applyClipboardHeight()
    }

    private fun toggleClipboardExpanded() {
        if (layer != KeyboardLayer.CLIPBOARD) return
        setClipboardExtra(if (clipboardExtraPx > clipboardMaxExtra() / 2) 0 else clipboardMaxExtra())
    }

    private fun snapClipboardExpanded(event: MotionEvent) {
        val tracker = clipboardVelocity
        tracker?.computeCurrentVelocity(1000)
        val velocityY = tracker?.yVelocity ?: 0f
        val flick = dp(KeyboardGeometry.CLIPBOARD_FLICK_DP_PER_SEC).toFloat()
        val maxExtra = clipboardMaxExtra()
        val target = when {
            velocityY <= -flick -> maxExtra
            velocityY >= flick -> 0
            clipboardExtraPx >= maxExtra / 2 -> maxExtra
            else -> 0
        }
        setClipboardExtra(target)
    }

    private fun endClipboardDrag() {
        clipboardDragActive = false
        clipboardDragTracking = false
        releaseClipboardVelocity()
    }

    private fun obtainClipboardVelocity(): VelocityTracker {
        val tracker = clipboardVelocity ?: VelocityTracker.obtain().also { clipboardVelocity = it }
        return tracker
    }

    private fun releaseClipboardVelocity() {
        clipboardVelocity?.recycle()
        clipboardVelocity = null
    }

    private fun inClipboardHandleZone(x: Float, y: Float): Boolean {
        if (y < 0f || y > dp(KeyboardGeometry.CLIPBOARD_HANDLE_HIT_DP)) return false
        if (y >= paddingTop && x < dp(KeyboardGeometry.CLIPBOARD_HANDLE_EXCLUDE_START_DP)) return false
        return true
    }

    private fun layoutClipboardHandle() {
        if (layer != KeyboardLayer.CLIPBOARD) {
            clipboardHandle.layout(0, 0, 0, 0)
            return
        }
        val hit = dp(KeyboardGeometry.CLIPBOARD_HANDLE_HIT_DP)
        val exclude = dp(KeyboardGeometry.CLIPBOARD_HANDLE_EXCLUDE_START_DP)
        clipboardHandle.layout(exclude, 0, width, hit)
    }

    private fun updateClipboardHandle() {
        val show = layer == KeyboardLayer.CLIPBOARD
        clipboardHandle.visibility = if (show) VISIBLE else GONE
        clipboardHandle.isClickable = show
        clipboardHandle.isFocusable = show
        clipboardHandle.contentDescription = if (clipboardExtraPx > clipboardMaxExtra() / 2) {
            "Collapse clipboard"
        } else {
            "Expand clipboard"
        }
        if (show) clipboardHandle.bringToFront()
    }

    /** Test helper: expanded extra height in pixels while clipboard is open. */
    internal fun clipboardExpansionPx(): Int = clipboardExtraPx

    /** Test helper: snap clipboard fully open or closed. */
    internal fun setClipboardExpandedForTest(expanded: Boolean) {
        if (layer != KeyboardLayer.CLIPBOARD) return
        setClipboardExtra(if (expanded) clipboardMaxExtra() else 0)
    }

    private fun refreshClipboardFromStore() {
        clipboardRecent = clipboardStore.items()
        clipboardPinned = clipboardStore.pinnedItems()
        if (layer == KeyboardLayer.CLIPBOARD) render()
    }

    private fun updateShift() {
        if (autoShift && !suppressAutoShift && !shiftLatch.active) {
            suppressAutoShift = true
            bindTyping()
            return
        }
        shiftLatch.tap(android.os.SystemClock.elapsedRealtime())
        bindTyping()
    }

    private fun acceptCandidate(value: String) {
        actions.onCandidate(value)
        if (shiftLatch.shifted && !shiftLatch.capsLock) {
            shiftLatch.consumeOneShot()
            if (layer == KeyboardLayer.LETTERS) bindTyping()
        }
    }
    private fun backspaceButton(): ImageButton {
        val b = iconButton(org.akshara.ime.R.drawable.ic_key_backspace, KeyRole.FUNCTION, "Delete") { }
        var repeats = 0
        b.setOnClickListener { actions.onBackspace() }
        val repeat = object : Runnable { override fun run() { repeats++; actions.onBackspace(repeats > 20); handler.postDelayed(this, if (repeats > 20) 45 else 80) } }
        b.setOnTouchListener { _, event -> when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { repeats = 0; handler.postDelayed(repeat, 420); true }
            MotionEvent.ACTION_UP -> { handler.removeCallbacks(repeat); if (repeats == 0) b.performClick(); true }
            MotionEvent.ACTION_CANCEL -> { handler.removeCallbacks(repeat); true }
            else -> true
        } }; return b
    }
    private fun spaceButton(): Button {
        val label = spaceCaption()
        val b = button(label, KeyRole.LETTER, "Space") { }
        b.textSize = KeyboardGeometry.SPACE_COLLAPSE_SP
        b.gravity = Gravity.BOTTOM or Gravity.END
        b.setPadding(dp(8), 0, dp(10), dp(7))
        b.setTextColor(ColorUtils.setAlphaComponent(ink, (255 * KeyboardGeometry.SPACE_COLLAPSE_ALPHA).toInt()))
        b.setOnClickListener { actions.onSpace() }
        var startX = 0f; var lastSteps = 0
        b.setOnTouchListener { _, e -> when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { startX = e.x; lastSteps = 0; true }
            MotionEvent.ACTION_MOVE -> { val steps = ((e.x - startX) / dp(24)).toInt(); if (steps != lastSteps) { actions.onCursorDelta(steps - lastSteps); lastSteps = steps }; true }
            MotionEvent.ACTION_UP -> { if (abs(e.x - startX) < dp(12)) b.performClick(); true }
            else -> true
        } }; return b
    }
    private fun enterButton(): View = when (enterLabel) {
        "↵" -> iconButton(org.akshara.ime.R.drawable.ic_key_enter, KeyRole.ACCENT, "Enter") { actions.onEnter() }
        "⌕" -> iconButton(org.akshara.ime.R.drawable.ic_key_search, KeyRole.ACCENT, "Enter") { actions.onEnter() }
        else -> button(enterLabel, KeyRole.ACCENT, "Enter") { actions.onEnter() }
    }
    private fun button(label: String, role: KeyRole, description: String, click: () -> Unit) = Button(context).apply {
        text = label; textSize = if (label.length > 10) 13f else 20f; isAllCaps = false; gravity = Gravity.CENTER
        setTextColor(inkFor(role)); contentDescription = description; minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
        background = keyBackground(role)
        stateListAnimator = null; setOnClickListener { click() }
        accessibilityDelegate = object : AccessibilityDelegate() { override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) { super.onInitializeAccessibilityNodeInfo(host, info); info.className = Button::class.java.name } }
    }
    private fun iconButton(icon: Int, role: KeyRole, description: String, click: () -> Unit) = ImageButton(context).apply {
        setImageResource(icon); setColorFilter(inkFor(role)); scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
        setPadding(dp(10), dp(10), dp(10), dp(10)); contentDescription = description; background = keyBackground(role)
        stateListAnimator = null; setOnClickListener { click() }
    }
    private fun textView(value: String, size: Float) = TextView(context).apply { text = value; textSize = size; gravity = Gravity.CENTER; setTextColor(ink) }
    private fun LayoutParams.margins() = keyMargins()
    private fun LayoutParams.keyMargins() = apply {
        val horizontal = KeyboardMetrics.marginPx(prefs.keySpacing, resources.displayMetrics.density, false)
        val vertical = KeyboardMetrics.marginPx(prefs.keySpacing, resources.displayMetrics.density, true)
        setMargins(horizontal, vertical, horizontal, vertical)
    }
    private fun usesTypingPanel() = editorLayout !in numericEditors && layer != KeyboardLayer.EMOJI && layer != KeyboardLayer.CLIPBOARD
    private fun suggestionKeySliver() = (KeyboardGeometry.SLIVER_DP * resources.displayMetrics.density).toInt()
    private fun isLandscape() = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    private fun inSuggestionSliver(y: Float): Boolean {
        val sliver = suggestionKeySliver()
        return y >= rail.bottom - sliver && y < rail.bottom + sliver
    }
    private fun dispatchToPanel(event: MotionEvent): Boolean {
        val transformed = MotionEvent.obtain(event)
        transformed.offsetLocation(-(body.left + panel.left).toFloat(), -(body.top + panel.top).toFloat())
        val handled = panel.dispatchTouchEvent(transformed)
        transformed.recycle()
        return handled
    }
    private enum class KeyRole { LETTER, FUNCTION, ACCENT, GHOST }
    private fun inkFor(role: KeyRole) = if (role == KeyRole.ACCENT) theme.accentInk else ink
    private fun keyBackground(role: KeyRole): StateListDrawable {
        fun shape(color: Int) = GradientDrawable().apply {
            cornerRadius = dp(8).toFloat(); setColor(color)
            setStroke(if (theme.highContrast) dp(2) else 0, theme.border)
        }
        val (base, pressed) = when (role) {
            KeyRole.LETTER -> theme.key to theme.keyPressed
            KeyRole.FUNCTION -> theme.function to theme.functionPressed
            KeyRole.ACCENT -> theme.accent to theme.accentPressed
            KeyRole.GHOST -> Color.TRANSPARENT to theme.ghostPressed
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), shape(pressed))
            addState(intArrayOf(), shape(base))
        }
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun suggestionRailHeight() = KeyboardGeometry.railHeightPx(isLandscape(), resources.displayMetrics.density).toInt()
    private fun keyHeight() = if (isLandscape()) dp(42) else dp(48)
    private fun rowHeight() = if (isLandscape()) dp(48) else dp(56)
    private fun emojiGridHeight(rows: Int) = EmojiBoard.gridHeight(context, rows, isLandscape())
    private fun auxiliaryHeight() = (KeyboardGeometry.rowHeightPx(prefs.keyboardSize, isLandscape(), resources.displayMetrics.density) *
        KeyboardLayoutFactory.typingRows(mode, KeyboardLayer.LETTERS, false, false, editorLayout,
            prefs.topRow, false, enterLabel, spaceCaption(), false, languageSwitchLabel(),
            prefs.spacePunctuationKeys, englishOneWord || persistentEnglish).size).toInt() +
        if (editorLayout == EditorLayout.TEXT) suggestionRailHeight() else 0
    private fun navigationBarFallback(): Int {
        val id = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (id != 0) resources.getDimensionPixelSize(id) else 0
    }
    internal fun isDarkTheme() = theme.dark
    internal fun keyboardBackground() = bg

    companion object {
        val qwertyRows = listOf("qwertyuiop".map(Char::toString), "asdfghjkl".map(Char::toString), "zxcvbnm".map(Char::toString))
        val slsRows = listOf(
            listOf("q","w","e","r","t","y","u","i","o","p","["),
            listOf("a","s","d","f","g","h","j","k","l",";"),
            listOf("rakaranshaya","x","c","v","b","n","m",",",".")
        )
        val numbers = listOf("1234567890".map(Char::toString), listOf("@","#","₨","_","&","-","+","(",")","/"), listOf("*","\"","'",":",";","!","?"))
        val symbols = listOf(listOf("~","`","|","•","√","π","÷","×","¶","∆"), listOf("£","€","$","¢","^","°","=","{","}","\\"), listOf("%","©","®","™","✓","[","]"))
        val numericEditors = setOf(EditorLayout.NUMBER, EditorLayout.SIGNED_NUMBER, EditorLayout.DECIMAL, EditorLayout.SIGNED_DECIMAL, EditorLayout.PHONE, EditorLayout.DATETIME)
    }
}
