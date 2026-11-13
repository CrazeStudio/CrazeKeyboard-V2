package com.crazestudio.crazekeyboard

import android.content.Context
import kotlin.math.ln

/**
 * Small on-device n-gram model. It stores confirmed word boundaries, not raw messages.
 * Password/private/no-personalized-learning fields are filtered by the keyboard service.
 */
internal object UserLanguageModel {
    private const val PREFS = "craze_personal_language"
    private const val MAX_KEYS = 4000
    private const val EVICT_SLACK = 400
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

    /** Display form of a stored (lowercase) word: pronoun "I" fixed, sentence-start capitalisation optional. */
    private fun display(word: String, capitalize: Boolean): String = when {
        word == "i" -> "I"
        word.startsWith("i'") -> "I" + word.substring(1)
        capitalize -> word.replaceFirstChar { it.uppercase() }
        else -> word
    }

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
        if (map.size > MAX_KEYS) evict(map, edit)
        edit.apply()
        return map["u:$token"] ?: 0
    }

    /**
     * Make room by dropping the oldest entries that were seen only once or twice. The previous policy
     * dropped the lowest counts globally, which are always the entries that were just added, so a
     * full model could never learn anything new.
     */
    private fun evict(map: MutableMap<String, Int>, edit: android.content.SharedPreferences.Editor) {
        var toRemove = map.size - (MAX_KEYS - EVICT_SLACK)
        val rare = map.entries.iterator()
        while (rare.hasNext() && toRemove > 0) {
            val entry = rare.next()
            if (entry.value <= 2) {
                edit.remove(entry.key)
                rare.remove()
                toRemove--
            }
        }
        val oldest = map.entries.iterator()
        while (oldest.hasNext() && toRemove > 0) {
            val entry = oldest.next()
            edit.remove(entry.key)
            oldest.remove()
            toRemove--
        }
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
     * can enter the list even when the generic model did not rank them. Candidates are de-duplicated
     * case-insensitively, and personal words copy the base list's sentence-start capitalisation.
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
        val base = baseCandidates.filter { it.isNotBlank() }
        val capitalize = base.isNotEmpty() && base.all { it.first().isUpperCase() }
        // normalised token -> form shown to the user
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
        return candidates.entries
            .sortedWith(compareByDescending<Map.Entry<String, String>> { score(it.key) }
                .thenBy { baseOrder[it.key] ?: Int.MAX_VALUE })
            .take(limit)
            .map { it.value }
    }

    /**
     * Context-aware completion ranking for the word currently being typed.
     *
     * The candidate pool is widened beyond the few base suggestions: it also takes the
     * frequency-ranked lexicon completions, the completions the n-gram model expects after the
     * previous words, and the user's own words. Each is then scored on lexicon frequency, the
     * n-gram context and the user's history together.
     */
    fun rankPrefix(
        context: Context,
        precedingWords: List<String>,
        prefix: String,
        baseCandidates: List<String>,
        limit: Int = 3
    ): List<String> {
        val start = clean(prefix)
        if (start.isEmpty() || limit <= 0) return baseCandidates.take(limit)
        val previous = precedingWords.map(::clean).filter { it.isNotEmpty() }.takeLast(2)

        // Lexicon and n-gram lookups can take a moment the first time a prefix is seen, so they run
        // before the model lock is taken; typing (which learns under that lock) is never held up by them.
        if (!NextWordPredictor.isLoaded()) NextWordPredictor.loadAsync(context.applicationContext)
        val lexicon = Words.suggestPool(context, start, 12).map(::clean)
        val contextual = NextWordPredictor.predictForPrefix(previous, start, 8).map(::clean)

        return synchronized(this) {
            val map = data(context)
            val candidates = LinkedHashSet<String>()
            fun offer(candidate: String) {
                if (candidate.length >= start.length && candidate.length <= 40 && candidate.startsWith(start) &&
                    candidate != start && candidate.all { it.isLetter() || it == '\'' }) {
                    candidates.add(candidate)
                }
            }
            baseCandidates.forEach { offer(clean(it)) }
            lexicon.forEach(::offer)
            contextual.forEach(::offer)
            map.forEach { (key, _) ->
                when {
                    key.startsWith("u:$start") -> offer(key.substring(2))
                    previous.isNotEmpty() && key.startsWith("b:${previous.last()}|") ->
                        offer(key.substringAfter('|'))
                    previous.size >= 2 && key.startsWith("t:${previous[0]}|${previous[1]}|") ->
                        offer(key.substringAfterLast('|'))
                }
            }
            if (candidates.isEmpty()) return@synchronized baseCandidates.take(limit)

            val baseOrder = baseCandidates.withIndex().associate { clean(it.value) to it.index }
            val contextOrder = contextual.withIndex().associate { it.value to it.index }
            fun score(candidate: String): Double {
                val unigram = map["u:$candidate"] ?: 0
                val bigram = if (previous.isNotEmpty()) map["b:${previous.last()}|$candidate"] ?: 0 else 0
                val trigram = if (previous.size >= 2) map["t:${previous[0]}|${previous[1]}|$candidate"] ?: 0 else 0
                val base = baseOrder[candidate]?.let { 0.3 / (it + 1) } ?: 0.0
                val modelContext = contextOrder[candidate]?.let { 0.8 + 2.2 / (it + 1) } ?: 0.0
                val lexiconPrior = 0.22 * VocabularyIndex.rankScore(candidate) -
                    0.05 * (candidate.length - start.length)
                return ln(1.0 + unigram) * 0.2 + ln(1.0 + bigram) * 1.8 +
                    ln(1.0 + trigram) * 2.8 + base + modelContext + lexiconPrior
            }
            val scored = candidates.map { it to score(it) }
            scored.sortedWith(compareByDescending<Pair<String, Double>> { it.second }
                .thenBy { baseOrder[it.first] ?: Int.MAX_VALUE })
                .take(limit).map { (candidate, _) ->
                    when {
                        prefix.length > 1 && prefix.all { it.isUpperCase() } -> candidate.uppercase()
                        prefix.firstOrNull()?.isUpperCase() == true ->
                            candidate.replaceFirstChar { it.uppercase() }
                        else -> candidate
                    }
                }
        }
    }

    @Synchronized
    fun clear(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        counts = LinkedHashMap()
        TinyNeuralRanker.clear(context)
    }
}
