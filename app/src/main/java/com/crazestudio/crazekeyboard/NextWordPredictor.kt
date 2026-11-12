package com.crazestudio.crazekeyboard

import android.content.Context
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import android.os.Handler
import android.os.Looper
import java.util.zip.GZIPInputStream

/**
 * Memory-mapped word-level n-gram predictor.
 *
 * The compressed language pack is an optional APK asset:
 * assets/en-US.cwp.gz. It is expanded once into private app storage and then
 * memory-mapped read-only. The expanded model is never loaded into a Kotlin
 * heap collection.
 */
internal object NextWordPredictor {
    private const val ASSET_NAME = "en-US.cwp.gz"
    private const val MODEL_NAME = "en-US.cwp"
    private const val MAGIC = 0x31505743 // "CWP1" in little endian
    private const val HEADER_SIZE = 12
    private const val INDEX_ENTRY_SIZE = 8

    @Volatile private var mapped: ByteBuffer? = null
    @Volatile private var indexedCount = 0
    @Volatile private var dataStart = 0
    @Volatile private var loading = false
    @Volatile private var loadFailed = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val callbacks = CopyOnWriteArrayList<() -> Unit>()

    private fun validModel(file: File): Boolean {
        if (!file.isFile || file.length() < HEADER_SIZE) return false
        return try {
            RandomAccessFile(file, "r").use { raf ->
                raf.readInt().let { java.lang.Integer.reverseBytes(it) == MAGIC } &&
                    raf.readInt().let { java.lang.Integer.reverseBytes(it) == 1 }
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun modelFile(context: Context): File? {
        val directory = File(context.filesDir, "language_models")
        if (!directory.exists() && !directory.mkdirs()) return null
        val destination = File(directory, MODEL_NAME)
        if (validModel(destination)) return destination

        val temporary = File(directory, "$MODEL_NAME.tmp")
        return try {
            context.assets.open(ASSET_NAME).use { input ->
                GZIPInputStream(input.buffered()).use { gzip ->
                    temporary.outputStream().buffered().use { output ->
                        gzip.copyTo(output, 64 * 1024)
                    }
                }
            }
            if (!validModel(temporary)) {
                temporary.delete()
                return null
            }
            if (destination.exists() && !destination.delete()) {
                temporary.delete()
                return null
            }
            if (!temporary.renameTo(destination)) {
                temporary.copyTo(destination, overwrite = true)
                temporary.delete()
            }
            destination.takeIf { validModel(it) }
        } catch (_: Exception) {
            temporary.delete()
            null
        }
    }

    @Synchronized
    private fun open(context: Context): ByteBuffer? {
        mapped?.let { return it }
        return try {
            val file = modelFile(context.applicationContext) ?: return null
            val buffer = RandomAccessFile(file, "r").use { raf ->
                raf.channel.map(FileChannel.MapMode.READ_ONLY, 0, raf.length())
                    .order(ByteOrder.LITTLE_ENDIAN)
            }
            if (buffer.getInt(0) != MAGIC || buffer.getInt(4) != 1) return null
            val count = buffer.getInt(8)
            if (count < 1 || count > (buffer.capacity() - HEADER_SIZE) / INDEX_ENTRY_SIZE) return null
            val start = HEADER_SIZE + count * INDEX_ENTRY_SIZE
            if (start > buffer.capacity()) return null
            indexedCount = count
            dataStart = start
            mapped = buffer.asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN)
            mapped
        } catch (_: Exception) {
            // A missing or invalid language pack is normal during development.
            null
        }
    }

    /**
     * Load the optional language model only after a real next-word lookup is requested.
     * GZIP extraction and memory mapping run off the keyboard UI thread. The mapped
     * file is demand-paged by the OS, so only index/data pages touched by lookups enter RAM.
     */
    fun loadAsync(context: Context, onReady: (() -> Unit)? = null) {
        synchronized(this) {
            if (loadFailed) return
            if (mapped != null) {
                onReady?.let { mainHandler.post(it) }
                return
            }
            onReady?.let { callbacks.add(it) }
            if (loading) return
            loading = true
        }
        Thread({
            val result = open(context.applicationContext)
            synchronized(this) {
                loading = false
                if (result == null) loadFailed = true
            }
            drainCallbacks()
        }, "craze-language-model").apply {
            isDaemon = true
            start()
        }
    }

    private fun drainCallbacks() {
        val pending = callbacks.toList()
        callbacks.clear()
        if (pending.isNotEmpty()) mainHandler.post { pending.forEach { callback ->
            try { callback() } catch (_: Exception) { }
        } }
    }

    /** Return up to three likely next words for the text immediately before the cursor. */
    fun predict(context: Context, textBeforeCursor: String, limit: Int = 3): List<String> {
        if (textBeforeCursor.isBlank() || limit <= 0) return emptyList()
        val tokens = textBeforeCursor.lowercase(Locale.ROOT)
            .split(Regex("[^\\p{L}']+"))
            .filter { it.isNotBlank() }
            .takeLast(3)
        if (tokens.isEmpty()) return emptyList()
        val buffer = mapped ?: return emptyList()
        val ranked = LinkedHashSet<String>(limit)
        // Prefer the most specific context, then back off to shorter contexts.
        for (size in minOf(3, tokens.size) downTo 1) {
            val phrase = tokens.takeLast(size).joinToString(" ")
            lookup(buffer, phrase, limit).forEach { if (ranked.size < limit) ranked.add(it) }
            if (ranked.size >= limit) break
        }
        return ranked.toList()
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
                    p += len + 2 // skip the stored frequency after each candidate
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
    }
}
