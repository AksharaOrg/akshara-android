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
    val glows: List<Glow> = emptyList(),
    val key: Int = 0,
    val function: Int = 0,
    val ink: Int = 0,
    /** The Enter key; Gboard gives every theme its own accent. */
    val accent: Int? = null,
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
        gradient = gradient,
        glows = glows,
        accent = accent
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

    private val darkInk = Color.rgb(32, 33, 36)
    private val lightInk = Color.rgb(241, 243, 244)

    // Above AA (4.5) because dark keys are lifted slightly toward the label color when drawn
    private const val MIN_CONTRAST = 5.0
    // A glow's brightest point only sits behind key labels, which are large text: WCAG AA asks 3:1 there
    private const val GLOW_CONTRAST = 3.2

    /**
     * Nudges [color]'s lightness away from [ink] until labels drawn on it (seen through a translucent
     * [overlay] key) reach WCAG AA contrast, so every design color stays readable.
     */
    private fun readable(color: Int, ink: Int, overlay: Int = Color.TRANSPARENT, min: Double = MIN_CONTRAST): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color, hsl)
        val darken = ColorUtils.calculateLuminance(ink) > .5
        var result = color
        while (ColorUtils.calculateContrast(ink, ColorUtils.compositeColors(overlay, result)) < min &&
            hsl[2] in .01f..0.99f) {
            hsl[2] += if (darken) -.01f else .01f
            result = ColorUtils.setAlphaComponent(ColorUtils.HSLToColor(hsl), Color.alpha(color))
        }
        return result
    }

    /** An accent in [color]'s hue; greys (no real hue) get a calm blue instead of an accidental red. */
    private fun accentFrom(color: Int, saturation: Float, lightness: Float): Int {
        val hsl = FloatArray(3).also { ColorUtils.colorToHSL(color, it) }
        return hsl(if (hsl[1] < .12f) 215f else hsl[0], saturation, lightness)
    }

    private fun hsl(hue: Float, saturation: Float, lightness: Float) =
        ColorUtils.HSLToColor(floatArrayOf(((hue % 360f) + 360f) % 360f, saturation, lightness))

    /** The Enter icon is white or near-black (whichever suits); keep it readable on the accent. */
    private fun readableAccent(accent: Int) = readable(accent, KeyboardTheme.onAccent(accent))

    private fun rgb(hex: Number) = (0xFF000000 or hex.toLong()).toInt()

    // Colors: solid backgrounds, opaque keys, and a matching Enter accent.

    private fun solid(id: String, name: Int, bg: Number, key: Number, function: Number, ink: Int, accent: Number, dark: Boolean) =
        ThemeSpec("color_$id", ThemeSection.COLORS, name, gradient = listOf(readable(rgb(bg), ink)),
            key = readable(rgb(key), ink), function = readable(rgb(function), ink), ink = ink, accent = readableAccent(rgb(accent)), dark = dark)

    private val colors = listOf(
        // Light
        solid("snow", R.string.theme_color_snow, 0xFFFFFF, 0xF1F3F4, 0xDADCE0, darkInk, 0x1B6EF3, false),
        solid("sand", R.string.theme_color_sand, 0xEFE6D8, 0xFBF6EE, 0xDCCDB6, rgb(0x3B3127), 0xA4652A, false),
        solid("blush", R.string.theme_color_blush, 0xF6E3E7, 0xFFF7F8, 0xE8C5CD, rgb(0x3A2329), 0xC2185B, false),
        solid("mint", R.string.theme_color_mint, 0xDFF1E8, 0xF6FCF9, 0xBFE0CF, rgb(0x1E3329), 0x1E8E5A, false),
        solid("sky", R.string.theme_color_sky, 0xDDEBF7, 0xF5FAFE, 0xBCD6EE, rgb(0x1D2E3D), 0x1565C0, false),
        solid("lavender", R.string.theme_color_lavender, 0xE8E2F4, 0xFAF8FE, 0xCFC4E6, rgb(0x2B2440), 0x6A4FC2, false),
        // Dark
        solid("black", R.string.theme_color_black, 0x000000, 0x1C1C1E, 0x2C2C2E, lightInk, 0x8DB6FA, true),
        solid("slate", R.string.theme_color_slate, 0x263238, 0x37474F, 0x455A64, rgb(0xECEFF1), 0x80CBC4, true),
        solid("midnight", R.string.theme_color_midnight, 0x0F1A2E, 0x1C2A44, 0x2A3B5C, rgb(0xE6ECF7), 0x7FA7F5, true),
        solid("forest", R.string.theme_color_forest, 0x13241C, 0x1F3A2D, 0x2C4E3D, rgb(0xE3F1E9), 0x7BD39A, true),
        solid("plum", R.string.theme_color_plum, 0x24152A, 0x3A2343, 0x4E3159, rgb(0xF3E6F7), 0xD59BEA, true),
        solid("espresso", R.string.theme_color_espresso, 0x241B16, 0x3A2C24, 0x4E3D33, rgb(0xF4EBE4), 0xE0A47A, true),
        // Bold hues with white labels
        solid("blue", R.string.theme_color_blue, 0x1A56C4, 0x3A70D6, 0x1546A3, Color.WHITE, 0x8EC5FF, true),
        solid("teal", R.string.theme_color_teal, 0x00796B, 0x26897C, 0x00625A, Color.WHITE, 0x80CBC4, true),
        solid("green", R.string.theme_color_green, 0x2E7D32, 0x4A9150, 0x24682A, Color.WHITE, 0xA5D6A7, true),
        solid("red", R.string.theme_color_red, 0xC62828, 0xD24545, 0xA61F1F, Color.WHITE, 0xFF8A65, true),
        solid("pink", R.string.theme_color_pink, 0xC2185B, 0xCF3C74, 0xA0124B, Color.WHITE, 0xF48FB1, true),
        solid("purple", R.string.theme_color_purple, 0x5E35B1, 0x7350C0, 0x4D2A96, Color.WHITE, 0xB39DDB, true)
    )

    // Light gradients: soft, muted two-tone washes (top to bottom) with frosted keys.

    private val lightWashes = listOf(
        0xF8B9B9 to 0xC9D6F5, 0xE7E3DC to 0xCFDDD9, 0xE2CFCF to 0xC5CCD8, 0xE9EEFA to 0xBDBDBD, 0xF6C6D9 to 0xF093B3,
        0xFCE3D9 to 0xBDE6F7, 0xFDE9A6 to 0xE3DDF6, 0xB5DFA3 to 0xE3CFD3, 0xF9C79C to 0xFDE5B5, 0xD9F0F5 to 0xA9D4E6,
        0xE9DDF7 to 0xC7B7EC, 0xFFF1D0 to 0xF7C8C2, 0xD7EEDC to 0xA8D5BA, 0xF4E4D0 to 0xD9BFA5, 0xDDE6F0 to 0xB3C4DA,
        0xF7D6E6 to 0xD9C2F0, 0xE6F4C9 to 0xC2E0A0, 0xFFE0E9 to 0xFFE9C7, 0xD1F2EB to 0xC8D7F7, 0xEFE7DF to 0xDCCFC4,
        0xFADADD to 0xE8B4BC, 0xE3F2FD to 0xD1C4E9, 0xFFF6E0 to 0xE6E6E6, 0xCDEBF4 to 0xF6D7C3, 0xE8E0F0 to 0xF3E5D8
    )

    private val lightGradientKey = ColorUtils.setAlphaComponent(Color.WHITE, 160)

    private val lightGradients = lightWashes.mapIndexed { i, (top, bottom) ->
        ThemeSpec(
            "light_gradient_${i + 1}", ThemeSection.LIGHT_GRADIENTS, 0, number = i + 1,
            gradient = listOf(rgb(top), rgb(bottom)).map { readable(it, darkInk, lightGradientKey) },
            key = lightGradientKey,
            function = ColorUtils.setAlphaComponent(Color.WHITE, 90),
            ink = darkInk,
            accent = readableAccent(accentFrom(rgb(bottom), .55f, .42f)),
            dark = false
        )
    }

    // Dark gradients: deep, mostly single-family fades, then glow themes (soft light from an edge or corner).

    private class Dark(val stops: List<Number>, val glows: List<Glow> = emptyList())

    private fun glow(color: Number, x: Float, y: Float, radius: Float, alpha: Int = 240) =
        Glow(ColorUtils.setAlphaComponent(rgb(color), alpha), x, y, radius)

    private val darkFades = listOf(
        Dark(listOf(0x2E1A6B, 0x9A4796)),            // indigo to orchid
        Dark(listOf(0x55585C, 0x101112)),            // graphite to black
        Dark(listOf(0x5A4038, 0x3E2B26)),            // cocoa
        Dark(listOf(0x525D66, 0x2B3034)),            // slate
        Dark(listOf(0x00796B, 0x00574D)),            // deep teal
        Dark(listOf(0x1E6FD9, 0x0E4FB0)),            // cobalt
        Dark(listOf(0x3D6A93, 0x1D2B38)),            // steel blue to night
        Dark(listOf(0x5B5F63, 0x2E3133)),            // storm grey
        Dark(listOf(0x6B5D54, 0x1E1715)),            // taupe to black
        Dark(listOf(0x0B5A49, 0x03140F)),            // forest to black
        Dark(listOf(0x00837F, 0x1A5B67)),            // lagoon
        Dark(listOf(0x857BA8, 0x3E3B4A)),            // dusk lavender
        Dark(listOf(0x8A3B3B, 0x3A1A16)),            // rust
        Dark(listOf(0x4A4A9E, 0x0E5E6E)),            // indigo to teal
        Dark(listOf(0x01709E, 0x2B3D4D)),            // ocean
        Dark(listOf(0x5530C9, 0x2B1A80)),            // violet
        Dark(listOf(0xB0175A, 0x86104A)),            // crimson
        Dark(listOf(0x0599E0, 0x0436B0)),            // sky to royal
        // Glows
        Dark(listOf(0x2E2A40, 0x5E4A9E, 0x9A7488)),  // twilight horizon
        Dark(listOf(0x1653A8, 0x532A9E)),            // blue to purple
        Dark(listOf(0x1440C0, 0x103A9A), listOf(glow(0x18C070, .5f, 1.05f, .62f))),                // aurora
        Dark(listOf(0x182878, 0x15205E), listOf(glow(0xE08050, .45f, 1.08f, .7f))),                // sunrise
        Dark(listOf(0x5C3A5E, 0x9E5A70)),                                                          // rose dusk
        Dark(listOf(0x3E5C86, 0x4A4F80), listOf(glow(0xF08A8A, .95f, 1f, .8f), glow(0x2E6FB0, 0f, .2f, .5f, 140))),  // coral bay
        Dark(listOf(0x5A1050, 0x3A2A70), listOf(glow(0x40C0C0, 1f, 1f, .7f))),                     // nebula
        Dark(listOf(0x0E2A6A, 0x0E3A5A), listOf(glow(0x30C050, 1f, 1f, .55f), glow(0x1E7A8A, .5f, .45f, .5f, 120))),  // lagoon light
        Dark(listOf(0x2E2A6A, 0x4A2A7A), listOf(glow(0xE040A0, 1f, 1f, .62f))),                    // orchid glow
        Dark(listOf(0x5A1208, 0x7A1A14), listOf(glow(0xF07830, 1f, 1f, .7f)))                      // ember
    )

    private val darkGradientKey = ColorUtils.setAlphaComponent(Color.WHITE, 34)

    private val darkGradients = darkFades.mapIndexed { i, d ->
        val stops = d.stops.map { readable(rgb(it), lightInk, darkGradientKey) }
        // A glow must not brighten the keys under it past readable contrast either
        val bottom = stops.last()
        val glows = d.glows.map { g ->
            val solid = readable(ColorUtils.compositeColors(g.color, bottom), lightInk, darkGradientKey, GLOW_CONTRAST)
            g.copy(color = ColorUtils.setAlphaComponent(solid, Color.alpha(g.color)))
        }
        ThemeSpec(
            "dark_gradient_${i + 1}", ThemeSection.DARK_GRADIENTS, 0, number = i + 1,
            gradient = stops,
            glows = glows,
            key = darkGradientKey,
            function = ColorUtils.setAlphaComponent(Color.BLACK, 70),
            ink = lightInk,
            accent = readableAccent(accentFrom(d.glows.firstOrNull()?.color ?: stops.first(), .7f, .72f)),
            dark = true
        )
    }
}
