package com.crazestudio.crazekeyboard

import android.content.Context
import kotlin.math.ln

/**
 * Small on-device n-gram model. Stores confirmed word boundaries (never raw sentences or passwords),
 * and batches SharedPreferences writes until flush() or every 10 words to reduce I/O churn.
 */
internal object UserLanguageModel {
    private const val PREFS = "craze_personal_language"
    private const val MAX_KEYS = 4000
    private const val EVICT_SLACK = 400
    private const val MAX_COUNT = 500

    @Volatile private var counts: MutableMap<String, Int>? = null
    private val pendingWrites = HashMap<String, Int>()
    private val pendingRemovals = HashSet<String>()
    private var updatesSinceFlush = 0

    private fun data(context: Context): MutableMap<String, Int> {
        counts?.let { return it }
        return synchronized(this) {
            counts ?: context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .all.entries.mapNotNull { (key, value) ->
                    (value as? Int)?.let { key to it }
                }.toMap(LinkedHashMap()).also { counts = it }
        }
    }

    private fun clean(value: String): String =
        value.lowercase().filter { it.isLetter() || it == '\'' }.take(40)

    private fun display(word: String, capitalize: Boolean): String = when {
        word == "i" -> "I"
        word.startsWith("i'") -> "I" + word.substring(1)
        capitalize -> word.replaceFirstChar { it.uppercase() }
        else -> word
    }

    @Synchronized
    fun observe(context: Context, previousWords: List<String>, word: String): Int {
        val token = clean(word)
        if (token.length < 2) return 0
        val previous = previousWords.map(::clean).filter { it.isNotEmpty() }.takeLast(2)
        val map = data(context)
        val updates = ArrayList<String>(3)
        updates.add("u:$token")
        if (previous.isNotEmpty()) updates.add("b:${previous.last()}|$token")
        if (previous.size >= 2) updates.add("t:${previous[0]}|${previous[1]}|$token")

        updates.forEach { key ->
            val next = ((map[key] ?: 0) + 1).coerceAtMost(MAX_COUNT)
            map[key] = next
            pendingRemovals.remove(key)
            pendingWrites[key] = next
        }
        if (map.size > MAX_KEYS) evict(map)
        updatesSinceFlush++
        if (updatesSinceFlush >= 10) flush(context)
        return map["u:$token"] ?: 0
    }

    @Synchronized
    fun flush(context: Context) {
        if (pendingWrites.isEmpty() && pendingRemovals.isEmpty()) return
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val edit = prefs.edit()
        pendingRemovals.forEach { edit.remove(it) }
        pendingWrites.forEach { (k, v) -> edit.putInt(k, v) }
        pendingRemovals.clear()
        pendingWrites.clear()
        updatesSinceFlush = 0
        edit.apply()
    }

    private fun evict(map: MutableMap<String, Int>) {
        var toRemove = map.size - (MAX_KEYS - EVICT_SLACK)
        val rare = map.entries.iterator()
        while (rare.hasNext() && toRemove > 0) {
            val entry = rare.next()
            if (entry.value <= 2) {
                pendingWrites.remove(entry.key)
                pendingRemovals.add(entry.key)
                rare.remove()
                toRemove--
            }
        }
        val oldest = map.entries.iterator()
        while (oldest.hasNext() && toRemove > 0) {
            val entry = oldest.next()
            pendingWrites.remove(entry.key)
            pendingRemovals.add(entry.key)
            oldest.remove()
            toRemove--
        }
    }

    @Synchronized
    fun rankingFeatures(
        context: Context,
        precedingWords: List<String>,
        candidate: String,
        baseRank: Int,
        candidateCount: Int
    ): FloatArray {
        val previous = precedingWords.map(::clean).filter { it.isNotEmpty() }.takeLast(2)
        val token = clean(candidate)
        val map = data(context)
        val unigram = map["u:$token"] ?: 0
        val bigram = if (previous.isNotEmpty()) map["b:${previous.last()}|$token"] ?: 0 else 0
        val trigram = if (previous.size >= 2) map["t:${previous[0]}|${previous[1]}|$token"] ?: 0 else 0
        return floatArrayOf(
            if (candidateCount <= 1) 1f else 1f - baseRank.toFloat() / (candidateCount - 1).coerceAtLeast(1),
            (ln(1.0 + unigram) / ln(501.0)).toFloat(),
            (ln(1.0 + bigram) / ln(501.0)).toFloat(),
            (ln(1.0 + trigram) / ln(501.0)).toFloat(),
            (token.length.coerceIn(1, 20) / 20f),
            if (Words.known(candidate)) 1f else 0f
        )
    }

