package org.akshara.ime.data

import org.akshara.ime.engine.SmartPhoneticV2

/**
 * Sound-alike disambiguation for Smart Phonetic v2: a port of `lexicon.py` from the
 * sinhala-phonetic-orthography research repo. Words are indexed by a sound key that erases the
 * distinctions speakers don't hear or don't write in Latin script (aspiration, ණ/න, ළ/ල, ශ/ෂ/ස,
 * ද/ඩ, vowel length, sanyaka vs cluster …), so "honda" finds හොඳ although the rules spell හොන්ද.
 * Checked against the reference by `SmartPhoneticV2Test`.
 */
class SoundLexicon(rows: Iterable<Pair<String, Int>>) {
    val count = HashMap<String, Int>()
    private val byKey = HashMap<String, MutableList<String>>()
    private val keys: List<String>

    init {
        rows.forEach { (word, n) -> if (word.isNotEmpty()) normalize(word).let { count[it] = (count[it] ?: 0) + n } }
        count.keys.forEach { byKey.getOrPut(soundKey(it)) { ArrayList(1) }.add(it) }
        keys = byKey.keys.sorted()
    }

    fun exact(key: String): List<String> = byKey[key].orEmpty()

    fun prefix(key: String, limit: Int = 200): List<String> {
        val out = ArrayList<String>()
        var i = firstKeyAtOrAfter(key)
        while (i < keys.size && keys[i].startsWith(key) && out.size < limit) out += byKey.getValue(keys[i++])
        return out
    }

    /** True when some word's sound key starts with [key]; used to weigh touch input towards real words. */
    fun hasPrefix(key: String): Boolean = firstKeyAtOrAfter(key).let { it < keys.size && keys[it].startsWith(key) }

    /** Ranked Sinhala spellings for a romanized word, or for a word prefix with [partial]. */
    fun candidates(
        roman: String, limit: Int = 5, partial: Boolean = false, options: SmartPhoneticV2.Options = SmartPhoneticV2.Options()
    ): List<String> {
        val spelled = SmartPhoneticV2.transliterate(roman, options)
        val key = soundKey(spelled)
        val byFrequency = compareByDescending<String> { count[it] ?: 0 }.thenBy { it }
        // An incomplete word: its last consonant may still take a vowel, so drop a trailing hal.
        if (partial) return prefix(key.removeSuffix(HAL)).distinct().sortedWith(byFrequency).take(limit)
        var ranked = exact(key).sortedWith(byFrequency)
        if (spelled in count && isExplicit(roman)) ranked = listOf(spelled) + ranked.filter { it != spelled }   // explicit markers beat frequency
        else if (spelled !in ranked) ranked = ranked + spelled                                                  // the rule spelling is always included
        return ranked.take(limit)
    }

    private fun firstKeyAtOrAfter(key: String): Int {
        var lo = 0
        var hi = keys.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (keys[mid] < key) lo = mid + 1 else hi = mid
        }
        return lo
    }

    companion object {
        const val HAL = "්"
        private const val ZWJ = "‍"
        private val JOIN = Regex("([ක-ෆ])$HAL(?!$ZWJ)(?=[යර])")   // lookahead: ය/ර may start the next join

        // Romanization markers that pin down a distinction the sound key erases.
        private val EXPLICIT = Regex("[KCGJTDNLPBSWVUIEOAXRMH]|z[a-zA-Z]|aa|ii|uu|ee|oo|ae|thh|dh|kh|gh|chh|jh|ph|bh|x")

        private val FOLD_SEQUENCES = listOf(
            "ෛ" to "යි", "ඓ" to "අයි", "ෞ" to "වු", "ඖ" to "අවු",
            "ෘ" to HAL + "රු", "ෲ" to HAL + "රු", "ඍ" to "රු", "ඎ" to "රු",
            "ඥ" to "ග" + HAL + "න",                                                        // G-NS-12
            "ඟ" to "න" + HAL + "ග", "ඦ" to "න" + HAL + "ජ", "ඬ" to "න" + HAL + "ද",           // R-03
            "ඳ" to "න" + HAL + "ද", "ඹ" to "ම" + HAL + "බ",
            "ං" to "න" + HAL, "ඞ" + HAL to "න" + HAL                                        // R-11
        )
        private val FOLD_CHARS: Map<Char, String> = mapOf(
            'ඛ' to "ක", 'ඝ' to "ග", 'ඡ' to "ච", 'ඣ' to "ජ", 'ඨ' to "ට", 'ඪ' to "ද", 'ථ' to "ත", 'ධ' to "ද",
            'ඵ' to "ප", 'භ' to "බ", 'ඩ' to "ද",                                              // G-SP-01, G-SP-06, R-01
            'ණ' to "න", 'ළ' to "ල", 'ශ' to "ස", 'ෂ' to "ස", 'ඤ' to "න",                       // G-SP-02…05, G-NS-13
            'ආ' to "අ", 'ඊ' to "ඉ", 'ඌ' to "උ", 'ඒ' to "එ", 'ඕ' to "ඔ", 'ඇ' to "එ", 'ඈ' to "එ",  // G-SP-07, G-TY-04
            'ා' to "", 'ී' to "ි", 'ූ' to "ු", 'ේ' to "ෙ", 'ෝ' to "ො", 'ැ' to "ෙ", 'ෑ' to "ෙ",
            '‍' to ""
        )

        /** Restores the mandatory ZWJ in C ් ය / C ් ර (never after ර: G-HC-14, R-09). */
        fun normalize(word: String): String =
            JOIN.replace(word) { it.groupValues[1] + HAL + (if (it.groupValues[1] != "ර") ZWJ else "") }

        fun soundKey(text: String): String {
            var folded = text
            FOLD_SEQUENCES.forEach { (from, to) -> folded = folded.replace(from, to) }
            val out = StringBuilder(folded.length)
            folded.forEach { ch -> FOLD_CHARS[ch]?.let(out::append) ?: out.append(ch) }
            return out.toString()
        }

        fun isExplicit(roman: String) = EXPLICIT.containsMatchIn(roman)

        /** Parses "word<TAB>count" lines like the reference: rows whose count isn't a number are skipped. */
        fun parse(lines: Sequence<String>): List<Pair<String, Int>> = lines.mapNotNull { line ->
            val tab = line.indexOf('\t')
            if (tab <= 0) return@mapNotNull null
            val n = line.substring(tab + 1).trimEnd('\r')
            if (n.isEmpty() || !n.all(Char::isDigit)) null else line.substring(0, tab) to n.toInt()
        }.toList()
    }
}
