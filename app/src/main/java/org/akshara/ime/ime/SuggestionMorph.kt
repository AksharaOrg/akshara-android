package org.akshara.ime.ime

import android.graphics.Paint
import java.text.BreakIterator

/** Grapheme runs used by the suggestion-rail morph, matching iOS CandidateMorphLabel. */
internal object SuggestionMorph {
    const val APPEAR_MS = 140L
    const val DISAPPEAR_MS = 80L
    const val SHIFT_MS = 140L
    const val STAGGER_MS = 6L
    const val MAX_STAGGER_MS = 36L
    const val APPEAR_SCALE = 0.28f
    /**
     * Changes closer together than this (fast typing) swap the text without the morph. Each morph draws about a
     * dozen frames per suggestion; while typing, those animations overlapped constantly and drawing them was most
     * of the keyboard's CPU time. A change after a pause, such as next words after Space, still morphs.
     */
    const val QUICK_SUCCESSION_MS = 300L
    const val TEXT_SP = 17f
    const val INSET_DP = 4f

    fun graphemes(text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        val iterator = BreakIterator.getCharacterInstance()
        iterator.setText(text)
        val parts = ArrayList<String>()
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            parts += text.substring(start, end)
            start = end
            end = iterator.next()
        }
        return parts
    }

    data class Run(val characters: List<String>, val truncated: Boolean)

    fun displayedRun(text: String, available: Float, paint: Paint): Run =
        displayedRun(text, available) { paint.measureText(it) }

    fun displayedRun(text: String, available: Float, widthOf: (String) -> Float): Run {
        val characters = graphemes(text)
        if (characters.isEmpty() || available <= 1f) return Run(characters, false)
        val widths = characters.map(widthOf)
        if (widths.sum() <= available) return Run(characters, false)
        val ellipsis = "…"
        val ellipsisWidth = widthOf(ellipsis)
        var used = ellipsisWidth
        var count = 0
        for (width in widths) {
            if (used + width > available) break
            used += width
            count++
        }
        if (count == 0) {
            return Run(if (ellipsisWidth <= available) listOf(ellipsis) else emptyList(), true)
        }
        return Run(characters.take(count) + ellipsis, true)
    }
}
