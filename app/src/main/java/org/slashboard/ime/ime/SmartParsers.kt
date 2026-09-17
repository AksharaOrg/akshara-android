package org.slashboard.ime.ime

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.floor

object SmartParsers {
    
    fun parseNIC(nic: String): String? {
        val oldPattern = Regex("^[0-9]{9}[vVxX]$")
        val newPattern = Regex("^[0-9]{12}$")
        
        if (!oldPattern.matches(nic) && !newPattern.matches(nic)) return null
        
        var year = 0
        var days = 0
        
        if (nic.length == 10) {
            year = 1900 + nic.substring(0, 2).toInt()
            days = nic.substring(2, 5).toInt()
        } else {
            year = nic.substring(0, 4).toInt()
            days = nic.substring(4, 7).toInt()
        }
        
        var gender = "Male"
        if (days > 500) {
            gender = "Female"
            days -= 500
        }
        
        if (days < 1 || days > 366) return "Invalid NIC"
        
        // Compute DOB
        var dobDate = LocalDate.of(year, 1, 1).plusDays((days - 1).toLong())
        // Adjust for leap year bug in old NICs
        if (year % 4 != 0 && days > 59) {
            dobDate = dobDate.minusDays(1)
        }
        
        val age = ChronoUnit.YEARS.between(dobDate, LocalDate.now())
        return "$gender | ${dobDate.format(DateTimeFormatter.ISO_DATE)} | ${age}y"
    }

    private val units = arrayOf("", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen")
    private val tens = arrayOf("", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety")

    private fun convertLessThanOneThousand(num: Int): String {
        if (num == 0) return ""
        if (num < 20) return units[num]
        if (num < 100) return tens[num / 10] + (if (num % 10 != 0) " " + units[num % 10] else "")
        return units[num / 100] + " Hundred" + (if (num % 100 != 0) " " + convertLessThanOneThousand(num % 100) else "")
    }

    fun numberToWordsEnglish(numberStr: String): String? {
        val parts = numberStr.split(".")
        val num = parts[0].toLongOrNull() ?: return null
        if (num == 0L && parts.size == 1) return "Zero Rupees Only"

        var words = ""
        var n = num
        if (n >= 10000000) {
            words += convertLessThanOneThousand((n / 10000000).toInt()) + " Crore "
            n %= 10000000
        }
        if (n >= 100000) {
            words += convertLessThanOneThousand((n / 100000).toInt()) + " Lakh "
            n %= 100000
        }
        if (n >= 1000) {
            words += convertLessThanOneThousand((n / 1000).toInt()) + " Thousand "
            n %= 1000
        }
        if (n > 0) {
            words += convertLessThanOneThousand(n.toInt())
        }
        
        var result = (if (words.isBlank()) "" else words.trim() + " Rupees")
        if (parts.size > 1 && parts[1].isNotEmpty()) {
            val cents = (parts[1].take(2).padEnd(2, '0')).toIntOrNull()
            if (cents != null && cents > 0) {
                if (result.isNotEmpty()) result += " and "
                result += convertLessThanOneThousand(cents).trim() + " Cents"
            }
        }
        return (if (result.isEmpty()) "Zero" else result) + " Only"
    }

    // A simplified Sinhala translation (exact linguistic mapping is complex, this provides a close approximation for common cheque amounts)
    fun numberToWordsSinhala(numberStr: String): String? {
        val parts = numberStr.split(".")
        val num = parts[0].toLongOrNull() ?: return null
        if (num == 0L && parts.size == 1) return "රුපියල් බිංදුවයි"
        
        var result = "රුපියල් " + numberToWordsEnglish(numberStr)
            ?.replace(" Only", " පමණි")
            ?.replace(" Rupees", "යි")
            ?.replace(" and ", " ශත ")
            ?.replace(" Cents", "ක්")
            ?.replace("One", "එක")?.replace("Two", "දෙක")?.replace("Three", "තුන්")
            ?.replace("Four", "හතර")?.replace("Five", "පස්")?.replace("Six", "හය")
            ?.replace("Seven", "හත්")?.replace("Eight", "අට")?.replace("Nine", "නව")
            ?.replace("Ten", "දහ")?.replace("Eleven", "එකොළොස්")?.replace("Twelve", "දොළොස්")
            ?.replace("Twenty", "විසි")?.replace("Thirty", "තිස්")?.replace("Forty", "හතළිස්")
            ?.replace("Fifty", "පනස්")?.replace("Hundred", "සිය")?.replace("Thousand", "දහස්")
            ?.replace("Lakh", "ලක්ෂ")?.replace("Crore", "කෝටි")
        
        return result
    }
}
