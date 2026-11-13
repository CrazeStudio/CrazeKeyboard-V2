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
import kotlin.math.ln

/**
 * Lazily builds a sorted ~30K prefix lexicon from the existing CWP phrase pack and keeps a
 * usage-frequency score for every word (summed from the phrase model's stored candidate
 * frequencies). The text file ("word<TAB>score" per line, sorted by word) is memory-mapped and
 * only the matching prefix range is decoded per lookup.
 *
 * v2: completions are ranked by frequency instead of alphabetically, so typing "th" offers
 * "the / that / this" instead of the alphabetically-first rare words.
 */
internal object VocabularyIndex {
    private const val MAGIC = 0x31505743
    private const val HEADER_SIZE = 12
    private const val INDEX_ENTRY_SIZE = 8
    private const val VOCAB_NAME = "en-US.v2.vocab"
    private const val LEGACY_VOCAB_NAME = "en-US.vocab"
    private const val MAX_SCAN = 6000      // wider than the largest single-letter range of the 30K lexicon
    private const val POOL = 16            // ranked completions kept per prefix
    private const val FIRST_LETTER_POOL = 700
    private const val CACHE_SIZE = 512
    private const val TAB: Byte = 9
    private const val NL: Byte = 10

    private val supplementalWords = listOf(
        "annalie", "ante", "aux", "bayer", "christoph", "césar", "elie", "guiry",
        "historia", "ik", "jaiya", "josip", "juliusz", "kaimana", "pinyin", "pld",
        "raina", "ramat", "sbcl", "sbf", "shinji", "sleepyhead", "spades", "syād",
        "ul", "zu", "zuzanna", "الله", "حصن"
    )

    private val tokenSplit = Regex("[^\\p{L}']+")

