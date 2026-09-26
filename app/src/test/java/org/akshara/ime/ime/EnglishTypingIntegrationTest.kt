package org.akshara.ime.ime

import android.content.Context
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnectionWrapper
import android.widget.EditText
import androidx.test.core.app.ApplicationProvider
import org.akshara.ime.settings.KeyboardPreferences
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
class EnglishTypingIntegrationTest {
    private fun withEditor(type: Int = InputType.TYPE_CLASS_TEXT, action: Int = EditorInfo.IME_ACTION_NONE,
                           forceZeroCaps: Boolean = false,
                           test: (AksharaInputMethodService, EditText, KeyboardView, MutableList<Int>) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences(KeyboardPreferences.FILE, 0).edit().clear().commit()
        KeyboardPreferences(context).persistentEnglish = true
        val controller = Robolectric.buildService(AksharaInputMethodService::class.java).create()
        val service = controller.get()
        try {
            val editor = EditText(context).apply { inputType = type }
            val info = EditorInfo()
            val base = editor.onCreateInputConnection(info)!!
            info.inputType = type
            info.imeOptions = action
            val actions = mutableListOf<Int>()
            val connection = object : InputConnectionWrapper(base, false) {
                override fun performEditorAction(code: Int): Boolean { actions += code; return true }
                override fun getCursorCapsMode(reqModes: Int): Int = if (forceZeroCaps) 0 else super.getCursorCapsMode(reqModes)
                override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence? =
                    if (forceZeroCaps) editor.text.takeLast(n) else super.getTextBeforeCursor(n, flags)
            }
            ReflectionHelpers.setField(service, "mStartedInputConnection", connection)
            ReflectionHelpers.setField(service, "mInputEditorInfo", info)
            val view = service.onCreateInputView() as KeyboardView
            service.onStartInput(info, false)
            service.onStartInputView(info, false)
            test(service, editor, view, actions)
        } finally { service.onFinishInputView(true); controller.destroy() }
    }

