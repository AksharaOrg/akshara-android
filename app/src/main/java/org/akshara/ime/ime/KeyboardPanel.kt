package org.akshara.ime.ime

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import org.akshara.ime.settings.KeyboardPreferences

@SuppressLint("ViewConstructor")
internal class KeyboardPanel(
    context: Context,
    private val prefs: KeyboardPreferences,
    private val popups: KeyPopups,
    private val actions: KeyboardActions,
    private val colors: KeyboardColors,
    private val onLayer: (KeyboardLayer) -> Unit,
    private val onShift: () -> Unit
) : ViewGroup(context), TouchController.Listener {
    var layout: KeyboardLayout? = null
        private set
    var learningEnabled = true
        set(value) { field = value; controller.learningEnabled = value }
    var debug = false
        set(value) {
            field = value
            controller.debug = value
            if (!value) debugFrame = null
            invalidate()
        }
    var playSpaceIntro = false
    private var pendingSpaceIntro: Boolean? = null

    private var rows: List<RowDef> = emptyList()
    private var rowHeight = 0f
    private val caps = ArrayList<KeyCap>(40)
    private val personalization = TouchPersonalizationStore(context)
    private val scheduler = HandlerScheduler(Handler(Looper.getMainLooper()))
    private val controller = TouchController(
        decoder = SpatialTouchDecoder(),
        personalization = personalization,
        scheduler = scheduler,
        listener = this,
        learningEnabled = true,
        debug = false
    )
    private var debugFrame: TouchController.DebugFrame? = null
    private var downElapsed = 0L
    private var pressShownElapsed = 0L
    private val debugPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pathPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f; color = Color.MAGENTA }
    private var pickerKey: KeyCap? = null

    init {
        setWillNotDraw(false)
        clipChildren = false
        clipToPadding = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    fun bind(rows: List<RowDef>, rowHeight: Float) {
        this.rows = rows
        this.rowHeight = rowHeight
        pendingSpaceIntro = playSpaceIntro
        playSpaceIntro = false
        controller.decoder = if (prefs.spatialDecoder) SpatialTouchDecoder() else RectangularTouchDecoder()
        controller.setDensity(resources.displayMetrics.density)
        val needed = rows.sumOf { it.keys.size }
        while (caps.size < needed) {
            val cap = KeyCap(context)
            cap.colors = colors
            caps += cap
            addView(cap)
        }
        for (i in caps.indices) caps[i].visibility = if (i < needed) VISIBLE else GONE
        var index = 0
        rows.forEach { row ->
            row.keys.forEach { def ->
                val cap = caps[index++]
                cap.colors = colors
                cap.spec = KeySpec(
                    def.id, def.label, def.output, def.action,
                    Bounds(0f, 0f, 0f, 0f), Bounds(0f, 0f, 0f, 0f), 0,
                    def.hint, def.extras, def.flickOutput, def.icon, def.utility, def.payload
                )
                cap.setOnClickListener { cap.spec?.let(::activate) }
            }
        }
        requestLayout()
        invalidate()
    }

    fun keyAt(x: Float, y: Float): KeySpec? = layout?.let { controller.decoder.decode(x, y, it, centers()).selected }

    fun resetPersonalization() = personalization.reset()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = (rows.size * rowHeight).toInt().coerceAtLeast(0)
        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val width = (r - l).toFloat()
        if (width <= 0f || rows.isEmpty()) return
        val density = resources.displayMetrics.density
        val placed = KeyboardLayoutFactory.place(
            rows, width, rowHeight,
            KeyboardGeometry.visualInsetH(density, prefs.keySpacing),
            KeyboardGeometry.visualInsetV(density, prefs.keySpacing),
            KeyboardGeometry.SLIVER_DP * density
        )
        layout = placed
        controller.layout = placed
        var index = 0
        placed.keys.forEach { spec ->
            val cap = caps[index++]
            cap.colors = colors
            cap.spec = spec
            cap.setOnClickListener { cap.spec?.let(::activate) }
            cap.layout(spec.visual.left.toInt(), spec.visual.top.toInt(), spec.visual.right.toInt(), spec.visual.bottom.toInt())
        }
        pendingSpaceIntro?.let { intro ->
            pendingSpaceIntro = null
            caps.filter { it.visibility == VISIBLE && it.spec?.action == KeyCode.SPACE }
                .forEach { it.showSpaceCaption(intro) }
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent) = true

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downElapsed = SystemClock.elapsedRealtime()
                pressShownElapsed = 0L
                controller.language = LanguageScorer { actions.languageScoreForKey(it) }
                controller.pointerDown(event.x, event.y)
            }
            MotionEvent.ACTION_MOVE -> controller.pointerMove(event.x, event.y, event.rawX)
            MotionEvent.ACTION_UP -> controller.pointerUp()
            MotionEvent.ACTION_CANCEL -> controller.pointerCancel()
            else -> return false
        }
        return true
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (!debug) return
        val board = layout ?: return
        debugPaint.style = Paint.Style.STROKE
        debugPaint.strokeWidth = 1.5f
        board.keys.forEach { key ->
            debugPaint.color = Color.argb(140, 80, 200, 90)
            canvas.drawRect(key.visual.left, key.visual.top, key.visual.right, key.visual.bottom, debugPaint)
            debugPaint.color = Color.argb(180, 220, 60, 60)
            canvas.drawRect(key.logical.left, key.logical.top, key.logical.right, key.logical.bottom, debugPaint)
            debugPaint.style = Paint.Style.FILL
            debugPaint.color = Color.YELLOW
            canvas.drawCircle(key.geometricCenterX, key.geometricCenterY, 4f, debugPaint)
            val (lx, ly) = personalization.center(key)
            debugPaint.color = Color.CYAN
            canvas.drawCircle(lx, ly, 4f, debugPaint)
            debugPaint.style = Paint.Style.STROKE
        }
        val frame = debugFrame
        if (frame != null) {
            if (frame.path.size > 1) {
                val path = android.graphics.Path()
                path.moveTo(frame.path[0].first, frame.path[0].second)
                frame.path.drop(1).forEach { path.lineTo(it.first, it.second) }
                canvas.drawPath(path, pathPaint)
            }
            debugPaint.style = Paint.Style.FILL
            debugPaint.color = Color.GREEN
            canvas.drawCircle(frame.downX, frame.downY, 8f, debugPaint)
            debugPaint.color = Color.RED
            canvas.drawCircle(frame.lastX, frame.lastY, 8f, debugPaint)
            debugPaint.color = Color.WHITE
            debugPaint.textSize = 28f
            val lines = ArrayList<String>(6)
            lines += "sel ${frame.selected ?: "-"} ${frame.state}"
            frame.candidates.take(3).forEachIndexed { i, c ->
                lines += "${i + 1} ${c.key.id} d=${"%.0f".format(c.distance)} s=${"%.2f".format(c.spatial)}"
            }
            if (pressShownElapsed > 0L) lines += "press ${pressShownElapsed - downElapsed}ms"
            lines.forEachIndexed { i, line -> canvas.drawText(line, 12f, 32f + i * 30f, debugPaint) }
        }
    }

    override fun onPressed(key: KeySpec?) {
        caps.forEach { cap ->
            val match = key != null && cap.spec?.id == key.id
            if (cap.isPressed != match) cap.isPressed = match
        }
        if (key != null && pressShownElapsed == 0L) pressShownElapsed = SystemClock.elapsedRealtime()
        invalidate()
    }

    override fun onFlick(key: KeySpec, active: Boolean) {
        caps.firstOrNull { it.spec?.id == key.id }?.flickActive = active
    }

    override fun onPreview(key: KeySpec) {
        capFor(key)?.let { popups.showPreview(it, key.label.ifEmpty { key.output }, colors.key, colors.dark) }
    }

    override fun onHidePreview() = popups.hidePreview()

    override fun onShowPicker(key: KeySpec) {
        val cap = capFor(key) ?: return
        pickerKey = cap
        val choices = listOf(key.label.ifEmpty { key.output } to key.output) + key.extras
        popups.showPicker(cap, choices, colors.key, colors.dark)
    }

    override fun onMovePicker(rawX: Float) {
        val event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_MOVE, rawX, 0f, 0)
        popups.movePicker(event)
        event.recycle()
    }

    override fun onHidePicker(): String? = popups.hidePicker()

    override fun onCommit(output: String) = actions.onCharacter(output)
    override fun onBackspace(word: Boolean) = actions.onBackspace(word)
    override fun onSpace() = actions.onSpace()
    override fun onEnter() = actions.onEnter()
    override fun onShift() = onShift.invoke()
    override fun onLayer(layer: KeyboardLayer) = onLayer.invoke(layer)
    override fun onEmoji() = onLayer.invoke(KeyboardLayer.EMOJI)
    override fun onGlobe() = actions.onGlobe()

    override fun onHaptic() {
        actions.onPressFeedback()
        val type = if (Build.VERSION.SDK_INT >= 27) HapticFeedbackConstants.KEYBOARD_PRESS else HapticFeedbackConstants.KEYBOARD_TAP
        if (prefs.haptics) performHapticFeedback(type)
    }

    override fun onCursorDelta(delta: Int) = actions.onCursorDelta(delta)
    override fun onCursorTick() {
        if (prefs.haptics) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }
    override fun onPreviewDelete(length: Int) = actions.onPreviewDelete(length)
    override fun onCommitPreviewDelete() = actions.onCommitPreviewDelete()
    override fun onCancelPreviewDelete() = actions.onCancelPreviewDelete()
    override fun onDebug(frame: TouchController.DebugFrame) {
        debugFrame = frame
        invalidate()
    }

    override fun onDetachedFromWindow() {
        scheduler.cancelAll()
        popups.dismiss()
        super.onDetachedFromWindow()
    }

    private fun capFor(key: KeySpec) = caps.firstOrNull { it.spec?.id == key.id && it.visibility == VISIBLE }
    private fun centers(): (KeySpec) -> Pair<Float, Float> = { personalization.center(it) }

    private fun activate(spec: KeySpec) {
        when (spec.action) {
            KeyCode.CHAR -> actions.onCharacter(spec.output)
            KeyCode.SHIFT -> onShift()
            KeyCode.DELETE -> actions.onBackspace(false)
            KeyCode.SPACE -> actions.onSpace()
            KeyCode.ENTER -> actions.onEnter()
            KeyCode.LAYER -> spec.payload.takeIf { it.isNotEmpty() }?.let { onLayer(KeyboardLayer.valueOf(it)) }
            KeyCode.EMOJI -> onLayer(KeyboardLayer.EMOJI)
            KeyCode.GLOBE -> actions.onGlobe()
        }
    }
}
