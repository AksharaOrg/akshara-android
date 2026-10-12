package org.akshara.ime.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.akshara.ime.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
class NextWordTableTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    private fun loadedRepository(): PredictionRepository {
        val repository = PredictionRepository(context, LocalLearningStore(context))
        ReflectionHelpers.callInstanceMethod<Unit>(repository, "ensureBigrams")
        return repository
    }

    @Suppress("UNCHECKED_CAST")
    private fun followers(repository: PredictionRepository, previous: String): List<Pair<String, Int>> {
        val table = ReflectionHelpers.getField<Any>(repository, "bigrams")
        return table.javaClass.getMethod("followers", String::class.java).invoke(table, previous) as List<Pair<String, Int>>
    }

    /** The first group in the bundled file, parsed the simple way. */
    private fun firstGroupFromTheRawFile(): Pair<String, List<Pair<String, Int>>> {
        val lines = context.resources.openRawResource(R.raw.sinhala_next_word_model).bufferedReader().useLines { rows ->
            rows.map { it.split('\t') }.filter { it.size >= 3 }.take(500).toList()
        }
        val key = lines.first()[0]
        return key to lines.takeWhile { it[0] == key }.map { it[1] to (it[2].toIntOrNull() ?: 1) }
    }

    @Test fun mappedTableReadsExactlyWhatTheFileSays() {
        val (key, expected) = firstGroupFromTheRawFile()
        assertEquals(expected, followers(loadedRepository(), key))
    }

    @Test fun theUnpackedCopyIsReusedAndOldCopiesAreRemoved() {
        val directory = context.noBackupFilesDir
        directory.listFiles()?.forEach { it.delete() }
        java.io.File(directory, "next_words_1.tsv").writeText("stale")
        loadedRepository()
        val copies = directory.listFiles()!!.filter { it.name.startsWith("next_words_") }
        assertEquals(1, copies.size)
        assertTrue("an old install's copy is replaced", copies.single().name != "next_words_1.tsv")
        val written = copies.single().lastModified()
        Thread.sleep(20)
        loadedRepository()
        assertEquals("a second load maps the same copy", written, copies.single().lastModified())
    }

    @Test fun nextWordSuggestionsStillUseTheTable() {
        val (key, expected) = firstGroupFromTheRawFile()
        val repository = loadedRepository()
        val suggested = repository.candidates("", listOf(key), 3).map { it.text }
        assertTrue("$suggested should come from $expected", suggested.any { word -> expected.any { it.first == word } })
    }
}
