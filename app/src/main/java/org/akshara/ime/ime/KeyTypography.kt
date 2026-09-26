package org.akshara.ime.ime

import android.content.res.Resources
import android.graphics.Typeface

internal object KeyTypography {
    /** Regular-weight Latin labels matched against Gboard on device. */
    const val LATIN_SP = 26f
    const val SINHALA_SP = 21.5f
    const val HINT_SP = 11f
    const val FUNCTION_SP = 14f
    const val PREVIEW_SP = 28f

    fun isSinhala(text: String) = text.codePoints().anyMatch { it in 0x0D80..0x0DFF }

    fun mainPx(resources: Resources, label: String): Float {
        val sp = if (isSinhala(label)) SINHALA_SP else LATIN_SP
        return sp * resources.displayMetrics.scaledDensity
    }

    fun hintPx(resources: Resources) = HINT_SP * resources.displayMetrics.scaledDensity
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
