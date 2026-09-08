package org.akshara.ime.settings

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.inputmethod.InputMethodManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import org.akshara.ime.BuildConfig
import org.akshara.ime.R
import org.akshara.ime.data.ClipboardHistoryStore
import org.akshara.ime.data.LocalLearningStore
import org.akshara.ime.engine.InputMode
import org.akshara.ime.ime.AksharaInputMethodService
import org.akshara.ime.ime.TouchPersonalizationStore

class SettingsActivity : Activity() {
    private lateinit var prefs: KeyboardPreferences
    private lateinit var scroll: ScrollView
    private lateinit var container: LinearLayout
    private var page = Page.HOME
    private var buildTapCount = 0
    private var lastBuildTap = 0L

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        prefs = KeyboardPreferences(this)
        page = state?.getString(STATE_PAGE)?.let { runCatching { Page.valueOf(it) }.getOrNull() } ?: Page.HOME
        setContentView(R.layout.activity_settings)
        scroll = findViewById(R.id.settings_scroll)
        container = findViewById(R.id.settings_container)
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_PAGE, page.name)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (page != Page.HOME) {
            navigateUp()
            return
        }
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }

    private fun navigateUp() {
        page = if (page == Page.ABOUT) Page.HOME else Page.ABOUT
        render()
    }

    private fun open(next: Page) {
        page = next
        scroll.scrollTo(0, 0)
        render()
    }

    private fun render() {
        val y = if (page == Page.HOME) scroll.scrollY else 0
        container.removeAllViews()
        when (page) {
            Page.HOME -> renderHome()
            Page.ABOUT -> renderAbout()
            Page.PRIVACY -> renderPrivacy()
            Page.NOTICES -> renderNotices()
            Page.CREDITS -> renderCredits()
            Page.DIAGNOSTICS -> renderDiagnostics()
            Page.DEVELOPER -> renderDeveloper()
        }
        scroll.post { scroll.scrollTo(0, y) }
    }

    private fun renderHome() {
        layoutInflater.inflate(R.layout.settings_header, container, true)
        val enabled = keyboardEnabled()
        val selected = keyboardSelected()
        section(R.string.category_get_started) {
            action(
                R.string.enable_keyboard,
                if (enabled) R.string.status_enabled else R.string.status_enable_needed,
                R.drawable.ic_check_circle,
                if (enabled) R.color.settings_icon_green else R.color.settings_icon_gray
            ) { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
            action(
                R.string.select_keyboard,
                if (selected) R.string.status_selected else R.string.status_select_needed,
                R.drawable.ic_keyboard,
                if (selected) R.color.settings_icon_green else R.color.settings_icon_blue
            ) { (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker() }
        }
        section(R.string.category_typing) {
            choice(R.string.input_mode, R.drawable.ic_language, R.color.settings_icon_blue, R.array.input_mode_entries, R.array.input_mode_values, prefs.mode.name) {
                prefs.mode = runCatching { InputMode.valueOf(it) }.getOrDefault(InputMode.SMART_PHONETIC)
            }
            toggle(R.string.suggestions, R.string.suggestions_summary, R.drawable.ic_suggestions, R.color.settings_icon_orange, prefs.suggestions) {
                prefs.suggestions = it
                render()
            }
            toggle(R.string.emoji_suggestions, R.string.emoji_suggestions_summary, R.drawable.ic_emoji, R.color.settings_icon_yellow, prefs.emojiSuggestions, enabled = prefs.suggestions) {
                prefs.emojiSuggestions = it
            }
            choice(R.string.top_row, R.drawable.ic_numbers, R.color.settings_icon_indigo, R.array.top_row_entries, R.array.top_row_values, prefs.topRow) {
                prefs.topRow = it
            }
            toggle(R.string.double_space_period, R.string.double_space_period_summary, R.drawable.ic_language, R.color.settings_icon_gray, prefs.doubleSpacePeriod) {
                prefs.doubleSpacePeriod = it
            }
            toggle(R.string.smart_quotes, R.string.smart_quotes_summary, R.drawable.ic_language, R.color.settings_icon_mint, prefs.smartQuotes) {
                prefs.smartQuotes = it
            }
            toggle(R.string.smart_punctuation, R.string.smart_punctuation_summary, R.drawable.ic_language, R.color.settings_icon_pink, prefs.smartPunctuation) {
                prefs.smartPunctuation = it
            }
            if (prefs.mode == InputMode.SMART_PHONETIC) {
                toggle(R.string.english_one_word, R.string.english_one_word_summary, R.drawable.ic_language, R.color.settings_icon_teal, prefs.englishForOneWord) {
                    prefs.englishForOneWord = it
                }
            }
        }
        section(R.string.category_tools) {
            toggle(R.string.emoji_picker, R.string.emoji_picker_summary, R.drawable.ic_emoji, R.color.settings_icon_yellow, prefs.emojiPicker) {
                prefs.emojiPicker = it
            }
            choice(R.string.skin_tone, R.drawable.ic_emoji, R.color.settings_icon_pink, R.array.skin_tone_entries, R.array.skin_tone_values, prefs.skinTone) {
                prefs.skinTone = it
            }
            toggle(R.string.clipboard_history, R.string.clipboard_summary, R.drawable.ic_clipboard, R.color.settings_icon_teal, prefs.clipboardHistory) {
                prefs.clipboardHistory = it
            }
        }
        section(R.string.category_appearance) {
            choice(R.string.theme, R.drawable.ic_palette, R.color.settings_icon_purple, R.array.theme_entries, R.array.theme_values, prefs.theme) {
                prefs.theme = it
            }
            choice(R.string.key_spacing, R.drawable.ic_keyboard, R.color.settings_icon_gray, R.array.spacing_entries, R.array.spacing_values, prefs.keySpacing) {
                prefs.keySpacing = it
            }
            choice(R.string.keyboard_size, R.drawable.ic_keyboard, R.color.settings_icon_gray, R.array.keyboard_size_entries, R.array.keyboard_size_values, prefs.keyboardSize) {
                prefs.keyboardSize = it
            }
            toggle(R.string.spatial_decoder, R.string.spatial_decoder_summary, R.drawable.ic_keyboard, R.color.settings_icon_blue, prefs.spatialDecoder) {
                prefs.spatialDecoder = it
            }
            choice(R.string.one_handed, R.drawable.ic_keyboard, R.color.settings_icon_indigo, R.array.one_handed_entries, R.array.one_handed_values, prefs.oneHanded) {
                prefs.oneHanded = it
            }
            toggle(R.string.haptics, 0, R.drawable.ic_vibration, R.color.settings_icon_pink, prefs.haptics) { prefs.haptics = it }
            toggle(R.string.key_sounds, 0, R.drawable.ic_volume, R.color.settings_icon_orange, prefs.keySounds) { prefs.keySounds = it }
            toggle(R.string.high_contrast, R.string.high_contrast_summary, R.drawable.ic_palette, R.color.settings_icon_gray, prefs.highContrast) {
                prefs.highContrast = it
            }
            if (BuildConfig.DEBUG || prefs.developerUnlocked) {
                toggle(R.string.debug_overlay, R.string.debug_overlay_summary, R.drawable.ic_bug, R.color.settings_icon_mint, prefs.debugOverlay) {
                    prefs.debugOverlay = it
                }
            }
        }
        section(R.string.category_privacy) {
            action(R.string.clear_learning_title, R.string.clear_learning_summary, R.drawable.ic_delete, R.color.settings_icon_red) {
                confirm(R.string.clear_learning_title, R.string.clear_learning_message, R.string.clear) {
                    LocalLearningStore(this@SettingsActivity).clear()
                }
            }
            action(R.string.reset_touch_title, R.string.reset_touch_summary, R.drawable.ic_restart, R.color.settings_icon_orange) {
                confirm(R.string.reset_touch_title, R.string.reset_touch_message, R.string.clear) {
                    TouchPersonalizationStore(this@SettingsActivity).reset()
                }
            }
            action(R.string.clear_clipboard_title, R.string.clear_clipboard_summary, R.drawable.ic_clipboard, R.color.settings_icon_teal) {
                confirm(R.string.clear_clipboard_title, R.string.clear_clipboard_message, R.string.clear) {
                    ClipboardHistoryStore(this@SettingsActivity).clearHistory()
                }
            }
        }
        section(R.string.category_links) {
            action(R.string.website_title, 0, R.drawable.ic_language, R.color.settings_icon_blue, summaryText = getString(R.string.link_website)) {
                openUrl(R.string.link_website)
            }
            action(R.string.github_title, 0, R.drawable.ic_code, R.color.settings_icon_indigo, summaryText = getString(R.string.link_github)) {
                openUrl(R.string.link_github)
            }
        }
        section(0) {
            action(
                R.string.about_title,
                0,
                R.drawable.ic_info,
                R.color.settings_icon_teal,
                summaryText = getString(R.string.about_summary, BuildConfig.VERSION_NAME)
            ) { open(Page.ABOUT) }
        }
    }

    private fun renderAbout() {
        toolbar(R.string.category_about)
        section(0) {
            action(R.string.privacy_title, R.string.privacy_summary, R.drawable.ic_privacy, R.color.settings_icon_teal) { open(Page.PRIVACY) }
            action(R.string.notices_title, 0, R.drawable.ic_doc, R.color.settings_icon_orange) { open(Page.NOTICES) }
            action(R.string.credits_title, 0, R.drawable.ic_heart, R.color.settings_icon_pink) { open(Page.CREDITS) }
            action(R.string.diagnostics_title, 0, R.drawable.ic_pulse, R.color.settings_icon_red) { open(Page.DIAGNOSTICS) }
            if (prefs.developerUnlocked) {
                action(R.string.developer_title, 0, R.drawable.ic_bug, R.color.settings_icon_mint) { open(Page.DEVELOPER) }
            }
        }
        section(0) {
            labeled(R.string.about_version, BuildConfig.VERSION_NAME)
            labeled(R.string.about_build, BuildConfig.VERSION_CODE.toString(), onClick = ::handleBuildTap)
            labeled(R.string.about_copyright_label, getString(R.string.about_copyright_value))
            labeled(R.string.about_license_label, getString(R.string.about_license_value))
        }
        section(0) {
            action(R.string.reset_title, R.string.reset_summary, R.drawable.ic_restart, R.color.settings_icon_red, destructive = true) {
                confirm(R.string.reset_title, R.string.reset_message, R.string.reset) {
                    prefs.reset()
                    ClipboardHistoryStore(this@SettingsActivity).clear()
                    render()
                }
            }
        }
    }

    private fun renderPrivacy() {
        toolbar(R.string.privacy_title)
        copy(R.string.privacy_on_device_title, R.string.privacy_on_device_body)
        copy(R.string.privacy_permissions_title, R.string.privacy_permissions_body)
        copy(R.string.privacy_clipboard_title, R.string.privacy_clipboard_body)
        copy(R.string.privacy_predictions_title, R.string.privacy_predictions_body)
        copy(R.string.privacy_diagnostics_title, R.string.privacy_diagnostics_body)
        copy(R.string.privacy_tracking_title, R.string.privacy_tracking_body)
        section(R.string.privacy_contact_title) {
            action(R.string.website_title, 0, R.drawable.ic_language, R.color.settings_icon_blue) { openUrl(R.string.link_website) }
            action(R.string.github_title, 0, R.drawable.ic_code, R.color.settings_icon_indigo) { openUrl(R.string.link_github) }
        }
    }

    private fun renderNotices() {
        toolbar(R.string.notices_title)
        copy(R.string.notices_akshara_title, R.string.notices_akshara_body)
        section(0) {
            action(R.string.notices_source_code, 0, R.drawable.ic_code, R.color.settings_icon_indigo, summaryText = getString(R.string.link_github)) {
                openUrl(R.string.link_github)
            }
        }
        copy(R.string.notices_contributors_title, R.string.credits_thimira_body)
        section(0) {
            action(R.string.credits_thimira_title, 0, R.drawable.ic_heart, R.color.settings_icon_pink, summaryText = getString(R.string.link_thimira)) {
                openUrl(R.string.link_thimira)
            }
        }
        copy(R.string.notices_frequency_title, R.string.notices_frequency_body)
        section(0) {
            action(R.string.notices_frequency_source, 0, R.drawable.ic_code, R.color.settings_icon_blue) { openUrl(R.string.link_frequency) }
        }
        copy(R.string.notices_corpus_title, R.string.notices_corpus_body)
        section(0) {
            action(R.string.notices_dataset, 0, R.drawable.ic_doc, R.color.settings_icon_orange) { openUrl(R.string.link_corpus) }
            action(R.string.notices_dataset_doi, 0, R.drawable.ic_doc, R.color.settings_icon_orange) { openUrl(R.string.link_corpus_doi) }
            action(R.string.notices_cc_by, 0, R.drawable.ic_doc, R.color.settings_icon_green) { openUrl(R.string.link_cc_by) }
        }
        copy(R.string.notices_privacy_title, R.string.notices_privacy_body)
    }

    private fun renderCredits() {
        toolbar(R.string.credits_title)
        copy(R.string.credits_thimira_title, R.string.credits_thimira_body)
        section(0) {
            action(R.string.website_title, 0, R.drawable.ic_language, R.color.settings_icon_blue, summaryText = getString(R.string.link_thimira)) {
                openUrl(R.string.link_thimira)
            }
        }
    }

    private fun renderDiagnostics() {
        toolbar(R.string.diagnostics_title)
        val keyboard = when {
            keyboardEnabled() && keyboardSelected() -> getString(R.string.diagnostics_keyboard_ready)
            keyboardEnabled() -> getString(R.string.diagnostics_keyboard_enabled)
            else -> getString(R.string.diagnostics_keyboard_off)
        }
        section(R.string.diagnostics_app_title) {
            labeled(R.string.about_version, BuildConfig.VERSION_NAME)
            labeled(R.string.about_build, BuildConfig.VERSION_CODE.toString())
            labeled(R.string.diagnostics_device_label, "${Build.MANUFACTURER} ${Build.MODEL}")
            labeled(R.string.diagnostics_android_label, "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            labeled(R.string.diagnostics_keyboard_label, keyboard)
            labeled(
                R.string.diagnostics_clipboard_label,
                getString(if (prefs.clipboardHistory) R.string.diagnostics_on else R.string.diagnostics_off)
            )
        }
        copy(0, R.string.diagnostics_footer)
    }

    private fun renderDeveloper() {
        toolbar(R.string.developer_title)
        section(0) {
            toggle(R.string.debug_overlay, R.string.debug_overlay_summary, R.drawable.ic_bug, R.color.settings_icon_mint, prefs.debugOverlay) {
                prefs.debugOverlay = it
            }
        }
    }

    private fun toolbar(title: Int) {
        layoutInflater.inflate(R.layout.settings_toolbar, container, true)
        val bar = container.getChildAt(container.childCount - 1)
        bar.findViewById<TextView>(R.id.settings_toolbar_title).setText(title)
        bar.findViewById<ImageButton>(R.id.settings_toolbar_back).apply {
            imageTintList = ColorStateList.valueOf(attrColor(android.R.attr.colorControlNormal))
            setOnClickListener { navigateUp() }
        }
    }

    private fun copy(title: Int, body: Int) {
        if (title != 0) {
            layoutInflater.inflate(R.layout.settings_category, container, true)
            container.getChildAt(container.childCount - 1).let { it as TextView }.setText(title)
        }
        val card = card()
        val text = layoutInflater.inflate(R.layout.settings_copy, card, false) as TextView
        text.setText(body)
        card.addView(text)
        addCard(card)
    }

    private fun section(title: Int, rows: CardScope.() -> Unit) {
        if (title != 0) {
            layoutInflater.inflate(R.layout.settings_category, container, true)
            container.getChildAt(container.childCount - 1).let { it as TextView }.setText(title)
        }
        val card = card()
        CardScope(card).rows()
        addCard(card)
    }

    private inner class CardScope(private val card: LinearLayout) {
        fun action(
            title: Int,
            summary: Int,
            icon: Int,
            tint: Int,
            summaryText: String? = null,
            destructive: Boolean = false,
            onClick: (() -> Unit)? = null
        ) {
            val row = inflateRow(
                card, title, summaryText ?: summary.takeIf { it != 0 }?.let(::getString),
                icon, tint, chevron = onClick != null, destructive = destructive
            )
            if (onClick != null) row.setOnClickListener { onClick() }
        }

        fun toggle(title: Int, summary: Int, icon: Int, tint: Int, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
            val row = inflateRow(card, title, summary.takeIf { it != 0 }?.let(::getString), icon, tint, switch = true)
            val toggle = row.findViewById<Switch>(R.id.settings_item_switch)
            toggle.visibility = View.VISIBLE
            toggle.isChecked = checked
            row.isEnabled = enabled
            row.alpha = if (enabled) 1f else 0.4f
            row.setOnClickListener {
                if (!enabled) return@setOnClickListener
                val next = !toggle.isChecked
                toggle.isChecked = next
                onChange(next)
            }
        }

        fun choice(title: Int, icon: Int, tint: Int, entries: Int, values: Int, current: String, onPick: (String) -> Unit) {
            val labels = resources.getStringArray(entries)
            val keys = resources.getStringArray(values)
            val selected = labels.getOrNull(keys.indexOf(current))
            val row = inflateRow(card, title, selected, icon, tint, chevron = true)
            row.setOnClickListener {
                AlertDialog.Builder(this@SettingsActivity)
                    .setTitle(title)
                    .setSingleChoiceItems(labels, keys.indexOf(current).coerceAtLeast(0)) { dialog, which ->
                        onPick(keys[which])
                        dialog.dismiss()
                        render()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        }

        fun labeled(title: Int, value: String, onClick: (() -> Unit)? = null) {
            val row = inflateRow(card, title, null, 0, 0, clickable = onClick != null)
            row.findViewById<TextView>(R.id.settings_item_value).apply {
                text = value
                visibility = View.VISIBLE
            }
            if (onClick != null) row.setOnClickListener { onClick() }
        }
    }

    private fun inflateRow(
        parent: ViewGroup,
        title: Int,
        summary: String?,
        icon: Int,
        tint: Int,
        chevron: Boolean = false,
        clickable: Boolean = false,
        switch: Boolean = false,
        destructive: Boolean = false
    ): View {
        if (parent.childCount > 0) layoutInflater.inflate(R.layout.settings_divider, parent, true)
        val row = layoutInflater.inflate(R.layout.settings_item, parent, false)
        val titleView = row.findViewById<TextView>(R.id.settings_item_title)
        titleView.setText(title)
        if (destructive) titleView.setTextColor(attrColor(android.R.attr.colorError).takeIf { it != 0 } ?: 0xFFB00020.toInt())
        row.findViewById<TextView>(R.id.settings_item_summary).apply {
            text = summary.orEmpty()
            visibility = if (summary.isNullOrBlank()) View.GONE else View.VISIBLE
        }
        val well = row.findViewById<View>(R.id.settings_item_icon_well)
        val iconView = row.findViewById<ImageView>(R.id.settings_item_icon)
        if (icon == 0) {
            well.visibility = View.GONE
        } else {
            iconView.setImageResource(icon)
            iconView.imageTintList = ColorStateList.valueOf(Color.WHITE)
            well.background = iconTile(getColor(tint))
        }
        row.findViewById<ImageView>(R.id.settings_item_chevron).apply {
            visibility = if (chevron) View.VISIBLE else View.GONE
            imageTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(attrColor(android.R.attr.colorControlNormal), 140))
        }
        row.isClickable = clickable || switch || chevron
        row.isFocusable = clickable || switch || chevron
        parent.addView(row)
        return row
    }

    private fun confirm(title: Int, message: Int, action: Int, run: () -> Unit) {
        AlertDialog.Builder(this).setTitle(title).setMessage(message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(action) { _, _ -> run() }
            .show()
    }

    private fun handleBuildTap() {
        val now = System.currentTimeMillis()
        if (now - lastBuildTap > 1500L) buildTapCount = 0
        lastBuildTap = now
        buildTapCount += 1
        vibrate()
        if (buildTapCount < 7) return
        buildTapCount = 0
        prefs.developerUnlocked = true
        Toast.makeText(this, R.string.developer_welcome, Toast.LENGTH_SHORT).show()
        open(Page.DEVELOPER)
    }

    private fun vibrate() {
        val vibrator = if (Build.VERSION.SDK_INT >= 31) {
            (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
        if (Build.VERSION.SDK_INT >= 29) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(20)
        }
    }

    private fun openUrl(res: Int) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(res))))
    }

    private fun keyboardEnabled(): Boolean {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        return imm.enabledInputMethodList.any { it.packageName == packageName }
    }

    private fun keyboardSelected(): Boolean {
        val component = ComponentName(this, AksharaInputMethodService::class.java).flattenToShortString()
        val selected = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        return selected?.let { ComponentName.unflattenFromString(it)?.packageName == packageName || it == component } == true
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = cardBackground()
        clipToOutline = true
        outlineProvider = ViewOutlineProvider.BACKGROUND
        elevation = 0f
    }

    private fun addCard(card: LinearLayout) {
        val gutter = resources.getDimensionPixelSize(R.dimen.settings_gutter)
        container.addView(
            card,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = gutter
                marginEnd = gutter
                bottomMargin = resources.getDimensionPixelSize(R.dimen.settings_section_gap)
            }
        )
    }

    private fun iconTile(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = resources.getDimension(R.dimen.settings_icon_radius)
        setColor(color)
    }

    private fun cardBackground() = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = cardRadius()
        setColor(cardColor())
    }

    private fun cardRadius(): Float {
        val fallback = 28f * resources.displayMetrics.density
        if (Build.VERSION.SDK_INT < 28) return fallback
        val value = TypedValue()
        if (!theme.resolveAttribute(android.R.attr.dialogCornerRadius, value, true)) return fallback
        return if (value.type == TypedValue.TYPE_DIMENSION) value.getDimension(resources.displayMetrics) else fallback
    }

    private fun cardColor(): Int {
        val window = attrColor(android.R.attr.colorBackground)
        val floating = attrColor(android.R.attr.colorBackgroundFloating)
        if (floating != 0 && floating != window) return floating
        val ink = attrColor(android.R.attr.textColorPrimary)
        return ColorUtils.blendARGB(window, ColorUtils.setAlphaComponent(ink, 255), 0.06f)
    }

    private fun attrColor(attr: Int): Int {
        val typed = obtainStyledAttributes(intArrayOf(attr))
        val color = typed.getColor(0, 0)
        typed.recycle()
        return color
    }

    private enum class Page { HOME, ABOUT, PRIVACY, NOTICES, CREDITS, DIAGNOSTICS, DEVELOPER }

    companion object {
        private const val STATE_PAGE = "settings_page"
    }
}
