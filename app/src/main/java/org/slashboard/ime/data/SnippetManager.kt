package org.slashboard.ime.data

import android.content.Context
import org.json.JSONObject

/**
 * Manages user text expansion snippets (e.g. "omw" -> "On my way!", "ලිපි" -> "මගේ ලිපිනය: ...").
 */
class SnippetManager(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    companion object {
        private const val FILE = "slashboard_snippets"
        private const val KEY_SNIPPETS = "user_snippets"

        val DEFAULT_SNIPPETS = mapOf(
            "omw" to "On my way!",
            "brb" to "Be right back!",
            "ty" to "Thank you so much!",
            "np" to "No problem!",
            "gm" to "Good morning!",
            "gn" to "Good night!",
            "ලිපි" to "මගේ ලිපිනය: ",
            "ගිණුම" to "මගේ ගිණුම් අංකය: ",
            "දුරකථන" to "මගේ දුරකථන අංකය: ",
            "ස්තූතියි" to "ඔබට බොහොමත්ම ස්තූතියි!",
            "සුබ" to "සුභ දවසක් වේවා!"
        )
    }

    init {
        if (!prefs.contains(KEY_SNIPPETS)) {
            saveMap(DEFAULT_SNIPPETS)
        }
    }

    fun getAll(): Map<String, String> {
        val raw = prefs.getString(KEY_SNIPPETS, null) ?: return DEFAULT_SNIPPETS
        return runCatching {
            val json = JSONObject(raw)
            val map = mutableMapOf<String, String>()
            json.keys().forEach { k ->
                map[k] = json.getString(k)
            }
            map
        }.getOrDefault(DEFAULT_SNIPPETS)
    }

    fun find(shortcut: String): String? {
        if (shortcut.isBlank()) return null
        val all = getAll()
        // Exact match first, then case-insensitive
        return all[shortcut] ?: all.entries.firstOrNull { it.key.equals(shortcut, ignoreCase = true) }?.value
    }

    fun add(shortcut: String, phrase: String) {
        val trimmedKey = shortcut.trim()
        val trimmedPhrase = phrase.trim()
        if (trimmedKey.isEmpty() || trimmedPhrase.isEmpty()) return
        val current = getAll().toMutableMap()
        current[trimmedKey] = trimmedPhrase
        saveMap(current)
    }

    fun remove(shortcut: String) {
        val current = getAll().toMutableMap()
        if (current.remove(shortcut) != null) {
            saveMap(current)
        }
    }

    private fun saveMap(map: Map<String, String>) {
        val json = JSONObject()
        map.forEach { (k, v) -> json.put(k, v) }
        prefs.edit().putString(KEY_SNIPPETS, json.toString()).apply()
    }
}
