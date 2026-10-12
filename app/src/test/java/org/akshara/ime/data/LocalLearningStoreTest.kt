package org.akshara.ime.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class LocalLearningStoreTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val file get() = File(context.noBackupFilesDir, "learning.json")
    private val legacy get() = context.getSharedPreferences("akshara_learning", Context.MODE_PRIVATE)

    @Before fun fresh() {
        LocalLearningStore(context).clear()
    }

    @Test fun learnsWordsAndBigramsAndClears() {
        val store = LocalLearningStore(context)
        store.record("ලෝකය", "හෙලෝ"); store.record("ලෝකය", "හෙලෝ")
        assertEquals(2, store.words()["ලෝකය"]); assertEquals(2, store.followers("හෙලෝ")["ලෝකය"])
        store.clear(); assertEquals(emptyMap<String, Int>(), store.words())
    }

    @Test fun learnedWordsSurviveARestart() {
        val store = LocalLearningStore(context)
        store.record("ආයුබෝවන්", null); store.record("ලංකාව", "ආයුබෝවන්")
        store.flushNow()
        val reopened = LocalLearningStore(context)
        assertEquals(1, reopened.words()["ආයුබෝවන්"])
        assertEquals(1, reopened.followers("ආයුබෝවන්")["ලංකාව"])
    }

    @Test fun typingDoesNotWriteToDiskOnEveryWord() {
        val store = LocalLearningStore(context)
        repeat(20) { store.record("වචනය$it", null) }
        assertFalse("saved after a pause, not per word", file.exists())
        store.flushNow()
        assertTrue(file.exists())
    }

    @Test fun continuousTypingDefersTheSaveUntilTheLatestPause() {
        val store = LocalLearningStore(context)
        repeat(4) {
            store.record("වචනය$it", null)
            Thread.sleep(600)
        }
        assertFalse("save delay starts at the latest word, not the first", file.exists())
        store.flushNow()
        assertEquals(4, LocalLearningStore(context).words().size)
    }

    @Test fun dataFromOlderVersionsMovesToTheNewFileOnce() {
        legacy.edit()
            .putString("words", """{"මම":5,"යනවා":2}""")
            .putString("bigram:මම", """{"යනවා":3}""")
            .putString("bigram:අපි", """{"යමු":1}""")
            .commit()
        val store = LocalLearningStore(context)
        assertEquals(5, store.words()["මම"])
        assertEquals(3, store.followers("මම")["යනවා"])
        assertTrue(file.exists())
        assertTrue("old preferences are cleared", legacy.all.isEmpty())
        assertEquals(1, LocalLearningStore(context).followers("අපි")["යමු"])
    }

    @Test fun failedMigrationKeepsTheLegacyWordsForARetry() {
        legacy.edit().putString("words", """{"මම":5}""").commit()
        // AtomicFile cannot open its staging file when that path is a directory.
        val staging = File(file.path + ".new")
        assertTrue(staging.mkdir())
        try {
            val store = LocalLearningStore(context)
            assertEquals(5, store.words()["මම"])
            store.flushNow()
            assertFalse(file.exists())
            assertFalse("a failed save must not erase the source", legacy.all.isEmpty())
        } finally { staging.deleteRecursively() }
        val retry = LocalLearningStore(context)
        assertEquals(5, retry.words()["මම"])
        retry.flushNow()
        assertTrue(legacy.all.isEmpty())
        assertEquals(5, LocalLearningStore(context).words()["මම"])
    }

    @Test fun precedingWordsAreCappedKeepingTheMostRecent() {
        val store = LocalLearningStore(context)
        repeat(LocalLearningStore.MAX_CONTEXTS + 50) { store.record("ඊළඟ", "පෙර$it") }
        store.followers("පෙර0")   // nothing kept for the oldest
        assertTrue(store.followers("පෙර0").isEmpty())
        assertEquals(1, store.followers("පෙර${LocalLearningStore.MAX_CONTEXTS + 49}")["ඊළඟ"])
        store.flushNow()
        val saved = JSONObject(file.readText()).getJSONArray("contexts")
        assertEquals(LocalLearningStore.MAX_CONTEXTS, saved.length())
    }

    @Test fun clearingSticksEvenWithAPendingSave() {
        val store = LocalLearningStore(context)
        store.record("රහස", null)
        store.flushNow()
        store.record("තවත්", null)   // a save is now scheduled
        store.clear()
        Thread.sleep(2_500)          // past the save delay
        assertFalse(file.exists())
        assertNull(LocalLearningStore(context).words()["රහස"])
    }

    @Test fun readersGetSnapshotsThatTypingDoesNotChange() {
        val store = LocalLearningStore(context)
        store.record("එක", "පළමු")
        val words = store.words()
        val followers = store.followers("පළමු")
        store.record("දෙක", "පළමු")
        assertNull(words["දෙක"])
        assertNull(followers["දෙක"])
    }
}
