package org.akshara.ime.ime

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.Drawable
import androidx.core.graphics.ColorUtils

/**
 * A soft radial light over a theme's background, like the glow themes in Gboard's dark gradients.
 * [x] and [y] are fractions of the width and height; [radius] is a fraction of the larger side.
 */
internal data class Glow(val color: Int, val x: Float, val y: Float, val radius: Float)

/** A top-to-bottom gradient with [Glow]s layered on top, sized to whatever it is drawn into. */
internal class ThemeBackgroundDrawable(private val stops: List<Int>, private val glows: List<Glow>) : Drawable() {
    private val base = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaints = glows.map { Paint(Paint.ANTI_ALIAS_FLAG) }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        val top = bounds.top.toFloat()
        val bottom = bounds.bottom.toFloat()
        base.shader = if (stops.size > 1) {
            LinearGradient(0f, top, 0f, bottom, stops.toIntArray(), null, Shader.TileMode.CLAMP)
        } else null
        base.color = stops.firstOrNull() ?: 0
        val size = maxOf(bounds.width(), bounds.height()).toFloat().coerceAtLeast(1f)
        glows.forEachIndexed { i, glow ->
            // Fade to the glow's own hue at zero alpha so the edge doesn't turn grey
            val clear = ColorUtils.setAlphaComponent(glow.color, 0)
            glowPaints[i].shader = RadialGradient(
                bounds.left + glow.x * bounds.width(), top + glow.y * bounds.height(), glow.radius * size,
                intArrayOf(glow.color, ColorUtils.setAlphaComponent(glow.color, android.graphics.Color.alpha(glow.color) / 3), clear),
                floatArrayOf(0f, .55f, 1f), Shader.TileMode.CLAMP
            )
        }
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        canvas.drawRect(b, base)
        glowPaints.forEach { canvas.drawRect(b, it) }
    }

    override fun setAlpha(alpha: Int) {
        base.alpha = alpha
        glowPaints.forEach { it.alpha = alpha }
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        base.colorFilter = colorFilter
        glowPaints.forEach { it.colorFilter = colorFilter }
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.OPAQUE
}
