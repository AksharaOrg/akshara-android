package org.akshara.ime.ime

import org.akshara.ime.engine.InputMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardMetricsTest {
    @Test fun phoneticMatchesGboardQwertyMetrics() {
        val widths = floatArrayOf(360f, 384f, 393f, 411f, 432f)
        widths.forEach { width ->
            val metrics = KeyboardMetrics.phonetic(width.toInt(), "standard", 1f, false)
            val layout = placePhonetic(width)
            val q = layout.rowKeys(0)
            assertEquals(10, q.size)
            val insetH = 2f
            q.dropLast(1).forEach { key ->
                assertEquals(metrics.tenKeyWidth - 2 * insetH, key.visual.width, 0.6f)
                assertTrue(key.visual.width < key.logical.width)
            }
            assertEquals(metrics.inset + insetH, q.first().visual.left, 0.6f)
            assertEquals(width - metrics.inset - insetH, q.last().visual.right, 0.6f)
            assertEquals(0f, q.first().logical.left, 0.01f)
            assertEquals(width, q.last().logical.right, 0.01f)
            assertEquals(5f, q.first().visual.top, 0.01f)
            assertEquals(47f, q.first().visual.bottom, 0.01f)
            assertEquals(42f, q.first().visual.height, 0.01f)

            val a = layout.keyById("a")!!
            val l = layout.keyById("l")!!
            assertEquals(metrics.tenKeyWidth - 2 * insetH, a.visual.width, 0.6f)
            assertEquals(q.first().visual.width, a.visual.width, 0.01f)
            assertEquals(metrics.inset + metrics.secondRowInset + insetH, a.visual.left, 0.6f)
            assertEquals(0f, a.logical.left, 0.01f)
            assertEquals(width, l.logical.right, 0.01f)

            val shift = layout.keyById("shift")!!
            val del = layout.keyById("delete")!!
            val z = layout.keyById("z")!!
            assertEquals(metrics.shiftWidth - 2 * insetH, shift.visual.width, 0.6f)
            assertEquals(metrics.inset + insetH, shift.visual.left, 0.01f)
            assertEquals(0f, shift.logical.left, 0.01f)
            assertEquals(width, del.logical.right, 0.01f)
            assertEquals(metrics.tenKeyWidth - 2 * insetH, z.visual.width, 0.6f)
            assertEquals(width - metrics.inset - insetH, del.visual.right, 0.6f)

            val symbols = layout.keyById("?123")!!
            val enter = layout.keyById("enter")!!
            val period = layout.keyById(".")!!
            val comma = layout.keyById(",")!!
            val space = layout.keyById("space")!!
            val qKey = layout.keyById("q")!!
            assertEquals("Q", qKey.label)
            assertEquals("q", qKey.output)
            assertEquals(shift.visual.width, symbols.visual.width, 0.6f)
            assertEquals(shift.visual.width, enter.visual.width, 1f)
            assertEquals(metrics.tenKeyWidth - 2 * insetH, period.visual.width, 0.6f)
            assertEquals(metrics.tenKeyWidth - 2 * insetH, comma.visual.width, 0.6f)
            assertTrue(space.logical.width / width in 0.32f..0.75f)
            assertEquals(",", comma.output)
            assertTrue(space.logical.right > period.visual.left)
            assertTrue(period.extras.any { it.second == "," })
        }
    }

    @Test fun presentPutsTheBestCandidateInTheCentreSlot() {
        assertEquals(listOf("give", "good", "go"), SuggestionRail.present(listOf("good", "give", "go")))
        assertEquals(listOf<String?>(null, "ක", null), SuggestionRail.present(listOf("ක")))
        assertEquals(listOf(null, null, null), SuggestionRail.present(emptyList()))
    }

    @Test fun wijesekaraFollowsGboardFrameWithElevenTopKeys() {
        val width = 360f
        val metrics = KeyboardMetrics.phonetic(width.toInt(), "standard", 1f, false)
        val phonetic = placePhonetic(width)
        val wijesekara = placeWijesekara(width)
        val top = wijesekara.rowKeys(0)
        assertEquals(11, top.size)
        val letter = top.first().visual.width
        top.forEach { key -> assertEquals(letter, key.visual.width, 1.1f) }
        assertTrue(letter < metrics.tenKeyWidth.toFloat())
        assertEquals(phonetic.keyById("q")!!.visual.left, top.first().visual.left, 0.6f)
        assertEquals(phonetic.keyById("p")!!.visual.right, top.last().visual.right, 1f)

        val home = wijesekara.rowKeys(1)
        assertEquals(10, home.size)
        home.dropLast(1).forEach { key -> assertEquals(phonetic.keyById("q")!!.visual.width, key.visual.width, 0.6f) }
        assertEquals(phonetic.keyById("q")!!.visual.left, home.first().visual.left, 0.6f)
        assertEquals(phonetic.keyById("p")!!.visual.right, home.last().visual.right, 0.6f)

        val shift = wijesekara.keyById("shift")!!
        val del = wijesekara.keyById("delete")!!
        assertEquals(phonetic.keyById("shift")!!.visual.left, shift.visual.left, 0.6f)
        assertEquals(width - metrics.inset - 2f, del.visual.right, 0.6f)
        assertEquals(phonetic.keyById("enter")!!.visual.width, wijesekara.keyById("enter")!!.visual.width, 0.6f)
        assertEquals(phonetic.keyById("?123")!!.visual.width, wijesekara.keyById("?123")!!.visual.width, 0.6f)
    }

    @Test fun shiftedWijesekaraShowsYansayaAndEmitsZwj() {
        val rows = KeyboardLayoutFactory.typingRows(
            InputMode.WIJESEKARA, KeyboardLayer.LETTERS, true, false,
            EditorLayout.TEXT, "none", false, "↵", "සිංහල"
        )
        val layout = KeyboardLayoutFactory.place(rows, 360f, 52f)
        val yansaya = layout.keyById("h")!!
        assertEquals("්‍ය", yansaya.label)
        assertEquals("\uE005", yansaya.output)
        val joiner = layout.keyById("rakaranshaya")!!
        assertEquals("ZWJ", joiner.label)
        assertEquals("\u200D", joiner.output)
    }

    @Test fun numberLayerUsesGboardUkBottomRow() {
        val rows = KeyboardLayoutFactory.typingRows(
            InputMode.PHONETIC, KeyboardLayer.NUMBERS, false, false,
            EditorLayout.TEXT, "none", true, "↵", "English"
        )
        assertEquals(listOf(10, 10, 9, 6), rows.map { it.keys.size })
        assertEquals(KeyCode.LAYER, rows[2].keys.first().action)
        assertEquals(KeyCode.DELETE, rows[2].keys.last().action)
        assertEquals(",", rows[3].keys[1].id)
        assertEquals(0.40f, rows[3].keys.first { it.action == KeyCode.SPACE }.widthFraction, 0.001f)
        assertTrue(rows[3].keys.first { it.id == "." }.extras.any { it.second == "," })
    }

    private fun placePhonetic(width: Float): KeyboardLayout {
        val rows = KeyboardLayoutFactory.typingRows(
            InputMode.PHONETIC, KeyboardLayer.LETTERS, false, false,
            EditorLayout.TEXT, "none", false, "↵", "Phonetic"
        )
        return KeyboardLayoutFactory.place(rows, width, 52f)
    }

    private fun placeWijesekara(width: Float): KeyboardLayout {
        val rows = KeyboardLayoutFactory.typingRows(
            InputMode.WIJESEKARA, KeyboardLayer.LETTERS, false, false,
            EditorLayout.TEXT, "none", false, "↵", "සිංහල"
        )
        return KeyboardLayoutFactory.place(rows, width, 52f)
    }
}
