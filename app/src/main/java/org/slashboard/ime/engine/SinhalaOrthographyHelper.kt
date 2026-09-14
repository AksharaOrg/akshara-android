package org.slashboard.ime.engine

object SinhalaOrthographyHelper {
    private const val D_NA = 'න' // \u0DB1
    private const val M_NA = 'ණ' // \u0DAB
    private const val D_LA = 'ල' // \u0DBD
    private const val M_LA = 'ළ' // \u0DC5

    /**
     * Highly common Sinhala orthographic misspellings and their standard dictionary corrections.
     * Covers common confusion between dantaja/murdhaja "න/ණ" and "ල/ළ".
     */
    val COMMON_CORRECTIONS = mapOf(
        // පැමිණියා / පැමිණීම (න/ණ)
        "පැමිනියා" to "පැමිණියා",
        "පැමිනියාය" to "පැමිණියාය",
        "පැමිනියේ" to "පැමිණියේ",
        "පැමිනෙනවා" to "පැමිණෙනවා",
        "පැමිනෙයි" to "පැමිණෙයි",
        "පැමින" to "පැමිණ",
        "පැමිනීම" to "පැමිණීම",
        "පැමිනෙන්න" to "පැමිණෙන්න",
        "පැමිනුනා" to "පැමිණියා",

        // කරුණාව (න/ණ)
        "කරුනාව" to "කරුණාව",
        "කරුනව" to "කරුණාව",
        "කරුනාවන්ත" to "කරුණාවන්ත",
        "කරුනාවෙන්" to "කරුණාවෙන්",
        "කරුනවෙන්" to "කරුණාවෙන්",
        "කරුනාකර" to "කරුණාකර",
        "කාරුනික" to "කාරුණික",
        "කරුනාවන්තව" to "කරුණාවන්තව",

        // ගුණ (න/ණ)
        "ගුන" to "ගුණ",
        "ගුනය" to "ගුණය",
        "ගුනාත්මක" to "ගුණාත්මක",
        "ගුනාංග" to "ගුණාංග",

        // මරණය (න/ණ)
        "මරනය" to "මරණය",
        "මරනයට" to "මරණයට",
        "මරනින්" to "මරණින්",

        // කල්‍යාණය (න/ණ and ල/ළ)
        "කල්‍යානය" to "කල්‍යාණය",
        "කල්යානය" to "කල්‍යාණය",
        "කල්‍යානි" to "කල්‍යාණි",

        // ළමයා (ල/ළ)
        "ලමයා" to "ළමයා",
        "ලමයි" to "ළමයි",
        "ලමයින්" to "ළමයින්",
        "ලමා" to "ළමා",
        "ලපටි" to "ළපටි",
        "ලමාපිටිය" to "ළමාපිටිය",
        "ලපටියා" to "ළපටියා",
        "ලදරුවා" to "ළදරුවා",
        "ලදරු" to "ළදරු",

        // පිළිබඳ (ල/ළ)
        "පිලිබඳ" to "පිළිබඳ",
        "පිලිබඳව" to "පිළිබඳව",
        "පිලිබද" to "පිළිබඳ",
        "පිලිබදව" to "පිළිබඳව",
        "පිලිබඳවත්" to "පිළිබඳවත්",

        // එළිය (ල/ළ)
        "එලිය" to "එළිය",
        "එලියට" to "එළියට",
        "එලියෙන්" to "එළියෙන්",
        "එලිදරව්" to "එළිදරව්",
        "එලිමහන්" to "එළිමහන්",

        // දෙපළ (ල/ළ)
        "දෙපල" to "දෙපළ",

        // කළමනාකරණය
        "කලමනාකරනය" to "කළමනාකරණය",
        "කලමණාකරනය" to "කළමනාකරණය",
        "කළමණාකරනය" to "කළමනාකරණය",
        "කලමනාකරණ" to "කළමනාකරණ",
        "කලමණාකරණ" to "කළමනාකරණ",

        // තීරණය (න/ණ)
        "තීරනය" to "තීරණය",
        "තීරන" to "තීරණ",
        "තීරනයක්" to "තීරණයක්",

        // ප්‍රමාණය (න/ණ)
        "ප්‍රමානය" to "ප්‍රමාණය",
        "ප්‍රමාන" to "ප්‍රමාණ",
        "ප්‍රමානවත්" to "ප්‍රමාණවත්",

        // ගණනය (න/ණ)
        "ගනනය" to "ගණනය",
        "ගනන්" to "ගණන්",

        // කාරණය (න/ණ)
        "කාරනය" to "කාරණය",
        "කාරනා" to "කාරණා",

        // විකිරණ (න/ණ)
        "විකිරන" to "විකිරණ",

        // ආභරණ (න/ණ)
        "ආභරන" to "ආභරණ",

        // තරුණ (න/ණ)
        "තරුන" to "තරුණ",
        "තරුනයා" to "තරුණයා",
        "තරුනිය" to "තරුණිය",

        // පරීක්ෂණය (න/ණ)
        "පරීක්ෂනය" to "පරීක්ෂණය",
        "පරීක්ෂන" to "පරීක්ෂණ",
        "නිරීක්ෂනය" to "නිරීක්ෂණය",
        "නිරීක්ෂන" to "නිරීක්ෂණ",

        // කළනය (ල/ළ)
        "කලනය" to "කළනය",

        // දියුණුව (න/ණ)
        "දියුනුව" to "දියුණුව",
        "දියුනු" to "දියුණු",
        "දියුනුවක්" to "දියුණුවක්",

        // ගණිතය (න/ණ)
        "ගනිතය" to "ගණිතය",
        "ගනිත" to "ගණිත",

        // ප්‍රවීණ (න/ණ)
        "ප්‍රවීන" to "ප්‍රවීණ",
        "ප්‍රවීනයා" to "ප්‍රවීණයා",

        // වීණාව (න/ණ)
        "වීනාව" to "වීණාව",

        // සරණ (න/ණ)
        "සරන" to "සරණ",

        // එළවළු (ල/ළ)
        "එලවලු" to "එළවළු",
        "එළවලු" to "එළවළු",
        "එලවළු" to "එළවළු",

        // කුළුඳුල් (ල/ළ)
        "කුලුදුල්" to "කුළුඳුල්",
        "කුලුඳුල්" to "කුළුඳුල්",

        // කාරුණික (න/ණ)
        "කාරුනිකව" to "කාරුණිකව",

        // ආයෝජන vs ආයෝජන, සංවිධාන
        "අවස්තාව" to "අවස්ථාව",
        "විසදුම" to "විසඳුම",
        "විසදුම්" to "විසඳුම්"
    )

