package com.crazestudio.crazekeyboard

import android.content.Context
import kotlin.math.ln

/**
 * Small on-device n-gram model. It stores confirmed word boundaries, not raw messages.
 * Password/private/no-personalized-learning fields are filtered by the keyboard service.
 */
internal object UserLanguageModel {
    private const val PREFS = "craze_personal_language"
    private const val MAX_KEYS = 2400
    private const val MAX_COUNT = 500

    @Volatile private var counts: MutableMap<String, Int>? = null

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

    /** Learn one completed token and its preceding one-/two-token context. */
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

        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val edit = prefs.edit()
        updates.forEach { key ->
            val next = ((map[key] ?: 0) + 1).coerceAtMost(MAX_COUNT)
            map[key] = next
            edit.putInt(key, next)
        }
        if (map.size > MAX_KEYS) {
            val remove = map.entries.sortedBy { it.value }.take(map.size - MAX_KEYS + 200)
            remove.forEach { entry ->
                map.remove(entry.key)
                edit.remove(entry.key)
            }
        }
        edit.apply()
        return map["u:$token"] ?: 0
    }

    fun frequency(context: Context, word: String): Int =
        data(context)[ "u:${clean(word)}" ] ?: 0

    /** Numeric, privacy-safe signals for the tiny local suggestion ranker. */
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
            (kotlin.math.ln(1.0 + unigram) / kotlin.math.ln(501.0)).toFloat(),
            (kotlin.math.ln(1.0 + bigram) / kotlin.math.ln(501.0)).toFloat(),
            (kotlin.math.ln(1.0 + trigram) / kotlin.math.ln(501.0)).toFloat(),
            (token.length.coerceIn(1, 20) / 20f),
            if (Words.known(candidate)) 1f else 0f
        )
    }

    /**
     * Blend user history into base-model suggestions. Personal bigram/trigram candidates
     * can enter the list even when the generic model did not rank them.
     */
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
        val candidates = LinkedHashSet<String>()
        baseCandidates.filter { it.isNotBlank() }.forEach(candidates::add)
        if (previous.isNotEmpty()) {
            val bigramPrefix = "b:${previous.last()}|"
            map.forEach { (key, _) ->
                if (key.startsWith(bigramPrefix)) candidates.add(key.substring(bigramPrefix.length))
            }
        }
        if (previous.size >= 2) {
            val trigramPrefix = "t:${previous[0]}|${previous[1]}|"
            map.forEach { (key, _) ->
                if (key.startsWith(trigramPrefix)) candidates.add(key.substring(trigramPrefix.length))
            }
        }
        if (candidates.isEmpty()) return emptyList()

        val baseOrder = baseCandidates.withIndex().associate { it.value to it.index }
        fun score(candidate: String): Double {
            val token = clean(candidate)
            val unigram = map["u:$token"] ?: 0
            val bigram = if (previous.isNotEmpty()) map["b:${previous.last()}|$token"] ?: 0 else 0
            val trigram = if (previous.size >= 2) map["t:${previous[0]}|${previous[1]}|$token"] ?: 0 else 0
            return ln(1.0 + unigram) * 0.15 + ln(1.0 + bigram) * 1.7 +
                ln(1.0 + trigram) * 2.6 + (baseOrder[candidate]?.let { 0.4 / (it + 1) } ?: 0.0)
        }
        return candidates.sortedWith(compareByDescending<String> { score(it) }
            .thenBy { baseOrder[it] ?: Int.MAX_VALUE }).take(limit)
    }

    @Synchronized
    fun clear(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        counts = LinkedHashMap()
        TinyNeuralRanker.clear(context)
    }
}
