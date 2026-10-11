package org.akshara.ime.ime

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.graphics.Rect
import android.os.Debug
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.akshara.ime.engine.InputMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises genuine overlapping pointer events in the installed IME, over ADB. */
@RunWith(AndroidJUnit4::class)
class RapidTypingDeviceTest {
    @Test fun overlappingLettersKeepPressOrderInBothReleaseOrders() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.uiAutomation
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        }
        val intent = Intent(instrumentation.targetContext, ImeTestActivity::class.java)
            .putExtra("input", "email").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ActivityScenario.launch<ImeTestActivity>(intent).use { scenario ->
            // Instrumentation restarts this package, which can make Android choose its fallback IME.
            automation.executeShellCommand("ime set lk.org.akshara.keyboard/org.akshara.ime.ime.AksharaInputMethodService").use {
                android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()
            }
            scenario.onActivity { activity ->
                val editor = activity.findViewById<EditText>(ImeTestActivity.EDITOR_ID)
                editor.requestFocus()
                (activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                    .showSoftInput(editor, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            }
            val deadline = SystemClock.uptimeMillis() + 10000
            var q: Rect? = null
            var w: Rect? = null
            var stable = 0
            val screen = if (android.os.Build.VERSION.SDK_INT >= 30) {
                instrumentation.targetContext.getSystemService(android.view.WindowManager::class.java).maximumWindowMetrics.bounds
            } else Rect(0, 0, instrumentation.targetContext.resources.displayMetrics.widthPixels,
                instrumentation.targetContext.resources.displayMetrics.heightPixels)
            while (SystemClock.uptimeMillis() < deadline && stable < 2) {
                val previousQ = q
                val previousW = w
                q = null
                w = null
                for (window in automation.windows) {
                    if (window.type != AccessibilityWindowInfo.TYPE_INPUT_METHOD) continue
                    q = keyBounds(window.root, "q")?.takeIf { !it.isEmpty && screen.contains(it) }
                    w = keyBounds(window.root, "w")?.takeIf { !it.isEmpty && screen.contains(it) }
                }
                stable = if (q != null && w != null && q == previousQ && w == previousW) stable + 1 else 0
                SystemClock.sleep(100)
            }
            assertTrue("Akshara's q and w keys must be visible and stable", stable == 2 && q != null && w != null)
            InstrumentationRegistry.getInstrumentation().sendStatus(0, android.os.Bundle().apply {
                putString("stream", "\nKey bounds: q=$q w=$w\n")
            })
            val points = mapOf(7 to q!!, 9 to w!!)
            repeat(20) { cycle ->
                val downTime = SystemClock.uptimeMillis()
                fun send(action: Int, ids: List<Int>) {
                    val properties = ids.map { id -> MotionEvent.PointerProperties().apply {
                        this.id = id; toolType = MotionEvent.TOOL_TYPE_FINGER
                    } }.toTypedArray()
                    val coords = ids.map { id -> MotionEvent.PointerCoords().apply {
                        x = points.getValue(id).exactCenterX(); y = points.getValue(id).exactCenterY()
                        pressure = 1f; size = 0.1f
                    } }.toTypedArray()
                    val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action,
                        ids.size, properties, coords, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
                    assertTrue(automation.injectInputEvent(event, true))
                    event.recycle()
                    SystemClock.sleep(12)
                }
                send(MotionEvent.ACTION_DOWN, listOf(7))
                send(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), listOf(7, 9))
                if (cycle % 2 == 0) {
                    send(MotionEvent.ACTION_POINTER_UP, listOf(7, 9))
                    send(MotionEvent.ACTION_UP, listOf(9))
                } else {
                    send(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), listOf(7, 9))
                    send(MotionEvent.ACTION_UP, listOf(7))
                }
            }
            // InputConnection edits cross a Binder boundary even when the editor belongs to this APK.
            val expected = "qw".repeat(20)
            var actual = ""
            val end = SystemClock.uptimeMillis() + 5000
            do {
                scenario.onActivity { actual = it.findViewById<EditText>(ImeTestActivity.EDITOR_ID).text.toString() }
                if (actual != expected) SystemClock.sleep(50)
            } while (actual != expected && SystemClock.uptimeMillis() < end)
            assertEquals(expected, actual)
        }
    }

    @Test fun reportBorderDecodeCost() {
        val rows = KeyboardLayoutFactory.typingRows(InputMode.PHONETIC, KeyboardLayer.LETTERS,
            false, false, EditorLayout.TEXT, "none", false, "Done", "Phonetic")
        val board = KeyboardLayoutFactory.place(rows, 1080f, 140f)
        val decoder = SpatialTouchDecoder()
        val centers: (KeySpec) -> Pair<Float, Float> = { it.geometricCenterX to it.geometricCenterY }
        val language = LanguageScorer { 1f }
        val q = board.keyById("q")!!
        val x = q.logical.right - 1f
        val y = q.geometricCenterY
        repeat(10000) { decoder.decode(x, y, board, centers, language) }
        val samples = (0..4).map {
            val before = Debug.getRuntimeStat("art.gc.bytes-allocated").toLong()
            val start = SystemClock.elapsedRealtimeNanos()
            repeat(20000) { decoder.decode(x, y, board, centers, language) }
            val nanos = (SystemClock.elapsedRealtimeNanos() - start) / 20000
            val bytes = (Debug.getRuntimeStat("art.gc.bytes-allocated").toLong() - before) / 20000
            nanos to bytes
        }.sortedBy { it.first }
        val median = samples[2]
        android.util.Log.i("AksharaBenchmark", "border decode median: ${median.first} ns, ${median.second} bytes/op")
        InstrumentationRegistry.getInstrumentation().sendStatus(0, android.os.Bundle().apply {
            putString("stream", "\nborder decode median: ${median.first} ns, ${median.second} bytes/op\n")
        })
    }

    private fun keyBounds(node: AccessibilityNodeInfo?, label: String): Rect? {
        node ?: return null
        if (node.contentDescription?.toString() == label) return Rect().also(node::getBoundsInScreen)
        for (index in 0 until node.childCount) keyBounds(node.getChild(index), label)?.let { return it }
        return null
    }
}
