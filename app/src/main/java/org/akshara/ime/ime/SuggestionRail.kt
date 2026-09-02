package org.akshara.ime.ime

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
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
    private var values: List<String?> = listOf(null, null, null)

    init {
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

        empty.textSize = 14f
        empty.setTextColor(ColorUtils.setAlphaComponent(ink, 170))
        empty.gravity = Gravity.CENTER
        empty.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        clipboard.setImageResource(org.akshara.ime.R.drawable.ic_key_clipboard)
        clipboard.imageTintList = android.content.res.ColorStateList.valueOf(ink)
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
        addView(clipboard, LayoutParams(dp(44), LayoutParams.MATCH_PARENT, Gravity.END or Gravity.CENTER_VERTICAL))
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
        val end = if (visible) dp(44) else 0
        chipRow.setPadding(0, 0, end, 0)
        empty.setPadding(0, 0, end, 0)
    }

    fun setSuggestions(ranked: List<String>, animated: Boolean) {
        val presented = present(ranked)
        val motion = animated && motionEnabled() && hasWindow()
        val becameEmpty = presented.all { it == null }
        val wasEmpty = values.all { it == null }
        values = presented
        showEmpty(becameEmpty)
        chips.forEachIndexed { index, chip ->
            chip.setCandidate(presented[index], motion && !becameEmpty && !wasEmpty)
        }
    }

    private fun showEmpty(emptyState: Boolean) {
        emptyRow.visibility = if (emptyState) VISIBLE else INVISIBLE
        chipRow.visibility = if (emptyState) INVISIBLE else VISIBLE
        emptyRow.isClickable = emptyState
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
    private fun hasWindow() = isAttachedToWindow && width > 1
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        fun present(ranked: List<String>): List<String?> = when (ranked.size) {
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

/** Shared-prefix fade: matching text stays; replacements fade in at full opacity. */
internal class MorphLabel(context: Context, private val color: Int) : FrameLayout(context) {
    private val label = TextView(context).apply {
        textSize = 17f
        setTextColor(color)
        gravity = Gravity.CENTER
        includeFontPadding = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        setPadding(dp(4), dp(8), dp(4), dp(4))
    }
    private var text = ""

    init {
        isClickable = false
        isFocusable = false
        clipChildren = false
        clipToPadding = false
        addView(label, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun setText(incoming: String?, animated: Boolean) {
        val next = incoming.orEmpty()
        if (next == text) {
            label.alpha = 1f
            return
        }
        label.animate().cancel()
        text = next
        label.text = next
        label.alpha = 1f
        if (animated && next.isNotEmpty() && ValueAnimator.areAnimatorsEnabled()) {
            label.scaleX = 0.96f
            label.scaleY = 0.96f
            label.animate().scaleX(1f).scaleY(1f).setDuration(140).setInterpolator(DecelerateInterpolator()).start()
        } else {
            label.scaleX = 1f
            label.scaleY = 1f
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
