package org.slashboard.ime.engine

import java.text.Normalizer

data class PillamCorrection(val deleteCount: Int, val replacement: String)

object SinhalaPillamCorrector {
    private const val KOMBUWA = "\u0DD9" // ෙ
    private const val AELA_PILLA = "\u0DCF" // ා
    private const val AL_LAKUNA = "\u0DCA" // ්
    private const val GAYANUKITTA = "\u0DDF" // ෟ
    private const val KOMBU_DEKA = "\u0DDB" // ෛ
    private const val DIGA_KOMBUWA = "\u0DDA" // ේ
    private const val KOMBUWA_AELA = "\u0DDC" // ො
    private const val KOMBUWA_DIGA_AELA = "\u0DDD" // ෝ
    private const val KOMBUWA_GAYANUKITTA = "\u0DDE" // ෞ
    private const val AEDA_PILLA = "\u0DD0" // ැ
    private const val DIGA_AEDA_PILLA = "\u0DD1" // ෑ
    private const val ISPILLA = "\u0DD2" // ි
    private const val DIGA_ISPILLA = "\u0DD3" // ී
    private const val PAPILLA = "\u0DD4" // ු
    private const val DIGA_PAPILLA = "\u0DD6" // ූ
    private const val GAETA_AEDA = "\u0DD8" // ෘ
    private const val DIGA_GAETA_AEDA = "\u0DF2" // ෲ

    private val VOWEL_SIGNS = setOf(
        "\u0DCF", "\u0DD0", "\u0DD1", "\u0DD2", "\u0DD3", "\u0DD4",
        "\u0DD6", "\u0DD8", "\u0DF2", "\u0DD9", "\u0DDA", "\u0DDB",
        "\u0DDC", "\u0DDD", "\u0DDE", "\u0DDF"
    )

    private fun isSinhalaConsonant(cp: Int): Boolean = cp in 0x0D9A..0x0DC6
    private fun isSinhalaConsonant(text: String): Boolean =
        text.isNotEmpty() && text.codePoints().allMatch { isSinhalaConsonant(it) }

