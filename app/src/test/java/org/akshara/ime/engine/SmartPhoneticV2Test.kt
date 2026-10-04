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

    @Test fun engineSwitchesToV2ForSmartPhoneticOnly() {
        try {
            SinhalaEngine.smartPhoneticV2 = true
            assertEquals("ද්", SinhalaEngine.transliterate("d", InputMode.SMART_PHONETIC))
            assertEquals("ඩ්", SinhalaEngine.transliterate("d", InputMode.PHONETIC))
        } finally {
            SinhalaEngine.smartPhoneticV2 = false
        }
        assertEquals("ඩ්", SinhalaEngine.transliterate("d", InputMode.SMART_PHONETIC))
    }
}
