package com.example.ai

import android.os.Build
import android.text.Html
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

enum class SearchSource(val displayName: String, val iconRes: Int) {
    ALL("All Web", com.example.R.drawable.ic_globe),
    DUCKDUCKGO("DuckDuckGo", com.example.R.drawable.ic_duck),
    WIKIPEDIA("Wikipedia", com.example.R.drawable.ic_wiki),
    REDDIT("Reddit", com.example.R.drawable.ic_reddit),
    NONE("Direct AI", com.example.R.drawable.ic_sparkle)
}

data class SearchResultItem(
    val source: SearchSource,
    val title: String,
    val snippet: String,
    val url: String? = null,
    val meta: String? = null
)

data class UnifiedSearchResult(
    val query: String,
    val source: SearchSource,
    val items: List<SearchResultItem>,
    val groundingContext: String
)

object AiSearchService {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private const val USER_AGENT = "CrazeKeyboard/1.0 (Android; Intelligent Assistant)"

    private fun cleanHtml(html: String): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString().trim()
            } else {
                @Suppress("DEPRECATION")
                Html.fromHtml(html).toString().trim()
            }
        } catch (_: Exception) {
            html.replace(Regex("<[^>]*>"), "").trim()
        }
    }

    /**
     * Search Wikipedia using official Wikipedia Search API
     */
    suspend fun searchWikipedia(query: String, limit: Int = 3): List<SearchResultItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<SearchResultItem>()
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch=$encodedQuery&utf8=&format=json&srlimit=$limit"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val bodyString = response.body?.string() ?: return@withContext emptyList()
                val json = JSONObject(bodyString)
                val queryObj = json.optJSONObject("query") ?: return@withContext emptyList()
                val searchArray = queryObj.optJSONArray("search") ?: return@withContext emptyList()

                for (i in 0 until searchArray.length()) {
                    val item = searchArray.getJSONObject(i)
                    val rawTitle = item.optString("title", "")
                    val rawSnippet = item.optString("snippet", "")
                    if (rawTitle.isNotBlank()) {
                        val cleanedSnippet = cleanHtml(rawSnippet)
                        val pageUrl = "https://en.wikipedia.org/wiki/" + URLEncoder.encode(rawTitle.replace(' ', '_'), "UTF-8")
                        results.add(
                            SearchResultItem(
                                source = SearchSource.WIKIPEDIA,
                                title = rawTitle,
                                snippet = cleanedSnippet,
                                url = pageUrl,
                                meta = "Wikipedia • Encyclopedia"
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        results
    }

    /**
     * Search Reddit discussions, opinions, and recommendations
     */
    suspend fun searchReddit(query: String, limit: Int = 4): List<SearchResultItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<SearchResultItem>()
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "https://www.reddit.com/search.json?q=$encodedQuery&limit=$limit&sort=relevance"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "android:com.example.keyboard:v1.0 (by /u/craze_ai_assistant)")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val bodyString = response.body?.string() ?: return@withContext emptyList()
                val json = JSONObject(bodyString)
                val dataObj = json.optJSONObject("data") ?: return@withContext emptyList()
                val children = dataObj.optJSONArray("children") ?: return@withContext emptyList()

                for (i in 0 until children.length()) {
                    val child = children.getJSONObject(i)
                    val postData = child.optJSONObject("data") ?: continue
                    val title = postData.optString("title", "")
                    val sub = postData.optString("subreddit_name_prefixed", "r/all")
                    val score = postData.optInt("score", 0)
                    val comments = postData.optInt("num_comments", 0)
                    val selftext = postData.optString("selftext", "")
                    val permalink = postData.optString("permalink", "")
                    val postUrl = if (permalink.isNotBlank()) "https://reddit.com$permalink" else null

                    val snippet = if (selftext.isNotBlank()) {
                        selftext.take(220).replace("\n", " ").trim()
                    } else {
                        "Reddit discussion with $comments comments and $score upvotes."
                    }

                    if (title.isNotBlank()) {
                        results.add(
                            SearchResultItem(
                                source = SearchSource.REDDIT,
                                title = title,
                                snippet = snippet,
                                url = postUrl,
                                meta = "$sub • $score upvotes"
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        results
    }

    /**
     * Search DuckDuckGo Instant Answers & Lite Web
     */
    suspend fun searchDuckDuckGo(query: String): List<SearchResultItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<SearchResultItem>()
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val apiUrl = "https://api.duckduckgo.com/?q=$encodedQuery&format=json&no_html=1&skip_disambig=1"

            val request = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", USER_AGENT)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string() ?: ""
                    if (bodyString.isNotBlank()) {
                        val json = JSONObject(bodyString)
                        val abstractText = json.optString("AbstractText", "")
                        val heading = json.optString("Heading", query)
                        val abstractUrl = json.optString("AbstractURL", "")
                        val abstractSource = json.optString("AbstractSource", "DuckDuckGo")

                        if (abstractText.isNotBlank()) {
                            results.add(
                                SearchResultItem(
                                    source = SearchSource.DUCKDUCKGO,
                                    title = if (heading.isNotBlank()) heading else query,
                                    snippet = abstractText,
                                    url = if (abstractUrl.isNotBlank()) abstractUrl else "https://duckduckgo.com/?q=$encodedQuery",
                                    meta = "DuckDuckGo Instant Answer ($abstractSource)"
                                )
                            )
                        }

                        // Related Topics
                        val relatedTopics = json.optJSONArray("RelatedTopics")
                        if (relatedTopics != null) {
                            var count = 0
                            for (i in 0 until relatedTopics.length()) {
                                if (count >= 3) break
                                val topicObj = relatedTopics.optJSONObject(i) ?: continue
                                val topicText = topicObj.optString("Text", "")
                                val topicUrl = topicObj.optString("FirstURL", "")
                                if (topicText.isNotBlank()) {
                                    results.add(
                                        SearchResultItem(
                                            source = SearchSource.DUCKDUCKGO,
                                            title = topicText.take(60),
                                            snippet = topicText,
                                            url = if (topicUrl.isNotBlank()) topicUrl else null,
                                            meta = "DuckDuckGo Topic"
                                        )
                                    )
                                    count++
                                }
                            }
                        }
                    }
                }
            }

            // Fallback to DuckDuckGo HTML / Lite search if instant answer was empty
            if (results.isEmpty()) {
                val liteUrl = "https://html.duckduckgo.com/html/?q=$encodedQuery"
                val liteRequest = Request.Builder()
                    .url(liteUrl)
                    .header("User-Agent", USER_AGENT)
                    .build()

                httpClient.newCall(liteRequest).execute().use { liteResp ->
                    if (liteResp.isSuccessful) {
                        val html = liteResp.body?.string() ?: ""
                        // Extract result snippets using regex
                        val snippetMatcher = Pattern.compile("<a class=\"result__snippet\"[^>]*>(.*?)</a>", Pattern.DOTALL).matcher(html)
                        val titleMatcher = Pattern.compile("<a class=\"result__url\"[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>", Pattern.DOTALL).matcher(html)

                        var found = 0
                        while (snippetMatcher.find() && found < 3) {
                            val snippetText = cleanHtml(snippetMatcher.group(1) ?: "")
                            var linkUrl = "https://duckduckgo.com/?q=$encodedQuery"
                            var linkTitle = query
                            if (titleMatcher.find()) {
                                linkUrl = titleMatcher.group(1) ?: linkUrl
                                linkTitle = cleanHtml(titleMatcher.group(2) ?: query)
                            }
                            if (snippetText.isNotBlank()) {
                                results.add(
                                    SearchResultItem(
                                        source = SearchSource.DUCKDUCKGO,
                                        title = if (linkTitle.isNotBlank()) linkTitle else "Web Result",
                                        snippet = snippetText,
                                        url = linkUrl,
                                        meta = "DuckDuckGo Web"
                                    )
                                )
                                found++
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        results
    }

    /**
     * Unified search executing the requested source(s) in parallel with a safe timeout
     */
    suspend fun searchUnified(query: String, source: SearchSource): UnifiedSearchResult = withContext(Dispatchers.IO) {
        if (source == SearchSource.NONE || query.isBlank()) {
            return@withContext UnifiedSearchResult(
                query = query,
                source = source,
                items = emptyList(),
                groundingContext = ""
            )
        }

        val collectedItems = mutableListOf<SearchResultItem>()

        withTimeoutOrNull(4500L) {
            when (source) {
                SearchSource.ALL -> {
                    val wikiDeferred = async { searchWikipedia(query, 2) }
                    val ddgDeferred = async { searchDuckDuckGo(query) }
                    val redditDeferred = async { searchReddit(query, 2) }

                    collectedItems.addAll(ddgDeferred.await())
                    collectedItems.addAll(wikiDeferred.await())
                    collectedItems.addAll(redditDeferred.await())
                }
                SearchSource.DUCKDUCKGO -> {
                    collectedItems.addAll(searchDuckDuckGo(query))
                }
                SearchSource.WIKIPEDIA -> {
                    collectedItems.addAll(searchWikipedia(query, 3))
                }
                SearchSource.REDDIT -> {
                    collectedItems.addAll(searchReddit(query, 3))
                }
                SearchSource.NONE -> {}
            }
        }

        val groundingContext = buildString {
            if (collectedItems.isNotEmpty()) {
                append("\n\n=== LIVE REAL-TIME WEB SEARCH RESULTS FOR: \"$query\" ===\n")
                collectedItems.forEachIndexed { idx, item ->
                    append("[Result #${idx + 1} from ${item.source.displayName}]\n")
                    append("Title: ${item.title}\n")
                    if (!item.meta.isNullOrBlank()) append("Context: ${item.meta}\n")
                    append("Details: ${item.snippet}\n")
                    if (!item.url.isNullOrBlank()) append("Link: ${item.url}\n")
                    append("\n")
                }
                append("=== END SEARCH RESULTS ===\n")
                append("Use the verified factual details and discussions above to accurately answer the user's request. ")
                append("Synthesize key insights into clear, well-formatted Markdown with bold headers/points and cite sources naturally (e.g. [Wikipedia], [Reddit r/...], or [DuckDuckGo]).")
            }
        }

        UnifiedSearchResult(
            query = query,
            source = source,
            items = collectedItems,
            groundingContext = groundingContext
        )
    }
}
