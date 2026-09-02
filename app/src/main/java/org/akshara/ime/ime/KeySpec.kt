package org.akshara.ime.ime

internal data class Bounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) * 0.5f
    val centerY: Float get() = (top + bottom) * 0.5f
    fun contains(x: Float, y: Float) = x >= left && x < right && y >= top && y < bottom
    fun inset(dx: Float, dy: Float) = Bounds(left + dx, top + dy, right - dx, bottom - dy)
}

internal enum class KeyCode { CHAR, SHIFT, DELETE, SPACE, ENTER, LAYER, EMOJI, GLOBE }

internal data class KeyDef(
    val id: String,
    val label: String,
    val output: String,
    val action: KeyCode = KeyCode.CHAR,
    val widthFraction: Float = KeyboardGeometry.LETTER,
    val hint: String? = null,
    val extras: List<Pair<String, String>> = emptyList(),
    val flickOutput: String? = null,
    val icon: Int? = null,
    val utility: Boolean = false,
    val payload: String = ""
)

internal data class RowDef(
    val keys: List<KeyDef>,
    val startFraction: Float = 0f,
    val expandEdges: Boolean = true,
    val sliverTop: Boolean = false
)

internal data class KeySpec(
    val id: String,
    val label: String,
    val output: String,
    val action: KeyCode,
    val logical: Bounds,
    val visual: Bounds,
    val row: Int,
    val hint: String? = null,
    val extras: List<Pair<String, String>> = emptyList(),
    val flickOutput: String? = null,
    val icon: Int? = null,
    val utility: Boolean = false,
    val payload: String = ""
) {
    val geometricCenterX: Float get() = visual.centerX
    val geometricCenterY: Float get() = visual.centerY
}

internal data class ScoredCandidate(
    val key: KeySpec,
    val spatial: Float,
    val language: Float,
    val finalScore: Float,
    val distance: Float
)

internal data class DecodeResult(
    val selected: KeySpec?,
    val candidates: List<ScoredCandidate>,
    val containing: KeySpec?,
    val clearCenter: Boolean
)
