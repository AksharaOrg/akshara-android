package org.akshara.ime.data

import android.content.Context
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Words the user types and what follows each one, kept on this device only.
 *
 * Everything lives in memory after one load: suggestions read it on every keystroke and Space reads it on the main
 * thread, so reads never parse or touch disk. Changes are saved in the background a moment after typing pauses
 * (and when the keyboard closes) as one small file, instead of rewriting preferences on every word.
 *
 * Sizes are capped: [MAX_WORDS] words, [MAX_CONTEXTS] preceding words (the least recently used are dropped) with
 * up to [MAX_FOLLOWERS] followers each.
 */
class LocalLearningStore(context: Context) {
    private val app = context.applicationContext
    private val file = AtomicFile(File(app.noBackupFilesDir, FILE))
    private val words = HashMap<String, Int>()
    /** Preceding word to its followers; access order makes the eldest entry the least recently used. */
    private val contexts = object : LinkedHashMap<String, HashMap<String, Int>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, HashMap<String, Int>>) = size > MAX_CONTEXTS
    }
    /** What readers see: replaced, never changed, so the main thread can read it without a lock. */
    @Volatile private var wordsSnapshot: Map<String, Int> = emptyMap()
    @Volatile private var loaded = false
    private var saveScheduled = false
    private var pendingSave: ScheduledFuture<*>? = null
    private var saveToken = 0
    private var migrationPending = false
    /** Bumped by [clear]; a save that started before it must not bring the data back. */
    private var generation = 0

    fun record(word: String, previous: String?) {
        if (word.isBlank()) return
        synchronized(this) {
            load()
            words[word] = (words[word] ?: 0) + 1
            trim(words, MAX_WORDS)
            wordsSnapshot = HashMap(words)
            if (!previous.isNullOrBlank()) {
                val followers = contexts.getOrPut(previous) { HashMap() }
                followers[word] = (followers[word] ?: 0) + 1
                trim(followers, MAX_FOLLOWERS)
            }
            scheduleSave(SAVE_DELAY_MS)
        }
    }

    fun words(): Map<String, Int> {
        if (!loaded) synchronized(this) { load() }
        return wordsSnapshot
    }

    fun followers(previous: String?): Map<String, Int> {
        if (previous == null) return emptyMap()
        synchronized(this) {
            load()
            // A copy: the caller reads it while typing continues on another thread
            return contexts[previous]?.let(::HashMap) ?: emptyMap()
        }
    }

    /** Saves pending changes now, in the background (the keyboard calls this when it closes). */
    fun flush() = synchronized(this) { if (saveScheduled) scheduleSave(0) }

    /** Writes pending changes before returning. For tests. */
    internal fun flushNow() {
        synchronized(this) {
            pendingSave?.cancel(false); pendingSave = null; saveScheduled = false; saveToken++
        }
        save()
    }

    fun clear() {
        synchronized(this) {
            words.clear()
            contexts.clear()
            wordsSnapshot = emptyMap()
            loaded = true
            pendingSave?.cancel(false)
            pendingSave = null
            saveScheduled = false
            saveToken++
            migrationPending = false
            generation++
            file.delete()
        }
        legacy().edit().clear().apply()
    }

    private fun load() {
        if (loaded) return
        if (file.baseFile.exists()) {
            runCatching { read(JSONObject(String(file.readFully(), Charsets.UTF_8))) }
        } else {
            migrate()
        }
        wordsSnapshot = HashMap(words)
        loaded = true
    }

    private fun read(root: JSONObject) {
        root.optJSONObject(WORDS)?.let { json -> json.keys().forEach { words[it] = json.optInt(it) } }
        // Saved least recently used first, so re-adding restores the order
        root.optJSONArray(CONTEXTS)?.let { array ->
            for (i in 0 until array.length()) {
                val entry = array.optJSONObject(i) ?: continue
                val followers = entry.optJSONObject(FOLLOWERS) ?: continue
                contexts[entry.optString(PREVIOUS)] = HashMap<String, Int>().also { map ->
                    followers.keys().forEach { map[it] = followers.optInt(it) }
                }
            }
        }
    }

    /** Moves data saved by older versions (one preferences key per preceding word) into the new file. */
    private fun migrate() {
        val prefs = legacy()
        val all = prefs.all
        if (all.isEmpty()) return
        fun parse(value: Any?): HashMap<String, Int> {
            val json = runCatching { JSONObject(value as? String ?: "{}") }.getOrDefault(JSONObject())
            return HashMap<String, Int>().also { map -> json.keys().forEach { map[it] = json.optInt(it) } }
        }
        words.putAll(parse(all[WORDS]))
        trim(words, MAX_WORDS)
        // Older versions kept every preceding word ever typed; keep the most used ones
        all.filterKeys { it.startsWith(LEGACY_BIGRAM) }
            .map { (key, value) -> key.removePrefix(LEGACY_BIGRAM) to parse(value) }
            .sortedByDescending { (_, followers) -> followers.values.sum() }
            .take(MAX_CONTEXTS)
            .reversed()   // most used last, as the most recently used
            .forEach { (previous, followers) -> contexts[previous] = followers.also { trim(it, MAX_FOLLOWERS) } }
        migrationPending = true
        save()
    }

    private fun scheduleSave(delayMs: Long) {
        // Keep one task, timed from the latest edit. Otherwise every word leaves a
        // queued task that can save in the middle of continuous typing.
        pendingSave?.cancel(false)
        saveScheduled = true
        val token = ++saveToken
        pendingSave = saver.schedule(Runnable {
            synchronized(this) {
                if (!saveScheduled || token != saveToken) return@Runnable
                saveScheduled = false
                pendingSave = null
            }
            save()
        }, delayMs, TimeUnit.MILLISECONDS)
    }

    private fun save() {
        var savedGeneration = 0
        val json = synchronized(this) {
            savedGeneration = generation
            JSONObject().apply {
                put(VERSION, 2)
                put(WORDS, JSONObject(words as Map<*, *>))
                put(CONTEXTS, org.json.JSONArray().also { array ->
                    contexts.forEach { (previous, followers) ->
                        array.put(JSONObject().put(PREVIOUS, previous).put(FOLLOWERS, JSONObject(followers as Map<*, *>)))
                    }
                })
            }.toString()
        }
        val stream = runCatching { file.startWrite() }.getOrNull() ?: return
        val saved = runCatching {
            stream.write(json.toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        }.onFailure { file.failWrite(stream) }
        synchronized(this) {
            if (generation != savedGeneration) file.delete()
            else if (saved.isSuccess && migrationPending) {
                // Delete the only recovery copy only after the new file is durable.
                legacy().edit().clear().apply()
                migrationPending = false
            }
        }
    }

    private fun legacy() = app.getSharedPreferences(LEGACY_FILE, Context.MODE_PRIVATE)

    private fun trim(map: MutableMap<String, Int>, max: Int) {
        if (map.size > max) map.entries.sortedBy { it.value }.take(map.size - max).forEach { map.remove(it.key) }
    }

    companion object {
        const val MAX_WORDS = 512
        const val MAX_CONTEXTS = 1000
        const val MAX_FOLLOWERS = 48
        private const val SAVE_DELAY_MS = 2_000L
        private const val FILE = "learning.json"
        private const val LEGACY_FILE = "akshara_learning"
        private const val LEGACY_BIGRAM = "bigram:"
        private const val VERSION = "version"
        private const val WORDS = "words"
        private const val CONTEXTS = "contexts"
        private const val PREVIOUS = "previous"
        private const val FOLLOWERS = "followers"

        /** One background thread for all stores; daemon so it never keeps the process alive. */
        private val saver = ScheduledThreadPoolExecutor(1) { task ->
            Thread(task, "akshara-learning").apply { isDaemon = true }
        }.apply { removeOnCancelPolicy = true }
    }
}
