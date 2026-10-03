package com.example.emoji

data class StickerItem(
    val id: String,
    val name: String,
    val category: String,
    val url: String,
    val fallbackEmoji: String
)

object StickerData {
    val categories = listOf("Trending", "Memes & Moods", "Cute Cats", "Celebration", "Gaming")

    val stickers: List<StickerItem> = listOf(
        // Trending
        StickerItem("s_fire", "Fire Vibing", "Trending", "https://media.giphy.com/media/l0MYt5jPR6QX5pnqM/giphy.gif", "🔥"),
        StickerItem("s_popcat", "Pop Cat", "Trending", "https://media.giphy.com/media/jpbnoe3UIa8TU8LM13/giphy.gif", "🐱"),
        StickerItem("s_party", "Party Parrot", "Trending", "https://media.giphy.com/media/l4FGpP4lxGGgK5CBW/giphy.gif", "🎉"),
        StickerItem("s_heart_eyes", "Heart Explosion", "Trending", "https://media.giphy.com/media/26FLdm964upIslUZ2/giphy.gif", "💖"),
        StickerItem("s_cool_doge", "Cool Doge", "Trending", "https://media.giphy.com/media/kyLYXonQYYfwYDIeZl/giphy.gif", "🐶"),
        StickerItem("s_clapping", "Standing Ovation", "Trending", "https://media.giphy.com/media/nbvFVPiEiJH6TCGAXf/giphy.gif", "👏"),

        // Memes & Moods
        StickerItem("m_fine", "This Is Fine", "Memes & Moods", "https://media.giphy.com/media/9M5jK4GXmD5o1irGrF/giphy.gif", "☕"),
        StickerItem("m_brain", "Big Brain Mind Blown", "Memes & Moods", "https://media.giphy.com/media/26ufdipQqU2lhNA4g/giphy.gif", "🧠"),
        StickerItem("m_deal", "Deal With It", "Memes & Moods", "https://media.giphy.com/media/1jnyRP4DorCh2/giphy.gif", "😎"),
        StickerItem("m_facepalm", "Epic Facepalm", "Memes & Moods", "https://media.giphy.com/media/3og0INyCmHlNylks9O/giphy.gif", "🤦"),
        StickerItem("m_shrug", "Shrug", "Memes & Moods", "https://media.giphy.com/media/ZeB5RzwVuoxb2/giphy.gif", "🤷"),
        StickerItem("m_crying", "Waterfalls Tears", "Memes & Moods", "https://media.giphy.com/media/d2lcHJTG5Tscg/giphy.gif", "😭"),
        StickerItem("m_stonks", "Stonks Rising", "Memes & Moods", "https://media.giphy.com/media/YnkMcHgNIMW4Yfmjxr/giphy.gif", "📈"),

        // Cute Cats
        StickerItem("c_cat_jam", "Cat Jamming", "Cute Cats", "https://media.giphy.com/media/GeimqsH0TLDt4tScGw/giphy.gif", "🎵"),
        StickerItem("c_cat_hug", "Kitten Snuggle", "Cute Cats", "https://media.giphy.com/media/BzyTuYCmvSORqs1ABM/giphy.gif", "😻"),
        StickerItem("c_cat_wink", "Winking Kitty", "Cute Cats", "https://media.giphy.com/media/mlvseq9yGZhba/giphy.gif", "😸"),
        StickerItem("c_cat_sleep", "Sleepy Loaf", "Cute Cats", "https://media.giphy.com/media/MDJ9IbxxvDUQM/giphy.gif", "😴"),
        StickerItem("c_cat_surprised", "Surprised Meow", "Cute Cats", "https://media.giphy.com/media/ICOgUNjpvO0PC/giphy.gif", "🙀"),

        // Celebration
        StickerItem("ce_bday", "Birthday Confetti", "Celebration", "https://media.giphy.com/media/g5R9dok94mrIvplmZd/giphy.gif", "🎂"),
        StickerItem("ce_champagne", "Popping Bottles", "Celebration", "https://media.giphy.com/media/3o6fJ1BM7R2EBRDnxK/giphy.gif", "🍾"),
        StickerItem("ce_fireworks", "Night Fireworks", "Celebration", "https://media.giphy.com/media/26tOZ42Mg6pbTUPHW/giphy.gif", "🎆"),
        StickerItem("ce_disco", "Disco Dance", "Celebration", "https://media.giphy.com/media/l2JIdnF6aJnAqzDgY/giphy.gif", "🕺"),

        // Gaming
        StickerItem("g_gg", "GG Well Played", "Gaming", "https://media.giphy.com/media/11ISwbgCxEzMyY/giphy.gif", "🎮"),
        StickerItem("g_victory", "Victory Royale", "Gaming", "https://media.giphy.com/media/artj92V8o75VPL7AeQ/giphy.gif", "🏆"),
        StickerItem("g_rage", "Rage Quit", "Gaming", "https://media.giphy.com/media/l4FGonhn8Tx2BM25i/giphy.gif", "💢"),
        StickerItem("g_loading", "Buffering...", "Gaming", "https://media.giphy.com/media/3oEjI6SIIHBdRxXI40/giphy.gif", "⏳")
    )

    fun getByCategory(category: String): List<StickerItem> {
        return if (category == "Trending") stickers else stickers.filter { it.category == category }
    }

    fun searchStickers(query: String): List<StickerItem> {
        val clean = query.trim().lowercase()
        if (clean.isEmpty()) return stickers
        return stickers.filter { it.name.lowercase().contains(clean) || it.category.lowercase().contains(clean) }
    }
}
