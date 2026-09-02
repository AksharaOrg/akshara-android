package org.akshara.ime.data

import android.content.Context
import org.akshara.ime.R
import org.json.JSONObject

data class EmojiCategory(val name: String, val icon: String, val emoji: List<String>)

class EmojiRepository(context: Context) {
    private val index = mutableMapOf<String, MutableSet<String>>()
    private val catalog = linkedSetOf<String>()
    init {
        val text = context.resources.openRawResource(R.raw.sinhala_emoji_index).bufferedReader().use { it.readText() }
        runCatching {
            val root = JSONObject(text)
            root.keys().forEach { key ->
                val value = root.get(key)
                when (value) {
                    is String -> index.getOrPut(key.lowercase()) { linkedSetOf() }.add(value).also { catalog.add(value) }
                    is org.json.JSONArray -> repeat(value.length()) { i -> value.getString(i).let { emoji -> index.getOrPut(key.lowercase()) { linkedSetOf() }.add(emoji); catalog.add(emoji) } }
                }
            }
        }
    }
    val allEmoji: List<String> get() = catalog.toList()
    private val englishNames: Map<String, String> by lazy {
        catalog.associateWith { emoji ->
            emoji.codePoints().toArray().map { cp -> Character.getName(cp).orEmpty() }.filter { it.isNotEmpty() }.joinToString(" ").lowercase()
        }
    }
    val categories: List<EmojiCategory> by lazy {
        val buckets = linkedMapOf(
            "Smileys" to Pair("😀", mutableListOf<String>()), "People" to Pair("👋", mutableListOf()),
            "Nature" to Pair("🐻", mutableListOf()), "Food" to Pair("🍜", mutableListOf()),
            "Activities" to Pair("⚽", mutableListOf()), "Travel" to Pair("🚗", mutableListOf()),
            "Objects" to Pair("💡", mutableListOf()), "Symbols" to Pair("❤️", mutableListOf())
        )
        catalog.forEach { emoji -> buckets.getValue(categoryFor(emoji)).second.add(emoji) }
        buckets.map { (name, pair) -> EmojiCategory(name, pair.first, pair.second.distinct()) }
    }

    fun search(query: String, max: Int = 48, scanNames: Boolean = true): List<String> {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return emptyList()
        val indexed = index.asSequence().filter { (key, _) -> key.contains(needle) }
            .sortedBy { (key, _) -> when { key == needle -> 0; key.startsWith(needle) -> 1; key.split(Regex("[^\\p{L}\\p{N}]+")).any { it.startsWith(needle) } -> 2; else -> 3 } }
            .flatMap { it.value.asSequence() }
        if (!scanNames) return indexed.distinct().take(max).toList()
        val unicodeNamed = englishNames.asSequence().filter { (_, name) -> name.contains(needle) }
            .sortedBy { (_, name) -> if (name.startsWith(needle)) 0 else 1 }.map { it.key }
        return (indexed + unicodeNamed).distinct().take(max).toList()
    }

    private fun categoryFor(emoji: String): String {
        val cp = emoji.codePointAt(0)
        return when {
            cp in 0x1F600..0x1F64F || cp in 0x1F910..0x1F92F || cp in 0x1F970..0x1F97F -> "Smileys"
            cp in 0x1F466..0x1F487 || cp in 0x1F590..0x1F596 || cp in 0x1F645..0x1F64F || cp in 0x1F9B0..0x1F9DD -> "People"
            cp in 0x1F32D..0x1F37F || cp in 0x1F950..0x1F96F -> "Food"
            cp in 0x1F3A0..0x1F3FF -> "Activities"
            cp in 0x1F680..0x1F6FF -> "Travel"
            cp in 0x1F400..0x1F43E || cp in 0x1F980..0x1F9AE || cp in 0x1F300..0x1F32C -> "Nature"
            cp in 0x1F4A0..0x1F5FF -> "Objects"
            else -> "Symbols"
        }
    }
    companion object {
        fun withTone(emoji: String, tone: String): String {
            if (tone.isEmpty() || emoji.codePoints().anyMatch { it in 0x1F3FB..0x1F3FF }) return emoji
            val toneable = setOf(0x1F44D,0x1F44E,0x1F44F,0x1F64F,0x1F4AA,0x1F44B,0x1F91D,0x1FAF6)
            val cps = emoji.codePoints().toArray(); if (cps.none { it in toneable }) return emoji
            val out = StringBuilder(); cps.forEach { cp -> out.appendCodePoint(cp); if (cp in toneable) out.append(tone) }; return out.toString()
        }
    }
}
