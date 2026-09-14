package org.slashboard.ime.engine

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.slashboard.ime.data.Candidate
import org.slashboard.ime.data.PredictionRepository
import androidx.test.core.app.ApplicationProvider

@RunWith(RobolectricTestRunner::class)
class SinhalaHelpersTest {

    @Test
    fun testPillamCorrectorMisplacedKombuwa() {
        // Typing kombuwa (ෙ) followed by consonant (ක)
        val correction = SinhalaPillamCorrector.handleCharacterInput("ෙ", "ක")
        assertNotNull(correction)
        assertEquals(1, correction?.deleteCount)
        assertEquals("කෙ", correction?.replacement)
    }

    @Test
    fun testPillamCorrectorDoubleKombuwa() {
        // Typing ෙ followed by ෙ -> ෛ
        val correction = SinhalaPillamCorrector.handleCharacterInput("කෙ", "ෙ")
        assertNotNull(correction)
        assertEquals(1, correction?.deleteCount)
        assertEquals("ෛ", correction?.replacement)
    }

    @Test
    fun testPillamCorrectorKombuwaPlusAelapilla() {
        // Typing කෙ followed by ා -> කො
        val correction = SinhalaPillamCorrector.handleCharacterInput("කෙ", "ා")
        assertNotNull(correction)
        assertEquals(1, correction?.deleteCount)
        assertEquals("ො", correction?.replacement)
    }

    @Test
    fun testPillamCorrectorFullTextNormalization() {
        // Misordered kombuwa before consonant: "ෙක" should become "කෙ"
        val corrected = SinhalaPillamCorrector.correctText("ෙකලස")
        assertEquals("කෙලස", corrected)
    }

    @Test
    fun testOrthographyHelperCommonErrors() {
        // පැමිනියා -> පැමිණියා
        val fix1 = SinhalaOrthographyHelper.findCorrection("පැමිනියා") { 10 }
        assertEquals("පැමිණියා", fix1)

        // කරුනාව -> කරුණාව
        val fix2 = SinhalaOrthographyHelper.findCorrection("කරුනාව") { 10 }
        assertEquals("කරුණාව", fix2)

        // පිලිබඳ -> පිළිබඳ
        val fix3 = SinhalaOrthographyHelper.findCorrection("පිලිබඳ") { 10 }
        assertEquals("පිළිබඳ", fix3)

        // Generate variants for unknown words containing na/la
        val variants = SinhalaOrthographyHelper.generateOrthographicVariants("පැමින")
        assertTrue(variants.contains("පැමිණ"))
    }

    @Test
    fun testPredictionRepositoryInjectsCorrection() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val learning = org.slashboard.ime.data.LocalLearningStore(context)
        val repo = PredictionRepository(context, learning)

        val candidates = repo.candidates("පැමිනියා", emptyList(), 3)
        assertTrue(candidates.isNotEmpty())
        val first = candidates.first()
        assertEquals("පැමිණියා", first.text)
        assertTrue(first.isCorrection)
    }

    @Test
    fun testPhoneticKnAndGnTransliteration() {
        // k + n -> ක්න්
        assertEquals("ක්න්", SinhalaEngine.transliterate("kn", InputMode.PHONETIC))
        assertEquals("ක්න්", SinhalaEngine.transliterate("kn-", InputMode.PHONETIC))
        assertEquals("ක්න්", SinhalaEngine.transliterate("k-n", InputMode.PHONETIC))
        assertEquals("ක්න", SinhalaEngine.transliterate("kna", InputMode.PHONETIC))

        // g + n -> ග්න්
        assertEquals("ග්න්", SinhalaEngine.transliterate("gn", InputMode.PHONETIC))
        assertEquals("ග්න", SinhalaEngine.transliterate("gna", InputMode.PHONETIC))
        assertEquals("අග්නි", SinhalaEngine.transliterate("agni", InputMode.PHONETIC))
        assertEquals("ලග්නය", SinhalaEngine.transliterate("lagnaya", InputMode.PHONETIC))

        // Smart phonetic mode as well
        assertEquals("ක්න්", SinhalaEngine.transliterate("kn", InputMode.SMART_PHONETIC))
        assertEquals("ග්න්", SinhalaEngine.transliterate("gn", InputMode.SMART_PHONETIC))
    }

    @Test
    fun testSinhalaNumberToWords() {
        assertEquals("දෙදහස් පන්සියයයි", SinhalaNumberToWords.convert("2500"))
        assertEquals("පනහයි", SinhalaNumberToWords.convert("50"))
        assertEquals("සියයයි", SinhalaNumberToWords.convert("100"))
        assertEquals("එක්දහසයි", SinhalaNumberToWords.convert("1000"))
        assertEquals("දෙදහස් පන්සියයයි ශත පනහයි", SinhalaNumberToWords.convert("2500.50"))
        assertEquals("දසදහසයි", SinhalaNumberToWords.convert("10000"))
        assertEquals("ලක්ෂයයි", SinhalaNumberToWords.convert("100000"))
    }

    @Test
    fun testSinglishParagraphConverter() {
        val singlish = "api heta ennam. karunawen balanna."
        val converted = SinglishParagraphConverter.convert(singlish)
        assertTrue(converted.contains("අපි"))
        assertTrue(converted.contains("හෙට"))
        assertTrue(converted.contains("කරුණාවෙන්"))
    }

    @Test
    fun testLiveUnitConverter() {
        val r1 = LiveUnitConverter.findConversion("50 USD to LKR")
        assertNotNull(r1)
        assertEquals("15,000 LKR", r1?.result)

        val r2 = LiveUnitConverter.findConversion("5 km to m")
        assertNotNull(r2)
        assertEquals("5,000 M", r2?.result)

        val r3 = LiveUnitConverter.findConversion("100 c to f")
        assertNotNull(r3)
        assertEquals("212 F", r3?.result)
    }
}
