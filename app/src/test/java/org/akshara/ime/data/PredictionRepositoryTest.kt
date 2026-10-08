package org.akshara.ime.data

import androidx.test.core.app.ApplicationProvider
import org.akshara.ime.engine.SinhalaEngine
import org.akshara.ime.engine.SmartPhoneticV2
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PredictionRepositoryTest {
    @Test fun learnedCandidatesKeepTheirRankingInTheChosenStyle() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val learning = LocalLearningStore(context)
        val repository = PredictionRepository(context, learning)
        val cases = listOf(
            Triple("karma", "කර්මා", SmartPhoneticV2.Options(repayaZwj = true)),
            Triple("akshara", "අක්ෂර", SmartPhoneticV2.Options(classical = true)),
            Triple("krura", "කෘර", SmartPhoneticV2.Options(rakaransayaU = true))
        )
        try {
            for ((roman, word, options) in cases) {
                learning.clear()
                repeat(50) { learning.record(word, null) }
                SinhalaEngine.smartPhoneticOptions = options
                val styled = SoundLexicon.restyle(word, options)
                val candidates = repository.phoneticCandidates(roman, emptyList(), 12)
                assertEquals(roman, styled, candidates.first())
                assertFalse(roman, word in candidates)
                assertEquals(roman, styled, repository.phoneticChoice(roman, emptyList()))
            }
        } finally {
            learning.clear()
            SinhalaEngine.smartPhoneticOptions = SmartPhoneticV2.Options()
        }
    }

    @Test fun bundledCandidatesAreRankedAndPrefixSafe() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val repository = PredictionRepository(context, LocalLearningStore(context))
        val candidates = repository.candidates("අක්", emptyList(), 3)
        assertTrue(candidates.isNotEmpty())
        assertTrue(candidates.zipWithNext().all { (a, b) -> a.score >= b.score })
        assertTrue(candidates.all { SinhalaEngine.hasUnicodeScalarPrefix(it.text, "අක්") })
        val next = repository.candidates("", listOf("මේ"), 3)
        assertTrue(next.isNotEmpty())
        assertTrue(next.none { it.text == "මේ" })
    }
}
