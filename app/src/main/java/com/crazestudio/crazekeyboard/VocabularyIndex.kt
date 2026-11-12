package com.crazestudio.crazekeyboard

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Lazily builds a sorted 30K prefix lexicon from the existing CWP phrase pack.
 * Phrase keys and candidate tokens contain 29,971 distinct words; the 29 entries
 * absent from phrase data are added below. The resulting text file is memory-mapped,
 * and only the matching prefix range is decoded for each lookup.
 */
internal object VocabularyIndex {
    private const val MAGIC = 0x31505743
    private const val HEADER_SIZE = 12
    private const val INDEX_ENTRY_SIZE = 8
    private const val MODEL_NAME = "en-US.cwp"
    private const val VOCAB_NAME = "en-US.vocab"

    private val supplementalWords = listOf(
        "annalie", "ante", "aux", "bayer", "christoph", "césar", "elie", "guiry",
        "historia", "ik", "jaiya", "josip", "juliusz", "kaimana", "pinyin", "pld",
        "raina", "ramat", "sbcl", "sbf", "shinji", "sleepyhead", "spades", "syād",
        "ul", "zu", "zuzanna", "الله", "حصن"
    )

    @Volatile private var mapped: ByteBuffer? = null
    @Volatile private var offsets: IntArray = IntArray(0)
    @Volatile private var loading = false
    @Volatile private var failed = false
    private val main = Handler(Looper.getMainLooper())
    private val callbacks = CopyOnWriteArrayList<() -> Unit>()

    fun isLoaded(): Boolean = mapped != null

    private fun addTokens(target: MutableSet<String>, phrase: String) {
        phrase.lowercase().split(Regex("[^\\p{L}']+")).forEach { word ->
            if (word.isNotEmpty() && word.length <= 40 && word.any { it.isLetter() }) target.add(word)
        }
    }

    private fun buildFromModel(context: Context): File? {
        val model = NextWordPredictor.ensureModelFile(context) ?: return null
        val directory = model.parentFile ?: return null
        val destination = File(directory, VOCAB_NAME)
        if (destination.isFile && destination.length() > 0L) return destination
        val temporary = File(directory, "$VOCAB_NAME.tmp")
        return try {
            val words = HashSet<String>(32_000)
            RandomAccessFile(model, "r").use { raf ->
                val source = raf.channel.map(FileChannel.MapMode.READ_ONLY, 0, raf.length())
                    .order(ByteOrder.LITTLE_ENDIAN)
                if (source.capacity() < HEADER_SIZE || source.getInt(0) != MAGIC || source.getInt(4) != 1) return null
                val count = source.getInt(8)
                if (count < 1 || count > (source.capacity() - HEADER_SIZE) / INDEX_ENTRY_SIZE) return null
                val dataStart = HEADER_SIZE + count * INDEX_ENTRY_SIZE

                for (index in 0 until count) {
                    val slot = HEADER_SIZE + index * INDEX_ENTRY_SIZE
                    val offset = source.getInt(slot + 4)
                    if (offset < dataStart || offset + 3 > source.capacity()) continue
                    val keyLength = source.getShort(offset).toInt() and 0xffff
                    if (keyLength < 1 || offset + 2 + keyLength + 1 > source.capacity()) continue
                    val keyBytes = ByteArray(keyLength)
                    val keyView = source.duplicate()
                    keyView.position(offset + 2)
                    keyView.get(keyBytes)
                    addTokens(words, String(keyBytes, Charsets.UTF_8))

                    var position = offset + 2 + keyLength
                    val candidates = source.get(position++).toInt() and 0xff
                    var valid = true
                    for (candidate in 0 until candidates) {
                        if (position >= source.capacity()) { valid = false; break }
                        val length = source.get(position++).toInt() and 0xff
                        if (length < 1 || position + length + 2 > source.capacity()) { valid = false; break }
                        val candidateBytes = ByteArray(length)
                        val candidateView = source.duplicate()
                        candidateView.position(position)
                        candidateView.get(candidateBytes)
                        addTokens(words, String(candidateBytes, Charsets.UTF_8))
                        position += length + 2
                    }
                    if (!valid) continue
                }
            }
            words.addAll(supplementalWords)
            temporary.bufferedWriter(Charsets.UTF_8).use { writer ->
                words.sorted().forEach { writer.append(it).append('\\n') }
            }
            if (!temporary.isFile || temporary.length() == 0L) {
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
            destination.takeIf { it.isFile && it.length() > 0L }
        } catch (_: Exception) {
            temporary.delete()
            null
        }
    }

    private fun open(context: Context): Boolean {
        if (mapped != null) return true
        return try {
            val file = buildFromModel(context.applicationContext) ?: return false
            val buffer = RandomAccessFile(file, "r").use { raf ->
                raf.channel.map(FileChannel.MapMode.READ_ONLY, 0, raf.length()).asReadOnlyBuffer()
            }
            val found = ArrayList<Int>(30_000)
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
        }, "craze-vocabulary-index").apply { isDaemon = true; start() }
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

    fun byFirstLetter(first: Char, limit: Int = 5000): List<String> =
        suggest(first.lowercaseChar().toString(), limit)
}
