package org.akshara.ime.engine

/**
 * Smart Phonetic v2: a port of `to_sinhala()` (default options) from the sinhala-phonetic-orthography
 * research repo, `src/sinhala_orthography/romanization.py`. The tables mirror the JSON tables in its `data` folder
 * (normal mode); rule ids refer to its `docs/00-rules.md` and `docs/07-phonetic-romanization.md`.
 *
 * Don't change behaviour here first: change the research repo, then port. `SmartPhoneticV2Test`
 * checks this port against `smart_phonetic_v2_golden.tsv`, generated from the reference by
 * `akshara-phonetics/tools/build_golden.py`.
 */
object SmartPhoneticV2 {
    private const val HAL = "්"
    private const val ZWJ = "‍"
    private const val ANUSVARA = "ං"
    private const val NGA = "ඞ"
    private val SANYAKA = setOf("ඟ", "ඦ", "ඬ", "ඳ", "ඹ")
    private val NO_HAL = SANYAKA + "ළ"                                         // G-HC-06, G-HC-07
    private val PLAIN = mapOf("ඟ" to "ග", "ඦ" to "ජ", "ඬ" to "ඩ", "ඳ" to "ද", "ඹ" to "බ")  // G-PH-01
    private val VELARS = setOf("ක", "ඛ", "ග", "ඝ")                             // R-11
    private val FRONT = setOf("i", "ii", "e", "ee", "ae", "aee", "ai")
    private val BACK = setOf("u", "uu", "o", "oo", "au")

    private sealed interface Token
    private data class Consonant(val letter: String, val seq: String) : Token
    private data class Vowel(val id: String, val independent: String, val sign: String) : Token
    private data class Mark(val output: String, val seq: String) : Token { val anusvara get() = output == ANUSVARA }
    private data class Literal(val ch: Char) : Token

    private val consonants = listOf(
        "k" to "ක", "c" to "ක", "kh" to "ඛ", "K" to "ඛ", "C" to "ඛ", "g" to "ග", "gh" to "ඝ", "G" to "ඝ",
        "X" to "ඞ", "zg" to "ඟ", "ch" to "ච", "chh" to "ඡ", "j" to "ජ", "jh" to "ඣ", "J" to "ඣ",
        "zk" to "ඤ", "zh" to "ඥ", "t" to "ට", "T" to "ඨ", "D" to "ඩ", "Dh" to "ඪ", "N" to "ණ", "zD" to "ඬ",
        "th" to "ත", "thh" to "ථ", "d" to "ද", "q" to "ද", "dh" to "ධ", "dhh" to "ධ", "n" to "න",
        "zd" to "ඳ", "zdh" to "ඳ", "zq" to "ඳ", "p" to "ප", "ph" to "ඵ", "P" to "ඵ", "b" to "බ", "bh" to "භ",
        "m" to "ම", "B" to "ඹ", "y" to "ය", "r" to "ර", "l" to "ල", "w" to "ව", "v" to "ව", "W" to "ව",
        "V" to "ව", "sh" to "ශ", "Sh" to "ෂ", "S" to "ෂ", "s" to "ස", "h" to "හ", "L" to "ළ", "f" to "ෆ"
    )
    private val vowels = listOf(
        listOf("a", "a", "අ", ""), listOf("aa", "aa", "ආ", "ා"), listOf("A", "ae", "ඇ", "ැ"),
        listOf("ae", "ae", "ඇ", "ැ"), listOf("Aa", "aee", "ඈ", "ෑ"), listOf("AA", "aee", "ඈ", "ෑ"),
        listOf("aee", "aee", "ඈ", "ෑ"), listOf("i", "i", "ඉ", "ි"), listOf("ii", "ii", "ඊ", "ී"),
        listOf("I", "ii", "ඊ", "ී"), listOf("u", "u", "උ", "ු"), listOf("U", "u", "උ", "ු"),
        listOf("uu", "uu", "ඌ", "ූ"), listOf("UU", "uu", "ඌ", "ූ"), listOf("Uu", "uu", "ඌ", "ූ"),
        listOf("R", "ru", "ඍ", "ෘ"), listOf("RR", "ruu", "ඎ", "ෲ"), listOf("e", "e", "එ", "ෙ"),
        listOf("ee", "ee", "ඒ", "ේ"), listOf("E", "ai", "ඓ", "ෛ"), listOf("o", "o", "ඔ", "ො"),
        listOf("O", "o", "ඔ", "ො"), listOf("oo", "oo", "ඕ", "ෝ"), listOf("OO", "oo", "ඕ", "ෝ"),
        listOf("Oo", "oo", "ඕ", "ෝ"), listOf("Au", "au", "ඖ", "ෞ"), listOf("AU", "au", "ඖ", "ෞ")
    )
    private val marks = listOf("x" to ANUSVARA, "zn" to ANUSVARA, "M" to ANUSVARA, "H" to "ඃ")

