package org.akshara.ime.data

import android.content.Context
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.BreakIterator
import java.util.Locale

/**
 * Offline, conservative spelling correction shared with the iOS keyboard's
 * verified-word lexicon. A word is corrected only when it has one unambiguous
 * verified candidate within one grapheme edit.
 */
class SinhalaAutocorrection(private val context: Context) {
    data class Candidate(val text: String, val frequency: Int)
    private data class Entry(val text: String, val frequency: Int)

    @Volatile private var loaded = false
    private val entries = ArrayList<Entry>()
    private val exact = HashMap<String, Int>()
    private val deletionIndex = HashMap<String, MutableList<Int>>()

    @Synchronized fun warmup() = load()

    @Synchronized fun suggestions(word: String, maximum: Int = 3): List<Candidate> {
        load()
        val normalized = normalize(word)
        if (!eligible(normalized) || exact.containsKey(normalized)) return emptyList()
        return candidates(normalized).take(maximum.coerceAtLeast(0)).map { Candidate(it.text, it.frequency) }
    }

    @Synchronized fun correction(word: String): String? {
        load()
        val normalized = normalize(word)
        if (!eligible(normalized) || exact.containsKey(normalized)) return null
        return candidates(normalized).singleOrNull()?.text
    }

    private fun load() {
        if (loaded) return
        loaded = true
        val bytes = runCatching { context.resources.openRawResource(org.akshara.ime.R.raw.sinhala_autocorrect).use { it.readBytes() } }.getOrNull() ?: return
        val magic = "AKSHARA_AUTOCORRECT_V1\u0000".toByteArray(Charsets.UTF_8)
        if (bytes.size < magic.size + 4 || !bytes.copyOfRange(0, magic.size).contentEquals(magic)) return
        val input = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        input.position(magic.size)
        val count = input.int
        repeat(count) {
            if (input.remaining() < 6) return@repeat
            val length = input.short.toInt() and 0xffff
            if (length > input.remaining() - 4) return@repeat
            val wordBytes = ByteArray(length); input.get(wordBytes)
            val frequency = input.int
            val word = normalize(String(wordBytes, Charsets.UTF_8))
            if (!eligible(word) || exact.containsKey(word)) return@repeat
            exact[word] = entries.size
            entries += Entry(word, frequency)
        }
        entries.forEachIndexed { index, entry ->
            deletionKeys(entry.text).forEach { key ->
                val values = deletionIndex.getOrPut(key) { ArrayList(2) }
                if (values.size < 12) values += index
            }
        }
    }

    private fun candidates(word: String): List<Entry> {
        val ids = LinkedHashSet<Int>()
        deletionIndex[key(graphemes(word))]?.let(ids::addAll)
        deletionKeys(word).forEach { key ->
            deletionIndex[key]?.let(ids::addAll)
            exact[normalize(key.replace(SEPARATOR, ""))]?.let(ids::add)
        }
        val source = graphemes(word)
        return ids.mapNotNull { entries.getOrNull(it) }
            .filter { editDistanceAtMostOne(source, graphemes(it.text)) }
            .sortedWith(compareByDescending<Entry> { it.frequency }.thenBy { it.text })
    }

    private fun eligible(word: String): Boolean = graphemes(word).size >= 3 && word.codePoints().allMatch {
        it in 0x0D80..0x0DFF || it == 0x200C || it == 0x200D
    }

    private fun deletionKeys(word: String): List<String> {
        val items = graphemes(word)
        return items.indices.map { removed -> key(items.filterIndexed { index, _ -> index != removed }) }
    }

    private fun graphemes(word: String): List<String> {
        val iterator = BreakIterator.getCharacterInstance(Locale("si", "LK")); iterator.setText(word)
        val result = ArrayList<String>(); var start = iterator.first(); var end = iterator.next()
        while (end != BreakIterator.DONE) { result += word.substring(start, end); start = end; end = iterator.next() }
        return result
    }

    private fun key(items: List<String>) = items.joinToString(SEPARATOR)

    private fun editDistanceAtMostOne(left: List<String>, right: List<String>): Boolean {
        if (kotlin.math.abs(left.size - right.size) > 1) return false
        var a = 0; var b = 0; var edits = 0
        while (a < left.size && b < right.size) {
            if (left[a] == right[b]) { a++; b++; continue }
            if (++edits > 1) return false
            when { left.size > right.size -> a++; right.size > left.size -> b++; else -> { a++; b++ } }
        }
        return edits + (left.size - a) + (right.size - b) <= 1
    }

    private fun normalize(text: String) = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFC)

    private companion object { const val SEPARATOR = "\u001f" }
}
