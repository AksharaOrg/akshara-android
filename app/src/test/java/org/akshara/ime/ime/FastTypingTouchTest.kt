package org.akshara.ime.ime

import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ApplicationProvider
import org.akshara.ime.engine.InputMode
import org.akshara.ime.settings.KeyboardPreferences
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** Fast typing: the next finger often lands before the previous one lifts. No key may be lost or reordered. */
@RunWith(RobolectricTestRunner::class)
class FastTypingTouchTest {
    private val typed = mutableListOf<String>()
    private val actions = object : KeyboardActions {
        override fun onCharacter(value: String) { typed += value }
        override fun onBackspace(word: Boolean) = Unit
        override fun onSpace() { typed += " " }
        override fun onEnter() = Unit; override fun onCandidate(value: String) = Unit
        override fun onGlobe() = Unit; override fun onModeRequested(mode: InputMode) = Unit
        override fun onHide() = Unit; override fun onCursorDelta(delta: Int) = Unit
    }

    private fun keyboard(): Pair<KeyboardView, KeyboardPanel> {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences(KeyboardPreferences.FILE, 0).edit().clear().commit()
        val view = KeyboardView(context, actions, KeyboardPreferences(context))
        view.configure(InputMode.PHONETIC, false, "↵", english = true)
        view.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, 1080, 900)
        shadowOf(android.os.Looper.getMainLooper()).idle()
        return view to findPanel(view)!!
    }

    private fun findPanel(view: View): KeyboardPanel? {
        if (view is KeyboardPanel) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findPanel(view.getChildAt(i))?.let { return it }
        return null
    }

    /** Fingers currently down: pointer id to the key it touches. */
    private val down = linkedMapOf<Int, String>()
    private var time = 0L

    private fun KeyboardPanel.send(action: Int, actionPointer: Int) {
        val layout = this.layout!!
        val ids = down.keys.toList()
        val properties = ids.map { id -> MotionEvent.PointerProperties().apply { this.id = id; toolType = MotionEvent.TOOL_TYPE_FINGER } }
        val coords = ids.map { id ->
            val key = layout.keyById(down.getValue(id))!!.visual
            MotionEvent.PointerCoords().apply { x = key.centerX; y = key.centerY; pressure = 1f; size = 1f }
        }
        val masked = if (action == MotionEvent.ACTION_POINTER_DOWN || action == MotionEvent.ACTION_POINTER_UP)
            action or (ids.indexOf(actionPointer) shl MotionEvent.ACTION_POINTER_INDEX_SHIFT) else action
        time += 30
        val event = MotionEvent.obtain(0, time, masked, ids.size, properties.toTypedArray(), coords.toTypedArray(), 0, 0, 1f, 1f, 0, 0, 0, 0)
        dispatchTouchEvent(event)
        event.recycle()
    }

    private fun KeyboardPanel.fingerDown(id: Int, key: String) {
        down[id] = key
        send(if (down.size == 1) MotionEvent.ACTION_DOWN else MotionEvent.ACTION_POINTER_DOWN, id)
    }

    private fun KeyboardPanel.fingerUp(id: Int) {
        send(if (down.size == 1) MotionEvent.ACTION_UP else MotionEvent.ACTION_POINTER_UP, id)
        down.remove(id)
        if (down.isNotEmpty()) send(MotionEvent.ACTION_MOVE, -1)
    }

    @Test fun rolledKeysAreTypedInOrder() {
        val (_, panel) = keyboard()
        panel.fingerDown(0, "d")
        panel.fingerDown(1, "k")   // k lands before d lifts
        panel.fingerUp(0)
        panel.fingerUp(1)
        assertEquals(listOf("d", "k"), typed)
    }

    @Test fun theSecondFingerCanLiftFirst() {
        val (_, panel) = keyboard()
        panel.fingerDown(0, "a")
        panel.fingerDown(1, "s")
        panel.fingerUp(1)          // s lifts while a is still down
        panel.fingerUp(0)
        assertEquals(listOf("a", "s"), typed)
    }

    @Test fun threeOverlappingFingersAndPlainTaps() {
        val (_, panel) = keyboard()
        panel.fingerDown(0, "t"); panel.fingerUp(0)
        panel.fingerDown(0, "h")
        panel.fingerDown(1, "e")
        panel.fingerDown(2, "n")
        panel.fingerUp(0); panel.fingerUp(1); panel.fingerUp(2)
        panel.fingerDown(0, "space"); panel.fingerUp(0)
        assertEquals(listOf("t", "h", "e", "n", " "), typed)
    }
}
