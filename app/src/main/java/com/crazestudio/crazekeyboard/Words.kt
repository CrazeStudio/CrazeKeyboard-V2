package com.crazestudio.crazekeyboard

import android.content.Context

object Alts {
    val numberHints = mapOf(
        "q" to "1", "w" to "2", "e" to "3", "r" to "4", "t" to "5",
        "y" to "6", "u" to "7", "i" to "8", "o" to "9", "p" to "0"
    )
    val map = mapOf(
        "a" to "@àáâäæãåā", "e" to "èéêëēėę", "i" to "îïíīįì", "o" to "ôöòóœøōõ", "u" to "ûüùúū",
        "c" to "çćč", "n" to "ñń", "s" to "#ßśš", "y" to "ý", "z" to "žźż", "l" to ")ł", "d" to "\$ð",
        "f" to "%", "g" to "&", "h" to "-", "j" to "+", "k" to "(",
        "." to ",?!;:-…", "," to "'\"", "-" to "–—_",
        "*" to "•×·", "\"" to "“”„", "'" to "‘’", ":" to "؛∶",
        ";" to "؛", "!" to "¡", "?" to "¿", "@" to "©®",
        "#" to "№", "$" to "₹€£¥", "%" to "‰", "&" to "§",
        "+" to "±", "=" to "≠≈", "/" to "\\", "(" to "[{",
        ")" to "]}", "~" to "≈≋", "`" to "´", "|" to "¦",
        "•" to "·", "√" to "∛∜", "π" to "τφ", "÷" to "⁄",
        "×" to "✕", "^" to "↑", "∆" to "∇", "£" to "₹₽",
        "¢" to "¤", "€" to "₿", "¥" to "元", "°" to "℃℉",
        "{" to "<", "}" to ">", "©" to "®™", "®" to "©™",
        "™" to "℠", "[" to "(", "]" to ")", "<" to "≤≪",
        ">" to "≥≫", "_" to "—–", "–" to "—_", "—" to "–_"
    )
}

object Words {
    // Roughly ordered by frequency, so earlier words are suggested first.
    private val dict: List<String> = ("the be to of and a in that have it for not on with he as you do at this but his by from they we " +
        "say her she or an will my one all would there their what so up out if about who get which go me when make can like time no " +
        "just him know take people into year your good some could them see other than then now look only come its over think also " +
        "back after use two how our work first well way even new want because any these give day most us hello hi hey thanks thank " +
        "please sorry yes okay ok great nice love really very much more many where why while today tomorrow tonight morning " +
        "evening night friend friends family home house school phone call message messages text send sent reply meet meeting lunch " +
        "dinner breakfast coffee happy birthday wish wishes best welcome sure maybe later soon never always sometimes still again " +
        "should might must need needs help helps find found keep left right going gone done doing being been were was " +
        "are is am did does had has having made makes making said says tell told ask asked wanted feel feeling felt little big " +
        "long short high low old young next last every both each few those such own same another between before during without " +
        "through under further once here everything something anything nothing someone anyone everyone though " +
        "although however already almost enough early late quick quickly slow slowly easy hard important different possible " +
        "keyboard android game music video photo picture camera app apps code file files link online internet website " +
        "going coming doing thinking looking working wanting getting making taking having being saying seeing knowing " +
        "tell talk speak watch play read write learn study start stop open close give take bring buy pay cost price free " +
        "money time week month year weekend monday tuesday wednesday thursday friday saturday sunday january february march " +
        "april june july august september october november december yesterday sorry excuse understand remember forget believe " +
        "believe maybe probably actually definitely absolutely exactly totally literally basically honestly seriously").split(" ").filter { it.isNotEmpty() }.distinct()

