package org.akshara.ime.ime

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat

internal data class KeyboardPalette(
    val background: Int,
    val key: Int,
    val utility: Int,
    val ink: Int,
    val selected: Int,
    val dark: Boolean,
    val highContrast: Boolean,
    val dynamic: Boolean
)

internal object KeyboardPaletteResolver {
    fun resolve(context: Context, theme: String, highContrast: Boolean, useSystemColor: Boolean = true): KeyboardPalette {
        val dark = when (theme) {
            "dark", "black" -> true
            "light" -> false
            else -> context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES
        }
        val isBlack = theme == "black"
        val basePalette = if (useSystemColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            dynamic(context, dark, highContrast)
        } else {
            fixed(dark, highContrast)
        }
        return if (isBlack) {
            basePalette.copy(background = Color.BLACK)
        } else {
            basePalette
        }
    }

    internal fun fixed(dark: Boolean, highContrast: Boolean) = if (dark) {
        KeyboardPalette(
            background = Color.rgb(32, 33, 36),
            key = Color.rgb(60, 64, 67),
            utility = Color.rgb(95, 99, 104),
            ink = Color.rgb(241, 243, 244),
            selected = Color.rgb(68, 68, 68),
            dark = true,
            highContrast = highContrast,
            dynamic = false
        )
    } else {
        KeyboardPalette(
            background = Color.rgb(238, 238, 238),
            key = Color.WHITE,
            utility = Color.rgb(211, 211, 211),
            ink = Color.rgb(32, 33, 36),
            selected = Color.rgb(68, 68, 68),
            dark = false,
            highContrast = highContrast,
            dynamic = false
        )
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun dynamic(context: Context, dark: Boolean, highContrast: Boolean): KeyboardPalette {
        fun color(resource: Int) = ContextCompat.getColor(context, resource)
        return if (dark) {
            KeyboardPalette(
                background = color(android.R.color.system_neutral1_900),
                key = color(android.R.color.system_neutral1_800),
                utility = color(android.R.color.system_neutral2_700),
                ink = color(android.R.color.system_neutral1_50),
                selected = color(android.R.color.system_accent1_700),
                dark = true,
                highContrast = highContrast,
                dynamic = true
            )
        } else {
            KeyboardPalette(
                background = color(android.R.color.system_neutral1_10),
                key = color(android.R.color.system_neutral1_0),
                utility = color(android.R.color.system_neutral2_100),
                ink = color(android.R.color.system_neutral1_900),
                selected = color(android.R.color.system_accent1_100),
                dark = false,
                highContrast = highContrast,
                dynamic = true
            )
        }
    }
}
