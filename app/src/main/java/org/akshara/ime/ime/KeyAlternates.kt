package org.akshara.ime.ime

import org.akshara.ime.engine.InputMode

/** Long-press extras. Base glyph is prepended by the keyboard, Gboard-style. */
internal object KeyAlternates {
    fun extras(identity: String, mode: InputMode, layer: KeyboardLayer, shifted: Boolean): List<Pair<String, String>> {
        if (layer == KeyboardLayer.LETTERS && mode == InputMode.WIJESEKARA) {
            wijesekara(identity)?.let { return it }
        }
        punctuation(identity)?.let { return it.map { value -> value to value } }
        // Wijesekara keys type different letters, so the QWERTY symbol positions don't apply there
        if (layer == KeyboardLayer.LETTERS && mode != InputMode.WIJESEKARA) letterSymbol(identity)?.let { return listOf(it to it) }
        if (layer == KeyboardLayer.NUMBERS || layer == KeyboardLayer.SYMBOLS) {
            numbers(identity)?.let { return it.map { value -> value to value } }
        }
        return emptyList()
    }

    fun hint(identity: String, mode: InputMode, layer: KeyboardLayer): String? {
        if (layer != KeyboardLayer.LETTERS || mode != InputMode.WIJESEKARA) return null
        return wijesekara(identity)?.firstOrNull()?.first
    }

    private fun wijesekara(identity: String): List<Pair<String, String>>? = when (identity) {
        "." -> listOf("ඟ" to "ඟ")
        "c" -> listOf("ඦ" to "ඦ")
        "v" -> listOf("ඬ" to "ඬ")
        "o" -> listOf("ඳ" to "ඳ")
        "r" -> listOf("ර්‍" to "\uE002")
        "x" -> listOf("ඃ" to "ඃ")
        "," -> listOf("ඏ" to "ඏ")
        else -> null
    }

    private fun punctuation(identity: String): List<String>? = when (identity) {
        "." -> listOf(",", ";", ":", "?", "!", "…", "෴")
        "," -> listOf(";", ":", "،")
        "'" -> listOf("‘", "’", "\"")
        "\"" -> listOf("“", "”", "'")
        "?" -> listOf("!", "…", "෴")
        "!" -> listOf("¡", "෴")
        "-" -> listOf("–", "—", "•")
        "[" -> listOf("{", "<")
        "]" -> listOf("}", ">")
        else -> null
    }

    /** Gboard's "long-press for symbols" on the second and third letter rows. */
    private fun letterSymbol(identity: String): String? = when (identity.lowercase()) {
        "a" -> "@"; "s" -> "#"; "d" -> "$"; "f" -> "_"; "g" -> "&"; "h" -> "-"; "j" -> "+"; "k" -> "("; "l" -> ")"
        "z" -> "*"; "x" -> "\""; "c" -> "'"; "v" -> ":"; "b" -> ";"; "n" -> "!"; "m" -> "?"
        else -> null
    }

    private fun numbers(identity: String): List<String>? = when (identity) {
        "1" -> listOf("¹", "½", "⅓", "¼")
        "2" -> listOf("²", "⅔")
        "3" -> listOf("³", "¾")
        "0" -> listOf("⁰", "∅", "º")
        "$" -> listOf("¢", "£", "€", "¥", "₨")
        else -> null
    }
}
