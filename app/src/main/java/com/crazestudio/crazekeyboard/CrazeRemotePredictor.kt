package com.crazestudio.crazekeyboard

import android.os.Handler
import android.os.HandlerThread
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicLong

/** Debounced prefix-only online suggestions. Never sends the surrounding sentence. */
internal object CrazeRemotePredictor {
    private val thread = HandlerThread("craze-online-prediction").apply { start() }
    private val worker = Handler(thread.looper)
    private val main = Handler(android.os.Looper.getMainLooper())
    private val sequence = AtomicLong()
    @Volatile private var pending: Runnable? = null

    fun request(prefix: String, language: String, callback: (List<String>) -> Unit) {
        val clean = prefix.trim().take(40)
        val code = language.substringBefore('-').lowercase()
        if (clean.isEmpty() || code !in setOf("en", "bn", "hi")) return
        val requestId = sequence.incrementAndGet()
        pending?.let(worker::removeCallbacks)
        val task = Runnable {
            var connection: HttpURLConnection? = null
            val result = try {
                val encoded = URLEncoder.encode(clean, "UTF-8")
                val url = URL("${LanguagePackManager.API_BASE_URL}/v1/predict?prefix=$encoded&language=$code&limit=8")
                connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 2500
                    readTimeout = 2500
                    requestMethod = "GET"
                }
                if (connection.responseCode !in 200..299) emptyList()
                else {
                    val json = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    val values = JSONObject(json).optJSONArray("suggestions")
                    if (values == null) emptyList() else
                        (0 until values.length()).mapNotNull { values.optString(it).takeIf(String::isNotBlank) }
                }
            } catch (_: Exception) {
                emptyList()
            } finally {
                connection?.disconnect()
            }
            if (sequence.get() == requestId) main.post {
                if (sequence.get() == requestId) callback(result)
            }
        }
        pending = task
        worker.postDelayed(task, 220)
    }
}
