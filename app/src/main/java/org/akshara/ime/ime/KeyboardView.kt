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
    private var emojiQuery = ""
    private var searchShift = false
    private var searchLayer = KeyboardLayer.LETTERS
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
    private val palette = KeyboardPaletteResolver.resolve(context, prefs.theme, prefs.highContrast)
    private val bg = palette.background
    private val key = palette.key
    private val utility = palette.utility
    private val ink = palette.ink
    private val rail = SuggestionRail(
        context,
        ink,
        { actions.onCandidate(it) },
        {
            actions.onClipboardOpen()
            leaveClipboardOrToggle()
        },
        {
            layer = KeyboardLayer.EMOJI
            render()
        },
        { actions.onClipboardPreviewPaste() },
        { actions.onEmojiPicked(it) }
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
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(8), dp(2), dp(8), dp(2))
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
            override fun onCandidate(value: String) = actions.onCandidate(value)
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
        KeyboardColors(key, utility, ink, palette.dark, palette.highContrast),
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
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || suggestions.isEmpty()) {
            inlineAutofill.visibility = GONE
            rail.visibility = VISIBLE
            return
        }
        inlineAutofill.visibility = VISIBLE
        rail.visibility = GONE
        val size = android.util.Size((resources.displayMetrics.widthPixels * .72f).toInt(), suggestionRailHeight())
        val executor = java.util.concurrent.Executor { handler.post(it) }
        suggestions.take(3).forEach { suggestion ->
            suggestion.inflate(context, size, executor) { view ->
                if (generation != inlineAutofillGeneration) return@inflate
                inlineRow.addView(view, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT).apply {
                    marginEnd = dp(8)
                })
            }
        }
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
        popups.dismiss()
        sliverPanel = null
        if (layer != KeyboardLayer.CLIPBOARD) clipboardExtraPx = 0
        railHost.layoutParams = (railHost.layoutParams as LayoutParams).apply {
            height = if (keepSuggestionRail()) suggestionRailHeight() else 0
        }
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
            prefs.topRow, prefs.emojiPicker, enterLabel, spaceLabel, false, languageSwitchLabel(), prefs.spacePunctuationKeys,
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
        if (inlineAutofill.visibility == VISIBLE) {
            rail.visibility = GONE
            return
        }
        rail.visibility = VISIBLE
        rail.setEmptyTitle("")
        rail.setClipboardVisible(showClipboardButton())
        rail.setEmojiVisible(prefs.emojiPicker && editorLayout == EditorLayout.TEXT && layer == KeyboardLayer.LETTERS)
        rail.setClipboardPreview(clipboardPreviewLabel, clipboardPreviewIsImage)
        rail.setSuggestions(if (show) candidates else emptyList(), animated && show, if (show) emojiCandidates else emptyList())
    }

    private fun languageSwitchLabel(): String? = when {
        editorLayout !in setOf(EditorLayout.TEXT, EditorLayout.URI) -> null
        persistentEnglish -> "සිං"
        else -> "EN"
    }

    private fun keepSuggestionRail() =
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
            else -> listOf(listOf("1","2","3"), listOf("4","5","6"), listOf("7","8","9"), listOf(
                if (editorLayout == EditorLayout.SIGNED_NUMBER || editorLayout == EditorLayout.SIGNED_DECIMAL) "−" else "",
                "0",
                if (editorLayout == EditorLayout.DECIMAL || editorLayout == EditorLayout.SIGNED_DECIMAL) "." else ""
            ))
        }
        rows.forEachIndexed { rowIndex, values ->
            val row = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER }
            values.forEach { value ->
                if (value.isEmpty()) row.addView(Space(context), LayoutParams(0, keyHeight(), 1f).keyMargins())
                else row.addView(button(value, key, value) { actions.onCharacter(if (value == "−") "-" else value) }, LayoutParams(0, keyHeight(), 1f).keyMargins())
            }
            val action = when (rowIndex) { 0 -> backspaceButton(); 3 -> enterButton(); else -> Space(context) }
            row.addView(action, LayoutParams(0, keyHeight(), 1f).keyMargins())
            body.addView(row, LayoutParams(LayoutParams.MATCH_PARENT, rowHeight()))
        }
        if (editorLayout == EditorLayout.PHONE) {
            val extras = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER }
            extras.addView(button("*", utility, "Asterisk") { actions.onCharacter("*") }, LayoutParams(0, dp(46), 1f).keyMargins())
            extras.addView(button("(", utility, "Left parenthesis") { actions.onCharacter("(") }, LayoutParams(0, dp(46), 1f).keyMargins())
            extras.addView(button(")", utility, "Right parenthesis") { actions.onCharacter(")") }, LayoutParams(0, dp(46), 1f).keyMargins())
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
        emojiQuery = ""
        emojiSearch = false
        emojiCatalog = null
        layer = KeyboardLayer.LETTERS
        render()
    }

    private fun emojiCategoryBar() = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(8), 0, dp(4), 0)
        addView(iconButton(org.akshara.ime.R.drawable.ic_nav_back, utility, "Letters") { closeEmoji() }.apply {
            background = emojiPill(utility)
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
            background = emojiPill(utility)
            isFocusable = true
            setOnClickListener { emojiSearch = true; render() }
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
            val tab = iconButton(icons[index], Color.TRANSPARENT, name) {
                emojiCategoryIndex = index
                emojiCatalog?.showCategory(index)
                updateEmojiTabs()
            }.apply { setPadding(dp(10), dp(10), dp(10), dp(10)) }
            emojiTabs += tab
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
        addView(button("ABC", Color.TRANSPARENT, "Letters") { closeEmoji() }.apply { typeface = android.graphics.Typeface.DEFAULT; textSize = 16f }, LayoutParams(dp(48), dp(40)))
        addView(Space(context), LayoutParams(0, 1, 1f))
        addView(iconButton(org.akshara.ime.R.drawable.ic_key_emoji, ink, "Emoji picker active") { }.apply {
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
        header.addView(iconButton(org.akshara.ime.R.drawable.ic_nav_back, utility, "Back to emoji") {
            emojiSearch = false; render()
        }.apply {
            background = emojiPill(utility)
            setPadding(dp(6), dp(6), dp(6), dp(6))
        }, LayoutParams(dp(34), dp(34)))
        header.addView(textView("Search emoji", 18f).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }, LayoutParams(0, dp(44), 1f))
        body.addView(header, LayoutParams(LayoutParams.MATCH_PARENT, dp(48)))
        val sinhalaQuery = SinhalaEngine.transliterate(emojiQuery, InputMode.SMART_PHONETIC)
        val results = if (emojiQuery.isBlank()) {
            (recentEmoji + emojiRepo.categories.first().emoji).distinct().take(36)
        } else (emojiRepo.search(emojiQuery, 64) + emojiRepo.search(sinhalaQuery, 64)).distinct().take(80)
        val searchHeight = dp(KeyboardGeometry.keyAreaDp(prefs.keyboardSize, isLandscape()))
        val gridHeight = if (isLandscape()) dp(50) else dp(104)
        val resultsCard = LinearLayout(context).apply {
            orientation = VERTICAL
            background = GradientDrawable().apply { cornerRadius = dp(16).toFloat(); setColor(key) }
            clipToOutline = true
        }
        val grid = if (results.isEmpty()) textView("No emoji found", 13f) else emojiScroller(results)
        resultsCard.addView(grid, LayoutParams(LayoutParams.MATCH_PARENT, gridHeight))
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
        if (emojiQuery.isNotEmpty()) {
            queryRow.addView(button("×", Color.TRANSPARENT, "Clear emoji search") {
                emojiQuery = ""; render()
            }, LayoutParams(dp(40), dp(36)))
        }
        resultsCard.addView(queryRow, LayoutParams(LayoutParams.MATCH_PARENT, dp(36)))
        body.addView(resultsCard, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            setMargins(dp(8), 0, dp(8), dp(8))
        })
        val searchActions = object : KeyboardActions by actions {
            override fun onCharacter(value: String) { emojiQuery += value; searchShift = false; render() }
            override fun onBackspace(word: Boolean) { emojiQuery = emojiQuery.dropLast(if (word) emojiQuery.length else 1); render() }
            override fun onSpace() { emojiQuery += " "; render() }
            override fun onEnter() { emojiSearch = false; render() }
            override fun onCursorDelta(delta: Int) = Unit
            override fun onPreviewDelete(clusters: Int) = Unit
            override fun onCommitPreviewDelete() = Unit
            override fun onCancelPreviewDelete() = Unit
            override fun onSpaceSwipe(up: Boolean) = Unit
            override fun languageScoreForKey(output: String) = 0f
        }
        val searchPanel = KeyboardPanel(context, prefs, popups, searchActions,
            KeyboardColors(key, utility, ink, palette.dark, palette.highContrast),
            onLayer = { searchLayer = it; render() },
            onShift = { searchShift = !searchShift; render() })
        searchPanel.learningEnabled = false
        val rows = KeyboardLayoutFactory.typingRows(InputMode.PHONETIC, searchLayer, searchShift, false,
            EditorLayout.ASCII, "none", false, "Done", "Search emoji", false)
        searchPanel.bind(rows, searchHeight.toFloat() / rows.size)
        body.addView(searchPanel, LayoutParams(LayoutParams.MATCH_PARENT, searchHeight))
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
            KeyboardColors(key, utility, ink, palette.dark, palette.highContrast),
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
    private fun backspaceButton(): ImageButton {
        val b = iconButton(org.akshara.ime.R.drawable.ic_key_backspace, utility, "Delete") { }
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
        val b = button(label, key, "Space") { }
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
        "↵" -> iconButton(org.akshara.ime.R.drawable.ic_key_enter, utility, "Enter") { actions.onEnter() }
        "⌕" -> iconButton(org.akshara.ime.R.drawable.ic_key_search, utility, "Enter") { actions.onEnter() }
        else -> button(enterLabel, utility, "Enter") { actions.onEnter() }
    }
    private fun button(label: String, color: Int, description: String, click: () -> Unit) = Button(context).apply {
        text = label; textSize = if (label.length > 10) 13f else 20f; isAllCaps = false; gravity = Gravity.CENTER
        setTextColor(ink); contentDescription = description; minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
        background = keyBackground(color)
        stateListAnimator = null; setOnClickListener { click() }
        accessibilityDelegate = object : AccessibilityDelegate() { override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) { super.onInitializeAccessibilityNodeInfo(host, info); info.className = Button::class.java.name } }
    }
    private fun iconButton(icon: Int, color: Int, description: String, click: () -> Unit) = ImageButton(context).apply {
        setImageResource(icon); setColorFilter(ink); scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
        setPadding(dp(10), dp(10), dp(10), dp(10)); contentDescription = description; background = keyBackground(color)
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
    private fun keyBackground(base: Int): StateListDrawable {
        fun shape(color: Int) = GradientDrawable().apply {
            cornerRadius = dp(8).toFloat(); setColor(color)
            setStroke(if (prefs.highContrast) dp(2) else 0, ink)
        }
        val pressed = ColorUtils.blendARGB(base, if (isDark()) Color.WHITE else Color.BLACK, .18f)
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
    private fun auxiliaryHeight() = (KeyboardGeometry.keyAreaDp(prefs.keyboardSize, isLandscape()) * resources.displayMetrics.density).toInt() +
        if (editorLayout == EditorLayout.TEXT) suggestionRailHeight() else 0
    private fun navigationBarFallback(): Int {
        val id = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (id != 0) resources.getDimensionPixelSize(id) else 0
    }
    private fun isDark() = palette.dark
    internal fun isDarkTheme() = palette.dark
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
