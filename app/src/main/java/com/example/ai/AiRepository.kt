package com.example.ai

import com.example.BuildConfig
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST

@JsonClass(generateAdapter = true)
data class OpenRouterRequest(
    val model: String,
    val messages: List<Message>
)

@JsonClass(generateAdapter = true)
data class Message(
    val role: String,
    val content: String
)

@JsonClass(generateAdapter = true)
data class OpenRouterResponse(
    val choices: List<Choice>
)

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val isUser: Boolean,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val searchResults: List<SearchResultItem> = emptyList(),
    val searchSource: SearchSource = SearchSource.NONE
)

@JsonClass(generateAdapter = true)
data class Choice(
    val message: Message
)

interface OpenRouterApi {
    @Headers("Content-Type: application/json")
    @POST("chat/completions")
    suspend fun getCompletion(
        @Header("Authorization") auth: String,
        @Body request: OpenRouterRequest
    ): OpenRouterResponse
}

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    val contents: List<GeminiContent>,
    val systemInstruction: GeminiContent? = null
)

@JsonClass(generateAdapter = true)
data class GeminiContent(
    val parts: List<GeminiPart>
)

@JsonClass(generateAdapter = true)
data class GeminiPart(
    val text: String
)

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    val candidates: List<GeminiCandidate>
)

@JsonClass(generateAdapter = true)
data class GeminiCandidate(
    val content: GeminiContent
)

interface GeminiApi {
    @POST("v1beta/models/gemini-1.5-flash-latest:generateContent")
    suspend fun generateContent(
        @retrofit2.http.Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

object AiRepository {
    // OpenRouter Free Models
    private const val MODEL_GEMINI_OR = "google/gemini-2.0-flash-exp:free"
    private const val MODEL_OPEN_SOURCE = "meta-llama/llama-3.3-70b-instruct:free"
    private const val MODEL_NEX = "nex-agi/nex-n2.5-mini:free"

    private val moshi = Moshi.Builder()
        .build()

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.NONE })
        .build()

    private val openRouterApi = Retrofit.Builder()
        .baseUrl("https://openrouter.ai/api/v1/")
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .client(client)
        .build()
        .create(OpenRouterApi::class.java)

    private val geminiApi = Retrofit.Builder()
        .baseUrl("https://generativelanguage.googleapis.com/")
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .client(client)
        .build()
        .create(GeminiApi::class.java)

    fun resolveApiKey(userKey: String? = null): String {
        if (!userKey.isNullOrBlank()) return userKey.trim()
        return ""
    }

    fun resolveGeminiKey(userKey: String? = null): String {
        if (!userKey.isNullOrBlank()) return userKey.trim()
        return ""
    }

    private fun resolveModel(groundingActive: Boolean = false): String {
        if (groundingActive) return MODEL_OPEN_SOURCE
        val prefs = com.example.settings.KeyboardPreferences.getInstance(com.example.CrazeApp.getAppContext())
        return when (prefs.preferredAiModel) {
            "gemini" -> MODEL_GEMINI_OR
            "llama" -> MODEL_OPEN_SOURCE
            else -> MODEL_NEX
        }
    }

    suspend fun polishText(text: String, customApiKey: String? = null): String {
        return rewriteTone(text, "polish", customApiKey)
    }

