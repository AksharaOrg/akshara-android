package org.akshara.ime.data

import android.content.Context
import org.json.JSONArray

class ClipboardHistoryStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun items(): List<String> = read(ITEMS)
    fun pinnedItems(): List<String> = read(PINNED)

    fun add(text: String) {
        val stored = sanitize(text) ?: return
        persist(ITEMS, (listOf(stored) + items().filter { it != stored }).take(MAXIMUM_ITEMS))
    }

    fun pin(index: Int) {
        val recent = items().toMutableList()
        if (index !in recent.indices) return
        val item = recent.removeAt(index)
        persist(ITEMS, recent)
        persist(PINNED, listOf(item) + pinnedItems().filter { it != item })
    }

    fun remove(index: Int) {
        val recent = items().toMutableList()
        if (index !in recent.indices) return
        recent.removeAt(index)
        persist(ITEMS, recent)
    }

    fun removePinned(index: Int) {
        val pinned = pinnedItems().toMutableList()
        if (index !in pinned.indices) return
        pinned.removeAt(index)
        persist(PINNED, pinned)
    }

    fun clearHistory() = prefs.edit().remove(ITEMS).apply()
    fun clear() = prefs.edit().clear().apply()

    private fun sanitize(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        return trimmed.take(MAXIMUM_LENGTH)
    }

    private fun read(key: String): List<String> {
        val raw = runCatching { JSONArray(prefs.getString(key, "[]")) }.getOrDefault(JSONArray())
        return (0 until raw.length()).mapNotNull { raw.optString(it).takeIf(String::isNotBlank) }
    }

    private fun persist(key: String, values: List<String>) {
        prefs.edit().putString(key, JSONArray(values).toString()).apply()
    }

    companion object {
        const val FILE = "akshara_clipboard"
        const val MAXIMUM_ITEMS = 20
        const val MAXIMUM_LENGTH = 2000
        private const val ITEMS = "items"
        private const val PINNED = "pinned"
    }
}
