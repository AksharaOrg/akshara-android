package org.akshara.ime.ime

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
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

enum class KeyboardLayer { LETTERS, NUMBERS, SYMBOLS, EMOJI, CLIPBOARD }
enum class EditorLayout { TEXT, ASCII, EMAIL, URI, NUMBER, SIGNED_NUMBER, DECIMAL, SIGNED_DECIMAL, PHONE, DATETIME }

interface KeyboardActions {
    fun onCharacter(value: String)
    fun onBackspace(word: Boolean = false)
    fun onSpace()
    fun onEnter()
    fun onCandidate(value: String)
    fun onGlobe()
    fun onModeRequested(mode: InputMode)
    fun onHide()
    fun onCursorDelta(delta: Int)
    fun onPressFeedback() {}
    fun languageScoreForKey(output: String): Float = 0f
    fun onPreviewDelete(clusters: Int) {}
    fun onCommitPreviewDelete() {}
    fun onCancelPreviewDelete() {}
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
    private val shifted get() = shiftLatch.shifted || shiftLatch.capsLock
    private val capsLock get() = shiftLatch.capsLock
    private var enterLabel = "↵"
    private var editorLayout = EditorLayout.TEXT
    private var offerGlobe = false
    private var animateSpaceLabel = true
    private var candidates = emptyList<String>()
    private var clipboardRecent = emptyList<String>()
    private var clipboardPinned = emptyList<String>()
    private var recentEmoji = emptyList<String>()
    private val emojiRepo = EmojiRepository(context)
    private val clipboardStore = ClipboardHistoryStore(context)
    private var emojiSearch = false
    private var emojiQuery = ""
    private var emojiCategoryIndex = 1
    private val handler = Handler(Looper.getMainLooper())
    private val palette = KeyboardPaletteResolver.resolve(context, prefs.theme, prefs.highContrast)
    private val bg = palette.background
    private val key = palette.key
    private val utility = palette.utility
    private val ink = palette.ink
    private val rail = SuggestionRail(context, ink, { actions.onCandidate(it) }) {
        layer = KeyboardLayer.CLIPBOARD; render()
    }
    private val body = LinearLayout(context)
    private val homePad = View(context)
    private val popups = KeyPopups(context)
    private val panel = KeyboardPanel(
        context, prefs, popups, object : KeyboardActions {
            override fun onCharacter(value: String) {
                actions.onCharacter(value)
                if (shiftLatch.shifted && !shiftLatch.capsLock) {
                    shiftLatch.consumeOneShot()
                    bindTyping()
                }
            }
            override fun onBackspace(word: Boolean) = actions.onBackspace(word)
            override fun onSpace() = actions.onSpace()
            override fun onEnter() = actions.onEnter()
            override fun onCandidate(value: String) = actions.onCandidate(value)
            override fun onGlobe() = actions.onGlobe()
            override fun onModeRequested(mode: InputMode) = actions.onModeRequested(mode)
            override fun onHide() = actions.onHide()
            override fun onCursorDelta(delta: Int) = actions.onCursorDelta(delta)
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
        clipChildren = false
        clipToPadding = false
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val types = WindowInsetsCompat.Type.navigationBars() or
                WindowInsetsCompat.Type.mandatorySystemGestures() or
                WindowInsetsCompat.Type.tappableElement()
            val system = insets.getInsetsIgnoringVisibility(types).bottom
            val resource = navigationBarFallback()
            val bottom = maxOf(system, resource, dp(KeyboardGeometry.BOTTOM_PAD_DP)).coerceAtMost(dp(64))
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
        addView(rail, LayoutParams(LayoutParams.MATCH_PARENT, suggestionRailHeight()))
        body.orientation = VERTICAL
        body.clipChildren = true
        body.clipToPadding = true
        addView(body, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(homePad, LayoutParams(LayoutParams.MATCH_PARENT, dp(KeyboardGeometry.BOTTOM_PAD_DP)))
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
        super.onDetachedFromWindow()
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

    fun configure(mode: InputMode, offerGlobe: Boolean, enter: String, editor: EditorLayout = EditorLayout.TEXT) {
        this.mode = mode; enterLabel = enter; editorLayout = editor; this.offerGlobe = offerGlobe
        shiftLatch.reset(); layer = KeyboardLayer.LETTERS
        animateSpaceLabel = true
        val width = if (prefs.oneHanded == "center") LayoutParams.MATCH_PARENT else (resources.displayMetrics.widthPixels * .82f).toInt()
        (body.layoutParams as LayoutParams).apply { this.width = width; gravity = when (prefs.oneHanded) { "left" -> Gravity.START; "right" -> Gravity.END; else -> Gravity.CENTER } }
        panel.learningEnabled = learningEnabled && editor == EditorLayout.TEXT
        render()
    }
    fun setCandidates(values: List<String>) {
        candidates = values.take(3)
        bindRail(true)
    }
    fun setClipboardItems(recent: List<String>, pinned: List<String> = emptyList()) {
        clipboardRecent = recent
        clipboardPinned = pinned
        rail.setClipboardVisible(showClipboardButton())
        if (layer == KeyboardLayer.CLIPBOARD) render()
    }
    fun setRecentEmoji(values: List<String>) { recentEmoji = values }

    internal fun keyNameAt(x: Float, y: Float): String? {
        if (!usesTypingPanel() || panel.parent !== body) return null
        return panel.keyAt(x - body.left - panel.left, y - body.top - panel.top)?.id
    }

    internal fun typingLayout(): KeyboardLayout? = if (usesTypingPanel()) panel.layout else null

    private fun render() {
        popups.dismiss()
        sliverPanel = null
        rail.layoutParams = (rail.layoutParams as LayoutParams).apply {
            height = if (keepSuggestionRail()) suggestionRailHeight() else 0
        }
        bindRail(false)
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
            mode, layer, shifted, capsLock, editorLayout, prefs.topRow, prefs.emojiPicker, enterLabel, spaceLabel, false
        )
        val rowHeight = KeyboardGeometry.rowHeightPx(prefs.keyboardSize, isLandscape(), resources.displayMetrics.density, rows.size)
        panel.debug = BuildConfig.DEBUG && prefs.debugOverlay
        panel.playSpaceIntro = animateSpaceLabel
        animateSpaceLabel = false
        panel.bind(rows, rowHeight)
    }

    private fun bindRail(animated: Boolean) {
        val show = layer == KeyboardLayer.LETTERS && editorLayout == EditorLayout.TEXT
        rail.setEmptyTitle(if (show) mode.title else "")
        rail.setClipboardVisible(showClipboardButton())
        rail.setSuggestions(if (show) candidates else emptyList(), animated && show)
    }

    private fun keepSuggestionRail() =
        editorLayout == EditorLayout.TEXT && layer in setOf(KeyboardLayer.LETTERS, KeyboardLayer.NUMBERS, KeyboardLayer.SYMBOLS)
    private fun spaceCaption() = if (editorLayout != EditorLayout.TEXT) "English" else "Akshara - ${mode.title}"

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
        val values = when (emojiCategoryIndex) {
            0 -> recentEmoji
            else -> emojiRepo.categories.getOrNull(emojiCategoryIndex - 1)?.emoji.orEmpty()
        }
        body.addView(emojiCategoryBar(), LayoutParams(LayoutParams.MATCH_PARENT, dp(KeyboardGeometry.EMOJI_TAB_DP)))
        body.addView(emojiSectionTitle(), LayoutParams(LayoutParams.MATCH_PARENT, dp(28)))
        val gridHeight = emojiGridHeight(if (isLandscape()) KeyboardGeometry.EMOJI_ROWS_LANDSCAPE else KeyboardGeometry.EMOJI_ROWS_PORTRAIT)
        val grid = if (values.isEmpty()) {
            textView("Recently used emoji appear here", 14f)
        } else {
            emojiScroller(values)
        }
        body.addView(grid, LayoutParams(LayoutParams.MATCH_PARENT, gridHeight))
        body.addView(emojiBottomBar())
    }

    private fun emojiCategoryBar() = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(bg)
        addView(
            iconButton(org.akshara.ime.R.drawable.ic_key_search, utility, "Search emoji") {
                emojiSearch = true; render()
            },
            LayoutParams(dp(56), LayoutParams.MATCH_PARENT).margins()
        )
        val items = listOf("Recent" to "🕒") + emojiRepo.categories.map { it.name to it.icon }
        items.forEachIndexed { i, item ->
            val selected = !emojiSearch && emojiCategoryIndex == i
            addView(emojiTab(item.second, item.first, selected) {
                emojiSearch = false; emojiCategoryIndex = i; render()
            }, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        }
    }

    private fun emojiBottomBar() = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(bg)
        addView(button("ABC", utility, "Letters") { emojiQuery = ""; emojiSearch = false; layer = KeyboardLayer.LETTERS; render() }, LayoutParams(0, dp(54), 1f).margins())
        addView(button("☺", palette.selected, "Emoji picker active") { }, LayoutParams(0, dp(54), 1f).margins())
        addView(Space(context), LayoutParams(0, dp(54), 3f))
        addView(backspaceButton(), LayoutParams(0, dp(54), 1f).margins())
    }

