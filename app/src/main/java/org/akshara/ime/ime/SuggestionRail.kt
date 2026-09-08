package org.akshara.ime.ime

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils

internal class SuggestionRail(
    context: Context,
    private val ink: Int,
    private val onCandidate: (String) -> Unit,
    private val onClipboard: () -> Unit
) : FrameLayout(context) {
    var keySliver = 0
    private val chips = Array(3) { MorphChip(context, ink) }
    private val chipRow = LinearLayout(context)
    private val empty = TextView(context)
    private val clipboard = ImageView(context)
    private val emptyRow = LinearLayout(context)

    init {
        blockForceDark()
        clipChildren = false
        clipToPadding = false
        chipRow.orientation = LinearLayout.HORIZONTAL
        chipRow.clipChildren = false
        chipRow.clipToPadding = false
        chips.forEachIndexed { index, chip ->
            if (index > 0) chipRow.addView(divider())
            chipRow.addView(chip, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        }
        addView(chipRow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        empty.blockForceDark()
        empty.textSize = 14f
        empty.setTextColor(ColorUtils.setAlphaComponent(ink, 170))
        empty.gravity = Gravity.CENTER
        empty.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        clipboard.setImageResource(org.akshara.ime.R.drawable.ic_key_clipboard)
        clipboard.setColorFilter(ink)
        clipboard.scaleType = ImageView.ScaleType.CENTER_INSIDE
        clipboard.setPadding(dp(10), dp(8), dp(10), dp(8))
        clipboard.contentDescription = "Clipboard history"
        clipboard.isClickable = true
        clipboard.isFocusable = true
        val ripple = android.util.TypedValue()
        if (context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true)) {
            clipboard.setBackgroundResource(ripple.resourceId)
        }
        clipboard.setOnClickListener { onClipboard() }
        emptyRow.orientation = LinearLayout.HORIZONTAL
        emptyRow.gravity = Gravity.CENTER_VERTICAL
        emptyRow.addView(empty, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(emptyRow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(clipboard, LayoutParams(dp(44), LayoutParams.MATCH_PARENT, Gravity.START or Gravity.CENTER_VERTICAL))
        showEmpty(true)
        setClipboardVisible(false)
    }

    fun setEmptyTitle(title: String) {
        empty.text = title
    }

    fun setClipboardVisible(visible: Boolean) {
        clipboard.visibility = if (visible) VISIBLE else GONE
        clipboard.isClickable = visible
        clipboard.isFocusable = visible
        clipboard.importantForAccessibility = if (visible) IMPORTANT_FOR_ACCESSIBILITY_YES else IMPORTANT_FOR_ACCESSIBILITY_NO
        val start = if (visible) dp(44) else 0
        chipRow.setPadding(start, 0, 0, 0)
        empty.setPadding(start, 0, 0, 0)
        if (visible) clipboard.bringToFront()
    }

    fun setSuggestions(ranked: List<String>, animated: Boolean, emoji: String? = null) {
        val presented = present(ranked, emoji)
        val motion = animated && motionEnabled()
        showEmpty(presented.all { it == null })
        chips.forEachIndexed { index, chip ->
            chip.setCandidate(presented[index], motion)
        }
    }

    private fun showEmpty(emptyState: Boolean) {
        emptyRow.visibility = if (emptyState) VISIBLE else INVISIBLE
        chipRow.visibility = if (emptyState) INVISIBLE else VISIBLE
        emptyRow.isClickable = false
        emptyRow.alpha = if (emptyState) 1f else 0f
        chipRow.alpha = 1f
        emptyRow.animate().cancel()
        chipRow.animate().cancel()
    }

    private fun divider() = View(context).apply {
        setBackgroundColor(ColorUtils.setAlphaComponent(ink, 40))
        layoutParams = LinearLayout.LayoutParams(dp(1), LayoutParams.MATCH_PARENT).apply {
            topMargin = dp(10)
            bottomMargin = dp(10)
        }
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private fun motionEnabled() = ValueAnimator.areAnimatorsEnabled()
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        fun present(ranked: List<String>, emoji: String? = null): List<String?> = if (emoji != null) {
            listOf(ranked.getOrNull(1), ranked.firstOrNull(), emoji)
        } else when (ranked.size) {
            3 -> listOf(ranked[1], ranked[0], ranked[2])
            2 -> listOf(ranked[1], ranked[0], null)
            1 -> listOf(null, ranked[0], null)
            else -> listOf(null, null, null)
        }
    }

    private inner class MorphChip(context: Context, private val color: Int) : FrameLayout(context) {
        private val morph = MorphLabel(context, color)
        private var text: String? = null

        init {
            blockForceDark()
            clipChildren = false
            clipToPadding = false
            addView(morph, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            isClickable = false
            isFocusable = true
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        fun setCandidate(value: String?, animated: Boolean) {
            text = value
            isClickable = value != null
            isFocusable = value != null
            contentDescription = value?.let { "Suggestion $it" }
            importantForAccessibility = if (value != null) IMPORTANT_FOR_ACCESSIBILITY_YES else IMPORTANT_FOR_ACCESSIBILITY_NO
            morph.setText(value, animated)
        }

        override fun drawableStateChanged() {
            super.drawableStateChanged()
            morph.alpha = if (isPressed) 0.35f else 1f
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            val value = text ?: return false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    isPressed = true
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    isPressed = false
                    if (event.y >= 0 && event.y <= height - keySliver) onCandidate(value)
                    performClick()
                    return true
                }
                MotionEvent.ACTION_CANCEL -> {
                    isPressed = false
                    return true
                }
            }
            return super.onTouchEvent(event)
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }
    }
}

/** Per-glyph shared-prefix morph, matching iOS CandidateMorphLabel. */
internal class MorphLabel(context: Context, color: Int) : ViewGroup(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = SuggestionMorph.TEXT_SP * resources.displayMetrics.scaledDensity
        typeface = Typeface.DEFAULT
        this.color = color
    }
    private var text = ""
    private val glyphs = ArrayList<GlyphView>()
    private val retiring = ArrayList<GlyphView>()
    private var lastW = 0
    private var lastH = 0
    private var pendingText: String? = null
    private var pendingAnimated = false
    private var motionCount = 0

    init {
        blockForceDark()
        isClickable = false
        isFocusable = false
        clipChildren = false
        clipToPadding = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val w = r - l
        val h = b - t
        if (w <= 1) return
        val pending = pendingText
        if (pending != null) {
            pendingText = null
            val animate = pendingAnimated
            pendingAnimated = false
            apply(pending, animate)
            return
        }
        val sizeChanged = kotlin.math.abs(w - lastW) > 1 || kotlin.math.abs(h - lastH) > 1
        lastW = w
        lastH = h
        if (motionCount > 0) {
            preserveChildren()
            return
        }
        if (glyphs.isEmpty() && text.isNotEmpty()) {
            rebuild(text)
            return
        }
        if (sizeChanged && glyphs.isNotEmpty()) applyFrames(glyphs, displayedRun(text))
        else preserveChildren()
    }

    private fun preserveChildren() {
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            child.layout(child.left, child.top, child.right, child.bottom)
        }
    }

    fun setText(incoming: String?, animated: Boolean) {
        val next = incoming.orEmpty()
        if (next == text && pendingText == null) return
        if (width <= 1) {
            pendingText = next
            pendingAnimated = animated
            text = next
            requestLayout()
            return
        }
        apply(next, animated)
    }

    private fun apply(next: String, animated: Boolean) {
        settle()
        val canAnimate = animated &&
            ValueAnimator.areAnimatorsEnabled() &&
            isAttachedToWindow &&
            width > 1
        if (!canAnimate) {
            rebuild(next)
            return
        }
        morph(next)
    }

    private fun settle() {
        motionCount = 0
        retiring.toList().forEach { view ->
            view.animate().cancel()
            removeView(view)
        }
        retiring.clear()
        glyphs.forEach { view ->
            view.animate().cancel()
            view.alpha = 1f
            view.scaleX = 1f
            view.scaleY = 1f
        }
        if (glyphs.isNotEmpty() && width > 1) applyFrames(glyphs, displayedRun(text))
    }

    private fun rebuild(incoming: String) {
        glyphs.forEach { removeView(it) }
        glyphs.clear()
        text = incoming
        if (incoming.isEmpty() || width <= 1) return
        val run = displayedRun(incoming)
        val frames = glyphFrames(run)
        run.characters.forEachIndexed { index, character ->
            val view = makeGlyph(character)
            glyphs += view
            addView(view)
            layoutGlyph(view, frames[index])
        }
        lastW = width
        lastH = height
    }

    private fun morph(incoming: String) {
        val oldRun = displayedRun(text)
        val newRun = displayedRun(incoming)
        if (glyphs.size != oldRun.characters.size) {
            rebuild(incoming)
            return
        }
        if (oldRun.characters == newRun.characters) {
            text = incoming
            return
        }
        val oldFrames = glyphFrames(oldRun)
        val newFrames = glyphFrames(newRun)
        val overlap = minOf(oldRun.characters.size, newRun.characters.size)
        val nextViews = arrayOfNulls<GlyphView>(newRun.characters.size)
        val outgoing = ArrayList<GlyphView>()
        oldRun.characters.forEachIndexed { index, _ ->
            val view = glyphs[index]
            layoutGlyph(view, oldFrames[index])
            if (index < overlap && oldRun.characters[index] == newRun.characters[index]) {
                nextViews[index] = view
            } else {
                outgoing += view
            }
        }
        retiring.addAll(outgoing)
        outgoing.forEach { view ->
            view.animate().cancel()
            beginMotion()
            view.animate()
                .alpha(0f)
                .setDuration(SuggestionMorph.DISAPPEAR_MS)
                .setInterpolator(AccelerateInterpolator())
                .withEndAction {
                    if (retiring.remove(view)) removeView(view)
                    endMotion()
                }
                .start()
        }
        var appearOrder = 0
        newRun.characters.forEachIndexed { index, character ->
            val kept = nextViews[index]
            if (kept != null) {
                val target = newFrames[index]
                val dx = (target.left - kept.left).toFloat()
                val dy = (target.top - kept.top).toFloat()
                if (kotlin.math.abs(dx) > 0.5f || kotlin.math.abs(dy) > 0.5f) {
                    kept.animate().cancel()
                    kept.translationX = 0f
                    kept.translationY = 0f
                    beginMotion()
                    kept.animate()
                        .translationX(dx)
                        .translationY(dy)
                        .setDuration(SuggestionMorph.SHIFT_MS)
                        .setInterpolator(DecelerateInterpolator())
                        .withEndAction {
                            kept.translationX = 0f
                            kept.translationY = 0f
                            layoutGlyph(kept, target)
                            endMotion()
                        }
                        .start()
                } else {
                    layoutGlyph(kept, target)
                }
                return@forEachIndexed
            }
            val view = makeGlyph(character)
            addView(view)
            layoutGlyph(view, newFrames[index])
            view.alpha = 0f
            view.scaleX = SuggestionMorph.APPEAR_SCALE
            view.scaleY = SuggestionMorph.APPEAR_SCALE
            view.pivotX = view.width / 2f
            view.pivotY = view.height / 2f
            nextViews[index] = view
            val delay = SuggestionMorph.STAGGER_MS * appearOrder
            appearOrder += 1
            beginMotion()
            view.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setStartDelay(delay)
                .setDuration(SuggestionMorph.APPEAR_MS)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction { endMotion() }
                .start()
        }
        glyphs.clear()
        nextViews.forEach { if (it != null) glyphs += it }
        text = incoming
        lastW = width
        lastH = height
    }

    private fun makeGlyph(character: String): GlyphView = GlyphView(context, paint).apply {
        glyph = character
    }

    private data class Frame(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width = right - left
    }

    private fun applyFrames(views: List<GlyphView>, run: SuggestionMorph.Run) {
        if (views.size != run.characters.size) return
        val frames = glyphFrames(run)
        views.forEachIndexed { index, view ->
            view.translationX = 0f
            view.translationY = 0f
            layoutGlyph(view, frames[index])
        }
    }

    private fun beginMotion() {
        motionCount++
    }

    private fun endMotion() {
        motionCount = (motionCount - 1).coerceAtLeast(0)
        if (motionCount == 0 && width > 1 && glyphs.isNotEmpty()) applyFrames(glyphs, displayedRun(text))
    }

    private fun displayedRun(value: String): SuggestionMorph.Run {
        val inset = SuggestionMorph.INSET_DP * resources.displayMetrics.density
        return SuggestionMorph.displayedRun(value, (width - inset * 2).coerceAtLeast(0f), paint)
    }

    private fun glyphFrames(run: SuggestionMorph.Run): List<Frame> {
        if (run.characters.isEmpty()) return emptyList()
        val sizes = run.characters.map { paint.measureText(it) }
        val total = sizes.sum()
        val fm = paint.fontMetrics
        val box = (fm.bottom - fm.top).coerceAtLeast(paint.fontSpacing)
        val extra = resources.displayMetrics.density * 3f
        val glyphHeight = (box + extra).coerceAtMost(height.toFloat()).coerceAtLeast(1f)
        val top = ((height - glyphHeight) / 2f).toInt().coerceAtLeast(0)
        val bottom = (top + glyphHeight).toInt().coerceAtMost(height).coerceAtLeast(top + 1)
        val inset = (SuggestionMorph.INSET_DP * resources.displayMetrics.density).toInt()
        var x = if (run.truncated) inset.toFloat() else (width - total) / 2f
        return sizes.map { size ->
            val left = x.toInt()
            x += size
            Frame(left, top, (left + size).toInt().coerceAtLeast(left + 1), bottom)
        }
    }

    private fun layoutGlyph(view: GlyphView, frame: Frame) {
        view.measure(
            MeasureSpec.makeMeasureSpec(frame.width.coerceAtLeast(1), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec((frame.bottom - frame.top).coerceAtLeast(1), MeasureSpec.EXACTLY)
        )
        view.layout(frame.left, frame.top, frame.right, frame.bottom)
    }

    /** Canvas glyph so OEM force-dark cannot invert suggestion text. */
    private class GlyphView(context: Context, private val paint: Paint) : View(context) {
        var glyph: String = ""
            set(value) {
                field = value
                invalidate()
            }

        init {
            blockForceDark()
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            isClickable = false
            isFocusable = false
        }

        override fun onDraw(canvas: Canvas) {
            if (glyph.isEmpty()) return
            val saved = paint.textAlign
            paint.textAlign = Paint.Align.CENTER
            val fm = paint.fontMetrics
            val y = height / 2f - (fm.ascent + fm.descent) / 2f
            canvas.drawText(glyph, width / 2f, y, paint)
            paint.textAlign = saved
        }
    }
}

internal fun View.blockForceDark() {
    if (Build.VERSION.SDK_INT >= 29) isForceDarkAllowed = false
}
