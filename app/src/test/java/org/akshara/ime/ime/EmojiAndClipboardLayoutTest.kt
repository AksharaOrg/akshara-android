package org.akshara.ime.ime

import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w461dp-h1020dp-mdpi")
class EmojiAndClipboardLayoutTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test fun clipboardChipIsCenteredAndLongClipsDoNotCoverActions() {
        var pasted = 0
        val rail = SuggestionRail(context, Color.WHITE, {}, {}, {}, { pasted++ }, {})
        rail.setClipboardVisible(true)
        rail.setEmojiVisible(true)
        rail.setClipboardPreview("Short clip")
        layout(rail, 461, 46)
        val chip = descendants(rail).filterIsInstance<TextView>().first { it.contentDescription == "Paste Short clip" }
        val location = android.graphics.Rect()
        chip.getDrawingRect(location)
        rail.offsetDescendantRectToMyCoords(chip, location)
        assertTrue(kotlin.math.abs(230 - location.centerX()) <= 1)
        assertTrue(chip.width < 250)
        chip.performClick()
        assertEquals(1, pasted)
        rail.setClipboardPreview("A long clipboard value ".repeat(30))
        layout(rail, 461, 46)
        chip.getDrawingRect(location)
        rail.offsetDescendantRectToMyCoords(chip, location)
        assertTrue("Clipboard and emoji buttons must remain reachable", location.left >= 88)
        assertTrue(location.right <= 461 - 88)
        rail.setClipboardPreview(null)
        assertEquals(View.GONE, chip.visibility)
    }

    @Test fun categoryJumpKeepsOneScrollableCatalogAndEmojiPicksDoNotResetPosition() {
        val picked = mutableListOf<String>()
        val sections = listOf("Recent emoji" to listOf("😀"), "Food" to List(36) { "🥑" }, "Flags" to List(36) { "🇱🇰" })
        var selected = -1
        val grid = EmojiCatalogView(context, sections, Color.WHITE, "", { selected = it }, picked::add)
        layout(grid, 461, 200)
        val originalAdapter = grid.adapter
        grid.showCategory(1)
        layout(grid, 461, 200)
        assertEquals(1, selected)
        assertTrue(descendants(grid).filterIsInstance<TextView>().any { it.text == "Food" })
        val cell = descendants(grid).first { it.contentDescription == "Emoji 🥑" }
        val position = (grid.layoutManager as GridLayoutManager).findFirstVisibleItemPosition()
        cell.performClick()
        assertEquals(listOf("🥑"), picked)
        assertEquals(position, (grid.layoutManager as GridLayoutManager).findFirstVisibleItemPosition())
        grid.scrollBy(0, 260)
        assertEquals(2, selected)
        assertSame(originalAdapter, grid.adapter)
    }

    private fun layout(view: View, width: Int, height: Int) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, width, height)
    }
    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
}
