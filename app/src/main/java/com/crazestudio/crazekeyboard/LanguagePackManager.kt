package com.crazestudio.crazekeyboard

import android.content.Context
import android.util.JsonReader
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.TreeSet
import java.util.zip.GZIPInputStream

/** Downloads verified .cpack vocabulary archives and creates compact local prefix indexes. */
internal object LanguagePackManager {
    const val API_BASE_URL = "https://crazepredictionapi.onrender.com"
    private const val CONNECT_TIMEOUT = 15_000
    private const val READ_TIMEOUT = 120_000

    fun isInstalled(context: Context, language: String): Boolean =
        wordsFile(context, language).let { it.isFile && it.length() > 0L }

    fun installedSize(context: Context, language: String): Long =
        wordsFile(context, language).takeIf { it.isFile }?.length() ?: 0L

    private fun directory(context: Context) =
        File(context.filesDir, "language_packs").apply { if (!exists()) mkdirs() }

    private fun wordsFile(context: Context, language: String) =
        File(directory(context), "$language.words")

    fun download(context: Context, language: String, callback: (Boolean, String) -> Unit) {
        require(language in setOf("en", "bn", "hi"))
        val app = context.applicationContext
        Thread({
            var connection: HttpURLConnection? = null
            val dir = directory(app)
            val archive = File(dir, "$language.cpack")
            val archiveTemp = File(dir, "$language.cpack.tmp")
            val words = wordsFile(app, language)
            val wordsTemp = File(dir, "$language.words.tmp")
            try {
                val manifestConnection = (URL("$API_BASE_URL/v1/packs/manifest").openConnection() as HttpURLConnection).apply {
                    connectTimeout = CONNECT_TIMEOUT
                    readTimeout = READ_TIMEOUT
                    requestMethod = "GET"
                }
                val manifestText = manifestConnection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                manifestConnection.disconnect()
                val manifest = org.json.JSONObject(manifestText)
                val packs = manifest.getJSONArray("packs")
                var expectedHash = ""
                var expectedCount = 0
                for (i in 0 until packs.length()) {
                    val item = packs.getJSONObject(i)
                    if (item.optString("language") == language) {
                        expectedHash = item.optString("sha256")
                        expectedCount = item.optInt("word_count")
                        break
                    }
                }
                if (expectedHash.length != 64) throw IllegalStateException("No published $language pack found in manifest")

                connection = (URL("$API_BASE_URL/v1/packs/$language").openConnection() as HttpURLConnection).apply {
                    connectTimeout = CONNECT_TIMEOUT
                    readTimeout = READ_TIMEOUT
                    requestMethod = "GET"
                }
                if (connection.responseCode !in 200..299) {
                    throw IllegalStateException("Pack download failed: HTTP ${connection.responseCode}")
                }
                BufferedInputStream(connection.inputStream).use { input ->
                    BufferedOutputStream(archiveTemp.outputStream()).use { output -> input.copyTo(output, 64 * 1024) }
                }
                val digest = MessageDigest.getInstance("SHA-256")
                archiveTemp.inputStream().buffered().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        digest.update(buffer, 0, count)
                    }
                }
                val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
                if (!actualHash.equals(expectedHash, ignoreCase = true)) {
                    throw IllegalStateException("Pack checksum verification failed")
                }

                val uniqueWords = TreeSet<String>()
                GZIPInputStream(BufferedInputStream(archiveTemp.inputStream())).use { gzip ->
                    JsonReader(InputStreamReader(gzip, Charsets.UTF_8)).use { reader ->
                        reader.beginObject()
                        while (reader.hasNext()) {
                            when (reader.nextName()) {
                                "entries" -> {
                                    reader.beginArray()
                                    while (reader.hasNext()) {
                                        var word = ""
                                        reader.beginObject()
                                        while (reader.hasNext()) {
                                            when (reader.nextName()) {
                                                "w" -> word = reader.nextString()
                                                else -> reader.skipValue()
                                            }
                                        }
                                        reader.endObject()
                                        if (word.isNotBlank() && word.length <= 40 && word.any { it.isLetter() }) {
                                            uniqueWords.add(word.lowercase())
                                        }
                                    }
                                    reader.endArray()
                                }
                                else -> reader.skipValue()
                            }
                        }
                        reader.endObject()
                    }
                }
                if (uniqueWords.isEmpty()) throw IllegalStateException("Downloaded pack contains no valid words")
                wordsTemp.bufferedWriter(Charsets.UTF_8).use { writer ->
                    uniqueWords.forEach { writer.append(it).append('\n') }
                }
                if (words.exists() && !words.delete()) throw IllegalStateException("Could not replace old language index")
                if (!wordsTemp.renameTo(words)) {
                    wordsTemp.copyTo(words, overwrite = true)
                    wordsTemp.delete()
                }
                if (archive.exists()) archive.delete()
                if (!archiveTemp.renameTo(archive)) archiveTemp.copyTo(archive, overwrite = true)
                DownloadedVocabularyIndex.reload(app, language)
                callback(true, "$language pack ready · ${uniqueWords.size} words")
            } catch (error: Exception) {
                archiveTemp.delete()
                wordsTemp.delete()
                callback(false, error.message ?: "Pack download failed")
            } finally {
                connection?.disconnect()
            }
        }, "craze-pack-$language").apply { isDaemon = true; start() }
    }
}
