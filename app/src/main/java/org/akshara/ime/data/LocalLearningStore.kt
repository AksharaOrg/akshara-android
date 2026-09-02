package org.akshara.ime.data

import android.content.Context
import org.json.JSONObject

class LocalLearningStore(context: Context) {
    private val prefs = context.getSharedPreferences("akshara_learning", Context.MODE_PRIVATE)
    fun record(word: String, previous: String?) {
        if (word.isBlank()) return
        val words = read("words"); words[word] = (words[word] ?: 0) + 1; trim(words, 512); write("words", words)
        if (!previous.isNullOrBlank()) {
            val key = "bigram:$previous"; val followers = read(key); followers[word] = (followers[word] ?: 0) + 1; trim(followers, 48); write(key, followers)
        }
    }
    fun words() = read("words")
    fun followers(previous: String?) = if (previous == null) emptyMap() else read("bigram:$previous")
    fun clear() = prefs.edit().clear().apply()
    private fun read(key: String): MutableMap<String, Int> {
        val json = runCatching { JSONObject(prefs.getString(key, "{}") ?: "{}") }.getOrDefault(JSONObject())
        return json.keys().asSequence().associateWith { json.optInt(it) }.toMutableMap()
    }
    private fun write(key: String, map: Map<String, Int>) = prefs.edit().putString(key, JSONObject(map).toString()).apply()
    private fun trim(map: MutableMap<String, Int>, max: Int) {
        if (map.size > max) map.entries.sortedBy { it.value }.take(map.size - max).forEach { map.remove(it.key) }
    }
}
