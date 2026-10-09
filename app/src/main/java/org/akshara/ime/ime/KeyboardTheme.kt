package org.akshara.ime.ime

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils

/**
 * Every color the keyboard draws with. Surfaces read these tokens instead of deriving their own,
 * so a theme is fully described by one value (Gboard's theme stylesheets cover the same parts).
 */
internal data class KeyboardTheme(
    val id: String,
    /** Behind the keys, the suggestion rail and every panel; for a gradient, its bottom color (it meets the navigation bar). */
    val background: Int,
    /** Letter keys. */
    val key: Int,
    val keyPressed: Int,
    /** Shift, delete, ?123, emoji and the other function keys. */
    val function: Int,
    val functionPressed: Int,
    /** Pressed state of borderless buttons (emoji tabs, ABC, clear search). */
    val ghostPressed: Int,
    /** The Enter (action) key. */
    val accent: Int,
    val accentPressed: Int,
    val accentInk: Int,
    /** Labels and icons. */
    val ink: Int,
    /** Corner hints on letter keys. */
    val hint: Int,
    /** Cards and chips on top of the background: clipboard items, emoji search, autofill chips. */
    val surface: Int,
    /** Key press preview and long-press picker. */
    val popup: Int,
    val popupInk: Int,
    val popupSelected: Int,
    /** Thin separators. */
    val divider: Int,
    /** Pinned clipboard items. */
    val highlight: Int,
    /** Key outline in high contrast mode. */
    val border: Int,
    val dark: Boolean,
    val highContrast: Boolean,
    val dynamic: Boolean,
    /** Gboard's "Key borders": off draws letter and plain function keys flat on the background. */
    val keyBorders: Boolean = true,
    /** Top-to-bottom background stops; empty or one stop means a solid [background]. */
    val gradient: List<Int> = emptyList(),
    val glows: List<Glow> = emptyList()
) {
    fun backgroundDrawable(): Drawable = if (gradient.size > 1 || glows.isNotEmpty()) {
        ThemeBackgroundDrawable(gradient.ifEmpty { listOf(background) }, glows)
    } else ColorDrawable(background)

    /** A gradient or glow continues under the navigation bar; a solid color can simply paint the bar. */
    val drawsUnderNavigationBar: Boolean get() = gradient.size > 1 || glows.isNotEmpty()

    /** High contrast needs a shape to outline, so it always keeps borders. */
    val flatKeys: Boolean get() = !keyBorders && !highContrast

    companion object {
        private const val PRESSED_BLEND = .18f
        private const val SELECTED_BLEND = .16f
        private const val DARK_KEY_LIFT = .045f
        private val PIN = 0xFFFF9800.toInt()

        /** Derives every token from the four colors a theme is designed with. */
        fun from(
            id: String,
            background: Int,
            key: Int,
            function: Int,
            ink: Int,
            dark: Boolean,
            highContrast: Boolean,
            dynamic: Boolean = false,
            keyBorders: Boolean = true,
            gradient: List<Int> = emptyList(),
            glows: List<Glow> = emptyList(),
            /** The Enter key's color; null uses the function key color. */
            accent: Int? = null
        ): KeyboardTheme {
            val overlay = if (dark) Color.WHITE else Color.BLACK
            // Dark keys sit a touch lighter so they don't sink into the background, like Gboard
            val letter = if (dark && !highContrast) ColorUtils.blendARGB(key, ink, DARK_KEY_LIFT) else key
            val functionPressed = ColorUtils.blendARGB(function, overlay, PRESSED_BLEND)
            // Keys may be translucent over a gradient; popups float above it, so they get the solid mix
            val popup = ColorUtils.compositeColors(key, midBackground(background, gradient))
            return KeyboardTheme(
                id = id,
                background = background,
                key = letter,
                keyPressed = ColorUtils.blendARGB(letter, overlay, PRESSED_BLEND),
                function = function,
                functionPressed = functionPressed,
                ghostPressed = ColorUtils.blendARGB(Color.TRANSPARENT, overlay, PRESSED_BLEND),
                accent = accent ?: function,
                accentPressed = accent?.let { ColorUtils.blendARGB(it, overlay, PRESSED_BLEND) } ?: functionPressed,
                accentInk = accent?.let { onAccent(it) } ?: ink,
                ink = ink,
                hint = ColorUtils.setAlphaComponent(ink, 140),
                surface = key,
                popup = popup,
                popupInk = if (dark) Color.WHITE else Color.rgb(25, 28, 33),
                popupSelected = ColorUtils.blendARGB(popup, overlay, SELECTED_BLEND),
                divider = ColorUtils.setAlphaComponent(ink, 40),
                highlight = PIN,
                border = ink,
                dark = dark,
                highContrast = highContrast,
                dynamic = dynamic,
                keyBorders = keyBorders,
                gradient = gradient,
                glows = glows
            )
        }

        /** White or near-black, whichever reads better on the accent. */
        fun onAccent(accent: Int): Int {
            val darkInk = Color.rgb(32, 33, 36)
            return if (ColorUtils.calculateContrast(Color.WHITE, accent) >= ColorUtils.calculateContrast(darkInk, accent)) Color.WHITE else darkInk
        }

        private fun midBackground(background: Int, gradient: List<Int>) =
            if (gradient.size > 1) ColorUtils.blendARGB(gradient.first(), gradient.last(), .5f) else background
    }
}