    /** All sequences, longest first; ties keep table order (consonants, vowels, marks). */
    private val sequences: List<Pair<String, Token>> = (
        consonants.map { (seq, letter) -> seq to Consonant(letter, seq) } +
            vowels.map { (seq, id, independent, sign) -> seq to Vowel(id, independent, sign) } +
            marks.map { (seq, output) -> seq to Mark(output, seq) }
        ).sortedByDescending { it.first.length }

    private fun tokenize(source: String): List<Token> {
        val tokens = ArrayList<Token>(source.length)
        var i = 0
        while (i < source.length) {
            val match = sequences.firstOrNull { source.startsWith(it.first, i) }
            if (match != null) {
                tokens += match.second; i += match.first.length
            } else {
                if (source[i] != 'z') tokens += Literal(source[i])   // an unknown z-combination is dropped
                i++
            }
        }
        return tokens
    }

    /** G-VS-06: ය after front vowels, ව after back; after a/aa, decided by the next vowel. */
    private fun glide(previous: String?, vowel: String) = when {
        previous in FRONT -> "ය"
        previous in BACK -> "ව"
        vowel in FRONT -> "ය"
        else -> "ව"
    }

    private enum class State { VOWEL, ANUSVARA, HAL }

    fun transliterate(source: String): String {
        val tokens = tokenize(source)
        val out = ArrayList<String>(tokens.size * 2)
        var state: State? = null        // null: word start
        var previousVowel: String? = null
        var j = 0
        while (j < tokens.size) {
            val token = tokens[j]
            val next = tokens.getOrNull(j + 1)
            when (token) {
                is Consonant -> {
                    var letter = token.letter
                    val after = tokens.getOrNull(j + 2)
                    if (state == null) PLAIN[letter]?.let { letter = it }
                    if (letter == NGA && next is Vowel) {
                        if (state == null) { out += token.seq; j++; continue }   // G-PH-01: ඞ never starts a word
                        letter = "ඟ"                                              // G-HC-08: /ŋ/ + vowel is ඟ
                    }
                    if (letter == NGA && state == null) { out += token.seq; j++; continue }
                    // R-11: n + velar after a vowel → ං; word-final "ng" → ං
                    if (letter == "න" && token.seq == "n" && state == State.VOWEL && next is Consonant && next.letter in VELARS) {
                        out += ANUSVARA; state = State.ANUSVARA
                        j += if (next.seq == "g" && (after == null || after is Literal)) 2 else 1
                        continue
                    }
                    out += letter
                    when (next) {
                        is Vowel -> { out += next.sign; state = State.VOWEL; previousVowel = next.id; j += 2 }
                        is Mark -> { state = State.VOWEL; previousVowel = "a"; j++ }   // ං/ඃ need a vowel base
                        is Consonant -> {
                            val nextLetter = next.letter
                            if (letter in NO_HAL || nextLetter in SANYAKA) {
                                state = State.VOWEL; previousVowel = "a"                // no hal here: keep inherent a
                            } else {
                                out += when {
                                    letter == NGA -> HAL
                                    nextLetter == "ය" -> if (letter != "ර") HAL + ZWJ else HAL   // G-HC-11, G-HC-14, R-09
                                    nextLetter == "ර" -> if (letter == "ර") HAL else HAL + ZWJ   // G-HC-12, R-07
                                    else -> HAL                                                    // R-08: plain repaya
                                }
                                state = State.HAL
                            }
                            j++
                        }
                        else -> {   // end of word
                            if (letter in NO_HAL) { state = State.VOWEL; previousVowel = "a" } else { out += HAL; state = State.HAL }
                            j++
                        }
                    }
                }
                is Vowel -> {
                    when (state) {
                        State.VOWEL -> out += glide(previousVowel, token.id) + token.sign
                        State.ANUSVARA -> out[out.lastIndex] = "ම" + token.sign            // G-NS-04: ං never before a vowel
                        else -> out += if (token.id == "ruu") "ඍ" else token.independent  // G-VS-08: ඎ is archaic
                    }
                    state = State.VOWEL; previousVowel = token.id; j++
                }
                is Mark -> {
                    if (token.anusvara && next is Consonant && next.letter in SANYAKA) { j++; continue }   // G-NS-09
                    if (state == State.VOWEL) {
                        out += token.output; state = if (token.anusvara) State.ANUSVARA else State.VOWEL
                    } else {
                        out += token.seq; state = null   // no base: leave the romanization as written
                    }
                    j++
                }
                is Literal -> { out += token.ch.toString(); state = null; previousVowel = null; j++ }
            }
        }
        return out.joinToString("")
    }
}