    private fun emojiSectionTitle() = TextView(context).apply {
        text = when (emojiCategoryIndex) {
            0 -> "Recent emoji"
            else -> emojiRepo.categories.getOrNull(emojiCategoryIndex - 1)?.name ?: "Emoji"
        }
        textSize = 12f
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        setTextColor(ColorUtils.setAlphaComponent(ink, 180))
        setPadding(dp(12), 0, dp(12), 0)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private fun emojiTab(icon: String, description: String, selected: Boolean, click: () -> Unit) = TextView(context).apply {
        text = icon
        textSize = 18f
        gravity = Gravity.CENTER
        includeFontPadding = false
        if (Build.VERSION.SDK_INT >= 28) isFallbackLineSpacing = false
        setTextColor(ink)
        contentDescription = description
        isClickable = true
        isFocusable = true
        background = if (selected) keyBackground(utility) else android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
        setOnClickListener { click() }
        minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
        setPadding(0, dp(6), 0, dp(6))
    }
    private fun showEmojiSearch() {
        body.addView(
            textView(if (emojiQuery.isEmpty()) "Search in English or Sinhala" else emojiQuery, 15f),
            LayoutParams(LayoutParams.MATCH_PARENT, dp(34))
        )
        val sinhalaQuery = SinhalaEngine.transliterate(emojiQuery, InputMode.SMART_PHONETIC)
        val results = (emojiRepo.search(emojiQuery, 64) + emojiRepo.search(sinhalaQuery, 64)).distinct().take(80)
        val gridHeight = emojiGridHeight(2)
        when {
            emojiQuery.isEmpty() -> body.addView(textView("Type a name to find emoji", 13f), LayoutParams(LayoutParams.MATCH_PARENT, gridHeight))
            results.isEmpty() -> body.addView(textView("No emoji found", 13f), LayoutParams(LayoutParams.MATCH_PARENT, gridHeight))
            else -> body.addView(emojiScroller(results), LayoutParams(LayoutParams.MATCH_PARENT, gridHeight))
        }
        listOf("qwertyuiop", "asdfghjkl", "zxcvbnm").forEach { keys ->
            val row = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER }
            keys.forEach { c -> row.addView(button(c.toString(), key, c.toString()) { emojiQuery += c; render() }, LayoutParams(0, dp(40), 1f).margins()) }
            body.addView(row, LayoutParams(LayoutParams.MATCH_PARENT, dp(44)))
        }
        val bottom = LinearLayout(context).apply { orientation = HORIZONTAL }
        bottom.addView(button("ABC", utility, "Letters") { emojiQuery = ""; emojiSearch = false; layer = KeyboardLayer.LETTERS; render() }, LayoutParams(0, dp(48), 1f).margins())
        bottom.addView(button("Space", key, "Search space") { emojiQuery += " "; render() }, LayoutParams(0, dp(48), 3f).margins())
        bottom.addView(iconButton(org.akshara.ime.R.drawable.ic_key_backspace, utility, "Delete search character") { if (emojiQuery.isNotEmpty()) emojiQuery = emojiQuery.dropLast(1); render() }, LayoutParams(0, dp(48), 1f).margins())
        body.addView(bottom)
    }
    private fun emojiScroller(values: List<String>) =
        EmojiBoard.scroller(context, values, ink, prefs.skinTone) { actions.onCharacter(it) }
    private fun showClipboardButton() =
        prefs.clipboardHistory && layer == KeyboardLayer.LETTERS && editorLayout == EditorLayout.TEXT