    /**
     * Intercepts typing in real time. If an incoming character combines with or corrects
     * the preceding character (e.g. kombuwa typed before consonant, out-of-order vowel signs),
     * returns how many characters to delete and what text to replace them with.
     */
    fun handleCharacterInput(precedingText: String, incoming: String): PillamCorrection? {
        if (precedingText.isEmpty() || incoming.isEmpty()) return null

        // 1. Kombuwa typed before consonant: "ෙ" followed by "ක" -> replace "ෙ" with "කෙ"
        if (precedingText.endsWith(KOMBUWA)) {
            val beforeKombuwa = precedingText.dropLast(KOMBUWA.length)
            val prevCp = beforeKombuwa.codePoints().toArray().lastOrNull() ?: -1
            val isAttached = prevCp != -1 && (isSinhalaConsonant(prevCp) || prevCp == 0x0DCA || prevCp == 0x200D || prevCp == 0x200C)
            val incomingCp = incoming.codePoints().findFirst().orElse(-1)
            if (!isAttached && isSinhalaConsonant(incomingCp)) {
                // If preceding is an isolated/unattached kombuwa "ෙ", user typed kombuwa before consonant!
                return PillamCorrection(KOMBUWA.length, incoming + KOMBUWA)
            }
            // "ෙ" + "ෙ" -> "ෛ"
            if (incoming == KOMBUWA) {
                return PillamCorrection(KOMBUWA.length, KOMBU_DEKA)
            }
            // "ෙ" + "ා" -> "ො"
            if (incoming == AELA_PILLA) {
                return PillamCorrection(KOMBUWA.length, KOMBUWA_AELA)
            }
            // "ෙ" + "්" -> "ේ"
            if (incoming == AL_LAKUNA) {
                return PillamCorrection(KOMBUWA.length, DIGA_KOMBUWA)
            }
            // "ෙ" + "ෟ" -> "ෞ"
            if (incoming == GAYANUKITTA) {
                return PillamCorrection(KOMBUWA.length, KOMBUWA_GAYANUKITTA)
            }
        }

        // 2. Kombuwa + Aela-pilla + Al-lakuna: "ො" + "්" -> "ෝ"
        if (precedingText.endsWith(KOMBUWA_AELA) && incoming == AL_LAKUNA) {
            return PillamCorrection(KOMBUWA_AELA.length, KOMBUWA_DIGA_AELA)
        }

        // 3. Reverse order: Consonant + "ා" followed by "ෙ" -> turns into Consonant + "ො"
        if (precedingText.endsWith(AELA_PILLA) && incoming == KOMBUWA) {
            return PillamCorrection(AELA_PILLA.length, KOMBUWA_AELA)
        }

        // 4. Independent vowels combined with vowel signs:
        if (precedingText.endsWith("අ")) {
            when (incoming) {
                AELA_PILLA -> return PillamCorrection(1, "ආ")
                AEDA_PILLA -> return PillamCorrection(1, "ඇ")
                DIGA_AEDA_PILLA -> return PillamCorrection(1, "ඈ")
            }
        }
        if (precedingText.endsWith("එ") && incoming == AL_LAKUNA) {
            return PillamCorrection(1, "ඒ")
        }
        if (precedingText.endsWith("ඉ") && (incoming == DIGA_ISPILLA || incoming == ISPILLA)) {
            return PillamCorrection(1, "ඊ")
        }
        if (precedingText.endsWith("උ") && (incoming == GAYANUKITTA || incoming == DIGA_PAPILLA)) {
            return PillamCorrection(1, "ඌ")
        }
        if (precedingText.endsWith("ඔ")) {
            when (incoming) {
                AL_LAKUNA -> return PillamCorrection(1, "ඕ")
                GAYANUKITTA -> return PillamCorrection(1, "ඖ")
            }
        }

        // 5. Conflicting / duplicate diacritics:
        // Consonant + "්" followed by any vowel sign (e.g. "ක්" + "ා" -> "කා", "ක්" + "ි" -> "කි")
        if (precedingText.endsWith(AL_LAKUNA) && incoming in VOWEL_SIGNS) {
            val beforeAl = precedingText.dropLast(AL_LAKUNA.length)
            val lastCp = beforeAl.codePoints().toArray().lastOrNull() ?: -1
            if (isSinhalaConsonant(lastCp)) {
                return PillamCorrection(AL_LAKUNA.length, incoming)
            }
        }

        // Duplicate/upgrade vowel signs (e.g. "ි" + "ී" -> "ී", "ු" + "ූ" -> "ූ", "ැ" + "ෑ" -> "ෑ")
        if (precedingText.endsWith(ISPILLA) && incoming == DIGA_ISPILLA) {
            return PillamCorrection(ISPILLA.length, DIGA_ISPILLA)
        }
        if (precedingText.endsWith(PAPILLA) && incoming == DIGA_PAPILLA) {
            return PillamCorrection(PAPILLA.length, DIGA_PAPILLA)
        }
        if (precedingText.endsWith(AEDA_PILLA) && incoming == DIGA_AEDA_PILLA) {
            return PillamCorrection(AEDA_PILLA.length, DIGA_AEDA_PILLA)
        }

        // Consonant + Vowel sign followed by "්" (e.g. "කි" + "්" -> "ක්")
        if (incoming == AL_LAKUNA) {
            for (sign in VOWEL_SIGNS) {
                if (sign != KOMBUWA && sign != KOMBUWA_AELA && precedingText.endsWith(sign)) {
                    val beforeSign = precedingText.dropLast(sign.length)
                    val lastCp = beforeSign.codePoints().toArray().lastOrNull() ?: -1
                    if (isSinhalaConsonant(lastCp)) {
                        return PillamCorrection(sign.length, AL_LAKUNA)
                    }
                }
            }
        }

        return null
    }

