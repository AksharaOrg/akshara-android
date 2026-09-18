package org.slashboard.ime.ime

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.BitmapFactory
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
import android.net.Uri
import org.slashboard.ime.BuildConfig
import org.slashboard.ime.data.ClipboardHistoryStore
import org.slashboard.ime.data.EmojiRepository
import org.slashboard.ime.engine.InputMode
import org.slashboard.ime.engine.SinhalaEngine
import org.slashboard.ime.settings.KeyboardPreferences
import kotlin.math.abs

enum class KeyboardLayer { LETTERS, NUMBERS, SYMBOLS, SINHALA_GLYPHS, EMOJI, CLIPBOARD, VOICE, TRANSLATE, FONT_STUDIO, CALCULATOR }
enum class EditorLayout { TEXT, ASCII, EMAIL, URI, NUMBER, SIGNED_NUMBER, DECIMAL, SIGNED_DECIMAL, PHONE, DATETIME }

interface KeyboardActions {
    fun onCharacter(value: String)
    fun onPasteText(text: String) { onCharacter(text) }
    fun onBackspace(word: Boolean = false)
    fun onSpace()
    fun onEnter()
    fun onCandidate(value: String)
    fun onGlobe()
    fun onModeRequested(mode: InputMode)
    fun onHide()
    fun onVoiceInputRequested() {}
    fun onCursorDelta(delta: Int)
    fun onPressFeedback() {}
    fun languageScoreForKey(output: String): Float = 0f
    fun onPreviewDelete(clusters: Int) {}
    fun onCommitPreviewDelete() {}
    fun onCancelPreviewDelete() {}
    fun onToolbarAction(action: String) {}
    fun onMediaSelected(uri: Uri, mimeType: String, description: String = "Media") {}
}

