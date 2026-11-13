package com.crazestudio.crazekeyboard

import android.content.Context
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.ConcurrentHashMap

/** Memory-mapped, sorted prefix indexes generated from downloaded language packs. */
internal object DownloadedVocabularyIndex {
    private data class Index(val buffer: ByteBuffer, val offsets: IntArray)
    private val indexes = ConcurrentHashMap<String, Index>()
    private const val MAX_SCAN = 6000
    private const val POOL = 16
    private const val CACHE_SIZE = 256
    private val suggestCache = object : LinkedHashMap<String, List<String>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<String>>?): Boolean =
            size > CACHE_SIZE
    }

    private fun file(context: Context, language: String) =
        File(File(context.filesDir, "language_packs"), "$language.words")

    fun loadInstalledAsync(context: Context) {
        val app = context.applicationContext
        Thread({
            listOf("en", "bn", "hi").forEach { language -> open(app, language) }
        }, "craze-pack-index").apply { isDaemon = true; start() }
    }

    fun reload(context: Context, language: String) {
        indexes.remove(language)
        synchronized(suggestCache) { suggestCache.clear() }
        open(context.applicationContext, language)
    }

    private fun open(context: Context, language: String): Boolean {
        if (indexes.containsKey(language)) return true
        return try {
            val sourceFile = file(context, language)
            if (!sourceFile.isFile || sourceFile.length() == 0L) return false
            val buffer = RandomAccessFile(sourceFile, "r").use { raf ->
                raf.channel.map(FileChannel.MapMode.READ_ONLY, 0, raf.length()).asReadOnlyBuffer()
            }
            val found = ArrayList<Int>(100_000)
            found.add(0)
            for (i in 0 until buffer.limit()) {
                if (buffer.get(i) == 10.toByte() && i + 1 < buffer.limit()) found.add(i + 1)
            }
            indexes[language] = Index(buffer, found.toIntArray())
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun wordAt(index: Index, position: Int): String {
        val start = index.offsets[position]
        var end = start
        val limit = index.buffer.limit()
        while (end < limit && index.buffer.get(end) != 10.toByte()) end++
        if (end <= start) return ""
        val bytes = ByteArray(end - start)
        for (k in bytes.indices) bytes[k] = index.buffer.get(start + k)
        return String(bytes, Charsets.UTF_8)
    }

    private fun lowerBound(index: Index, prefix: String): Int {
        var low = 0
        var high = index.offsets.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (wordAt(index, mid).compareTo(prefix) < 0) low = mid + 1 else high = mid
        }
        return low
    }

    fun contains(language: String, word: String): Boolean {
        val code = language.lowercase().substringBefore('-')
        val index = indexes[code] ?: return false
        val value = word.lowercase()
        val position = lowerBound(index, value)
        return position < index.offsets.size && wordAt(index, position) == value
    }

    /**
     * Completions of [prefix]. The packs are plain word lists, so alphabetical order would offer
     * the rarest words first. English packs are ranked with the frequency data of [VocabularyIndex];
     * other languages prefer the shortest completions, which are on average the most common.
     */
    fun suggest(language: String, prefix: String, limit: Int = 3): List<String> {
        if (prefix.isBlank() || limit <= 0) return emptyList()
        val code = language.lowercase().substringBefore('-')
        val index = indexes[code] ?: return emptyList()
        val needle = prefix.lowercase()
        val english = code == "en"
        // English ranking depends on the frequency lexicon, so it is only cached once that is loaded.
        val cacheable = !english || VocabularyIndex.isLoaded()
        val cacheKey = "$code|$needle"
        if (cacheable) {
            synchronized(suggestCache) { suggestCache[cacheKey] }?.let { return it.take(limit) }
        }

        var position = lowerBound(index, needle)
        val matches = ArrayList<String>(64)
        var scanned = 0
        while (position < index.offsets.size && scanned < MAX_SCAN) {
            val word = wordAt(index, position++)
            scanned++
            if (!word.startsWith(needle)) break
            if (word != needle) matches.add(word)
        }
        if (matches.isEmpty()) return emptyList()

        val ranked: List<String> = if (english && VocabularyIndex.isLoaded()) {
            matches.map { it to VocabularyIndex.rankScore(it) - 0.08 * (it.length - needle.length) }
                .sortedByDescending { it.second }
                .map { it.first }
        } else {
            matches.sortedWith(compareBy<String>({ it.length }, { it }))
        }
        val pool = ranked.take(POOL)
        if (cacheable) synchronized(suggestCache) { suggestCache[cacheKey] = pool }
        return pool.take(limit)
    }
}
