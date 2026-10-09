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
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private var clock = 1_000_000L
    private lateinit var store: ClipboardHistoryStore

    @Before fun open() {
        context.getSharedPreferences(ClipboardHistoryStore.FILE, 0).edit().clear().commit()
        store = ClipboardHistoryStore(context) { clock }
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

    @Test fun pinMovesAClipOutOfRecentAndUnpinMovesItBack() {
        store.add("keep")
        store.add("pin me")
        store.pin("pin me")
        assertEquals(listOf("pin me"), store.pinnedItems())
        assertEquals(listOf("keep"), store.items())
        store.unpin("pin me")
        assertTrue(store.pinnedItems().isEmpty())
        assertEquals(listOf("pin me", "keep"), store.items())
        store.pin("pin me")
        store.remove("keep")
        assertTrue(store.items().isEmpty())
        store.clearHistory()
        assertEquals(listOf("pin me"), store.pinnedItems())
        store.removePinned("pin me")
        assertTrue(store.pinnedItems().isEmpty())
    }

    @Test fun aDeletedOrClearedClipIsNotSavedAgainFromTheSameCopy() {
        store.capture("now", "copy-1")
        assertEquals(listOf("now"), store.items())
        store.remove("now")
        store.capture("now", "copy-1")   // the keyboard reopens while "now" is still on the system clipboard
        assertTrue(store.items().isEmpty())

        store.capture("again", "copy-2")
        store.clearHistory()
        store.capture("again", "copy-2")
        assertTrue(store.items().isEmpty())

        store.capture("now", "copy-3")   // copying the same text again is a new copy
        assertEquals(listOf("now"), store.items())
    }

    @Test fun capturingAPinnedClipDoesNotDuplicateItInRecent() {
        store.capture("kept", "copy-1")
        store.pin("kept")
        store.capture("kept", "copy-2")
        assertEquals(listOf("kept"), store.pinnedItems())
        assertTrue(store.items().isEmpty())
    }

    @Test fun recentClipsExpireAfterAnHourButPinnedClipsStay() {
        store.add("old")
        store.add("pinned")
        store.pin("pinned")
        clock += ClipboardHistoryStore.RECENT_LIFETIME_MS - 1
        assertEquals(listOf("old"), store.items())
        clock += 2
        assertTrue(store.items().isEmpty())
        assertEquals(listOf("pinned"), store.pinnedItems())
    }

    @Test fun clipsSavedBeforeTimestampsAreKeptAndStartTheirHourNow() {
        context.getSharedPreferences(ClipboardHistoryStore.FILE, 0).edit()
            .putString("items", """["legacy"]""").putString("pinned", """["kept"]""").commit()
        assertEquals(listOf("legacy"), store.items())
        assertEquals(listOf("kept"), store.pinnedItems())
        clock += ClipboardHistoryStore.RECENT_LIFETIME_MS + 1
        assertTrue(store.items().isEmpty())
        assertEquals(listOf("kept"), store.pinnedItems())
    }
}
