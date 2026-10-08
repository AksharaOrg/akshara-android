package org.akshara.ime.ime

import org.akshara.ime.engine.InputMode
import org.akshara.ime.engine.SinhalaEngine

/** Search composition stays local and never changes the host editor or typing language. */
internal class EmojiSearchQuery {
    private var committed = ""
    private var source = ""
    private var mode = InputMode.SMART_PHONETIC
    var english = true
        private set
    val text: String get() = committed + when {
        english -> source
        mode == InputMode.WIJESEKARA -> SinhalaEngine.normalizeSls(source)
        else -> SinhalaEngine.transliterate(source, mode)
    }

    fun setLanguage(mode: InputMode, english: Boolean) {
        committed = text
        source = ""
        this.mode = mode
        this.english = english
    }

    fun append(value: String, letters: Boolean) {
        if (letters && value != " ") source += value
        else { committed = text + value; source = "" }
    }

    fun delete(word: Boolean) {
        if (word) { clear(); return }
        if (source.isNotEmpty()) source = source.substring(0, source.offsetByCodePoints(source.length, -1))
        else if (committed.isNotEmpty()) committed = committed.substring(0, committed.offsetByCodePoints(committed.length, -1))
    }

    fun clear() { committed = ""; source = "" }
}
