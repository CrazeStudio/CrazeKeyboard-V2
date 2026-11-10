package com.crazestudio.crazekeyboard

import android.content.Context
import java.util.concurrent.Executors
import kotlin.math.abs

object Alts {
    val map by lazy(LazyThreadSafetyMode.PUBLICATION) {
        mapOf(
            "a" to "àáâäæãåā", "e" to "èéêëēėę", "i" to "îïíīį", "o" to "ôöòóœøōõ", "u" to "ûüùúū",
            "c" to "çćč", "n" to "ñń", "s" to "ßśš", "y" to "ý", "z" to "žźż", "l" to "ł", "d" to "ð",
            "." to ",?!;:-…", "," to "'\"", "-" to "–—_"
        )
    }
}

/**
 * Small offline typing engine.
 *
 * The bundled dictionary is loaded once, on a background thread, after suggestions are first
 * requested. Until it is ready, a compact built-in vocabulary keeps the candidate strip useful
 * without blocking the IME's main thread or the key-commit path.
 */
object Words {
    private val fallbackWords by lazy(LazyThreadSafetyMode.PUBLICATION) {
        (
            "the be to of and a in that have it for not on with he as you do at this but his by from they we " +
            "say her she or an will my one all would there their what so up out if about who get which go me when make can like time no " +
            "just him know take people into year your good some could them see other than then now look only come its over think also " +
            "back after use two how our work first well way even new want because any these give day most us hello hi hey thanks thank " +
            "please sorry yes okay ok great nice love really very much more many where why while today tomorrow tonight morning " +
            "evening night friend friends family home house school phone call message messages text send sent reply meet meeting lunch " +
            "dinner breakfast coffee happy birthday wish wishes best welcome sure maybe later soon never always sometimes still again " +
            "should would could might must need needs help helps find found keep left right going gone done doing being been were was " +
            "are is am did does had has having made makes making said says tell told ask asked want wanted feel feeling felt little big " +
            "long short high low old young next last every both each few those such own same another between before during without " +
            "through under further once here everything something anything nothing someone anyone everyone though although however " +
            "already almost enough early late quick quickly slow slowly easy hard important different possible " +
            "keyboard android phone game music video photo picture camera app apps code file files link online internet website"
        ).split(Regex("\\s+")).filter { it.isNotBlank() }
    }

    private val frequentWords by lazy(LazyThreadSafetyMode.PUBLICATION) {
        (
            "the be to of and a in that have i it for not on with he as you do at this but his by " +
            "from they we say her she or an will my one all would there their what so up out if about " +
            "who get which go me when make can like time no just him know take people into year your " +
            "good some could them see other than then now look only come its over think also back after " +
            "use two how our work first well way even new want because any these give day most us"
        ).split(Regex("\\s+"))
    }

    private val nextWordMap by lazy(LazyThreadSafetyMode.PUBLICATION) {
        mapOf(
            "how" to "are is do can did was much about to",
            "what" to "is are do did can about if happened",
            "where" to "are is do can did",
            "when" to "is are do did will can",
            "i" to "am am not have will can think know want",
            "you" to "are can have will should want need know",
            "we" to "are can have will should need",
            "they" to "are have will can were",
            "it" to "is was will can has",
            "this" to "is was will can",
            "that" to "is was will can",
            "thank" to "you",
            "good" to "morning evening night luck job",
            "see" to "you it them",
            "love" to "you it this",
            "have" to "a the to been",
            "i'm" to "not going sure happy",
            "im" to "not going sure happy",
            "are" to "you we they there",
            "can" to "you i we they",
            "do" to "you i we they"
        )
    }

    @Volatile private var cachedDictionary: List<String>? = null
    @Volatile private var cachedSet: Set<String>? = null
    @Volatile private var cachedRanks: Map<String, Int>? = null
    private var loading = false
    private val loadCallbacks = ArrayList<() -> Unit>()
    private val loader by lazy {
        Executors.newSingleThreadExecutor { task ->
            Thread(task, "CrazeDictionary").apply { isDaemon = true }
        }
    }

    fun isLoaded(): Boolean = cachedDictionary != null

    /**
     * Starts dictionary loading once. All callbacks are invoked on the loader thread; callers
     * that update UI must post back to their main thread.
     */
    fun preload(context: Context, onLoaded: (() -> Unit)? = null) {
        var startLoad = false
        val alreadyLoaded: Boolean
        synchronized(this) {
            alreadyLoaded = cachedDictionary != null
            if (!alreadyLoaded) {
                if (onLoaded != null) loadCallbacks.add(onLoaded)
                if (!loading) {
                    loading = true
                    startLoad = true
                }
            }
        }
        if (alreadyLoaded) {
            onLoaded?.invoke()
            return
        }
        if (!startLoad) return

        val appContext = context.applicationContext
        loader.execute {
            try {
                loadDictionary(appContext)
            } finally {
                val callbacks = synchronized(this) {
                    loading = false
                    loadCallbacks.toList().also { loadCallbacks.clear() }
                }
                callbacks.forEach { callback ->
                    try { callback() } catch (_: Exception) { }
                }
            }
        }
    }

