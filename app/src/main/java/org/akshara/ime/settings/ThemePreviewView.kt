package org.akshara.ime.settings

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import org.akshara.ime.ime.KeyboardTheme

/**
 * A theme preview. [compact] is Gboard's picker card (the background with a space bar and the Enter accent);
 * otherwise it is a miniature keyboard drawing the same key roles as the real one and honouring Key borders.
 */
internal class ThemePreviewView(context: Context) : View(context) {
    var theme: KeyboardTheme? = null
        set(value) { field = value; invalidate() }
    /** Height as a fraction of width. */
    var aspect = 0.62f
    var cornerRadius = 12f * resources.displayMetrics.density
    var compact = false
    /** Drawn on the right half, for System auto (light and dark). */
    var splitTheme: KeyboardTheme? = null
        set(value) { field = value; invalidate() }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val clip = Path()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(width, resolveSize((width * aspect).toInt(), heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        val theme = theme ?: return
        val w = width.toFloat()
        val h = height.toFloat()
        clip.reset()
        clip.addRoundRect(0f, 0f, w, h, cornerRadius, cornerRadius, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clip)
        if (compact) {
            drawCard(canvas, theme)
            splitTheme?.let { right ->
                canvas.clipRect(w / 2, 0f, w, h)
                drawCard(canvas, right)
            }
            canvas.restore()
            return
        }
        theme.backgroundDrawable().apply { setBounds(0, 0, width, height); draw(canvas) }

        val pad = w * .035f
        val gap = w * .014f
        val rowHeight = (h - pad * 2 - gap * 3) / 4f
        val unit = (w - pad * 2 - gap * 9) / 10f
        fun key(x: Float, y: Float, width: Float, color: Int, shaped: Boolean = false) {
            val radius = theme.keyShape.radius(width, rowHeight, rowHeight * .22f)
            if (theme.flatKeys && !shaped) {
                // Flat keys still show where a label would be
                paint.color = theme.hint
                val dot = rowHeight * .09f
                canvas.drawCircle(x + width / 2, y + rowHeight / 2, dot, paint)
                return
            }
            paint.color = color
            rect.set(x, y, x + width, y + rowHeight)
            canvas.drawRoundRect(rect, radius, radius, paint)
        }

        var y = pad
        for (i in 0 until 10) key(pad + i * (unit + gap), y, unit, theme.key)
        y += rowHeight + gap
        val indent = (unit + gap) / 2
        for (i in 0 until 9) key(pad + indent + i * (unit + gap), y, unit, theme.key)
        y += rowHeight + gap
        val wide = unit * 1.5f + gap * .5f
        key(pad, y, wide, theme.function)
        for (i in 0 until 7) key(pad + wide + gap + i * (unit + gap), y, unit, theme.key)
        key(w - pad - wide, y, wide, theme.function)
        y += rowHeight + gap
        key(pad, y, wide, theme.function, shaped = true)
        key(pad + wide + gap, y, unit, theme.function)
        val enterX = w - pad - wide
        val periodX = enterX - gap - unit
        val spaceX = pad + wide + unit + gap * 2
        key(spaceX, y, periodX - gap - spaceX, theme.key, shaped = true)
        key(periodX, y, unit, theme.function)
        key(enterX, y, wide, theme.accent, shaped = true)
        canvas.restore()
    }

    private fun drawCard(canvas: Canvas, theme: KeyboardTheme) {
        val w = width.toFloat()
        val h = height.toFloat()
        theme.backgroundDrawable().apply { setBounds(0, 0, width, height); draw(canvas) }
        val pill = h * .1f
        val top = h * .78f
        paint.color = theme.key
        rect.set(w * .27f, top, w * .67f, top + pill)
        canvas.drawRoundRect(rect, pill / 2, pill / 2, paint)
        paint.color = theme.accent
        rect.set(w * .74f, top, w * .86f, top + pill)
        canvas.drawRoundRect(rect, pill / 2, pill / 2, paint)
    }
}
