package org.akshara.ime.ime

import kotlin.math.hypot

internal class HysteresisSelector(private val fraction: Float = KeyboardGeometry.HYSTERESIS) {
    var current: KeySpec? = null
        private set

    fun reset() { current = null }

    fun select(touchX: Float, touchY: Float, decoded: KeySpec?, letterWidth: Float, centers: (KeySpec) -> Pair<Float, Float>): KeySpec? {
        val held = current
        if (held == null) {
            current = decoded
            return current
        }
        if (decoded == null || decoded.id == held.id) return held
        val hysteresisPx = fraction * letterWidth
        val (hx, hy) = centers(held)
        val (nx, ny) = centers(decoded)
        val keep = hypot(touchX - hx, touchY - hy) < hypot(touchX - nx, touchY - ny) + hysteresisPx
        if (!keep) current = decoded
        return current
    }
}