    @Volatile private var mapped: ByteBuffer? = null
    @Volatile private var offsets: IntArray = IntArray(0)
    @Volatile private var loading = false
    @Volatile private var failed = false
    private val main = Handler(Looper.getMainLooper())
    private val callbacks = CopyOnWriteArrayList<() -> Unit>()
    private val firstLetterCache = java.util.concurrent.ConcurrentHashMap<Char, List<String>>()
    private val prefixCache = object : LinkedHashMap<String, List<String>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<String>>?): Boolean =
            size > CACHE_SIZE
    }

    fun isLoaded(): Boolean = mapped != null

    private fun addTokens(target: HashMap<String, Long>, phrase: String, weight: Long) {
        phrase.lowercase().split(tokenSplit).forEach { word ->
            if (word.isNotEmpty() && word.length <= 40 && word.any { it.isLetter() }) {
                target[word] = (target[word] ?: 0L) + weight
            }
        }
    }

    private fun buildFromModel(context: Context): File? {
        val model = NextWordPredictor.ensureModelFile(context) ?: return null
        val directory = model.parentFile ?: return null
        // The v1 file had no frequencies; drop it so it is not left behind as dead weight.
        try { File(directory, LEGACY_VOCAB_NAME).delete() } catch (_: Exception) { }
        val destination = File(directory, VOCAB_NAME)
        if (destination.isFile && destination.length() > 0L) return destination
        val temporary = File(directory, "$VOCAB_NAME.tmp")
        return try {
            val words = HashMap<String, Long>(40_000)
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
                    for (k in 0 until keyLength) keyBytes[k] = source.get(offset + 2 + k)
                    addTokens(words, String(keyBytes, Charsets.UTF_8), 1L)

                    var position = offset + 2 + keyLength
                    val candidates = source.get(position++).toInt() and 0xff
                    for (candidate in 0 until candidates) {
                        if (position >= source.capacity()) break
                        val length = source.get(position++).toInt() and 0xff
                        if (length < 1 || position + length + 2 > source.capacity()) break
                        val candidateBytes = ByteArray(length)
                        for (k in 0 until length) candidateBytes[k] = source.get(position + k)
                        val frequency = source.getShort(position + length).toInt() and 0xffff
                        addTokens(words, String(candidateBytes, Charsets.UTF_8), 1L + frequency)
                        position += length + 2
                    }
                }
            }
            supplementalWords.forEach { word -> if (word !in words) words[word] = 1L }
            temporary.bufferedWriter(Charsets.UTF_8).use { writer ->
                words.keys.sorted().forEach { word ->
                    writer.append(word).append('\t')
                        .append((words[word] ?: 1L).coerceAtMost(2_000_000_000L).toString())
                        .append('\n')
                }
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
            val found = ArrayList<Int>(40_000)
            found.add(0)
            for (i in 0 until buffer.limit()) {
                if (buffer.get(i) == NL && i + 1 < buffer.limit()) found.add(i + 1)
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

    /** The word on line [index] (the text before the tab). */
    private fun wordAt(source: ByteBuffer, index: Int): String {
        val start = offsets[index]
        var end = start
        val limit = source.limit()
        while (end < limit) {
            val b = source.get(end)
            if (b == TAB || b == NL) break
            end++
        }
        if (end <= start) return ""
        val bytes = ByteArray(end - start)
        for (k in bytes.indices) bytes[k] = source.get(start + k)
        return String(bytes, Charsets.UTF_8)
    }

    /** The usage score on line [index] (the digits after the tab); 0 when absent. */
    private fun freqAt(source: ByteBuffer, index: Int): Long {
        var p = offsets[index]
        val limit = source.limit()
        while (p < limit) {
            val b = source.get(p)
            if (b == NL) return 0L
            if (b == TAB) break
            p++
        }
        p++
        var value = 0L
        while (p < limit) {
            val digit = source.get(p).toInt() - 48
            if (digit < 0 || digit > 9) break
            value = value * 10 + digit
            p++
        }
        return value
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

    /**
     * Ranking score for a lexicon word: log usage frequency, plus a bonus for the curated
     * frequency-ordered list in [Words]. Words outside both score only their dictionary bonus.
     */
    fun rankScore(word: String): Double {
        val value = word.lowercase()
        val source = mapped
        var score = 0.0
        if (source != null) {
            val index = lowerBound(source, value)
            if (index < offsets.size && wordAt(source, index) == value) score = ln(1.0 + freqAt(source, index))
        }
        return score + Words.dictBonus(value)
    }

    private fun scanRange(source: ByteBuffer, value: String, keep: Int): List<String> {
        var index = lowerBound(source, value)
        val scored = ArrayList<Pair<String, Double>>()
        var scanned = 0
        while (index < offsets.size && scanned < MAX_SCAN) {
            val word = wordAt(source, index)
            if (!word.startsWith(value)) break
            if (word != value) {
                val score = ln(1.0 + freqAt(source, index)) + Words.dictBonus(word) -
                    0.08 * (word.length - value.length)
                scored.add(word to score)
            }
            index++
            scanned++
        }
        scored.sortByDescending { it.second }
        return scored.take(keep).map { it.first }
    }

    /** Up to [limit] completions of [prefix], most frequent first. */
    fun suggest(prefix: String, limit: Int = 3): List<String> {
        val value = prefix.lowercase()
        if (value.isEmpty() || limit <= 0) return emptyList()
        val source = mapped ?: return emptyList()
        val cached = synchronized(prefixCache) { prefixCache[value] }
        val pool = cached ?: scanRange(source, value, POOL).also { computed ->
            synchronized(prefixCache) { prefixCache[value] = computed }
        }
        return pool.take(limit)
    }

    fun contains(word: String): Boolean {
        val value = word.lowercase()
        val source = mapped ?: return false
        val index = lowerBound(source, value)
        return index < offsets.size && wordAt(source, index) == value
    }

    /** The most frequent words starting with [first]; used as the pool for typo correction. */
    fun byFirstLetter(first: Char, limit: Int = 256): List<String> {
        val source = mapped ?: return emptyList()
        val key = first.lowercaseChar()
        val cached = firstLetterCache.computeIfAbsent(key) { scanRange(source, it.toString(), FIRST_LETTER_POOL) }
        return cached.take(limit)
    }
}
