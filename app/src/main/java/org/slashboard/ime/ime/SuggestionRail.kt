package org.slashboard.ime.ime

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import org.slashboard.ime.R
import org.slashboard.ime.settings.KeyboardPreferences

internal class SuggestionRail(
    context: Context,
    private var ink: Int,
    private val onCandidate: (String) -> Unit,
    private val onClipboard: () -> Unit,
    private val onSettings: () -> Unit,
    private val onEmoji: () -> Unit,
    private val onVoice: () -> Unit
) : FrameLayout(context) {
    var keySliver = 0
    var onLangToggle: (() -> Unit)? = null
    var onToolbarAction: ((String) -> Unit)? = null
    var onEmojiSelected: ((String) -> Unit)? = null
    private var recentEmojis: List<String> = emptyList()
    private val chips = Array(3) { MorphChip(context, ink) }
    private val chipRow = LinearLayout(context)
    private val emptyScroll = HorizontalScrollView(context)
    private val emptyRow = LinearLayout(context)
    private var currentPrefs: KeyboardPreferences? = null
    private var isOtpAvailable = false
    private var currentOtpCode: String? = null
    private var isIncognitoActive = false
    private var isClipboardRequested = false
    private var isEnglishLanguage = false

    private val undoBtn = createCircleIconButton(R.drawable.ic_key_undo, "Undo") { onToolbarAction?.invoke("undo") }
    private val redoBtn = createCircleIconButton(R.drawable.ic_key_redo, "Redo") { onToolbarAction?.invoke("redo") }
    private val astrologyBtn = createCircleIconButton(R.drawable.ic_key_astrology, "Sinhala & Astrology Glyphs") { onToolbarAction?.invoke("astrology") }
    private val fontStudioBtn = createCircleIconButton(R.drawable.ic_key_font_studio, "Font Studio") { onToolbarAction?.invoke("font_studio") }
    private val translateBtn = createCircleIconButton(R.drawable.ic_key_translate, "Translator") { onToolbarAction?.invoke("translate") }
    private val fontBtn = createCircleIconButton(R.drawable.ic_key_font, "Convert to FM") { onToolbarAction?.invoke("fm") }
    private val calcBtn = createCircleIconButton(R.drawable.ic_key_calc, "Calculator") { onToolbarAction?.invoke("calculator") }
    private val templatesBtn = createCircleIconButton(R.drawable.ic_key_template, "Email & Message Templates") { onToolbarAction?.invoke("templates") }
    private val notesBtn = createCircleIconButton(R.drawable.ic_key_notes, "Encrypted Vault") { onToolbarAction?.invoke("notes") }
    private val otpBtn = createCircleIconButton(R.drawable.ic_key_otp, "Paste OTP") { onToolbarAction?.invoke("otp") }
    private val singlishBtn = createCircleIconButton(R.drawable.ic_key_singlish, "Singlish to Sinhala Bulk Converter") { onToolbarAction?.invoke("singlish_bulk") }
    private val whatsappBtn = createCircleIconButton(R.drawable.ic_key_whatsapp, "WhatsApp Quick Chat") { onToolbarAction?.invoke("whatsapp_quick") }
    private val caseConvertBtn = createCircleIconButton(R.drawable.ic_key_case, "Case Converter") { onToolbarAction?.invoke("case_convert") }
    private val formatBtn = createCircleIconButton(R.drawable.ic_key_format, "Markdown Bold/Italic") { onToolbarAction?.invoke("format") }
    private val devModeBtn = createCircleIconButton(R.drawable.ic_key_terminal, "Developer Keys") { onToolbarAction?.invoke("dev_mode") }
    private val oneHandedBtn = createCircleIconButton(R.drawable.ic_key_one_handed, "One-Handed Mode") { onToolbarAction?.invoke("one_handed_toggle") }
    private val incognitoIndicator = ImageView(context).apply {
        setImageResource(R.drawable.ic_key_incognito)
        imageTintList = ColorStateList.valueOf(ink)
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        setPadding(dp(6), dp(6), dp(6), dp(6))
        contentDescription = "Incognito Mode Active"
        visibility = GONE
    }
    private val clipboard = createCircleIconButton(R.drawable.ic_key_clipboard, "Clipboard History") { onClipboard() }
    private val settings = createCircleIconButton(R.drawable.ic_key_settings, "Settings") { onSettings() }
    private val emojiSwitch = createCircleIconButton(R.drawable.ic_key_emoji, "Emoji") { onEmoji() }
    private val voiceBtn = createCircleIconButton(R.drawable.ic_key_mic, "Voice Input") { onVoice() }

    private val langToggle = TextView(context).apply {
        textSize = 11f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        setTextColor(ink)
        gravity = Gravity.CENTER
        background = circularRippleBackground(ink)
        text = "සිං"
        isClickable = true
        isFocusable = true
        setOnClickListener { onLangToggle?.invoke() }
    }

    private var values: List<String?> = listOf(null, null, null)

    init {
        clipChildren = true
        clipToPadding = true

        // Chip Row for Word Suggestions
        chipRow.orientation = LinearLayout.HORIZONTAL
        chipRow.clipChildren = true
        chipRow.clipToPadding = true
        chips.forEachIndexed { index, chip ->
            if (index > 0) chipRow.addView(divider())
            chipRow.addView(chip, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        }
        addView(chipRow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
            marginStart = dp(12)
            marginEnd = dp(12)
        })

        // Scrollable Toolbar Row with Equal Spacing & Circular Icons
        emptyRow.orientation = LinearLayout.HORIZONTAL
        emptyRow.gravity = Gravity.CENTER_VERTICAL
        emptyRow.clipChildren = true
        emptyRow.clipToPadding = true
        emptyRow.setPadding(dp(4), 0, dp(4), 0)

        emptyScroll.isHorizontalScrollBarEnabled = false
        emptyScroll.overScrollMode = OVER_SCROLL_NEVER
        emptyScroll.clipChildren = true
        emptyScroll.clipToPadding = true
        emptyScroll.isFillViewport = true
        emptyScroll.addView(emptyRow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        addView(emptyScroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        populateDefaultEmptyRow()
        showEmpty(true)
    }

    private fun circularBackground(inkColor: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(ColorUtils.setAlphaComponent(inkColor, 20))
        setStroke(dp(1), ColorUtils.setAlphaComponent(inkColor, 60))
    }

    private fun circularRippleBackground(inkColor: Int): Drawable {
        val bgShape = circularBackground(inkColor)
        val mask = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.WHITE)
        }
        return RippleDrawable(
            ColorStateList.valueOf(ColorUtils.setAlphaComponent(inkColor, 80)),
            bgShape,
            mask
        )
    }

    private fun createCircleIconButton(resId: Int, desc: String, onClick: () -> Unit) = ImageView(context).apply {
        setImageResource(resId)
        imageTintList = ColorStateList.valueOf(ink)
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        setPadding(dp(6), dp(6), dp(6), dp(6))
        contentDescription = desc
        background = circularRippleBackground(ink)
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }

    private fun wrapIconSlot(view: View): View {
        if (view.parent != null) {
            (view.parent as? ViewGroup)?.removeView(view)
        }
        view.visibility = VISIBLE
        val container = FrameLayout(context).apply {
            clipChildren = true
            clipToPadding = true
        }
        val iconLp = FrameLayout.LayoutParams(dp(30), dp(30)).apply {
            gravity = Gravity.CENTER
        }
        container.addView(view, iconLp)
        return container
    }

    private fun populateDefaultEmptyRow() {
        emptyRow.removeAllViews()
        val defaultViews = mutableListOf<View>()
        if (isIncognitoActive) {
            defaultViews.add(incognitoIndicator)
        }
        defaultViews.addAll(listOf(
            langToggle,
            fontStudioBtn,
            singlishBtn,
            whatsappBtn,
            calcBtn,
            undoBtn,
            redoBtn,
            astrologyBtn,
            fontBtn,
            translateBtn,
            oneHandedBtn,
            emojiSwitch,
            clipboard,
            settings
        ))
        for (view in defaultViews) {
            val container = wrapIconSlot(view)
            val lp = LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply {
                gravity = Gravity.CENTER_VERTICAL
            }
            emptyRow.addView(container, lp)
        }
    }

    fun configureToolbar(prefs: KeyboardPreferences) {
        this.currentPrefs = prefs
        val rawIcons = prefs.toolbarIcons
        val enabledIcons = rawIcons.split(",").map { it.trim() }.filter { it.isNotEmpty() }

        emptyRow.removeAllViews()

        if (isIncognitoActive) {
            val container = wrapIconSlot(incognitoIndicator)
            val lp = LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply {
                gravity = Gravity.CENTER_VERTICAL
            }
            emptyRow.addView(container, lp)
        }

        val toolViews = mapOf(
            "lang_toggle" to langToggle,
            "font_studio" to fontStudioBtn,
            "singlish" to singlishBtn,
            "whatsapp" to whatsappBtn,
            "case_convert" to caseConvertBtn,
            "format" to formatBtn,
            "dev_mode" to devModeBtn,
            "calculator" to calcBtn,
            "templates" to templatesBtn,
            "notes" to notesBtn,
            "emoji" to emojiSwitch,
            "voice" to voiceBtn,
            "undo" to undoBtn,
            "redo" to redoBtn,
            "astrology" to astrologyBtn,
            "fm" to fontBtn,
            "translate" to translateBtn,
            "one_handed" to oneHandedBtn,
            "otp" to otpBtn,
            "clipboard" to clipboard,
            "settings" to settings
        )

        for (id in enabledIcons) {
            val view = toolViews[id] ?: continue
            if (id == "otp" && !isOtpAvailable) continue
            if (id == "emoji" && !prefs.emojiPicker) continue

            val container = wrapIconSlot(view)
            val lp = LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply {
                gravity = Gravity.CENTER_VERTICAL
            }
            emptyRow.addView(container, lp)
        }

        // If no icons were selected, fall back to default
        if (emptyRow.childCount == 0) {
            populateDefaultEmptyRow()
        }
    }

    fun setRecentEmojis(emojis: List<String>) {
        this.recentEmojis = emojis
    }

    fun setEmptyTitle(title: String) {
        // Toolbar icons are displayed when idle
    }

    fun setLanguage(isEnglish: Boolean) {
        this.isEnglishLanguage = isEnglish
        langToggle.text = if (isEnglish) "EN" else "සිං"
    }

    fun setOtpAvailable(available: Boolean, code: String? = null) {
        this.isOtpAvailable = available
        this.currentOtpCode = code
        val enabled = currentPrefs?.isToolbarIconEnabled("otp") ?: true
        otpBtn.visibility = if (available && enabled) View.VISIBLE else View.GONE
        if (!code.isNullOrEmpty()) {
            otpBtn.contentDescription = "Paste OTP: $code"
        }
        currentPrefs?.let { configureToolbar(it) }
    }

    fun setIncognito(active: Boolean) {
        this.isIncognitoActive = active
        incognitoIndicator.visibility = if (active) View.VISIBLE else View.GONE
        currentPrefs?.let { configureToolbar(it) } ?: populateDefaultEmptyRow()
    }

    fun updateInk(newInk: Int) {
        this.ink = newInk
        undoBtn.imageTintList = ColorStateList.valueOf(newInk)
        redoBtn.imageTintList = ColorStateList.valueOf(newInk)
        astrologyBtn.imageTintList = ColorStateList.valueOf(newInk)
        translateBtn.imageTintList = ColorStateList.valueOf(newInk)
        fontBtn.imageTintList = ColorStateList.valueOf(newInk)
        otpBtn.imageTintList = ColorStateList.valueOf(newInk)
        singlishBtn.imageTintList = ColorStateList.valueOf(newInk)
        oneHandedBtn.imageTintList = ColorStateList.valueOf(newInk)
        incognitoIndicator.imageTintList = ColorStateList.valueOf(newInk)
        clipboard.imageTintList = ColorStateList.valueOf(newInk)
        settings.imageTintList = ColorStateList.valueOf(newInk)
        emojiSwitch.imageTintList = ColorStateList.valueOf(newInk)
        voiceBtn.imageTintList = ColorStateList.valueOf(newInk)

        undoBtn.background = circularRippleBackground(newInk)
        redoBtn.background = circularRippleBackground(newInk)
        astrologyBtn.background = circularRippleBackground(newInk)
        translateBtn.background = circularRippleBackground(newInk)
        fontBtn.background = circularRippleBackground(newInk)
        otpBtn.background = circularRippleBackground(newInk)
        singlishBtn.background = circularRippleBackground(newInk)
        oneHandedBtn.background = circularRippleBackground(newInk)
        clipboard.background = circularRippleBackground(newInk)
        settings.background = circularRippleBackground(newInk)
        emojiSwitch.background = circularRippleBackground(newInk)
        voiceBtn.background = circularRippleBackground(newInk)

        langToggle.setTextColor(newInk)
        langToggle.background = circularRippleBackground(newInk)

        chips.forEach { it.updateColor(newInk) }
    }

    fun setClipboardVisible(visible: Boolean) {
        this.isClipboardRequested = visible
        val enabled = currentPrefs?.isToolbarIconEnabled("clipboard") ?: true
        val reallyVisible = visible && enabled && (currentPrefs?.clipboardHistory ?: true)
        val container = clipboard.parent as? View
        if (container != null && container != emptyRow) {
            container.visibility = if (reallyVisible) VISIBLE else GONE
        } else {
            clipboard.visibility = if (reallyVisible) VISIBLE else GONE
        }
    }

    fun setSuggestions(ranked: List<String>, animated: Boolean, corrections: Set<String> = emptySet()) {
        val presented = present(ranked)
        val motion = animated && motionEnabled() && hasWindow()
        val becameEmpty = presented.all { it == null }
        val wasEmpty = values.all { it == null }
        values = presented
        showEmpty(becameEmpty)
        chips.forEachIndexed { index, chip ->
            val cand = presented[index]
            val isCorr = cand != null && cand in corrections
            chip.setCandidate(cand, motion && !becameEmpty && !wasEmpty, isCorr)
        }
    }

    private fun showEmpty(emptyState: Boolean) {
        emptyScroll.visibility = if (emptyState) VISIBLE else INVISIBLE
        chipRow.visibility = if (emptyState) INVISIBLE else VISIBLE
        emptyScroll.isClickable = emptyState
        emptyScroll.alpha = if (emptyState) 1f else 0f
        chipRow.alpha = 1f
        emptyScroll.animate().cancel()
        chipRow.animate().cancel()
    }

    private fun divider() = View(context).apply {
        setBackgroundColor(ColorUtils.setAlphaComponent(ink, 40))
        layoutParams = LinearLayout.LayoutParams(dp(1), LayoutParams.MATCH_PARENT).apply {
            topMargin = dp(8)
            bottomMargin = dp(8)
        }
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private fun motionEnabled() = ValueAnimator.areAnimatorsEnabled()
    private fun hasWindow() = isAttachedToWindow && width > 1
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        fun present(ranked: List<String>): List<String?> = when (ranked.size) {
            3 -> listOf(ranked[1], ranked[0], ranked[2])
            2 -> listOf(ranked[1], ranked[0], null)
            1 -> listOf(null, ranked[0], null)
            else -> listOf(null, null, null)
        }
    }

    private inner class MorphChip(context: Context, private var color: Int) : FrameLayout(context) {
        private val morph = MorphLabel(context, color)
        private var text: String? = null
        private var isCorrectionMode = false

        init {
            clipChildren = false
            clipToPadding = false
            addView(morph, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            isClickable = false
            isFocusable = true
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        fun setCandidate(value: String?, animated: Boolean, isCorrection: Boolean = false) {
            text = value
            isCorrectionMode = isCorrection
            isClickable = value != null
            isFocusable = value != null
            contentDescription = value?.let { if (isCorrection) "Grammar correction $it" else "Suggestion $it" }
            importantForAccessibility = if (value != null) IMPORTANT_FOR_ACCESSIBILITY_YES else IMPORTANT_FOR_ACCESSIBILITY_NO
            if (isCorrection && value != null) {
                background = GradientDrawable().apply {
                    cornerRadius = dp(14).toFloat()
                    setColor(ColorUtils.setAlphaComponent(this@MorphChip.color, 45))
                    setStroke(dp(1), ColorUtils.setAlphaComponent(this@MorphChip.color, 180))
                }
                morph.setText("✓ $value", animated)
            } else {
                background = null
                morph.setText(value, animated)
            }
        }

        fun updateColor(newColor: Int) {
            this.color = newColor
            morph.updateColor(newColor)
            if (isCorrectionMode && text != null) {
                background = GradientDrawable().apply {
                    cornerRadius = dp(14).toFloat()
                    setColor(ColorUtils.setAlphaComponent(newColor, 45))
                    setStroke(dp(1), ColorUtils.setAlphaComponent(newColor, 180))
                }
            }
        }

        override fun drawableStateChanged() {
            super.drawableStateChanged()
            morph.alpha = if (isPressed) 0.35f else 1f
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            val value = text ?: return false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    isPressed = true
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    isPressed = false
                    if (event.y >= 0 && event.y <= height - keySliver) onCandidate(value)
                    performClick()
                    return true
                }
                MotionEvent.ACTION_CANCEL -> {
                    isPressed = false
                    return true
                }
            }
            return super.onTouchEvent(event)
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }
    }
}

/** Shared-prefix fade: matching text stays; replacements fade in at full opacity. */
internal class MorphLabel(context: Context, private val color: Int) : FrameLayout(context) {
    private val label = com.vanniktech.emoji.EmojiTextView(context).apply {
        textSize = 16f
        setTextColor(color)
        gravity = Gravity.CENTER
        includeFontPadding = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        setPadding(dp(4), dp(4), dp(4), dp(4))
    }
    private var text = ""

    init {
        isClickable = false
        isFocusable = false
        clipChildren = false
        clipToPadding = false
        addView(label, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun setText(incoming: String?, animated: Boolean) {
        val next = incoming.orEmpty()
        if (next == text) {
            label.alpha = 1f
            return
        }
        label.animate().cancel()
        text = next
        label.text = next
        label.alpha = 1f
        if (animated && next.isNotEmpty() && ValueAnimator.areAnimatorsEnabled()) {
            label.scaleX = 0.96f
            label.scaleY = 0.96f
            label.animate().scaleX(1f).scaleY(1f).setDuration(140).setInterpolator(DecelerateInterpolator()).start()
        } else {
            label.scaleX = 1f
            label.scaleY = 1f
        }
    }

    fun updateColor(newColor: Int) {
        label.setTextColor(newColor)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
