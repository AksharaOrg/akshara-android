package org.slashboard.ime.engine

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Live Unit and Currency Converter for Slashboard suggestions.
 * Evaluates queries like "50 USD to LKR", "5 km to m", "100 c to f", "10 kg to g"
 * and returns the converted value directly in the suggestion bar.
 */
object LiveUnitConverter {

    private val CONVERSION_REGEX = Regex(
        """(?i)(?:^|[\s,;])(\d+(?:\.\d+)?)\s*([a-zA-Z$€£¥]+)\s*(?:to|in|=)\s*([a-zA-Z$€£¥]+)\s*$"""
    )

    // Base currency: LKR
    private val CURRENCY_TO_LKR = mapOf(
        "usd" to 300.0,
        "$" to 300.0,
        "lkr" to 1.0,
        "rs" to 1.0,
        "eur" to 328.0,
        "€" to 328.0,
        "gbp" to 388.0,
        "£" to 388.0,
        "aud" to 198.0,
        "cad" to 220.0,
        "jpy" to 2.05,
        "¥" to 2.05,
        "inr" to 3.60,
        "aed" to 81.68,
        "sgd" to 230.0,
        "cny" to 42.0,
        "krw" to 0.22,
        "myr" to 68.0,
        "qar" to 82.4,
        "sar" to 80.0,
        "kwd" to 975.0
    )

    // Base length: Meter (m)
    private val LENGTH_TO_METERS = mapOf(
        "km" to 1000.0,
        "m" to 1.0,
        "cm" to 0.01,
        "mm" to 0.001,
        "mile" to 1609.344,
        "miles" to 1609.344,
        "mi" to 1609.344,
        "yd" to 0.9144,
        "yard" to 0.9144,
        "yards" to 0.9144,
        "ft" to 0.3048,
        "feet" to 0.3048,
        "in" to 0.0254,
        "inch" to 0.0254,
        "inches" to 0.0254
    )

    // Base mass: Gram (g)
    private val MASS_TO_GRAMS = mapOf(
        "kg" to 1000.0,
        "g" to 1.0,
        "mg" to 0.001,
        "lb" to 453.592,
        "lbs" to 453.592,
        "oz" to 28.3495,
        "ton" to 1_000_000.0,
        "tons" to 1_000_000.0
    )

    // Base volume: Liter (l)
    private val VOLUME_TO_LITERS = mapOf(
        "l" to 1.0,
        "liter" to 1.0,
        "liters" to 1.0,
        "litre" to 1.0,
        "litres" to 1.0,
        "ml" to 0.001,
        "gal" to 3.78541,
        "gallon" to 3.78541,
        "gallons" to 3.78541,
        "pt" to 0.473176,
        "pint" to 0.473176
    )

    // Base data: Megabyte (MB)
    private val DATA_TO_MB = mapOf(
        "tb" to 1024.0 * 1024.0,
        "gb" to 1024.0,
        "mb" to 1.0,
        "kb" to 1.0 / 1024.0,
        "byte" to 1.0 / (1024.0 * 1024.0),
        "bytes" to 1.0 / (1024.0 * 1024.0)
    )

    // Base time: Minute (min)
    private val TIME_TO_MINUTES = mapOf(
        "day" to 1440.0,
        "days" to 1440.0,
        "hr" to 60.0,
        "hrs" to 60.0,
        "hour" to 60.0,
        "hours" to 60.0,
        "min" to 1.0,
        "mins" to 1.0,
        "minute" to 1.0,
        "minutes" to 1.0,
        "s" to 1.0 / 60.0,
        "sec" to 1.0 / 60.0,
        "second" to 1.0 / 60.0,
        "seconds" to 1.0 / 60.0
    )

    private val df = DecimalFormat("#,##0.###", DecimalFormatSymbols(Locale.US))

    fun findConversion(text: String): ConversionResult? {
        val match = CONVERSION_REGEX.find(text) ?: return null
        val amount = match.groupValues[1].toDoubleOrNull() ?: return null
        val fromRaw = match.groupValues[2].lowercase()
        val toRaw = match.groupValues[3].lowercase()

        val converted = convert(amount, fromRaw, toRaw) ?: return null
        val fullMatch = match.value.trim()
        val displayUnit = toRaw.uppercase(Locale.US)
        val formatted = "${df.format(converted)} $displayUnit"
        return ConversionResult(query = fullMatch, result = formatted, numericValue = converted)
    }

    private fun convert(amount: Double, from: String, to: String): Double? {
        if (from == to) return amount

        // Currency
        if (CURRENCY_TO_LKR.containsKey(from) && CURRENCY_TO_LKR.containsKey(to)) {
            val fromRate = CURRENCY_TO_LKR[from]!!
            val toRate = CURRENCY_TO_LKR[to]!!
            return (amount * fromRate) / toRate
        }

        // Length
        if (LENGTH_TO_METERS.containsKey(from) && LENGTH_TO_METERS.containsKey(to)) {
            val meters = amount * LENGTH_TO_METERS[from]!!
            return meters / LENGTH_TO_METERS[to]!!
        }

        // Mass
        if (MASS_TO_GRAMS.containsKey(from) && MASS_TO_GRAMS.containsKey(to)) {
            val grams = amount * MASS_TO_GRAMS[from]!!
            return grams / MASS_TO_GRAMS[to]!!
        }

        // Temperature (C, F, K)
        if (isTemperature(from) && isTemperature(to)) {
            return convertTemperature(amount, from, to)
        }

        // Volume
        if (VOLUME_TO_LITERS.containsKey(from) && VOLUME_TO_LITERS.containsKey(to)) {
            val liters = amount * VOLUME_TO_LITERS[from]!!
            return liters / VOLUME_TO_LITERS[to]!!
        }

        // Data
        if (DATA_TO_MB.containsKey(from) && DATA_TO_MB.containsKey(to)) {
            val mb = amount * DATA_TO_MB[from]!!
            return mb / DATA_TO_MB[to]!!
        }

        // Time
        if (TIME_TO_MINUTES.containsKey(from) && TIME_TO_MINUTES.containsKey(to)) {
            val mins = amount * TIME_TO_MINUTES[from]!!
            return mins / TIME_TO_MINUTES[to]!!
        }

        return null
    }

    private fun isTemperature(unit: String) = unit in setOf("c", "f", "k", "celsius", "fahrenheit", "kelvin")

    private fun convertTemperature(value: Double, from: String, to: String): Double {
        val c = when (from) {
            "c", "celsius" -> value
            "f", "fahrenheit" -> (value - 32.0) * (5.0 / 9.0)
            "k", "kelvin" -> value - 273.15
            else -> value
        }
        return when (to) {
            "c", "celsius" -> c
            "f", "fahrenheit" -> (c * (9.0 / 5.0)) + 32.0
            "k", "kelvin" -> c + 273.15
            else -> c
        }
    }

    data class ConversionResult(
        val query: String,
        val result: String,
        val numericValue: Double
    )
}
