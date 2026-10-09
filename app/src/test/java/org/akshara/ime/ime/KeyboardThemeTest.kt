package org.akshara.ime.ime

import android.graphics.Color
import androidx.core.graphics.ColorUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class KeyboardThemeTest {
    @Test
    fun fixedLightRetainsCrispGboardFallback() {
        val theme = KeyboardThemes.fixed(dark = false, highContrast = false)

        assertEquals(Color.rgb(238, 238, 238), theme.background)
        assertEquals(Color.WHITE, theme.key)
        assertEquals(Color.WHITE, theme.surface)
        assertEquals(Color.rgb(211, 211, 211), theme.function)
        assertEquals(Color.rgb(32, 33, 36), theme.ink)
        assertFalse(theme.dark)
        assertFalse(theme.dynamic)
    }

    @Test
    fun fixedDarkCarriesAccessibilitySetting() {
        val theme = KeyboardThemes.fixed(dark = true, highContrast = true)

        assertEquals(Color.rgb(32, 33, 36), theme.background)
        assertEquals(Color.rgb(241, 243, 244), theme.ink)
        assertTrue(theme.dark)
        assertTrue(theme.highContrast)
        assertFalse(theme.dynamic)
    }

    @Test
    fun keyBordersOffFlattensKeysUnlessHighContrast() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        assertFalse(KeyboardThemes.resolve(context, "light", highContrast = false).flatKeys)
        assertTrue(KeyboardThemes.resolve(context, "light", highContrast = false, keyBorders = false).flatKeys)
        assertFalse(KeyboardThemes.resolve(context, "light", highContrast = true, keyBorders = false).flatKeys)
    }

    /** The tokens reproduce what each surface used to derive on its own. */
    @Test
    fun derivedTokensMatchTheColorsSurfacesUsedToCompute() {
        val dark = KeyboardThemes.fixed(dark = true, highContrast = false)
        val key = Color.rgb(60, 64, 67)
        val lifted = ColorUtils.blendARGB(key, dark.ink, .045f)
        assertEquals(lifted, dark.key)
        assertEquals(key, dark.surface)
        assertEquals(ColorUtils.blendARGB(lifted, Color.WHITE, .18f), dark.keyPressed)
        assertEquals(ColorUtils.blendARGB(dark.function, Color.WHITE, .18f), dark.functionPressed)
        assertEquals(Color.rgb(141, 182, 250), dark.accent)   // Gboard-style blue Enter
        assertEquals(KeyboardTheme.onAccent(dark.accent), dark.accentInk)
        assertEquals(Color.WHITE, dark.popupInk)
        assertEquals(ColorUtils.blendARGB(key, Color.WHITE, .16f), dark.popupSelected)
        assertEquals(ColorUtils.setAlphaComponent(dark.ink, 140), dark.hint)

        val light = KeyboardThemes.fixed(dark = false, highContrast = false)
        assertEquals(ColorUtils.blendARGB(Color.WHITE, Color.BLACK, .18f), light.keyPressed)
        assertEquals(Color.rgb(25, 28, 33), light.popupInk)
        assertEquals(0xFFFF9800.toInt(), light.highlight)

        // High contrast keeps keys flat so the outline reads clearly
        assertEquals(key, KeyboardThemes.fixed(dark = true, highContrast = true).key)
    }
}