    private fun loadDictionary(context: Context) {
        if (cachedDictionary != null) return
        val bundled = try {
            context.assets.open("main_en-US.dict").bufferedReader().useLines { lines ->
                lines.map { it.trim().lowercase() }
                    .filter { it.length in 1..40 && it.all { ch -> ch.isLetter() || ch == '\'' || ch == '-' } }
                    .distinct()
                    .toList()
            }
        } catch (_: Exception) {
            emptyList()
        }
        val all = (frequentWords + bundled + fallbackWords).distinct()
        synchronized(this) {
            if (cachedDictionary == null) {
                cachedSet = all.toHashSet()
                cachedRanks = all.withIndex().associate { it.value to it.index }
                cachedDictionary = all
            }
        }
    }

    fun known(context: Context, word: String): Boolean {
        val lower = word.trim().lowercase()
        val dictionaryKnows = cachedSet?.contains(lower) ?: fallbackWords.contains(lower)
        return dictionaryKnows || Prefs.learned(context).any { it.equals(lower, true) }
    }

    // Compatibility overload for older callers that do not have a Context.
    fun known(word: String): Boolean {
        val lower = word.trim().lowercase()
        return fallbackWords.contains(lower) || cachedSet?.contains(lower) == true || Prefs.isLearned(lower)
    }

    private fun cased(candidate: String, typed: String): String = when {
        typed.length > 1 && typed.all { it.isUpperCase() } -> candidate.uppercase()
        typed.isNotEmpty() && typed[0].isUpperCase() -> candidate.replaceFirstChar { it.uppercase() }
        else -> candidate
    }

    private fun rank(word: String, learned: List<String>): Int {
        val learnedIndex = learned.indexOfFirst { it.equals(word, true) }
        if (learnedIndex >= 0) return -10_000 + learnedIndex
        return cachedRanks?.get(word) ?: Int.MAX_VALUE
    }

    fun suggest(context: Context, word: String, previousWord: String = ""): List<String> {
        val words = cachedDictionary
        val learned = Prefs.learned(context)
        val availableWords = words ?: (frequentWords + fallbackWords).distinct()

        if (word.isBlank()) {
            val contextual = nextWordMap[previousWord.lowercase()].orEmpty()
                .split(Regex("\\s+")).filter { it.isNotBlank() }
            val ordered = (contextual + frequentWords + learned + availableWords).distinct()
                .filter { it in availableWords || learned.any { learnedWord -> learnedWord.equals(it, true) } }
            return ordered.take(3).map { cased(it, "") }
        }

        val lower = word.lowercase()
        val prefix = (learned + availableWords).distinct()
            .asSequence()
            .filter { it.startsWith(lower) && !it.equals(lower, true) }
            .sortedBy { rank(it, learned) }
            .take(3)
            .map { cased(it, word) }
            .toList()
        if (prefix.isNotEmpty()) return prefix

        // Avoid costly typo scans on the lightweight fallback vocabulary.
        if (words == null) return listOf(word)

        if (lower.length >= 3 && lower.length <= 20) {
            return words.asSequence()
                .map { it to editDistance(lower, it, 2) }
                .filter { it.second in 1..2 }
                .sortedWith(compareBy<Pair<String, Int>> { it.second }.thenBy { rank(it.first, learned) })
                .take(3)
                .map { cased(it.first, word) }
                .toList()
        }
        return listOf(word)
    }

    fun correction(context: Context, typed: String): String? {
        val lower = typed.lowercase()
        val words = cachedDictionary ?: return null
        if (lower.length !in 3..20 || known(context, lower)) return null
        val learned = Prefs.learned(context)
        val best = words.asSequence()
            .map { it to editDistance(lower, it, 1) }
            .filter { it.second == 1 }
            .minWithOrNull(compareBy<Pair<String, Int>> { it.second }.thenBy { rank(it.first, learned) })
            ?: return null
        // Require a close match; avoid aggressive replacement of unfamiliar names.
        return cased(best.first, typed)
    }

    /** Optimal-string-alignment distance, with a cutoff to bound work on the typing path. */
    private fun editDistance(a: String, b: String, limit: Int): Int {
        if (abs(a.length - b.length) > limit) return limit + 1
        val rows = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) rows[i][0] = i
        for (j in 0..b.length) rows[0][j] = j
        for (i in 1..a.length) {
            var rowMin = Int.MAX_VALUE
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                rows[i][j] = minOf(
                    rows[i - 1][j] + 1,
                    rows[i][j - 1] + 1,
                    rows[i - 1][j - 1] + cost
                )
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    rows[i][j] = minOf(rows[i][j], rows[i - 2][j - 2] + 1)
                }
                rowMin = minOf(rowMin, rows[i][j])
            }
            if (rowMin > limit) return limit + 1
        }
        return rows[a.length][b.length]
    }
}