    @Synchronized
    fun rank(
        context: Context,
        precedingWords: List<String>,
        baseCandidates: List<String>,
        limit: Int = 3
    ): List<String> {
        if (limit <= 0) return emptyList()
        val previous = precedingWords.map(::clean).filter { it.isNotEmpty() }.takeLast(2)
        val map = data(context)
        val base = baseCandidates.filter { it.isNotBlank() }
        val capitalize = base.isNotEmpty() && base.all { it.first().isUpperCase() }
        val candidates = LinkedHashMap<String, String>()
        base.forEach { candidates.putIfAbsent(clean(it).ifEmpty { it.lowercase() }, it) }
        if (previous.isNotEmpty()) {
            val bigramPrefix = "b:${previous.last()}|"
            map.forEach { (key, _) ->
                if (key.startsWith(bigramPrefix)) {
                    val word = key.substring(bigramPrefix.length)
                    candidates.putIfAbsent(word, display(word, capitalize))
                }
            }
        }
        if (previous.size >= 2) {
            val trigramPrefix = "t:${previous[0]}|${previous[1]}|"
            map.forEach { (key, _) ->
                if (key.startsWith(trigramPrefix)) {
                    val word = key.substring(trigramPrefix.length)
                    candidates.putIfAbsent(word, display(word, capitalize))
                }
            }
        }
        if (candidates.isEmpty()) return emptyList()

        val baseOrder = base.withIndex().associate { clean(it.value).ifEmpty { it.value.lowercase() } to it.index }
        fun score(token: String): Double {
            val unigram = map["u:$token"] ?: 0
            val bigram = if (previous.isNotEmpty()) map["b:${previous.last()}|$token"] ?: 0 else 0
            val trigram = if (previous.size >= 2) map["t:${previous[0]}|${previous[1]}|$token"] ?: 0 else 0
            return ln(1.0 + unigram) * 0.15 + ln(1.0 + bigram) * 1.7 +
                ln(1.0 + trigram) * 2.6 + (baseOrder[token]?.let { 0.4 / (it + 1) } ?: 0.0)
        }
        val pool = candidates.entries
            .sortedWith(compareByDescending<Map.Entry<String, String>> { score(it.key) }
                .thenBy { baseOrder[it.key] ?: Int.MAX_VALUE })
            .take(limit + 2)
            .map { it.value }
        return TinyNeuralRanker.rank(context, previous, pool, limit)
    }

    fun rankPrefix(
        context: Context,
        precedingWords: List<String>,
        prefix: String,
        limit: Int = 3
    ): List<String> {
        val start = clean(prefix)
        if (start.isEmpty() || limit <= 0) return emptyList()
        val previous = precedingWords.map(::clean).filter { it.isNotEmpty() }.takeLast(2)
        val lexicon = Words.suggestPool(context, start, 10).map(::clean)

        return synchronized(this) {
            val map = data(context)
            val candidates = LinkedHashSet<String>()
            fun offer(candidate: String) {
                if (candidate.length >= start.length && candidate.length <= 40 && candidate.startsWith(start) &&
                    candidate != start && candidate.all { it.isLetter() || it == '\'' }) {
                    candidates.add(candidate)
                }
            }
            lexicon.forEach(::offer)
            map.forEach { (key, _) ->
                when {
                    key.startsWith("u:$start") -> offer(key.substring(2))
                    previous.isNotEmpty() && key.startsWith("b:${previous.last()}|") ->
                        offer(key.substringAfter('|'))
                    previous.size >= 2 && key.startsWith("t:${previous[0]}|${previous[1]}|") ->
                        offer(key.substringAfterLast('|'))
                }
            }
            if (candidates.isEmpty()) return@synchronized emptyList()

            fun score(candidate: String): Double {
                val unigram = map["u:$candidate"] ?: 0
                val bigram = if (previous.isNotEmpty()) map["b:${previous.last()}|$candidate"] ?: 0 else 0
                val trigram = if (previous.size >= 2) map["t:${previous[0]}|${previous[1]}|$candidate"] ?: 0 else 0
                val lexiconPrior = 0.25 * Words.rankScore(candidate) - 0.06 * (candidate.length - start.length)
                return ln(1.0 + unigram) * 0.2 + ln(1.0 + bigram) * 1.8 +
                    ln(1.0 + trigram) * 2.8 + lexiconPrior
            }
            val pool = candidates.sortedByDescending(::score).take(limit + 2)
            val reranked = TinyNeuralRanker.rank(context, previous, pool, limit)
            reranked.map { candidate ->
                when {
                    prefix.length > 1 && prefix.all { it.isUpperCase() } -> candidate.uppercase()
                    prefix.firstOrNull()?.isUpperCase() == true -> candidate.replaceFirstChar { it.uppercase() }
                    else -> candidate
                }
            }
        }
    }

    @Synchronized
    fun clear(context: Context) {
        pendingWrites.clear()
        pendingRemovals.clear()
        updatesSinceFlush = 0
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        counts = LinkedHashMap()
        TinyNeuralRanker.clear(context)
    }
}
