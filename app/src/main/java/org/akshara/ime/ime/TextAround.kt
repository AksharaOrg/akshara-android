package org.akshara.ime.ime

import android.os.Build
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection

/**
 * The text beside the cursor, read in one call to the editor where Android allows it.
 *
 * Each read from an [InputConnection] blocks until the app's process answers, so typing reads as little as
 * possible. On Android 12+ `getSurroundingText` returns the text, the selection and its document offset at once;
 * `getExtractedText` (used before) copies the whole document instead.
 */
internal data class TextAround(
    val before: String,
    val after: String,
    /** Whether text is selected. */
    val selected: Boolean,
    /** The cursor's offset in the document, when the editor reports it. */
    val cursor: Int?
)

internal fun InputConnection.textAround(before: Int, after: Int): TextAround? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val around = runCatching { getSurroundingText(before, after, 0) }.getOrNull()
        if (around != null) {
            val text = around.text
            val start = around.selectionStart
            val end = around.selectionEnd
            if (start in 0..end && end <= text.length) {
                return TextAround(
                    text.subSequence(0, start).toString(),
                    text.subSequence(end, text.length).toString(),
                    start != end,
                    around.offset.takeIf { it >= 0 }?.plus(end)
                )
            }
        }
    }
    val textBefore = getTextBeforeCursor(before, 0) ?: return null
    return TextAround(
        textBefore.toString(),
        if (after > 0) getTextAfterCursor(after, 0)?.toString().orEmpty() else "",
        !getSelectedText(0).isNullOrEmpty(),
        null
    )
}

/** The cursor's document offset: one small read on Android 12+, a whole-document copy before that. */
internal fun InputConnection.cursorOffset(): Int? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        runCatching { getSurroundingText(0, 0, 0) }.getOrNull()
            ?.takeIf { it.offset >= 0 && it.selectionEnd >= 0 }
            ?.let { return it.offset + it.selectionEnd }
    }
    return getExtractedText(ExtractedTextRequest(), 0)?.let { it.startOffset + it.selectionEnd }
}
