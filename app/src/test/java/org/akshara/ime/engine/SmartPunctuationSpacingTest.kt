package org.akshara.ime.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartPunctuationSpacingTest {
    @Test fun collapsesSpaceBeforePeriodAndAddsSentenceSpace() {
        assertEquals("hello. ", SmartPunctuationSpacing.applied(".", "hello "))
        assertEquals("hello. ", SmartPunctuationSpacing.applied(".", "hello"))
        assertEquals("hello? ", SmartPunctuationSpacing.applied("?", "hello"))
        assertEquals("hello! ", SmartPunctuationSpacing.applied("!", "hello"))
    }

    @Test fun trimsSpaceBeforeClosingMarksWithoutStacking() {
        assertEquals("hello)", SmartPunctuationSpacing.applied(")", "hello "))
        assertEquals("hello,", SmartPunctuationSpacing.applied(",", "hello "))
    }

    @Test fun collapsesSpaceAfterOpeningMarks() {
        assertEquals("\"h", SmartPunctuationSpacing.applied("h", "\" "))
        assertEquals("(h", SmartPunctuationSpacing.applied("h", "( "))
    }

    @Test fun decimalAndUrlFieldsDoNotAutoSpace() {
        assertEquals("3.", SmartPunctuationSpacing.applied(".", "3"))
        assertEquals(
            "www.",
            SmartPunctuationSpacing.applied(".", "www", SmartPunctuationSpacing.FieldKind.SUPPRESSES_SENTENCE_SPACING)
        )
        assertEquals("hello\n.", SmartPunctuationSpacing.applied(".", "hello\n"))
    }

    @Test fun sinhalaAndKundaliyaFollowTheSameRules() {
        assertEquals("ක. ", SmartPunctuationSpacing.applied(".", "ක "))
        assertEquals("ක෴ ", SmartPunctuationSpacing.applied("෴", "ක"))
        assertEquals("hello..", SmartPunctuationSpacing.applied(".", "hello. "))
    }

    @Test fun trailingSentenceSpaceDetection() {
        assertTrue(SmartPunctuationSpacing.hasTrailingSentenceSpace("hello. "))
        assertFalse(SmartPunctuationSpacing.hasTrailingSentenceSpace("hello "))
    }

    @Test fun smartQuotesPairFromContext() {
        assertEquals("‘", SmartPunctuationSpacing.smartQuote("'", null))
        assertEquals("‘", SmartPunctuationSpacing.smartQuote("'", ' '))
        assertEquals("’", SmartPunctuationSpacing.smartQuote("'", 'a'))
        assertEquals("“", SmartPunctuationSpacing.smartQuote("\"", '('))
        assertEquals("”", SmartPunctuationSpacing.smartQuote("\"", 'a'))
    }

    @Test fun doubleSpaceReplacesOnlyAfterAWord() {
        assertTrue(SmartPunctuationSpacing.DoubleSpace.shouldReplace(true, 100, "hello "))
        assertFalse(SmartPunctuationSpacing.DoubleSpace.shouldReplace(true, 500, "hello "))
        assertFalse(SmartPunctuationSpacing.DoubleSpace.shouldReplace(true, 100, "hello. "))
        assertFalse(SmartPunctuationSpacing.DoubleSpace.shouldReplace(false, 100, "hello "))
        assertFalse(SmartPunctuationSpacing.DoubleSpace.shouldReplace(true, 100, " "))
    }
}
