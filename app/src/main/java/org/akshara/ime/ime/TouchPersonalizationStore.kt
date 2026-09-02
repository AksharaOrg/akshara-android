package org.akshara.ime.ime

import android.content.Context
import kotlin.math.abs

internal class TouchPersonalizationStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val cache = HashMap<String, Offset>()
    private var lastKey: String? = null
    private var lastObservedX = 0f
    private var lastObservedY = 0f

    data class Offset(val x: Float, val y: Float, val samples: Int)

    fun offset(keyId: String): Offset {
        cache[keyId]?.let { return it }
        val raw = prefs.getString(keyId, null) ?: return Offset(0f, 0f, 0).also { cache[keyId] = it }
        val parts = raw.split(',')
        val value = Offset(
            parts.getOrNull(0)?.toFloatOrNull() ?: 0f,
            parts.getOrNull(1)?.toFloatOrNull() ?: 0f,
            parts.getOrNull(2)?.toIntOrNull() ?: 0
        )
        cache[keyId] = value
        return value
    }

    fun center(key: KeySpec): Pair<Float, Float> {
        val off = offset(key.id)
        return key.geometricCenterX + off.x to key.geometricCenterY + off.y
    }

    fun learn(key: KeySpec, touchX: Float, touchY: Float, highConfidence: Boolean) {
        if (!highConfidence || key.action != KeyCode.CHAR) return
        val observedX = touchX - key.geometricCenterX
        val observedY = touchY - key.geometricCenterY
        val clampX = key.logical.width * KeyboardGeometry.PERSONALIZATION_CLAMP
        val clampY = key.logical.height * KeyboardGeometry.PERSONALIZATION_CLAMP
        if (abs(observedX) > clampX * 2f || abs(observedY) > clampY * 2f) return
        val old = offset(key.id)
        val next = Offset(
            ((old.x * KeyboardGeometry.EWMA_OLD) + (observedX * KeyboardGeometry.EWMA_NEW)).coerceIn(-clampX, clampX),
            ((old.y * KeyboardGeometry.EWMA_OLD) + (observedY * KeyboardGeometry.EWMA_NEW)).coerceIn(-clampY, clampY),
            old.samples + 1
        )
        write(key.id, next)
        lastKey = key.id
        lastObservedX = observedX
        lastObservedY = observedY
    }

    fun punishLast() {
        val id = lastKey ?: return
        val keyOffset = offset(id)
        val clampX = 1000f
        val next = Offset(
            (keyOffset.x * KeyboardGeometry.EWMA_OLD) - (lastObservedX * KeyboardGeometry.EWMA_NEW),
            (keyOffset.y * KeyboardGeometry.EWMA_OLD) - (lastObservedY * KeyboardGeometry.EWMA_NEW),
            keyOffset.samples
        )
        write(id, Offset(next.x.coerceIn(-clampX, clampX), next.y.coerceIn(-clampX, clampX), next.samples))
        lastKey = null
    }

    fun reset() {
        cache.clear()
        lastKey = null
        prefs.edit().clear().apply()
    }

    private fun write(id: String, offset: Offset) {
        cache[id] = offset
        prefs.edit().putString(id, "${offset.x},${offset.y},${offset.samples}").apply()
    }

    companion object {
        const val FILE = "akshara_touch_centers"
    }
}
