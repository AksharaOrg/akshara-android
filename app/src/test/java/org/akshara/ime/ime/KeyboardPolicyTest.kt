package org.akshara.ime.ime

import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.junit.Assert.*
import org.junit.Test

class KeyboardPolicyTest {
    @Test fun qwertyGeometryIsCanonical() {
        assertEquals("qwertyuiop", KeyboardView.qwertyRows[0].joinToString(""))
        assertEquals("asdfghjkl", KeyboardView.qwertyRows[1].joinToString(""))
        assertEquals("zxcvbnm", KeyboardView.qwertyRows[2].joinToString(""))
    }
    @Test fun secureAndRestrictiveFieldsSuppressPersonalization() {
        val password = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        val email = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS }
        val number = EditorInfo().apply { inputType = InputType.TYPE_CLASS_NUMBER }
        val normal = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }
        assertTrue(AksharaInputMethodService.isRestrictedEditor(password)); assertTrue(AksharaInputMethodService.isRestrictedEditor(email))
        assertTrue(AksharaInputMethodService.isRestrictedEditor(number)); assertFalse(AksharaInputMethodService.isRestrictedEditor(normal))
    }
    @Test fun enterLabelsFollowEditorActions() {
        assertEquals("Send", AksharaInputMethodService.enterLabel(EditorInfo().apply { imeOptions = EditorInfo.IME_ACTION_SEND }))
        assertEquals("⌕", AksharaInputMethodService.enterLabel(EditorInfo().apply { imeOptions = EditorInfo.IME_ACTION_SEARCH }))
    }
    @Test fun emailAndUriBottomRowsExposeNativePunctuation() {
        val email = KeyboardLayoutFactory.typingRows(
            org.akshara.ime.engine.InputMode.PHONETIC, KeyboardLayer.LETTERS, false, false,
            EditorLayout.EMAIL, "none", false, "Send", "English"
        )
        val uri = KeyboardLayoutFactory.typingRows(
            org.akshara.ime.engine.InputMode.PHONETIC, KeyboardLayer.LETTERS, false, false,
            EditorLayout.URI, "none", false, "Go", "English"
        )
        val emailLayout = KeyboardLayoutFactory.place(email, 360f, 53f, 2.5f, 3f, 4f)
        val uriLayout = KeyboardLayoutFactory.place(uri, 360f, 53f, 2.5f, 3f, 4f)
        assertEquals("@", emailLayout.keyById("@")?.output)
        assertEquals("/", uriLayout.keyById("/")?.output)
        assertEquals(".", emailLayout.keyById(".")?.output)
        assertEquals(".", uriLayout.keyById(".")?.output)
    }
    @Test fun editorLayoutsFollowNativeInputTypes() {
        assertEquals(EditorLayout.NUMBER, AksharaInputMethodService.editorLayout(EditorInfo().apply { inputType = InputType.TYPE_CLASS_NUMBER }))
        assertEquals(EditorLayout.SIGNED_DECIMAL, AksharaInputMethodService.editorLayout(EditorInfo().apply { inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED }))
        assertEquals(EditorLayout.PHONE, AksharaInputMethodService.editorLayout(EditorInfo().apply { inputType = InputType.TYPE_CLASS_PHONE }))
        assertEquals(EditorLayout.EMAIL, AksharaInputMethodService.editorLayout(EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS }))
        assertEquals(EditorLayout.URI, AksharaInputMethodService.editorLayout(EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI }))
    }
}
