package org.akshara.ime.engine

import java.text.Normalizer

enum class InputMode(val title: String) {
    WIJESEKARA("Wijesekara"), PHONETIC("Phonetic"), SMART_PHONETIC("Smart Phonetic")
}

/** Direct port of ios/Shared/SinhalaEngine.swift. Keep rule ordering significant. */
object SinhalaEngine {
    private val consonants = listOf(
        "ng" to "ඞ", "gn" to "ඥ", "ny" to "ඤ", "kh" to "ඛ", "gh" to "ඝ",
        "ch" to "ච", "jh" to "ඣ", "Th" to "ඨ", "Dh" to "ඪ", "th" to "ත",
        "dh" to "ද", "ph" to "ඵ", "bh" to "භ", "sh" to "ශ", "Sh" to "ෂ",
        "k" to "ක", "g" to "ග", "c" to "ක", "j" to "ජ", "C" to "ඡ",
        "T" to "ට", "D" to "ඩ", "N" to "ණ", "t" to "ට", "d" to "ඩ",
        "n" to "න", "p" to "ප", "b" to "බ", "m" to "ම", "y" to "ය",
        "r" to "ර", "l" to "ල", "L" to "ළ", "v" to "ව", "w" to "ව",
        "s" to "ස", "h" to "හ", "f" to "ෆ", "R" to "ර", "Y" to "ය"
    )
    private data class Vowel(val key: String, val independent: String, val sign: String?)
    private val vowels = listOf(
        Vowel("aee", "ඈ", "ෑ"), Vowel("ae", "ඇ", "ැ"), Vowel("aa", "ආ", "ා"),
        Vowel("ii", "ඊ", "ී"), Vowel("uu", "ඌ", "ූ"), Vowel("ee", "ඒ", "ේ"),
        Vowel("ai", "ඓ", "ෛ"), Vowel("oo", "ඕ", "ෝ"), Vowel("au", "ඖ", "ෞ"),
        Vowel("A", "ආ", "ා"), Vowel("I", "ඊ", "ී"), Vowel("U", "ඌ", "ූ"),
        Vowel("E", "ඒ", "ේ"), Vowel("O", "ඕ", "ෝ"), Vowel("a", "අ", ""),
        Vowel("i", "ඉ", "ි"), Vowel("u", "උ", "ු"), Vowel("e", "එ", "ෙ"), Vowel("o", "ඔ", "ො")
    )
    private val smartConsonants = listOf(
        "chh" to "ඡ", "thh" to "ථ", "dhh" to "ධ", "zdh" to "ඳ", "ch" to "ච",
        "th" to "ත", "dh" to "ද", "sh" to "ශ", "Sh" to "ෂ", "kh" to "ඛ",
        "gh" to "ඝ", "ph" to "ඵ", "bh" to "භ", "zg" to "ඟ", "zj" to "ඦ",
        "zd" to "ඬ", "zq" to "ඳ", "zk" to "ඤ", "zh" to "ඥ", "k" to "ක",
        "g" to "ග", "c" to "ක", "j" to "ජ", "t" to "ට", "d" to "ඩ",
        "q" to "ද", "n" to "න", "N" to "ණ", "p" to "ප", "b" to "බ",
        "m" to "ම", "y" to "ය", "r" to "ර", "l" to "ල", "L" to "ළ",
        "w" to "ව", "v" to "ව", "s" to "ස", "S" to "ෂ", "h" to "හ",
        "f" to "ෆ", "T" to "ඨ", "D" to "ඪ", "B" to "ඹ", "X" to "ඞ",
        "K" to "ඛ", "P" to "ඵ", "W" to "ව", "C" to "ඛ", "V" to "ව",
        "J" to "ඣ", "G" to "ඝ"
    )
    private val smartVowels = listOf(
        Vowel("ruu", "", "ෲ"), Vowel("Aa", "ඈ", "ෑ"), Vowel("AA", "ඈ", "ෑ"),
        Vowel("aa", "ආ", "ා"), Vowel("ii", "ඊ", "ී"), Vowel("uu", "ඌ", "ූ"),
        Vowel("UU", "ඌ", "ූ"), Vowel("Uu", "ඌ", "ූ"), Vowel("ee", "ඒ", "ේ"),
        Vowel("ai", "ඓ", "ෛ"), Vowel("oo", "ඕ", "ෝ"), Vowel("OO", "ඕ", "ෝ"),
        Vowel("Oo", "ඕ", "ෝ"), Vowel("au", "ඖ", "ෞ"), Vowel("ou", "ඖ", "ෞ"),
        Vowel("Ru", "ඎ", null), Vowel("ru", "", "ෘ"), Vowel("A", "ඇ", "ැ"),
        Vowel("I", "ඊ", "ී"), Vowel("U", "උ", "ු"), Vowel("E", "ඓ", "ෛ"),
        Vowel("O", "ඔ", "ො"), Vowel("a", "අ", ""), Vowel("i", "ඉ", "ි"),
        Vowel("u", "උ", "ු"), Vowel("e", "එ", "ෙ"), Vowel("o", "ඔ", "ො"), Vowel("R", "ඍ", null)
    )

