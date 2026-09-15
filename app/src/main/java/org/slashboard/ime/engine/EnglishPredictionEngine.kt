package org.slashboard.ime.engine

import android.content.Context
import org.slashboard.ime.R
import org.slashboard.ime.data.Candidate
import org.slashboard.ime.data.LocalLearningStore
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.min

class EnglishPredictionEngine(
    private val context: Context,
    private val learning: LocalLearningStore
) {
    @Volatile private var loaded = false
    private var entries: List<Pair<String, Int>> = emptyList()
    private val unigramIndex = HashMap<String, Int>(65_536)
    private val topWordsByChar = HashMap<Char, List<Pair<String, Int>>>(32)
    private val wordsByFirstChar = HashMap<Char, ArrayList<Pair<String, Int>>>(32)
    private val phraseBigrams = HashMap<String, MutableList<Pair<String, Int>>>(1024)
    private val phraseTrigrams = HashMap<String, MutableList<Pair<String, Int>>>(512)
    private val typoCorrections = HashMap<String, String>(256)

    init {
        loadPhrases()
        loadTypoCorrections()
        ensureLoaded()
    }

    fun warmup() {
        ensureLoaded()
    }

    @Synchronized
    private fun ensureLoaded() {
        if (loaded) return
        loadVocabulary()
    }

    private fun loadVocabulary() {
        runCatching {
            context.resources.openRawResource(R.raw.english_frequency_model).bufferedReader().useLines { lines ->
                val list = ArrayList<Pair<String, Int>>(55_000)
                val byChar = HashMap<Char, ArrayList<Pair<String, Int>>>(32)
                lines.forEach { line ->
                    if (line.isNotEmpty()) {
                        val tab = line.indexOf('\t')
                        if (tab > 0) {
                            val word = line.substring(0, tab)
                            val freq = line.substring(tab + 1).toIntOrNull() ?: 1
                            val pair = word to freq
                            list.add(pair)
                            unigramIndex[word] = freq
                            val firstChar = word[0]
                            val charList = byChar.getOrPut(firstChar) { ArrayList(2048) }
                            charList.add(pair)
                        }
                    }
                }
                entries = list
                for ((ch, words) in byChar) {
                    val sorted = words.sortedByDescending { it.second }
                    topWordsByChar[ch] = sorted.take(50)
                    wordsByFirstChar[ch] = ArrayList(sorted.take(200))
                }
                loaded = true
            }
        }.onFailure {
            loadFallbackVocabulary()
        }
    }

    private fun loadFallbackVocabulary() {
        val fallback = listOf(
            "the" to 1000, "be" to 950, "to" to 940, "of" to 930, "and" to 920, "a" to 910, "in" to 900,
            "that" to 890, "have" to 880, "i" to 870, "it" to 860, "for" to 850, "not" to 840, "on" to 830,
            "with" to 820, "he" to 810, "as" to 800, "you" to 790, "do" to 780, "at" to 770, "this" to 760,
            "but" to 750, "his" to 740, "by" to 730, "from" to 720, "they" to 710, "we" to 700, "say" to 690,
            "her" to 680, "she" to 670, "or" to 660, "an" to 650, "will" to 640, "my" to 630, "one" to 620,
            "all" to 610, "would" to 600, "there" to 590, "their" to 580, "what" to 570, "so" to 560, "up" to 550,
            "out" to 540, "if" to 530, "about" to 520, "who" to 510, "get" to 500, "which" to 490, "go" to 480,
            "me" to 470, "when" to 460, "make" to 450, "can" to 440, "like" to 430, "time" to 420, "no" to 410,
            "just" to 400, "him" to 390, "know" to 380, "take" to 370, "people" to 360, "into" to 350, "year" to 340,
            "your" to 330, "good" to 320, "some" to 310, "could" to 300, "them" to 290, "see" to 280, "other" to 270,
            "than" to 260, "then" to 250, "now" to 240, "look" to 230, "only" to 220, "come" to 210, "its" to 200,
            "over" to 190, "think" to 180, "also" to 170, "back" to 160, "after" to 150, "use" to 140, "two" to 130,
            "how" to 120, "our" to 110, "work" to 100, "first" to 95, "well" to 90, "way" to 85, "even" to 80,
            "new" to 75, "want" to 70, "because" to 65, "any" to 60, "these" to 55, "give" to 50, "day" to 45,
            "suggest" to 500, "suggestion" to 450, "suggested" to 420, "suggesting" to 400, "suggestions" to 380,
            "hello" to 600, "thanks" to 650, "please" to 630, "sorry" to 600, "welcome" to 550, "okay" to 580
        )
        entries = fallback.sortedBy { it.first }
        fallback.forEach { (w, f) ->
            unigramIndex[w] = f
            val ch = w[0]
            wordsByFirstChar.getOrPut(ch) { ArrayList() }.add(w to f)
            topWordsByChar[ch] = (topWordsByChar[ch].orEmpty() + (w to f)).sortedByDescending { it.second }
        }
        loaded = true
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

    private fun searchPrefixInDictionary(prefix: String, limit: Int = 40): List<Pair<String, Int>> {
        if (!loaded) ensureLoaded()
        if (prefix.isEmpty() || entries.isEmpty()) return emptyList()
        if (prefix.length == 1) {
            return topWordsByChar[prefix[0]] ?: emptyList()
        }
        val first = firstIndexAtOrAfter(prefix)
        if (first >= entries.size) return emptyList()
        val matches = ArrayList<Pair<String, Int>>(64)
        val maxScan = 2000
        var count = 0
        for (i in first until entries.size) {
            val entry = entries[i]
            if (!entry.first.startsWith(prefix)) break
            matches.add(entry)
            count++
            if (count >= maxScan) break
        }
        matches.sortByDescending { it.second }
        return if (matches.size > limit) matches.subList(0, limit) else matches
    }

    fun candidates(
        rawPrefix: String,
        preceding: List<String>,
        max: Int = 3
    ): List<Candidate> {
        if (!loaded) ensureLoaded()
        if (max <= 0) return emptyList()

        val prefix = rawPrefix.trim()
        val lowerPrefix = prefix.lowercase(Locale.ENGLISH)
        val isAllUpper = prefix.length > 1 && prefix.all { it.isUpperCase() }
        val isTitle = prefix.isNotEmpty() && prefix[0].isUpperCase() && (prefix.length == 1 || prefix.drop(1).all { it.isLowerCase() })

        val previous = preceding.lastOrNull()?.trim()?.lowercase(Locale.ENGLISH)
        val earlier = preceding.dropLast(1).lastOrNull()?.trim()?.lowercase(Locale.ENGLISH)

        val learnedWords = learning.words()
        val learnedNext = previous?.let { learning.followers(it) }.orEmpty()
        val learnedTri = if (earlier != null && previous != null) learning.trigramFollowers(earlier, previous) else emptyMap()

        val staticNext = previous?.let { phraseBigrams[it] }.orEmpty()
        val staticTri = if (earlier != null && previous != null) phraseTrigrams["$earlier\t$previous"].orEmpty() else emptyList()

        val ranked = ArrayList<Candidate>(max * 2)
        val considered = HashSet<String>(64)

        fun applyCase(word: String): String {
            return when {
                isAllUpper -> word.uppercase(Locale.ENGLISH)
                isTitle -> word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ENGLISH) else it.toString() }
                word == "i" -> "I"
                word == "i'm" -> "I'm"
                word == "i'll" -> "I'll"
                word == "i've" -> "I've"
                word == "i'd" -> "I'd"
                else -> word
            }
        }

        fun consider(candidateWord: String, frequency: Int, unigramWeight: Double, isCorrection: Boolean = false) {
            val lowerWord = candidateWord.lowercase(Locale.ENGLISH)
            if (!considered.add(lowerWord)) return

            val learnedCount = learnedWords[candidateWord] ?: learnedWords[lowerWord] ?: 0
            val learnedNextCount = learnedNext[candidateWord] ?: learnedNext[lowerWord] ?: 0
            val learnedTriCount = learnedTri[candidateWord] ?: learnedTri[lowerWord] ?: 0

            val staticNextCount = staticNext.firstOrNull { it.first.equals(lowerWord, ignoreCase = true) }?.second ?: 0
            val staticTriCount = staticTri.firstOrNull { it.first.equals(lowerWord, ignoreCase = true) }?.second ?: 0

            val lengthBonus = if (lowerWord == lowerPrefix) 25.0 else 0.0

            val score = unigramWeight * ln(frequency.coerceAtLeast(1) + 1.0) +
                    lengthBonus +
                    (if (learnedCount > 0) learnedCount * 30.0 + 60.0 else 0.0) +
                    (if (learnedNextCount > 0) learnedNextCount * 35.0 + 70.0 else 0.0) +
                    (if (learnedTriCount > 0) learnedTriCount * 45.0 + 90.0 else 0.0) +
                    ln(staticNextCount + 1.0) * 3.0 +
                    ln(staticTriCount + 1.0) * 4.0 +
                    if (isCorrection) 15.0 else 0.0

            val finalWord = applyCase(candidateWord)
            val cand = Candidate(finalWord, score, isCorrection)

            val index = ranked.indexOfFirst { cand.score > it.score }
            if (index < 0) {
                if (ranked.size < max * 2) ranked.add(cand)
            } else {
                ranked.add(index, cand)
                if (ranked.size > max * 2) ranked.removeAt(ranked.lastIndex)
            }
        }

        // 1. If prefix is empty -> Predict next words based on phrase context / bigrams / sentence starters
        if (prefix.isEmpty()) {
            if (previous != null) {
                learnedTri.forEach { (word, count) -> consider(word, count * 20, 1.5) }
                staticTri.forEach { (word, count) -> consider(word, count, 1.4) }
                learnedNext.forEach { (word, count) -> consider(word, count * 15, 1.2) }
                staticNext.forEach { (word, count) -> consider(word, count, 1.0) }
            }

            if (ranked.isEmpty()) {
                val starters = if (previous == null) {
                    listOf("I", "The", "How", "What", "Hello", "Thanks", "Good", "Can", "Please", "Are", "We", "You", "Where", "Let", "Have")
                } else {
                    listOf("and", "the", "to", "you", "a", "in", "it", "is", "for", "that", "on", "with", "my", "of", "be")
                }
                starters.forEach { word ->
                    consider(word, unigramIndex[word.lowercase(Locale.ENGLISH)] ?: 50, 1.0)
                }
            }

            return ranked.take(max)
        }

        // 2. Exact typo / contraction correction match (e.g., "teh" -> "the", "sugest" -> "suggest", "dont" -> "don't")
        val directCorrection = typoCorrections[lowerPrefix]
        if (directCorrection != null) {
            val formatted = applyCase(directCorrection)
            ranked.add(0, Candidate(formatted, 9999.0, isCorrection = true))
            considered.add(directCorrection.lowercase(Locale.ENGLISH))
        }

        // 3. User learned words matching prefix
        learnedWords.forEach { (word, count) ->
            if (word.startsWith(prefix, ignoreCase = true)) {
                consider(word, count * 35, 3.0)
            }
        }

        // 4. Context continuation matches matching prefix
        learnedTri.forEach { (word, count) ->
            if (word.startsWith(prefix, ignoreCase = true)) consider(word, count * 20, 2.5)
        }
        staticTri.forEach { (word, count) ->
            if (word.startsWith(prefix, ignoreCase = true)) consider(word, count, 2.2)
        }
        learnedNext.forEach { (word, count) ->
            if (word.startsWith(prefix, ignoreCase = true)) consider(word, count * 15, 2.0)
        }
        staticNext.forEach { (word, count) ->
            if (word.startsWith(prefix, ignoreCase = true)) consider(word, count, 1.8)
        }

        // 5. Dictionary prefix matches (55,000+ words fast lookup)
        val dictMatches = searchPrefixInDictionary(lowerPrefix, limit = 40)
        for ((word, freq) in dictMatches) {
            consider(word, freq, 2.5)
        }

        // 6. Fuzzy edit distance / Auto-correction if candidates are few
        if (ranked.size < max && lowerPrefix.length >= 3) {
            val fuzzyMatches = findFuzzyMatches(lowerPrefix)
            for ((word, freq, dist) in fuzzyMatches) {
                val penalty = when (dist) {
                    1 -> 0.85
                    else -> 0.65
                }
                val isTypoCorrection = dist == 1 && (unigramIndex[lowerPrefix] == null || (unigramIndex[lowerPrefix] ?: 0) < freq / 10)
                consider(word, (freq * penalty).toInt(), penalty, isTypoCorrection)
            }
        }

        return ranked.take(max)
    }

    fun learn(word: String, previous: String?, earlier: String? = null) {
        if (word.isBlank()) return
        learning.record(word, previous, earlier)
    }

    private fun findFuzzyMatches(input: String): List<Triple<String, Int, Int>> {
        val results = ArrayList<Triple<String, Int, Int>>(8)
        val inputLen = input.length
        val firstChar = input[0]

        val candidatesPool = ArrayList<Pair<String, Int>>(128)
        wordsByFirstChar[firstChar]?.let { candidatesPool.addAll(it) }
        val adjacent = getAdjacentChars(firstChar)
        for (adj in adjacent) {
            wordsByFirstChar[adj]?.let { candidatesPool.addAll(it) }
        }

        for (i in 0 until candidatesPool.size) {
            val (word, freq) = candidatesPool[i]
            if (abs(word.length - inputLen) > 2) continue

            val dist = levenshteinDistance(input, word, maxLimit = 2)
            if (dist in 1..2) {
                results.add(Triple(word, freq, dist))
                if (results.size >= 8) break
            }
        }
        return results.sortedBy { it.third * 1000 - it.second }
    }

    private fun getAdjacentChars(c: Char): List<Char> {
        return when (c) {
            'q' -> listOf('w', 'a', 's')
            'w' -> listOf('q', 'e', 'a', 's', 'd')
            'e' -> listOf('w', 'r', 's', 'd', 'f')
            'r' -> listOf('e', 't', 'd', 'f', 'g')
            't' -> listOf('r', 'y', 'f', 'g', 'h')
            'y' -> listOf('t', 'u', 'g', 'h', 'j')
            'u' -> listOf('y', 'i', 'h', 'j', 'k')
            'i' -> listOf('u', 'o', 'j', 'k', 'l')
            'o' -> listOf('i', 'p', 'k', 'l')
            'p' -> listOf('o', 'l')
            'a' -> listOf('q', 'w', 's', 'z')
            's' -> listOf('a', 'w', 'e', 'd', 'x', 'z')
            'd' -> listOf('s', 'e', 'r', 'f', 'c', 'x')
            'f' -> listOf('d', 'r', 't', 'g', 'v', 'c')
            'g' -> listOf('f', 't', 'y', 'h', 'b', 'v')
            'h' -> listOf('g', 'y', 'u', 'j', 'n', 'b')
            'j' -> listOf('h', 'u', 'i', 'k', 'm', 'n')
            'k' -> listOf('j', 'i', 'o', 'l', 'm')
            'l' -> listOf('k', 'o', 'p')
            'z' -> listOf('a', 's', 'x')
            'x' -> listOf('z', 's', 'd', 'c')
            'c' -> listOf('x', 'd', 'f', 'v')
            'v' -> listOf('c', 'f', 'g', 'b')
            'b' -> listOf('v', 'g', 'h', 'n')
            'n' -> listOf('b', 'h', 'j', 'm')
            'm' -> listOf('n', 'j', 'k')
            else -> emptyList()
        }
    }

    private fun levenshteinDistance(s1: String, s2: String, maxLimit: Int): Int {
        val len1 = s1.length
        val len2 = s2.length
        if (abs(len1 - len2) > maxLimit) return maxLimit + 1

        var prev = IntArray(len2 + 1) { it }
        var curr = IntArray(len2 + 1)

        for (i in 1..len1) {
            curr[0] = i
            var minInRow = curr[0]
            for (j in 1..len2) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                curr[j] = min(min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost)
                minInRow = min(minInRow, curr[j])
            }
            if (minInRow > maxLimit) return maxLimit + 1
            val temp = prev
            prev = curr
            curr = temp
        }
        return prev[len2]
    }

    private fun loadPhrases() {
        fun addBigram(w1: String, w2: String, weight: Int) {
            phraseBigrams.getOrPut(w1.lowercase(Locale.ENGLISH)) { ArrayList() }.add(w2 to weight)
        }
        fun addTrigram(w1: String, w2: String, w3: String, weight: Int) {
            phraseTrigrams.getOrPut("${w1.lowercase(Locale.ENGLISH)}\t${w2.lowercase(Locale.ENGLISH)}") { ArrayList() }.add(w3 to weight)
        }

        // Common conversational bigrams
        addBigram("how", "are", 60); addBigram("how", "is", 50); addBigram("how", "about", 45); addBigram("how", "was", 40)
        addBigram("thank", "you", 60); addBigram("thank", "god", 35)
        addBigram("thanks", "for", 50); addBigram("thanks", "a", 45); addBigram("thanks", "bro", 45); addBigram("thanks", "so", 40); addBigram("thanks", "machan", 40); addBigram("thanks", "again", 35)

        addBigram("see", "you", 55); addBigram("see", "later", 40); addBigram("see", "soon", 40); addBigram("see", "tomorrow", 35)
        addBigram("let", "me", 55); addBigram("let", "us", 45); addBigram("let", "you", 40); addBigram("let", "know", 45)
        addBigram("let's", "go", 50); addBigram("let's", "do", 45); addBigram("let's", "meet", 45); addBigram("let's", "see", 40)

        addBigram("i", "am", 60); addBigram("i", "will", 55); addBigram("i", "have", 55); addBigram("i", "can", 50); addBigram("i", "want", 50); addBigram("i", "know", 50)
        addBigram("i", "think", 50); addBigram("i", "love", 50); addBigram("i", "need", 45); addBigram("i", "was", 45); addBigram("i", "don't", 55); addBigram("i", "got", 45)

        addBigram("i'm", "going", 50); addBigram("i'm", "at", 45); addBigram("i'm", "on", 45); addBigram("i'm", "in", 45); addBigram("i'm", "sorry", 45); addBigram("i'm", "ready", 45); addBigram("i'm", "fine", 40); addBigram("i'm", "busy", 40)
        addBigram("i'll", "be", 50); addBigram("i'll", "call", 50); addBigram("i'll", "let", 45); addBigram("i'll", "come", 45); addBigram("i'll", "send", 45); addBigram("i'll", "do", 40); addBigram("i'll", "try", 40)

        addBigram("take", "care", 55); addBigram("take", "it", 45); addBigram("take", "time", 40); addBigram("take", "your", 40)
        addBigram("nice", "to", 50); addBigram("nice", "day", 40); addBigram("nice", "one", 40)
        addBigram("no", "problem", 50); addBigram("no", "worries", 45); addBigram("no", "idea", 40); addBigram("no", "way", 40); addBigram("no", "need", 40)
        addBigram("on", "my", 50); addBigram("on", "the", 50); addBigram("on", "time", 45); addBigram("on", "it", 40)
        addBigram("at", "home", 45); addBigram("at", "work", 45); addBigram("at", "the", 50); addBigram("at", "office", 40); addBigram("at", "night", 40)
        addBigram("in", "the", 55); addBigram("in", "a", 45); addBigram("in", "touch", 40); addBigram("in", "fact", 40)
        addBigram("what", "is", 50); addBigram("what", "are", 45); addBigram("what", "about", 45); addBigram("what", "time", 45); addBigram("what", "do", 40); addBigram("what", "happened", 40)
        addBigram("where", "are", 50); addBigram("where", "is", 45); addBigram("where", "to", 40); addBigram("where", "can", 35)
        addBigram("when", "will", 45); addBigram("when", "are", 45); addBigram("when", "can", 40); addBigram("when", "is", 40)
        addBigram("why", "not", 50); addBigram("why", "are", 40); addBigram("why", "did", 40); addBigram("why", "is", 35)
        addBigram("can", "you", 55); addBigram("can", "i", 50); addBigram("can", "we", 45); addBigram("can", "be", 40)
        addBigram("could", "you", 50); addBigram("could", "be", 40); addBigram("could", "have", 35)
        addBigram("would", "you", 50); addBigram("would", "like", 45); addBigram("would", "be", 40)
        addBigram("are", "you", 55); addBigram("are", "there", 45); addBigram("are", "we", 40); addBigram("are", "they", 35)
        addBigram("have", "a", 55); addBigram("have", "to", 50); addBigram("have", "been", 45); addBigram("have", "you", 45); addBigram("have", "fun", 40)
        addBigram("please", "let", 50); addBigram("please", "call", 45); addBigram("please", "send", 45); addBigram("please", "help", 40); addBigram("please", "check", 40)
        addBigram("happy", "birthday", 55); addBigram("happy", "new", 50); addBigram("happy", "weekend", 40); addBigram("happy", "anniversary", 40)
        addBigram("call", "me", 50); addBigram("call", "you", 45); addBigram("call", "back", 45); addBigram("call", "later", 40)
        addBigram("talk", "to", 50); addBigram("talk", "later", 45); addBigram("talk", "soon", 40)
        addBigram("sounds", "good", 50); addBigram("sounds", "great", 45); addBigram("sounds", "like", 40)
        addBigram("looking", "forward", 50); addBigram("looking", "for", 45); addBigram("looking", "good", 40)
        addBigram("welcome", "back", 45); addBigram("welcome", "to", 45)
        addBigram("good", "afternoon", 45); addBigram("good", "evening", 45); addBigram("good", "morning", 50); addBigram("good", "night", 50)
        addBigram("all", "the", 50); addBigram("all", "good", 45); addBigram("all", "right", 50); addBigram("all", "set", 45)
        addBigram("give", "me", 50); addBigram("give", "a", 45); addBigram("give", "you", 45)
        addBigram("send", "me", 50); addBigram("send", "the", 50); addBigram("send", "it", 45); addBigram("send", "you", 45)
        addBigram("tell", "me", 50); addBigram("tell", "you", 45); addBigram("tell", "them", 40)
        addBigram("need", "to", 55); addBigram("need", "help", 45); addBigram("need", "more", 40)
        addBigram("ready", "to", 50); addBigram("ready", "for", 45); addBigram("ready", "now", 45)

        // Sri Lankan collocations
        addBigram("machan", "kohomada", 50); addBigram("machan", "mokada", 45); addBigram("machan", "waren", 40); addBigram("machan", "ado", 40); addBigram("machan", "call", 40)
        addBigram("ela", "machan", 50); addBigram("ela", "kiri", 50); addBigram("ela", "bro", 45)
        addBigram("ado", "machan", 50); addBigram("ado", "mokada", 45); addBigram("ado", "kohomada", 40)

        // Common phrase trigrams
        addTrigram("how", "are", "you", 60); addTrigram("how", "are", "things", 40); addTrigram("how", "are", "we", 35)
        addTrigram("let", "me", "know", 60); addTrigram("let", "me", "see", 45); addTrigram("let", "me", "check", 45); addTrigram("let", "me", "call", 40)
        addTrigram("nice", "to", "meet", 55); addTrigram("nice", "to", "see", 50); addTrigram("nice", "to", "hear", 45)
        addTrigram("have", "a", "good", 55); addTrigram("have", "a", "great", 55); addTrigram("have", "a", "nice", 50); addTrigram("have", "a", "safe", 45); addTrigram("have", "a", "wonderful", 40)
        addTrigram("happy", "birthday", "to", 55); addTrigram("happy", "birthday", "bro", 50); addTrigram("happy", "birthday", "machan", 45)
        addTrigram("take", "care", "of", 50); addTrigram("take", "care", "bro", 50); addTrigram("take", "care", "machan", 45)
        addTrigram("no", "problem", "bro", 50); addTrigram("no", "problem", "at", 45); addTrigram("no", "problem", "machan", 45)
        addTrigram("on", "my", "way", 60); addTrigram("on", "my", "own", 45); addTrigram("on", "my", "mind", 40)
        addTrigram("see", "you", "later", 55); addTrigram("see", "you", "soon", 55); addTrigram("see", "you", "tomorrow", 50)
        addTrigram("talk", "to", "you", 55); addTrigram("talk", "to", "him", 40); addTrigram("talk", "to", "her", 40)
        addTrigram("i", "am", "going", 50); addTrigram("i", "am", "sorry", 50); addTrigram("i", "am", "ready", 45); addTrigram("i", "am", "at", 45)
        addTrigram("i", "will", "call", 50); addTrigram("i", "will", "be", 50); addTrigram("i", "will", "let", 45); addTrigram("i", "will", "come", 45); addTrigram("i", "will", "send", 45)
        addTrigram("i", "don't", "know", 60); addTrigram("i", "don't", "think", 50); addTrigram("i", "don't", "have", 45); addTrigram("i", "don't", "mind", 40)
        addTrigram("i", "want", "to", 55); addTrigram("i", "need", "to", 55); addTrigram("i", "love", "you", 60); addTrigram("i", "hope", "you", 50)
        addTrigram("can", "you", "please", 55); addTrigram("can", "you", "call", 50); addTrigram("can", "you", "send", 50); addTrigram("can", "you", "help", 45)
        addTrigram("could", "you", "please", 55); addTrigram("could", "you", "send", 45); addTrigram("could", "you", "check", 45)
        addTrigram("would", "you", "like", 55); addTrigram("would", "like", "to", 55)
        addTrigram("are", "you", "free", 50); addTrigram("are", "you", "ready", 50); addTrigram("are", "you", "sure", 50); addTrigram("are", "you", "there", 50); addTrigram("are", "you", "coming", 45)
        addTrigram("where", "are", "you", 60); addTrigram("where", "are", "we", 45)
        addTrigram("what", "are", "you", 55); addTrigram("what", "time", "is", 50); addTrigram("what", "time", "will", 45)
        addTrigram("looking", "forward", "to", 60)
        addTrigram("as", "soon", "as", 60)
        addTrigram("by", "the", "way", 60)
        addTrigram("thank", "you", "so", 55); addTrigram("thank", "you", "very", 50); addTrigram("thank", "you", "bro", 50)
        addTrigram("give", "me", "a", 50); addTrigram("send", "me", "the", 50); addTrigram("all", "the", "best", 55)
    }

    private fun loadTypoCorrections() {
        val map = listOf(
            // Suggest related misspellings
            "sugest" to "suggest", "sugestion" to "suggestion", "sugestions" to "suggestions",
            "sugested" to "suggested", "sugesting" to "suggesting", "sugestive" to "suggestive",

            // Contractions without apostrophes
            "im" to "I'm", "dont" to "don't", "cant" to "can't", "wont" to "won't", "didnt" to "didn't",
            "doesnt" to "doesn't", "isnt" to "isn't", "arent" to "aren't", "wasnt" to "wasn't", "werent" to "weren't",
            "havent" to "haven't", "hasnt" to "hasn't", "hadnt" to "hadn't", "couldnt" to "couldn't", "shouldnt" to "shouldn't",
            "wouldnt" to "wouldn't", "thats" to "that's", "whats" to "what's", "hows" to "how's", "wheres" to "where's",
            "theres" to "there's", "lets" to "let's", "youre" to "youre", "theyre" to "they're", "weve" to "we've",
            "youve" to "you've", "theyve" to "they've", "ill" to "I'll", "youll" to "you'll", "theyll" to "they'll",
            "id" to "I'd", "ive" to "I've",

            // Common Misspellings & Typo Swaps
            "teh" to "the", "taht" to "that", "waht" to "what", "wierd" to "weird", "beleive" to "believe",
            "seperate" to "separate", "definately" to "definitely", "recieved" to "received", "recieve" to "receive",
            "occured" to "occurred", "untill" to "until", "alot" to "a lot", "tommorrow" to "tomorrow",
            "tommorow" to "tomorrow", "thier" to "their", "guarentee" to "guarantee", "accomodate" to "accommodate",
            "acheive" to "achieve", "calender" to "calendar", "concious" to "conscious", "experiance" to "experience",
            "foriegn" to "foreign", "goverment" to "government", "grammer" to "grammar", "happended" to "happened",
            "interupt" to "interrupt", "mispell" to "misspell", "neccessary" to "necessary", "noticable" to "noticeable",
            "occurence" to "occurrence", "peice" to "piece", "posession" to "possession", "privilege" to "privilege",
            "reccomend" to "recommend", "remeber" to "remember", "suprise" to "surprise", "truely" to "truly",
            "unfortunatly" to "unfortunately", "writting" to "writing", "yu" to "you", "u" to "you",
            "r" to "are", "ur" to "your", "pls" to "please", "plz" to "please", "thx" to "thanks",
            "tks" to "thanks", "ty" to "thank you", "np" to "no problem", "idk" to "I don't know",
            "imo" to "in my opinion", "btw" to "by the way", "omw" to "on my way", "tbh" to "to be honest",
            "rn" to "right now", "gm" to "good morning", "gn" to "good night", "hbd" to "happy birthday",
            "bday" to "birthday", "tmrw" to "tomorrow", "yday" to "yesterday", "pic" to "picture",
            "pics" to "pictures", "msg" to "message", "broo" to "bro", "machann" to "machan"
        )
        for ((typo, fix) in map) {
            typoCorrections[typo.lowercase(Locale.ENGLISH)] = fix
        }
    }
}