    private fun bindClipboard() {
        body.clipChildren = true
        body.clipToPadding = true
        val existing = body.getChildAt(0) as? ClipboardBoard
        if (existing != null && body.childCount == 1) {
            existing.configure(clipboardRecent, clipboardPinned)
            return
        }
        body.removeAllViews()
        val board = ClipboardBoard(
            context,
            KeyboardColors(key, utility, ink, palette.dark, palette.highContrast),
            onPaste = { clip ->
                actions.onCharacter(clip)
                layer = KeyboardLayer.LETTERS
                render()
            },
            onBack = { layer = KeyboardLayer.LETTERS; render() },
            onHide = { actions.onHide() },
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
        val height = KeyboardGeometry.keyAreaDp(prefs.keyboardSize, isLandscape()) *
            resources.displayMetrics.density
        body.addView(board, LayoutParams(LayoutParams.MATCH_PARENT, height.toInt()))
    }

    private fun refreshClipboardFromStore() {
        clipboardRecent = clipboardStore.items()
        clipboardPinned = clipboardStore.pinnedItems()
        if (layer == KeyboardLayer.CLIPBOARD) render()
    }

    private fun updateShift() {
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
    private fun navigationBarFallback(): Int {
        val id = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (id != 0) resources.getDimensionPixelSize(id) else 0
    }
    private fun isDark() = palette.dark

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
