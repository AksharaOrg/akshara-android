package org.akshara.ime.ime

import android.os.Bundle
import android.text.InputType
import android.view.inputmethod.EditorInfo
import androidx.autofill.inline.Renderer
import androidx.autofill.inline.UiVersions
import androidx.autofill.inline.v1.InlineSuggestionUi
import androidx.test.core.app.ApplicationProvider
import org.akshara.ime.settings.KeyboardPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
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
            val rendererExtras = Renderer.getSupportedInlineUiVersionsAsBundle()
            for (inputType in listOf(
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
                InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            )) {
                service.onStartInput(EditorInfo().apply { this.inputType = inputType }, false)
                val request = service.onCreateInlineSuggestionsRequest(rendererExtras)
                assertNotNull(request)
                assertEquals(3, request!!.maxSuggestionCount)
                val style = request.inlinePresentationSpecs.single().style
                assertEquals(listOf(UiVersions.INLINE_UI_VERSION_1), UiVersions.getVersions(style))
                val chipStyle = InlineSuggestionUi.fromBundle(style.getBundle(UiVersions.INLINE_UI_VERSION_1)!!)
                assertNotNull(chipStyle?.chipStyle)
                assertNotNull(chipStyle?.singleIconChipStyle)
                assertNotNull(chipStyle?.titleStyle)
                assertFalse("A renderer capability bundle is not a chip style", style.keySet() == rendererExtras.keySet())
            }
            prefs.inlineAutofill = false
            assertNull(service.onCreateInlineSuggestionsRequest(rendererExtras))
            prefs.inlineAutofill = true
            assertNull(service.onCreateInlineSuggestionsRequest(Bundle()))
        } finally {
            prefs.reset()
            controller.destroy()
        }
    }
}
