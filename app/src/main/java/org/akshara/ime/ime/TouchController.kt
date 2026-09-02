package org.akshara.ime.ime

internal interface TaskScheduler {
    fun now(): Long
    fun post(delayMs: Long, token: String, run: () -> Unit)
    fun cancel(token: String)
    fun cancelAll()
}

internal class HandlerScheduler(private val handler: android.os.Handler) : TaskScheduler {
    private val runs = HashMap<String, Runnable>()
    override fun now() = android.os.SystemClock.elapsedRealtime()
    override fun post(delayMs: Long, token: String, run: () -> Unit) {
        cancel(token)
        val wrapped = Runnable {
            runs.remove(token)
            run()
        }
        runs[token] = wrapped
        handler.postDelayed(wrapped, delayMs)
    }
    override fun cancel(token: String) {
        runs.remove(token)?.let { handler.removeCallbacks(it) }
    }
    override fun cancelAll() {
        runs.values.forEach { handler.removeCallbacks(it) }
        runs.clear()
    }
}

internal enum class PointerState { IDLE, PRESSED, LONG_PRESS, FLICK, SPACE_DRAG, DELETE_SWIPE, CANCELLED }

internal class TouchController(
    var decoder: TouchDecoder,
    private val hysteresis: HysteresisSelector = HysteresisSelector(),
    private val personalization: TouchPersonalizationStore? = null,
    private val scheduler: TaskScheduler,
    private val listener: Listener,
    var learningEnabled: Boolean = true,
    var debug: Boolean = false,
    var longPressMs: Long = KeyboardGeometry.LONG_PRESS_MS,
    var language: LanguageScorer? = null
) {
    interface Listener {
        fun onPressed(key: KeySpec?)
        fun onFlick(key: KeySpec, active: Boolean)
        fun onPreview(key: KeySpec)
        fun onHidePreview()
        fun onShowPicker(key: KeySpec)
        fun onMovePicker(rawX: Float)
        fun onHidePicker(): String?
        fun onCommit(output: String)
        fun onBackspace(word: Boolean)
        fun onSpace()
        fun onEnter()
        fun onShift()
        fun onLayer(layer: KeyboardLayer)
        fun onEmoji()
        fun onGlobe()
        fun onHaptic()
        fun onCursorDelta(delta: Int)
        fun onCursorTick()
        fun onPreviewDelete(length: Int)
        fun onCommitPreviewDelete()
        fun onCancelPreviewDelete()
        fun onDebug(frame: DebugFrame)
    }

    data class DebugFrame(
        val downX: Float,
        val downY: Float,
        val lastX: Float,
        val lastY: Float,
        val path: List<Pair<Float, Float>>,
        val selected: String?,
        val candidates: List<ScoredCandidate>,
        val state: PointerState,
        val downAt: Long
    )

    var state = PointerState.IDLE
        private set
    var layout: KeyboardLayout? = null
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var downAt = 0L
    private var selected: KeySpec? = null
    private var lastDecode = DecodeResult(null, emptyList(), null, false)
    private var repeats = 0
    private var lastCursorSteps = 0
    private var swipeClusters = 0
    private var lastCharId: String? = null
    private var lastCharAt = 0L
    private val path = ArrayList<Pair<Float, Float>>(64)
    private var density = 1f

    fun setDensity(value: Float) { density = value }

    fun pointerDown(x: Float, y: Float) {
        val board = layout ?: return
        downX = x; downY = y; lastX = x; lastY = y
        downAt = scheduler.now()
        path.clear()
        record(x, y)
        repeats = 0
        lastCursorSteps = 0
        swipeClusters = 0
        hysteresis.reset()
        lastDecode = decode(x, y, board)
        selected = hysteresis.select(x, y, lastDecode.selected, board.letterWidth, centers())
        state = PointerState.PRESSED
        listener.onPressed(selected)
        listener.onHaptic()
        selected?.let { key ->
            if (key.action == KeyCode.CHAR) listener.onPreview(key)
            if (key.action == KeyCode.DELETE) {
                scheduler.post(KeyboardGeometry.DELETE_REPEAT_START_MS, REPEAT) { repeatDelete() }
            } else if (key.action == KeyCode.CHAR && key.extras.isNotEmpty()) {
                scheduler.post(longPressMs, LONG_PRESS) { openPicker() }
            }
        }
        publishDebug()
    }

    fun pointerMove(x: Float, y: Float, rawX: Float) {
        if (state == PointerState.IDLE || state == PointerState.CANCELLED) return
        val board = layout ?: return
        lastX = x; lastY = y
        record(x, y)
        val dx = x - downX
        val dy = y - downY
        when (state) {
            PointerState.LONG_PRESS -> listener.onMovePicker(rawX)
            PointerState.SPACE_DRAG -> stepCursor(dx)
            PointerState.DELETE_SWIPE -> stepDelete(dx)
            PointerState.FLICK -> Unit
            PointerState.PRESSED -> {
                val key = selected
                if (key?.action == KeyCode.SPACE && kotlin.math.abs(dx) > KeyboardGeometry.SPACE_DRAG_DP * density) {
                    scheduler.cancel(LONG_PRESS)
                    listener.onHidePreview()
                    state = PointerState.SPACE_DRAG
                    stepCursor(dx)
                } else if (key?.action == KeyCode.DELETE && dx < -KeyboardGeometry.DELETE_SWIPE_DP * density) {
                    scheduler.cancel(REPEAT)
                    state = PointerState.DELETE_SWIPE
                    stepDelete(dx)
                } else if (key?.action == KeyCode.CHAR && key.flickOutput != null &&
                    dy > board.rowHeight * KeyboardGeometry.FLICK_ROW_FRACTION &&
                    dy > kotlin.math.abs(dx) * 1.6f
                ) {
                    scheduler.cancel(LONG_PRESS)
                    state = PointerState.FLICK
                    listener.onFlick(key, true)
                } else {
                    lastDecode = decode(x, y, board)
                    val next = hysteresis.select(x, y, lastDecode.selected, board.letterWidth, centers())
                    if (next?.id != selected?.id) {
                        scheduler.cancel(LONG_PRESS)
                        selected = next
                        listener.onPressed(selected)
                        selected?.let { listener.onFlick(it, false) }
                        val current = selected
                        if (current?.action == KeyCode.CHAR) {
                            listener.onPreview(current)
                            if (current.extras.isNotEmpty()) scheduler.post(longPressMs, LONG_PRESS) { openPicker() }
                        } else listener.onHidePreview()
                    }
                }
            }
            else -> Unit
        }
        publishDebug()
    }

    fun pointerUp() {
        scheduler.cancelAll()
        val key = selected
        when (state) {
            PointerState.LONG_PRESS -> {
                val picked = listener.onHidePicker()
                if (picked != null) listener.onCommit(picked) else key?.let { commit(it) }
            }
            PointerState.FLICK -> {
                listener.onFlick(key ?: return, false)
                key.flickOutput?.let(listener::onCommit)
            }
            PointerState.SPACE_DRAG -> Unit
            PointerState.DELETE_SWIPE -> {
                if (swipeClusters > 0) listener.onCommitPreviewDelete() else listener.onCancelPreviewDelete()
            }
            PointerState.PRESSED -> {
                if (key?.action == KeyCode.DELETE) {
                    if (repeats == 0) {
                        punishIfRecent()
                        listener.onBackspace(false)
                    }
                } else {
                    key?.let { commit(it) }
                }
            }
            else -> Unit
        }
        if (key?.action == KeyCode.CHAR && state == PointerState.PRESSED && learningEnabled && lastDecode.clearCenter) {
            personalization?.learn(key, lastX, lastY, true)
            lastCharId = key.id
            lastCharAt = scheduler.now()
        }
        finish()
    }

    fun pointerCancel() {
        scheduler.cancelAll()
        if (state == PointerState.DELETE_SWIPE) listener.onCancelPreviewDelete()
        listener.onHidePicker()
        finish()
    }

    private fun commit(key: KeySpec) {
        when (key.action) {
            KeyCode.CHAR -> {
                listener.onCommit(key.output)
            }
            KeyCode.SHIFT -> listener.onShift()
            KeyCode.DELETE -> {
                punishIfRecent()
                listener.onBackspace(false)
            }
            KeyCode.SPACE -> listener.onSpace()
            KeyCode.ENTER -> listener.onEnter()
            KeyCode.LAYER -> key.payload.takeIf { it.isNotEmpty() }?.let { listener.onLayer(KeyboardLayer.valueOf(it)) }
            KeyCode.EMOJI -> listener.onEmoji()
            KeyCode.GLOBE -> listener.onGlobe()
        }
    }

    private fun punishIfRecent() {
        if (lastCharId != null && scheduler.now() - lastCharAt < 700 && learningEnabled) {
            personalization?.punishLast()
            lastCharId = null
        }
    }

    private fun openPicker() {
        val key = selected ?: return
        if (key.extras.isEmpty()) return
        state = PointerState.LONG_PRESS
        listener.onShowPicker(key)
    }

    private fun repeatDelete() {
        repeats++
        listener.onBackspace(repeats > KeyboardGeometry.DELETE_WORD_AFTER)
        val delay = if (repeats > KeyboardGeometry.DELETE_WORD_AFTER) 45L else KeyboardGeometry.DELETE_REPEAT_MS
        scheduler.post(delay, REPEAT) { repeatDelete() }
    }

    private fun stepCursor(dx: Float) {
        val steps = (dx / (KeyboardGeometry.SPACE_STEP_DP * density)).toInt()
        if (steps != lastCursorSteps) {
            listener.onCursorDelta(steps - lastCursorSteps)
            listener.onCursorTick()
            lastCursorSteps = steps
        }
    }

    private fun stepDelete(dx: Float) {
        val clusters = ((-dx) / (layout?.letterWidth ?: 1f)).toInt().coerceAtLeast(0)
        if (clusters != swipeClusters) {
            swipeClusters = clusters
            listener.onPreviewDelete(clusters)
        }
    }

    private fun decode(x: Float, y: Float, board: KeyboardLayout) =
        decoder.decode(x, y, board, centers(), if (learningEnabled) language else null)

    private fun centers(): (KeySpec) -> Pair<Float, Float> = { key ->
        personalization?.center(key) ?: (key.geometricCenterX to key.geometricCenterY)
    }

    private fun record(x: Float, y: Float) {
        if (!debug) return
        if (path.size == 64) path.removeAt(0)
        path += x to y
    }

    private fun publishDebug() {
        if (!debug) return
        listener.onDebug(DebugFrame(downX, downY, lastX, lastY, path.toList(), selected?.id, lastDecode.candidates, state, downAt))
    }

    private fun finish() {
        listener.onHidePreview()
        listener.onPressed(null)
        selected = null
        state = PointerState.IDLE
        hysteresis.reset()
    }

    companion object {
        private const val LONG_PRESS = "long-press"
        private const val REPEAT = "repeat"
    }
}