@SuppressLint("ViewConstructor")
class KeyboardView(
    context: Context,
    private val actions: KeyboardActions,
    private var prefs: KeyboardPreferences,
    emojiRepository: EmojiRepository? = null,
    clipboardHistoryStore: ClipboardHistoryStore? = null
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
    private var correctionItems = emptySet<String>()
    private var clipboardRecent = emptyList<String>()
    private var clipboardPinned = emptyList<String>()
    private var recentEmoji = emptyList<String>()
    private var emojiRepo: EmojiRepository = emojiRepository ?: EmojiRepository(context)
    private var clipboardStore: ClipboardHistoryStore = clipboardHistoryStore ?: ClipboardHistoryStore(context)
    private var emojiSearch = false
    private var emojiQuery = ""
    private var emojiCategoryIndex = 0
    private val handler = Handler(Looper.getMainLooper())
    private var palette = KeyboardPaletteResolver.resolve(context, prefs.theme, prefs.highContrast)
    private var bg = palette.background
    private var key = palette.key
    private var utility = palette.utility
    var action = palette.action
    var actionText = palette.actionText
    private var ink = palette.ink
    private var voiceManager: VoiceInputManager? = null
    private val rail = SuggestionRail(
        context, ink, 
        onCandidate = { actions.onCandidate(it) }, 
        onClipboard = { openClipboard() },
        onSettings = { 
            val intent = android.content.Intent(context, org.slashboard.ime.settings.SettingsActivity::class.java).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            context.startActivity(intent)
        },
        onEmoji = {
            layer = KeyboardLayer.EMOJI
            render()
        },
        onVoice = {
            openVoiceTyping()
            actions.onVoiceInputRequested()
        }
    )
    private val body = LinearLayout(context)
    private val homePad = View(context)
    private val popups = KeyPopups(context)
    private var activeTranslateBoard: TranslateBoard? = null
    private val panel = KeyboardPanel(
        context, prefs, popups, object : KeyboardActions {
            override fun onCharacter(value: String) {
                if (layer == KeyboardLayer.TRANSLATE && activeTranslateBoard != null) {
                    activeTranslateBoard?.appendInput(value)
                } else {
                    actions.onCharacter(value)
                }
                if (shiftLatch.shifted && !shiftLatch.capsLock) {
                    shiftLatch.consumeOneShot()
                    bindTyping()
                }
            }
            override fun onBackspace(word: Boolean) {
                if (layer == KeyboardLayer.TRANSLATE && activeTranslateBoard != null) {
                    activeTranslateBoard?.backspace(word)
                } else {
                    actions.onBackspace(word)
                }
            }
            override fun onSpace() {
                if (layer == KeyboardLayer.TRANSLATE && activeTranslateBoard != null) {
                    activeTranslateBoard?.appendInput(" ")
                } else {
                    actions.onSpace()
                }
            }
            override fun onEnter() {
                if (layer == KeyboardLayer.TRANSLATE && activeTranslateBoard != null) {
                    activeTranslateBoard?.commitAndSend()
                } else {
                    actions.onEnter()
                }
            }
            override fun onCandidate(value: String) {
                if (layer == KeyboardLayer.TRANSLATE && activeTranslateBoard != null) {
                    activeTranslateBoard?.appendInput(value)
                } else {
                    actions.onCandidate(value)
                }
            }
            override fun onGlobe() {
                if (layer == KeyboardLayer.TRANSLATE && activeTranslateBoard != null) {
                    activeTranslateBoard?.swapLanguages()
                } else {
                    prefs.useEnglish = !prefs.useEnglish
                    actions.onGlobe()
                    android.widget.Toast.makeText(context, if (prefs.useEnglish) "English" else "සිංහල", android.widget.Toast.LENGTH_SHORT).show()
                    render()
                }
            }
            override fun onModeRequested(mode: InputMode) = actions.onModeRequested(mode)
            override fun onHide() = actions.onHide()
            override fun onCursorDelta(delta: Int) = actions.onCursorDelta(delta)
            override fun onPressFeedback() = actions.onPressFeedback()
            override fun languageScoreForKey(output: String) = actions.languageScoreForKey(output)
            override fun onPreviewDelete(clusters: Int) = actions.onPreviewDelete(clusters)
            override fun onCommitPreviewDelete() = actions.onCommitPreviewDelete()
            override fun onCancelPreviewDelete() = actions.onCancelPreviewDelete()
        },
        KeyboardColors(
            key = key,
            utility = utility,
            ink = ink,
            action = palette.action,
            actionText = palette.actionText,
            dark = palette.dark,
            highContrast = palette.highContrast,
            keyRadiusDp = palette.keyRadiusDp,
            keyOpacity = palette.keyOpacity,
            spaceBarKey = palette.spaceKey,
            spaceBarBorder = palette.spaceBorder,
            keyStyle = palette.keyStyle,
            animationType = palette.animationType,
            borderWidthDp = palette.borderWidthDp,
            borderColor = palette.borderColor,
            glowColor = palette.glowColor,
            typeface = org.slashboard.ime.settings.font.CustomFontManager.getTypeface(context, prefs.keyboardFont)
        ),
        onLayer = { next ->
            if (editorLayout in numericEditors && forceNormalKeyboard) {
                forceNormalKeyboard = false
                render()
            } else {
                layer = next
                render()
            }
        },
        onShift = { updateShift() }
    )
    var forceNormalKeyboard: Boolean = false
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
        rail.onLangToggle = {
            prefs.useEnglish = !prefs.useEnglish
            actions.onGlobe()
            android.widget.Toast.makeText(context, if (prefs.useEnglish) "English" else "සිංහල", android.widget.Toast.LENGTH_SHORT).show()
            render()
        }
        rail.onToolbarAction = {
            when (it) {
                "calculator" -> {
                    layer = KeyboardLayer.CALCULATOR
                    render()
                }
                "astrology" -> {
                    layer = KeyboardLayer.SINHALA_GLYPHS
                    render()
                }
                "font_studio" -> {
                    openFontStudio()
                }
                "translate" -> {
                    openTranslator()
                }
                "one_handed_toggle" -> {
                    toggleOneHanded()
                }
                else -> {
                    actions.onToolbarAction(it)
                }
            }
        }
        rail.onEmojiSelected = { emoji ->
            actions.onCharacter(emoji)
        }
        rail.setRecentEmojis(prefs.recentEmojis)
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
        return runCatching {
            super.dispatchTouchEvent(event)
        }.getOrDefault(true)
    }

    fun applyTheme() {
        palette = KeyboardPaletteResolver.resolve(context, prefs.theme, prefs.highContrast)
        bg = palette.background
        key = palette.key
        utility = palette.utility
        action = palette.action
        actionText = palette.actionText
        ink = palette.ink
        val hasBgImage = !palette.backgroundImagePath.isNullOrEmpty() && java.io.File(palette.backgroundImagePath!!).exists()
        val isTransparent = (prefs.transparentBackground || bg == Color.TRANSPARENT || prefs.theme.contains("transparent")) && !hasBgImage
        if (isTransparent) {
            setBackgroundColor(Color.TRANSPARENT)
            background = null
            body.setBackgroundColor(Color.TRANSPARENT)
            homePad.setBackgroundColor(Color.TRANSPARENT)
            rail.setBackgroundColor(Color.TRANSPARENT)
        } else if (palette.gradientStart != null && palette.gradientEnd != null) {
            val gradient = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(palette.gradientStart!!, palette.gradientEnd!!)
            )
            background = gradient
        } else if (hasBgImage) {
            try {
                val options = android.graphics.BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                android.graphics.BitmapFactory.decodeFile(palette.backgroundImagePath, options)
                var sampleSize = 1
                val maxDim = 1200
                while (options.outWidth / sampleSize > maxDim || options.outHeight / sampleSize > maxDim) {
                    sampleSize *= 2
                }
                options.inJustDecodeBounds = false
                options.inSampleSize = sampleSize
                val bitmap = android.graphics.BitmapFactory.decodeFile(palette.backgroundImagePath, options)
                if (bitmap != null) {
                    val solidBaseColor = if (palette.dark) Color.parseColor("#0F172A") else Color.parseColor("#F8FAFC")
                    val scrimColor = if (palette.dark) Color.argb(125, 0, 0, 0) else Color.argb(55, 255, 255, 255)
                    val bgDrawable = object : android.graphics.drawable.Drawable() {
                        private val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG).apply {
                            if (palette.blurEffect && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                                setRenderEffect(android.graphics.RenderEffect.createBlurEffect(35f, 35f, android.graphics.Shader.TileMode.CLAMP))
                            }
                        }
                        private val src = android.graphics.Rect(0, 0, bitmap.width, bitmap.height)
                        private val dst = android.graphics.Rect()

                        override fun draw(canvas: android.graphics.Canvas) {
                            // 1. Draw solid background base so keyboard NEVER becomes see-through
                            canvas.drawColor(solidBaseColor)

                            // 2. Draw scaled background bitmap (center-crop)
                            val bWidth = bounds.width()
                            val bHeight = bounds.height()
                            if (bWidth > 0 && bHeight > 0) {
                                val scale = maxOf(bWidth.toFloat() / bitmap.width, bHeight.toFloat() / bitmap.height)
                                val scaledW = (bitmap.width * scale).toInt()
                                val scaledH = (bitmap.height * scale).toInt()
                                val left = (bWidth - scaledW) / 2
                                val top = (bHeight - scaledH) / 2
                                dst.set(left, top, left + scaledW, top + scaledH)
                                canvas.drawBitmap(bitmap, src, dst, paint)
                            }

                            // 3. Draw protective scrim so keys and typography remain crystal clear
                            canvas.drawColor(scrimColor)
                        }

                        override fun setAlpha(alpha: Int) {}
                        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}
                        override fun getOpacity() = android.graphics.PixelFormat.OPAQUE
                    }
                    background = bgDrawable
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        setRenderEffect(null)
                    }
                } else {
                    setBackgroundColor(bg)
                }
            } catch (e: Throwable) {
                e.printStackTrace()
                setBackgroundColor(bg)
            }
        } else {
            setBackgroundColor(bg)
        }

        val effectiveOpacity = if (hasBgImage) {
            if (palette.keyOpacity < 0.85f) 0.90f else palette.keyOpacity
        } else if (isTransparent) {
            if (palette.keyOpacity >= 1.0f) 0.68f else palette.keyOpacity
        } else palette.keyOpacity

        val effectiveBorderWidth = if (hasBgImage) {
            if (palette.borderWidthDp == 0f) 1f else palette.borderWidthDp
        } else if (isTransparent && palette.borderWidthDp == 0f) 1f else palette.borderWidthDp

        val effectiveBorderColor = if (hasBgImage) {
            palette.borderColor ?: if (palette.dark) Color.argb(80, 255, 255, 255) else Color.argb(60, 0, 0, 0)
        } else if (isTransparent && palette.borderColor == null) {
            if (palette.dark) Color.argb(60, 255, 255, 255) else Color.argb(40, 0, 0, 0)
        } else palette.borderColor

        val colors = KeyboardColors(
            key = key,
            utility = utility,
            ink = ink,
            action = action,
            actionText = actionText,
            dark = palette.dark,
            highContrast = palette.highContrast,
            keyRadiusDp = palette.keyRadiusDp,
            keyOpacity = effectiveOpacity,
            spaceBarKey = palette.spaceKey,
            spaceBarBorder = palette.spaceBorder,
            keyStyle = palette.keyStyle,
            animationType = palette.animationType,
            borderWidthDp = effectiveBorderWidth,
            borderColor = effectiveBorderColor,
            glowColor = palette.glowColor,
            typeface = org.slashboard.ime.settings.font.CustomFontManager.getTypeface(context, prefs.keyboardFont)
        )
        panel.updateColors(colors)
        rail.updateInk(ink)
    }

    fun configure(mode: InputMode, offerGlobe: Boolean, enter: String, editor: EditorLayout = EditorLayout.TEXT) {
        applyTheme()
        forceNormalKeyboard = false
        this.mode = mode; enterLabel = enter; editorLayout = editor; this.offerGlobe = offerGlobe
        shiftLatch.reset(); layer = KeyboardLayer.LETTERS
        animateSpaceLabel = true
        rail.configureToolbar(prefs)
        val width = if (prefs.oneHanded == "center") LayoutParams.MATCH_PARENT else (resources.displayMetrics.widthPixels * .82f).toInt()
        (body.layoutParams as LayoutParams).apply { this.width = width; gravity = when (prefs.oneHanded) { "left" -> Gravity.START; "right" -> Gravity.END; else -> Gravity.CENTER } }
        panel.learningEnabled = learningEnabled && editor == EditorLayout.TEXT
        render()
    }

    fun reloadPreferences(newPrefs: KeyboardPreferences? = null) {
        if (newPrefs != null) {
            this.prefs = newPrefs
        }
        panel.updatePreferences(this.prefs)
        applyTheme()
        rail.configureToolbar(this.prefs)
        val width = if (prefs.oneHanded == "center") LayoutParams.MATCH_PARENT else (resources.displayMetrics.widthPixels * .82f).toInt()
        (body.layoutParams as LayoutParams).apply { this.width = width; gravity = when (prefs.oneHanded) { "left" -> Gravity.START; "right" -> Gravity.END; else -> Gravity.CENTER } }
        render()
    }
    fun setCandidates(values: List<String>, corrections: Set<String> = emptySet()) {
        candidates = values.take(3)
        correctionItems = corrections
        bindRail(true)
    }
    fun setOtpAvailable(available: Boolean, code: String? = null) {
        rail.setOtpAvailable(available, code)
    }
    fun setIncognito(active: Boolean) {
        rail.setIncognito(active)
    }
    fun toggleOneHanded() {
        val next = when (prefs.oneHanded) {
            "center" -> "right"
            "right" -> "left"
            else -> "center"
        }
        prefs.oneHanded = next
        val width = if (prefs.oneHanded == "center") LayoutParams.MATCH_PARENT else (resources.displayMetrics.widthPixels * .82f).toInt()
        (body.layoutParams as LayoutParams).apply {
            this.width = width
            gravity = when (prefs.oneHanded) {
                "left" -> Gravity.START
                "right" -> Gravity.END
                else -> Gravity.CENTER
            }
        }
        render()
        val label = when (next) {
            "right" -> "One-handed: Right"
            "left" -> "One-handed: Left"
            else -> "Standard Keyboard"
        }
        android.widget.Toast.makeText(context, label, android.widget.Toast.LENGTH_SHORT).show()
    }
    fun setClipboardItems(recent: List<String>, pinned: List<String> = emptyList()) {
        clipboardRecent = recent
        clipboardPinned = pinned
        rail.setClipboardVisible(showClipboardButton())
        if (layer == KeyboardLayer.CLIPBOARD) render()
    }
    fun setRecentEmoji(values: List<String>) {
        recentEmoji = values
        rail.setRecentEmojis(values)
        if (layer == KeyboardLayer.EMOJI) render()
        else if (layer == KeyboardLayer.LETTERS && (prefs.topRow == "emoji" || prefs.topRow == "both")) {
            bindTyping()
        }
    }
    fun updateRepositories(emojiRepository: EmojiRepository, clipboardHistoryStore: ClipboardHistoryStore) {
        emojiRepo = emojiRepository
        clipboardStore = clipboardHistoryStore
    }

    internal fun keyNameAt(x: Float, y: Float): String? {
        if (!usesTypingPanel() || panel.parent !== body) return null
        return panel.keyAt(x - body.left - panel.left, y - body.top - panel.top)?.id
    }

    internal fun typingLayout(): KeyboardLayout? = if (usesTypingPanel()) panel.layout else null

    private fun createSwipeableEmojiBar(emojis: List<String>): View {
        val scroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = OVER_SCROLL_NEVER
            clipChildren = false
            clipToPadding = false
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(dp(4), dp(2), dp(4), dp(2))
        }

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false
            clipToPadding = false
        }

        // Fast shortcut to full emoji picker at the start of the bar
        val moreBtn = iconButton(org.slashboard.ime.R.drawable.ic_key_emoji, utility, "More emojis") {
            actions.onPressFeedback()
            layer = KeyboardLayer.EMOJI
            render()
        }
        val moreLp = LinearLayout.LayoutParams(dp(36), dp(36)).apply {
            setMargins(dp(2), dp(2), dp(4), dp(2))
        }
        row.addView(moreBtn, moreLp)

        val defaultPopular = listOf(
            "😂", "❤️", "🙏", "👍", "😍", "🤣", "😊", "🔥", "🥺", "🥰",
            "😭", "✨", "🎉", "👏", "😁", "😘", "😎", "😅", "🇱🇰", "💯",
            "🙌", "💖", "🤝", "👌", "💪", "💐"
        )
        val combinedList = (emojis + defaultPopular).distinct().take(28)

        combinedList.forEach { emoji ->
            val item = TextView(context).apply {
                text = emoji
                textSize = 20f
                gravity = Gravity.CENTER
                includeFontPadding = false
                isClickable = true
                isFocusable = true
                val radius = dp(8).toFloat()
                val normalBg = GradientDrawable().apply {
                    cornerRadius = radius
                    setColor(key)
                }
                val ripple = RippleDrawable(
                    ColorStateList.valueOf(ColorUtils.setAlphaComponent(ink, 40)),
                    normalBg,
                    null
                )
                background = ripple

                setOnClickListener {
                    actions.onPressFeedback()
                    actions.onCharacter(emoji)
                }
                setOnLongClickListener {
                    actions.onPressFeedback()
                    layer = KeyboardLayer.EMOJI
                    render()
                    true
                }
            }
            val lp = LinearLayout.LayoutParams(dp(40), dp(36)).apply {
                setMargins(dp(2), dp(2), dp(2), dp(2))
            }
            row.addView(item, lp)
        }

        scroll.addView(row, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return scroll
    }

    private fun standardContentHeight(): Int {
        val rows = KeyboardLayoutFactory.typingRows(
            mode, KeyboardLayer.LETTERS, false, false, EditorLayout.TEXT, prefs.topRow, prefs.emojiPicker, enterLabel, " ", false, prefs.useEnglish, prefs.smartKeyModifiers,
            recentEmoji.ifEmpty { prefs.recentEmojis }
        )
        val rowHeight = KeyboardGeometry.rowHeightPx(prefs.keyboardSize, isLandscape(), resources.displayMetrics.density, rows.size)
        var total = rows.sumOf { (rowHeight * it.heightFactor).toDouble() }.toInt()
        if (prefs.topRow == "emoji" || prefs.topRow == "both") {
            total += dp(40)
        }
        return total.coerceAtLeast(dp(220))
    }

    private fun render() {
        popups.dismiss()
        sliverPanel = null
        val showRail = keepSuggestionRail()
        rail.visibility = if (showRail) View.VISIBLE else View.GONE
        rail.layoutParams = (rail.layoutParams as LayoutParams).apply {
            height = if (showRail) suggestionRailHeight() else 0
        }
        bindRail(false)
        if (!forceNormalKeyboard && editorLayout in numericEditors) {
            body.removeAllViews()
            renderNativePad()
            return
        }
        when (layer) {
            KeyboardLayer.LETTERS, KeyboardLayer.NUMBERS, KeyboardLayer.SYMBOLS, KeyboardLayer.SINHALA_GLYPHS -> bindTyping()
            KeyboardLayer.EMOJI -> { body.removeAllViews(); renderEmoji() }
            KeyboardLayer.CLIPBOARD -> bindClipboard()
            KeyboardLayer.VOICE -> bindVoice()
            KeyboardLayer.TRANSLATE -> bindTranslate()
            KeyboardLayer.FONT_STUDIO -> bindFontStudio()
            KeyboardLayer.CALCULATOR -> { body.removeAllViews(); renderCalculator() }
        }
    }

    private fun bindTyping() {
        body.removeAllViews()
        val showTopRow = editorLayout !in numericEditors && layer == KeyboardLayer.LETTERS
        if (showTopRow && (prefs.topRow == "emoji" || prefs.topRow == "both")) {
            val emojiList = recentEmoji.ifEmpty { prefs.recentEmojis }
            body.addView(createSwipeableEmojiBar(emojiList), LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }
        body.addView(panel, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        val spaceLabel = spaceCaption()
        val rows = KeyboardLayoutFactory.typingRows(
            mode, layer, shifted, capsLock, editorLayout, prefs.topRow, prefs.emojiPicker, enterLabel, spaceLabel, false, prefs.useEnglish, prefs.smartKeyModifiers,
            recentEmoji.ifEmpty { prefs.recentEmojis }
        )
        val rowHeight = KeyboardGeometry.rowHeightPx(prefs.keyboardSize, isLandscape(), resources.displayMetrics.density, rows.size)
        panel.bindContext(mode.name, layer.name, prefs.topRow, prefs.useEnglish, shifted, capsLock)
        panel.debug = BuildConfig.DEBUG && prefs.debugOverlay
        panel.playSpaceIntro = animateSpaceLabel
        animateSpaceLabel = false
        panel.bind(rows, rowHeight)
    }

    private fun bindRail(animated: Boolean) {
        val showRail = keepSuggestionRail()
        rail.visibility = if (showRail) View.VISIBLE else View.GONE
        if (!showRail) return
        val showSuggestions = layer == KeyboardLayer.LETTERS && (editorLayout == EditorLayout.TEXT || editorLayout == EditorLayout.EMAIL || editorLayout == EditorLayout.URI)
        rail.configureToolbar(prefs)
        rail.setEmptyTitle(if (showSuggestions) if (prefs.useEnglish) "English" else mode.title else "")
        rail.setLanguage(prefs.useEnglish)
        rail.setClipboardVisible(showClipboardButton())
        rail.setSuggestions(if (showSuggestions) candidates else emptyList(), animated && showSuggestions, correctionItems)
    }

    private fun keepSuggestionRail() =
        layer != KeyboardLayer.TRANSLATE && layer != KeyboardLayer.EMOJI && layer != KeyboardLayer.CALCULATOR
    private fun spaceCaption(): String {
        val custom = prefs.customSpacebarText.trim().take(14)
        if (custom.isNotEmpty()) return custom
        return if (prefs.useEnglish) "English" else "සිංහල"
    }

    private fun renderNativePad() {
        val totalKeyPadHeight = standardContentHeight()
        val numRows = if (editorLayout == EditorLayout.PHONE) 5 else 4
        val targetRowHeight = totalKeyPadHeight / numRows
        val targetKeyHeight = (targetRowHeight - dp(6)).coerceAtLeast(dp(38))
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
            values.forEachIndexed { colIndex, value ->
                if (value.isEmpty()) {
                    if (rowIndex == 3 && colIndex == 0) {
                        // Toggle to full keyboard
                        row.addView(button("ABC", utility, "Switch to normal keyboard") {
                            forceNormalKeyboard = true
                            render()
                        }, LayoutParams(0, targetKeyHeight, 1f).keyMargins())
                    } else {
                        row.addView(Space(context), LayoutParams(0, targetKeyHeight, 1f).keyMargins())
                    }
                }
                else row.addView(button(value, key, value) { actions.onCharacter(if (value == "−") "-" else value) }, LayoutParams(0, targetKeyHeight, 1f).keyMargins())
            }
            val action = when (rowIndex) {
                0 -> backspaceButton()
                1 -> button("C", utility, "Clear input") { actions.onBackspace(true) }
                2 -> Space(context)
                3 -> enterButton()
                else -> Space(context)
            }
            row.addView(action, LayoutParams(0, targetKeyHeight, 1f).keyMargins())
            body.addView(row, LayoutParams(LayoutParams.MATCH_PARENT, targetRowHeight))
        }
        if (editorLayout == EditorLayout.PHONE) {
            val extras = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER }
            extras.addView(button("*", utility, "Asterisk") { actions.onCharacter("*") }, LayoutParams(0, targetKeyHeight, 1f).keyMargins())
            extras.addView(button("(", utility, "Left parenthesis") { actions.onCharacter("(") }, LayoutParams(0, targetKeyHeight, 1f).keyMargins())
            extras.addView(button(")", utility, "Right parenthesis") { actions.onCharacter(")") }, LayoutParams(0, targetKeyHeight, 1f).keyMargins())
            body.addView(extras, LayoutParams(LayoutParams.MATCH_PARENT, targetRowHeight))
        }
    }

    private fun renderEmoji() {
        body.clipChildren = true
        body.clipToPadding = true
        if (emojiSearch) {
            showEmojiSearch()
            return
        }
        val fullHeight = standardContentHeight() + suggestionRailHeight()
        val emojiBoardView = EmojiBoardView(
            context = context,
            kbColors = KeyboardColors(key, utility, ink, palette.action, palette.actionText, palette.dark, palette.highContrast, palette.keyRadiusDp, palette.keyOpacity),
            emojiRepo = emojiRepo,
            recentEmojis = recentEmoji.ifEmpty { prefs.recentEmojis },
            skinTone = prefs.skinTone,
            onPick = { emoji ->
                actions.onCharacter(emoji)
            },
            onSearchClick = {
                emojiSearch = true
                render()
            },
            onBackspace = {
                actions.onBackspace()
            },
            onClose = {
                layer = KeyboardLayer.LETTERS
                render()
            }
        )
        body.addView(emojiBoardView, LayoutParams(LayoutParams.MATCH_PARENT, fullHeight))
    }

    private fun renderCalculator() {
        body.clipChildren = true
        body.clipToPadding = true
        val fullHeight = standardContentHeight() + suggestionRailHeight()
        val calcView = CalculatorBoard(
            context = context,
            kbColors = KeyboardColors(key, utility, ink, palette.action, palette.actionText, palette.dark, palette.highContrast, palette.keyRadiusDp, palette.keyOpacity),
            onInsert = { textToInsert ->
                actions.onPasteText(textToInsert)
                layer = KeyboardLayer.LETTERS
                render()
            },
            onClose = {
                layer = KeyboardLayer.LETTERS
                render()
            }
        )
        body.addView(calcView, LayoutParams(LayoutParams.MATCH_PARENT, fullHeight))
    }

    fun openCalculator() {
        layer = KeyboardLayer.CALCULATOR
        render()
    }

    private fun emojiCategoryBar() = HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        setBackgroundColor(bg)
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(
                iconButton(org.slashboard.ime.R.drawable.ic_key_search, utility, "Search emoji") {
                    emojiSearch = true; render()
                },
                LinearLayout.LayoutParams(dp(44), dp(44)).apply {
                    setMargins(dp(2), dp(2), dp(2), dp(2))
                }
            )
            val items = listOf("Recent" to "🕒") + emojiRepo.categories.map { it.name to it.icon }
            items.forEachIndexed { i, item ->
                val selected = !emojiSearch && emojiCategoryIndex == i
                addView(emojiTab(item.second, item.first, selected) {
                    emojiSearch = false; emojiCategoryIndex = i; render()
                }, LinearLayout.LayoutParams(dp(44), dp(44)).apply {
                    setMargins(dp(1), dp(2), dp(1), dp(2))
                })
            }
        }
        addView(row, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun emojiBottomBar() = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(bg)
        addView(button("ABC", utility, "Letters") { emojiQuery = ""; emojiSearch = false; layer = KeyboardLayer.LETTERS; render() }, LayoutParams(0, dp(54), 1f).margins())
        addView(button("☺", palette.selected, "Emoji picker active") { }, LayoutParams(0, dp(54), 1f).margins())
        addView(Space(context), LayoutParams(0, dp(54), 2f))
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
        bottom.addView(iconButton(org.slashboard.ime.R.drawable.ic_key_backspace, utility, "Delete search character") { if (emojiQuery.isNotEmpty()) emojiQuery = emojiQuery.dropLast(1); render() }, LayoutParams(0, dp(48), 1f).margins())
        body.addView(bottom)
    }
    private fun emojiScroller(values: List<String>) =
        EmojiBoard.scroller(context, values, ink, prefs.skinTone) { actions.onCharacter(it) }
    private fun showClipboardButton() =
        prefs.clipboardHistory && layer == KeyboardLayer.LETTERS

    fun openClipboard() {
        runCatching {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
            val clip = cm?.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).coerceToText(context)?.toString()
                if (!text.isNullOrBlank()) {
                    clipboardStore.add(text)
                }
            }
        }
        refreshClipboardFromStore()
        layer = KeyboardLayer.CLIPBOARD
        render()
    }

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
            KeyboardColors(key, utility, ink, palette.action, palette.actionText, palette.dark, palette.highContrast, palette.keyRadiusDp, palette.keyOpacity),
            onPaste = { clip ->
                actions.onPasteText(clip)
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
        val height = standardContentHeight()
        body.addView(board, LayoutParams(LayoutParams.MATCH_PARENT, height))
    }

    fun setVoiceManager(manager: VoiceInputManager) {
        this.voiceManager = manager
    }

    fun openVoiceTyping() {
        layer = KeyboardLayer.VOICE
        render()
    }

    private fun bindVoice() {
        val existing = body.getChildAt(0) as? VoiceTypingBoard
        if (existing != null) {
            existing.updateLanguage(prefs.useEnglish)
            return
        }
        body.removeAllViews()
        val board = VoiceTypingBoard(
            context = context,
            colors = KeyboardColors(key, utility, ink, palette.action, palette.actionText, palette.dark, palette.highContrast, palette.keyRadiusDp, palette.keyOpacity),
            actions = actions,
            useEnglish = prefs.useEnglish,
            onDismiss = {
                layer = KeyboardLayer.LETTERS
                render()
            },
            onToggleLanguage = {
                prefs.useEnglish = !prefs.useEnglish
                actions.onToolbarAction("lang_toggle")
                (body.getChildAt(0) as? VoiceTypingBoard)?.updateLanguage(prefs.useEnglish)
            }
        )
        voiceManager?.let {
            board.attachVoiceManager(it, prefs.useEnglish)
        }
        val height = standardContentHeight()
        body.addView(board, LayoutParams(LayoutParams.MATCH_PARENT, height))
        board.alpha = 0f
        board.animate().alpha(1f).setDuration(160).start()
    }

    fun openFontStudio() {
        layer = if (layer == KeyboardLayer.FONT_STUDIO) KeyboardLayer.LETTERS else KeyboardLayer.FONT_STUDIO
        render()
    }

    private fun bindFontStudio() {
        body.removeAllViews()
        val board = FontBoard(
            context = context,
            colors = KeyboardColors(key, utility, ink, palette.action, palette.actionText, palette.dark, palette.highContrast, palette.keyRadiusDp, palette.keyOpacity),
            prefs = prefs,
            onFontSelected = { fontId ->
                applyTheme()
                bindRail(false)
            },
            onDismiss = {
                layer = KeyboardLayer.LETTERS
                render()
            }
        )
        val height = standardContentHeight()
        body.addView(board, LayoutParams(LayoutParams.MATCH_PARENT, height))
        board.alpha = 0f
        board.animate().alpha(1f).setDuration(160).start()
    }

    fun openTranslator() {
        layer = KeyboardLayer.TRANSLATE
        render()
    }

    private fun bindTranslate() {
        body.removeAllViews()
        val board = TranslateBoard(
            context = context,
            colors = KeyboardColors(key, utility, ink, palette.action, palette.actionText, palette.dark, palette.highContrast, palette.keyRadiusDp, palette.keyOpacity),
            actions = actions,
            onDismiss = {
                activeTranslateBoard?.release()
                activeTranslateBoard = null
                layer = KeyboardLayer.LETTERS
                render()
            },
            onLanguageSwapped = { useEnglish ->
                prefs.useEnglish = useEnglish
                val spaceLabel = spaceCaption()
                val rows = KeyboardLayoutFactory.typingRows(
                    mode, KeyboardLayer.LETTERS, shifted, capsLock, editorLayout, prefs.topRow, prefs.emojiPicker, enterLabel, spaceLabel, false, prefs.useEnglish, prefs.smartKeyModifiers,
                    recentEmoji.ifEmpty { prefs.recentEmojis }
                )
                val rowHeight = KeyboardGeometry.rowHeightPx(prefs.keyboardSize, isLandscape(), resources.displayMetrics.density, rows.size)
                panel.bindContext(mode.name, KeyboardLayer.LETTERS.name, prefs.topRow, prefs.useEnglish, shifted, capsLock)
                panel.bind(rows, rowHeight)
            }
        )
        activeTranslateBoard = board
        body.addView(board, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        // Also add keyboard panel below the translate bar
        body.addView(panel, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        val spaceLabel = spaceCaption()
        val rows = KeyboardLayoutFactory.typingRows(
            mode, KeyboardLayer.LETTERS, shifted, capsLock, editorLayout, prefs.topRow, prefs.emojiPicker, enterLabel, spaceLabel, false, prefs.useEnglish, prefs.smartKeyModifiers,
            recentEmoji.ifEmpty { prefs.recentEmojis }
        )
        val rowHeight = KeyboardGeometry.rowHeightPx(prefs.keyboardSize, isLandscape(), resources.displayMetrics.density, rows.size)
        panel.bindContext(mode.name, KeyboardLayer.LETTERS.name, prefs.topRow, prefs.useEnglish, shifted, capsLock)
        panel.debug = BuildConfig.DEBUG && prefs.debugOverlay
        panel.playSpaceIntro = false
        panel.bind(rows, rowHeight)
    }

    private fun refreshClipboardFromStore() {
        clipboardRecent = clipboardStore.items()
        clipboardPinned = clipboardStore.pinnedItems()
        val existing = body.getChildAt(0) as? ClipboardBoard
        if (existing != null && layer == KeyboardLayer.CLIPBOARD) {
            existing.configure(clipboardRecent, clipboardPinned)
        } else if (layer == KeyboardLayer.CLIPBOARD) {
            render()
        }
    }

    private fun updateShift() {
        shiftLatch.tap(android.os.SystemClock.elapsedRealtime())
        bindTyping()
    }
    private fun backspaceButton(): ImageButton {
        val b = iconButton(org.slashboard.ime.R.drawable.ic_key_backspace, utility, "Delete") { }
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
        "↵" -> iconButton(org.slashboard.ime.R.drawable.ic_key_enter, utility, "Enter") { actions.onEnter() }
        "⌕" -> iconButton(org.slashboard.ime.R.drawable.ic_key_search, utility, "Enter") { actions.onEnter() }
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
    private fun usesTypingPanel() = (forceNormalKeyboard || editorLayout !in numericEditors) && layer != KeyboardLayer.EMOJI && layer != KeyboardLayer.CLIPBOARD && layer != KeyboardLayer.VOICE && layer != KeyboardLayer.TRANSLATE && layer != KeyboardLayer.FONT_STUDIO && layer != KeyboardLayer.CALCULATOR
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
        val numbers = listOf("1234567890".map(Char::toString), listOf("@","#","₨","_","&","-","+","(",")","/"), listOf("*","\"","'",":",";","!","?"))
        val symbols = listOf(listOf("~","`","|","•","√","π","÷","×","¶","∆"), listOf("£","€","$","¢","^","°","=","{","}","\\"), listOf("%","©","®","™","✓","[","]"))
        val sinhalaGlyphs = listOf(
            listOf("𑇡", "𑇢", "𑇣", "𑇤", "𑇥", "𑇦", "𑇧", "𑇨", "𑇩", "𑇪"),
            listOf("𑇫", "𑇬", "𑇭", "𑇮", "𑇯", "𑇰", "𑇳", "𑇴", "෦", "෴"),
            listOf("♈", "♉", "♊", "♋", "♌", "♍", "♎", "♏", "♐", "♑", "♒", "♓")
        )
        val numericEditors = setOf(EditorLayout.NUMBER, EditorLayout.SIGNED_NUMBER, EditorLayout.DECIMAL, EditorLayout.SIGNED_DECIMAL, EditorLayout.PHONE, EditorLayout.DATETIME)
    }
}
