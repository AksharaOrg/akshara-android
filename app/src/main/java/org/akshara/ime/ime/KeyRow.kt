package org.akshara.ime.ime

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import kotlin.math.hypot

/**
 * Letter-row hit testing. Visual keycaps keep their painted size; each key
 * owns the gutter to the midpoint of its neighbour so taps between caps are
 * not dead. Phonetic rows also give A / L the side leftovers and Z / M the
 * strips beside Shift / Delete. Wijesekara stays on half-gap snap so a
 * narrow Shift is not stolen by ්‍ර.
 */
internal class KeyRow(context: Context) : LinearLayout(context) {
    var expandsToRowEdges = false
    var hitExpansion = Rect()
    private var tracked: View? = null

    init {
        orientation = HORIZONTAL
        clipChildren = false
        clipToPadding = false
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                tracked = keyOwning(event.x, event.y)
                val target = tracked ?: return super.dispatchTouchEvent(event)
                return dispatchTransformed(target, event)
            }
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val target = tracked ?: return super.dispatchTouchEvent(event)
                val handled = dispatchTransformed(target, event)
                if (event.actionMasked != MotionEvent.ACTION_MOVE) tracked = null
                return handled
            }
        }
        return super.dispatchTouchEvent(event)
    }

    fun keyOwning(x: Float, y: Float): View? {
        val slots = hitSlots()
        if (slots.isEmpty()) return null
        val insideCap = slots.filter { it.cap.contains(x.toInt(), y.toInt()) }
        if (insideCap.size == 1) return insideCap[0].view
        if (insideCap.size > 1) {
            return insideCap.minByOrNull { hypot(it.cap.centerX() - x, it.cap.centerY() - y) }?.view
        }
        val cells = slots.filter { it.cell.contains(x.toInt(), y.toInt()) }
        return cells.minByOrNull { hypot(it.cap.centerX() - x, it.cap.centerY() - y) }?.view
    }

    fun keyNameAt(x: Float, y: Float): String? = keyOwning(x, y)?.tag as? String

    private fun hitSlots(): List<HitSlot> {
        val keys = keys()
        if (keys.isEmpty()) return emptyList()
        val cells = if (expandsToRowEdges) edgeCells(keys) else halfGapCells(keys)
        return keys.zip(cells) { key, cell -> HitSlot(key.view, key.frame, cell) }
    }

    private fun keys(): List<KeyedView> {
        val items = ArrayList<KeyedView>(childCount)
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility != VISIBLE) continue
            val name = child.tag as? String ?: continue
            if (name.startsWith(GUTTER)) continue
            items += KeyedView(name, child, Rect(child.left, child.top, child.right, child.bottom))
        }
        return items
    }

    private fun halfGapCells(keys: List<KeyedView>): List<Rect> {
        val minY = -hitExpansion.top
        val maxY = height + hitExpansion.bottom
        return keys.mapIndexed { index, key ->
            val left = if (index == 0) {
                key.frame.left - hitExpansion.left
            } else {
                (keys[index - 1].frame.right + key.frame.left) / 2
            }
            val right = if (index == keys.lastIndex) {
                key.frame.right + hitExpansion.right
            } else {
                (key.frame.right + keys[index + 1].frame.left) / 2
            }
            Rect(left, minY, right, maxY)
        }
    }

    private fun edgeCells(keys: List<KeyedView>): List<Rect> {
        val minY = -hitExpansion.top
        val maxY = height + hitExpansion.bottom
        val leading = -hitExpansion.left
        val trailing = width + hitExpansion.right
        val lefts = IntArray(keys.size)
        val rights = IntArray(keys.size)
        keys.forEachIndexed { index, key ->
            lefts[index] = if (index == 0) leading else (keys[index - 1].frame.right + key.frame.left) / 2
            rights[index] = if (index == keys.lastIndex) trailing else (key.frame.right + keys[index + 1].frame.left) / 2
        }
        keys.forEachIndexed { index, key ->
            when (key.name) {
                "a" -> lefts[index] = leading
                "l" -> if (index == keys.lastIndex) rights[index] = trailing
                else -> {}
            }
        }
        return keys.indices.map { Rect(lefts[it], minY, rights[it], maxY) }
    }

    private fun dispatchTransformed(child: View, event: MotionEvent): Boolean {
        val transformed = MotionEvent.obtain(event)
        val x = (event.x - child.left).coerceIn(0f, (child.width - 1).coerceAtLeast(0).toFloat())
        val y = (event.y - child.top).coerceIn(0f, (child.height - 1).coerceAtLeast(0).toFloat())
        transformed.setLocation(x, y)
        val handled = child.dispatchTouchEvent(transformed)
        transformed.recycle()
        return handled
    }

    private data class KeyedView(val name: String, val view: View, val frame: Rect)
    private data class HitSlot(val view: View, val cap: Rect, val cell: Rect)

    companion object {
        const val GUTTER = "gutter"
        const val SHIFT = "shift"
        const val DELETE = "delete"
    }
}
