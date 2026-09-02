package org.akshara.ime.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GraphemeDeleteTest {
    @Test fun deletesExtendedSinhalaCluster() {
        assertEquals("ක්‍රෝ", GraphemeDelete.lastCluster("අක්‍රෝ"))
        assertEquals("අ", GraphemeDelete.lastCluster("අ"))
        assertEquals("කෙ", GraphemeDelete.lastCluster("මකෙ"))
    }

    @Test fun backspacePeelsCompoundSinhalaVowels() {
        assertEquals("පෙ", GraphemeDelete.reduceAkshara("පො"))
        assertEquals("ප", GraphemeDelete.reduceAkshara("පෙ"))
        assertNull(GraphemeDelete.reduceAkshara("ප"))
        assertEquals("පෙ", GraphemeDelete.reduceAkshara("පේ"))
        assertEquals("පො", GraphemeDelete.reduceAkshara("පෝ"))
        assertEquals("පෙ", GraphemeDelete.reduceAkshara("පෞ"))
        assertEquals("ක", GraphemeDelete.reduceAkshara("කා"))
        assertEquals("ක", GraphemeDelete.reduceAkshara("ක්"))
        assertEquals("ක", GraphemeDelete.reduceAkshara("ක්‍ර"))
    }

    @Test fun wijesekaraBufferDropsAelaPillaFromOSign() {
        assertEquals("ෙප", GraphemeDelete.peelLastScalar("ෙපා"))
        assertEquals("පෙ", GraphemeDelete.peelLastScalar("පො"))
        assertEquals("කෙ", SinhalaEngine.normalizeSls(GraphemeDelete.peelLastScalar("ෙකා")))
    }
    @Test fun wordDeleteIncludesTrailingWhitespace() {
        assertEquals("ලෝකය ", GraphemeDelete.lastWordSegment("හෙලෝ ලෝකය "))
        assertEquals("ලෝකය", GraphemeDelete.lastWordSegment("හෙලෝ ලෝකය"))
    }
}
