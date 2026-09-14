package org.slashboard.ime.engine

/**
 * Singlish Bulk Paragraph Converter.
 * Takes arbitrary Singlish text or paragraphs and converts them to standard Sinhala script,
 * preserving punctuation, newlines, numbers, and spacing.
 */
object SinglishParagraphConverter {

    private val WORD_REGEX = Regex("""([a-zA-Z~^]+)""")

    /**
     * Converts a full paragraph or selected text from Singlish to Sinhala Unicode.
     */
    fun convert(input: String, mode: InputMode = InputMode.SMART_PHONETIC): String {
        if (input.isBlank()) return input

        // Match contiguous alphabetic/singlish tokens while keeping separators intact
        return WORD_REGEX.replace(input) { matchResult ->
            val token = matchResult.value
            transliterateWord(token, mode)
        }
    }

    private fun transliterateWord(word: String, mode: InputMode): String {
        if (word.isEmpty()) return word
        
        // Use SinhalaEngine to transliterate the phonetic word
        val transliterated = SinhalaEngine.transliterate(word, mode)
        
        // Correct pillam ordering (e.g. kombuva + al-lakuna)
        val pillamCorrected = SinhalaPillamCorrector.correctText(transliterated)
        
        // Check for common orthographic corrections (න/ණ, ල/ළ)
        val orthographyFix = SinhalaOrthographyHelper.COMMON_CORRECTIONS[pillamCorrected]
        return orthographyFix ?: pillamCorrected
    }
}
