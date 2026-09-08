package org.akshara.ime.ime

import android.widget.EditText
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.BaseInputConnection
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UnmarkedPreviewTest {
    @Test fun previewReplacesOnlyItsOwnTextWithoutComposingUnderline() {
        val editor = EditText(ApplicationProvider.getApplicationContext())
        editor.setText("left right")
        editor.setSelection(5)
        val ic = editor.onCreateInputConnection(EditorInfo())!!
        val preview = UnmarkedPreview()
        preview.replace(ic, "ක")
        preview.replace(ic, "කා")
        assertEquals("left කාright", editor.text.toString())
        assertEquals(7, editor.selectionStart)
        assertEquals(-1, BaseInputConnection.getComposingSpanStart(editor.text))
        preview.replace(ic, "")
        assertEquals("left right", editor.text.toString())
    }

    @Test fun cursorInsidePreviewInvalidatesItAndPreservesSuffix() {
        val editor = EditText(ApplicationProvider.getApplicationContext())
        val ic = editor.onCreateInputConnection(EditorInfo())!!
        val preview = UnmarkedPreview()
        preview.replace(ic, "අම්මා")
        editor.setSelection(1)
        assertFalse(preview.matches(ic))
        preview.clear()
        preview.replace(ic, "ක")
        assertEquals("අකම්මා", editor.text.toString())
        assertEquals(2, editor.selectionStart)
    }

    @Test fun selectionAndExternalChangesInvalidatePreview() {
        val editor = EditText(ApplicationProvider.getApplicationContext())
        val ic = editor.onCreateInputConnection(EditorInfo())!!
        val preview = UnmarkedPreview()
        preview.replace(ic, "abc")
        editor.setSelection(0, 3)
        assertFalse(preview.matches(ic))
        editor.setText("xyz")
        editor.setSelection(3)
        assertFalse(preview.matches(ic))
    }
}
