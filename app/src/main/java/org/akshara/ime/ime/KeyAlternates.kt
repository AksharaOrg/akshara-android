package org.akshara.ime.ime

import org.akshara.ime.engine.InputMode

/** Long-press extras. Base glyph is prepended by the keyboard, Gboard-style. */
internal object KeyAlternates {
    fun extras(identity: String, mode: InputMode, layer: KeyboardLayer, shifted: Boolean): List<Pair<String, String>> {
        if (layer == KeyboardLayer.LETTERS && mode == InputMode.WIJESEKARA) {
            wijesekara(identity)?.let { return it }
        }
        punctuation(identity)?.let { return it.map { value -> value to value } }
        if (layer == KeyboardLayer.LETTERS) latin(identity, shifted)?.let { return it.map { value -> value to value } }
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

    private fun latin(identity: String, shifted: Boolean): List<String>? {
        val values = when (identity.lowercase()) {
            "a" -> listOf("à", "á", "â", "ä", "æ", "ã", "å", "ā")
            "e" -> listOf("è", "é", "ê", "ë", "ē", "ė", "ę")
            "i" -> listOf("ì", "í", "î", "ï", "ī")
            "o" -> listOf("ò", "ó", "ô", "ö", "ø", "õ", "œ", "ō")
            "u" -> listOf("ù", "ú", "û", "ü", "ū")
            "c" -> listOf("ç", "ć", "č")
            "n" -> listOf("ñ", "ń")
            "s" -> listOf("ß", "ś", "š")
            "y" -> listOf("ÿ")
            else -> null
        } ?: return null
        return if (shifted) values.map { it.uppercase() } else values
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