    private const val JOIN = '\uE000'
    private const val TOUCH = '\uE001'
    private const val REPAYA = '\uE002'
    private const val SANYAKAYA = '\uE003'
    private const val RAKARANSAYA = '\uE004'
    private const val YANSAYA = '\uE005'
    private val slsNormal = mapOf(
        '`' to "$RAKARANSAYA", 'q' to "ු", 'w' to "අ", 'e' to "ැ", 'r' to "ර", 't' to "එ", 'y' to "හ", 'u' to "ම", 'i' to "ස", 'o' to "ද", 'p' to "ච", '[' to "ඤ", ']' to ";", '\\' to "$JOIN",
        'a' to "්", 's' to "ි", 'd' to "ා", 'f' to "ෙ", 'g' to "ට", 'h' to "ය", 'j' to "ව", 'k' to "න", 'l' to "ක", ';' to "ත", '\'' to ".",
        'z' to "'", 'x' to "ං", 'c' to "ජ", 'v' to "ඩ", 'b' to "ඉ", 'n' to "බ", 'm' to "ප", ',' to "ල", '.' to "ග", '/' to "/"
    )
    private val slsShift = mapOf(
        '~' to "$REPAYA", 'Q' to "ූ", 'W' to "උ", 'E' to "ෑ", 'R' to "ඍ", 'T' to "ඔ", 'Y' to "ශ", 'U' to "ඹ", 'I' to "ෂ", 'O' to "ධ", 'P' to "ඡ", '{' to "ඥ", '}' to ":", '|' to "$TOUCH",
        'A' to "ෟ", 'S' to "ී", 'D' to "ෘ", 'F' to "ෆ", 'G' to "ඨ", 'H' to "$YANSAYA", 'J' to "ළු", 'K' to "ණ", 'L' to "ඛ", ':' to "ථ", '"' to ",",
        'Z' to "\"", 'X' to "ඞ", 'C' to "ඣ", 'V' to "ඪ", 'B' to "ඊ", 'N' to "භ", 'M' to "ඵ", '<' to "ළ", '>' to "ඝ", '?' to "?"
    )

    fun slsCharacter(key: String, shifted: Boolean): String {
        if (key == "rakaranshaya") return if (shifted) "\u200D" else RAKARANSAYA.toString()
        val letter = key.singleOrNull() ?: return key
        return slsCharacter(letter, shifted)
    }

    fun slsCharacter(key: Char, shifted: Boolean): String {
        val shiftedSymbols = mapOf('[' to '{', ']' to '}', '\\' to '|', ';' to ':', '\'' to '"', ',' to '<', '.' to '>', '/' to '?', '`' to '~')
        val lookup = if (shifted) shiftedSymbols[key] ?: key.uppercaseChar() else key.lowercaseChar()
        return (if (shifted) slsShift[lookup] else slsNormal[lookup]) ?: key.toString()
    }

    fun slsKeyLabel(key: Char, shifted: Boolean): String {
        val mapped = slsCharacter(key, shifted)
        if (mapped.codePoints().noneMatch { it in 0xE000..0xE0FF }) return mapped
        return normalizeSls(mapped).ifEmpty { "◌" }
    }

