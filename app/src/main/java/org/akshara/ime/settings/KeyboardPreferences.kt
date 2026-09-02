package org.akshara.ime.settings

import android.content.Context
import org.akshara.ime.engine.InputMode

class KeyboardPreferences(context: Context) {
    private val store = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    var mode: InputMode
        get() = runCatching { InputMode.valueOf(store.getString(MODE, null) ?: "SMART_PHONETIC") }.getOrDefault(InputMode.SMART_PHONETIC)
        set(value) = store.edit().putString(MODE, value.name).apply()
    var suggestions: Boolean by bool(SUGGESTIONS, true)
    var emojiSuggestions: Boolean by bool(EMOJI_SUGGESTIONS, false)
    var emojiPicker: Boolean by bool(EMOJI_PICKER, true)
    var haptics: Boolean by bool(HAPTICS, true)
    var keySounds: Boolean by bool(KEY_SOUNDS, false)
    var keyPopup: Boolean by bool(KEY_POPUP, true)
    var highContrast: Boolean by bool(HIGH_CONTRAST, false)
    var clipboardHistory: Boolean by bool(CLIPBOARD, false)
    var topRow: String
        get() = store.getString(TOP_ROW, "none") ?: "none"
        set(value) = store.edit().putString(TOP_ROW, value).apply()
    var oneHanded: String
        get() = store.getString(ONE_HANDED, "center") ?: "center"
        set(value) = store.edit().putString(ONE_HANDED, value).apply()
    var keySpacing: String
        get() = store.getString(KEY_SPACING, "standard") ?: "standard"
        set(value) = store.edit().putString(KEY_SPACING, value).apply()
    var keyboardSize: String
        get() = store.getString(KEYBOARD_SIZE, "standard") ?: "standard"
        set(value) = store.edit().putString(KEYBOARD_SIZE, value).apply()
    var keyRoundness: Int
        get() = store.getInt(KEY_ROUNDNESS, 8)
        set(value) = store.edit().putInt(KEY_ROUNDNESS, value).apply()
    var spatialDecoder: Boolean by bool(SPATIAL_DECODER, true)
    var debugOverlay: Boolean by bool(DEBUG_OVERLAY, false)
    var theme: String
        get() = store.getString(THEME, "system") ?: "system"
        set(value) = store.edit().putString(THEME, value).apply()
    var useSystemColor: Boolean by bool(USE_SYSTEM_COLOR, true)
    var skinTone: String
        get() = store.getString(SKIN_TONE, "") ?: ""
        set(value) = store.edit().putString(SKIN_TONE, value).apply()

    fun reset() { store.edit().clear().apply() }
    private fun bool(key: String, default: Boolean) = object : kotlin.properties.ReadWriteProperty<Any?, Boolean> {
        override fun getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>) = store.getBoolean(key, default)
        override fun setValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>, value: Boolean) { store.edit().putBoolean(key, value).apply() }
    }
    companion object {
        const val FILE = "akshara_keyboard_preferences"
        private const val MODE = "mode"; private const val SUGGESTIONS = "suggestions"
        private const val EMOJI_SUGGESTIONS = "emoji_suggestions"; private const val EMOJI_PICKER = "emoji_picker"
        private const val HAPTICS = "haptics"; private const val KEY_SOUNDS = "key_sounds"
        private const val KEY_POPUP = "key_popup"
        private const val HIGH_CONTRAST = "high_contrast"; private const val CLIPBOARD = "clipboard"
        private const val TOP_ROW = "top_row"; private const val ONE_HANDED = "one_handed"
        private const val KEY_SPACING = "key_spacing"; private const val KEYBOARD_SIZE = "keyboard_size"
        private const val KEY_ROUNDNESS = "key_roundness"
        private const val SPATIAL_DECODER = "spatial_decoder"; private const val DEBUG_OVERLAY = "debug_overlay"
        private const val THEME = "theme"; private const val USE_SYSTEM_COLOR = "use_system_color"; private const val SKIN_TONE = "skin_tone"
    }
}
