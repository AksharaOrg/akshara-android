package org.akshara.ime.data

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EnglishPredictionRepositoryTest {
    @Test fun commonTyposContractionsAndCaseArePreserved() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val repository = EnglishPredictionRepository(context, LocalLearningStore(context))
        assertEquals("the", repository.correction("teh"))
        assertEquals("THE", repository.correction("TEH"))
        assertEquals("Don't", repository.correction("Dont"))
        assertEquals("I", repository.correction("i"))
        assertTrue(repository.candidates("TE").all { it == it.uppercase() })
        assertEquals("the", repository.candidates("teh").first())
        assertNull(repository.correction("hello"))
    }
    @Test fun wordfreqProvidesRankedCompletionsAndLeavesKnownWordsAlone() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val repository = EnglishPredictionRepository(context, LocalLearningStore(context))
        assertTrue(repository.candidates("the").contains("the"))
        assertNull(repository.correction("the"))
    }

    @Test fun nextWordSuggestionsUseThePrecedingEnglishWord() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val repository = EnglishPredictionRepository(context, LocalLearningStore(context))
        assertTrue(repository.candidates("h", listOf("hello"), 3).contains("how"))
    }
}
