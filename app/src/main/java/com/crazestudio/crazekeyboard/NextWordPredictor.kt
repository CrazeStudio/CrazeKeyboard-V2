package com.crazestudio.crazekeyboard

import android.content.Context
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.Locale

/**
 * Memory-mapped word-level n-gram predictor.
 *
 * The language pack is an optional binary file at filesDir/language_models/en-US.cwp.
 * The mapped model is not copied into a Kotlin heap collection. Only a small number
 * of candidate strings are allocated for each lookup.
 */
internal object NextWordPredictor {
    private const val FILE_NAME = "en-US.cwp"
    private const val MAGIC = 0x31505743 // "CWP1" in little endian
    private const val HEADER_SIZE = 12
    private const val INDEX_ENTRY_SIZE = 8

    @Volatile private var mapped: ByteBuffer? = null
    @Volatile private var indexedCount = 0
    @Volatile private var dataStart = 0
    @Volatile private var modelFile: File? = null

    @Synchronized
    private fun open(context: Context): ByteBuffer? {
        mapped?.let { return it }
        return try {
            // The .cwp asset is marked no-compress in Gradle, allowing direct read-only mmap
            // from the APK without copying a 20–30 MiB pack into the Java heap.
            context.assets.openFd(FILE_NAME).use { afd ->
                val buffer = java.io.FileInputStream(afd.fileDescriptor).channel.use { channel ->
                    channel.map(
                        FileChannel.MapMode.READ_ONLY,
                        afd.startOffset,
                        afd.length
                    ).order(ByteOrder.LITTLE_ENDIAN)
                }
                if (buffer.int != MAGIC) return null
                val version = buffer.int
                if (version != 1) return null
                val count = buffer.int
                if (count < 1 || count > (buffer.capacity() - HEADER_SIZE) / INDEX_ENTRY_SIZE) return null
                val start = HEADER_SIZE + count * INDEX_ENTRY_SIZE
                if (start > buffer.capacity()) return null
                indexedCount = count
                dataStart = start
                mapped = buffer.asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN)
                mapped
            }
        } catch (_: Exception) {
            // Missing pack is normal during development; the keyboard uses its fallback suggestions.
            null
        }
    }
    /** Return up to three likely next words for the text immediately before the cursor. */
    fun predict(context: Context, textBeforeCursor: String, limit: Int = 3): List<String> {
        if (textBeforeCursor.isBlank() || limit <= 0) return emptyList()
        val tokens = textBeforeCursor.lowercase(Locale.ROOT)
            .split(Regex("[^\\p{L}']+"))
            .filter { it.isNotBlank() }
            .takeLast(3)
        if (tokens.isEmpty()) return emptyList()
        val buffer = open(context) ?: return emptyList()
        for (size in minOf(3, tokens.size) downTo 1) {
            val phrase = tokens.takeLast(size).joinToString(" ")
            val found = lookup(buffer, phrase, limit)
            if (found.isNotEmpty()) return found
        }
        return emptyList()
    }

    private fun lookup(source: ByteBuffer, phrase: String, limit: Int): List<String> {
        val hash = fnv1a(phrase)
        var low = 0
        var high = indexedCount - 1
        var first = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            val value = source.getInt(HEADER_SIZE + mid * INDEX_ENTRY_SIZE)
            val cmp = Integer.compareUnsigned(value, hash)
            if (cmp < 0) low = mid + 1
            else {
                if (cmp == 0) first = mid
                high = mid - 1
            }
        }
        if (first < 0) return emptyList()
        val phraseBytes = phrase.toByteArray(Charsets.UTF_8)
        val results = ArrayList<String>(limit)
        var i = first
        while (i < indexedCount) {
            val slot = HEADER_SIZE + i * INDEX_ENTRY_SIZE
            if (source.getInt(slot) != hash) break
            val offset = source.getInt(slot + 4)
            if (offset < dataStart || offset >= source.capacity()) { i++; continue }
            val keyLength = source.getShort(offset).toInt() and 0xffff
            if (keyLength < 1 || offset + 2 + keyLength + 1 > source.capacity()) { i++; continue }
            var same = keyLength == phraseBytes.size
            if (same) for (j in phraseBytes.indices) {
                if (source.get(offset + 2 + j) != phraseBytes[j]) { same = false; break }
            }
            if (same) {
                var p = offset + 2 + keyLength
                val count = source.get(p++).toInt() and 0xff
                repeat(count) {
                    if (p >= source.capacity()) return results
                    val len = source.get(p++).toInt() and 0xff
                    if (len < 1 || p + len + 2 > source.capacity()) return results
                    val bytes = ByteArray(len)
                    val view = source.duplicate()
                    view.position(p)
                    view.get(bytes)
                    p += len
                    p += 2 // frequency is retained in the file for future reranking
                    if (results.size < limit) results.add(String(bytes, Charsets.UTF_8))
                }
                return results
            }
            i++
        }
        return emptyList()
    }

    private fun fnv1a(value: String): Int {
        var hash = 0x811c9dc5.toInt()
        for (b in value.toByteArray(Charsets.UTF_8)) {
            hash = hash xor (b.toInt() and 0xff)
            hash *= 0x01000193
        }
        return hash
    }

    @Synchronized
    fun close() {
        mapped = null
        indexedCount = 0
        dataStart = 0
        modelFile = null
    }
}
