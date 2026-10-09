package org.akshara.ime.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Clipboard history kept on this device. Recent clips expire after [RECENT_LIFETIME_MS]; pinned clips stay until
 * deleted. Each system clip is captured once, so a clip the user deletes or clears does not come back while it is
 * still on the system clipboard.
 */
class ClipboardHistoryStore(context: Context, private val now: () -> Long = System::currentTimeMillis) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun items(): List<String> = recent().map { it.text }
    fun pinnedItems(): List<String> = read(PINNED).map { it.text }

    /**
     * Saves the system clipboard's text once per copy. [source] identifies the copy (for example its timestamp),
     * so reading the same clipboard again, after the clip was deleted, does not save it again.
     */
    fun capture(text: String, source: String) {
        val stored = sanitize(text) ?: return
        val key = "$source:${stored.hashCode()}"
        if (prefs.getString(LAST_CAPTURED, null) == key) return
        prefs.edit().putString(LAST_CAPTURED, key).apply()
        if (stored in pinnedItems()) return
        add(stored)
    }

    fun add(text: String) {
        val stored = sanitize(text) ?: return
        persist(ITEMS, (listOf(Clip(stored, now())) + recent().filter { it.text != stored }).take(MAXIMUM_ITEMS))
    }

    fun pin(text: String) {
        val recent = recent()
        if (recent.none { it.text == text }) return
        persist(ITEMS, recent.filter { it.text != text })
        persist(PINNED, listOf(Clip(text, now())) + read(PINNED).filter { it.text != text })
    }

    /** Moves a pinned clip back to the top of Recent, where it expires like any other recent clip. */
    fun unpin(text: String) {
        val pinned = read(PINNED)
        if (pinned.none { it.text == text }) return
        persist(PINNED, pinned.filter { it.text != text })
        persist(ITEMS, (listOf(Clip(text, now())) + recent().filter { it.text != text }).take(MAXIMUM_ITEMS))
    }

    fun remove(text: String) = persist(ITEMS, recent().filter { it.text != text })

    fun removePinned(text: String) = persist(PINNED, read(PINNED).filter { it.text != text })

    fun clearHistory() = prefs.edit().remove(ITEMS).apply()

    /** Deletes every clip. The last captured copy is remembered so it is not saved again. */
    fun clear() = prefs.edit().remove(ITEMS).remove(PINNED).apply()

    private fun recent(): List<Clip> {
        val cutoff = now() - RECENT_LIFETIME_MS
        return read(ITEMS).filter { it.time in cutoff..now() }
    }

    private fun sanitize(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        return trimmed.take(MAXIMUM_LENGTH)
    }

    private fun read(key: String): List<Clip> {
        val raw = runCatching { JSONArray(prefs.getString(key, "[]")) }.getOrDefault(JSONArray())
        var legacy = false
        val clips = (0 until raw.length()).mapNotNull { index ->
            when (val value = raw.opt(index)) {
                is JSONObject -> Clip(value.optString(TEXT), value.optLong(TIME))
                // Clips saved before timestamps start their hour now
                is String -> Clip(value, now()).also { legacy = true }
                else -> null
            }?.takeIf { it.text.isNotBlank() }
        }
        if (legacy) persist(key, clips)
        return clips
    }

    private fun persist(key: String, values: List<Clip>) {
        val array = JSONArray()
        values.forEach { array.put(JSONObject().put(TEXT, it.text).put(TIME, it.time)) }
        prefs.edit().putString(key, array.toString()).apply()
    }

    private data class Clip(val text: String, val time: Long)

    companion object {
        const val FILE = "akshara_clipboard"
        const val MAXIMUM_ITEMS = 20
        const val MAXIMUM_LENGTH = 2000
        const val RECENT_LIFETIME_MS = 60 * 60 * 1000L
        private const val ITEMS = "items"
        private const val PINNED = "pinned"
        private const val LAST_CAPTURED = "last_captured"
        private const val TEXT = "text"
        private const val TIME = "time"
    }
}
