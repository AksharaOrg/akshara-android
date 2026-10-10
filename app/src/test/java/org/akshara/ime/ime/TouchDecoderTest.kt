package org.akshara.ime.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class TouchDecoderTest {
    private val left = key("q", 0f, 50f)
    private val right = key("w", 50f, 100f)
    private val layout = KeyboardLayout(listOf(left, right), 100f, 50f, 50f, 50f, 1)
    private val centers: (KeySpec) -> Pair<Float, Float> = { it.geometricCenterX to it.geometricCenterY }

    @Test fun rectangularPicksContainingKeyAndSplitsTheBorder() {
        val decoder = RectangularTouchDecoder()
        assertEquals("q", decoder.decode(25f, 25f, layout, centers).selected?.id)
        assertEquals("w", decoder.decode(75f, 25f, layout, centers).selected?.id)
        assertEquals("q", decoder.decode(49f, 25f, layout, centers).selected?.id)
        assertEquals("w", decoder.decode(51f, 25f, layout, centers).selected?.id)
    }

    @Test fun spatialKeepsClearCenterTapsAndScoresBorderNeighbors() {
        val decoder = SpatialTouchDecoder()
        val center = decoder.decode(25f, 25f, layout, centers)
        assertTrue(center.clearCenter)
        assertEquals("q", center.selected?.id)
        val border = decoder.decode(50f, 25f, layout, centers)
        assertEquals(2, border.candidates.size)
        assertTrue(border.candidates.all { it.spatial > 0.2f })
        assertTrue(abs(border.candidates[0].spatial - border.candidates[1].spatial) < 0.08f)
    }

    @Test fun languageBlendOnlyMovesAmbiguousTaps() {
        val decoder = SpatialTouchDecoder()
        val language = LanguageScorer { output -> if (output == "w") 20f else 0f }
        val center = decoder.decode(25f, 25f, layout, centers, language)
        assertEquals("q", center.selected?.id)
        val border = decoder.decode(50f, 25f, layout, centers, language)
        assertEquals("w", border.selected?.id)
    }

    @Test fun spatialKeepsSpaceEdgeTapsInsteadOfPeriod() {
        val width = 360f
        val rows = KeyboardLayoutFactory.typingRows(
            org.akshara.ime.engine.InputMode.PHONETIC, KeyboardLayer.LETTERS, false, false,
            EditorLayout.TEXT, "none", false, "↵", "Phonetic"
        )
        val board = KeyboardLayoutFactory.place(rows, width, 52f)
        val decoder = SpatialTouchDecoder()
        val space = board.keyById("space")!!
        val period = board.keyById(".")!!
        val y = space.logical.centerY
        val spaceEdge = space.visual.right - 2f
        assertEquals("space", decoder.decode(spaceEdge, y, board, centers).selected?.id)
        val gutter = period.visual.left - 1f
        assertTrue(gutter >= space.logical.left)
        assertEquals("space", decoder.decode(gutter, y, board, centers).selected?.id)
        assertEquals(".", decoder.decode(period.visual.centerX, period.visual.centerY, board, centers).selected?.id)
        val onPeriodCap = period.visual.left + 2f
        assertEquals(".", decoder.decode(onPeriodCap, period.visual.centerY, board, centers).selected?.id)
    }

    @Test fun outsideTouchesNeverSnapToAnEdgeKey() {
        for (decoder in listOf(RectangularTouchDecoder(), SpatialTouchDecoder())) {
            for ((x, y) in listOf(-1f to 25f, 101f to 25f, 25f to -1f, 25f to 51f, Float.NaN to 25f)) {
                assertEquals(null, decoder.decode(x, y, layout, centers).selected)
            }
        }
    }

    @Test fun languageCannotStealControlOrLetterEdgeTaps() {
        val enter = right.copy(action = KeyCode.ENTER, output = "")
        val board = layout.copy(keys = listOf(left, enter))
        val decoder = SpatialTouchDecoder()
        assertEquals("q", decoder.decode(49f, 25f, board, centers, LanguageScorer { 100000f }).selected?.id)
        assertEquals("w", decoder.decode(51f, 25f, board, centers, LanguageScorer { 100000f }).selected?.id)
    }

    @Test fun languageCannotPromoteADistantCandidate() {
        val distant = key("e", 100f, 150f).copy(
            logical = Bounds(0f, 50f, 50f, 100f), visual = Bounds(3f, 53f, 47f, 97f))
        val board = layout.copy(keys = listOf(left, right, distant), height = 100f, rows = 2)
        val result = SpatialTouchDecoder().decode(50f, 40f, board, centers,
            LanguageScorer { if (it == "e") 1000000f else 0f })
        assertTrue(result.selected?.id in listOf("q", "w"))
    }

    private fun key(id: String, left: Float, right: Float) = KeySpec(
        id, id, id, KeyCode.CHAR,
        Bounds(left, 0f, right, 50f),
        Bounds(left + 3f, 3f, right - 3f, 47f),
        0
    )
}
