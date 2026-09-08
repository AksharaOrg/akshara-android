package org.akshara.ime.data

import android.content.Context
import org.akshara.ime.R
import org.json.JSONObject

data class EmojiCategory(val name: String, val icon: String, val emoji: List<String>)

class EmojiRepository(context: Context) {
    private val index = mutableMapOf<String, MutableSet<String>>()
    private val catalog = linkedSetOf<String>()
    private val groups = mutableMapOf<String, String>()
    private val order = mutableMapOf<String, Int>()
    private val names = mutableMapOf<String, String>()
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
        val metadata = org.json.JSONArray(context.resources.openRawResource(R.raw.emoji_catalog).bufferedReader().use { it.readText() })
        val categoryNames = mapOf("smileys" to SMILEYS, "animals" to NATURE, "food" to FOOD,
            "activity" to ACTIVITY, "travel" to TRAVEL, "objects" to OBJECTS, "symbols" to SYMBOLS, "flags" to FLAGS)
        repeat(metadata.length()) { i ->
            val row = metadata.getJSONArray(i)
            val emoji = row.getString(0)
            catalog.add(emoji)
            groups[emoji] = categoryNames.getValue(row.getString(1))
            order[emoji] = row.getInt(2)
            val terms = row.getJSONArray(3)
            names[emoji] = (0 until terms.length()).joinToString(" ") { terms.getString(it) }.lowercase()
        }
    }
    val allEmoji: List<String> get() = catalog.toList()
    private val englishNames: Map<String, String> by lazy {
        catalog.associateWith { emoji ->
            names[emoji] ?: emoji.codePoints().toArray().map { cp -> Character.getName(cp).orEmpty() }.filter { it.isNotEmpty() }.joinToString(" ").lowercase()
        }
    }
    val categories: List<EmojiCategory> by lazy {
        val buckets = linkedMapOf(
            SMILEYS to Pair("😀", mutableListOf<String>()),
            NATURE to Pair("🐻", mutableListOf()),
            FOOD to Pair("🍔", mutableListOf()),
            ACTIVITY to Pair("⚽", mutableListOf()),
            TRAVEL to Pair("🚗", mutableListOf()),
            OBJECTS to Pair("💡", mutableListOf()),
            SYMBOLS to Pair("❤️", mutableListOf()),
            FLAGS to Pair("🏁", mutableListOf())
        )
        catalog.forEach { emoji -> buckets.getValue(categoryFor(emoji)).second.add(emoji) }
        extraFlags().forEach { emoji ->
            val list = buckets.getValue(FLAGS).second
            if (list.none { it == emoji }) list.add(emoji)
        }
        buckets.map { (name, pair) -> EmojiCategory(name, pair.first, pair.second.distinct()
            .sortedWith(compareBy<String> { order[it] ?: order[it + "\uFE0F"] ?: Int.MAX_VALUE }.thenBy { it })) }
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
        (groups[emoji] ?: groups[emoji + "\uFE0F"])?.let { return it }
        val cp = emoji.codePointAt(0)
        return when {
            isFlag(cp) -> FLAGS
            cp in 0x1F600..0x1F64F || cp in 0x1F910..0x1F92F || cp in 0x1F970..0x1F97A ||
                cp in 0x1F440..0x1F450 || cp in 0x1F466..0x1F487 || cp in 0x1F590..0x1F596 ||
                cp in 0x1F90C..0x1F90F || cp in 0x1F91A..0x1F91F || cp in 0x1F9B0..0x1F9DF ||
                cp in 0x1FAC0..0x1FAC5 || cp in 0x261D..0x261D || cp in 0x270A..0x270D ||
                cp == 0x1F4AA || cp == 0x1F44D || cp == 0x1F44E || cp == 0x1F44F -> SMILEYS
            cp in 0x1F32D..0x1F37F || cp in 0x1F950..0x1F96F || cp in 0x1F9C0..0x1F9CB -> FOOD
            cp in 0x1F3A0..0x1F3C5 || cp in 0x1F3C7..0x1F3CA || cp in 0x1F3CF..0x1F3D3 ||
                cp in 0x1F3F8..0x1F3FA || cp in 0x1F93A..0x1F94F || cp == 0x26BD || cp == 0x26BE -> ACTIVITY
            cp in 0x1F680..0x1F6FF || cp in 0x1F3D4..0x1F3F0 || cp in 0x1F5FA..0x1F5FF ||
                cp in 0x1F30D..0x1F30F -> TRAVEL
            cp in 0x1F400..0x1F43F || cp in 0x1F980..0x1F9AE || cp in 0x1F300..0x1F32C ||
                cp in 0x1F330..0x1F343 || cp in 0x1F98B..0x1F9A2 -> NATURE
            cp in 0x1F4A0..0x1F5F9 || cp in 0x1F321..0x1F32C || cp in 0x1F9E0..0x1F9FF ||
                cp in 0x1FA70..0x1FA87 || cp in 0x1F50D..0x1F52E -> OBJECTS
            else -> SYMBOLS
        }
    }

    private fun isFlag(cp: Int) =
        cp in 0x1F1E6..0x1F1FF || cp == 0x1F3C1 || cp == 0x1F6A9 || cp == 0x1F38C || cp == 0x1F3F3 || cp == 0x1F3F4

    companion object {
        const val SMILEYS = "Smileys & People"
        const val NATURE = "Animals & Nature"
        const val FOOD = "Food & Drink"
        const val ACTIVITY = "Activity"
        const val TRAVEL = "Travel & Places"
        const val OBJECTS = "Objects"
        const val SYMBOLS = "Symbols"
        const val FLAGS = "Flags"

        private val ISO_FLAGS = (
            "AD AE AF AG AI AL AM AO AR AS AT AU AW AX AZ BA BB BD BE BF BG BH BI BJ BL BM BN BO BQ BR BS BT BW BY BZ " +
            "CA CC CD CF CG CH CI CK CL CM CN CO CR CU CV CW CX CY CZ DE DJ DK DM DO DZ EC EE EG EH ER ES ET FI FJ FK " +
            "FM FO FR GA GB GD GE GF GG GH GI GL GM GN GP GQ GR GT GU GW GY HK HN HR HT HU ID IE IL IM IN IO IQ IR IS " +
            "IT JE JM JO JP KE KG KH KI KM KN KP KR KW KY KZ LA LB LC LI LK LR LS LT LU LV LY MA MC MD ME MG MH MK ML " +
            "MM MN MO MP MQ MR MS MT MU MV MW MX MY MZ NA NC NE NF NG NI NL NO NP NR NU NZ OM PA PE PF PG PH PK PL PM " +
            "PN PR PS PT PW PY QA RE RO RS RU RW SA SB SC SD SE SG SH SI SJ SK SL SM SN SO SR SS ST SV SX SY SZ TC TD " +
            "TG TH TJ TK TL TM TN TO TR TT TV TW TZ UA UG UN US UY UZ VA VC VE VG VI VN VU WF WS XK YE YT ZA ZM ZW"
        ).split(" ")

        private fun extraFlags(): List<String> {
            val special = listOf("🏁", "🚩", "🎌", "🏴", "🏳️", "🏳️‍🌈", "🏳️‍⚧️", "🏴‍☠️")
            val countries = ISO_FLAGS.map { code ->
                buildString { code.forEach { ch -> appendCodePoint(0x1F1E6 + (ch - 'A')) } }
            }
            return special + countries
        }

        fun withTone(emoji: String, tone: String): String {
            if (tone.isEmpty() || emoji.codePoints().anyMatch { it in 0x1F3FB..0x1F3FF }) return emoji
            val toneable = setOf(0x1F44D,0x1F44E,0x1F44F,0x1F64F,0x1F4AA,0x1F44B,0x1F91D,0x1FAF6)
            val cps = emoji.codePoints().toArray(); if (cps.none { it in toneable }) return emoji
            val out = StringBuilder(); cps.forEach { cp -> out.appendCodePoint(cp); if (cp in toneable) out.append(tone) }; return out.toString()
        }
    }
}
