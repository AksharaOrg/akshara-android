package org.akshara.ime.data

import android.content.Context
import org.akshara.ime.R
import org.akshara.ime.engine.SinhalaEngine
import org.akshara.ime.engine.SmartPhoneticV2
import kotlin.math.ln

data class Candidate(val text: String, val score: Double)

class PredictionRepository(private val context: Context, private val learning: LocalLearningStore) {
    @Volatile private var loaded = false
    @Volatile private var bigramsReady = false
    private val entries = mutableListOf<Pair<String, Int>>()
    private val frequent = mutableListOf<Pair<String, Int>>()
    private val unigramFrequency = HashMap<String, Int>()
    private val starts = mutableListOf<Pair<String, Int>>()
    private val trigrams = mutableMapOf<String, MutableList<Pair<String, Int>>>()
    private var bigrams: BigramTable? = null
    private var sounds: SoundLexicon? = null

    fun warmup() {
        ensureLoaded()
        if (!bigramsReady) Thread({ ensureBigrams() }, "akshara-bigrams").apply { isDaemon = true; start() }
    }

    @Synchronized private fun ensureLoaded() {
        if (loaded) return
        readPairs(R.raw.sinhala_frequency_model).let { rows ->
            entries.addAll(rows)
            entries.sortBy { it.first }
            rows.forEach { unigramFrequency[it.first] = it.second }
            frequent.addAll(rows.sortedByDescending { it.second }.take(96))
            sounds = SoundLexicon(rows)
        }
        readPairs(R.raw.sinhala_sentence_start_model).let(starts::addAll)
        readNgrams(R.raw.sinhala_trigram_model, trigrams, 3)
        loaded = true
    }

    @Synchronized private fun ensureBigrams() {
        if (bigramsReady) return
        try {
            bigrams = BigramTable.load(context, R.raw.sinhala_next_word_model)
        } catch (_: Throwable) {
            bigrams = null
        }
        bigramsReady = true
    }

    /** Scores words by frequency, the user's own words, and what follows the preceding one or two words. */
    private inner class ContextScore(preceding: List<String>) {
        val previous = preceding.lastOrNull()
        private val earlier = preceding.dropLast(1).lastOrNull()
        val learned = learning.words()
        val learnedNext = previous?.let { learning.followers(it) }.orEmpty()
        val bundledNext = previous?.let { bigrams?.followers(it) }.orEmpty()
        val trigramNext = if (earlier != null && previous != null) trigrams["$earlier\t$previous"].orEmpty() else emptyList()
        private val bundledCounts = HashMap<String, Int>(bundledNext.size).also { counts ->
            bundledNext.forEach { (word, count) -> counts[word] = maxOf(counts[word] ?: 0, count) }
        }
        private val trigramCounts = HashMap<String, Int>(trigramNext.size).also { counts ->
            trigramNext.forEach { (word, count) -> counts[word] = maxOf(counts[word] ?: 0, count) }
        }
        val hasContinuations = previous != null && (bundledNext.isNotEmpty() || learnedNext.isNotEmpty() || trigramNext.isNotEmpty())

        fun score(word: String, frequency: Int, unigramWeight: Double) =
            unigramWeight * ln(frequency.coerceAtLeast(1) + 1.0) +
                learned.getOrDefault(word, 0) +
                learnedNext.getOrDefault(word, 0) * 1.8 +
                ln((bundledCounts[word] ?: 0) + 1.0) * 1.7 +
                ln((trigramCounts[word] ?: 0) + 1.0) * 2.2
    }

