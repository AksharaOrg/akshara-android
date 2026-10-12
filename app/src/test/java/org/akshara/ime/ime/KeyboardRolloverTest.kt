package org.akshara.ime.ime

import android.content.Context
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ApplicationProvider
import org.akshara.ime.engine.InputMode
import org.akshara.ime.settings.KeyboardPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class KeyboardRolloverTest {
    @Test fun reversedReleaseOrderStillTypesInPressOrder() {
        val keys = Harness()
        keys.send(MotionEvent.ACTION_DOWN, 7 to "q")
        keys.send(pointerDown(1), 7 to "q", 9 to "w")
        keys.send(pointerUp(1), 7 to "q", 9 to "w")
        keys.send(MotionEvent.ACTION_UP, 7 to "q")
        assertEquals("qw", keys.text.toString())
    }

    @Test fun threeOverlappingLettersCommitOnceInOrder() {
        val keys = Harness()
        keys.send(MotionEvent.ACTION_DOWN, 7 to "q")
        keys.send(pointerDown(1), 7 to "q", 9 to "w")
        keys.send(pointerDown(2), 7 to "q", 9 to "w", 11 to "e")
        keys.send(pointerUp(1), 7 to "q", 9 to "w", 11 to "e")
        keys.send(pointerUp(0), 7 to "q", 11 to "e")
        keys.send(MotionEvent.ACTION_UP, 11 to "e")
        assertEquals("qwe", keys.text.toString())
    }

    @Test fun sameKeyContactDoesNotDuplicateTheOriginalPress() {
        val keys = Harness()
        keys.send(MotionEvent.ACTION_DOWN, 7 to "q")
        keys.send(pointerDown(1), 7 to "q", 9 to "q")
        keys.send(pointerUp(0), 7 to "q", 9 to "q")
        keys.send(MotionEvent.ACTION_UP, 9 to "q")
        assertEquals("q", keys.text.toString())
    }

    @Test fun heldShiftAppliesBeforeTheNextFingerSelectsALetter() {
        val keys = Harness()
        keys.send(MotionEvent.ACTION_DOWN, 7 to KeyRow.SHIFT)
        keys.send(pointerDown(1), 7 to KeyRow.SHIFT, 9 to "q")
        keys.send(pointerUp(1), 7 to KeyRow.SHIFT, 9 to "q")
        keys.send(MotionEvent.ACTION_UP, 7 to KeyRow.SHIFT)
        keys.send(MotionEvent.ACTION_DOWN, 11 to "w")
        keys.send(MotionEvent.ACTION_UP, 11 to "w")
        assertEquals("Qw", keys.text.toString())
    }

    @Test fun anExtraFingerDoesNotInterruptCursorDragging() {
        val keys = Harness()
        keys.send(MotionEvent.ACTION_DOWN, 7 to "space")
        keys.spaceDrag = true
        keys.send(MotionEvent.ACTION_MOVE, 7 to "space")
        keys.send(pointerDown(1), 7 to "space", 9 to "q")
        keys.send(pointerUp(1), 7 to "space", 9 to "q")
        keys.send(MotionEvent.ACTION_UP, 7 to "space")
        assertTrue(keys.text.isEmpty())
        assertTrue(keys.cursor != 0)
    }

    @Test fun cancelledFirstPressDoesNotLoseTheNextKey() {
        val keys = Harness()
        keys.send(MotionEvent.ACTION_DOWN, 7 to "q")
        keys.outside = true
        keys.send(MotionEvent.ACTION_MOVE, 7 to "q")
        keys.send(pointerDown(1), 7 to "q", 9 to "w")
        keys.send(pointerUp(0), 7 to "q", 9 to "w")
        keys.send(MotionEvent.ACTION_UP, 9 to "w")
        assertEquals("w", keys.text.toString())
    }

    @Test fun suggestionSliverForwardsTheEntireOverlappingGesture() {
        val keys = Harness(throughView = true)
        keys.sliver = true
        keys.send(MotionEvent.ACTION_DOWN, 7 to "q")
        keys.send(pointerDown(1), 7 to "q", 9 to "w")
        keys.send(pointerUp(0), 7 to "q", 9 to "w")
        keys.send(MotionEvent.ACTION_UP, 9 to "w")
        assertEquals("qw", keys.text.toString())
    }

    private fun pointerDown(index: Int) = MotionEvent.ACTION_POINTER_DOWN or (index shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
    private fun pointerUp(index: Int) = MotionEvent.ACTION_POINTER_UP or (index shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)

    private class Harness(val throughView: Boolean = false) {
        val text = StringBuilder()
        var cursor = 0
        var spaceDrag = false
        var outside = false
        var sliver = false
        private val context = ApplicationProvider.getApplicationContext<Context>()
        private val prefs = KeyboardPreferences(context).apply { reset() }
        private val view = KeyboardView(context, object : KeyboardActions {
            override fun onCharacter(value: String) { text.append(value) }
            override fun onBackspace(word: Boolean) = Unit
            override fun onSpace() { text.append(' ') }
            override fun onEnter() = Unit
            override fun onCandidate(value: String) = Unit
            override fun onGlobe() = Unit
            override fun onModeRequested(mode: InputMode) = Unit
            override fun onHide() = Unit
            override fun onCursorDelta(delta: Int) { cursor += delta }
        }, prefs).apply {
            configure(InputMode.PHONETIC, false, "Done")
            measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY))
            layout(0, 0, 1080, 900)
        }
        private val panel = findPanel(view)!!
        private var time = 0L

        fun send(action: Int, vararg pointers: Pair<Int, String>) {
            val properties = pointers.map { (id, _) -> MotionEvent.PointerProperties().apply {
                this.id = id; toolType = MotionEvent.TOOL_TYPE_FINGER
            } }.toTypedArray()
            var originX = 0f
            var originY = 0f
            if (throughView) {
                var current: View = panel
                while (current !== view) {
                    originX += current.left; originY += current.top
                    current = current.parent as View
                }
            }
            val coords = pointers.map { (id, keyId) ->
                val key = panel.layout!!.keyById(keyId)!!
                MotionEvent.PointerCoords().apply {
                    x = key.geometricCenterX + if (spaceDrag && id == 7) 100f else 0f
                    y = when {
                        outside && id == 7 -> -100f
                        sliver && id == 7 -> -1f
                        else -> key.geometricCenterY
                    }
                    x += originX; y += originY
                    pressure = 1f; size = 0.1f
                }
            }.toTypedArray()
            time += 20
            val event = MotionEvent.obtain(0, time, action, pointers.size, properties, coords,
                0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
            if (throughView) view.dispatchTouchEvent(event) else panel.onTouchEvent(event)
            event.recycle()
        }

        private fun findPanel(view: View): KeyboardPanel? {
            if (view is KeyboardPanel) return view
            if (view is ViewGroup) for (i in 0 until view.childCount) findPanel(view.getChildAt(i))?.let { return it }
            return null
        }
    }
}
