package org.akshara.ime.ime

import org.akshara.ime.engine.InputMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardMetricsTest {
    @Test fun phoneticPercentagesHoldAcrossLogicalWidths() {
        val widths = floatArrayOf(360f, 384f, 393f, 411f, 432f)
        widths.forEach { width ->
            val rows = KeyboardLayoutFactory.typingRows(
                InputMode.PHONETIC, KeyboardLayer.LETTERS, false, false,
                EditorLayout.TEXT, "none", false, "↵", "Phonetic"
            )
            val layout = KeyboardLayoutFactory.place(rows, width, 53f, 2.5f, 3f, 4f)
            val q = layout.rowKeys(0)
            assertEquals(10, q.size)
            q.forEach { key ->
                assertEquals(width * 0.10f, key.logical.width, 0.6f)
                assertTrue(key.visual.width < key.logical.width)
                assertTrue(key.visual.left > key.logical.left - 0.01f)
            }
            assertEquals(0f, q.first().logical.left, 0.01f)
            assertEquals(width, q.last().logical.right, 0.01f)
            val a = layout.keyById("a")!!
            val l = layout.keyById("l")!!
            assertEquals(0f, a.logical.left, 0.01f)
            assertEquals(width, l.logical.right, 0.01f)
            assertEquals(width * KeyboardGeometry.ROW2_OFFSET + 2.5f, a.visual.left, 0.6f)
            val shift = layout.keyById("shift")!!
            val del = layout.keyById("delete")!!
            assertEquals(width * KeyboardGeometry.SHIFT, shift.logical.width, 0.6f)
            assertEquals(width * KeyboardGeometry.DELETE, del.logical.width, 0.6f)
            assertEquals(0f, shift.logical.left, 0.01f)
            assertEquals(width, del.logical.right, 0.01f)
            val space = layout.keyById("space")!!
            assertTrue(space.logical.width / width in 0.49f..0.75f)
            assertNull(layout.keyById(","))
            val period = layout.keyById(".")!!
            val periodCell = width * KeyboardGeometry.PUNCT
            assertEquals(periodCell * (1f - KeyboardGeometry.SPACE_STEAL), period.logical.width, 0.6f)
            assertTrue(period.visual.width < periodCell)
            assertTrue(space.logical.right > period.visual.left)
            assertEquals(",", period.hint)
            assertTrue(period.extras.any { it.second == "," })
        }
    }

    @Test fun presentPutsTheBestCandidateInTheCentreSlot() {
        assertEquals(listOf("give", "good", "go"), SuggestionRail.present(listOf("good", "give", "go")))
        assertEquals(listOf<String?>(null, "ක", null), SuggestionRail.present(listOf("ක")))
        assertEquals(listOf(null, null, null), SuggestionRail.present(emptyList()))
    }

    @Test fun wijesekaraFirstRowUsesElevenEqualCells() {
        val rows = KeyboardLayoutFactory.typingRows(
            InputMode.WIJESEKARA, KeyboardLayer.LETTERS, false, false,
            EditorLayout.TEXT, "none", false, "↵", "සිංහල"
        )
        val layout = KeyboardLayoutFactory.place(rows, 360f, 53f, 2.5f, 3f, 4f)
        val top = layout.rowKeys(0)
        assertEquals(11, top.size)
        top.forEach { assertEquals(360f / 11f, it.logical.width, 0.6f) }
    }

    @Test fun shiftedWijesekaraShowsYansayaAndEmitsZwj() {
        val rows = KeyboardLayoutFactory.typingRows(
            InputMode.WIJESEKARA, KeyboardLayer.LETTERS, true, false,
            EditorLayout.TEXT, "none", false, "↵", "සිංහල"
        )
        val layout = KeyboardLayoutFactory.place(rows, 360f, 53f, 2.5f, 3f, 4f)
        val yansaya = layout.keyById("h")!!
        assertEquals("්‍ය", yansaya.label)
        assertEquals("\uE005", yansaya.output)
        val joiner = layout.keyById("rakaranshaya")!!
        assertEquals("ZWJ", joiner.label)
        assertEquals("\u200D", joiner.output)
    }

    @Test fun numberLayerUsesGboardRowsWithoutACommaKey() {
        val rows = KeyboardLayoutFactory.typingRows(
            InputMode.PHONETIC, KeyboardLayer.NUMBERS, false, false,
            EditorLayout.TEXT, "none", true, "↵", "English"
        )
        assertEquals(listOf(10, 10, 9, 5), rows.map { it.keys.size })
        assertEquals(KeyCode.LAYER, rows[2].keys.first().action)
        assertEquals(KeyCode.DELETE, rows[2].keys.last().action)
        assertTrue(rows[3].keys.none { it.id == "," })
        assertEquals(0.50f, rows[3].keys.first { it.action == KeyCode.SPACE }.widthFraction, 0.001f)
        assertTrue(rows[3].keys.first { it.id == "." }.extras.any { it.second == "," })
    }
}
