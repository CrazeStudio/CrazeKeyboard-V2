package com.crazestudio.crazekeyboard

object LatinDictionary {
    private val commonWords = listOf(
        "the", "be", "to", "of", "and", "a", "in", "that", "have", "it",
        "for", "not", "on", "with", "he", "as", "you", "do", "at", "this",
        "but", "his", "by", "from", "they", "we", "say", "her", "she", "or",
        "an", "will", "my", "one", "all", "would", "there", "their", "what", "so",
        "up", "out", "if", "about", "who", "get", "which", "go", "me", "when",
        "make", "can", "like", "time", "no", "just", "him", "know", "take", "people",
        "into", "year", "your", "good", "some", "could", "them", "see", "other", "than",
        "then", "now", "look", "only", "come", "its", "over", "think", "also", "back",
        "after", "use", "two", "how", "our", "work", "first", "well", "way", "even",
        "new", "want", "because", "any", "these", "give", "day", "most", "us", "great",
        "need", "feel", "high", "please", "thank", "thanks", "hello", "hi", "hey",
        "help", "today", "tomorrow", "tonight", "night", "morning", "friend", "love", "awesome",
        "cool", "nice", "fine", "okay", "sure", "done", "ready", "keyboard", "android", "craze",
        "studio", "mobile", "phone", "happy", "smile", "world", "music", "video", "photo", "message"
    )

    fun getSuggestions(prefix: String, limit: Int = 3): List<String> {
        if (prefix.isBlank()) return listOf("I", "The", "Thanks")
        val clean = prefix.lowercase()
        val exactMatches = commonWords.filter { it.startsWith(clean) }
        val result = mutableListOf<String>()
        val isCapital = prefix.isNotEmpty() && prefix.first().isUpperCase()
        
        for (w in exactMatches) {
            val formatted = if (isCapital) w.replaceFirstChar { it.uppercase() } else w
            if (!result.contains(formatted)) {
                result.add(formatted)
            }
            if (result.size >= limit) break
        }
        
        if (result.isEmpty()) {
            val formatted = if (isCapital) prefix.replaceFirstChar { it.uppercase() } else prefix
            result.add(formatted)
        }
        
        val fillDefaults = listOf("the", "and", "you", "to", "is", "for", "with", "that")
        for (f in fillDefaults) {
            if (result.size >= limit) break
            val formatted = if (isCapital) f.replaceFirstChar { it.uppercase() } else f
            if (!result.contains(formatted)) {
                result.add(formatted)
            }
        }
        return result.take(limit)
    }
}