    fun normalizeSls(input: String): String {
        val cps = input.codePoints().toArray(); val out = StringBuilder(); var i = 0
        fun consonant(cp: Int) = cp in 0x0D9A..0x0DC6
        while (i < cps.size) {
            val cp = cps[i]
            if (cp in 0x0D85..0x0D96) {
                val next = cps.getOrNull(i + 1)
                val combined = when (cp to next) {
                    0x0D85 to 0x0DCF -> "ආ"; 0x0D85 to 0x0DD0 -> "ඇ"; 0x0D85 to 0x0DD1 -> "ඈ"
                    0x0D91 to 0x0DCA -> "ඒ"; 0x0D89 to 0x0DD3 -> "ඊ"; 0x0D94 to 0x0DCA -> "ඕ"
                    0x0D94 to 0x0DDF, 0x0D94 to 0x0DD6 -> "ඖ"; 0x0D8B to 0x0DDF, 0x0D8B to 0x0DD6 -> "ඌ"
                    0x0D8D to 0x0DD8 -> "ඎ"; else -> null
                }
                if (combined != null) { out.append(combined); i += 2 } else { out.appendCodePoint(cp); i++ }
                continue
            }
            if (cp == 0x0DD9) {
                var prefixes = 0
                while (i < cps.size && cps[i] == 0x0DD9) { prefixes++; i++ }
                if (i >= cps.size || !consonant(cps[i])) { repeat(prefixes) { out.append("ෙ") }; continue }
                out.appendCodePoint(cps[i++])
                i = appendCluster(cps, i, out)
                if (prefixes >= 2) out.append("ෛ")
                else if (i < cps.size) when (cps[i]) {
                    0x0DCA -> { out.append("ේ"); i++ }
                    0x0DCF -> { i++; if (i < cps.size && cps[i] == 0x0DCA) { out.append("ෝ"); i++ } else out.append("ො") }
                    0x0DDF -> { out.append("ෞ"); i++ }
                    else -> out.append("ෙ")
                } else out.append("ෙ")
                continue
            }
            if (consonant(cp)) {
                out.appendCodePoint(cp)
                i = appendCluster(cps, i + 1, out)
                continue
            }
            when (cp) {
                RAKARANSAYA.code -> { out.append("්‍ර"); i++ }
                YANSAYA.code -> { out.append("්‍ය"); i++ }
                REPAYA.code -> { out.append("ර්‍"); i++ }
                JOIN.code, TOUCH.code -> {
                    val next = cps.getOrNull(i + 1)
                    if (next != null && consonant(next)) {
                        out.append("්‍").appendCodePoint(next)
                        i += 2
                    } else i++
                }
                SANYAKAYA.code -> i++
                else -> { out.appendCodePoint(cp); i++ }
            }
            continue
        }
        return Normalizer.normalize(out, Normalizer.Form.NFC)
    }

    /** Rakaranshaya / yansaya / touching letters belong on the consonant before ෙ becomes ේ. */
    private fun appendCluster(cps: IntArray, start: Int, out: StringBuilder): Int {
        var i = start
        fun consonant(cp: Int) = cp in 0x0D9A..0x0DC6
        while (i < cps.size) {
            when (cps[i]) {
                RAKARANSAYA.code -> { out.append("්‍ර"); i++ }
                YANSAYA.code -> { out.append("්‍ය"); i++ }
                JOIN.code, TOUCH.code -> {
                    val next = cps.getOrNull(i + 1)
                    if (next != null && consonant(next)) {
                        out.append("්‍").appendCodePoint(next)
                        i += 2
                    } else break
                }
                0x200D -> {
                    val next = cps.getOrNull(i + 1)
                    if (next != null && consonant(next)) {
                        out.append("්‍").appendCodePoint(next)
                        i += 2
                    } else {
                        out.append("්‍")
                        i++
                    }
                }
                else -> break
            }
        }
        return i
    }

