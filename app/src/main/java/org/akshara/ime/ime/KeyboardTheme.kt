package org.akshara.ime.ime

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
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
    /** Behind the keys, the suggestion rail and every panel. */
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
    val dynamic: Boolean
) {
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
            dynamic: Boolean = false
        ): KeyboardTheme {
            val overlay = if (dark) Color.WHITE else Color.BLACK
            // Dark keys sit a touch lighter so they don't sink into the background, like Gboard
            val letter = if (dark && !highContrast) ColorUtils.blendARGB(key, ink, DARK_KEY_LIFT) else key
            val functionPressed = ColorUtils.blendARGB(function, overlay, PRESSED_BLEND)
            return KeyboardTheme(
                id = id,
                background = background,
                key = letter,
                keyPressed = ColorUtils.blendARGB(letter, overlay, PRESSED_BLEND),
                function = function,
                functionPressed = functionPressed,
                ghostPressed = ColorUtils.blendARGB(Color.TRANSPARENT, overlay, PRESSED_BLEND),
                accent = function,
                accentPressed = functionPressed,
                accentInk = ink,
                ink = ink,
                hint = ColorUtils.setAlphaComponent(ink, 140),
                surface = key,
                popup = key,
                popupInk = if (dark) Color.WHITE else Color.rgb(25, 28, 33),
                popupSelected = ColorUtils.blendARGB(key, overlay, SELECTED_BLEND),
                divider = ColorUtils.setAlphaComponent(ink, 40),
                highlight = PIN,
                border = ink,
                dark = dark,
                highContrast = highContrast,
                dynamic = dynamic
            )
        }
    }
}

internal object KeyboardThemes {
    fun resolve(context: Context, theme: String, highContrast: Boolean): KeyboardTheme {
        val dark = when (theme) {
            "dark" -> true
            "light" -> false
            else -> context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES
        }
        return if (theme == "system" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            dynamic(context, dark, highContrast)
        } else {
            fixed(dark, highContrast)
        }
    }

    internal fun fixed(dark: Boolean, highContrast: Boolean) = if (dark) {
        KeyboardTheme.from(
            id = "dark",
            background = Color.rgb(32, 33, 36),
            key = Color.rgb(60, 64, 67),
            function = Color.rgb(95, 99, 104),
            ink = Color.rgb(241, 243, 244),
            dark = true,
            highContrast = highContrast
        )
    } else {
        KeyboardTheme.from(
            id = "light",
            background = Color.rgb(238, 238, 238),
            key = Color.WHITE,
            function = Color.rgb(211, 211, 211),
            ink = Color.rgb(32, 33, 36),
            dark = false,
            highContrast = highContrast
        )
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun dynamic(context: Context, dark: Boolean, highContrast: Boolean): KeyboardTheme {
        fun color(resource: Int) = ContextCompat.getColor(context, resource)
        return if (dark) {
            KeyboardTheme.from(
                id = "dynamic",
                background = color(android.R.color.system_neutral1_900),
                key = color(android.R.color.system_neutral1_800),
                function = color(android.R.color.system_neutral2_700),
                ink = color(android.R.color.system_neutral1_50),
                dark = true,
                highContrast = highContrast,
                dynamic = true
            )
        } else {
            KeyboardTheme.from(
                id = "dynamic",
                background = color(android.R.color.system_neutral1_50),
                key = color(android.R.color.system_neutral1_0),
                function = color(android.R.color.system_neutral2_100),
                ink = color(android.R.color.system_neutral1_900),
                dark = false,
                highContrast = highContrast,
                dynamic = true
            )
        }
    }
}
