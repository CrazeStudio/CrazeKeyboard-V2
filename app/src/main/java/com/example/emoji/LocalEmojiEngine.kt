package com.example.emoji

import android.util.LruCache

object LocalEmojiEngine {

    private val predictionCache = LruCache<String, List<String>>(64)

    private val intentMap: List<Pair<List<String>, List<String>>> = listOf(
        // Love / Affection / Dating
        listOf("love", "miss", "heart", "darling", "babe", "baby", "crush", "kiss", "kisses", "adore", "sweetheart", "handsome", "gorgeous", "romantic", "ily", "marry", "hug", "hugs", "cuddle") to
                listOf("❤️", "🥰", "😍", "💖", "😘", "💕", "🌹", "🫶", "💍", "🤗"),

        // Joy / Celebration / Birthday
        listOf("happy", "birthday", "bday", "party", "yay", "celebrate", "celebration", "cheers", "woohoo", "tada", "congrats", "congratulations", "graduated", "promotion", "festive", "dance", "dancing", "cheer") to
                listOf("🎂", "🎉", "🥳", "✨", "🥂", "🎈", "🎁", "👏", "🍾", "💃"),

        // Laugh / Humor / Memes
        listOf("lol", "lmao", "rofl", "haha", "hahaha", "funny", "joke", "hilarious", "humor", "dead", "dying", "kidding", "cracking", "lmfao") to
                listOf("😂", "🤣", "💀", "😆", "😹", "🤭", "😜"),

        // Gratitude / Respect / Agreement / Done
        listOf("thanks", "thank", "thankyou", "thx", "ty", "appreciate", "grateful", "blessed", "welcome", "please", "agree", "deal", "perfect", "awesome", "great", "done", "yes", "sure", "ok", "okay", "alright", "np") to
                listOf("🙏", "👍", "👏", "🙌", "✨", "💯", "🤝", "✅", "👌"),

        // Cool / Fire / Hyped / Slang
        listOf("fire", "lit", "cool", "epic", "insane", "goat", "legend", "boss", "hype", "wild", "dope", "sick", "clean", "slay", "rizz", "vibes", "vibe", "drippy", "fresh", "w", "win", "winning") to
                listOf("🔥", "😎", "🚀", "⚡", "🤯", "🤩", "👑", "🐐", "🏆", "🤙"),

        // Disbelief / Skull / RIP / Loss
        listOf("rip", "rip bozo", "l", "loss", "bruh", "cap", "nocap", "fr", "for real", "smh", "tbh", "ngl", "idk", "oof", "nah", "sus") to
                listOf("💀", "🗿", "🧢", "🤦", "🫣", "🤨", "🤷", "😶"),

        // Shocked / Surprise / OMG
        listOf("omg", "wow", "what", "shocked", "mindblown", "unbelievable", "crazy", "seriously", "no way", "gasp", "wtf", "whaaat", "woah") to
                listOf("🤯", "😱", "😳", "😮", "🫢", "👀", "🙀"),

        // Sad / Crying / Upset / Sorry
        listOf("sad", "cry", "crying", "tears", "unhappy", "depressed", "heartbroken", "hurt", "pain", "sorry", "miss", "lonely", "apologize", "grief", "sob") to
                listOf("😢", "😭", "🥺", "💔", "😞", "😿", "💔", "🥀"),

        // Angry / Frustrated / Rage
        listOf("angry", "mad", "furious", "hate", "rage", "pissed", "annoyed", "irritated", "stupid", "worst", "shut up", "damn", "fuming") to
                listOf("😡", "🤬", "😤", "💢", "🔥", "👿", "🖕"),

        // Tired / Sleep / Night
        listOf("tired", "sleep", "sleepy", "exhausted", "bed", "goodnight", "night", "gn", "yawn", "zzz", "nap", "resting", "passed out", "lazy") to
                listOf("😴", "🥱", "💤", "🛌", "🌙", "😪", "✨"),

        // Morning / Sunshine / Hello
        listOf("morning", "goodmorning", "gm", "sun", "sunrise", "wake", "awake", "sunshine", "hello", "hi", "hey", "sup", "yo", "greeting") to
                listOf("☀️", "🌅", "☕", "😊", "✨", "🌸", "👋", "🌞"),

        // Work / Tech / Code / Study
        listOf("code", "coding", "programmer", "developer", "computer", "laptop", "bug", "github", "software", "tech", "build", "terminal", "ai", "gemini", "robot", "bot") to
                listOf("💻", "👨‍💻", "🤖", "⌨️", "⚡", "💡", "🚀", "🖥️"),
        listOf("study", "exam", "school", "college", "test", "homework", "read", "book", "learn", "quiz", "university", "class") to
                listOf("📚", "📝", "✍️", "🤓", "🧠", "🎓", "📖"),
        listOf("work", "job", "office", "meeting", "busy", "client", "boss", "email", "task", "project", "presentation", "contract", "salary") to
                listOf("💼", "📊", "📋", "💻", "👔", "⏳", "📁"),

        // Money / Wealth / Success
        listOf("money", "cash", "dollar", "rich", "wealth", "paid", "crypto", "bitcoin", "profit", "win", "won", "jackpot", "expensive", "bag", "bucks", "bank") to
                listOf("💰", "💵", "🤑", "💳", "📈", "💎", "🪙", "🏦"),

        // Food & Dining
        listOf("food", "eat", "eating", "hungry", "lunch", "dinner", "breakfast", "meal", "tasty", "delicious", "yum", "yummy", "cook", "chef", "snack") to
                listOf("😋", "🍽️", "🤤", "🍔", "🍕", "🥗", "🥪", "🍳"),
        listOf("pizza", "slice") to listOf("🍕", "😋", "🧀", "🤤"),
        listOf("burger", "hamburger", "fries") to listOf("🍔", "🍟", "🥤", "😋"),
        listOf("coffee", "cappuccino", "latte", "espresso", "cafe", "starbucks") to listOf("☕", "🥐", "✨", "🧋", "🍩"),
        listOf("tea", "boba", "matcha") to listOf("🫖", "🍵", "🧋", "✨"),
        listOf("beer", "drinks", "bar", "wine", "alcohol", "cocktail", "vodka", "whiskey", "cheers", "pub") to listOf("🍻", "🍺", "🍷", "🥂", "🍹", "🍸"),
        listOf("cake", "icecream", "dessert", "sweet", "chocolate", "cookie", "donut", "candy") to listOf("🍰", "🍦", "🍫", "🍪", "🍩", "🧁", "🍬"),
        listOf("sushi", "ramen", "noodles", "rice", "asian", "taco", "mexican", "burrito") to listOf("🍣", "🍜", "🍱", "🌮", "🌯", "🥢"),

        // Sports / Fitness / Gym
        listOf("gym", "workout", "lift", "fitness", "muscle", "run", "running", "training", "exercise", "strong", "gains", "pushups", "cardio") to
                listOf("💪", "🏋️", "🏃", "🔥", "🥊", "🥇", "🚴"),
        listOf("soccer", "football", "goal", "fifa", "ronaldo", "messi") to listOf("⚽", "🥅", "🏆", "🔥"),
        listOf("basketball", "nba", "dunk", "hoop", "ball") to listOf("🏀", "🔥", "👟", "🏆"),
        listOf("game", "gaming", "playstation", "xbox", "gamer", "stream", "nintendo", "pc", "play") to listOf("🎮", "🕹️", "👾", "🏆", "🔥"),

        // Music / Art / Creative
        listOf("music", "song", "listen", "sing", "singing", "guitar", "piano", "beats", "melody", "track", "concert", "spotify") to
                listOf("🎵", "🎶", "🎧", "🎸", "🎹", "🎤", "✨"),
        listOf("art", "draw", "drawing", "paint", "painting", "artist", "design", "creative", "photo", "camera", "picture") to
                listOf("🎨", "🖌️", "📸", "🖼️", "✨", "✏️"),

        // Travel / Transportation / Places
        listOf("travel", "trip", "vacation", "flight", "plane", "airport", "holiday", "tour", "explore", "hotel", "beach", "island", "sea") to
                listOf("✈️", "🌴", "🏖️", "🧳", "🗺️", "🗽", "🏝️"),
        listOf("car", "drive", "driving", "traffic", "road", "speed", "ride", "uber", "taxi", "bus", "train") to
                listOf("🚗", "🏎️", "🚘", "🚦", "🛣️", "🚕", "🚆"),

        // Weather / Nature
        listOf("rain", "raining", "rainy", "storm", "umbrella", "thunder", "wet", "cloud", "clouds") to
                listOf("🌧️", "☔", "⛈️", "⚡", "💧", "☁️"),
        listOf("cold", "freeze", "freezing", "snow", "winter", "ice", "chilly") to
                listOf("🥶", "❄️", "☃️", "🧣", "🧤", "🧊"),
        listOf("hot", "heat", "summer", "sunny", "sweat", "beach", "warm") to
                listOf("🥵", "☀️", "🏖️", "🕶️", "🌊", "🌴"),
        listOf("flower", "flowers", "nature", "plant", "garden", "rose", "tree", "forest") to
                listOf("🌸", "🌹", "🌻", "🌿", "🌱", "🌺", "🌳"),

        // Health / Medical / Sick
        listOf("sick", "ill", "fever", "cough", "doctor", "hospital", "medicine", "pill", "headache", "virus", "flu") to
                listOf("🤒", "🤕", "🤧", "💊", "🏥", "🩺", "💉"),

        // Animals / Pets
        listOf("dog", "puppy", "doggo", "bark") to listOf("🐶", "🐕", "🐾", "🦴"),
        listOf("cat", "kitten", "kitty", "meow") to listOf("🐱", "🐈", "🐾", "😻"),
        listOf("pet", "animal", "bird", "lion", "bear", "panda", "monkey", "rabbit", "fish", "tiger") to
                listOf("🐾", "🦁", "🐼", "🐻", "🐦", "🐰", "🐠", "🐯", "🐵"),

        // Shopping / Gifts
        listOf("shop", "shopping", "buy", "bought", "order", "package", "gift", "present", "amazon", "sale", "discount", "clothes", "shoes") to
                listOf("🛍️", "🎁", "📦", "👗", "👟", "💳", "🛒", "🏷️")
    )

