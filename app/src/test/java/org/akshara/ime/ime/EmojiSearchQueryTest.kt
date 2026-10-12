package org.akshara.ime.ime

import org.akshara.ime.engine.InputMode
import org.junit.Assert.*
import org.junit.Test

class EmojiSearchQueryTest {
    @Test fun composesSinhalaAndPreservesEnglishAcrossSwitches() {
        for (mode in listOf(InputMode.PHONETIC, InputMode.SMART_PHONETIC)) {
            val query = EmojiSearchQuery()
            query.setLanguage(mode, false)
            "mala".forEach { query.append(it.toString(), true) }
            assertEquals("මල", query.text)
            query.append(" ", false)
            query.setLanguage(mode, true)
            query.append("flower", true)
            assertEquals("මල flower", query.text)
            query.delete(false)
            assertEquals("මල flowe", query.text)
            query.clear()
            assertEquals("", query.text)
        }
    }

    @Test fun wijesekaraReordersPrebaseVowelsAndDeleteRecomposes() {
        val query = EmojiSearchQuery()
        query.setLanguage(InputMode.WIJESEKARA, false)
        query.append("ෙ", true)
        query.append("ක", true)
        assertEquals("කෙ", query.text)
        query.delete(false)
        assertEquals("ෙ", query.text)
        query.append("😀", false)
        query.delete(false)
        assertEquals("ෙ", query.text)
    }

    @Test fun optionalRowsKeepTheStandardKeyHeight() {
        for (landscape in listOf(false, true)) for (size in listOf("compact", "standard", "tall")) {
            val normal = KeyboardGeometry.rowHeightPx(size, landscape, 2.5f, 4)
            val extra = KeyboardGeometry.rowHeightPx(size, landscape, 2.5f, 5)
            assertEquals(normal, extra, 0.001f)
            assertTrue(extra * 5 > normal * 4)
        }
    }

    @Test fun commaPrecedesEmojiKeyInEveryLanguageLayout() {
        for (mode in InputMode.entries) for (english in listOf(false, true)) for (punctuation in listOf(false, true)) {
            val row = KeyboardLayoutFactory.typingRows(mode, KeyboardLayer.LETTERS, false, false,
                EditorLayout.TEXT, "none", true, "Enter", "Space", false, "EN", punctuation, english).last()
            val index = row.keys.indexOfFirst { it.action == KeyCode.EMOJI }
            assertTrue(index >= 0)
            assertEquals(",", row.keys[1].output)   // beside ?123, like Gboard
            assertTrue(index > 1)
            assertEquals(1f, row.keys.sumOf { it.widthFraction.toDouble() }.toFloat(), 0.001f)
        }
    }
}
