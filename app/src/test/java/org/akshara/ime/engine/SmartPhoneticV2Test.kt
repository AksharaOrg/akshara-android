package org.akshara.ime.engine

import org.akshara.ime.data.SoundLexicon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The port must reproduce the research repo's reference exactly. The golden file comes from
 * `akshara-phonetics/tools/build_golden.py`; regenerate it when the reference changes.
 */
class SmartPhoneticV2Test {
    private val golden: List<List<String>> by lazy {
        val stream = javaClass.classLoader!!.getResourceAsStream("smart_phonetic_v2_golden.tsv")!!
        stream.bufferedReader().readLines().filter { it.isNotEmpty() }.map { it.split('\t') }
    }
    private val lexicon by lazy {
        SoundLexicon(SoundLexicon.parse(File("src/main/res/raw/sinhala_frequency_model.tsv").bufferedReader().lineSequence()))
    }

    private fun check(kind: String, actual: (List<String>) -> String, expected: (List<String>) -> String) {
        val rows = golden.filter { it[0] == kind }
        assertTrue("no $kind rows", rows.isNotEmpty())
        val failures = rows.mapNotNull { row ->
            val got = actual(row)
            if (got == expected(row)) null else "${row.drop(1).dropLast(1)}: expected ${expected(row)}, got $got"
        }
        assertEquals("${failures.size}/${rows.size} $kind mismatches:\n" + failures.take(20).joinToString("\n"), 0, failures.size)
    }

    @Test fun transliterationMatchesReference() = check("T", { SmartPhoneticV2.transliterate(it[1]) }, { it[2] })

    @Test fun optionsMatchReference() = check("O", { SmartPhoneticV2.transliterate(it[2], option(it[1])) }, { it[3] })

    @Test fun restyleMatchesReference() = check("R", { SoundLexicon.restyle(it[2], option(it[1])) }, { it[3] })

    @Test fun candidatesWithOptionsMatchReference() =
        check("D", { lexicon.candidates(it[2], partial = it[3] == "1", options = option(it[1])).joinToString("|") }, { it[4] })

    private fun option(name: String) = when (name) {
        "archaic" -> SmartPhoneticV2.Options(archaic = true)
        "repaya_zwj" -> SmartPhoneticV2.Options(repayaZwj = true)
        "classical" -> SmartPhoneticV2.Options(classical = true)
        "rakaransaya_u" -> SmartPhoneticV2.Options(rakaransayaU = true)
        "retroflex_d" -> SmartPhoneticV2.Options(retroflexD = true)
        else -> error("unknown option $name")
    }

    @Test fun soundKeyMatchesReference() = check("K", { SoundLexicon.soundKey(it[1]) }, { it[2] })

    @Test fun normalizeMatchesReference() = check("N", { SoundLexicon.normalize(it[1]) }, { it[2] })

    @Test fun candidatesMatchReference() =
        check("C", { lexicon.candidates(it[1], partial = it[2] == "1").joinToString("|") }, { it[3] })

    @Test fun everydayWords() {
        val z = "‍"
        mapOf(
            "lankaava" to "ලංකාව", "kruura" to "කෲර", "lait" to "ලයිට්", "kramaya" to "ක්${z}රමය",
            "d" to "ද්", "D" to "ඩ්", "ee" to "ඒ", "ai" to "අයි", "Au" to "ඖ", "kaaryaya" to "කාර්යය"
        ).forEach { (roman, expected) -> assertEquals(roman, expected, SmartPhoneticV2.transliterate(roman)) }
        assertEquals("හොඳ", lexicon.candidates("honda").first())
        assertEquals("ක්${z}රමය", lexicon.candidates("kramaya").first())
    }

    /** R-01: retroflexD swaps the d keys to the older keyboard convention. */
    @Test fun retroflexDSwapsTheDKeys() {
        val singlish = SmartPhoneticV2.Options(retroflexD = true)
        assertEquals("බඩ", SmartPhoneticV2.transliterate("bada", singlish))
        assertEquals("කොහොමද", SmartPhoneticV2.transliterate("kohomadha", singlish))
        assertEquals("ධර්මය", SmartPhoneticV2.transliterate("Dharmaya", singlish))
        assertEquals("ඪ", SmartPhoneticV2.transliterate("Da", singlish))
        assertEquals("හොඳ", SmartPhoneticV2.transliterate("hozdha", singlish))
        assertEquals("ද", SmartPhoneticV2.transliterate("da"))
        assertEquals("කොහොමද", lexicon.candidates("kohomada", options = singlish).first())   // Space still finds කොහොමද
    }

    /** R-07: after ම න ල, ර takes plain hal (දුම්රිය, not දුම්‍රිය); `classical` keeps ම්‍ර. */
    @Test fun noRakaransayaAfterMaNaLa() {
        val z = "‍"
        assertEquals("දුම්රිය", SmartPhoneticV2.transliterate("dumriya"))
        assertEquals("හෙන්රි", SmartPhoneticV2.transliterate("henri"))
        assertEquals("දිල්රුක්ශි", SmartPhoneticV2.transliterate("dilrukshi"))
        assertEquals("මෘදු", SmartPhoneticV2.transliterate("mrudu"))
        assertEquals("සමෘද්ධි", SmartPhoneticV2.transliterate("samruddhi"))
        assertEquals("තාම්${z}ර", SmartPhoneticV2.transliterate("thaamra", SmartPhoneticV2.Options(classical = true)))
        assertEquals("දුම්රිය", SoundLexicon.normalize("දුම්රිය"))
        assertEquals("දුම්රිය", lexicon.candidates("dumriya").first())
    }

    @Test fun engineUsesV2ForSmartPhoneticByDefault() {
        assertTrue(SinhalaEngine.smartPhoneticV2)
        assertEquals("ද්", SinhalaEngine.transliterate("d", InputMode.SMART_PHONETIC))
        assertEquals("ඩ්", SinhalaEngine.transliterate("d", InputMode.PHONETIC))
        try {
            SinhalaEngine.smartPhoneticV2 = false   // the classic (v1) Smart Phonetic
            assertEquals("ඩ්", SinhalaEngine.transliterate("d", InputMode.SMART_PHONETIC))
        } finally {
            SinhalaEngine.smartPhoneticV2 = true
        }
    }

    @Test fun engineAppliesTheV2Options() {
        val z = "‍"
        try {
            SinhalaEngine.smartPhoneticOptions = SmartPhoneticV2.Options(rakaransayaU = true, repayaZwj = true)
            assertEquals("ක්${z}රූර", SinhalaEngine.transliterate("kruura", InputMode.SMART_PHONETIC))
            assertEquals("කර්${z}ම", SinhalaEngine.transliterate("karma", InputMode.SMART_PHONETIC))
        } finally {
            SinhalaEngine.smartPhoneticOptions = SmartPhoneticV2.Options()
        }
        assertEquals("කෲර", SinhalaEngine.transliterate("kruura", InputMode.SMART_PHONETIC))
    }
}
