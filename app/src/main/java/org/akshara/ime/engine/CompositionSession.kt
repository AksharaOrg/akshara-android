package org.akshara.ime.engine

class CompositionSession {
    var source: String = ""; private set
    var rendered: String = ""; private set
    val active get() = source.isNotEmpty() || rendered.isNotEmpty()

    fun type(key: String, mode: InputMode): String {
        source += key
        rendered = SinhalaEngine.transliterate(source, mode)
        return rendered
    }
    fun backspace(mode: InputMode): String {
        if (source.isNotEmpty()) source = source.dropLast(1)
        rendered = SinhalaEngine.transliterate(source, mode)
        return rendered
    }
    fun replace(text: String) { source = ""; rendered = text }
    fun clear() { source = ""; rendered = "" }
}
