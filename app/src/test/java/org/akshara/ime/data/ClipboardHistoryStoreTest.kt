package org.akshara.ime.data

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ClipboardHistoryStoreTest {
    private lateinit var store: ClipboardHistoryStore

    @Before fun open() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences(ClipboardHistoryStore.FILE, 0).edit().clear().commit()
        store = ClipboardHistoryStore(context)
    }

    @Test fun newestClipMovesToTheFrontAndDedupes() {
        store.add("alpha")
        store.add("beta")
        store.add("alpha")
        assertEquals(listOf("alpha", "beta"), store.items())
    }

    @Test fun blankClipsAreIgnoredAndLongClipsAreTrimmed() {
        store.add("   ")
        store.add("x".repeat(3000))
        assertEquals(1, store.items().size)
        assertEquals(ClipboardHistoryStore.MAXIMUM_LENGTH, store.items()[0].length)
    }

    @Test fun pinMovesAClipOutOfRecentHistory() {
        store.add("keep")
        store.add("pin me")
        store.pin(0)
        assertEquals(listOf("pin me"), store.pinnedItems())
        assertEquals(listOf("keep"), store.items())
        store.remove(0)
        assertTrue(store.items().isEmpty())
        store.clearHistory()
        assertEquals(listOf("pin me"), store.pinnedItems())
        store.removePinned(0)
        assertTrue(store.pinnedItems().isEmpty())
    }
}
