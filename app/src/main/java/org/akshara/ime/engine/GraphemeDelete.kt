package org.akshara.ime.engine

import java.text.Normalizer

object GraphemeDelete {
    fun lastCluster(text: String): String {
        if (text.isEmpty()) return ""
        var start = text.length
        fun previous(): Int = text.codePointBefore(start)
        fun step() { start -= Character.charCount(previous()) }
        while (start > 0 && isMark(previous())) step()
        if (start > 0) step() // base scalar
        // Sinhala conjuncts link the preceding consonant through virama + ZWJ.
        while (start > 0 && previous() == 0x200D) {
            step()
            if (start > 0 && previous() == 0x0DCA) step()
            if (start > 0) step()
            while (start > 0 && isMark(previous()) && previous() != 0x0DCA) step()
        }
        return text.substring(start)
    }

    /**
     * One Wijesekara-style backspace inside a Sinhala akshara.
     * පො (කොම්බුව + ඇලපිල්ල) becomes පෙ, then ප, instead of deleting the whole cluster.
     */
    fun reduceAkshara(cluster: String): String? {
        if (cluster.isEmpty()) return null
        val cps = Normalizer.normalize(cluster, Normalizer.Form.NFD).codePoints().toArray().toMutableList()
        if (cps.size < 2) return null
        val last = cps.last()
        when {
            isSinhalaMark(last) -> cps.removeAt(cps.lastIndex)
            isSinhalaConsonant(last) && cps.getOrNull(cps.lastIndex - 1) == 0x200D -> {
                cps.removeAt(cps.lastIndex)
                cps.removeAt(cps.lastIndex)
                if (cps.lastOrNull() == 0x0DCA) cps.removeAt(cps.lastIndex)
            }
            else -> return null
        }
        while (cps.lastOrNull() == 0x200D) cps.removeAt(cps.lastIndex)
        if (cps.isEmpty()) return null
        return Normalizer.normalize(buildString { cps.forEach(::appendCodePoint) }, Normalizer.Form.NFC)
    }

    /** Drop the last typed scalar, decomposing compound vowel signs such as ො → ෙ. */
    fun peelLastScalar(source: String): String {
        if (source.isEmpty()) return ""
        val last = source.codePointBefore(source.length)
        val prefix = source.substring(0, source.length - Character.charCount(last))
        val pieces = Normalizer.normalize(buildString { appendCodePoint(last) }, Normalizer.Form.NFD).codePoints().toArray()
        if (pieces.size <= 1) return prefix
        return prefix + buildString { for (i in 0 until pieces.lastIndex) appendCodePoint(pieces[i]) }
    }
    fun lastWordSegment(text: String): String {
        if (text.isEmpty()) return ""
        var end = text.length
        while (end > 0 && Character.isWhitespace(text.codePointBefore(end))) end -= Character.charCount(text.codePointBefore(end))
        var start = end
        while (start > 0) {
            val cp = text.codePointBefore(start)
            if (Character.isWhitespace(cp) || isPunctuation(cp)) break
            start -= Character.charCount(cp)
        }
        return text.substring(start)
    }

    private fun isMark(cp: Int) = Character.getType(cp) in setOf(
        Character.NON_SPACING_MARK.toInt(), Character.COMBINING_SPACING_MARK.toInt(), Character.ENCLOSING_MARK.toInt()
    )
    private fun isSinhalaMark(cp: Int) = cp in 0x0D82..0x0D83 || cp in 0x0DCA..0x0DFF
    private fun isSinhalaConsonant(cp: Int) = cp in 0x0D9A..0x0DC6
    private fun isPunctuation(cp: Int) = Character.getType(cp) in setOf(
        Character.CONNECTOR_PUNCTUATION.toInt(), Character.DASH_PUNCTUATION.toInt(), Character.START_PUNCTUATION.toInt(),
        Character.END_PUNCTUATION.toInt(), Character.INITIAL_QUOTE_PUNCTUATION.toInt(), Character.FINAL_QUOTE_PUNCTUATION.toInt(), Character.OTHER_PUNCTUATION.toInt()
    )
}
