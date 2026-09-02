package org.akshara.ime.ime

import org.junit.Assert.assertEquals
import org.junit.Test

class TouchHysteresisTest {
    private val q = key("q", 0f, 50f)
    private val w = key("w", 50f, 100f)
    private val centers: (KeySpec) -> Pair<Float, Float> = { it.geometricCenterX to it.geometricCenterY }

    @Test fun smallMovesDoNotChangeTheHeldKey() {
        val hysteresis = HysteresisSelector(0.14f)
        hysteresis.select(25f, 25f, q, 50f, centers)
        assertEquals("q", hysteresis.select(51f, 25f, w, 50f, centers)?.id)
        assertEquals("w", hysteresis.select(80f, 25f, w, 50f, centers)?.id)
    }

    private fun key(id: String, left: Float, right: Float) = KeySpec(
        id, id, id, KeyCode.CHAR,
        Bounds(left, 0f, right, 50f),
        Bounds(left + 3f, 3f, right - 3f, 47f),
        0
    )
}
