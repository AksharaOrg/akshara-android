package org.akshara.ime.ime

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView

/** Gboard-style press preview and long-press alternate strip. */
internal class KeyPopups(private val context: Context) {
    private val preview = PopupWindow(context)
    private val picker = PopupWindow(context)
    private val previewLabel = TextView(context)
    private val previewHost = FrameLayout(context)
    private var pickerRow: LinearLayout? = null
    private var choices: List<Pair<String, String>> = emptyList()
    private var selected = 0
    private var itemWidth = 1
    private var panelColor = Color.WHITE
    private var labelColor = Color.BLACK
    private var selectedColor = Color.LTGRAY
    private var previewColor = 0
    private val density = context.resources.displayMetrics.density

    init {
        val clear = ColorDrawable(Color.TRANSPARENT)
        preview.setBackgroundDrawable(clear)
        picker.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        previewLabel.gravity = Gravity.CENTER
        previewLabel.textSize = KeyboardGeometry.PREVIEW_TEXT_SP
        previewLabel.includeFontPadding = false
        if (Build.VERSION.SDK_INT >= 28) previewLabel.isFallbackLineSpacing = false
        // The bubble casts its own shadow; the window around it is transparent and lets touches through
        previewLabel.elevation = dp(12).toFloat()
        previewLabel.visibility = View.INVISIBLE
        previewHost.clipChildren = false
        previewHost.addView(previewLabel, FrameLayout.LayoutParams(0, 0))
        preview.contentView = previewHost
        preview.isClippingEnabled = false
        preview.isTouchable = false
        preview.inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
        preview.animationStyle = 0
        picker.isClippingEnabled = false
        picker.isTouchable = false
        picker.inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
        picker.elevation = dp(14).toFloat()
        picker.animationStyle = 0
    }

    fun showPreview(key: View, text: String, theme: KeyboardTheme) {
        if (!key.isShown || text.isBlank()) return
        hidePicker()
        useTheme(theme)
        previewLabel.text = text
        previewLabel.setTextColor(labelColor)
        if (previewColor != panelColor || previewLabel.background == null) {
            previewColor = panelColor
            previewLabel.background = panel(panelColor, dp(14).toFloat())
        }
        val width = maxOf(key.width + dp(4), dp(52))
        val height = dp(KeyboardGeometry.PREVIEW_HEIGHT_DP)
        val loc = IntArray(2)
        key.getLocationInWindow(loc)
        val root = key.rootView
        val screen = root?.width ?: return
        val x = (loc[0] - (width - key.width) / 2).coerceIn(dp(4), maxOf(dp(4), screen - width - dp(4)))
        val y = loc[1] - height + dp(8)
        // One window covers the keyboard plus a preview's height above it, and the bubble moves inside it.
        // Moving a window and redrawing its content land in different frames, which flashed the previous
        // letter; moving a view within a window happens in the same frame as its new text.
        val hostTop = -height
        val hostWidth = screen
        val hostHeight = root.height + height
        (previewLabel.layoutParams as FrameLayout.LayoutParams).let { params ->
            if (params.width != width || params.height != height) {
                params.width = width
                params.height = height
                previewLabel.layoutParams = params
            }
        }
        previewLabel.translationX = x.toFloat()
        previewLabel.translationY = (y - hostTop).toFloat()
        previewLabel.visibility = View.VISIBLE
        if (!preview.isShowing) {
            preview.width = hostWidth
            preview.height = hostHeight
            preview.showAtLocation(key, Gravity.NO_GRAVITY, 0, hostTop)
        } else if (!previewExpanded || preview.width != hostWidth || preview.height != hostHeight) {
            preview.update(0, hostTop, hostWidth, hostHeight)
        }
        previewExpanded = true
    }

    /** Whether the preview window currently spans the keyboard, rather than idling as one pixel inside it. */
    private var previewExpanded = false

