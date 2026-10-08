package org.akshara.ime.ime

import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.test.core.app.ApplicationProvider
import org.akshara.ime.data.PredictionRepository
import org.akshara.ime.data.LocalLearningStore
import org.akshara.ime.engine.InputMode
import org.akshara.ime.engine.SinhalaEngine
import org.akshara.ime.engine.SmartPhoneticV2
import org.akshara.ime.settings.KeyboardPreferences
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
class SmartPhoneticV2IntegrationTest {
    private fun withEditor(settings: KeyboardPreferences.() -> Unit = {}, block: (AksharaInputMethodService, EditText) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        KeyboardPreferences(context).apply { mode = InputMode.SMART_PHONETIC; smartPhoneticV2 = true; settings() }
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
            SinhalaEngine.smartPhoneticV2 = true
            SinhalaEngine.smartPhoneticOptions = SmartPhoneticV2.Options()
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

    @Test fun loneVowelLetterTypedWithAMarkerIsKept() = withEditor { service, editor ->
        service.type("R")
        service.onSpace()
        service.type("E")
        service.onSpace()
        service.type("e")
        service.onSpace()
        assertEquals("ඍ ඓ ඒ ", editor.text.toString())   // unmarked e is still matched by sound
    }

    @Test fun spaceKeepsTheChosenSpellingStyle() = withEditor({ v2RakaransayaU = true; v2RepayaZwj = true }) { service, editor ->
        val z = "‍"
        service.type("kruura")
        assertEquals("ක්${z}රූර", editor.text.toString())
        service.onSpace()
        service.type("karma")
        service.onSpace()
        assertEquals("ක්${z}රූර කර්${z}ම ", editor.text.toString())   // the word list's කෲර / කර්ම don't undo the options
    }

    @Test fun rulesFollowTheResearchRepo() = withEditor { service, editor ->
        service.type("kramaya")
        service.onSpace()
        service.type("d")
        assertEquals("ක්‍රමය ද්", editor.text.toString())
    }

    @Test fun archaicTouchMarkerStaysInCompositionAndBackspaceRecomposes() = withEditor({ v2Archaic = true }) { service, editor ->
        service.type("dham+ma")
        assertEquals("ධම\u200D්ම", editor.text.toString())
        service.onBackspace(false)
        service.onBackspace(false)
        service.onBackspace(false)
        assertEquals("ධම්", editor.text.toString())
        service.type("+ma")
        assertEquals("ධම\u200D්ම", editor.text.toString())
    }

    @Test fun archaicTildeSequencesReachTheConverter() = withEditor({ v2Archaic = true }) { service, editor ->
        service.type("~l")
        assertEquals("ඏ", editor.text.toString())
        service.type("l")
        assertEquals("ඐ", editor.text.toString())
        service.onBackspace(false)
        service.onBackspace(false)
        service.onBackspace(false)
        service.type("ka~n")
        assertEquals("කඁ", editor.text.toString())
    }

    @Test fun archaicMarkersRemainLiteralWhenOptionIsOff() = withEditor { service, editor ->
        service.type("dham+ma")
        assertEquals("ධම්+ම", editor.text.toString())
    }

    @Test fun learnedSpellingDoesNotUndoJoinedRepayaOnSpace() = withEditor({ v2RepayaZwj = true }) { service, editor ->
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        LocalLearningStore(context).record("කර්ම", null)
        service.type("karma")
        assertEquals("කර්\u200Dම", editor.text.toString())
        service.onSpace()
        assertEquals("කර්\u200Dම ", editor.text.toString())
    }
}
