package org.akshara.ime.settings

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
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

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        prefs = KeyboardPreferences(this)
        setContentView(R.layout.activity_settings)
        scroll = findViewById(R.id.settings_scroll)
        container = findViewById(R.id.settings_container)
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val y = scroll.scrollY
        container.removeAllViews()
        layoutInflater.inflate(R.layout.settings_header, container, true)

        val enabled = keyboardEnabled()
        val selected = keyboardSelected()
        section(R.string.category_get_started) {
            action(
                R.string.enable_keyboard,
                if (enabled) R.string.status_enabled else R.string.status_enable_needed,
                if (enabled) R.drawable.ic_check_circle else R.drawable.ic_settings,
                accent = enabled
            ) { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
            action(
                R.string.select_keyboard,
                if (selected) R.string.status_selected else R.string.status_select_needed,
                if (selected) R.drawable.ic_check_circle else R.drawable.ic_keyboard,
                accent = selected
            ) { (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker() }
        }
        section(R.string.category_typing) {
            choice(R.string.input_mode, R.drawable.ic_language, R.array.input_mode_entries, R.array.input_mode_values, prefs.mode.name) {
                prefs.mode = runCatching { InputMode.valueOf(it) }.getOrDefault(InputMode.SMART_PHONETIC)
            }
            toggle(R.string.suggestions, R.string.suggestions_summary, R.drawable.ic_suggestions, prefs.suggestions) {
                prefs.suggestions = it
                render()
            }
            toggle(R.string.emoji_suggestions, R.string.emoji_suggestions_summary, R.drawable.ic_emoji, prefs.emojiSuggestions, enabled = prefs.suggestions) {
                prefs.emojiSuggestions = it
            }
            choice(R.string.top_row, R.drawable.ic_numbers, R.array.top_row_entries, R.array.top_row_values, prefs.topRow) {
                prefs.topRow = it
            }
        }
        section(R.string.category_tools) {
            toggle(R.string.emoji_picker, R.string.emoji_picker_summary, R.drawable.ic_emoji, prefs.emojiPicker) {
                prefs.emojiPicker = it
            }
            choice(R.string.skin_tone, R.drawable.ic_emoji, R.array.skin_tone_entries, R.array.skin_tone_values, prefs.skinTone) {
                prefs.skinTone = it
            }
            toggle(R.string.clipboard_history, R.string.clipboard_summary, R.drawable.ic_clipboard, prefs.clipboardHistory) {
                prefs.clipboardHistory = it
            }
        }
        section(R.string.category_appearance) {
            choice(R.string.theme, R.drawable.ic_palette, R.array.theme_entries, R.array.theme_values, prefs.theme) {
                prefs.theme = it
            }
            choice(R.string.key_spacing, R.drawable.ic_keyboard, R.array.spacing_entries, R.array.spacing_values, prefs.keySpacing) {
                prefs.keySpacing = it
            }
            choice(R.string.keyboard_size, R.drawable.ic_keyboard, R.array.keyboard_size_entries, R.array.keyboard_size_values, prefs.keyboardSize) {
                prefs.keyboardSize = it
            }
            toggle(R.string.spatial_decoder, R.string.spatial_decoder_summary, R.drawable.ic_keyboard, prefs.spatialDecoder) {
                prefs.spatialDecoder = it
            }
            if (org.akshara.ime.BuildConfig.DEBUG) {
                toggle(R.string.debug_overlay, R.string.debug_overlay_summary, R.drawable.ic_info, prefs.debugOverlay) {
                    prefs.debugOverlay = it
                }
            }
            choice(R.string.one_handed, R.drawable.ic_keyboard, R.array.one_handed_entries, R.array.one_handed_values, prefs.oneHanded) {
                prefs.oneHanded = it
            }
            toggle(R.string.haptics, 0, R.drawable.ic_vibration, prefs.haptics) { prefs.haptics = it }
            toggle(R.string.key_sounds, 0, R.drawable.ic_volume, prefs.keySounds) { prefs.keySounds = it }
            toggle(R.string.high_contrast, R.string.high_contrast_summary, R.drawable.ic_palette, prefs.highContrast) {
                prefs.highContrast = it
            }
        }
        section(R.string.category_privacy) {
            action(R.string.clear_learning_title, R.string.clear_learning_summary, R.drawable.ic_delete) {
                confirm(R.string.clear_learning_title, R.string.clear_learning_message, R.string.clear) {
                    LocalLearningStore(this@SettingsActivity).clear()
                }
            }
            action(R.string.reset_touch_title, R.string.reset_touch_summary, R.drawable.ic_restart) {
                confirm(R.string.reset_touch_title, R.string.reset_touch_message, R.string.clear) {
                    TouchPersonalizationStore(this@SettingsActivity).reset()
                }
            }
            action(R.string.clear_clipboard_title, R.string.clear_clipboard_summary, R.drawable.ic_delete) {
                confirm(R.string.clear_clipboard_title, R.string.clear_clipboard_message, R.string.clear) {
                    ClipboardHistoryStore(this@SettingsActivity).clearHistory()
                }
            }
            action(R.string.reset_title, R.string.reset_summary, R.drawable.ic_restart) {
                confirm(R.string.reset_title, R.string.reset_message, R.string.reset) {
                    prefs.reset()
                    LocalLearningStore(this@SettingsActivity).clear()
                    ClipboardHistoryStore(this@SettingsActivity).clear()
                    TouchPersonalizationStore(this@SettingsActivity).reset()
                    render()
                }
            }
        }
        section(R.string.category_about) {
            action(R.string.about_title, 0, R.drawable.ic_info, summaryText = getString(R.string.about_summary, BuildConfig.VERSION_NAME))
            action(R.string.privacy_title, R.string.privacy_summary, R.drawable.ic_privacy)
        }
        scroll.post { scroll.scrollTo(0, y) }
    }

    private fun section(title: Int, rows: CardScope.() -> Unit) {
        layoutInflater.inflate(R.layout.settings_category, container, true)
        container.getChildAt(container.childCount - 1).let { it as TextView }.setText(title)
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cardBackground()
            clipToOutline = true
            outlineProvider = ViewOutlineProvider.BACKGROUND
            elevation = 0f
        }
        CardScope(card).rows()
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

    private inner class CardScope(private val card: LinearLayout) {
        fun action(title: Int, summary: Int, icon: Int, accent: Boolean = false, summaryText: String? = null, onClick: (() -> Unit)? = null) {
            val row = inflateRow(card, title, summaryText ?: summary.takeIf { it != 0 }?.let(::getString), icon, accent, onClick != null)
            if (onClick != null) row.setOnClickListener { onClick() }
        }

        fun toggle(title: Int, summary: Int, icon: Int, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
            val row = inflateRow(card, title, summary.takeIf { it != 0 }?.let(::getString), icon, switch = true)
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

        fun choice(title: Int, icon: Int, entries: Int, values: Int, current: String, onPick: (String) -> Unit) {
            val labels = resources.getStringArray(entries)
            val keys = resources.getStringArray(values)
            val selected = labels.getOrNull(keys.indexOf(current))
            val row = inflateRow(card, title, selected, icon, clickable = true)
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
    }

    private fun inflateRow(
        parent: ViewGroup,
        title: Int,
        summary: String?,
        icon: Int,
        accent: Boolean = false,
        clickable: Boolean = false,
        switch: Boolean = false
    ): View {
        val row = layoutInflater.inflate(R.layout.settings_item, parent, false)
        row.findViewById<TextView>(R.id.settings_item_title).setText(title)
        row.findViewById<TextView>(R.id.settings_item_summary).apply {
            text = summary.orEmpty()
            visibility = if (summary.isNullOrBlank()) View.GONE else View.VISIBLE
        }
        row.findViewById<ImageView>(R.id.settings_item_icon).apply {
            setImageResource(icon)
            imageTintList = ColorStateList.valueOf(if (accent) attrColor(android.R.attr.colorAccent) else attrColor(android.R.attr.colorControlNormal))
        }
        row.isClickable = clickable || switch
        row.isFocusable = clickable || switch
        parent.addView(row)
        return row
    }

    private fun confirm(title: Int, message: Int, action: Int, run: () -> Unit) {
        AlertDialog.Builder(this).setTitle(title).setMessage(message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(action) { _, _ -> run() }
            .show()
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

    private fun cardBackground() = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = cardRadius()
        setColor(cardColor())
    }

    private fun cardRadius(): Float {
        val fallback = 24f * resources.displayMetrics.density
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
}
