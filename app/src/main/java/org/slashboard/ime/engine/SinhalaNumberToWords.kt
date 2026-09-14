package org.slashboard.ime.engine

/**
 * Converts numbers into formal Sinhala words.
 * Highly useful for cheques, banking, official documents, and typing numbers in Sinhala.
 * Example: 2500 -> "දෙදහස් පන්සියයයි"
 */
object SinhalaNumberToWords {

    private val UNITS = arrayOf(
        "", "එක", "දෙක", "තුන", "හතර", "පහ", "හය", "හත", "අට", "නවය",
        "දහය", "එකොළහ", "දොළහ", "දහතුන", "දාහතර", "පහළොව", "දාසය", "දාහත", "දහඅට", "දහනවය"
    )

    private val UNITS_TERMINAL = arrayOf(
        "බිංදුවයි", "එකයි", "දෙකයි", "තුනයි", "හතරයි", "පහයි", "හයයි", "හතයි", "අටයි", "නවයයි",
        "දහයයි", "එකොළහයි", "දොළහයි", "දහතුනයි", "දාහතරයි", "පහළොවයි", "දාසයයි", "දාහතයි", "දහඅටයි", "දහනවයයි"
    )

    private val TENS_PREFIX = arrayOf(
        "", "", "විසි", "තිස්", "හතළිස්", "පනස්", "හැට", "හැත්තෑ", "අසූ", "අනූ"
    )

    private val TENS_TERMINAL = arrayOf(
        "", "", "විස්සයි", "තිහයි", "හතළිහයි", "පනහයි", "හැටයි", "හැත්තෑවයි", "අසූවයි", "අනූවයි"
    )

    private val HUNDREDS_PREFIX = arrayOf(
        "", "එකසිය", "දෙසිය", "තුන්සිය", "හාරසිය", "පන්සිය", "හයසිය", "හත්සිය", "අටසිය", "නමසිය"
    )

    private val HUNDREDS_TERMINAL = arrayOf(
        "", "සියයයි", "දෙසියයයි", "තුන්සියයයි", "හාරසියයයි", "පන්සියයයි", "හයසියයයි", "හත්සියයයි", "අටසියයයි", "නමසියයයි"
    )

    /**
     * Converts an integer or decimal numeric string to Sinhala words.
     * Examples:
     * - "2500" -> "දෙදහස් පන්සියයයි"
     * - "50" -> "පනහයි"
     * - "100" -> "සියයයි"
     * - "2500.50" -> "දෙදහස් පන්සියයයි ශත පනහයි"
     */
    fun convert(input: String): String? {
        val trimmed = input.trim().replace(",", "")
        if (trimmed.isEmpty()) return null

        val parts = trimmed.split(".")
        val integerPartStr = parts[0]
        val integerVal = integerPartStr.toLongOrNull() ?: return null

        val words = convertInteger(integerVal)

        if (parts.size > 1 && parts[1].isNotEmpty()) {
            val centStr = parts[1].take(2).padEnd(2, '0')
            val cents = centStr.toIntOrNull() ?: 0
            if (cents > 0) {
                val centWords = convertSub100(cents, isTerminal = true)
                return "$words ශත $centWords"
            }
        }

        return words
    }

    private fun convertInteger(n: Long): String {
        if (n == 0L) return "බිංදුවයි"
        if (n < 0L) return "ඍණ " + convertInteger(-n)

        val parts = mutableListOf<String>()

        var remaining = n

        // Millions (දසලක්ෂ / මිලියන)
        val millions = remaining / 1_000_000L
        if (millions > 0) {
            remaining %= 1_000_000L
            if (remaining == 0L) {
                if (millions == 1L) return "මිලියනයයි"
                parts.add("${convertSubThousand(millions.toInt(), isTerminal = false)} මිලියනයයි")
                return parts.joinToString(" ")
            } else {
                val prefix = if (millions == 1L) "එක් මිලියන" else "${convertSubThousand(millions.toInt(), isTerminal = false)} මිලියන"
                parts.add(prefix)
            }
        }

        // Lakhs (ලක්ෂ)
        val lakhs = remaining / 100_000L
        if (lakhs > 0) {
            remaining %= 100_000L
            if (remaining == 0L && parts.isEmpty()) {
                if (lakhs == 1L) return "ලක්ෂයයි"
                return "${convertSub100(lakhs.toInt(), isTerminal = false)} ලක්ෂයයි"
            } else if (remaining == 0L) {
                parts.add("${convertSub100(lakhs.toInt(), isTerminal = false)} ලක්ෂයයි")
                return parts.joinToString(" ")
            } else {
                val prefix = if (lakhs == 1L && parts.isEmpty()) "එක්ලක්ෂ" else "${convertSub100(lakhs.toInt(), isTerminal = false)} ලක්ෂ"
                parts.add(prefix)
            }
        }

        // Thousands (දහස්)
        val thousands = (remaining / 1000L).toInt()
        if (thousands > 0) {
            remaining %= 1000L
            if (remaining == 0L && parts.isEmpty()) {
                return when (thousands) {
                    1 -> "එක්දහසයි"
                    2 -> "දෙදහසයි"
                    3 -> "තුන්දහසයි"
                    4 -> "හාරදහසයි"
                    5 -> "පන්දහසයි"
                    6 -> "හයදහසයි"
                    7 -> "හත්දහසයි"
                    8 -> "අටදහසයි"
                    9 -> "නමදහසයි"
                    10 -> "දසදහසයි"
                    else -> "${convertSubThousand(thousands, isTerminal = false)} දහසයි"
                }
            } else if (remaining == 0L) {
                parts.add("${convertThousandsPrefix(thousands)} දහසයි")
                return parts.joinToString(" ")
            } else {
                parts.add(convertThousandsPrefix(thousands))
            }
        }

        // Remainder under 1000
        if (remaining > 0) {
            parts.add(convertSubThousand(remaining.toInt(), isTerminal = true))
        }

        return parts.joinToString(" ")
    }

    private fun convertThousandsPrefix(t: Int): String {
        return when (t) {
            1 -> "එක්දහස්"
            2 -> "දෙදහස්"
            3 -> "තුන්දහස්"
            4 -> "හාරදහස්"
            5 -> "පන්දහස්"
            6 -> "හයදහස්"
            7 -> "හත්දහස්"
            8 -> "අටදහස්"
            9 -> "නමදහස්"
            10 -> "දසදහස්"
            else -> "${convertSubThousand(t, isTerminal = false)} දහස්"
        }
    }

    private fun convertSubThousand(n: Int, isTerminal: Boolean): String {
        if (n < 100) return convertSub100(n, isTerminal)

        val hundreds = n / 100
        val rem = n % 100

        if (rem == 0) {
            return if (isTerminal) HUNDREDS_TERMINAL[hundreds] else HUNDREDS_PREFIX[hundreds]
        }

        return "${HUNDREDS_PREFIX[hundreds]} ${convertSub100(rem, isTerminal)}"
    }

    private fun convertSub100(n: Int, isTerminal: Boolean): String {
        if (n < 20) {
            return if (isTerminal) UNITS_TERMINAL[n] else UNITS[n]
        }
        val tens = n / 10
        val units = n % 10

        if (units == 0) {
            return if (isTerminal) TENS_TERMINAL[tens] else TENS_PREFIX[tens]
        }

        val prefix = TENS_PREFIX[tens]
        val unitStr = if (isTerminal) UNITS_TERMINAL[units] else UNITS[units]
        return "$prefix $unitStr"
    }
}