    // Pre-indexed single-word keyword map for O(1) instant token lookups
    private val singleWordIndex: Map<String, List<String>> by lazy {
        val map = mutableMapOf<String, MutableList<String>>()
        for ((keywords, emojis) in intentMap) {
            for (kw in keywords) {
                if (!kw.contains(" ")) {
                    val existing = map.getOrPut(kw) { mutableListOf() }
                    for (emoji in emojis) {
                        if (!existing.contains(emoji)) {
                            existing.add(emoji)
                        }
                    }
                }
            }
        }
        map
    }

    // Pre-filtered multi-word phrase list
    private val phraseIndex: List<Pair<String, List<String>>> by lazy {
        val list = mutableListOf<Pair<String, List<String>>>()
        for ((keywords, emojis) in intentMap) {
            for (kw in keywords) {
                if (kw.contains(" ")) {
                    list.add(kw to emojis)
                }
            }
        }
        list
    }

    /**
     * High-speed, sub-microsecond 0-latency Local AI Emoji Predictor:
     * Analyzes written text contextually in real time and predicts matching emojis.
     */
    fun predictEmojisForText(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        val clean = text.lowercase().trim()
        if (clean.isEmpty()) return emptyList()

        predictionCache.get(clean)?.let { return it }

        val candidateScores = mutableMapOf<String, Int>()

        // 1. Fast multi-word phrase matching (if text has spaces)
        if (clean.contains(" ")) {
            for ((phrase, emojis) in phraseIndex) {
                if (clean.contains(phrase)) {
                    emojis.forEachIndexed { idx, emoji ->
                        val score = (emojis.size - idx) * 5
                        candidateScores[emoji] = (candidateScores[emoji] ?: 0) + score
                    }
                }
            }
        }

        // 2. Tokenize with fast space split
        val tokens = clean.split(' ', '\n', '\t', ',', '.', '!', '?').filter { it.length >= 2 }
        if (tokens.isEmpty() && candidateScores.isEmpty()) return emptyList()

        for (token in tokens) {
            // Exact keyword match O(1)
            val directMatches = singleWordIndex[token]
            if (directMatches != null) {
                directMatches.forEachIndexed { idx, emoji ->
                    val score = (directMatches.size - idx) * 4
                    candidateScores[emoji] = (candidateScores[emoji] ?: 0) + score
                }
            } else {
                // Prefix match for tokens length >= 3
                if (token.length >= 3) {
                    for ((kw, emojis) in singleWordIndex) {
                        if (kw.startsWith(token) || token.startsWith(kw)) {
                            emojis.take(4).forEach { emoji ->
                                candidateScores[emoji] = (candidateScores[emoji] ?: 0) + 3
                            }
                            break
                        }
                    }
                }
            }
        }

        // 3. Fallback from EmojiData for the last token
        tokens.lastOrNull()?.let { lastToken ->
            val matches = EmojiData.searchEmojis(lastToken)
            matches.take(3).forEach { emoji ->
                candidateScores[emoji] = (candidateScores[emoji] ?: 0) + 2
            }
        }

        if (candidateScores.isEmpty()) {
            predictionCache.put(clean, emptyList())
            return emptyList()
        }

        val result = candidateScores.entries
            .sortedByDescending { it.value }
            .map { it.key }
            .take(8)

        predictionCache.put(clean, result)
        return result
    }
}
