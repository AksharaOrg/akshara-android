package org.akshara.ime.data

import android.content.Context
import org.json.JSONArray

/** On-device English completion and conservative correction, ranked with the
 * CC BY-SA wordfreq-en-25000 frequency export. */
class EnglishPredictionRepository(private val context: Context, private val learning: LocalLearningStore) {
    private data class Entry(val word: String, val rank: Int)
    private data class NextWord(val word: String, val frequency: Int)
    @Volatile private var loaded = false
    private val entries = ArrayList<Entry>(25_000)
    private val exact = HashMap<String, Int>(25_000)
    private val deletionIndex = HashMap<String, MutableList<Int>>(100_000)
    private val nextWords = HashMap<String, MutableList<NextWord>>(32_000)

    @Synchronized fun warmup() = load()

    @Synchronized fun candidates(prefix: String, preceding: List<String> = emptyList(), maximum: Int = 3): List<String> {
        load()
        val normalized = prefix.lowercase()
        val previous = preceding.lastOrNull()?.lowercase()
        val learnedNext = previous?.let { learning.followers(it) }.orEmpty()
        val bundledNext = previous?.let { nextWords[it] }.orEmpty()
        val continuations = (learnedNext.entries
            .sortedByDescending { it.value }
            .map { NextWord(it.key.lowercase(), it.value * 10_000) } + bundledNext)
            .asSequence()
            .filter { eligible(it.word) && it.word.startsWith(normalized) }
            .sortedByDescending { it.frequency }
            .map { it.word }
        val learned = learning.words().filterKeys(::eligible).filterKeys { it.lowercase().startsWith(normalized) }
            .entries.sortedByDescending { it.value }.map { it.key }
        if (normalized.isEmpty()) return continuations.distinct().take(maximum.coerceAtLeast(0)).toList()
        val first = entries.binarySearchBy(normalized) { it.word }.let { if (it < 0) -it - 1 else it }
        val bundled = entries.asSequence().drop(first).takeWhile { it.word.startsWith(normalized) }.map { it.word }
        return (continuations + learned.asSequence() + bundled).distinct().take(maximum.coerceAtLeast(0)).toList()
    }

    @Synchronized fun correction(word: String): String? {
        load()
        val normalized = word.lowercase()
        if (normalized.length < 3 || !eligible(normalized) || exact.containsKey(normalized)) return null
        if (learning.words().keys.any { it.equals(normalized, ignoreCase = true) }) return null
        val correction = candidatesAtOneEdit(normalized).singleOrNull()?.word ?: return null
        return if (word.firstOrNull()?.isUpperCase() == true) correction.replaceFirstChar(Char::uppercase) else correction
    }

    private fun load() {
        if (loaded) return
        loaded = true
        val rows = runCatching {
            JSONArray(context.resources.openRawResource(org.akshara.ime.R.raw.english_wordfreq_25000).bufferedReader().use { it.readText() })
        }.getOrNull() ?: return
        for (rank in 0 until rows.length()) {
            val word = rows.optJSONArray(rank)?.optString(0)?.lowercase().orEmpty()
            if (!eligible(word) || exact.containsKey(word)) continue
            exact[word] = entries.size
            entries += Entry(word, rank)
        }
        entries.sortBy { it.word }
        exact.clear()
        entries.forEachIndexed { index, entry -> exact[entry.word] = index }
        entries.forEachIndexed { index, entry -> deletionKeys(entry.word).forEach { key ->
            val values = deletionIndex.getOrPut(key) { ArrayList(2) }
            if (values.size < 12) values += index
        } }
        context.resources.openRawResource(org.akshara.ime.R.raw.english_next_word_model).bufferedReader().useLines { lines ->
            lines.forEach { line ->
                val parts = line.split('\t')
                if (parts.size < 3) return@forEach
                val previous = parts[0].lowercase()
                val next = parts[1].lowercase()
                if (!eligible(previous) || !eligible(next)) return@forEach
                nextWords.getOrPut(previous) { ArrayList(2) } += NextWord(next, parts[2].toIntOrNull() ?: 1)
            }
        }
        // Conversational greetings are sparse in the web/news corpus; keep these common flows on-device.
        conversationalFollowers.forEach { (previous, followers) ->
            val bucket = nextWords.getOrPut(previous) { ArrayList(followers.size) }
            followers.forEach { (next, frequency) -> if (bucket.none { it.word == next }) bucket += NextWord(next, frequency) }
        }
    }

    private fun candidatesAtOneEdit(word: String): List<Entry> {
        val ids = LinkedHashSet<Int>()
        deletionIndex[word]?.let(ids::addAll)
        deletionKeys(word).forEach { key ->
            deletionIndex[key]?.let(ids::addAll)
            exact[key]?.let(ids::add)
        }
        return ids.mapNotNull(entries::getOrNull).filter { oneEditAway(word, it.word) }.sortedBy { it.rank }
    }

    private fun eligible(word: String) = word.length >= 2 && word.all { it in 'a'..'z' }
    private fun deletionKeys(word: String) = word.indices.map { word.removeRange(it, it + 1) }

    private fun oneEditAway(left: String, right: String): Boolean {
        if (kotlin.math.abs(left.length - right.length) > 1) return false
        var a = 0; var b = 0; var edits = 0
        while (a < left.length && b < right.length) {
            if (left[a] == right[b]) { a++; b++; continue }
            if (++edits > 1) return false
            when { left.length > right.length -> a++; right.length > left.length -> b++; else -> { a++; b++ } }
        }
        return edits + (left.length - a) + (right.length - b) <= 1
    }

    private companion object {
        val conversationalFollowers = mapOf(
            "hello" to listOf("how" to 50_000, "there" to 42_000, "everyone" to 16_000),
            "hi" to listOf("how" to 45_000, "there" to 38_000),
            "how" to listOf("are" to 55_000, "do" to 40_000),
            "thank" to listOf("you" to 60_000),
            "good" to listOf("morning" to 30_000, "night" to 30_000, "afternoon" to 18_000)
        )
    }
}
