package org.akshara.ime.ime

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.graphics.ColorUtils

/** Gboard-style press preview and long-press alternate strip. */
internal class KeyPopups(private val context: Context) {
    private val preview = PopupWindow(context)
    private val picker = PopupWindow(context)
    private val previewLabel = TextView(context)
    private var pickerRow: LinearLayout? = null
    private var choices: List<Pair<String, String>> = emptyList()
    private var selected = 0
    private var itemWidth = 1
    private var panelColor = Color.WHITE
    private var labelColor = Color.rgb(25, 28, 33)
    private var darkTheme = false
    private val density = context.resources.displayMetrics.density

    init {
        val clear = ColorDrawable(Color.TRANSPARENT)
        preview.setBackgroundDrawable(clear)
        picker.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        previewLabel.gravity = Gravity.CENTER
        previewLabel.textSize = KeyboardGeometry.PREVIEW_TEXT_SP
        previewLabel.includeFontPadding = false
        if (Build.VERSION.SDK_INT >= 28) previewLabel.isFallbackLineSpacing = false
        preview.contentView = previewLabel
        preview.isClippingEnabled = false
        preview.isTouchable = false
        preview.inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
        preview.elevation = dp(12).toFloat()
        preview.animationStyle = 0
        picker.isClippingEnabled = false
        picker.isTouchable = false
        picker.inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
        picker.elevation = dp(14).toFloat()
        picker.animationStyle = 0
    }

    fun showPreview(key: View, text: String, fill: Int, dark: Boolean) {
        if (!key.isShown || text.isBlank()) return
        hidePicker()
        darkTheme = dark
        panelColor = fill
        labelColor = if (dark) Color.WHITE else Color.rgb(25, 28, 33)
        previewLabel.text = text
        previewLabel.setTextColor(labelColor)
        previewLabel.background = panel(fill, dp(14).toFloat())
        val width = maxOf(key.width + dp(4), dp(52))
        val height = dp(KeyboardGeometry.PREVIEW_HEIGHT_DP)
        preview.width = width
        preview.height = height
        previewLabel.textSize = KeyboardGeometry.PREVIEW_TEXT_SP
        val loc = IntArray(2)
        key.getLocationInWindow(loc)
        val screen = key.rootView?.width ?: Int.MAX_VALUE
        val x = (loc[0] - (width - key.width) / 2).coerceIn(dp(4), maxOf(dp(4), screen - width - dp(4)))
        val y = loc[1] - height + dp(8)
        if (preview.isShowing) preview.update(x, y, width, height) else preview.showAtLocation(key, Gravity.NO_GRAVITY, x, y)
    }

    fun hidePreview() {
        if (preview.isShowing) preview.dismiss()
    }

    fun showPicker(key: View, values: List<Pair<String, String>>, fill: Int, dark: Boolean) {
        if (!key.isShown || values.size < 2) return
        hidePreview()
        darkTheme = dark
        panelColor = fill
        labelColor = if (dark) Color.WHITE else Color.rgb(25, 28, 33)
        choices = values
        selected = 0
        itemWidth = maxOf(key.width, dp(48))
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(6), dp(6), dp(6))
            background = panel(fill, dp(18).toFloat())
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
            choices = values.drop(1).reversed() + values.first()
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
        hidePreview()
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
        setColor(ColorUtils.blendARGB(panelColor, if (darkTheme) Color.WHITE else Color.BLACK, 0.16f))
    }

    private fun dp(value: Int) = (value * density).toInt()
}
