package org.akshara.ime.ime

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
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

internal data class KeyboardColors(
    val key: Int,
    val utility: Int,
    val ink: Int,
    val dark: Boolean,
    val highContrast: Boolean
)

internal class KeyCap(context: Context) : View(context) {
    var spec: KeySpec? = null
        set(value) {
            if (value?.action != KeyCode.SPACE) cancelSpaceCaption()
            if (field?.action != value?.action) {
                radiusAnimator?.cancel()
                currentRadius = -1f
            }
            field = value
            tag = value?.id
            contentDescription = value?.let { description(it) }
            isClickable = value != null
            invalidate()
        }
    var colors: KeyboardColors = KeyboardColors(0, 0, 0, false, false)
        set(value) { field = value; invalidate() }
    var flickActive = false
        set(value) { field = value; invalidate() }
    var cornerRadiusDp: Float = KeyboardGeometry.LETTER_RADIUS_DP
        set(value) {
            if (field != value) {
                field = value
                currentRadius = -1f
                invalidate()
            }
        }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.RIGHT }
    private val rect = RectF()
    private var icon: Drawable? = null
    private var iconRes = 0
    private var spaceProgress = 1f
    private var spaceAnimator: ValueAnimator? = null
    private val spaceHandler = Handler(Looper.getMainLooper())
    private val collapseSpace = Runnable { animateSpaceCollapse() }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    private var currentRadius = -1f
    private var radiusAnimator: ValueAnimator? = null

    private fun defaultRadius(key: KeySpec): Float {
        return when (key.action) {
            KeyCode.LAYER, KeyCode.ENTER -> if (height > 0) height / 2f else dp(cornerRadiusDp)
            else -> dp(cornerRadiusDp)
        }
    }

    override fun onDraw(canvas: Canvas) {
        val key = spec ?: return
        val pressed = isPressed
        val base = if (key.utility) colors.utility else colors.key
        fill.color = if (pressed) ColorUtils.blendARGB(base, if (colors.dark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt(), 0.18f) else base
        val defRadius = defaultRadius(key)
        val targetRadius = if (pressed) dp(4f) else defRadius
        if (currentRadius < 0f || (!pressed && (key.action == KeyCode.LAYER || key.action == KeyCode.ENTER) && currentRadius < defRadius)) {
            currentRadius = targetRadius
        }
        val radius = currentRadius
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(rect, radius, radius, fill)
        if (colors.highContrast) {
            fill.style = Paint.Style.STROKE
            fill.strokeWidth = dp(2)
            fill.color = colors.ink
            canvas.drawRoundRect(rect, radius, radius, fill)
            fill.style = Paint.Style.FILL
        }
        if (key.icon != null) {
            val drawable = iconFor(key.icon)
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
        if (text.isNotEmpty()) {
            val function = key.utility || text.length > 2 && !KeyTypography.isSinhala(text)
            labelPaint.color = colors.ink
            var textSize = if (function) KeyTypography.functionPx(resources) else KeyTypography.mainPx(resources, text)
            val maxWidth = width - dp(4)
            while (textSize > dp(11) && labelPaint.apply { this.textSize = textSize }.measureText(text) > maxWidth) {
                textSize *= 0.92f
            }
            labelPaint.textSize = textSize
            val fm = labelPaint.fontMetrics
            val baseline = if (KeyTypography.isSinhala(text)) KeyTypography.sinhalaBaseline(height / 2f, fm) else KeyTypography.baseline(height / 2f, fm)
            canvas.drawText(text, width / 2f, baseline, labelPaint)
        }
        val hint = key.hint
        if (!hint.isNullOrEmpty() && !flickActive) {
            hintPaint.color = ColorUtils.setAlphaComponent(colors.ink, 150)
            hintPaint.textSize = KeyTypography.hintPx(resources)
            canvas.drawText(hint, width - dp(4), dp(13), hintPaint)
        }
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        val key = spec
        if (key != null) {
            val defaultRad = defaultRadius(key)
            val targetRadius = if (isPressed) dp(4f) else defaultRad
            if (currentRadius < 0f) {
                currentRadius = targetRadius
            } else if (currentRadius != targetRadius) {
                radiusAnimator?.cancel()
                if (ValueAnimator.areAnimatorsEnabled() && isAttachedToWindow) {
                    radiusAnimator = ValueAnimator.ofFloat(currentRadius, targetRadius).apply {
                        duration = if (isPressed) 70L else 180L
                        interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
                        addUpdateListener {
                            currentRadius = it.animatedValue as Float
                            invalidate()
                        }
                        start()
                    }
                } else {
                    currentRadius = targetRadius
                    invalidate()
                }
            }
        }
        invalidate()
    }

    override fun onDetachedFromWindow() {
        cancelSpaceCaption()
        radiusAnimator?.cancel()
        radiusAnimator = null
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
            DrawableCompat.setTint(cached, colors.ink)
            return cached
        }
        val raw = ContextCompat.getDrawable(context, res) ?: return null
        val wrapped = DrawableCompat.wrap(raw.mutate())
        DrawableCompat.setTint(wrapped, colors.ink)
        icon = wrapped
        iconRes = res
        return wrapped
    }

    private fun description(key: KeySpec) = when (key.action) {
        KeyCode.SHIFT -> "Shift"
        KeyCode.DELETE -> "Delete"
        KeyCode.SPACE -> "Space"
        KeyCode.ENTER -> "Enter"
        KeyCode.EMOJI -> "Emoji"
        KeyCode.GLOBE -> "Next keyboard"
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
        labelPaint.color = ColorUtils.setAlphaComponent(colors.ink, alpha)
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
