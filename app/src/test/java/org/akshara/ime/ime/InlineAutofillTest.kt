package org.akshara.ime.ime

import android.os.Bundle
import android.text.InputType
import android.view.inputmethod.EditorInfo
import androidx.test.core.app.ApplicationProvider
import org.akshara.ime.settings.KeyboardPreferences
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class InlineAutofillTest {
    @Test fun passwordAndNumericPasswordEditorsRequestSystemAutofill() {
        val prefs = KeyboardPreferences(ApplicationProvider.getApplicationContext())
        prefs.inlineAutofill = true
        val controller = Robolectric.buildService(AksharaInputMethodService::class.java).create()
        val service = controller.get()
        try {
            for (inputType in listOf(
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
                InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            )) {
                service.onStartInput(EditorInfo().apply { this.inputType = inputType }, false)
                assertNotNull(service.onCreateInlineSuggestionsRequest(Bundle()))
            }
            prefs.inlineAutofill = false
            assertNull(service.onCreateInlineSuggestionsRequest(Bundle()))
        } finally {
            prefs.reset()
            controller.destroy()
        }
    }
}
