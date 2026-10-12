package org.akshara.ime.ime

import android.content.res.Resources
import android.graphics.Typeface

internal object KeyTypography {
    /** Regular-weight Latin labels matched against Gboard on device. */
    const val LATIN_SP = 26f
    const val LATIN_LETTER_SP = 27.5f
    const val SINHALA_SP = 21.5f
    const val HINT_SP = 11f
    const val LATIN_HINT_SP = 12.5f
    const val SINHALA_HINT_SP = 12f
    const val HINT_INSET_DP = 3f
    const val HINT_LABEL_SHIFT_DP = 4f
    const val FUNCTION_SP = 14f
    const val PREVIEW_SP = 28f

    fun isSinhala(text: String) = text.codePoints().anyMatch { it in 0x0D80..0x0DFF }
    fun isLatinLetter(text: String) = text.length == 1 && (text[0] in 'a'..'z' || text[0] in 'A'..'Z')

    fun mainPx(resources: Resources, label: String): Float {
        val sp = when {
            isSinhala(label) -> SINHALA_SP
            isLatinLetter(label) -> LATIN_LETTER_SP
            else -> LATIN_SP
        }
        return sp * resources.displayMetrics.scaledDensity
    }

    fun hintPx(resources: Resources, hint: String, latinHint: Boolean = false) =
        (when {
            latinHint -> LATIN_HINT_SP
            isSinhala(hint) -> SINHALA_HINT_SP
            else -> HINT_SP
        }) * resources.displayMetrics.scaledDensity
    fun functionPx(resources: Resources) = FUNCTION_SP * resources.displayMetrics.scaledDensity
    fun previewPx(resources: Resources) = PREVIEW_SP * resources.displayMetrics.scaledDensity

    fun keyTypeface(): Typeface = Typeface.create("sans-serif", Typeface.NORMAL)

    fun baseline(centerY: Float, fontMetrics: android.graphics.Paint.FontMetrics): Float {
        return centerY - (fontMetrics.ascent + fontMetrics.descent) / 2f
    }

    fun sinhalaBaseline(centerY: Float, fontMetrics: android.graphics.Paint.FontMetrics): Float {
        val optical = (fontMetrics.ascent + fontMetrics.descent) / 2f
        return centerY - optical - fontMetrics.descent * 0.12f
    }
}
