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
        while (end < index.buffer.limit() && index.buffer.get(end) != 10.toByte()) end++
        if (end <= start) return ""
        val bytes = ByteArray(end - start)
        val view = index.buffer.duplicate()
        view.position(start)
        view.get(bytes)
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

    fun suggest(language: String, prefix: String, limit: Int = 3): List<String> {
        if (prefix.isBlank() || limit <= 0) return emptyList()
        val code = language.lowercase().substringBefore('-')
        val index = indexes[code] ?: return emptyList()
        val needle = prefix.lowercase()
        var position = lowerBound(index, needle)
        val result = ArrayList<String>(limit)
        while (position < index.offsets.size && result.size < limit) {
            val word = wordAt(index, position++)
            if (!word.startsWith(needle)) break
            if (word != needle) result.add(word)
        }
        return result
    }
}
