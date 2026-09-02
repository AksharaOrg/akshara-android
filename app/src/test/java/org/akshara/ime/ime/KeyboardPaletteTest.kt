package org.akshara.ime.ime

import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class KeyboardPaletteTest {
    @Test
    fun fixedLightRetainsCrispGboardFallback() {
        val palette = KeyboardPaletteResolver.fixed(dark = false, highContrast = false)

        assertEquals(Color.rgb(238, 238, 238), palette.background)
        assertEquals(Color.WHITE, palette.key)
        assertEquals(Color.rgb(32, 33, 36), palette.ink)
        assertFalse(palette.dark)
        assertFalse(palette.dynamic)
    }

    @Test
    fun fixedDarkCarriesAccessibilitySetting() {
        val palette = KeyboardPaletteResolver.fixed(dark = true, highContrast = true)

        assertEquals(Color.rgb(32, 33, 36), palette.background)
        assertEquals(Color.rgb(241, 243, 244), palette.ink)
        assertTrue(palette.dark)
        assertTrue(palette.highContrast)
        assertFalse(palette.dynamic)
    }
}