    private val dictSet = dict.toHashSet()
    private val dictRank: Map<String, Int> = HashMap<String, Int>(dict.size * 2).also { map ->
        dict.forEachIndexed { index, word -> map[word] = index }
    }
    private val byFirst: Map<Char, List<String>> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        dict.groupBy { it.first() }
    }
    private val byPrefix: Map<String, List<String>> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        dict.groupBy { it.take(2) }
    }
    private val starters = listOf("I", "The", "Thanks")

    // Typing shortcuts that are almost never intended: missing apostrophes, a lone "i" and the
    // most common misspellings. Applied before the (more conservative) edit-distance search.
    private val commonFixes: Map<String, String> = mapOf(
        "i" to "I", "im" to "I'm", "ive" to "I've", "ill" to "ill", "id" to "id",
        "dont" to "don't", "cant" to "can't", "wont" to "won't", "isnt" to "isn't", "arent" to "aren't",
        "wasnt" to "wasn't", "werent" to "weren't", "didnt" to "didn't", "doesnt" to "doesn't",
        "couldnt" to "couldn't", "shouldnt" to "shouldn't", "wouldnt" to "wouldn't", "havent" to "haven't",
        "hasnt" to "hasn't", "hadnt" to "hadn't", "youre" to "you're", "theyre" to "they're",
        "thats" to "that's", "whats" to "what's", "theres" to "there's", "heres" to "here's",
        "wheres" to "where's", "itll" to "it'll", "youll" to "you'll", "youve" to "you've",
        "weve" to "we've", "theyve" to "they've", "thatll" to "that'll",
        "teh" to "the", "adn" to "and", "taht" to "that", "waht" to "what", "wiht" to "with",
        "thier" to "their", "recieve" to "receive", "recieved" to "received", "beleive" to "believe",
        "seperate" to "separate", "definately" to "definitely", "definatly" to "definitely",
        "occured" to "occurred", "untill" to "until", "wich" to "which", "becuase" to "because",
        "becasue" to "because", "freind" to "friend", "wierd" to "weird", "alot" to "a lot",
        "tommorow" to "tomorrow", "tomorow" to "tomorrow", "tommorrow" to "tomorrow",
        "thankyou" to "thank you", "goverment" to "government", "enviroment" to "environment",
        "adress" to "address", "accomodate" to "accommodate", "acheive" to "achieve",
        "calender" to "calendar", "comming" to "coming", "diffrent" to "different",
        "existance" to "existence", "foriegn" to "foreign", "happend" to "happened",
        "immediatly" to "immediately", "independant" to "independent", "knowlege" to "knowledge",
        "neccessary" to "necessary", "necesary" to "necessary", "noticable" to "noticeable",
        "occassion" to "occasion", "persistant" to "persistent", "posession" to "possession",
        "prefered" to "preferred", "publically" to "publicly", "realy" to "really",
        "reccomend" to "recommend", "recomend" to "recommend", "refered" to "referred",
        "relevent" to "relevant", "resturant" to "restaurant", "succesful" to "successful",
        "sucessful" to "successful", "suprise" to "surprise", "truely" to "truly", "writting" to "writing"
    )

    /**
     * Ranking bonus for the curated list above: 3.0 for the last entry up to 7.0 for the first.
     * Words outside it get no bonus. Used to combine with the lexicon's usage frequency.
     */
    internal fun dictBonus(word: String): Double {
        val rank = dictRank[word] ?: return 0.0
        return 3.0 + 4.0 * (1.0 - rank.toDouble() / dict.size)
    }

    fun known(w: String): Boolean {
        val value = w.lowercase()
        if (value in dictSet) return true
        val language = when {
            value.any { it in '\u0980'..'\u09FF' } -> "bn"
            value.any { it in '\u0900'..'\u097F' } -> "hi"
            else -> "en"
        }
        return if (language == "en") {
            VocabularyIndex.contains(value) || DownloadedVocabularyIndex.contains("en", value)
        } else DownloadedVocabularyIndex.contains(language, value)
    }

    fun learn(c: Context, w: String) {
        val lw = w.lowercase()
        if (lw in dictSet) return
        Prefs.learn(c, lw)
    }

    private fun cased(s: String, w: String) = when {
        w.length > 1 && w.all { it.isUpperCase() } -> s.uppercase()
        w.isNotEmpty() && w[0].isUpperCase() -> s.replaceFirstChar { it.uppercase() }
        else -> s
    }

    // True when a and b differ by exactly one insertion, deletion, substitution or adjacent swap.
    private fun near(a: String, b: String): Boolean {
        if (a == b) return false
        val la = a.length
        val lb = b.length
        if (Math.abs(la - lb) > 1) return false
        var i = 0
        var j = 0
        var edits = 0
        while (i < la && j < lb) {
            if (a[i] == b[j]) { i++; j++; continue }
            if (++edits > 1) return false
            if (la == lb && i + 1 < la && a[i] == b[j + 1] && a[i + 1] == b[j]) { i += 2; j += 2; continue }
            when {
                la > lb -> i++
                la < lb -> j++
                else -> { i++; j++ }
            }
        }
        edits += (la - i) + (lb - j)
        return edits <= 1
    }

    // Ranking used to pick between several equally-close correction candidates.
    private fun candidateScore(learned: List<String>, word: String): Double =
        VocabularyIndex.rankScore(word) + if (word in learned) 4.0 else 0.0

    // Conservative auto-correction. Order of checks:
    //  1. the user's own learned words are never "corrected";
    //  2. a table of near-certain fixes ("dont" -> "don't", "teh" -> "the", a lone "i" -> "I");
    //  3. real words of the lexicon are never touched;
    //  4. an unknown, plain-letter word with one close candidate that shares its first letter. With several
    //     close candidates the most frequent wins only if it clearly dominates the runner-up.
    // Three-letter words are only fixed when they are a simple swap ("teh" -> "the").
    fun correction(c: Context, w: String): String? {
        if (w.isEmpty() || w.length > 20 || w.any { !it.isLetter() }) return null
        val lower = w.lowercase()
        val learned = Prefs.learned(c)
        if (lower in learned) return null
        commonFixes[lower]?.let { fix ->
            if (fix == lower) return null
            return cased(fix, w).takeIf { it != w }
        }
        if (w.length < 3) return null
        if (w.drop(1).any { it.isUpperCase() }) return null
        if (lower in dictSet) return null
        val vocabReady = VocabularyIndex.isLoaded()
        if (vocabReady && known(lower)) return null

        val candidates = LinkedHashSet<String>()
        candidates.addAll(byFirst[lower[0]].orEmpty())
        candidates.addAll(learned)
        candidates.addAll(VocabularyIndex.byFirstLetter(lower[0], 700))
        val matches = candidates.filter { near(lower, it) }
        if (matches.isEmpty()) return null

        val best: String
        if (matches.size == 1) {
            best = matches[0]
        } else {
            // Without frequency data there is no safe way to choose between candidates.
            if (!vocabReady) return null
            val ranked = matches.map { it to candidateScore(learned, it) }.sortedByDescending { it.second }
            if (ranked[0].second - ranked[1].second < 3.0) return null
            best = ranked[0].first
        }
        if (lower.length == 3 && lower.toCharArray().sorted() != best.toCharArray().sorted()) return null
        return cased(best, w)
    }

    fun suggest(c: Context, w: String): List<String> = suggestPool(c, w, 3)

    /**
     * Up to [limit] completions (or close spellings) of [w], best first. Candidates come from the
     * user's learned words, the frequency-ranked lexicon and the curated list, and are scored on one
     * common scale so a frequent word beats a rare alphabetical neighbour.
     */
    fun suggestPool(c: Context, w: String, limit: Int): List<String> {
        if (limit <= 0) return emptyList()
        if (w.isEmpty()) return starters.take(limit)
        val lower = w.lowercase()
        val scored = LinkedHashMap<String, Double>()

        // Words the user has taught the keyboard rank above ordinary words of similar frequency.
        Prefs.learned(c).forEachIndexed { index, x ->
            if (x.startsWith(lower) && x != lower) scored[x] = 11.0 - minOf(index, 300) * 0.01
        }
        for (x in VocabularyIndex.suggest(lower, 12)) {
            if (x !in scored) scored[x] = VocabularyIndex.rankScore(x) - 0.08 * (x.length - lower.length)
        }
        // The curated list still answers while the lexicon is loading.
        val prefixCandidates = if (lower.length >= 2) byPrefix[lower.take(2)].orEmpty()
            else byFirst[lower.first()].orEmpty()
        for (x in prefixCandidates) {
            if (x.startsWith(lower) && x != lower && x !in scored) {
                scored[x] = VocabularyIndex.rankScore(x) - 0.08 * (x.length - lower.length)
            }
        }
        // Typo fallback is uncommon and only useful for longer, clearly mistyped words.
        if (scored.size < limit && lower.length >= 4) {
            val typoPool = LinkedHashSet<String>(dict)
            typoPool.addAll(VocabularyIndex.byFirstLetter(lower[0], 400))
            for (x in typoPool) {
                if (x !in scored && near(lower, x)) scored[x] = VocabularyIndex.rankScore(x) - 2.0
            }
        }
        val out = scored.entries.sortedByDescending { it.value }.map { cased(it.key, w) }.distinct().take(limit)
        return if (out.isEmpty()) listOf(w) else out
    }
    /**
     * Rank a captured QWERTY key path against the local dictionary. This is a lightweight
     * edit-distance heuristic, not a trained swipe model; the feature is therefore opt-in.
     */
    fun swipeCandidates(c: Context, keyPath: String, limit: Int = 3): List<String> {
        val path = keyPath.filter { it.isLetter() }.lowercase().take(24)
        if (path.length < 3 || limit <= 0) return emptyList()
        val learned = Prefs.learned(c)
        val candidates = LinkedHashSet<String>()
        candidates.addAll(dict)
        candidates.addAll(learned)
        candidates.addAll(VocabularyIndex.byFirstLetter(path.first(), 2500))
        fun editDistance(a: String, b: String): Int {
            if (kotlin.math.abs(a.length - b.length) > maxOf(3, path.length / 3)) return maxOf(a.length, b.length)
            var previous = IntArray(b.length + 1) { it }
            var current = IntArray(b.length + 1)
            for (i in a.indices) {
                current[0] = i + 1
                for (j in b.indices) {
                    val cost = if (a[i] == b[j]) 0 else 1
                    current[j + 1] = minOf(current[j] + 1, previous[j + 1] + 1, previous[j] + cost)
                }
                val swap = previous; previous = current; current = swap
            }
            return previous[b.length]
        }
        val maxDistance = maxOf(2, path.length / 3)
        return candidates.asSequence()
            .filter { it.length in 3..24 && it.all(Char::isLetter) }
            .map { word ->
                val edits = editDistance(path, word)
                val normalizedPenalty = edits.toDouble() / maxOf(path.length, word.length)
                val personalBonus = if (word in learned) 1.5 else 0.0
                val score = VocabularyIndex.rankScore(word) + personalBonus - normalizedPenalty * 11.0 - edits * 0.7
                Triple(word, edits, score)
            }
            .filter { it.second <= maxDistance }
            .sortedByDescending { it.third }
            .take(limit)
            .map { it.first }
            .toList()
    }

}