    fun candidates(prefix: String, preceding: List<String>, max: Int = 3): List<Candidate> {
        ensureLoaded()
        if (max <= 0) return emptyList()
        val context = ContextScore(preceding)
        val previous = context.previous
        val learned = context.learned
        val learnedNext = context.learnedNext
        val bundledNext = context.bundledNext
        val trigramNext = context.trigramNext
        val hasContinuations = context.hasContinuations
        val ranked = ArrayList<Candidate>(max)
        val considered = HashSet<String>(max * 16)

        fun consider(word: String, frequency: Int, unigramWeight: Double) {
            if (word == prefix) return
            if (prefix.isEmpty() && word == previous) return
            if (!considered.add(word)) return
            val candidate = Candidate(word, context.score(word, frequency, unigramWeight))
            val insertion = ranked.indexOfFirst { candidate.score > it.score || (candidate.score == it.score && candidate.text < it.text) }
                .let { if (it < 0) ranked.size else it }
            if (insertion >= max && ranked.size >= max) return
            ranked.add(insertion, candidate)
            if (ranked.size > max) ranked.removeAt(ranked.lastIndex)
        }

        if (prefix.isEmpty()) {
            if (!hasContinuations) {
                val pool = if (previous == null && starts.isNotEmpty()) starts else frequent
                pool.take(maxOf(max * 8, 24)).forEach { consider(it.first, it.second, 1.0) }
            }
        } else {
            val first = firstIndexAtOrAfter(prefix)
            val last = minOf(entries.size, first + 4_096)
            for (i in first until last) {
                val entry = entries[i]
                if (!SinhalaEngine.hasUnicodeScalarPrefix(entry.first, prefix)) break
                consider(entry.first, entry.second, 1.0)
            }
            learned.forEach { (word, count) ->
                if (SinhalaEngine.hasUnicodeScalarPrefix(word, prefix)) consider(word, count, 1.0)
            }
        }

        val continuationWeight = if (prefix.isEmpty() && hasContinuations) 0.20 else 1.0
        fun matchesPrefix(word: String) = prefix.isEmpty() || SinhalaEngine.hasUnicodeScalarPrefix(word, prefix)
        learnedNext.forEach { (word, count) ->
            if (matchesPrefix(word)) consider(word, unigramFrequency[word] ?: learned.getOrDefault(word, count), continuationWeight)
        }
        bundledNext.forEach { (word, count) ->
            if (matchesPrefix(word)) consider(word, unigramFrequency[word] ?: 0, continuationWeight)
        }
        trigramNext.forEach { (word, count) ->
            if (matchesPrefix(word)) consider(word, unigramFrequency[word] ?: 0, continuationWeight)
        }
        return ranked
    }

    /**
     * Smart Phonetic v2 suggestions for the romanized word being typed: whole words that sound like it
     * (හොඳ for "honda", although the rules spell හොන්ද), then completions, ranked with the preceding words.
     */
    fun phoneticCandidates(roman: String, preceding: List<String>, max: Int = 3): List<String> {
        ensureLoaded()
        val lexicon = sounds ?: return emptyList()
        if (roman.isEmpty() || max <= 0) return emptyList()
        val context = ContextScore(preceding)
        val completions = lexicon.candidates(roman, PHONETIC_POOL, partial = true)
            .sortedByDescending { context.score(it, lexicon.count[it] ?: 0, 1.0) }
        return (phoneticWords(lexicon, roman, context) + completions).distinct().take(max)
    }

    /** The word Space commits in Smart Phonetic v2, or null to keep the rule spelling. */
    fun phoneticChoice(roman: String, preceding: List<String>): String? {
        if (!loaded || roman.isEmpty()) return null
        val lexicon = sounds ?: return null
        return phoneticWords(lexicon, roman, ContextScore(preceding)).firstOrNull()
    }

    /** Whole words that sound like [roman]. An explicit spelling (`kazda`, `aa` …) that is a word stays first. */
    private fun phoneticWords(lexicon: SoundLexicon, roman: String, context: ContextScore): List<String> {
        val exact = lexicon.candidates(roman, PHONETIC_POOL).filter { it in lexicon.count }
        val spelled = SmartPhoneticV2.transliterate(roman)
        val pinned = exact.firstOrNull()?.takeIf { it == spelled && SoundLexicon.isExplicit(roman) }
        val key = SoundLexicon.soundKey(spelled)
        val learned = context.learned.keys.filter { SoundLexicon.soundKey(it) == key }
        val ranked = (exact + learned).distinct().sortedByDescending { context.score(it, lexicon.count[it] ?: 0, 1.0) }
        return listOfNotNull(pinned) + ranked.filter { it != pinned }
    }

