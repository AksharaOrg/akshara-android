package org.akshara.ime.ime

import android.graphics.Color
import android.os.Build
import androidx.core.graphics.ColorUtils
import org.akshara.ime.R

/** The picker's sections, in Gboard's order. */
internal enum class ThemeSection(val title: Int) {
    DEFAULT(R.string.theme_section_default),
    COLORS(R.string.theme_section_colors),
    LIGHT_GRADIENTS(R.string.theme_section_light_gradients),
    DARK_GRADIENTS(R.string.theme_section_dark_gradients)
}

/**
 * A built-in theme. Default themes follow the system (see [KeyboardThemes.resolve]); the others are fixed
 * and described by their design colors. [gradient] runs top to bottom, so its last stop meets the navigation bar.
 */
internal data class ThemeSpec(
    val id: String,
    val section: ThemeSection,
    /** A string resource, or 0 with [number] for the numbered gradients. */
    val name: Int,
    val number: Int = 0,
    val gradient: List<Int> = emptyList(),
    val key: Int = 0,
    val function: Int = 0,
    val ink: Int = 0,
    val dark: Boolean = false
) {
    fun theme(highContrast: Boolean, keyBorders: Boolean) = KeyboardTheme.from(
        id = id,
        background = gradient.last(),
        key = key,
        function = function,
        ink = ink,
        dark = dark,
        highContrast = highContrast,
        keyBorders = keyBorders,
        gradient = gradient
    )
}

/** Every built-in theme. All of them are drawn in code, so nothing is downloaded or bundled as images. */
internal object ThemeCatalog {
    const val DYNAMIC = "dynamic"
    const val SYSTEM = "system"
    const val LIGHT = "light"
    const val DARK = "dark"

