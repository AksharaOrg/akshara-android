package org.akshara.ime.ime

import android.os.Looper
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.test.core.app.ApplicationProvider
import org.akshara.ime.engine.InputMode
import org.akshara.ime.engine.SinhalaEngine
import org.akshara.ime.engine.SmartPhoneticV2
import org.akshara.ime.settings.KeyboardPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.util.ReflectionHelpers
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
class TypedLatinSuggestionTest {
    @Test fun typedLettersGoLeftAndTheBestSinhalaWordStaysInTheCentre() {
        val ranked = AksharaInputMethodService.withTypedLatin(listOf("කොහොමද", "කොහොම", "කොහෙද"), "kohomada")
        assertEquals(listOf("කොහොමද", "kohomada", "කොහොම"), ranked)
        // The rail shows the second-ranked word on the left and the best in the centre
        assertEquals(listOf("kohomada", "කොහොමද", "කොහොම"), SuggestionRail.present(ranked).slots)
    }

    @Test fun typedLettersStillShowWithFewOrNoSinhalaWords() {
        assertEquals(listOf("කෑම", "kaema"), AksharaInputMethodService.withTypedLatin(listOf("කෑම"), "kaema"))
        assertEquals(listOf("zzq"), AksharaInputMethodService.withTypedLatin(emptyList(), "zzq"))
        assertEquals(listOf("අ", "ආ", "ඇ"), AksharaInputMethodService.withTypedLatin(listOf("අ", "ආ", "ඇ", "අ"), null))
    }

    @Test fun onlyPlainLettersCountAsAnEnglishWord() {
        assertTrue(AksharaInputMethodService.isLatinWord("Hello"))
        assertFalse(AksharaInputMethodService.isLatinWord("dham+ma"))   // archaic markers
        assertFalse(AksharaInputMethodService.isLatinWord(""))
    }

    @Test fun theRailOffersTheTypedWordAndTappingItCommitsEnglish() = withKeyboard(InputMode.SMART_PHONETIC) { service, editor, keyboard ->
        "meeting".forEach { service.onCharacter(it.toString()) }
        awaitSuggestion(keyboard, "meeting")
        assertTrue("typed as Sinhala until the chip is tapped", editor.text.none { it in 'a'..'z' })
        suggestion(keyboard, "meeting")!!.performClick()
        assertEquals("meeting ", editor.text.toString())
    }

    private fun withKeyboard(mode: InputMode, block: (AksharaInputMethodService, EditText, KeyboardView) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        KeyboardPreferences(context).apply { reset(); this.mode = mode; smartPhoneticV2 = true }
        val controller = Robolectric.buildService(AksharaInputMethodService::class.java).create()
        val service = controller.get()
        try {
            val editor = EditText(context)
            val info = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }
            ReflectionHelpers.setField(service, "mStartedInputConnection", editor.onCreateInputConnection(info)!!)
            ReflectionHelpers.setField(service, "mInputEditorInfo", info)
            val keyboard = service.onCreateInputView() as KeyboardView
            service.onStartInput(info, false)
            service.onStartInputView(info, false)
            keyboard.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY))
            keyboard.layout(0, 0, 1080, 900)
            block(service, editor, ReflectionHelpers.getField(service, "keyboard"))
        } finally {
            service.onFinishInputView(true)
            controller.destroy()
            KeyboardPreferences(context).reset()
            SinhalaEngine.smartPhoneticV2 = true
            SinhalaEngine.smartPhoneticOptions = SmartPhoneticV2.Options()
        }
    }

    /** Suggestions are computed off the main thread; wait for them to reach the rail. */
    private fun awaitSuggestion(keyboard: KeyboardView, value: String) {
        repeat(200) {
            idle()
            if (suggestion(keyboard, value) != null) return
            Thread.sleep(10)
        }
        assertNotNull("no suggestion $value", suggestion(keyboard, value))
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50))

    private fun suggestion(view: View, value: String): View? {
        if (view.isClickable && view.contentDescription == "Suggestion $value") return view
        if (view is ViewGroup) for (i in 0 until view.childCount) suggestion(view.getChildAt(i), value)?.let { return it }
        return null
    }
}
