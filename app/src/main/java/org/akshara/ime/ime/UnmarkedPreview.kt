package org.akshara.ime.ime

import android.view.inputmethod.InputConnection

/** An unstyled, committed preview. Replace only text still immediately before our cursor. */
internal class UnmarkedPreview {
    private var text = ""
    private var before = ""
    private var end: Int? = null

    fun clear() { text = ""; before = ""; end = null }

    /** One read from the editor: the cursor is still collapsed right after the text we wrote. */
    fun matches(ic: InputConnection): Boolean {
        if (text.isEmpty()) return true
        if (!before.endsWith(text)) return false
        val around = ic.textAround(before.length, 0) ?: return false
        if (around.selected) return false
        // Older editors (and implementations without a surrounding-text offset) need
        // the extracted cursor position too: identical nearby text is not a cursor anchor.
        val cursor = around.cursor ?: if (end != null) ic.cursorOffset() else null
        if (end != null && cursor != null && cursor != end) return false
        // Editors may return more or less text than asked for: the end must match, and include all of our text
        val got = around.before
        return if (got.length >= before.length) got.endsWith(before) else got.length >= text.length && before.endsWith(got)
    }

    /**
     * Whether a selection update is just the editor reporting our own last edit: a collapsed cursor where we left it.
     * Such updates need no read from the editor.
     */
    fun isOwnEdit(selStart: Int, selEnd: Int): Boolean =
        text.isNotEmpty() && end != null && selStart == selEnd && selEnd == end

    fun replace(ic: InputConnection, value: String, alreadyValidated: Boolean = false): Boolean {
        if (!alreadyValidated && !matches(ic)) return false
        val previousText = text
        val stablePrefix = if (before.endsWith(previousText)) before.dropLast(previousText.length) else ""
        val previousEnd = end
        ic.beginBatchEdit()
        try {
            if (text.isNotEmpty()) ic.deleteSurroundingText(text.length, 0)
            ic.commitText(value, 1)
            text = value
            // Keep the local anchor current. Selection callbacks invalidate it when the host
            // moves the cursor, so later keys need not read it back.
            if (previousText.isEmpty()) {
                val around = ic.textAround(value.length + 64, 0)
                before = around?.before.orEmpty()
                end = around?.cursor ?: ic.cursorOffset()   // a second read only before Android 12
            } else {
                before = stablePrefix + value
                end = previousEnd?.plus(value.length - previousText.length)
            }
        } finally { ic.endBatchEdit() }
        return true
    }
}
