package org.akshara.ime.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionMorphTest {
    @Test fun graphemesKeepSinhalaClustersTogether() {
        val parts = SuggestionMorph.graphemes("කතා")
        assertEquals("කතා", parts.joinToString(""))
        assertTrue(parts.size in 2..3)
        assertEquals(listOf("g", "o", "o", "d"), SuggestionMorph.graphemes("good"))
        assertEquals(emptyList<String>(), SuggestionMorph.graphemes(""))
    }

    @Test fun displayedRunTruncatesWithEllipsisWhenTheSlotIsNarrow() {
        val widthOf = { glyph: String -> if (glyph == "…") 8f else 10f }
        val full = SuggestionMorph.displayedRun("good", 10_000f, widthOf)
        assertEquals(listOf("g", "o", "o", "d"), full.characters)
        assertEquals(false, full.truncated)

        val tight = SuggestionMorph.displayedRun("good", 10f + 8f + 1f, widthOf)
        assertTrue(tight.truncated)
        assertEquals("…", tight.characters.last())
        assertTrue(tight.characters.size < full.characters.size)
    }
}
