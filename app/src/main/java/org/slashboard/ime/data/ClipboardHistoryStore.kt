package org.slashboard.ime.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class ClipboardHistoryStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun items(): List<String> {
        cleanupExpired()
        return read(ITEMS)
    }

    fun pinnedItems(): List<String> = read(PINNED)

    fun add(text: String) {
        val stored = sanitize(text) ?: return
        val current = items().filter { it != stored }
        persist(ITEMS, (listOf(stored) + current).take(MAXIMUM_ITEMS))

        if (isSensitive(stored)) {
            recordSensitiveTimestamp(stored)
        }
    }

    private fun isSensitive(text: String): Boolean {
        val lower = text.lowercase()
        // OTP: 4-8 digit isolated code, or code accompanied by security keywords
        if (Regex("""\b\d{4,8}\b""").matches(text.trim())) return true
        if (lower.contains("otp") || lower.contains("verification") || lower.contains("password") || lower.contains("passcode") || lower.contains("cvv")) {
            if (Regex("""\b\d{4,8}\b""").containsMatchIn(text)) return true
        }
        // Credit card patterns (e.g. 16 digits or 4x4 blocks)
        if (Regex("""\b(?:\d{4}[ -]?){3}\d{4}\b""").containsMatchIn(text)) return true
        return false
    }

    private fun recordSensitiveTimestamp(text: String) {
        val raw = prefs.getString(KEY_SENSITIVE, "{}") ?: "{}"
        val json = runCatching { JSONObject(raw) }.getOrDefault(JSONObject())
        json.put(text, System.currentTimeMillis())
        prefs.edit().putString(KEY_SENSITIVE, json.toString()).apply()
    }

    private fun cleanupExpired(expiryMs: Long = 3 * 60 * 1000L) {
        val raw = prefs.getString(KEY_SENSITIVE, null) ?: return
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return
        val now = System.currentTimeMillis()
        val expired = mutableListOf<String>()
        val it = json.keys()
        while (it.hasNext()) {
            val key = it.next()
            val timestamp = json.optLong(key, 0L)
            if (now - timestamp > expiryMs) {
                expired.add(key)
            }
        }

        if (expired.isNotEmpty()) {
            val currentItems = read(ITEMS).filter { it !in expired }
            persist(ITEMS, currentItems)
            expired.forEach { json.remove(it) }
            prefs.edit().putString(KEY_SENSITIVE, json.toString()).apply()
        }
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
        const val FILE = "slashboard_clipboard"
        const val MAXIMUM_ITEMS = 20
        const val MAXIMUM_LENGTH = 2000
        private const val ITEMS = "items"
        private const val PINNED = "pinned"
        private const val KEY_SENSITIVE = "sensitive_timestamps"
    }
}
