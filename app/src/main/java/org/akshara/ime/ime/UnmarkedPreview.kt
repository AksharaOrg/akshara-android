package org.akshara.ime.ime

import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection

/** An unstyled, committed preview. Replace only text still immediately before our cursor. */
internal class UnmarkedPreview {
    private var text = ""
    private var before = ""
    private var end: Int? = null

    fun clear() { text = ""; before = ""; end = null }

    fun matches(ic: InputConnection): Boolean {
        if (text.isEmpty()) return true
        if (!before.endsWith(text)) return false
        if (!ic.getSelectedText(0).isNullOrEmpty()) return false
        val extracted = ic.getExtractedText(ExtractedTextRequest(), 0)
        if (extracted != null && (extracted.selectionStart != extracted.selectionEnd ||
                end?.let { it != extracted.startOffset + extracted.selectionEnd } == true)) return false
        return ic.getTextBeforeCursor(before.length, 0)?.toString() == before
    }

    fun replace(ic: InputConnection, value: String): Boolean {
        if (!matches(ic)) return false
        ic.beginBatchEdit()
        try {
            if (text.isNotEmpty()) ic.deleteSurroundingText(text.length, 0)
            ic.commitText(value, 1)
            text = value
            before = ic.getTextBeforeCursor(value.length + 64, 0)?.toString().orEmpty()
            end = ic.getExtractedText(ExtractedTextRequest(), 0)?.let { it.startOffset + it.selectionEnd }
        } finally { ic.endBatchEdit() }
        return true
    }
}
