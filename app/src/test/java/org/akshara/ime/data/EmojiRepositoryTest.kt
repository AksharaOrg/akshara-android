package org.akshara.ime.data

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EmojiRepositoryTest {
    private val repository get() = EmojiRepository(ApplicationProvider.getApplicationContext())

    @Test fun bundledCatalogExposesAllIndexedEmoji() {
        assertTrue(repository.allEmoji.size > 1_500)
        assertTrue(repository.categories.sumOf { it.emoji.size } >= repository.allEmoji.size)
    }

    @Test fun searchRanksUsefulKeywordMatchesAndRejectsBlankQueries() {
        assertTrue(repository.search("").isEmpty())
        assertTrue(repository.search("heart").any { it.contains("❤") || it.contains("💙") })
        assertTrue(repository.search("smile").isNotEmpty())
    }
}
