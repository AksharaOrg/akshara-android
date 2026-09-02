package org.akshara.ime.settings

import androidx.test.core.app.ApplicationProvider
import org.akshara.ime.engine.InputMode
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class KeyboardPreferencesTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    @Before fun clear() = context.getSharedPreferences(KeyboardPreferences.FILE, 0).edit().clear().commit().let { }
    @Test fun defaultsArePrivateAndPractical() {
        val p = KeyboardPreferences(context); assertEquals(InputMode.SMART_PHONETIC, p.mode); assertTrue(p.suggestions); assertFalse(p.clipboardHistory)
    }
    @Test fun valuesPersistAndReset() {
        KeyboardPreferences(context).apply { mode = InputMode.WIJESEKARA; highContrast = true }
        KeyboardPreferences(context).apply { assertEquals(InputMode.WIJESEKARA, mode); assertTrue(highContrast); reset() }
        assertEquals(InputMode.SMART_PHONETIC, KeyboardPreferences(context).mode)
    }
}
