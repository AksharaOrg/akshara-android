package org.akshara.ime.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TouchControllerTest {
    @Test fun characterKeysCommitOnUpNotDown() {
        val recorder = Recorder()
        val controller = controller(recorder)
        controller.pointerDown(25f, 25f)
        assertEquals(0, recorder.commits.size)
        assertEquals(PointerState.PRESSED, controller.state)
        controller.pointerUp()
        assertEquals(listOf("q"), recorder.commits)
        assertEquals(PointerState.IDLE, controller.state)
    }

    @Test fun longPressOpensPickerAndSlidingCommitsAlternate() {
        val recorder = Recorder()
        val scheduler = FakeScheduler()
        val controller = controller(recorder, scheduler)
        controller.pointerDown(25f, 25f)
        scheduler.advance(KeyboardGeometry.LONG_PRESS_MS)
        assertEquals(PointerState.LONG_PRESS, controller.state)
        assertTrue(recorder.picker)
        recorder.picked = "à"
        controller.pointerUp()
        assertEquals(listOf("à"), recorder.commits)
    }

    @Test fun downwardFlickCommitsSecondaryGlyph() {
        val recorder = Recorder()
        val controller = controller(recorder)
        controller.pointerDown(25f, 10f)
        controller.pointerMove(25f, 40f, 25f)
        assertEquals(PointerState.FLICK, controller.state)
        controller.pointerUp()
        assertEquals(listOf("à"), recorder.commits)
    }

    @Test fun spaceDragDoesNotInsertSpaces() {
        val recorder = Recorder()
        val space = KeySpec("space", " ", " ", KeyCode.SPACE, Bounds(0f, 50f, 100f, 100f), Bounds(4f, 54f, 96f, 96f), 1)
        val q = key("q", 0f, 50f)
        val layout = KeyboardLayout(listOf(q, space), 100f, 100f, 50f, 50f, 2)
        val recorder2 = Recorder()
        val controller = controller(recorder2)
        controller.layout = layout
        controller.pointerDown(50f, 75f)
        controller.pointerMove(90f, 75f, 90f)
        assertEquals(PointerState.SPACE_DRAG, controller.state)
        controller.pointerUp()
        assertTrue(recorder2.commits.isEmpty())
        assertTrue(recorder2.cursor != 0)
    }

    @Test fun spaceSwipeUpDoesNotInsertASpace() {
        val recorder = Recorder()
        val space = KeySpec("space", " ", " ", KeyCode.SPACE, Bounds(0f, 50f, 100f, 100f), Bounds(4f, 54f, 96f, 96f), 1)
        val controller = controller(recorder)
        controller.layout = KeyboardLayout(listOf(space), 100f, 100f, 50f, 50f, 1)
        controller.pointerDown(50f, 75f)
        controller.pointerMove(50f, 40f, 50f)
        assertEquals(PointerState.SPACE_SWIPE, controller.state)
        controller.pointerUp()
        assertEquals(listOf("up"), recorder.swipes)
        assertTrue(recorder.commits.isEmpty())
    }

    @Test fun slidingOffDeleteCancelsRepeatAndRelease() {
        val recorder = Recorder()
        val scheduler = FakeScheduler()
        val controller = controller(recorder, scheduler)
        val delete = key("delete", 50f, 100f).copy(action = KeyCode.DELETE, extras = emptyList(), flickOutput = null)
        controller.layout = controller.layout!!.copy(keys = listOf(key("q", 0f, 50f), delete))
        controller.pointerDown(75f, 25f)
        controller.pointerMove(75f, -30f, 75f)
        scheduler.advance(1000)
        controller.pointerUp()
        assertEquals(0, recorder.deletes)
        assertTrue(recorder.commits.isEmpty())
    }

    @Test fun slidingFromLetterOntoDeleteDoesNotDelete() {
        val recorder = Recorder()
        val controller = controller(recorder)
        val delete = key("delete", 50f, 100f).copy(action = KeyCode.DELETE, extras = emptyList(), flickOutput = null)
        controller.layout = controller.layout!!.copy(keys = listOf(key("q", 0f, 50f), delete))
        controller.pointerDown(25f, 25f)
        controller.pointerMove(90f, 25f, 90f)
        controller.pointerUp()
        assertEquals(0, recorder.deletes)
        assertTrue(recorder.commits.isEmpty())
    }

    @Test fun outsideDownCannotSlideIntoAKey() {
        val recorder = Recorder()
        val controller = controller(recorder)
        controller.pointerDown(-10f, 25f)
        controller.pointerMove(25f, 25f, 25f)
        controller.pointerUp()
        assertTrue(recorder.commits.isEmpty())
    }

    @Test fun cancelledLongPressAndDeleteDoNotFireLater() {
        val recorder = Recorder()
        val scheduler = FakeScheduler()
        val controller = controller(recorder, scheduler)
        controller.pointerDown(25f, 25f)
        controller.pointerCancel()
        scheduler.advance(1000)
        assertTrue(!recorder.picker)
        assertTrue(recorder.commits.isEmpty())
    }

    private fun key(id: String, left: Float, right: Float) = KeySpec(
        id, id, id, KeyCode.CHAR,
        Bounds(left, 0f, right, 50f),
        Bounds(left + 3f, 3f, right - 3f, 47f),
        0,
        extras = listOf("à" to "à"),
        flickOutput = "à"
    )

    private fun controller(recorder: Recorder, scheduler: FakeScheduler = FakeScheduler()): TouchController {
        val q = key("q", 0f, 50f)
        val w = key("w", 50f, 100f)
        val controller = TouchController(RectangularTouchDecoder(), scheduler = scheduler, listener = recorder)
        controller.layout = KeyboardLayout(listOf(q, w), 100f, 50f, 50f, 50f, 1)
        controller.setDensity(1f)
        return controller
    }

    private class FakeScheduler : TaskScheduler {
        var time = 0L
        private val tasks = mutableListOf<Triple<Long, String, () -> Unit>>()
        override fun now() = time
        override fun post(delayMs: Long, token: String, run: () -> Unit) {
            cancel(token)
            tasks += Triple(time + delayMs, token, run)
        }
        override fun cancel(token: String) { tasks.removeAll { it.second == token } }
        override fun cancelAll() { tasks.clear() }
        fun advance(ms: Long) {
            time += ms
            val due = tasks.filter { it.first <= time }
            tasks.removeAll { it.first <= time }
            due.forEach { it.third.invoke() }
        }
    }

    private class Recorder : TouchController.Listener {
        val commits = mutableListOf<String>()
        var picker = false
        var picked: String? = null
        var deletes = 0
        var cursor = 0
        val swipes = mutableListOf<String>()
        override fun onPressed(key: KeySpec?) = Unit
        override fun onFlick(key: KeySpec, active: Boolean) = Unit
        override fun onPreview(key: KeySpec) = Unit
        override fun onHidePreview() = Unit
        override fun onShowPicker(key: KeySpec) { picker = true }
        override fun onMovePicker(rawX: Float) = Unit
        override fun onHidePicker(): String? { picker = false; return picked }
        override fun onCommit(output: String) { commits += output }
        override fun onBackspace(word: Boolean) { deletes++ }
        override fun onSpace() { commits += " " }
        override fun onEnter() = Unit
        override fun onShift() = Unit
        override fun onLayer(layer: KeyboardLayer) = Unit
        override fun onEmoji() = Unit
        override fun onGlobe() = Unit
        override fun onLanguageSwitch() = Unit
        override fun onHaptic() = Unit
        override fun onCursorDelta(delta: Int) { cursor += delta }
        override fun onCursorTick() = Unit
        override fun onPreviewDelete(length: Int) = Unit
        override fun onCommitPreviewDelete() = Unit
        override fun onCancelPreviewDelete() = Unit
        override fun onDebug(frame: TouchController.DebugFrame) = Unit
        override fun onSpaceSwipe(up: Boolean) { swipes += if (up) "up" else "down" }
    }
}