    /** Like [prefixEvidence], for a romanized Smart Phonetic v2 prefix. */
    fun phoneticPrefixEvidence(roman: String): Float {
        if (!loaded || roman.isEmpty()) return 0f
        val lexicon = sounds ?: return 0f
        val key = SoundLexicon.soundKey(SmartPhoneticV2.transliterate(roman))
        lexicon.exact(key).maxOfOrNull { lexicon.count[it] ?: 0 }?.let { return ln(it + 1.0).toFloat() }
        val longer = lexicon.prefix(key.removeSuffix(SoundLexicon.HAL), 1).maxOfOrNull { lexicon.count[it] ?: 0 } ?: return 0f
        return ln(longer + 1.0).toFloat() * 0.4f
    }

    fun prefixEvidence(prefix: String): Float {
        if (!loaded || prefix.isEmpty()) return 0f
        val exact = unigramFrequency[prefix]
        if (exact != null) return ln(exact + 1.0).toFloat()
        val index = firstIndexAtOrAfter(prefix)
        if (index < entries.size && SinhalaEngine.hasUnicodeScalarPrefix(entries[index].first, prefix)) {
            return ln(entries[index].second + 1.0).toFloat() * 0.4f
        }
        return 0f
    }

    private companion object {
        const val PHONETIC_POOL = 12
    }

    private fun firstIndexAtOrAfter(prefix: String): Int {
        var lo = 0
        var hi = entries.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (entries[mid].first < prefix) lo = mid + 1 else hi = mid
        }
        return lo
    }

    private fun readPairs(raw: Int): List<Pair<String, Int>> = context.resources.openRawResource(raw).bufferedReader().useLines { lines ->
        lines.filter { it.isNotBlank() && !it.startsWith("#") }.mapNotNull { line ->
            val p = line.split('\t'); if (p.size >= 2) p[0] to (p.last().toIntOrNull() ?: 1) else null
        }.toList()
    }
    private fun readNgrams(raw: Int, target: MutableMap<String, MutableList<Pair<String, Int>>>, width: Int) {
        context.resources.openRawResource(raw).bufferedReader().useLines { lines -> lines.forEach { line ->
            val p = line.split('\t'); if (p.size >= width + 1) {
                val key = p.take(width - 1).joinToString("\t"); target.getOrPut(key) { mutableListOf() }.add(p[width - 1] to (p.last().toIntOrNull() ?: 1))
            }
        } }
    }

    private class BigramTable(private val data: ByteArray, private val ranges: Map<String, IntRange>) {
        fun followers(previous: String): List<Pair<String, Int>> {
            val range = ranges[previous] ?: return emptyList()
            val chunk = String(data, range.first, range.last - range.first + 1, Charsets.UTF_8)
            val found = ArrayList<Pair<String, Int>>(32)
            chunk.lineSequence().forEach { line ->
                if (line.isEmpty()) return@forEach
                val first = line.indexOf('\t')
                val second = if (first >= 0) line.indexOf('\t', first + 1) else -1
                if (second < 0) return@forEach
                val word = line.substring(first + 1, second)
                val count = line.substring(second + 1).toIntOrNull() ?: 1
                found += word to count
            }
            return found
        }

        companion object {
            fun load(context: Context, raw: Int): BigramTable {
                val data = context.resources.openRawResource(raw).use { it.readBytes() }
                val ranges = HashMap<String, IntRange>(32_000)
                var lineStart = 0
                var firstTab = -1
                var groupStart = 0
                var currentKey: String? = null
                fun keyAt(start: Int, tab: Int) = String(data, start, tab - start, Charsets.UTF_8)
                fun finish(lineEnd: Int) {
                    val tab = firstTab
                    firstTab = -1
                    val nextStart = lineEnd + 1
                    if (tab <= lineStart) {
                        lineStart = nextStart
                        return
                    }
                    val key = keyAt(lineStart, tab)
                    if (key != currentKey) {
                        currentKey?.let { ranges[it] = groupStart until lineStart }
                        currentKey = key
                        groupStart = lineStart
                    }
                    lineStart = nextStart
                }
                for (i in data.indices) {
                    when (data[i]) {
                        '\t'.code.toByte() -> if (firstTab < 0) firstTab = i
                        '\n'.code.toByte() -> finish(i)
                    }
                }
                if (lineStart < data.size) finish(data.size)
                currentKey?.let { ranges[it] = groupStart until data.size }
                return BigramTable(data, ranges)
            }
        }
    }
}
