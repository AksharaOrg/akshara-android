package org.akshara.ime.ime

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.PathInterpolator
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.DrawableCompat

internal class KeyCap(context: Context) : View(context) {
    var spec: KeySpec? = null
        set(value) {
            if (value?.action != KeyCode.SPACE) cancelSpaceCaption()
            field = value
            tag = value?.id
            contentDescription = value?.let { description(it) }
            isClickable = value != null
            invalidate()
        }
    var theme: KeyboardTheme = KeyboardThemes.fixed(dark = false, highContrast = false)
        set(value) { field = value; invalidate() }
    var hints = true
        set(value) { field = value; invalidate() }
    var flickActive = false
        set(value) { field = value; invalidate() }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.RIGHT }
    private val rect = RectF()
    private val mainInkBounds = Rect()
    private val hintInkBounds = Rect()
    private var icon: Drawable? = null
    private var iconRes = 0
    private var spaceProgress = 1f
    private var spaceAnimator: ValueAnimator? = null
    private val spaceHandler = Handler(Looper.getMainLooper())
    private val collapseSpace = Runnable { animateSpaceCollapse() }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    override fun onDraw(canvas: Canvas) {
        val key = spec ?: return
        val pressed = isPressed
        fill.color = when {
            key.action == KeyCode.ENTER -> if (pressed) theme.accentPressed else theme.accent
            key.utility -> if (pressed) theme.functionPressed else theme.function
            else -> if (pressed) theme.keyPressed else theme.key
        }
        val radius = when (key.action) {
            KeyCode.LAYER, KeyCode.ENTER -> minOf(width, height) / 2f
            else -> theme.keyShape.radius(width.toFloat(), height.toFloat(), dp(KeyboardGeometry.LETTER_RADIUS_DP))
        }
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        // Without key borders, Gboard keeps shapes only on Space, ?123 and Enter; other keys show one while pressed
        val shaped = key.action == KeyCode.SPACE || key.action == KeyCode.LAYER || key.action == KeyCode.ENTER
        if (!theme.flatKeys || shaped || pressed) canvas.drawRoundRect(rect, radius, radius, fill)
        if (theme.highContrast) {
            fill.style = Paint.Style.STROKE
            fill.strokeWidth = dp(2)
            fill.color = theme.border
            canvas.drawRoundRect(rect, radius, radius, fill)
            fill.style = Paint.Style.FILL
        }
        if (key.icon != null) {
            val drawable = iconFor(if (key.action == KeyCode.DELETE) org.akshara.ime.R.drawable.ic_key_backspace_outline else key.icon)
            val size = dp(KeyboardGeometry.ICON_DP).toInt().coerceAtMost(minOf(width, height) - dp(8).toInt())
            val left = (width - size) / 2
            val top = (height - size) / 2
            drawable?.setBounds(left, top, left + size, top + size)
            drawable?.draw(canvas)
            return
        }
        if (key.action == KeyCode.SPACE && key.label.isNotEmpty()) {
            drawSpaceCaption(canvas, key.label)
            return
        }
        val text = if (flickActive && key.flickOutput != null) key.flickOutput else key.label
        val hint = key.hint?.takeIf { hints && !flickActive && !key.utility }
        val prominentHint = hint?.length == 1 && !KeyTypography.isSinhala(hint) &&
            KeyTypography.isLatinLetter(text)
        var mainInkTop = height.toFloat()
        var mainInkLeft = width.toFloat()
        var mainInkRight = 0f
        if (text.isNotEmpty()) {
            val function = key.utility || text.length > 2 && !KeyTypography.isSinhala(text)
            labelPaint.color = inkFor(key)
            labelPaint.typeface = KeyTypography.keyTypeface()
            var textSize = if (function) KeyTypography.functionPx(resources) else KeyTypography.mainPx(resources, text)
            val maxWidth = width - dp(4)
            while (textSize > dp(11) && labelPaint.apply { this.textSize = textSize }.measureText(text) > maxWidth) {
                textSize *= 0.92f
            }
            labelPaint.textSize = textSize
            val fm = labelPaint.fontMetrics
            // English numbers and symbols share the same corner hint style and letter alignment.
            val centerY = height / 2f +
                (if (hint.isNullOrEmpty() || prominentHint) 0f else dp(KeyTypography.HINT_LABEL_SHIFT_DP)) -
                (if (KeyTypography.isLatinLetter(text)) dp(1.5f) else 0f)
            val baseline = if (KeyTypography.isSinhala(text)) KeyTypography.sinhalaBaseline(centerY, fm) else KeyTypography.baseline(centerY, fm)
            labelPaint.getTextBounds(text, 0, text.length, mainInkBounds)
            mainInkTop = baseline + mainInkBounds.top
            val mainStart = (width - labelPaint.measureText(text)) / 2f
            mainInkLeft = mainStart + mainInkBounds.left
            mainInkRight = mainStart + mainInkBounds.right
            canvas.drawText(text, width / 2f, baseline, labelPaint)
        }
        if (!hint.isNullOrEmpty()) {
            // A Sinhala letter hint takes the right corner, so the long-press number or symbol goes on the left
            val second = key.extras.firstOrNull()?.first?.takeIf { it != hint }
            val inset = if (prominentHint) dp(1.5f) else dp(KeyTypography.HINT_INSET_DP)
            if (theme.keyShape == KeyShape.PILL) {
                // Round tops have no corners, so hints sit side by side at the top centre (Gboard's round keys)
                val gap = dp(2)
                if (second == null) drawHint(canvas, hint, Paint.Align.CENTER, width / 2f, prominentHint, mainInkTop, mainInkLeft, mainInkRight)
                else {
                    drawHint(canvas, second, Paint.Align.RIGHT, width / 2f - gap, false, mainInkTop, mainInkLeft, mainInkRight)
                    drawHint(canvas, hint, Paint.Align.LEFT, width / 2f + gap, prominentHint, mainInkTop, mainInkLeft, mainInkRight)
                }
            } else {
                drawHint(canvas, hint, Paint.Align.RIGHT, width - inset, prominentHint, mainInkTop, mainInkLeft, mainInkRight)
                second?.let { drawHint(canvas, it, Paint.Align.LEFT, inset, false, mainInkTop, mainInkLeft, mainInkRight) }
            }
        }
    }

    private fun drawHint(
        canvas: Canvas, hint: String, align: Paint.Align, x: Float,
        prominentHint: Boolean = false, mainTop: Float = height.toFloat(),
        mainLeft: Float = width.toFloat(), mainRight: Float = 0f
    ) {
        val sinhala = KeyTypography.isSinhala(hint)
        hintPaint.color = ColorUtils.setAlphaComponent(theme.hint, if (prominentHint) 180 else if (sinhala) 175 else 140)
        hintPaint.typeface = KeyTypography.keyTypeface()
        hintPaint.textAlign = align
        var hintSize = KeyTypography.hintPx(resources, hint, prominentHint)
        val maxHintWidth = width * if (sinhala) 0.44f else 0.4f
        val top = dp(if (theme.keyShape == KeyShape.PILL) 5f else KeyTypography.HINT_INSET_DP) +
            if (prominentHint) dp(1f) else 0f
        var baseline: Float
        while (true) {
            hintPaint.textSize = hintSize
            hintPaint.getTextBounds(hint, 0, hint.length, hintInkBounds)
            baseline = if (sinhala) top - hintInkBounds.top else top - hintPaint.fontMetrics.ascent * 0.72f
            val hintWidth = hintPaint.measureText(hint)
            val start = when (align) {
                Paint.Align.RIGHT -> x - hintWidth
                Paint.Align.CENTER -> x - hintWidth / 2f
                else -> x
            }
            val left = start + hintInkBounds.left
            val right = start + hintInkBounds.right
            val overlaps = baseline + hintInkBounds.bottom + dp(2f) > mainTop &&
                right + dp(2f) > mainLeft && left - dp(2f) < mainRight
            if (hintWidth <= maxHintWidth && left >= dp(1f) && right <= width - dp(1f) && !overlaps) break
            if (hintSize <= dp(7f)) return
            hintSize *= 0.9f
        }
        canvas.drawText(hint, x, baseline, hintPaint)
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        invalidate()
    }

    override fun onDetachedFromWindow() {
        cancelSpaceCaption()
        super.onDetachedFromWindow()
    }

    fun showSpaceCaption(animate: Boolean) {
        cancelSpaceCaption()
        if (spec?.action != KeyCode.SPACE) return
        if (animate) {
            spaceProgress = 0f
            invalidate()
            spaceHandler.postDelayed(collapseSpace, KeyboardGeometry.SPACE_INTRO_MS)
        } else {
            spaceProgress = 1f
            invalidate()
        }
    }

    private fun iconFor(res: Int): Drawable? {
        val cached = icon
        if (cached != null && iconRes == res) {
            DrawableCompat.setTint(cached, inkFor(spec))
            return cached
        }
        val raw = ContextCompat.getDrawable(context, res) ?: return null
        val wrapped = DrawableCompat.wrap(raw.mutate())
        DrawableCompat.setTint(wrapped, inkFor(spec))
        icon = wrapped
        iconRes = res
        return wrapped
    }

    private fun inkFor(key: KeySpec?) = if (key?.action == KeyCode.ENTER) theme.accentInk else theme.ink

    private fun description(key: KeySpec) = when (key.action) {
        KeyCode.SHIFT -> "Shift"
        KeyCode.DELETE -> "Delete"
        KeyCode.SPACE -> "Space"
        KeyCode.ENTER -> "Enter"
        KeyCode.EMOJI -> "Emoji"
        KeyCode.GLOBE -> "Next keyboard"
        KeyCode.LANGUAGE -> "Switch keyboard language"
        KeyCode.LAYER -> when (key.payload) {
            KeyboardLayer.NUMBERS.name -> "Numbers and symbols"
            KeyboardLayer.LETTERS.name -> "Letters"
            else -> key.label
        }
        KeyCode.CHAR -> if (key.id == "rakaranshaya") {
            if (key.label == "ZWJ") "Zero width joiner" else "Rakaranshaya"
        } else key.label.ifEmpty { key.id }
    }

    private fun drawSpaceCaption(canvas: Canvas, text: String) {
        val progress = spaceProgress.coerceIn(0f, 1f)
        val density = resources.displayMetrics.scaledDensity
        val introSize = fitSpaceSize(text, KeyboardGeometry.SPACE_INTRO_SP * density)
        val collapsedSize = introSize * (KeyboardGeometry.SPACE_COLLAPSE_SP / KeyboardGeometry.SPACE_INTRO_SP)
        val textSize = introSize + (collapsedSize - introSize) * progress
        val scale = 1f + (KeyboardGeometry.SPACE_COLLAPSE_SCALE - 1f) * progress
        val alpha = (255f * (1f + (KeyboardGeometry.SPACE_COLLAPSE_ALPHA - 1f) * progress)).toInt()
        labelPaint.textSize = textSize
        labelPaint.color = ColorUtils.setAlphaComponent(theme.ink, alpha)
        labelPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        val fm = labelPaint.fontMetrics
        val textWidth = labelPaint.measureText(text)
        val textHeight = fm.descent - fm.ascent
        val introX = width / 2f
        val introY = height / 2f
        val collapsedX = width - dp(10) - textWidth / 2f
        val collapsedY = height - dp(7) - textHeight / 2f
        val gx = introX + (collapsedX - introX) * progress
        val gy = introY + (collapsedY - introY) * progress
        canvas.save()
        canvas.scale(scale, scale, gx, gy)
        canvas.drawText(text, gx, KeyTypography.baseline(gy, fm), labelPaint)
        canvas.restore()
    }

    private fun fitSpaceSize(text: String, start: Float): Float {
        var size = start
        val maxWidth = width - dp(16)
        while (size > dp(9) && labelPaint.apply { textSize = size }.measureText(text) > maxWidth) {
            size *= 0.92f
        }
        return size
    }

    private fun animateSpaceCollapse() {
        spaceAnimator?.cancel()
        if (!ValueAnimator.areAnimatorsEnabled()) {
            spaceProgress = 1f
            invalidate()
            return
        }
        spaceAnimator = ValueAnimator.ofFloat(spaceProgress, 1f).apply {
            duration = KeyboardGeometry.SPACE_COLLAPSE_MS
            interpolator = PathInterpolator(0.22f, 1f, 0.36f, 1f)
            addUpdateListener {
                spaceProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun cancelSpaceCaption() {
        spaceHandler.removeCallbacks(collapseSpace)
        spaceAnimator?.cancel()
        spaceAnimator = null
    }

    private fun dp(value: Int) = value * resources.displayMetrics.density
    private fun dp(value: Float) = value * resources.displayMetrics.density
}