    suspend fun rewriteTone(text: String, tone: String, customApiKey: String? = null): String {
        if (com.example.core.NetworkCheck.enforceOfflineMode(com.example.CrazeApp.getAppContext())) {
            return "Offline Mode enabled. Network features blocked."
        }

        val prefs = com.example.settings.KeyboardPreferences.getInstance(com.example.CrazeApp.getAppContext())
        val isGeminiPreferred = prefs.preferredAiModel == "gemini"
        val geminiKey = resolveGeminiKey()
        
        if (isGeminiPreferred && geminiKey.isNotBlank()) {
            return rewriteToneGemini(text, tone, geminiKey)
        }

        val apiKey = resolveApiKey(customApiKey)
        if (apiKey.isBlank()) {
            return "API Key not configured. Please add an API Key in settings."
        }

        val promptInstruction = when (tone.lowercase()) {
            "professional" -> "Rewrite this in a clear, professional, and well-structured manner suitable for work or business. Return ONLY the rewritten text without commentary."
            "casual" -> "Rewrite this in a casual, friendly, warm, and natural conversational tone. Return ONLY the rewritten text without commentary."
            "polite" -> "Rewrite this courteously, politely, and respectfully. Return ONLY the rewritten text without commentary."
            "concise" -> "Rewrite this to be as concise, punchy, and direct as possible, removing any fluff. Return ONLY the rewritten text without commentary."
            "social" -> "Rewrite this for a social media post, engaging, modern, and lively. Return ONLY the rewritten text without commentary."
            "creative" -> "Rewrite this creatively and engagingly. Return ONLY the rewritten text without commentary."
            else -> "Polish and improve the user's text for grammar, clarity, and flow while keeping the meaning intact. Return ONLY the polished text without quotes or commentary."
        }

        val request = OpenRouterRequest(
            model = resolveModel(),
            messages = listOf(
                Message(role = "system", content = promptInstruction),
                Message(role = "user", content = text)
            )
        )

        return try {
            val response = openRouterApi.getCompletion("Bearer $apiKey", request)
            response.choices.firstOrNull()?.message?.content?.trim() ?: "No response from AI."
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    private suspend fun rewriteToneGemini(text: String, tone: String, apiKey: String): String {
        val promptInstruction = when (tone.lowercase()) {
            "professional" -> "Rewrite this in a clear, professional, and well-structured manner suitable for work or business. Return ONLY the rewritten text without commentary."
            "casual" -> "Rewrite this in a casual, friendly, warm, and natural conversational tone. Return ONLY the rewritten text without commentary."
            "polite" -> "Rewrite this courteously, politely, and respectfully. Return ONLY the rewritten text without commentary."
            "concise" -> "Rewrite this to be as concise, punchy, and direct as possible, removing any fluff. Return ONLY the rewritten text without commentary."
            "social" -> "Rewrite this for a social media post, engaging, modern, and lively. Return ONLY the rewritten text without commentary."
            "creative" -> "Rewrite this creatively and engagingly. Return ONLY the rewritten text without commentary."
            else -> "Polish and improve the user's text for grammar, clarity, and flow while keeping the meaning intact. Return ONLY the polished text without quotes or commentary."
        }

        val request = GeminiRequest(
            contents = listOf(GeminiContent(listOf(GeminiPart(text)))),
            systemInstruction = GeminiContent(listOf(GeminiPart(promptInstruction)))
        )

        return try {
            val response = geminiApi.generateContent(apiKey, request)
            response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim() ?: "No response from Gemini."
        } catch (e: Exception) {
            "Error (Gemini): ${e.message}"
        }
    }

    suspend fun ask(query: String, contextText: String? = null, customApiKey: String? = null): String {
        if (com.example.core.NetworkCheck.enforceOfflineMode(com.example.CrazeApp.getAppContext())) {
            return "Offline Mode enabled. Network features blocked."
        }

        val prefs = com.example.settings.KeyboardPreferences.getInstance(com.example.CrazeApp.getAppContext())
        val isGeminiPreferred = prefs.preferredAiModel == "gemini"
        val geminiKey = resolveGeminiKey()
        
        if (isGeminiPreferred && geminiKey.isNotBlank()) {
            val systemPrompt = if (!contextText.isNullOrBlank()) {
                "You are an intelligent assistant. The user is typing: \"$contextText\". Answer concisely."
            } else {
                "You are an intelligent assistant. Answer concisely."
            }
            val request = GeminiRequest(
                contents = listOf(GeminiContent(listOf(GeminiPart(query)))),
                systemInstruction = GeminiContent(listOf(GeminiPart(systemPrompt)))
            )
            return try {
                val response = geminiApi.generateContent(geminiKey, request)
                response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim() ?: "No response from Gemini."
            } catch (e: Exception) {
                "Error (Gemini): ${e.message}"
            }
        }

        val apiKey = resolveApiKey(customApiKey)
        if (apiKey.isBlank()) {
            return "OpenRouter API Key not configured."
        }

        val systemPrompt = if (!contextText.isNullOrBlank()) {
            "You are an intelligent SwiftKey-style keyboard assistant. The user is currently typing the following text in their app:\n\"\"\"\n$contextText\n\"\"\"\nAnswer the user's request helpfully, concisely, and directly so it is ready to be used or inserted. Avoid overly long disclaimers."
        } else {
            "You are an intelligent SwiftKey-style keyboard assistant. Answer the user's request helpfully, concisely, and directly so it is ready to be inserted. Keep responses concise and focused."
        }

        val request = OpenRouterRequest(
            model = resolveModel(),
            messages = listOf(
                Message(role = "system", content = systemPrompt),
                Message(role = "user", content = query)
            )
        )

        return try {
            val response = openRouterApi.getCompletion("Bearer $apiKey", request)
            response.choices.firstOrNull()?.message?.content?.trim() ?: "No response from AI."
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    suspend fun chat(messages: List<Message>, customApiKey: String? = null): String {
        if (com.example.core.NetworkCheck.enforceOfflineMode(com.example.CrazeApp.getAppContext())) {
            return "Offline Mode enabled. Network features blocked."
        }

        val prefs = com.example.settings.KeyboardPreferences.getInstance(com.example.CrazeApp.getAppContext())
        val isGeminiPreferred = prefs.preferredAiModel == "gemini"
        val geminiKey = resolveGeminiKey()
        
        if (isGeminiPreferred && geminiKey.isNotBlank()) {
            val geminiContents = messages.filter { it.role != "system" }.map {
                GeminiContent(listOf(GeminiPart(it.content)))
            }
            val systemMsg = messages.firstOrNull { it.role == "system" }?.content
            val request = GeminiRequest(
                contents = geminiContents,
                systemInstruction = systemMsg?.let { GeminiContent(listOf(GeminiPart(it))) }
            )
            return try {
                val response = geminiApi.generateContent(geminiKey, request)
                response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim() ?: "No response from Gemini."
            } catch (e: Exception) {
                "Error (Gemini): ${e.message}"
            }
        }

        val apiKey = resolveApiKey(customApiKey)
        if (apiKey.isBlank()) {
            return "OpenRouter API Key not configured."
        }

        val request = OpenRouterRequest(
            model = resolveModel(),
            messages = messages
        )

        return try {
            val response = openRouterApi.getCompletion("Bearer $apiKey", request)
            response.choices.firstOrNull()?.message?.content?.trim() ?: "No response from AI."
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    suspend fun sendChatWithSearch(
        conversation: List<ChatMessage>,
        searchSource: SearchSource = SearchSource.ALL,
        contextText: String? = null,
        customApiKey: String? = null
    ): Pair<String, List<SearchResultItem>> {
        if (com.example.core.NetworkCheck.enforceOfflineMode(com.example.CrazeApp.getAppContext())) {
            return Pair("Offline Mode enabled. Network features blocked.", emptyList())
        }
        val lastUserMessage = conversation.lastOrNull { it.isUser }?.text ?: ""
        var searchItems = emptyList<SearchResultItem>()
        var searchGrounding = ""

        if (searchSource != SearchSource.NONE && lastUserMessage.isNotBlank()) {
            val searchResult = AiSearchService.searchUnified(lastUserMessage, searchSource)
            searchItems = searchResult.items
            searchGrounding = searchResult.groundingContext
        }

        val prefs = com.example.settings.KeyboardPreferences.getInstance(com.example.CrazeApp.getAppContext())
        val isGeminiPreferred = prefs.preferredAiModel == "gemini"
        val geminiKey = resolveGeminiKey()
        if (isGeminiPreferred && geminiKey.isNotBlank()) {
             val systemPrompt = buildString {
                append("You are an intelligent, helpful AI Chatbot integrated directly into the user's Android keyboard. ")
                append("Format your responses clearly using Markdown. ")
                if (!contextText.isNullOrBlank()) {
                    append("\n\nContext:\n\"\"\"\n$contextText\n\"\"\"")
                }
                if (searchGrounding.isNotBlank()) {
                    append(searchGrounding)
                }
            }
            val geminiContents = conversation.map {
                GeminiContent(listOf(GeminiPart(it.text)))
            }
            val request = GeminiRequest(
                contents = geminiContents,
                systemInstruction = GeminiContent(listOf(GeminiPart(systemPrompt)))
            )
            return try {
                val response = geminiApi.generateContent(geminiKey, request)
                val answer = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim() ?: "No response from Gemini."
                Pair(answer, searchItems)
            } catch (e: Exception) {
                Pair("Error (Gemini): ${e.message}", searchItems)
            }
        }

        val apiKey = resolveApiKey(customApiKey)
        if (apiKey.isBlank()) {
            if (searchItems.isNotEmpty()) {
                val fallbackSummary = buildString {
                    append("### Real-time Search Results\n\n")
                    searchItems.forEach { item ->
                        append("- **${item.title}** (${item.source.displayName})\n  ${item.snippet}\n")
                    }
                }
                return Pair(fallbackSummary, searchItems)
            }
            return Pair("API Key not configured.", emptyList())
        }

        val systemPrompt = buildString {
            append("You are an intelligent, helpful AI Chatbot integrated directly into the user's Android keyboard. ")
            append("Format your responses clearly using Markdown. ")
            if (!contextText.isNullOrBlank()) {
                append("\n\nThe user is currently writing in an app with the following text in their field:\n\"\"\"\n$contextText\n\"\"\"")
            }
            if (searchGrounding.isNotBlank()) {
                append(searchGrounding)
            }
        }

        val apiMessages = mutableListOf<Message>()
        apiMessages.add(Message(role = "system", content = systemPrompt))
        val recentConversation = if (conversation.size > 10) conversation.takeLast(10) else conversation
        for (msg in recentConversation) {
            apiMessages.add(
                Message(
                    role = if (msg.isUser) "user" else "assistant",
                    content = msg.text
                )
            )
        }

        val request = OpenRouterRequest(
            model = resolveModel(searchGrounding.isNotBlank()),
            messages = apiMessages
        )

        return try {
            val response = openRouterApi.getCompletion("Bearer $apiKey", request)
            val answer = response.choices.firstOrNull()?.message?.content?.trim() ?: "No response from AI."
            Pair(answer, searchItems)
        } catch (e: Exception) {
            if (searchItems.isNotEmpty()) {
                val fallback = buildString {
                    append("### Real-time Search Results (${searchSource.displayName})\n\n")
                    searchItems.forEach { item ->
                        append("- **${item.title}**\n  ${item.snippet}\n")
                        if (!item.url.isNullOrBlank()) append("  [Link](${item.url})\n")
                    }
                }
                Pair(fallback, searchItems)
            } else {
                Pair("Error: ${e.message}", emptyList())
            }
        }
    }

    suspend fun askWithSearch(
        query: String,
        searchSource: SearchSource = SearchSource.ALL,
        contextText: String? = null,
        customApiKey: String? = null
    ): Pair<String, List<SearchResultItem>> {
        if (com.example.core.NetworkCheck.enforceOfflineMode(com.example.CrazeApp.getAppContext())) {
            return Pair("Offline Mode enabled. Network features blocked.", emptyList())
        }
        var searchItems = emptyList<SearchResultItem>()
        var searchGrounding = ""

        if (searchSource != SearchSource.NONE && query.isNotBlank()) {
            val searchResult = AiSearchService.searchUnified(query, searchSource)
            searchItems = searchResult.items
            searchGrounding = searchResult.groundingContext
        }

        val prefs = com.example.settings.KeyboardPreferences.getInstance(com.example.CrazeApp.getAppContext())
        val isGeminiPreferred = prefs.preferredAiModel == "gemini"
        val geminiKey = resolveGeminiKey()
        if (isGeminiPreferred && geminiKey.isNotBlank()) {
            val systemPrompt = buildString {
                append("You are an intelligent assistant. ")
                if (!contextText.isNullOrBlank()) {
                    append("Context: \"$contextText\". ")
                }
                append("Answer concisely.")
                if (searchGrounding.isNotBlank()) {
                    append(searchGrounding)
                }
            }
            val request = GeminiRequest(
                contents = listOf(GeminiContent(listOf(GeminiPart(query)))),
                systemInstruction = GeminiContent(listOf(GeminiPart(systemPrompt)))
            )
            return try {
                val response = geminiApi.generateContent(geminiKey, request)
                val answer = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim() ?: "No response from Gemini."
                Pair(answer, searchItems)
            } catch (e: Exception) {
                Pair("Error (Gemini): ${e.message}", searchItems)
            }
        }

        val apiKey = resolveApiKey(customApiKey)
        if (apiKey.isBlank()) {
            if (searchItems.isNotEmpty()) {
                val fallbackSummary = buildString {
                    append("### Search Results\n\n")
                    searchItems.forEach { item ->
                        append("- **${item.title}** (${item.source.displayName})\n  ${item.snippet}\n")
                    }
                }
                return Pair(fallbackSummary, searchItems)
            }
            return Pair("API Key not configured.", emptyList())
        }

        val systemPrompt = buildString {
            append("You are an intelligent SwiftKey-style keyboard assistant. ")
            if (!contextText.isNullOrBlank()) {
                append("The user is typing:\n\"\"\"\n$contextText\n\"\"\"\n")
            }
            append("Answer the user's request helpfully, concisely, and directly so it is ready to be inserted. Use clean Markdown formatting.")
            if (searchGrounding.isNotBlank()) {
                append(searchGrounding)
            }
        }

        val request = OpenRouterRequest(
            model = resolveModel(searchGrounding.isNotBlank()),
            messages = listOf(
                Message(role = "system", content = systemPrompt),
                Message(role = "user", content = query)
            )
        )

        return try {
            val response = openRouterApi.getCompletion("Bearer $apiKey", request)
            val answer = response.choices.firstOrNull()?.message?.content?.trim() ?: "No response from AI."
            Pair(answer, searchItems)
        } catch (e: Exception) {
            if (searchItems.isNotEmpty()) {
                val fallback = buildString {
                    append("### Real-time Search Results\n\n")
                    searchItems.forEach { item ->
                        append("- **${item.title}** (${item.source.displayName})\n  ${item.snippet}\n")
                    }
                }
                Pair(fallback, searchItems)
            } else {
                Pair("Error: ${e.message}", emptyList())
            }
        }
    }
}
