package org.slashboard.ime.settings

import android.content.Context
import org.slashboard.ime.engine.InputMode
import java.util.concurrent.ConcurrentHashMap

class KeyboardPreferences(context: Context) {
    val store = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private val prefChangeListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key != null) {
            memCache.remove(key)
        } else {
            memCache.clear()
        }
    }

    init {
        store.registerOnSharedPreferenceChangeListener(prefChangeListener)
    }

    fun reload() {
        memCache.clear()
    }

    var mode: InputMode
        get() = (memCache[MODE] as? InputMode) ?: run {
            val v = runCatching { InputMode.valueOf(store.getString(MODE, null) ?: "SMART_PHONETIC") }.getOrDefault(InputMode.SMART_PHONETIC)
            memCache[MODE] = v
            v
        }
        set(value) {
            memCache[MODE] = value
            store.edit().putString(MODE, value.name).apply()
        }

    var useEnglish: Boolean by cachedBool("use_english", false)
    var suggestions: Boolean by cachedBool(SUGGESTIONS, true)
    var emojiSuggestions: Boolean by cachedBool(EMOJI_SUGGESTIONS, false)
    var emojiPicker: Boolean by cachedBool(EMOJI_PICKER, true)
    var haptics: Boolean by cachedBool(HAPTICS, true)
    var keySounds: Boolean by cachedBool(KEY_SOUNDS, false)

    var soundPack: String
        get() = (memCache[SOUND_PACK] as? String) ?: run {
            val v = store.getString(SOUND_PACK, "system") ?: "system"
            memCache[SOUND_PACK] = v
            v
        }
        set(value) {
            memCache[SOUND_PACK] = value
            store.edit().putString(SOUND_PACK, value).apply()
        }

    var highContrast: Boolean by cachedBool(HIGH_CONTRAST, false)
    var clipboardHistory: Boolean by cachedBool(CLIPBOARD, true)
    var securePasswordMode: Boolean by cachedBool(SECURE_PASSWORD_MODE, false)

    var topRow: String
        get() = (memCache[TOP_ROW] as? String) ?: run {
            val v = store.getString(TOP_ROW, "none") ?: "none"
            val normalized = if (v == "off") "none" else v
            memCache[TOP_ROW] = normalized
            normalized
        }
        set(value) {
            val normalized = if (value == "off") "none" else value
            memCache[TOP_ROW] = normalized
            store.edit().putString(TOP_ROW, normalized).apply()
        }

    var oneHanded: String
        get() = (memCache[ONE_HANDED] as? String) ?: run {
            val v = store.getString(ONE_HANDED, "center") ?: "center"
            memCache[ONE_HANDED] = v
            v
        }
        set(value) {
            memCache[ONE_HANDED] = value
            store.edit().putString(ONE_HANDED, value).apply()
        }

    var keySpacing: String
        get() = (memCache[KEY_SPACING] as? String) ?: run {
            val v = store.getString(KEY_SPACING, "standard") ?: "standard"
            memCache[KEY_SPACING] = v
            v
        }
        set(value) {
            memCache[KEY_SPACING] = value
            store.edit().putString(KEY_SPACING, value).apply()
        }

    var keyboardSize: String
        get() = (memCache[KEYBOARD_SIZE] as? String) ?: run {
            val v = store.getString(KEYBOARD_SIZE, "standard") ?: "standard"
            memCache[KEYBOARD_SIZE] = v
            v
        }
        set(value) {
            memCache[KEYBOARD_SIZE] = value
            store.edit().putString(KEYBOARD_SIZE, value).apply()
        }

    var spatialDecoder: Boolean by cachedBool(SPATIAL_DECODER, true)
    var debugOverlay: Boolean by cachedBool(DEBUG_OVERLAY, false)
    var transparentBackground: Boolean by cachedBool(TRANSPARENT_BACKGROUND, false)

    var theme: String
        get() = (memCache[THEME] as? String) ?: run {
            val v = store.getString(THEME, "dark") ?: "dark"
            memCache[THEME] = v
            v
        }
        set(value) {
            memCache[THEME] = value
            store.edit().putString(THEME, value).commit()
        }

    var skinTone: String
        get() = (memCache[SKIN_TONE] as? String) ?: run {
            var v = store.getString(SKIN_TONE, "") ?: ""
            if (v.equals("default", ignoreCase = true)) {
                v = ""
                store.edit().putString(SKIN_TONE, v).apply()
            }
            memCache[SKIN_TONE] = v
            v
        }
        set(value) {
            memCache[SKIN_TONE] = value
            store.edit().putString(SKIN_TONE, value).apply()
        }

    var toolbarIcons: String
        get() {
            val saved = store.getString(TOOLBAR_ICONS, null) ?: return DEFAULT_TOOLBAR_ICONS
            val list = saved.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
            if (!list.contains("font_studio")) {
                val langIdx = list.indexOf("lang_toggle")
                if (langIdx != -1) {
                    list.add(langIdx + 1, "font_studio")
                } else {
                    list.add(0, "font_studio")
                }
                val updated = list.joinToString(",")
                store.edit().putString(TOOLBAR_ICONS, updated).apply()
                return updated
            }
            return saved
        }
        set(value) {
            store.edit().putString(TOOLBAR_ICONS, value).apply()
        }

    var longPressMs: Long
        get() = (memCache[LONG_PRESS_MS] as? Long) ?: run {
            val v = store.getLong(LONG_PRESS_MS, 200L).coerceIn(120L, 500L)
            memCache[LONG_PRESS_MS] = v
            v
        }
        set(value) {
            val clamped = value.coerceIn(120L, 500L)
            memCache[LONG_PRESS_MS] = clamped
            store.edit().putLong(LONG_PRESS_MS, clamped).apply()
        }

    var appLayoutMemory: Boolean by cachedBool(APP_LAYOUT_MEMORY, true)
    var smartKeyModifiers: Boolean by cachedBool(SMART_KEY_MODIFIERS, true)

    var thumbReachMode: String
        get() = (memCache[THUMB_REACH_MODE] as? String) ?: run {
            val v = store.getString(THUMB_REACH_MODE, "off") ?: "off"
            memCache[THUMB_REACH_MODE] = v
            v
        }
        set(value) {
            memCache[THUMB_REACH_MODE] = value
            store.edit().putString(THUMB_REACH_MODE, value).apply()
        }

    var customSpacebarText: String
        get() = (memCache[CUSTOM_SPACEBAR_TEXT] as? String) ?: run {
            val v = store.getString(CUSTOM_SPACEBAR_TEXT, "") ?: ""
            memCache[CUSTOM_SPACEBAR_TEXT] = v
            v
        }
        set(value) {
            memCache[CUSTOM_SPACEBAR_TEXT] = value
            store.edit().putString(CUSTOM_SPACEBAR_TEXT, value).apply()
        }

    var keyboardFont: String
        get() = (memCache[KEYBOARD_FONT] as? String) ?: run {
            val v = store.getString(KEYBOARD_FONT, "default") ?: "default"
            memCache[KEYBOARD_FONT] = v
            v
        }
        set(value) {
            memCache[KEYBOARD_FONT] = value
            store.edit().putString(KEYBOARD_FONT, value).apply()
        }

    var confirmExit: Boolean by cachedBool(CONFIRM_EXIT, true)
    var firstLaunchPermissionsPrompted: Boolean by cachedBool(FIRST_LAUNCH_PERMS, false)

    var recentEmojis: List<String>
        get() {
            val raw = store.getString("recent_emojis", null)
            if (raw.isNullOrEmpty()) {
                return listOf("😂", "❤️", "🙏", "👍", "😍", "🤣", "😊", "🔥", "🥺", "🥰", "😭", "✨", "🎉", "👏", "😁", "😘", "😎", "😅", "🇱🇰", "💯", "🙌", "💖")
            }
            return raw.split(",").filter { it.isNotEmpty() }
        }
        set(value) {
            store.edit().putString("recent_emojis", value.joinToString(",")).apply()
        }

    fun isToolbarIconEnabled(id: String): Boolean {
        val list = toolbarIcons.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        return id in list
    }

    fun setToolbarIconEnabled(id: String, enabled: Boolean) {
        val current = toolbarIcons.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
        if (enabled) {
            if (id !in current) {
                if (id == "lang_toggle") {
                    current.add(0, id)
                } else if (current.isNotEmpty() && current.last() == "settings") {
                    current.add(current.size - 1, id)
                } else {
                    current.add(id)
                }
            }
        } else {
            current.remove(id)
        }
        toolbarIcons = current.joinToString(",")
    }

    fun moveToolbarIcon(id: String, moveUp: Boolean) {
        val current = toolbarIcons.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
        val index = current.indexOf(id)
        if (index == -1) return
        val targetIndex = if (moveUp) index - 1 else index + 1
        if (targetIndex in 0 until current.size) {
            val item = current.removeAt(index)
            current.add(targetIndex, item)
            toolbarIcons = current.joinToString(",")
        }
    }

    fun swapToolbarIcons(indexA: Int, indexB: Int) {
        val current = toolbarIcons.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
        if (indexA in current.indices && indexB in current.indices && indexA != indexB) {
            val temp = current[indexA]
            current[indexA] = current[indexB]
            current[indexB] = temp
            toolbarIcons = current.joinToString(",")
        }
    }

    fun reorderToolbarIcons(newList: List<String>) {
        toolbarIcons = newList.filter { it.isNotEmpty() }.joinToString(",")
    }

    fun resetToolbarIcons() {
        toolbarIcons = DEFAULT_TOOLBAR_ICONS
    }

    fun reset() {
        memCache.clear()
        store.edit().clear().apply()
    }

    private fun cachedBool(key: String, default: Boolean) = object : kotlin.properties.ReadWriteProperty<Any?, Boolean> {
        override fun getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>): Boolean {
            val cached = memCache[key] as? Boolean
            if (cached != null) return cached
            val v = store.getBoolean(key, default)
            memCache[key] = v
            return v
        }
        override fun setValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>, value: Boolean) {
            memCache[key] = value
            store.edit().putBoolean(key, value).apply()
        }
    }

    companion object {
        private val memCache = ConcurrentHashMap<String, Any>()
        const val FILE = "slashboard_keyboard_preferences"
        private const val MODE = "mode"; private const val SUGGESTIONS = "suggestions"
        private const val EMOJI_SUGGESTIONS = "emoji_suggestions"; private const val EMOJI_PICKER = "emoji_picker"
        private const val HAPTICS = "haptics"; private const val KEY_SOUNDS = "key_sounds"
        private const val SOUND_PACK = "sound_pack"
        private const val HIGH_CONTRAST = "high_contrast"; private const val CLIPBOARD = "clipboard"
        private const val SECURE_PASSWORD_MODE = "secure_password_mode"
        const val TOP_ROW = "top_row"
        private const val ONE_HANDED = "one_handed"
        private const val KEY_SPACING = "key_spacing"; private const val KEYBOARD_SIZE = "keyboard_size"
        private const val SPATIAL_DECODER = "spatial_decoder"; private const val DEBUG_OVERLAY = "debug_overlay"
        private const val TRANSPARENT_BACKGROUND = "transparent_background"
        private const val THEME = "theme"; private const val SKIN_TONE = "skin_tone"
        private const val TOOLBAR_ICONS = "toolbar_icons"
        private const val LONG_PRESS_MS = "long_press_ms"
        private const val APP_LAYOUT_MEMORY = "app_layout_memory"
        private const val SMART_KEY_MODIFIERS = "smart_key_modifiers"
        private const val THUMB_REACH_MODE = "thumb_reach_mode"
        private const val CUSTOM_SPACEBAR_TEXT = "custom_spacebar_text"
        private const val KEYBOARD_FONT = "keyboard_font"
        private const val CONFIRM_EXIT = "confirm_exit"
        private const val FIRST_LAUNCH_PERMS = "first_launch_perms"
        const val DEFAULT_TOOLBAR_ICONS = "lang_toggle,font_studio,singlish,calculator,undo,redo,astrology,fm,translate,one_handed,emoji,clipboard,settings"
    }
}
