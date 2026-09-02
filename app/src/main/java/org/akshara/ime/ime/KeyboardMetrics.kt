package org.akshara.ime.ime

import kotlin.math.max

/**
 * Gboard QWERTY proportions for Phonetic / Smart Phonetic.
 * Ten equal letter keys; A–L use that same width, centered; Shift / Delete
 * absorb the leftover so every gap on the Z row matches the Q row.
 */
internal data class KeyboardMetrics(
    val inset: Int,
    val gap: Int,
    val verticalInset: Int,
    val tenKeyWidth: Int,
    val secondRowInset: Int,
    val shiftWidth: Int,
    val thirdRowInnerGap: Int,
    val keyHeight: Int,
    val rowHeight: Int
) {
    companion object {
        fun phonetic(
            widthPx: Int,
            spacing: String,
            density: Float,
            landscape: Boolean
        ): KeyboardMetrics {
            val gap = gapPx(spacing, density)
            var inset = insetPx(spacing, density)
            var usable = max(0, widthPx - inset * 2)
            val ten = max(1, (usable - gap * 9) / 10)
            inset += max(0, usable - (ten * 10 + gap * 9)) / 2
            usable = max(0, widthPx - inset * 2)
            val secondInset = max(0, (usable - (ten * 9 + gap * 8)) / 2)
            val shift = max(ten, (usable - ten * 7 - gap * 8) / 2)
            val keyHeight = dp(if (landscape) 42 else 48, density)
            val rowHeight = dp(if (landscape) 48 else 56, density)
            val verticalInset = max(0, (rowHeight - keyHeight) / 2)
            return KeyboardMetrics(
                inset = inset,
                gap = gap,
                verticalInset = verticalInset,
                tenKeyWidth = ten,
                secondRowInset = secondInset,
                shiftWidth = shift,
                thirdRowInnerGap = gap,
                keyHeight = keyHeight,
                rowHeight = rowHeight
            )
        }

        fun gapPx(spacing: String, density: Float) = when (spacing) {
            "compact" -> dp(2, density)
            "spacious" -> dp(5, density)
            else -> dp(4, density)
        }

        fun insetPx(spacing: String, density: Float) = when (spacing) {
            "compact" -> dp(3, density)
            "spacious" -> dp(6, density)
            else -> dp(4, density)
        }

        fun marginPx(spacing: String, density: Float, vertical: Boolean): Int {
            val compact = if (vertical) 2 else 1
            val standard = if (vertical) 4 else 2
            val spacious = if (vertical) 5 else 3
            val dpValue = when (spacing) {
                "compact" -> compact
                "spacious" -> spacious
                else -> standard
            }
            return dp(dpValue, density)
        }

        private fun dp(value: Int, density: Float) = (value * density).toInt()
    }
}
