package org.akshara.ime.data

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LocalLearningStoreTest {
    @Test fun learnsWordsAndBigramsAndClears() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = LocalLearningStore(context); store.clear(); store.record("ලෝකය", "හෙලෝ"); store.record("ලෝකය", "හෙලෝ")
        assertEquals(2, store.words()["ලෝකය"]); assertEquals(2, store.followers("හෙලෝ")["ලෝකය"])
        store.clear(); assertEquals(emptyMap<String,Int>(), store.words())
    }
}
