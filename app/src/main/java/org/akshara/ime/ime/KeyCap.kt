package org.akshara.ime.ime

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
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
import org.akshara.ime.R

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
        if (key.action == KeyCode.SPACE) {
            drawSpaceLogo(canvas, radius)
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
        radiusAnimator?.cancel()
        radiusAnimator = null
        spaceLogoAnimator?.cancel()
        spaceLogoAnimator = null
        super.onDetachedFromWindow()
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

    private val clipPath = Path()
    private var spaceLogoDrawable: Drawable? = null
    private var spaceLogoAlpha = 25
    private var spaceLogoAnimator: ValueAnimator? = null

    fun showSpaceCaption(animate: Boolean) {
        spaceLogoAnimator?.cancel()
        if (spec?.action != KeyCode.SPACE) return
        if (animate && ValueAnimator.areAnimatorsEnabled()) {
            spaceLogoAlpha = 150
            spaceLogoAnimator = ValueAnimator.ofInt(190, 20).apply {
                duration = 2500L
                interpolator = PathInterpolator(0.22f, 1f, 0.36f, 1f)
                addUpdateListener {
                    spaceLogoAlpha = it.animatedValue as Int
                    invalidate()
                }
                start()
            }
        } else {
            spaceLogoAlpha = 25
            invalidate()
        }
    }

    private fun drawSpaceLogo(canvas: Canvas, radius: Float) {
        var logo = spaceLogoDrawable
        if (logo == null) {
            val raw = ContextCompat.getDrawable(context, R.drawable.ic_logo_vector) ?: return
            logo = DrawableCompat.wrap(raw.mutate())
            spaceLogoDrawable = logo
        }
        
        DrawableCompat.setTint(logo, ColorUtils.setAlphaComponent(colors.ink, spaceLogoAlpha))

        val logoH = (height * 1.75f).toInt()
        val logoW = logoH
        val right = width + (logoW * 0.25f).toInt()
        val left = right - logoW
        val top = (height - logoH) / 2
        val bottom = top + logoH

        logo.setBounds(left, top, right, bottom)

        canvas.save()
        clipPath.reset()
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        clipPath.addRoundRect(rect, radius, radius, Path.Direction.CW)
        canvas.clipPath(clipPath)
        logo.draw(canvas)
        canvas.restore()
    }

    private fun dp(value: Int) = value * resources.displayMetrics.density
    private fun dp(value: Float) = value * resources.displayMetrics.density
}
