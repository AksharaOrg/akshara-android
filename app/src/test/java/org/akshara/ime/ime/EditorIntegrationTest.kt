package org.akshara.ime.ime

import android.text.InputType
import android.widget.EditText
import android.view.inputmethod.EditorInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
class EditorIntegrationTest {
    @Test fun typingDeletingAndReplacingSelectionInMiddlePreservesSuffix() {
        val controller = Robolectric.buildService(AksharaInputMethodService::class.java).create()
        val service = controller.get()
        try {
            val editor = EditText(ApplicationProvider.getApplicationContext())
            val info = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }
            val ic = editor.onCreateInputConnection(info)!!
            ReflectionHelpers.setField(service, "mStartedInputConnection", ic)
            service.onStartInput(info, false)
            "amma".forEach { service.onCharacter(it.toString()) }
            val original = editor.text.toString()
            assertTrue(original.isNotEmpty())
            editor.setSelection(1)
            service.onUpdateSelection(original.length, original.length, 1, 1, -1, -1)
            service.onCharacter("k")
            assertTrue(editor.text.toString().endsWith(original.drop(1)))
            assertTrue(editor.selectionStart < editor.text.length)
            service.onBackspace(false)
            assertEquals(original, editor.text.toString())
            editor.setSelection(0, 1)
            service.onBackspace(false)
            assertEquals(original.drop(1), editor.text.toString())
            assertEquals(0, editor.selectionStart)
            info.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            info.imeOptions = EditorInfo.IME_ACTION_DONE
            ReflectionHelpers.setField(service, "mInputEditorInfo", info)
            service.onEnter()
            assertEquals("\n" + original.drop(1), editor.text.toString())
        } finally { controller.destroy() }
    }
}
