package org.akshara.ime.settings

import androidx.test.core.app.ApplicationProvider
import org.akshara.ime.engine.InputMode
import org.akshara.ime.engine.SmartPhoneticV2
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
        assertTrue(p.doubleSpacePeriod); assertTrue(p.smartQuotes); assertTrue(p.smartPunctuation); assertFalse(p.englishForOneWord)
        assertFalse(p.clipboardPreview); assertTrue(p.spacePunctuationKeys); assertTrue(p.emojiPicker)
    }
    @Test fun grammarCorrectSmartPhoneticIsTheDefault() {
        val p = KeyboardPreferences(context)
        assertTrue(p.smartPhoneticV2)
        assertEquals(SmartPhoneticV2.Options(), p.smartPhoneticOptions)
        p.v2RakaransayaU = true; p.v2Archaic = true
        assertEquals(SmartPhoneticV2.Options(archaic = true, rakaransayaU = true), KeyboardPreferences(context).smartPhoneticOptions)
    }
    @Test fun emojiPlacementMigratesLegacyDisabledAndPersistsNewChoice() {
        context.getSharedPreferences(KeyboardPreferences.FILE, 0).edit().putBoolean("emoji_picker", false).commit()
        assertEquals(EmojiButtonPlacement.DISABLED, KeyboardPreferences(context).emojiButtonPlacement)
        KeyboardPreferences(context).emojiButtonPlacement = EmojiButtonPlacement.KEYBOARD
        assertEquals(EmojiButtonPlacement.KEYBOARD, KeyboardPreferences(context).emojiButtonPlacement)
        KeyboardPreferences(context).reset()
        assertEquals(EmojiButtonPlacement.TOOLBAR, KeyboardPreferences(context).emojiButtonPlacement)
    }
    @Test fun valuesPersistAndReset() {
        KeyboardPreferences(context).apply { mode = InputMode.WIJESEKARA; highContrast = true }
        KeyboardPreferences(context).apply { assertEquals(InputMode.WIJESEKARA, mode); assertTrue(highContrast); reset() }
        assertEquals(InputMode.SMART_PHONETIC, KeyboardPreferences(context).mode)
    }
}