internal object KeyboardThemes {
    /** Gboard's Default themes give Enter a blue accent. */
    private val LIGHT_ACCENT = Color.rgb(27, 110, 243)
    private val DARK_ACCENT = Color.rgb(141, 182, 250)

    /** [theme] is a [ThemeCatalog] id; an unknown id falls back to System auto. */
    fun resolve(context: Context, theme: String, highContrast: Boolean, keyBorders: Boolean = true): KeyboardTheme {
        ThemeCatalog.find(theme)?.takeIf { it.section != ThemeSection.DEFAULT }?.let { return it.theme(highContrast, keyBorders) }
        val systemDark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        val resolved = when (theme) {
            ThemeCatalog.LIGHT -> fixed(false, highContrast)
            ThemeCatalog.DARK -> fixed(true, highContrast)
            ThemeCatalog.DYNAMIC -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) dynamic(context, systemDark, highContrast)
                else fixed(systemDark, highContrast)
            else -> fixed(systemDark, highContrast)
        }
        return resolved.copy(keyBorders = keyBorders)
    }

    internal fun fixed(dark: Boolean, highContrast: Boolean) = if (dark) {
        KeyboardTheme.from(
            id = "dark",
            background = Color.rgb(32, 33, 36),
            key = Color.rgb(60, 64, 67),
            function = Color.rgb(95, 99, 104),
            ink = Color.rgb(241, 243, 244),
            dark = true,
            highContrast = highContrast,
            accent = DARK_ACCENT
        )
    } else {
        KeyboardTheme.from(
            id = "light",
            background = Color.rgb(238, 238, 238),
            key = Color.WHITE,
            function = Color.rgb(211, 211, 211),
            ink = Color.rgb(32, 33, 36),
            dark = false,
            highContrast = highContrast,
            accent = LIGHT_ACCENT
        )
    }

    /**
     * Gboard's Dynamic color (2025 mapping): a tinted background, letter keys in one wallpaper tone, and
     * every function key, Enter included, sharing one muted secondary tone (less vivid than before 2025).
     */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun dynamic(context: Context, dark: Boolean, highContrast: Boolean): KeyboardTheme {
        fun color(resource: Int) = ContextCompat.getColor(context, resource)
        return if (dark) {
            KeyboardTheme.from(
                id = ThemeCatalog.DYNAMIC,
                background = color(android.R.color.system_neutral1_900),
                key = color(android.R.color.system_neutral2_800),
                function = color(android.R.color.system_accent2_700),
                ink = color(android.R.color.system_neutral1_50),
                dark = true,
                highContrast = highContrast,
                dynamic = true
            )
        } else {
            KeyboardTheme.from(
                id = ThemeCatalog.DYNAMIC,
                background = color(android.R.color.system_neutral2_50),
                key = color(android.R.color.system_neutral1_10),
                function = color(android.R.color.system_accent2_100),
                ink = color(android.R.color.system_neutral1_900),
                dark = false,
                highContrast = highContrast,
                dynamic = true
            )
        }
    }
}
