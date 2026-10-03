package com.example.keyboard

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class GifItem(val id: String, val url: String, val previewUrl: String, val label: String)

object GifRepository {

    val curatedTrending = listOf(
        GifItem("g1", "https://media.giphy.com/media/3o7TKSjRrfIPjeiVyM/giphy.gif", "https://media.giphy.com/media/3o7TKSjRrfIPjeiVyM/200w.gif", "Hello & Wave"),
        GifItem("g2", "https://media.giphy.com/media/l41YkxvU8c7J7Bba0/giphy.gif", "https://media.giphy.com/media/l41YkxvU8c7J7Bba0/200w.gif", "Dance Party"),
        GifItem("g3", "https://media.giphy.com/media/26AHONQ79FdWZhAI0/giphy.gif", "https://media.giphy.com/media/26AHONQ79FdWZhAI0/200w.gif", "Thumbs Up"),
        GifItem("g4", "https://media.giphy.com/media/xT0xezQGU5xCDJuCPe/giphy.gif", "https://media.giphy.com/media/xT0xezQGU5xCDJuCPe/200w.gif", "Mind Blown"),
        GifItem("g5", "https://media.giphy.com/media/3o7abKhOpu0NwenH3O/giphy.gif", "https://media.giphy.com/media/3o7abKhOpu0NwenH3O/200w.gif", "Sad Tears"),
        GifItem("g6", "https://media.giphy.com/media/l0MYt5jPR6QX5pnqM/giphy.gif", "https://media.giphy.com/media/l0MYt5jPR6QX5pnqM/200w.gif", "Fire Flame"),
        GifItem("g7", "https://media.giphy.com/media/jpbnoe3UIa8TU8LM13/giphy.gif", "https://media.giphy.com/media/jpbnoe3UIa8TU8LM13/200w.gif", "Pop Cat"),
        GifItem("g8", "https://media.giphy.com/media/3oEjI6SIIHBdRxXI40/giphy.gif", "https://media.giphy.com/media/3oEjI6SIIHBdRxXI40/200w.gif", "Waiting"),
        GifItem("g9", "https://media.giphy.com/media/l4FGpP4lxGGgK5CBW/giphy.gif", "https://media.giphy.com/media/l4FGpP4lxGGgK5CBW/200w.gif", "Party Confetti"),
        GifItem("g10", "https://media.giphy.com/media/26FLdm964upIslUZ2/giphy.gif", "https://media.giphy.com/media/26FLdm964upIslUZ2/200w.gif", "Heart Love"),
        GifItem("g11", "https://media.giphy.com/media/kyLYXonQYYfwYDIeZl/giphy.gif", "https://media.giphy.com/media/kyLYXonQYYfwYDIeZl/200w.gif", "Cool Doge"),
        GifItem("g12", "https://media.giphy.com/media/nbvFVPiEiJH6TCGAXf/giphy.gif", "https://media.giphy.com/media/nbvFVPiEiJH6TCGAXf/200w.gif", "Bravo Clapping")
    )

    val curatedCategories = listOf("Trending", "Reactions", "Memes", "Anime", "Gaming", "Dance", "Sad", "Love", "Funny")

    suspend fun searchGifs(query: String): List<GifItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        
        if (com.example.core.NetworkCheck.enforceOfflineMode(com.example.CrazeApp.getAppContext())) {
             return@withContext if (trimmed.isEmpty()) curatedTrending else curatedTrending.filter { it.label.lowercase().contains(trimmed.lowercase()) }
        }

        if (trimmed.isEmpty()) {
            return@withContext curatedTrending
        }

        try {
            // Tenor / Giphy Open API endpoint
            val encodedQuery = URLEncoder.encode(trimmed, "UTF-8")
            val endpoint = "https://api.giphy.com/v1/gifs/search?api_key=dc6zaTOxFJmzC&q=$encodedQuery&limit=24&rating=g"
            val url = URL(endpoint)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4000
                readTimeout = 4000
                setRequestProperty("User-Agent", "CrazeKeyboard/1.0")
            }

            if (connection.responseCode == 200) {
                val jsonString = connection.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(jsonString)
                val dataArray = root.optJSONArray("data")
                if (dataArray != null && dataArray.length() > 0) {
                    val result = mutableListOf<GifItem>()
                    for (i in 0 until dataArray.length()) {
                        val item = dataArray.getJSONObject(i)
                        val id = item.optString("id", "g_$i")
                        val title = item.optString("title", trimmed).ifBlank { trimmed }
                        val images = item.optJSONObject("images")
                        val original = images?.optJSONObject("fixed_height") ?: images?.optJSONObject("original")
                        val preview = images?.optJSONObject("fixed_height_small") ?: original
                        val gifUrl = original?.optString("url") ?: ""
                        val previewUrl = preview?.optString("url") ?: gifUrl

                        if (gifUrl.isNotBlank()) {
                            result.add(GifItem(id = id, url = gifUrl, previewUrl = previewUrl, label = title))
                        }
                    }
                    if (result.isNotEmpty()) {
                        return@withContext result
                    }
                }
            }
        } catch (_: Exception) {
            // Network timeout or blocked, fallback gracefully to filtered curated items
        }

        // Filter curated items as fallback
        val filtered = curatedTrending.filter { it.label.lowercase().contains(trimmed.lowercase()) }
        if (filtered.isNotEmpty()) filtered else curatedTrending
    }
}
