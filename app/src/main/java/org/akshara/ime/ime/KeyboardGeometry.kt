package org.akshara.ime.ime

/** Gboard UK slots with 2dp extra chin and slightly more keycap gutters. */
internal object KeyboardGeometry {
    const val LETTER = 0.10f
    const val ROW2_OFFSET = 0.05f
    const val SHIFT = 0.15f
    const val DELETE = 0.15f
    const val SYMBOLS = 0.15f
    const val PUNCT = 0.10f
    const val ENTER = 0.15f
    const val HYSTERESIS = 0.14f
    const val SEARCH_KEYS = 1.15f
    const val SIGMA_X = 0.45f
    const val SIGMA_Y = 0.52f
    const val SPATIAL_WEIGHT = 0.65f
    const val LANGUAGE_WEIGHT = 0.35f
    const val AMBIGUITY_RATIO = 1.35f
    const val CLEAR_CENTER = 0.70f
    /** Fraction of each neighbouring cell the space bar steals as extra hit area. */
    const val SPACE_STEAL = 0.28f
    const val SHIFT_DOUBLE_MS = 400L
    const val VISUAL_INSET_H_DP = 2f
    const val VISUAL_INSET_V_DP = 5f
    const val RAIL_PORTRAIT_DP = 46
    const val RAIL_LANDSCAPE_DP = 38
    const val KEY_AREA_COMPACT_DP = 218
    const val KEY_AREA_STANDARD_DP = 234
    const val KEY_AREA_TALL_DP = 250
    const val SLIVER_DP = 4
    const val LONG_PRESS_MS = 400L
    const val LANGUAGE_SWITCH_HOLD_MS = 500L
    const val DELETE_REPEAT_START_MS = 420L
    const val DELETE_REPEAT_MS = 80L
    const val DELETE_WORD_AFTER = 20
    const val PERSONALIZATION_CLAMP = 0.18f
    const val EWMA_OLD = 0.98f
    const val EWMA_NEW = 0.02f
    const val FLICK_ROW_FRACTION = 0.45f
    const val SPACE_DRAG_DP = 12
    const val SPACE_SWIPE_DP = 28
    const val SPACE_STEP_DP = 24
    const val DELETE_SWIPE_DP = 24
    const val ICON_DP = 22f
    const val TOP_PAD_DP = 8
    const val BOTTOM_PAD_DP = 30
    const val LETTER_RADIUS_DP = 8f
    const val SPACE_INTRO_MS = 1200L
    const val SPACE_COLLAPSE_MS = 580L
    const val SPACE_INTRO_SP = 13f
    const val SPACE_COLLAPSE_SP = 11f
    const val SPACE_COLLAPSE_ALPHA = 0.64f
    const val SPACE_COLLAPSE_SCALE = 0.88f
    const val PREVIEW_HEIGHT_DP = 58
    const val PREVIEW_TEXT_SP = 32f
    const val EMOJI_TEXT_SP = 40f
    const val EMOJI_TAB_DP = 44
    const val EMOJI_MIN_CELL_DP = 48
    const val EMOJI_COLUMNS_PORTRAIT = 8
    const val EMOJI_COLUMNS_LANDSCAPE = 10
    const val EMOJI_ROWS_PORTRAIT = 5
    const val EMOJI_ROWS_LANDSCAPE = 3
    /** Hit height from the top of the IME for clipboard expand/collapse. */
    const val CLIPBOARD_HANDLE_HIT_DP = 24
    const val CLIPBOARD_HANDLE_WIDTH_DP = 36
    const val CLIPBOARD_HANDLE_HEIGHT_DP = 4
    const val CLIPBOARD_DRAG_SLOP_DP = 8
    const val CLIPBOARD_FLICK_DP_PER_SEC = 800
    const val CLIPBOARD_EXPAND_FRACTION_PORTRAIT = 0.55f
    const val CLIPBOARD_EXPAND_FRACTION_LANDSCAPE = 0.70f
    /** Always leave some editor visible when clipboard is pulled up. */
    const val CLIPBOARD_EXPAND_CAP_FRACTION = 0.85f
    /** Minimum growth when screen fraction would not enlarge a tall keyboard. */
    const val CLIPBOARD_MIN_EXTRA_DP = 120
    /** Left inset matching the rail clipboard button so that button keeps receiving taps. */
    const val CLIPBOARD_HANDLE_EXCLUDE_START_DP = 44

    fun keyAreaDp(size: String, landscape: Boolean): Int {
        if (landscape) return when (size) {
            "compact" -> 154
            "tall" -> 178
            else -> 166
        }
        return when (size) {
            "compact" -> KEY_AREA_COMPACT_DP
            "tall" -> KEY_AREA_TALL_DP
            else -> KEY_AREA_STANDARD_DP
        }
    }

    fun rowHeightPx(size: String, landscape: Boolean, density: Float, rows: Int = 4): Float {
        val area = keyAreaDp(size, landscape) * density
        return area / rows.coerceAtLeast(1)
    }

    fun railHeightPx(landscape: Boolean, density: Float): Float {
        val dp = if (landscape) RAIL_LANDSCAPE_DP else RAIL_PORTRAIT_DP
        return dp * density
    }

    fun visualInsetH(density: Float, spacing: String): Float {
        val dp = when (spacing) {
            "compact" -> 1f
            "spacious" -> 3f
            else -> VISUAL_INSET_H_DP
        }
        return dp * density
    }

    fun visualInsetV(density: Float, spacing: String): Float {
        val dp = when (spacing) {
            "compact" -> 4f
            "spacious" -> 6f
            else -> VISUAL_INSET_V_DP
        }
        return dp * density
    }
}
