package org.akshara.ime.data

import android.content.Context
import org.json.JSONArray

/** Persistent, ordered emoji recents. Values come from the emoji picker, not Unicode heuristics. */
class RecentEmojiStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun items(): List<String> {
        val raw = runCatching { JSONArray(prefs.getString(ITEMS, "[]")) }.getOrDefault(JSONArray())
        return (0 until raw.length()).mapNotNull { raw.optString(it).takeIf(String::isNotBlank) }
    }

    fun add(emoji: String): List<String> {
        if (emoji.isBlank()) return items()
        val next = (listOf(emoji) + items().filter { it != emoji }).take(MAXIMUM_ITEMS)
        prefs.edit().putString(ITEMS, JSONArray(next).toString()).apply()
        return next
    }

    fun clear() = prefs.edit().remove(ITEMS).apply()

    companion object {
        const val FILE = "akshara_recent_emoji"
        const val MAXIMUM_ITEMS = 32
        private const val ITEMS = "items"
    }
}