    /**
     * Corrects and normalizes any Sinhala text:
     * - Fixes misplaced kombuwa before consonants (e.g. "ෙක" -> "කෙ")
     * - Fixes decomposed kombuwa sequences ("ෙ" + "ා" -> "ො", "ෙ" + "්" -> "ේ", "ෙ" + "ා" + "්" -> "ෝ")
     * - Fixes reverse vowel signs ("ා" + "ෙ" -> "ො")
     * - Normalizes independent vowels + signs ("අ" + "ා" -> "ආ", etc.)
     * - Fixes conflicting diacritics
     */
    fun correctText(input: String): String {
        if (input.isEmpty()) return input

        val cps = input.codePoints().toArray()
        val out = StringBuilder()
        var i = 0

        while (i < cps.size) {
            val cp = cps[i]

            // Check misplaced kombuwa (0x0DD9) before consonant(s)
            if (cp == 0x0DD9) {
                // If it's already preceded by a consonant (or ZWJ/hal kirima), it's correctly placed.
                val prevCp = out.toString().codePoints().toArray().lastOrNull() ?: -1
                val isAttached = prevCp != -1 && (isSinhalaConsonant(prevCp) || prevCp == 0x0DCA || prevCp == 0x200D || prevCp == 0x200C)
                
                if (isAttached) {
                    // Just append the kombuwa and let the later decomposition rules (like kombuwa + al lakuna) handle it
                    out.appendCodePoint(cp)
                    i++
                    continue
                }

                var kombuwaCount = 0
                while (i < cps.size && cps[i] == 0x0DD9) {
                    kombuwaCount++
                    i++
                }
                if (i < cps.size && isSinhalaConsonant(cps[i])) {
                    val cons = cps[i++]
                    out.appendCodePoint(cons)
                    // Check for consonant conjuncts (ZWJ + consonant like Rakaransaya / Yansaya)
                    while (i + 1 < cps.size && (cps[i] == 0x0DCA || cps[i] == 0x200D) && isSinhalaConsonant(cps[i + 1])) {
                        out.appendCodePoint(cps[i++])
                        out.appendCodePoint(cps[i++])
                    }
                    if (kombuwaCount >= 2) {
                        out.append(KOMBU_DEKA)
                    } else if (i < cps.size) {
                        val next = cps[i]
                        when (next) {
                            0x0DCA -> { out.append(DIGA_KOMBUWA); i++ }
                            0x0DCF -> {
                                i++
                                if (i < cps.size && cps[i] == 0x0DCA) {
                                    out.append(KOMBUWA_DIGA_AELA)
                                    i++
                                } else {
                                    out.append(KOMBUWA_AELA)
                                }
                            }
                            0x0DDF -> { out.append(KOMBUWA_GAYANUKITTA); i++ }
                            else -> out.append(KOMBUWA)
                        }
                    } else {
                        out.append(KOMBUWA)
                    }
                    continue
                } else {
                    repeat(kombuwaCount) { out.append(KOMBUWA) }
                    continue
                }
            }

            // Check independent vowels combined with vowel signs
            if (cp in 0x0D85..0x0D96) {
                val next = cps.getOrNull(i + 1)
                val combined = when (cp to next) {
                    0x0D85 to 0x0DCF -> "ආ"
                    0x0D85 to 0x0DD0 -> "ඇ"
                    0x0D85 to 0x0DD1 -> "ඈ"
                    0x0D91 to 0x0DCA -> "ඒ"
                    0x0D89 to 0x0DD3, 0x0D89 to 0x0DD2 -> "ඊ"
                    0x0D94 to 0x0DCA -> "ඕ"
                    0x0D94 to 0x0DDF, 0x0D94 to 0x0DD6 -> "ඖ"
                    0x0D8B to 0x0DDF, 0x0D8B to 0x0DD6 -> "ඌ"
                    0x0D8D to 0x0DD8 -> "ඎ"
                    else -> null
                }
                if (combined != null) {
                    out.append(combined)
                    i += 2
                    continue
                }
            }

            // Check inverted "ා" + "ෙ" -> "ො"
            if (cp == 0x0DCF && cps.getOrNull(i + 1) == 0x0DD9) {
                i += 2
                if (i < cps.size && cps[i] == 0x0DCA) {
                    out.append(KOMBUWA_DIGA_AELA)
                    i++
                } else {
                    out.append(KOMBUWA_AELA)
                }
                continue
            }

            // Check "ෙ" + "ා" decomposed into "ො"
            if (cp == 0x0DD9 && cps.getOrNull(i + 1) == 0x0DCF) {
                i += 2
                if (i < cps.size && cps[i] == 0x0DCA) {
                    out.append(KOMBUWA_DIGA_AELA)
                    i++
                } else {
                    out.append(KOMBUWA_AELA)
                }
                continue
            }

            // Check "ෙ" + "්" decomposed into "ේ"
            if (cp == 0x0DD9 && cps.getOrNull(i + 1) == 0x0DCA) {
                out.append(DIGA_KOMBUWA)
                i += 2
                continue
            }

            // Check "ො" + "්" decomposed into "ෝ"
            if (cp == 0x0DDC && cps.getOrNull(i + 1) == 0x0DCA) {
                out.append(KOMBUWA_DIGA_AELA)
                i += 2
                continue
            }

            // Check "ෙ" + "ෟ" decomposed into "ෞ"
            if (cp == 0x0DD9 && cps.getOrNull(i + 1) == 0x0DDF) {
                out.append(KOMBUWA_GAYANUKITTA)
                i += 2
                continue
            }

            // Check "ෙ" + "ෙ" decomposed into "ෛ"
            if (cp == 0x0DD9 && cps.getOrNull(i + 1) == 0x0DD9) {
                out.append(KOMBU_DEKA)
                i += 2
                continue
            }

            out.appendCodePoint(cp)
            i++
        }

        return Normalizer.normalize(out.toString(), Normalizer.Form.NFC)
    }
}
