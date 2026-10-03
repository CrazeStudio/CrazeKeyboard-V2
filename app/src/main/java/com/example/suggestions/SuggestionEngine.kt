package com.example.suggestions

import android.util.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

import android.content.Context

enum class WordCategory { MOST_USED, COMMONLY_USED, MODERATELY_USED }
data class DictWord(val word: String, val freq: Int, val category: WordCategory)

class SuggestionEngine(
    context: Context,
    private val personalDictionaryDao: PersonalDictionaryDao
) {
    private val appContext = context.applicationContext
    private val engineJob = SupervisorJob()
    private val engineScope = CoroutineScope(engineJob + Dispatchers.IO)
    private val inMemoryPersonalWords = ConcurrentHashMap<String, Int>()
    private val suggestionCache = LruCache<String, List<String>>(256)
    private val autoCorrectCache = LruCache<String, String>(256)

    @Volatile
    private var dictionary: List<DictWord> = emptyList()
    @Volatile
    private var topWordsForFuzzy: List<String> = emptyList()
    @Volatile
    private var dictionaryLoaded = false
    @Volatile
    private var commonWordsSet: Set<String> = emptySet()
    
    private var loadingJob: Job? = null

    fun ensureLoaded() {
        if (dictionaryLoaded || loadingJob?.isActive == true) return
        
        loadingJob = engineScope.launch {
            try {
                // Pre-load personal words
                try {
                    val words = personalDictionaryDao.getTopWords(1000)
                    words.forEach { inMemoryPersonalWords[it.word.lowercase()] = it.frequency }
                } catch (_: Exception) {}

                val tempTopWordsForFuzzy = ArrayList<String>(10500)
                val tempCommonWordsSet = HashSet<String>(135000)
                val wordsAlpha = ArrayList<DictWord>(105000)
                
                appContext.assets.open("dictionary.txt").bufferedReader().useLines { lines ->
                    var rank = 0
                    lines.forEach { line ->
                        val commaIndex = line.indexOf(',')
                        if (commaIndex != -1) {
                            val word = line.substring(0, commaIndex)
                            val freq = line.substring(commaIndex + 1).toIntOrNull() ?: 0
                            rank++
                            val category = when {
                                rank <= 10000 -> WordCategory.MOST_USED
                                rank <= 50000 -> WordCategory.COMMONLY_USED
                                else -> WordCategory.MODERATELY_USED
                            }
                            wordsAlpha.add(DictWord(word, freq, category))
                            if (rank <= 10000) tempTopWordsForFuzzy.add(word)
                            tempCommonWordsSet.add(word)
                        }
                    }
                }
                wordsAlpha.sortBy { it.word }
                
                dictionary = wordsAlpha
                topWordsForFuzzy = tempTopWordsForFuzzy
                commonWordsSet = tempCommonWordsSet
                dictionaryLoaded = true
            } catch(e: Exception) {
                e.printStackTrace()
            }
        }
    }

    init {
        // We no longer load everything in init to save memory and CPU at startup
    }

    // Physical QWERTY adjacency matrix (Key -> Adjacent Neighbor Keys)
    private val qwertyNeighbors = mapOf(
        'q' to listOf('w', 'a', 's', '1', '2'),
        'w' to listOf('q', 'e', 's', 'a', 'd', '2', '3'),
        'e' to listOf('w', 'r', 'd', 's', 'f', '3', '4'),
        'r' to listOf('e', 't', 'f', 'd', 'g', '4', '5'),
        't' to listOf('r', 'y', 'g', 'f', 'h', '5', '6'),
        'y' to listOf('t', 'u', 'h', 'g', 'j', '6', '7'),
        'u' to listOf('y', 'i', 'j', 'h', 'k', '7', '8'),
        'i' to listOf('u', 'o', 'k', 'j', 'l', '8', '9'),
        'o' to listOf('i', 'p', 'l', 'k', '9', '0'),
        'p' to listOf('o', 'l', '0'),
        'a' to listOf('q', 'w', 's', 'z'),
        's' to listOf('w', 'e', 'a', 'd', 'z', 'x'),
        'd' to listOf('e', 'r', 's', 'f', 'x', 'c'),
        'f' to listOf('r', 't', 'd', 'g', 'c', 'v'),
        'g' to listOf('t', 'y', 'f', 'h', 'v', 'b'),
        'h' to listOf('y', 'u', 'g', 'j', 'b', 'n'),
        'j' to listOf('u', 'i', 'h', 'k', 'n', 'm'),
        'k' to listOf('i', 'o', 'j', 'l', 'm'),
        'l' to listOf('o', 'p', 'k'),
        'z' to listOf('a', 's', 'x'),
        'x' to listOf('z', 's', 'd', 'c'),
        'c' to listOf('x', 'd', 'f', 'v'),
        'v' to listOf('c', 'f', 'g', 'b'),
        'b' to listOf('v', 'g', 'h', 'n'),
        'n' to listOf('b', 'h', 'j', 'm'),
        'm' to listOf('n', 'j', 'k')
    )

    // Next-word bigram/phrase predictor (FUTO style N-Gram engine)
    private val nextWordPredictions = mapOf(
        "how" to listOf("are", "is", "was", "about", "can"),
        "how are" to listOf("you", "things", "we", "they"),
        "thank" to listOf("you", "so", "very", "god"),
        "thank you" to listOf("so", "very", "much", "for", "again"),
        "thanks" to listOf("for", "a", "again", "bro", "mate"),
        "thanks for" to listOf("the", "your", "sharing", "helping", "all"),
        "i" to listOf("am", "will", "have", "don't", "can", "think", "want", "need", "know", "was"),
        "i am" to listOf("going", "here", "ready", "happy", "so", "sorry", "fine", "trying"),
        "i have" to listOf("been", "a", "to", "no", "already", "seen", "done"),
        "i will" to listOf("be", "do", "call", "let", "see", "come", "send"),
        "i don't" to listOf("know", "think", "have", "want", "like", "see"),
        "let" to listOf("me", "us", "it", "them"),
        "let me" to listOf("know", "see", "check", "get", "try", "look"),
        "let us" to listOf("know", "go", "see", "start"),
        "good" to listOf("morning", "night", "luck", "day", "job", "afternoon", "evening", "idea"),
        "have" to listOf("a", "been", "to", "you", "good", "fun"),
        "have a" to listOf("great", "good", "nice", "wonderful", "safe", "blessed"),
        "see" to listOf("you", "if", "what", "how", "them"),
        "see you" to listOf("soon", "tomorrow", "later", "there", "again"),
        "what" to listOf("is", "are", "do", "time", "about", "happened"),
        "what is" to listOf("the", "your", "that", "this", "going"),
        "where" to listOf("are", "is", "were", "did", "can"),
        "when" to listOf("are", "is", "will", "did", "can", "you"),
        "why" to listOf("is", "are", "did", "not", "would"),
        "please" to listOf("let", "find", "send", "check", "help", "note", "call"),
        "please let" to listOf("me", "us"),
        "looking" to listOf("forward", "at", "for", "good"),
        "looking forward" to listOf("to", "for"),
        "best" to listOf("regards", "wishes", "way", "friend", "time"),
        "nice" to listOf("to", "meeting", "day", "one", "work"),
        "nice to" to listOf("meet", "see", "hear"),
        "take" to listOf("care", "time", "your", "a", "it"),
        "take care" to listOf("of", "and", "bro"),
        "can" to listOf("you", "we", "i", "be", "do", "get"),
        "can you" to listOf("please", "send", "check", "call", "help", "see"),
        "do" to listOf("you", "not", "it", "we"),
        "do you" to listOf("have", "want", "know", "think", "need", "like"),
        "are" to listOf("you", "they", "we", "there"),
        "are you" to listOf("sure", "ready", "there", "coming", "okay", "free"),
        "we" to listOf("are", "will", "have", "can", "need", "should"),
        "they" to listOf("are", "were", "will", "have", "said"),
        "you" to listOf("are", "can", "have", "will", "should", "know", "want"),
        "you are" to listOf("the", "welcome", "right", "great", "awesome", "so")
    )

    // Extensive High-Frequency English Vocabulary (Ordered by unigram rank)

    // Comprehensive fast lookup Sets for O(1) membership checks
    private val technicalTermsSet: Set<String> = setOf(
        "info", "wifi", "bluetooth", "gps", "nfc", "usb", "sdk", "apk", "app", "apps",
        "api", "apis", "url", "urls", "uri", "http", "https", "ftp", "ssh", "ssl", "tls",
        "html", "css", "js", "ts", "json", "xml", "yaml", "sql", "db", "dao", "dto",
        "cpu", "gpu", "ram", "rom", "ssd", "hdd", "os", "ios", "mac", "pc", "linux",
        "git", "github", "gitlab", "repo", "commit", "push", "pull", "merge", "branch",
        "cli", "gui", "ui", "ux", "id", "ids", "ip", "dns", "vpn", "lan", "wan",
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "zip", "rar", "7z", "tar", "gz",
        "png", "jpg", "jpeg", "gif", "webp", "svg", "mp3", "mp4", "wav", "mkv", "avi",
        "dev", "devs", "admin", "root", "sudo", "null", "bool", "int", "str", "float",
        "bug", "bugs", "fix", "fixes", "log", "logs", "msg", "msgs", "btn", "nav", "tab", "tabs",
        "ai", "ml", "llm", "gpt", "gemini", "claude", "bot", "bots", "prompt", "token",
        "ok", "okay", "bro", "dude", "mate", "lol", "lmao", "rofl", "omg", "wtf", "idk", "tbh", "imo",
        "fyi", "asap", "brb", "btw", "gg", "gl", "hf", "afk", "np", "yw", "thx", "pls", "plz",
        "hi", "hey", "hello", "yo", "sup", "bye", "cya", "gn", "gm", "ty", "tnx", "k", "kk"
    )

    // Comprehensive common typo & contraction corrections map
    private val commonTypos = mapOf(
        "teh" to "the",
        "adn" to "and",
        "taht" to "that",
        "waht" to "what",
        "becuase" to "because",
        "becasue" to "because",
        "recieved" to "received",
        "recieve" to "receive",
        "seperate" to "separate",
        "definately" to "definitely",
        "definitly" to "definitely",
        "dont" to "don't",
        "cant" to "can't",
        "wont" to "won't",
        "didnt" to "didn't",
        "isnt" to "isn't",
        "arent" to "aren't",
        "wasnt" to "wasn't",
        "couldnt" to "couldn't",
        "wouldnt" to "wouldn't",
        "shouldnt" to "shouldn't",
        "youre" to "you're",
        "theyre" to "they're",
        "weve" to "we've",
        "ive" to "I've",
        "im" to "I'm",
        "ill" to "I'll",
        "id" to "I'd",
        "hes" to "he's",
        "shes" to "she's",
        "thsi" to "this",
        "helo" to "hello",
        "helllo" to "hello",
        "woudl" to "would",
        "coudl" to "could",
        "shoudl" to "should",
        "thansk" to "thanks",
        "thnaks" to "thanks",
        "peopel" to "people",
        "yuor" to "your",
        "fro" to "for",
        "nad" to "and",
        "knwo" to "know",
        "alot" to "a lot",
        "goverment" to "government",
        "ocured" to "occurred",
        "untill" to "until",
        "beleive" to "believe",
        "tommorow" to "tomorrow",
        "tomorow" to "tomorrow",
        "frist" to "first",
        "latter" to "later",
        "lagy" to "laggy",
        "thier" to "their",
        "thibk" to "think",
        "thik" to "think",
        "godo" to "good",
        "gret" to "great",
        "awsome" to "awesome",
        "pls" to "please",
        "plz" to "please",
        "thx" to "thanks",
        "ty" to "thank you",
        "np" to "no problem",
        "yw" to "you're welcome",
        "omw" to "on my way",
        "brb" to "be right back",
        "idk" to "I don't know",
        "tbh" to "to be honest",
        "imo" to "in my opinion",
        "fyi" to "for your information",
        "asap" to "ASAP"
    )

    fun findAutoCorrection(word: String, level: String = "balanced"): String? {
        if (level.equals("off", ignoreCase = true)) return null
        ensureLoaded()
        val lower = word.trim().lowercase()
        if (lower.length < 2) return null

        // Check fast auto-correct cache
        autoCorrectCache.get(lower)?.let { return if (it.isEmpty()) null else it }

        // 1. Direct typo dictionary (O(1) instant)
        commonTypos[lower]?.let {
            autoCorrectCache.put(lower, it)
            return it
        }

        // If the word is already a valid known word or in personal dictionary, NEVER auto-correct!
        if (isKnownWord(lower)) {
            autoCorrectCache.put(lower, "")
            return null
        }

        // For short words (2-3 chars), do NOT perform fuzzy matching to prevent corrupting valid slang/short words/acronyms
        if (lower.length <= 3 || level.equals("mild", ignoreCase = true)) {
            autoCorrectCache.put(lower, "")
            return null
        }

        // 2. Spatial QWERTY Levenshtein distance check for words >= 4 chars
        var bestCandidate: String? = null
        var minScore = Double.MAX_VALUE

        val maxAllowedScore = if (level.equals("aggressive", ignoreCase = true)) 1.05 else 0.80
        val searchLimit = kotlin.math.min(topWordsForFuzzy.size, if (level.equals("aggressive", ignoreCase = true)) 2000 else 1200)

        // Preallocate buffer rows once outside the loop
        val maxLen = 32
        val prevPrevRow = DoubleArray(maxLen)
        val prevRow = DoubleArray(maxLen)
        val currRow = DoubleArray(maxLen)

        val firstChar = lower.first()
        var evaluatedCount = 0

        for (idx in 0 until searchLimit) {
            val candidate = topWordsForFuzzy[idx]
            if (candidate.isEmpty() || kotlin.math.abs(candidate.length - lower.length) > 1 || candidate.length >= maxLen) continue
            // First letter match constraint (prevents swapping starting letters)
            val cFirst = candidate.first()
            if (cFirst != firstChar && !isAdjacent(cFirst, firstChar)) continue

            evaluatedCount++
            val score = calculateSpatialDistance(lower, candidate, prevPrevRow, prevRow, currRow)
            if (score < minScore && score <= maxAllowedScore) {
                minScore = score
                bestCandidate = candidate
                // If we found a very high confidence fat-finger match (e.g. single adjacent key swap), early return!
                if (score <= 0.45) {
                    break
                }
            }
            if (evaluatedCount >= 150) break
        }

        autoCorrectCache.put(lower, bestCandidate ?: "")
        return bestCandidate
    }

    private fun isKnownWord(word: String): Boolean {
        return commonWordsSet.contains(word) || inMemoryPersonalWords.containsKey(word) || technicalTermsSet.contains(word)
    }

    private fun calculateSpatialDistance(
        s1: String,
        s2: String,
        rPrevPrev: DoubleArray,
        rPrev: DoubleArray,
        rCurr: DoubleArray
    ): Double {
        val len1 = s1.length
        val len2 = s2.length
        if (len1 == 0) return len2.toDouble()
        if (len2 == 0) return len1.toDouble()

        var prevPrev = rPrevPrev
        var prev = rPrev
        var curr = rCurr

        for (j in 0..len2) {
            prev[j] = j.toDouble()
        }

        for (i in 1..len1) {
            curr[0] = i.toDouble()
            val c1 = s1[i - 1]
            for (j in 1..len2) {
                val c2 = s2[j - 1]
                val cost = if (c1 == c2) {
                    0.0
                } else if (isAdjacent(c1, c2)) {
                    0.45 // Low penalty for fat-finger adjacent keys!
                } else {
                    1.0
                }

                var minCost = min(
                    prev[j] + 1.0,      // deletion
                    min(
                        curr[j - 1] + 1.0,  // insertion
                        prev[j - 1] + cost // substitution
                    )
                )

                // Transposition check (e.g. teh -> the)
                if (i > 1 && j > 1 && s1[i - 1] == s2[j - 2] && s1[i - 2] == s2[j - 1]) {
                    minCost = min(minCost, prevPrev[j - 2] + 0.6)
                }

                curr[j] = minCost
            }

            // Rotate rows
            val temp = prevPrev
            prevPrev = prev
            prev = curr
            curr = temp
        }

        return prev[len2]
    }

    private fun isAdjacent(c1: Char, c2: Char): Boolean {
        return qwertyNeighbors[c1]?.contains(c2) == true
    }

    fun getSuggestionsSync(currentPrefix: String, previousText: String = ""): List<String> {
        val trimmed = currentPrefix.trim().lowercase()

        // If prefix is empty, return empty list to avoid showing suggestions when no word is written
        if (trimmed.isEmpty()) {
            return emptyList()
        }
        
        ensureLoaded()

        val cacheKey = "$trimmed|$previousText"
        suggestionCache.get(cacheKey)?.let { return it }

        val result = mutableListOf<String>()

        // 1. Direct typo correction O(1)
        val typoCorrection = commonTypos[trimmed]
        if (typoCorrection != null) {
            result.add(typoCorrection)
        }

        // 2. Personal dictionary high-priority matches
        if (inMemoryPersonalWords.isNotEmpty()) {
            for ((w, _) in inMemoryPersonalWords) {
                if (w.startsWith(trimmed) && !result.contains(w)) {
                    result.add(w)
                    if (result.size >= 3) break
                }
            }
        }

        // 3. O(log N) Dictionary prefix scan
        if (dictionaryLoaded && result.size < 3) {
            var low = 0
            var high = dictionary.size - 1
            var firstMatch = -1
            
            while (low <= high) {
                val mid = (low + high) / 2
                val midWord = dictionary[mid].word
                if (midWord.startsWith(trimmed)) {
                    firstMatch = mid
                    high = mid - 1 // keep searching left
                } else if (midWord < trimmed) {
                    low = mid + 1
                } else {
                    high = mid - 1
                }
            }
            
            if (firstMatch != -1) {
                // Find top candidates by frequency without allocating large lists or running Timsort
                var top1: DictWord? = null
                var top2: DictWord? = null
                var top3: DictWord? = null
                var scanned = 0

                for (i in firstMatch until dictionary.size) {
                    val candidate = dictionary[i]
                    if (candidate.word.startsWith(trimmed)) {
                        scanned++
                        if (scanned > 60) break 
                        if (!result.contains(candidate.word)) {
                            if (top1 == null || candidate.freq > top1.freq) {
                                top3 = top2
                                top2 = top1
                                top1 = candidate
                            } else if (top2 == null || candidate.freq > top2.freq) {
                                top3 = top2
                                top2 = candidate
                            } else if (top3 == null || candidate.freq > top3.freq) {
                                top3 = candidate
                            }
                        }
                    } else {
                        break
                    }
                }
                
                top1?.let { if (result.size < 3 && !result.contains(it.word)) result.add(it.word) }
                top2?.let { if (result.size < 3 && !result.contains(it.word)) result.add(it.word) }
                top3?.let { if (result.size < 3 && !result.contains(it.word)) result.add(it.word) }
            }
        }

        // 4. Fuzzy spatial match fallback if very few matches
        // Only do fuzzy matching for prefixes of length 3-8 to prevent extreme lag
        if (result.size < 3 && trimmed.length in 3..8) {
            val firstChar = trimmed[0]
            var bestFuzzy: String? = null
            var bestScore = Double.MAX_VALUE
            // Reduce search limit for fuzzy to 1000 for better performance
            val searchLimit = kotlin.math.min(topWordsForFuzzy.size, 1000)
            
            // Preallocate buffer rows once outside the loop
            val maxLen = 32
            val prevPrevRow = DoubleArray(maxLen)
            val prevRow = DoubleArray(maxLen)
            val currRow = DoubleArray(maxLen)

            for (idx in 0 until searchLimit) {
                val w = topWordsForFuzzy[idx]
                if (w.isNotEmpty()) {
                    val wFirst = w[0]
                    // Strict first char match for fuzzy to prune search space by 90%+
                    if (wFirst == firstChar) {
                        if (kotlin.math.abs(w.length - trimmed.length) <= 1 && w.length < maxLen) {
                            val score = calculateSpatialDistance(trimmed, w, prevPrevRow, prevRow, currRow)
                            if (score < bestScore && score <= 0.85 && !result.contains(w)) {
                                    bestScore = score
                                    bestFuzzy = w
                            }
                        }
                    }
                }
            }
            if (bestFuzzy != null) {
                result.add(bestFuzzy)
            }
        }

        val finalResult = if (result.isEmpty()) {
            listOf(currentPrefix)
        } else if (currentPrefix.firstOrNull()?.isUpperCase() == true) {
            result.take(3).map { it.replaceFirstChar { c -> c.uppercase() } }
        } else {
            result.take(3)
        }

        suggestionCache.put(cacheKey, finalResult)
        return finalResult
    }

    suspend fun getSuggestions(currentPrefix: String, previousText: String = ""): List<String> {
        return getSuggestionsSync(currentPrefix, previousText)
    }

    fun getGlideSuggestions(sampledChars: List<Char>): List<String> {
        if (sampledChars.size < 2) return emptyList()
        
        ensureLoaded()

        // Clean consecutive duplicates in sampled chars
        val cleanPath = mutableListOf<Char>()
        sampledChars.forEach { c ->
            val lower = c.lowercaseChar()
            if (cleanPath.isEmpty() || cleanPath.last() != lower) {
                cleanPath.add(lower)
            }
        }
        if (cleanPath.size < 2) return emptyList()

        val startChar = cleanPath.first()
        val secondChar = if (cleanPath.size > 1) cleanPath[1] else null
        val endChar = cleanPath.last()

        val scoredResults = mutableListOf<Pair<String, Double>>()

        val processWord: (String) -> Unit = { word ->
            if (word.length >= 2) {
                val wStart = word.first()
                if (wStart == startChar || wStart == secondChar) {
                    val wEnd = word.last()
                    if (wEnd == endChar || isAdjacent(wEnd, endChar)) {
                        var pathIdx = 0
                        var matchedChars = 0
                        for (i in 0 until word.length) {
                            val charToFind = word[i]
                            if (i > 0 && word[i] == word[i - 1]) continue
                            
                            while (pathIdx < cleanPath.size) {
                                val pChar = cleanPath[pathIdx]
                                pathIdx++
                                if (pChar == charToFind) {
                                    matchedChars++
                                    break
                                }
                            }
                        }

                        val uniqueCharsInWord = word.toSet().size
                        val matchRatio = matchedChars.toDouble() / uniqueCharsInWord.toDouble()
                        
                        if (matchRatio >= 0.70) {
                            var score = matchRatio * 100.0
                            if (wStart == startChar) score += 50.0
                            if (wEnd == endChar) score += 30.0
                            
                            val freq = inMemoryPersonalWords[word] ?: 0
                            score += (freq * 5).coerceAtMost(50)

                            val lenDiff = kotlin.math.abs(word.length - cleanPath.size)
                            score -= (lenDiff * 2.0)

                            scoredResults.add(word to score)
                        }
                    }
                }
            }
        }

        for (word in inMemoryPersonalWords.keys) {
            processWord(word)
        }
        if (dictionaryLoaded) {
            val limit = kotlin.math.min(topWordsForFuzzy.size, 6000)
            for (i in 0 until limit) {
                processWord(topWordsForFuzzy[i])
            }
        }

        return scoredResults.sortedByDescending { it.second }
            .take(3)
            .map { it.first }
    }

    fun findGlideWord(sampledChars: List<Char>): String? {
        return getGlideSuggestions(sampledChars).firstOrNull()
    }

    suspend fun learnWord(word: String) = withContext(Dispatchers.IO) {
        val clean = word.trim().lowercase()
        if (clean.length < 2 || clean.any { !it.isLetter() }) return@withContext
        val currentFreq = inMemoryPersonalWords[clean] ?: 0
        inMemoryPersonalWords[clean] = currentFreq + 1
        suggestionCache.evictAll()
        try {
            personalDictionaryDao.insertOrUpdate(
                PersonalDictionaryEntity(
                    word = clean,
                    frequency = currentFreq + 1,
                    lastUsed = System.currentTimeMillis()
                )
            )
        } catch (_: Exception) {
            // Safe fallback
        }
    }

    fun close() {
        engineJob.cancel()
    }
}