    /**
     * Hides the press preview but keeps its window: adding and removing a window on every key press costs
     * milliseconds of main-thread time. [dismiss] removes it when the keyboard goes away.
     */
    fun hidePreview() {
        if (!preview.isShowing) return
        previewLabel.visibility = View.INVISIBLE
        // The spanning window reaches above the keyboard, over the app. Android treats even an invisible window
        // there as covering the app (it can block taps and warns that the keyboard is displaying over other
        // apps), so between presses it shrinks to one pixel inside the keyboard. Its last frame is empty, so
        // growing it again can't flash an old letter.
        if (previewExpanded) {
            previewExpanded = false
            preview.update(0, 0, 1, 1)
        }
    }

    fun showPicker(key: View, values: List<Pair<String, String>>, theme: KeyboardTheme) {
        if (!key.isShown || values.isEmpty()) return
        hidePreview()
        useTheme(theme)
        choices = values
        selected = 0    // Gboard-style: the first extra (the key's hint) is ready on release
        itemWidth = maxOf(key.width, dp(48))
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(6), dp(6), dp(6))
            background = panel(panelColor, dp(18).toFloat())
        }
        values.forEachIndexed { index, choice -> row.addView(pickerCell(choice.first, index == selected), LinearLayout.LayoutParams(itemWidth, dp(48))) }
        pickerRow = row
        picker.contentView = row
        val width = itemWidth * values.size + dp(12)
        val height = dp(60)
        picker.width = width
        picker.height = height
        val loc = IntArray(2)
        key.getLocationInWindow(loc)
        val expandsRight = loc[0] + width <= (key.rootView?.width ?: Int.MAX_VALUE) - dp(4)
        if (!expandsRight) {
            choices = values.reversed()
            selected = choices.lastIndex
            row.removeAllViews()
            choices.forEachIndexed { index, choice ->
                row.addView(pickerCell(choice.first, index == selected), LinearLayout.LayoutParams(itemWidth, dp(48)))
            }
        }
        val selectedX = loc[0] - dp(6) - selected * itemWidth
        val maxX = (key.rootView?.width ?: loc[0] + width) - width - dp(4)
        val x = selectedX.coerceIn(dp(4), maxOf(dp(4), maxX))
        val y = loc[1] - height - dp(2)
        if (picker.isShowing) picker.update(x, y, width, height) else picker.showAtLocation(key, Gravity.NO_GRAVITY, x, y)
    }

    fun movePicker(event: MotionEvent): Int {
        val row = pickerRow ?: return selected
        val loc = IntArray(2)
        row.getLocationOnScreen(loc)
        val index = ((event.rawX - loc[0] - dp(6)) / itemWidth).toInt().coerceIn(0, choices.lastIndex)
        if (index != selected) {
            selected = index
            paintSelection()
        }
        return selected
    }

    fun hidePicker(): String? {
        val value = choices.getOrNull(selected)?.second
        if (picker.isShowing) picker.dismiss()
        pickerRow = null
        choices = emptyList()
        return value
    }

    fun dismiss() {
        if (preview.isShowing) preview.dismiss()
        previewExpanded = false
        hidePicker()
    }

    private fun paintSelection() {
        val row = pickerRow ?: return
        for (i in 0 until row.childCount) {
            val label = row.getChildAt(i) as TextView
            val active = i == selected
            label.setTextColor(labelColor)
            label.background = if (active) selection() else null
        }
    }

    private fun pickerCell(text: String, active: Boolean) = TextView(context).apply {
        this.text = text
        gravity = Gravity.CENTER
        textSize = 22f
        includeFontPadding = false
        if (Build.VERSION.SDK_INT >= 28) isFallbackLineSpacing = false
        setTextColor(labelColor)
        background = if (active) selection() else null
    }

    private fun panel(color: Int, radius: Float) = GradientDrawable().apply {
        cornerRadius = radius
        setColor(color)
    }

    private fun selection() = GradientDrawable().apply {
        cornerRadius = dp(24).toFloat()
        setColor(selectedColor)
    }

    private fun useTheme(theme: KeyboardTheme) {
        panelColor = theme.popup
        labelColor = theme.popupInk
        selectedColor = theme.popupSelected
    }

    private fun dp(value: Int) = (value * density).toInt()
}
