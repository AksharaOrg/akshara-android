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

    @Test
    fun keyShapesRoundKeysWithoutChangingThemeColors() {
        // A 36 x 48 letter key with the standard 8 radius
        assertEquals(8f, KeyShape.RECTANGULAR.radius(36f, 48f, 8f))
        assertEquals(10.8f, KeyShape.ROUNDED.radius(36f, 48f, 8f), .001f)
        assertEquals(8f, KeyShape.ROUNDED.radius(20f, 20f, 8f))   // never squarer than standard
        assertEquals(18f, KeyShape.PILL.radius(36f, 48f, 8f))
        assertEquals(KeyShape.RECTANGULAR, KeyShape.of("nonsense"))

        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        for (id in listOf(ThemeCatalog.LIGHT, "color_blue", "dark_gradient_21")) {
            val plain = KeyboardThemes.resolve(context, id, highContrast = false)
            val pill = KeyboardThemes.resolve(context, id, highContrast = false, keyShape = KeyShape.PILL)
            assertEquals(KeyShape.PILL, pill.keyShape)
            assertEquals(plain.copy(keyShape = KeyShape.PILL), pill)
        }
    }

    /** Gboard's 2025 Dynamic color: letters in one tone, every function key and Enter in one shared accent. */
    @Test
    @org.robolectric.annotation.Config(sdk = [34])
    fun dynamicColorUsesTwoKeyTonesAndStaysReadable() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        for (night in listOf("notnight", "night")) {
            org.robolectric.RuntimeEnvironment.setQualifiers(night)
            val theme = KeyboardThemes.resolve(context, ThemeCatalog.DYNAMIC, highContrast = false)
            assertTrue(theme.dynamic)
            assertEquals(night == "night", theme.dark)
            assertEquals(theme.function, theme.accent)
            assertEquals(theme.ink, theme.accentInk)
            assertTrue(ColorUtils.calculateContrast(theme.ink, theme.key) >= 4.5)
            assertTrue(ColorUtils.calculateContrast(theme.ink, theme.function) >= 4.5)
        }
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
