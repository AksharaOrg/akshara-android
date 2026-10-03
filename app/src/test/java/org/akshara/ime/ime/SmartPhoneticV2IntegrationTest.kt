package org.akshara.ime.ime

import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.test.core.app.ApplicationProvider
import org.akshara.ime.data.PredictionRepository
import org.akshara.ime.engine.InputMode
import org.akshara.ime.engine.SinhalaEngine
import org.akshara.ime.settings.KeyboardPreferences
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
class SmartPhoneticV2IntegrationTest {
    private fun withEditor(block: (AksharaInputMethodService, EditText) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        KeyboardPreferences(context).apply { mode = InputMode.SMART_PHONETIC; smartPhoneticV2 = true }
        val controller = Robolectric.buildService(AksharaInputMethodService::class.java).create()
        val service = controller.get()
        try {
            val editor = EditText(context)
            val info = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }
            ReflectionHelpers.setField(service, "mStartedInputConnection", editor.onCreateInputConnection(info)!!)
            service.onStartInput(info, false)
            ReflectionHelpers.getField<PredictionRepository>(service, "prediction").phoneticCandidates("a", emptyList())
            block(service, editor)
        } finally {
            controller.destroy()
            KeyboardPreferences(context).reset()
            SinhalaEngine.smartPhoneticV2 = false
        }
    }

    private fun AksharaInputMethodService.type(text: String) = text.forEach { onCharacter(it.toString()) }

    @Test fun spaceCommitsTheDictionarySpellingAndBackspaceUndoesIt() = withEditor { service, editor ->
        service.type("honda")
        assertEquals("හොන්ද", editor.text.toString())
        service.onSpace()
        assertEquals("හොඳ ", editor.text.toString())
        service.onBackspace(false)
        assertEquals("හොන්ද", editor.text.toString())
    }

    @Test fun explicitSpellingThatIsAWordIsKept() = withEditor { service, editor ->
        service.type("kazda")
        service.onSpace()
        assertEquals("කඳ ", editor.text.toString())
    }

    @Test fun rulesFollowTheResearchRepo() = withEditor { service, editor ->
        service.type("kramaya")
        service.onSpace()
        service.type("d")
        assertEquals("ක්‍රමය ද්", editor.text.toString())
    }
}
