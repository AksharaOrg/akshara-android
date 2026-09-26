package org.akshara.ime.data

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RecentEmojiStoreTest {
    private lateinit var store: RecentEmojiStore

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences(RecentEmojiStore.FILE, android.content.Context.MODE_PRIVATE).edit().clear().commit()
        store = RecentEmojiStore(context)
    }

    @Test fun persistsAndMovesAllEmojiSequencesToTheFront() {
        store.add("❤️")
        store.add("©️")
        store.add("❤️")
        assertEquals(listOf("❤️", "©️"), RecentEmojiStore(ApplicationProvider.getApplicationContext()).items())
    }

    @Test fun capsHistory() {
        repeat(RecentEmojiStore.MAXIMUM_ITEMS + 5) { store.add("emoji-$it") }
        assertEquals(RecentEmojiStore.MAXIMUM_ITEMS, store.items().size)
        assertEquals("emoji-${RecentEmojiStore.MAXIMUM_ITEMS + 4}", store.items().first())
    }
}