    /** Dynamic color needs the wallpaper palette (Android 12+); older devices start on System auto. */
    val defaultId: String get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) DYNAMIC else SYSTEM

    val all: List<ThemeSpec> by lazy { defaults + colors + lightGradients + darkGradients }

    fun find(id: String): ThemeSpec? = all.firstOrNull { it.id == id }

    /** The sections and themes this device can show. */
    fun available(): List<Pair<ThemeSection, List<ThemeSpec>>> = ThemeSection.values().map { section ->
        section to all.filter { it.section == section && (it.id != DYNAMIC || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) }
    }

    private val defaults = listOf(
        ThemeSpec(DYNAMIC, ThemeSection.DEFAULT, R.string.theme_dynamic),
        ThemeSpec(SYSTEM, ThemeSection.DEFAULT, R.string.theme_system),
        ThemeSpec(LIGHT, ThemeSection.DEFAULT, R.string.theme_light),
        ThemeSpec(DARK, ThemeSection.DEFAULT, R.string.theme_dark)
    )

    private fun solid(id: String, name: Int, bg: Int, key: Int, function: Int, ink: Int, dark: Boolean) =
        ThemeSpec("color_$id", ThemeSection.COLORS, name, gradient = listOf(readable(bg, ink)),
            key = readable(key, ink), function = readable(function, ink), ink = ink, dark = dark)

    /**
     * Nudges [color]'s lightness away from [ink] until labels drawn on it (seen through a translucent
     * [overlay] key) reach WCAG AA contrast, so every design color stays readable.
     */
    private fun readable(color: Int, ink: Int, overlay: Int = Color.TRANSPARENT): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color, hsl)
        val darken = ColorUtils.calculateLuminance(ink) > .5
        var result = color
        while (ColorUtils.calculateContrast(ink, ColorUtils.compositeColors(overlay, result)) < MIN_CONTRAST &&
            hsl[2] in .01f..0.99f) {
            hsl[2] += if (darken) -.01f else .01f
            result = ColorUtils.setAlphaComponent(ColorUtils.HSLToColor(hsl), Color.alpha(color))
        }
        return result
    }

    // Above AA (4.5) because dark keys are lifted slightly toward the label color when drawn
    private const val MIN_CONTRAST = 5.0

    private val darkInk = Color.rgb(32, 33, 36)
    private val lightInk = Color.rgb(241, 243, 244)

    private val colors = listOf(
        // Light
        solid("snow", R.string.theme_color_snow, 0xFFFFFFFF.toInt(), 0xFFF1F3F4.toInt(), 0xFFDADCE0.toInt(), darkInk, false),
        solid("sand", R.string.theme_color_sand, 0xFFEFE6D8.toInt(), 0xFFFBF6EE.toInt(), 0xFFDCCDB6.toInt(), 0xFF3B3127.toInt(), false),
        solid("blush", R.string.theme_color_blush, 0xFFF6E3E7.toInt(), 0xFFFFF7F8.toInt(), 0xFFE8C5CD.toInt(), 0xFF3A2329.toInt(), false),
        solid("mint", R.string.theme_color_mint, 0xFFDFF1E8.toInt(), 0xFFF6FCF9.toInt(), 0xFFBFE0CF.toInt(), 0xFF1E3329.toInt(), false),
        solid("sky", R.string.theme_color_sky, 0xFFDDEBF7.toInt(), 0xFFF5FAFE.toInt(), 0xFFBCD6EE.toInt(), 0xFF1D2E3D.toInt(), false),
        solid("lavender", R.string.theme_color_lavender, 0xFFE8E2F4.toInt(), 0xFFFAF8FE.toInt(), 0xFFCFC4E6.toInt(), 0xFF2B2440.toInt(), false),
        // Dark
        solid("black", R.string.theme_color_black, 0xFF000000.toInt(), 0xFF1C1C1E.toInt(), 0xFF2C2C2E.toInt(), lightInk, true),
        solid("slate", R.string.theme_color_slate, 0xFF263238.toInt(), 0xFF37474F.toInt(), 0xFF455A64.toInt(), 0xFFECEFF1.toInt(), true),
        solid("midnight", R.string.theme_color_midnight, 0xFF0F1A2E.toInt(), 0xFF1C2A44.toInt(), 0xFF2A3B5C.toInt(), 0xFFE6ECF7.toInt(), true),
        solid("forest", R.string.theme_color_forest, 0xFF13241C.toInt(), 0xFF1F3A2D.toInt(), 0xFF2C4E3D.toInt(), 0xFFE3F1E9.toInt(), true),
        solid("plum", R.string.theme_color_plum, 0xFF24152A.toInt(), 0xFF3A2343.toInt(), 0xFF4E3159.toInt(), 0xFFF3E6F7.toInt(), true),
        solid("espresso", R.string.theme_color_espresso, 0xFF241B16.toInt(), 0xFF3A2C24.toInt(), 0xFF4E3D33.toInt(), 0xFFF4EBE4.toInt(), true),
        // Bold hues with white labels
        solid("blue", R.string.theme_color_blue, 0xFF1A56C4.toInt(), 0xFF3A70D6.toInt(), 0xFF1546A3.toInt(), Color.WHITE, true),
        solid("teal", R.string.theme_color_teal, 0xFF00796B.toInt(), 0xFF26897C.toInt(), 0xFF00625A.toInt(), Color.WHITE, true),
        solid("green", R.string.theme_color_green, 0xFF2E7D32.toInt(), 0xFF4A9150.toInt(), 0xFF24682A.toInt(), Color.WHITE, true),
        solid("red", R.string.theme_color_red, 0xFFC62828.toInt(), 0xFFD24545.toInt(), 0xFFA61F1F.toInt(), Color.WHITE, true),
        solid("pink", R.string.theme_color_pink, 0xFFC2185B.toInt(), 0xFFCF3C74.toInt(), 0xFFA0124B.toInt(), Color.WHITE, true),
        solid("purple", R.string.theme_color_purple, 0xFF5E35B1.toInt(), 0xFF7350C0.toInt(), 0xFF4D2A96.toInt(), Color.WHITE, true)
    )

    private fun hsl(hue: Float, saturation: Float, lightness: Float) =
        ColorUtils.HSLToColor(floatArrayOf(((hue % 360f) + 360f) % 360f, saturation, lightness))

    /** Pastel two-hue blends around the color wheel; frosted white keys and dark labels. */
    private val lightGradients = List(25) { i ->
        val hue = i * 360f / 25
        ThemeSpec(
            "light_gradient_${i + 1}", ThemeSection.LIGHT_GRADIENTS, 0, number = i + 1,
            gradient = listOf(hsl(hue, .72f, .87f), hsl(hue + 45f, .68f, .78f)),
            key = ColorUtils.setAlphaComponent(Color.WHITE, 160),
            function = ColorUtils.setAlphaComponent(Color.WHITE, 90),
            ink = darkInk,
            dark = false
        )
    }

    private val darkGradientKey = ColorUtils.setAlphaComponent(Color.WHITE, 34)

    /** Deep two-hue blends; smoky translucent keys and light labels. */
    private val darkGradients = List(28) { i ->
        val hue = i * 360f / 28
        ThemeSpec(
            "dark_gradient_${i + 1}", ThemeSection.DARK_GRADIENTS, 0, number = i + 1,
            gradient = listOf(hsl(hue, .55f, .32f), hsl(hue + 50f, .6f, .13f)).map { readable(it, lightInk, darkGradientKey) },
            key = darkGradientKey,
            function = ColorUtils.setAlphaComponent(Color.BLACK, 70),
            ink = lightInk,
            dark = true
        )
    }
}
