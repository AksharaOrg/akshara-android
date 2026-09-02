package org.akshara.ime.engine

import org.junit.Assert.*
import org.junit.Test

class SinhalaEngineTest {
    @Test fun phoneticFixturesMatchIosRules() {
        val cases = mapOf(
            "amma" to "අම්ම", "sinhala" to "සින්හල", "akshara" to "අක්ශර",
            "akShara" to "අක්ෂර", "kra" to "ක්‍ර", "kya" to "ක්‍ය",
            "ka" to "ක", "kii" to "කී", "M" to "ං", "H" to "ඃ",
            "ke" to "කෙ", "ko" to "කො", "kai" to "කෛ", "kee" to "කේ"
        )
        cases.forEach { (source, expected) -> assertEquals(source, expected, SinhalaEngine.transliterate(source, InputMode.PHONETIC)) }
    }

    @Test fun smartFixturesMatchIosRules() {
        val cases = mapOf(
            "x" to "ං", "zn" to "ං", "zga" to "ඟ", "chha" to "ඡ", "thha" to "ථ",
            "ru" to "රු", "kru" to "කෘ", "sha" to "ශ", "q" to "ද්"
        )
        cases.forEach { (source, expected) -> assertEquals(source, expected, SinhalaEngine.transliterate(source, InputMode.SMART_PHONETIC)) }
    }

    @Test fun slsNormalizationHandlesPrebaseAndIndependentSequences() {
        assertEquals("කේ", SinhalaEngine.normalizeSls("ෙක්"))
        assertEquals("කො", SinhalaEngine.normalizeSls("ෙකා"))
        assertEquals("කෝ", SinhalaEngine.normalizeSls("ෙකා්"))
        assertEquals("කෛ", SinhalaEngine.normalizeSls("ෙෙක"))
        assertEquals("කෙ", SinhalaEngine.normalizeSls("ෙක"))
        assertEquals("ආ", SinhalaEngine.normalizeSls("අා"))
        assertEquals("ඇ", SinhalaEngine.normalizeSls("අැ"))
        assertEquals("්‍ර", SinhalaEngine.normalizeSls(SinhalaEngine.slsCharacter("rakaranshaya", false)))
        assertEquals("්‍ය", SinhalaEngine.normalizeSls(SinhalaEngine.slsCharacter('h', true)))
        val raka = SinhalaEngine.slsCharacter("rakaranshaya", false)
        val yansa = SinhalaEngine.slsCharacter('h', true)
        assertEquals("ප්‍රේ", SinhalaEngine.normalizeSls("ෙප${raka}්"))
        assertEquals("ප්‍රෙ", SinhalaEngine.normalizeSls("ෙප$raka"))
        assertEquals("ප්‍රො", SinhalaEngine.normalizeSls("ෙප${raka}ා"))
        assertEquals("ක්‍යේ", SinhalaEngine.normalizeSls("ෙක${yansa}්"))
        assertEquals("පේ", SinhalaEngine.normalizeSls("ෙප්"))
        assertTrue(SinhalaEngine.canExtendPrebase("ෙප", raka))
        assertTrue(SinhalaEngine.canExtendPrebase("ෙප$raka", "්"))
    }

    @Test fun slsMappingAndSpecialTokensMatchReference() {
        assertEquals("ු", SinhalaEngine.slsCharacter('q', false))
        assertEquals("ූ", SinhalaEngine.slsCharacter('q', true))
        assertEquals("්‍ර", SinhalaEngine.normalizeSls(SinhalaEngine.slsCharacter("rakaranshaya", false)))
        assertEquals("\u200D", SinhalaEngine.slsCharacter("rakaranshaya", true))
        assertEquals("ර්‍", SinhalaEngine.normalizeSls(SinhalaEngine.slsCharacter('`', true)))
        assertEquals("්", SinhalaEngine.slsCharacter('a', false))
        assertEquals("ෙ", SinhalaEngine.slsCharacter('f', false))
        assertEquals("්‍ය", SinhalaEngine.slsKeyLabel('h', true))
        assertEquals("ය", SinhalaEngine.slsKeyLabel('h', false))
    }

    @Test fun zwjJoinsAFollowingConsonant() {
        val zwj = SinhalaEngine.slsCharacter("rakaranshaya", true)
        assertEquals("\u200D", zwj)
        assertEquals("ක්‍ෂ", SinhalaEngine.normalizeSls("ක්${zwj}ෂ"))
        assertEquals("ක්‍ෂ", SinhalaEngine.normalizeSls("ක${zwj}ෂ"))
        assertEquals("ක්‍", SinhalaEngine.normalizeSls("ක$zwj"))
        val join = SinhalaEngine.slsCharacter('\\', false)
        assertEquals("ක්‍ෂ", SinhalaEngine.normalizeSls("ක${join}ෂ"))
    }

    @Test fun trueNameIsExact() {
        assertTrue(AksharaEasterEgg.isCompleteTrueName("අක්ශර", "akshara"))
        assertFalse(AksharaEasterEgg.isCompleteTrueName("අක්ශරය", "aksharaya"))
    }
}
