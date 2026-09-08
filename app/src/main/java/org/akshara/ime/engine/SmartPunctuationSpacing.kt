package org.akshara.ime.engine

/** Direct port of iOS `SmartPunctuationSpacing` and smart-quote pairing. */
object SmartPunctuationSpacing {
    data class Adjustment(val deletePrecedingCount: Int, val text: String) {
        companion object {
            fun unchanged(text: String) = Adjustment(0, text)
        }
    }

    enum class FieldKind { STANDARD, SUPPRESSES_SENTENCE_SPACING }

    fun adjustment(
        inserting: String,
        before: String,
        field: FieldKind = FieldKind.STANDARD
    ): Adjustment {
        val first = inserting.firstOrNull() ?: return Adjustment.unchanged(inserting)
        var deletePrecedingCount = 0
        if (canCollapseOpeningSpace(before) && shouldCollapseSpaceAfterOpening(inserting)) {
            deletePrecedingCount = 1
        } else if (isClosing(first) && isCollapsibleSpace(before.lastOrNull())) {
            deletePrecedingCount = 1
        }
        var output = inserting
        if (shouldAppendSentenceSpace(first, field) && !output.endsWith(" ")) {
            val previous = if (deletePrecedingCount > 0) before.dropLast(1).lastOrNull() else before.lastOrNull()
            if (isSentenceBoundary(first, previous, field)) output += " "
        }
        return Adjustment(deletePrecedingCount, output)
    }

    fun applied(
        inserting: String,
        before: String,
        field: FieldKind = FieldKind.STANDARD
    ): String {
        val change = adjustment(inserting, before, field)
        val stem = if (change.deletePrecedingCount > 0) before.dropLast(change.deletePrecedingCount) else before
        return stem + change.text
    }

    fun isOpening(character: Char): Boolean = when (character) {
        '(', '[', '{', '"', '“', '‘', '«', '‹' -> true
        else -> false
    }

    fun shouldCollapseSpaceAfterOpening(inserting: String): Boolean {
        val first = inserting.firstOrNull() ?: return false
        return !first.isWhitespace()
    }

    fun canCollapseOpeningSpace(before: String): Boolean {
        val space = before.lastOrNull() ?: return false
        if (!isCollapsibleSpace(space)) return false
        val opener = before.dropLast(1).lastOrNull() ?: return false
        return isOpening(opener)
    }

    fun hasTrailingSentenceSpace(context: String): Boolean {
        val space = context.lastOrNull() ?: return false
        if (!isCollapsibleSpace(space)) return false
        val terminator = context.dropLast(1).lastOrNull() ?: return false
        return sentenceTerminators.contains(terminator)
    }

    fun smartQuote(text: String, previous: Char?): String {
        if (text != "'" && text != "\"") return text
        val opens = previous == null || previous.isWhitespace() || isPunctuation(previous)
        return when (text) {
            "'" -> if (opens) "‘" else "’"
            else -> if (opens) "“" else "”"
        }
    }

    object DoubleSpace {
        const val WINDOW_MS = 450L

        fun shouldReplace(enabled: Boolean, elapsedMs: Long, before: String): Boolean {
            if (!enabled || elapsedMs >= WINDOW_MS) return false
            if (!before.endsWith(" ")) return false
            val characterBeforeSpace = before.dropLast(1).lastOrNull() ?: return false
            return characterBeforeSpace.isLetter() || characterBeforeSpace.isDigit()
        }
    }

    private val sentenceTerminators = setOf('.', '?', '!', '…', '෴')

    private fun isClosing(character: Char): Boolean = when (character) {
        '.', ',', ';', ':', '!', '?', ')', ']', '}', '”', '’', '»', '›', '…', '෴' -> true
        else -> false
    }

    private fun isCollapsibleSpace(character: Char?): Boolean =
        character == ' ' || character == '\u00A0'

    private fun shouldAppendSentenceSpace(character: Char, field: FieldKind): Boolean =
        field == FieldKind.STANDARD && sentenceTerminators.contains(character)

    private fun isSentenceBoundary(terminator: Char, previous: Char?, field: FieldKind): Boolean {
        if (field != FieldKind.STANDARD || previous == null || previous.isWhitespace()) return false
        return if (terminator == '.') previous.isLetter() else true
    }

    private fun isPunctuation(character: Char): Boolean = when (Character.getType(character)) {
        Character.DASH_PUNCTUATION.toInt(),
        Character.START_PUNCTUATION.toInt(),
        Character.END_PUNCTUATION.toInt(),
        Character.CONNECTOR_PUNCTUATION.toInt(),
        Character.OTHER_PUNCTUATION.toInt(),
        Character.INITIAL_QUOTE_PUNCTUATION.toInt(),
        Character.FINAL_QUOTE_PUNCTUATION.toInt() -> true
        else -> false
    }
}
