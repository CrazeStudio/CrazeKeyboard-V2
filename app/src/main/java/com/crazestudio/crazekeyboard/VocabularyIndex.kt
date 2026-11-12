package com.crazestudio.crazekeyboard

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import java.io.ByteArrayInputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.CopyOnWriteArrayList
import java.util.zip.GZIPInputStream

/**
 * Sorted 30K lexicon, kept on disk and memory-mapped. The index records line offsets;
 * words are decoded only for the prefix range being queried.
 *
 * Asset: assets/en-US.vocab.b64 (base64(gzip(sorted UTF-8 words))).
 */
internal object VocabularyIndex {
    private const val ASSET = "en-US.vocab.b64"
    private const val FILE = "en-US.vocab"
    @Volatile private var mapped: ByteBuffer? = null
    @Volatile private var offsets: IntArray = IntArray(0)
    @Volatile private var loading = false
    @Volatile private var failed = false
    private val main = Handler(Looper.getMainLooper())
    private val callbacks = CopyOnWriteArrayList<() -> Unit>()

    fun isLoaded(): Boolean = mapped != null

    private fun prepare(context: Context): File? {
        val dir = File(context.filesDir, "language_models")
        if (!dir.exists() && !dir.mkdirs()) return null
        val destination = File(dir, FILE)
        if (destination.isFile && destination.length() > 0L) return destination
        val temporary = File(dir, "$FILE.tmp")
        return try {
            val encoded = context.assets.open(ASSET).bufferedReader(Charsets.US_ASCII).use { it.readText() }
            val compressed = Base64.decode(encoded, Base64.DEFAULT)
            GZIPInputStream(ByteArrayInputStream(compressed)).use { input ->
                temporary.outputStream().buffered().use { output -> input.copyTo(output, 32 * 1024) }
            }
            if (!temporary.isFile || temporary.length() == 0L) {
                temporary.delete()
                return null
            }
            if (!temporary.renameTo(destination)) {
                temporary.copyTo(destination, overwrite = true)
                temporary.delete()
            }
            destination.takeIf { it.isFile && it.length() > 0L }
        } catch (_: Exception) {
            temporary.delete()
            null
        }
    }

    private fun open(context: Context): Boolean {
        if (mapped != null) return true
        return try {
            val file = prepare(context.applicationContext) ?: return false
            val buffer = RandomAccessFile(file, "r").use { raf ->
                raf.channel.map(FileChannel.MapMode.READ_ONLY, 0, raf.length()).asReadOnlyBuffer()
            }
            val found = ArrayList<Int>(30000)
            found.add(0)
            for (i in 0 until buffer.limit()) {
                if (buffer.get(i) == 10.toByte() && i + 1 < buffer.limit()) found.add(i + 1)
            }
            if (found.isEmpty()) return false
            offsets = found.toIntArray()
            mapped = buffer
            true
        } catch (_: Exception) {
            false
        }
    }

    fun loadAsync(context: Context, onReady: (() -> Unit)? = null) {
        synchronized(this) {
            if (failed) return
            if (mapped != null) {
                onReady?.let { callback -> main.post { callback() } }
                return
            }
            onReady?.let(callbacks::add)
            if (loading) return
            loading = true
        }
        Thread({
            val success = open(context.applicationContext)
            synchronized(this) {
                loading = false
                if (!success) failed = true
            }
            val pending = callbacks.toList()
            callbacks.clear()
            if (pending.isNotEmpty()) main.post {
                pending.forEach { callback -> try { callback() } catch (_: Exception) { } }
            }
        }, "craze-vocabulary-load").apply { isDaemon = true; start() }
    }

    private fun wordAt(source: ByteBuffer, index: Int): String {
        val start = offsets[index]
        var end = start
        while (end < source.limit() && source.get(end) != 10.toByte()) end++
        if (end <= start) return ""
        val bytes = ByteArray(end - start)
        val view = source.duplicate()
        view.position(start)
        view.get(bytes)
        return String(bytes, Charsets.UTF_8)
    }

    private fun lowerBound(source: ByteBuffer, value: String): Int {
        var low = 0
        var high = offsets.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (wordAt(source, mid).compareTo(value) < 0) low = mid + 1 else high = mid
        }
        return low
    }

    fun suggest(prefix: String, limit: Int = 3): List<String> {
        val value = prefix.lowercase()
        if (value.isEmpty() || limit <= 0) return emptyList()
        val source = mapped ?: return emptyList()
        var index = lowerBound(source, value)
        val out = ArrayList<String>(limit)
        while (index < offsets.size && out.size < limit) {
            val word = wordAt(source, index++)
            if (!word.startsWith(value)) break
            if (word != value) out.add(word)
        }
        return out
    }

    fun contains(word: String): Boolean {
        val value = word.lowercase()
        val source = mapped ?: return false
        val index = lowerBound(source, value)
        return index < offsets.size && wordAt(source, index) == value
    }

    /** Candidate list for conservative same-first-letter typo correction. */
    fun byFirstLetter(first: Char, limit: Int = 5000): List<String> =
        suggest(first.lowercaseChar().toString(), limit)
}
