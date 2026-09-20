package org.akshara.ime.data

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EnglishPredictionRepositoryTest {
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
