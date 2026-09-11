package org.akshara.ime.ime

import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.junit.Assert.*
import org.junit.Test

class KeyboardPolicyTest {
    @Test fun emojiAlwaysUsesRightmostSlotEvenWithoutWords() {
        assertEquals(listOf(null, null, null), SuggestionRail.present(emptyList(), listOf("😀")).slots)
        assertEquals(listOf("😀"), SuggestionRail.present(emptyList(), listOf("😀")).emoji)
        assertEquals(listOf(null, "word", null), SuggestionRail.present(listOf("word"), listOf("😀")).slots)
        assertEquals(listOf("😀"), SuggestionRail.present(listOf("word"), listOf("😀")).emoji)
        assertEquals(listOf("second", "first", null), SuggestionRail.present(listOf("first", "second", "third"), listOf("😀", "😂")).slots)
        assertEquals(listOf("😀", "😂"), SuggestionRail.present(listOf("first", "second", "third"), listOf("😀", "😂")).emoji)
        assertEquals(listOf("😀"), SuggestionRail.present(emptyList(), listOf("😀", "😀", " ")).emoji)
    }
    @Test fun multilineDoneAndNoEnterActionUseReturn() {
        val info = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            imeOptions = EditorInfo.IME_ACTION_DONE
        }
        assertEquals("↵", AksharaInputMethodService.enterLabel(info))
        assertEquals(EditorInfo.IME_ACTION_NONE, AksharaInputMethodService.enterAction(info))
        info.inputType = InputType.TYPE_CLASS_TEXT
        info.imeOptions = EditorInfo.IME_ACTION_SEND or EditorInfo.IME_FLAG_NO_ENTER_ACTION
        assertEquals("↵", AksharaInputMethodService.enterLabel(info))
    }
    @Test fun literalLettersNeverExposeSinhalaHintsOrAlternates() {
        for (mode in org.akshara.ime.engine.InputMode.values()) {
            for (editor in listOf(EditorLayout.ASCII, EditorLayout.URI, EditorLayout.EMAIL)) {
                val rows = KeyboardLayoutFactory.typingRows(mode, KeyboardLayer.LETTERS, false, false, editor, "none", false, "Go", "English")
                val letters = rows.flatMap { it.keys }.filter { it.id.length == 1 && it.id[0] in 'a'..'z' }
                assertEquals(26, letters.size)
                assertTrue(letters.all { it.hint == null && it.extras.isEmpty() && it.flickOutput == null })
            }
        }
    }
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
        val emailLayout = KeyboardLayoutFactory.place(email, 360f, 52f)
        val uriLayout = KeyboardLayoutFactory.place(uri, 360f, 52f)
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
