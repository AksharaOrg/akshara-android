package org.akshara.ime.data

import androidx.test.core.app.ApplicationProvider
import org.akshara.ime.engine.SinhalaEngine
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PredictionRepositoryTest {
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