    private fun layout(view: KeyboardView) {
        view.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, 1080, 900)
    }

    @Test fun fastSentenceTypingConsumesShiftBeforeTheNextLayoutFrame() = withEditor { _, editor, view, _ ->
        layout(view)
        val panel = ReflectionHelpers.getField<KeyboardPanel>(view, "panel")
        // No layout pass between taps: queued touch events may arrive in the same frame.
        "hello".forEach { tap(panel, it.toString()) }
        assertEquals("Hello", editor.text.toString())
        tap(panel, "."); tap(panel, "space")
        "there".forEach { tap(panel, it.toString()) }
        assertEquals("Hello. There", editor.text.toString())
    }

    private fun tap(panel: KeyboardPanel, id: String) {
        val key = panel.layout!!.keyById(id)!!
        val x = key.geometricCenterX
        val y = key.geometricCenterY
        val now = android.os.SystemClock.uptimeMillis()
        for (action in listOf(android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_UP)) {
            val event = android.view.MotionEvent.obtain(now, now, action, x, y, 0)
            panel.onTouchEvent(event)
            event.recycle()
        }
    }

    @Test fun fastTypingHonorsManualShiftAndCapsLock() = withEditor { service, editor, view, _ ->
        service.onCharacter("x ")
        layout(view)
        val panel = ReflectionHelpers.getField<KeyboardPanel>(view, "panel")
        tap(panel, "shift")
        tap(panel, "a"); tap(panel, "b")
        assertEquals("x Ab", editor.text.toString())
        tap(panel, "shift"); tap(panel, "shift")
        tap(panel, "c"); tap(panel, "d")
        assertEquals("x AbCD", editor.text.toString())
    }

    @Test fun capitalsOnlyEditorsRetainUppercaseDuringFastTyping() = withEditor(
        InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
    ) { _, editor, view, _ ->
        layout(view)
        val panel = ReflectionHelpers.getField<KeyboardPanel>(view, "panel")
        tap(panel, "a"); tap(panel, "b")
        assertEquals("AB", editor.text.toString())
    }

    @Test fun correctionCanBeUndoneAndRejected() = withEditor { service, editor, _, _ ->
        "teh".forEach { service.onCharacter(it.toString()) }
        service.onSpace()
        assertEquals("the ", editor.text.toString())
        service.onBackspace(false)
        assertEquals("teh", editor.text.toString())
        service.onSpace()
        assertEquals("teh ", editor.text.toString())
    }

    @Test fun correctionDoesNotSwallowDoneOrInsertNewline() = withEditor(
        InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE, EditorInfo.IME_ACTION_DONE
    ) { service, editor, _, actions ->
        "teh".forEach { service.onCharacter(it.toString()) }
        service.onEnter()
        assertEquals("the", editor.text.toString())
        assertEquals(listOf(EditorInfo.IME_ACTION_DONE), actions)
    }

    @Test fun pasteIsExactAndEnglishApostrophesAndPeriodsStayLiteral() = withEditor { service, editor, _, _ ->
        service.onPasteText("a")
        assertEquals("a", editor.text.toString())
        editor.setText("")
        "don't".forEach { service.onCharacter(it.toString()) }
        service.onSpace()
        assertEquals("don't ", editor.text.toString())
        service.onPasteText("https://example.com/a?b=1  ")
        assertEquals("don't https://example.com/a?b=1  ", editor.text.toString())
    }

    @Test fun englishShiftFollowsSentenceBoundariesAndAccentsAreAvailable() = withEditor { service, editor, view, _ ->
        layout(view)
        assertEquals("Akshara - English", view.typingLayout()!!.keyById("space")!!.label)
        assertEquals("A", view.typingLayout()!!.keyById("a")!!.output)
        assertTrue(view.typingLayout()!!.keyById("a")!!.extras.any { it.second == "Á" })
        service.onCharacter("Hello")
        layout(view)
        assertEquals("a", view.typingLayout()!!.keyById("a")!!.output)
        service.onCharacter(".")
        service.onSpace()
        layout(view)
        assertEquals("A", view.typingLayout()!!.keyById("a")!!.output)
        assertEquals("Hello. ", editor.text.toString())
    }

    @Test fun emptyEditorCapitalizesEvenWhenEditorReportsNoCaps() = withEditor(forceZeroCaps = true) { service, editor, view, _ ->
        layout(view)
        assertEquals("A", view.typingLayout()!!.keyById("a")!!.output)
        assertEquals("1", view.typingLayout()!!.keyById("q")!!.hint)
        assertTrue(view.typingLayout()!!.keyById("q")!!.extras.any { it.second == "1" })
        service.onCharacter("Hello")
        assertEquals("Hello", editor.text.toString())
        assertEquals("Hello", service.currentInputConnection.getTextBeforeCursor(128, 0).toString())
        assertEquals(0, service.currentInputConnection.getCursorCapsMode(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES))
        layout(view)
        assertEquals("a", view.typingLayout()!!.keyById("a")!!.output)
        service.onCharacter(".")
        service.onSpace()
        layout(view)
        assertEquals("A", view.typingLayout()!!.keyById("a")!!.output)
        assertEquals("Hello. ", editor.text.toString())
    }

    @Test fun noSuggestionsStillAllowsSentenceCapitalization() = withEditor(
        InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS, forceZeroCaps = true
    ) { _, _, view, _ ->
        layout(view)
        assertEquals("A", view.typingLayout()!!.keyById("a")!!.output)
    }

    @Test fun urlKeepsSlashAndLanguageKeyInBothLanguages() = withEditor(
        InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI, EditorInfo.IME_ACTION_GO
    ) { service, editor, view, _ ->
        layout(view)
        assertNotNull(view.typingLayout()!!.keyById("/"))
        assertNotNull(view.typingLayout()!!.keyById("language"))
        assertTrue(view.typingLayout()!!.keyById("language")!!.logical.left < view.typingLayout()!!.keyById("/")!!.logical.left)
        assertEquals("a", view.typingLayout()!!.keyById("a")!!.output)
        service.onLanguageSwitch()
        layout(view)
        assertNotNull(view.typingLayout()!!.keyById("/"))
        assertNotNull(view.typingLayout()!!.keyById("language"))
        assertTrue(view.typingLayout()!!.keyById("language")!!.logical.left < view.typingLayout()!!.keyById("/")!!.logical.left)
        "amma".forEach { service.onCharacter(it.toString()) }
        assertTrue(editor.text.any { it in '\u0D80'..'\u0DFF' })
    }
}
