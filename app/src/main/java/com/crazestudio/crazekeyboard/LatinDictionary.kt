package com.crazestudio.crazekeyboard

import android.content.Context
import android.content.SharedPreferences

object LatinDictionary {
    private var prefs: SharedPreferences? = null
    private val learnedWords = mutableSetOf<String>()

    fun init(context: Context) {
        prefs = context.getSharedPreferences("craze_dictionary", Context.MODE_PRIVATE)
        prefs?.getStringSet("learned_words", emptySet())?.let {
            learnedWords.addAll(it)
        }
    }

    fun learnWord(word: String) {
        val clean = word.trim().trim(',', '.', '!', '?', ';', ':', '-', '"', '\'', '(', ')', '[', ']').lowercase()
        if (clean.length in 2..24 && clean.all { it.isLetter() }) {
            learnedWords.add(clean)
            prefs?.edit()?.putStringSet("learned_words", learnedWords)?.apply()
        }
    }

    private val commonWords = listOf(
        // High-frequency English words
        "the", "be", "to", "of", "and", "a", "in", "that", "have", "i",
        "it", "for", "not", "on", "with", "he", "as", "you", "do", "at",
        "this", "but", "his", "by", "from", "they", "we", "say", "her", "she",
        "or", "an", "will", "my", "one", "all", "would", "there", "their", "what",
        "so", "up", "out", "if", "about", "who", "get", "which", "go", "me",
        "when", "make", "can", "like", "time", "no", "just", "him", "know", "take",
        "people", "into", "year", "your", "good", "some", "could", "them", "see", "other",
        "than", "then", "now", "look", "only", "come", "its", "over", "think", "also",
        "back", "after", "use", "two", "how", "our", "work", "first", "well", "way",
        "even", "new", "want", "because", "any", "these", "give", "day", "most", "us",
        "great", "need", "feel", "high", "please", "thank", "thanks", "hello", "hi", "hey",
        "help", "today", "tomorrow", "tonight", "night", "morning", "friend", "love", "awesome", "cool",
        "nice", "fine", "okay", "sure", "done", "ready", "keyboard", "android", "craze", "studio",
        "mobile", "phone", "happy", "smile", "world", "music", "video", "photo", "message", "text",
        "chat", "email", "call", "meet", "place", "home", "life", "family", "school", "house",
        "game", "play", "run", "walk", "drive", "read", "write", "learn", "teach", "food",
        "water", "coffee", "tea", "money", "buy", "sell", "pay", "send", "receive", "open",
        "close", "start", "stop", "change", "try", "ask", "answer", "tell", "show", "hear",
        "listen", "talk", "speak", "wait", "stay", "leave", "keep", "let", "begin", "seem",
        "turn", "move", "live", "believe", "hold", "bring", "happen", "must", "provide", "sit",
        "stand", "lose", "include", "continue", "set", "lead", "understand", "watch", "follow", "create",
        "allow", "add", "spend", "grow", "win", "offer", "remember", "consider", "appear", "serve",
        "die", "expect", "build", "fall", "cut", "reach", "kill", "remain", "suggest", "raise",
        "pass", "require", "report", "decide", "pull", "always", "never", "sometimes", "often", "already",
        "almost", "together", "probably", "really", "actually", "perhaps", "maybe", "definitely", "completely", "especially",
        "certainly", "finally", "beautiful", "important", "different", "small", "large", "big", "little", "long",
        "short", "young", "old", "early", "late", "right", "wrong", "best", "better", "worst",
        "hard", "easy", "simple", "fast", "slow", "clean", "clear", "smart", "quick", "strong",
        "weak", "free", "true", "false", "real", "welcome", "congratulations", "cheers", "goodbye", "bye",
        "yes", "yeah", "yep", "nope", "please", "glad", "sorry", "excuse", "pardon", "welcome",
        // Extended common words
        "project", "application", "system", "feature", "interface", "design", "layout", "screen", "theme",
        "button", "color", "sound", "volume", "vibration", "language", "setting", "preference", "update",
        "version", "developer", "google", "cloud", "network", "speed", "fastest", "accuracy", "responsive",
        "connection", "document", "folder", "media", "camera", "gallery", "device", "battery", "display",
        // Multilingual common words (Spanish, French, German)
        "gracias", "hola", "por", "favor", "bueno", "amigo", "casa", "vida", "tiempo", "trabajo",
        "merci", "bonjour", "avec", "pour", "bien", "monde", "temps", "homme", "femme", "maison",
        "danke", "bitte", "hallo", "guten", "morgen", "tag", "nacht", "freund", "leben", "arbeit"
    )

    fun getSuggestions(prefix: String, limit: Int = 3, previousContext: String = ""): List<String> {
        val clean = prefix.trim().trim(',', '.', '!', '?', ';', ':', '-', '"', '\'', '(', ')', '[', ']').lowercase()
        
        // When prefix is empty, use smart context-aware suggestions
        if (clean.isBlank()) {
            val ctx = previousContext.trimEnd()
            return when {
                ctx.isEmpty() || ctx.endsWith(".") || ctx.endsWith("!") || ctx.endsWith("?") || ctx.endsWith("\n") -> {
                    listOf("I", "The", "Thanks")
                }
                ctx.endsWith("thank", ignoreCase = true) || ctx.endsWith("thanks", ignoreCase = true) -> {
                    listOf("you", "so", "much")
                }
                ctx.endsWith("how", ignoreCase = true) -> {
                    listOf("are", "is", "about")
                }
                ctx.endsWith("where", ignoreCase = true) -> {
                    listOf("are", "is", "do")
                }
                ctx.endsWith("what", ignoreCase = true) -> {
                    listOf("is", "time", "do")
                }
                ctx.endsWith("good", ignoreCase = true) -> {
                    listOf("morning", "day", "night")
                }
                else -> {
                    listOf("the", "to", "and")
                }
            }
        }

        val allCandidates = linkedSetOf<String>()
        // 1. User learned words come first
        learnedWords.filter { it.startsWith(clean) }.forEach { allCandidates.add(it) }
        // 2. High-frequency common words
        commonWords.filter { it.startsWith(clean) }.forEach { allCandidates.add(it) }

        val result = mutableListOf<String>()
        val isAllUpper = prefix.length > 1 && prefix.all { it.isUpperCase() }
        val isCapital = prefix.isNotEmpty() && prefix.first().isUpperCase()

        fun format(w: String): String = when {
            isAllUpper -> w.uppercase()
            isCapital -> w.replaceFirstChar { it.uppercase() }
            else -> w
        }

        for (w in allCandidates) {
            val formatted = format(w)
            if (!result.contains(formatted)) {
                result.add(formatted)
            }
            if (result.size >= limit) break
        }

        if (result.isEmpty()) {
            result.add(format(clean))
        }

        val fallbackDefaults = listOf("the", "and", "you", "to", "is", "for", "with", "that", "can", "will")
        for (f in fallbackDefaults) {
            if (result.size >= limit) break
            val formatted = format(f)
            if (!result.contains(formatted)) {
                result.add(formatted)
            }
        }

        return result.take(limit)
    }
}
