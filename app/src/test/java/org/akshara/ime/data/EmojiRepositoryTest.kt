package org.akshara.ime.data

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EmojiRepositoryTest {
    @Test fun metadataOrdersFacesAndClassifiesFoodAndAnimals() {
        val categories = repository.categories
        assertEquals("😀", categories.first().emoji.first())
        assertTrue(categories.first { it.name == EmojiRepository.NATURE }.emoji.contains("🦊"))
        assertTrue(categories.first { it.name == EmojiRepository.FOOD }.emoji.contains("🥑"))
        assertTrue(repository.search("avocado").contains("🥑"))
    }
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

    @Test fun categoriesMatchGboardSectionsIncludingFlags() {
        val names = repository.categories.map { it.name }
        assertEquals(
            listOf(
                "Smileys & People", "Animals & Nature", "Food & Drink", "Activity",
                "Travel & Places", "Objects", "Symbols", "Flags"
            ),
            names
        )
        val flags = repository.categories.first { it.name == "Flags" }.emoji
        assertTrue(flags.contains("🇱🇰"))
        assertTrue(flags.contains("🏁"))
        assertTrue(flags.size > 200)
    }
}