    fun canExtendPrebase(source: String, suffix: String): Boolean {
        val cps = source.codePoints().toArray(); if (cps.firstOrNull() != 0x0DD9) return false
        val count = cps.takeWhile { it == 0x0DD9 }.size
        val rest = cps.drop(count)
        val hasConsonant = rest.any { it in 0x0D9A..0x0DC6 }
        if (!hasConsonant) return (suffix == "ෙ" && count < 2) || isSinhalaConsonant(suffix)
        if (count != 1 || rest.isEmpty() || rest[0] !in 0x0D9A..0x0DC6) return false
        val suffixCp = suffix.codePoints().findFirst().orElse(-1)
        val clusterTokens = setOf(RAKARANSAYA.code, YANSAYA.code)
        val finishers = setOf(0x0DCA, 0x0DCF, 0x0DDF)
        val after = rest.drop(1)
        val leftover = after.dropWhile { it in clusterTokens }
        if (leftover.isEmpty()) return suffixCp in clusterTokens || suffixCp in finishers
        return leftover.size == 1 && leftover[0] == 0x0DCF && suffix == "්"
    }

    fun combinesWithIndependentVowel(source: String, suffix: String): Boolean {
        val v = source.codePoints().findFirst().orElse(-1); val s = suffix.codePoints().findFirst().orElse(-1)
        return when (v) {
            0x0D85 -> s in setOf(0x0DCF, 0x0DD0, 0x0DD1); 0x0D91 -> s == 0x0DCA
            0x0D89 -> s == 0x0DD3; 0x0D94 -> s in setOf(0x0DCA, 0x0DDF, 0x0DD6)
            0x0D8B -> s in setOf(0x0DDF, 0x0DD6); 0x0D8D -> s == 0x0DD8; else -> false
        }
    }

    fun isSinhalaConsonant(text: String) = text.codePoints().findFirst().orElse(-1) in 0x0D9A..0x0DC6
    fun hasUnicodeScalarPrefix(text: String, prefix: String): Boolean {
        if (prefix.isEmpty()) return true
        if (text.length < prefix.length) return false
        return text.startsWith(prefix)
    }

    fun transliterate(source: String, mode: InputMode): String = when (mode) {
        InputMode.WIJESEKARA -> normalizeSls(source)
        InputMode.SMART_PHONETIC -> transliterateWith(source, smartConsonants, smartVowels, true)
        InputMode.PHONETIC -> transliterateWith(source, consonants, vowels, false)
    }

    private fun transliterateWith(source: String, cs: List<Pair<String, String>>, vs: List<Vowel>, smart: Boolean): String {
        val out = StringBuilder(); var i = 0
        fun consonant() = cs.firstOrNull { source.startsWith(it.first, i) }
        fun vowel() = vs.firstOrNull { source.startsWith(it.key, i) }
        while (i < source.length) {
            val ch = source[i]
            if (ch == 'M' || (smart && ch == 'x')) { out.append("ං"); i++; continue }
            if (smart && source.startsWith("zn", i)) { out.append("ං"); i += 2; continue }
            if (smart && ch == 'z' && listOf("zg", "zj", "zd", "zdh", "zq", "zk", "zh").none { source.startsWith(it, i) }) { i++; continue }
            if (ch == 'H') { out.append("ඃ"); i++; continue }
            val c = consonant()
            if (c != null) {
                out.append(c.second); i += c.first.length
                val v = vowel()
                if (v != null && (!smart || v.sign != null)) { out.append(v.sign ?: ""); i += v.key.length }
                else if (!smart && i < source.length && source[i] == 'r') {
                    out.append("්‍ර"); i++; vowel()?.let { out.append(it.sign ?: ""); i += it.key.length }
                } else {
                    val next = consonant()
                    if (next != null) {
                        val join = next.first == "y" || (next.first == "r" && c.first !in listOf("m", "n", "l"))
                        out.append(if (join) "්‍" else "්")
                    } else out.append("්")
                }
                continue
            }
            val v = vowel()
            if (v != null && v.independent.isNotEmpty()) { out.append(v.independent); i += v.key.length; continue }
            out.append(ch); i++
        }
        return out.toString()
    }
}

object AksharaEasterEgg {
    const val TRUE_NAME_DISPLAY = "✦ අක්ෂර"
    const val TRUE_NAME_INSERT = "Made in Sri Lanka 🇱🇰"
    fun isCompleteTrueName(rendered: String, source: String): Boolean = rendered in setOf("අක්ෂර", "අක්ශර") && (source.isEmpty() || source.lowercase() == "akshara")
}