    /**
     * Checks if the word contains letters prone to "න/ණ" or "ල/ළ" confusion.
     */
    fun hasConfusableLetters(word: String): Boolean {
        for (ch in word) {
            if (ch == D_NA || ch == M_NA || ch == D_LA || ch == M_LA) return true
        }
        return false
    }

    /**
     * Generates all orthographic variants by swapping confusable letters (න<->ණ, ල<->ළ).
     */
    fun generateOrthographicVariants(word: String): List<String> {
        if (!hasConfusableLetters(word) || word.length > 20) return emptyList()

        val results = mutableListOf<String>()

        fun swap(ch: Char): Char = when (ch) {
            D_NA -> M_NA
            M_NA -> D_NA
            D_LA -> M_LA
            M_LA -> D_LA
            else -> ch
        }

        // 1. Single-letter swaps
        for (i in word.indices) {
            val original = word[i]
            val swapped = swap(original)
            if (swapped != original) {
                val variant = word.substring(0, i) + swapped + word.substring(i + 1)
                results.add(variant)
            }
        }

        // 2. All-letter swap (e.g. if a word has both ල and න, like කල්‍යානය -> කල්‍යාණය)
        val allSwapped = StringBuilder(word.length)
        var changed = false
        for (ch in word) {
            val sw = swap(ch)
            if (sw != ch) changed = true
            allSwapped.append(sw)
        }
        if (changed) {
            results.add(allSwapped.toString())
        }

        return results.distinct().filter { it != word }
    }

    /**
     * Finds the best orthographic correction for the given prefix/word.
     * Uses dictionary frequency to determine if the variant is genuinely the correct form.
     */
    fun findCorrection(prefix: String, lookupFrequency: (String) -> Int?): String? {
        if (prefix.length < 2) return null

        // 1. Fast path: Direct dictionary of known common confusions
        COMMON_CORRECTIONS[prefix]?.let { direct ->
            if (direct != prefix) return direct
        }

        if (!hasConfusableLetters(prefix)) return null

        // 2. Dynamic generation and validation against vocabulary frequency
        val variants = generateOrthographicVariants(prefix)
        if (variants.isEmpty()) return null

        val currentFreq = lookupFrequency(prefix) ?: 0
        var bestCandidate: String? = null
        var bestFreq = 0

        for (variant in variants) {
            val freq = lookupFrequency(variant) ?: 0
            if (freq > 0) {
                // If the user's typed word is not in the dictionary at all (currentFreq == 0),
                // or if the corrected form has significantly higher frequency:
                val isSignificantlyBetter = if (currentFreq == 0) true else freq > (currentFreq * 1.4)
                if (isSignificantlyBetter && freq > bestFreq) {
                    bestFreq = freq
                    bestCandidate = variant
                }
            }
        }

        return bestCandidate
    }
}
