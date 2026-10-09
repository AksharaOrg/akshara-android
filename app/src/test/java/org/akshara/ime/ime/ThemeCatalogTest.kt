package org.akshara.ime.ime

import android.content.Context
import androidx.core.graphics.ColorUtils
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ThemeCatalogTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Test fun sectionsMatchGboardsPicker() {
        val counts = ThemeCatalog.all.groupingBy { it.section }.eachCount()
        assertEquals(4, counts[ThemeSection.DEFAULT])
        assertEquals(18, counts[ThemeSection.COLORS])
        assertEquals(25, counts[ThemeSection.LIGHT_GRADIENTS])
        assertEquals(28, counts[ThemeSection.DARK_GRADIENTS])
        assertEquals(ThemeCatalog.all.size, ThemeCatalog.all.map { it.id }.toSet().size)
    }

    @Test fun everyThemeResolvesToItself() {
        for (spec in ThemeCatalog.all.filter { it.section != ThemeSection.DEFAULT }) {
            val theme = KeyboardThemes.resolve(context, spec.id, highContrast = false)
            assertEquals(spec.id, theme.id)
            assertEquals(spec.gradient.last(), theme.background)
        }
        assertEquals("light", KeyboardThemes.resolve(context, "no_such_theme", false).id)
    }

    /** Labels stay readable on every built-in theme (WCAG AA for text, 4.5:1). */
    @Test fun labelsAreReadableOnEveryKey() {
        val failures = mutableListOf<String>()
        fun check(what: String, ink: Int, fill: Int) {
            val contrast = ColorUtils.calculateContrast(ink, fill)
            if (contrast < 4.5) failures += "$what %.2f".format(contrast)
        }
        for (spec in ThemeCatalog.all.filter { it.section != ThemeSection.DEFAULT }) {
            val theme = spec.theme(highContrast = false, keyBorders = true)
            for (stop in spec.gradient) {
                check("${spec.id} key", theme.ink, ColorUtils.compositeColors(theme.key, stop))
                check("${spec.id} function", theme.ink, ColorUtils.compositeColors(theme.function, stop))
                check("${spec.id} flat", theme.ink, stop)
            }
            check("${spec.id} popup", theme.popupInk, theme.popup)
        }
        assertTrue(failures.joinToString("; "), failures.isEmpty())
    }

    @Test fun popupsAreOpaqueOverTranslucentKeys() {
        val theme = ThemeCatalog.find("dark_gradient_1")!!.theme(false, true)
        assertTrue(android.graphics.Color.alpha(theme.key) < 255)
        assertEquals(255, android.graphics.Color.alpha(theme.popup))
    }
}
