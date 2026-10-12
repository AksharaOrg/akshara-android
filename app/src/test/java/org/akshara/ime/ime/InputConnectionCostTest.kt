package org.akshara.ime.ime

import android.os.Looper
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.view.inputmethod.SurroundingText
import android.widget.EditText
import androidx.test.core.app.ApplicationProvider
import org.akshara.ime.engine.InputMode
import org.akshara.ime.engine.SinhalaEngine
import org.akshara.ime.engine.SmartPhoneticV2
import org.akshara.ime.settings.KeyboardPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.util.ReflectionHelpers

/**
 * Every read from the text field is a blocking call into the app's process. These tests count them per keystroke
 * so typing stays cheap, and check that typing still produces the right text.
 */
@RunWith(RobolectricTestRunner::class)
class InputConnectionCostTest {
    /** Counts synchronous reads; writes are one-way calls and cost little. */
    private class CountingConnection(target: InputConnection) : InputConnectionWrapper(target, true) {
        val reads = mutableMapOf<String, Int>()
        var wholeDocumentCopies = 0
        private fun read(name: String) { reads[name] = (reads[name] ?: 0) + 1 }
        val total get() = reads.values.sum()
        fun reset() { reads.clear(); wholeDocumentCopies = 0 }

        override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence? { read("getTextBeforeCursor"); return super.getTextBeforeCursor(n, flags) }
        override fun getTextAfterCursor(n: Int, flags: Int): CharSequence? { read("getTextAfterCursor"); return super.getTextAfterCursor(n, flags) }
        override fun getSelectedText(flags: Int): CharSequence? { read("getSelectedText"); return super.getSelectedText(flags) }
        override fun getCursorCapsMode(reqModes: Int): Int { read("getCursorCapsMode"); return super.getCursorCapsMode(reqModes) }
        override fun getSurroundingText(beforeLength: Int, afterLength: Int, flags: Int): SurroundingText? {
            read("getSurroundingText"); return super.getSurroundingText(beforeLength, afterLength, flags)
        }
        override fun getExtractedText(request: ExtractedTextRequest?, flags: Int): ExtractedText? {
            read("getExtractedText")
            if ((request?.hintMaxChars ?: 0) == 0) wholeDocumentCopies++
            return super.getExtractedText(request, flags)
        }
    }

    private fun withService(english: Boolean = false, block: (AksharaInputMethodService, EditText, CountingConnection) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        KeyboardPreferences(context).apply { reset(); mode = InputMode.SMART_PHONETIC; smartPhoneticV2 = true; persistentEnglish = english }
        val controller = Robolectric.buildService(AksharaInputMethodService::class.java).create()
        val service = controller.get()
        try {
            val editor = EditText(context)
            editor.setText("A long note that is already in the field. ".repeat(50))
            editor.setSelection(editor.text.length)
            val info = EditorInfo().apply {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                initialSelStart = editor.text.length; initialSelEnd = editor.text.length
            }
            val connection = CountingConnection(editor.onCreateInputConnection(info)!!)
            ReflectionHelpers.setField(service, "mStartedInputConnection", connection)
            ReflectionHelpers.setField(service, "mInputEditorInfo", info)
            service.onCreateInputView()
            service.onStartInput(info, false)
            service.onStartInputView(info, false)
            ReflectionHelpers.getField<org.akshara.ime.data.PredictionRepository>(service, "prediction").phoneticCandidates("a", emptyList())
            idle()
            block(service, editor, connection)
        } finally {
            service.onFinishInputView(true)
            controller.destroy()
            KeyboardPreferences(context).reset()
            SinhalaEngine.smartPhoneticV2 = true
            SinhalaEngine.smartPhoneticOptions = SmartPhoneticV2.Options()
        }
    }

    /** What the app sends back after each edit, as a real editor would. */
    private fun AksharaInputMethodService.key(editor: EditText, value: String) {
        val before = editor.selectionEnd
        onCharacter(value)
        onUpdateSelection(before, before, editor.selectionStart, editor.selectionEnd, -1, -1)
        idle()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(30))

    @Test fun lettersInAWordStayCheap() = withService { service, editor, connection ->
        service.key(editor, "k")
        connection.reset()
        "ohomada".forEach { service.key(editor, it.toString()) }
        val perKey = connection.total / 7.0
        println("Sinhala reads per key: %.1f %s, whole-document copies %d".format(perKey, connection.reads, connection.wholeDocumentCopies))
        assertEquals("no whole-document copies while typing", 0, connection.wholeDocumentCopies)
        assertTrue("reads per key $perKey ${connection.reads}", perKey <= 3.0)
        assertTrue(editor.text.endsWith("කොහොමද"))
    }

    @Test fun spaceStaysCheapAndStillChoosesTheWord() = withService { service, editor, connection ->
        "honda".forEach { service.key(editor, it.toString()) }
        connection.reset()
        val before = editor.selectionEnd
        service.onSpace()
        service.onUpdateSelection(before, before, editor.selectionStart, editor.selectionEnd, -1, -1)
        idle()
        println("Space reads: ${connection.total} ${connection.reads}, whole-document copies ${connection.wholeDocumentCopies}")
        assertEquals(0, connection.wholeDocumentCopies)
        assertTrue(editor.text.endsWith("හොඳ "))
    }

    @Test fun englishLettersSkipNothingTheyNeed() = withService(english = true) { service, editor, connection ->
        "Hello".forEach { service.key(editor, it.toString()) }
        connection.reset()
        " wor".forEach { if (it == ' ') service.onSpace() else service.key(editor, it.toString()) }
        println("English reads per key: %.1f %s".format(connection.total / 4.0, connection.reads))
        assertEquals(0, connection.wholeDocumentCopies)
        assertTrue(editor.text.endsWith("Hello wor"))
    }
}
