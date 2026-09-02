package org.akshara.ime.ime

import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln

internal fun interface LanguageScorer {
    fun score(output: String): Float
}

internal interface TouchDecoder {
    fun decode(
        x: Float,
        y: Float,
        layout: KeyboardLayout,
        centers: (KeySpec) -> Pair<Float, Float>,
        language: LanguageScorer? = null
    ): DecodeResult
}

internal class RectangularTouchDecoder : TouchDecoder {
    override fun decode(
        x: Float,
        y: Float,
        layout: KeyboardLayout,
        centers: (KeySpec) -> Pair<Float, Float>,
        language: LanguageScorer?
    ): DecodeResult {
        val containing = layout.keys.filter { it.logical.contains(x, y) }
        val selected = when {
            containing.size == 1 -> containing[0]
            containing.size > 1 -> containing.minBy { hypot(centers(it).first - x, centers(it).second - y) }
            else -> {
                val radius = layout.letterWidth * KeyboardGeometry.SEARCH_KEYS
                layout.keys
                    .map { it to hypot(centers(it).first - x, centers(it).second - y) }
                    .filter { it.second <= radius }
                    .minByOrNull { it.second }
                    ?.first
            }
        }
        val candidates = nearby(x, y, layout, centers).take(3)
        return DecodeResult(selected, candidates, containing.singleOrNull(), containing.size == 1)
    }
}

internal class SpatialTouchDecoder(
    private val spatialWeight: Float = KeyboardGeometry.SPATIAL_WEIGHT,
    private val languageWeight: Float = KeyboardGeometry.LANGUAGE_WEIGHT
) : TouchDecoder {
    override fun decode(
        x: Float,
        y: Float,
        layout: KeyboardLayout,
        centers: (KeySpec) -> Pair<Float, Float>,
        language: LanguageScorer?
    ): DecodeResult {
        val containing = layout.keys.filter { it.logical.contains(x, y) }
        val inside = containing.singleOrNull()
        val sigmaX = layout.letterWidth * KeyboardGeometry.SIGMA_X
        val sigmaY = layout.rowHeight * KeyboardGeometry.SIGMA_Y
        if (inside != null && inside.action == KeyCode.SPACE) {
            val visualOther = layout.keys.firstOrNull { it.id != inside.id && it.visual.contains(x, y) }
            return locked(visualOther ?: inside, x, y, centers, sigmaX, sigmaY, inside, visualOther == null)
        }
        if (inside != null) {
            val (cx, cy) = centers(inside)
            val nx = kotlin.math.abs(x - cx) / (inside.logical.width * 0.5f).coerceAtLeast(1f)
            val ny = kotlin.math.abs(y - cy) / (inside.logical.height * 0.5f).coerceAtLeast(1f)
            if (nx < KeyboardGeometry.CLEAR_CENTER && ny < KeyboardGeometry.CLEAR_CENTER) {
                return locked(inside, x, y, centers, sigmaX, sigmaY, inside, true)
            }
        }
        val scored = nearby(x, y, layout, centers, sigmaX, sigmaY).toMutableList()
        if (scored.isEmpty()) {
            return DecodeResult(inside, emptyList(), inside, false)
        }
        val ambiguous = scored.size > 1 &&
            scored[0].spatial / scored[1].spatial.coerceAtLeast(1e-6f) < KeyboardGeometry.AMBIGUITY_RATIO
        if (ambiguous && language != null) {
            for (i in scored.indices) {
                val item = scored[i]
                val lang = language.score(item.key.output).coerceAtLeast(0f)
                val blended = spatialWeight * ln(item.spatial.coerceAtLeast(1e-6f)) +
                    languageWeight * ln((lang + 1f).toDouble()).toFloat()
                scored[i] = item.copy(language = lang, finalScore = blended)
            }
            scored.sortByDescending { it.finalScore }
        }
        return DecodeResult(scored.first().key, scored.take(3), inside, false)
    }
}

private fun locked(
    key: KeySpec,
    x: Float,
    y: Float,
    centers: (KeySpec) -> Pair<Float, Float>,
    sigmaX: Float,
    sigmaY: Float,
    containing: KeySpec?,
    clearCenter: Boolean
): DecodeResult {
    val (cx, cy) = centers(key)
    val spatial = gaussian(x, y, cx, cy, sigmaX, sigmaY)
    return DecodeResult(
        key,
        listOf(ScoredCandidate(key, spatial, 0f, spatial, hypot(x - cx, y - cy))),
        containing,
        clearCenter
    )
}

private fun nearby(
    x: Float,
    y: Float,
    layout: KeyboardLayout,
    centers: (KeySpec) -> Pair<Float, Float>,
    sigmaX: Float = layout.letterWidth * KeyboardGeometry.SIGMA_X,
    sigmaY: Float = layout.rowHeight * KeyboardGeometry.SIGMA_Y
): List<ScoredCandidate> {
    val radius = layout.letterWidth * KeyboardGeometry.SEARCH_KEYS
    return layout.keys.map { key ->
        val (cx, cy) = centers(key)
        val distance = hypot(x - cx, y - cy)
        ScoredCandidate(key, gaussian(x, y, cx, cy, sigmaX, sigmaY), 0f, 0f, distance)
    }
        .filter { it.distance <= radius || it.key.logical.contains(x, y) }
        .sortedByDescending { it.spatial }
        .map { it.copy(finalScore = it.spatial) }
}

private fun gaussian(x: Float, y: Float, cx: Float, cy: Float, sigmaX: Float, sigmaY: Float): Float {
    val dx = (x - cx) / sigmaX.coerceAtLeast(1f)
    val dy = (y - cy) / sigmaY.coerceAtLeast(1f)
    return exp(-0.5f * (dx * dx + dy * dy))
}
