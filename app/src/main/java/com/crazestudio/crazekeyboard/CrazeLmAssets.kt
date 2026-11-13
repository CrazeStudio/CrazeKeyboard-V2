package com.crazestudio.crazekeyboard

import android.content.Context
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

internal data class CrazeLmFiles(val model: File, val tokenizer: File)

/** Uses verified APK-bundled assets first; falls back to a verified release download. */
internal object CrazeLmAssets {
    private const val BASE =
        "https://github.com/CrazeStudio/Crazekeyboard-llm/releases/download/v1.0/"
    private const val MANIFEST_URL = BASE + "CrazeLM-30M-v1.0.tflite.json"
    private const val MODEL_URL = BASE + "CrazeLM-30M-v1.0.tflite"
    private const val TOKENIZER_URL = BASE + "tokenizer.model"
    private const val TOKENIZER_BYTES = 1_993_147L
    private const val TOKENIZER_SHA256 =
        "2f48ce89eee2cd8a49668d4baf08652eaf8206a07def1bee55451f80b1bd981e"

    fun ensureDownloaded(context: Context, progress: (String) -> Unit = {}): CrazeLmFiles {
        val directory = File(context.filesDir, "crazelm")
        if (!directory.exists() && !directory.mkdirs()) error("Cannot create CrazeLM private storage.")
        val manifestText = readBundledText(context, "crazelm/manifest.json") ?: readText(MANIFEST_URL)
        val manifest = JSONObject(manifestText)
        require(manifest.optString("format") == "LiteRT/TFLite") {
            "The published CrazeLM asset is not a TFLite model."
        }
        require(manifest.optInt("vocabulary_size") == 100_000 &&
            manifest.optInt("context_length") == 48) {
            "CrazeLM model has an unexpected vocabulary or context size."
        }
        val outputSpec = manifest.getJSONObject("output")
        val outputShape = outputSpec.getJSONArray("shape")
        require(outputSpec.optString("dtype") == "float32" &&
            outputShape.length() == 2 &&
            outputShape.optInt(0) == 1 &&
            outputShape.optInt(1) == 100_000) {
            "CrazeLM TFLite output specification is not supported by this app version."
        }
        val modelBytes = manifest.getLong("size_bytes")
        val modelSha = manifest.getString("sha256").lowercase()
        require(modelBytes > 0L && modelSha.matches(Regex("[0-9a-f]{64}"))) {
            "CrazeLM model manifest is invalid."
        }

        val model = File(directory, "CrazeLM-30M-v1.0.tflite")
        val modelMarker = File(directory, "model.verified")
        if (!copyBundledVerified(context, "crazelm/CrazeLM-30M-v1.0.tflite",
                model, modelBytes, modelSha, modelMarker, "AI model", progress)) {
            downloadVerified(MODEL_URL, model, modelBytes, modelSha,
                modelMarker, "AI model", progress)
        }

        val tokenizer = File(directory, "tokenizer.model")
        val tokenizerMarker = File(directory, "tokenizer.verified")
        if (!copyBundledVerified(context, "crazelm/tokenizer.model",
                tokenizer, TOKENIZER_BYTES, TOKENIZER_SHA256, tokenizerMarker, "Tokenizer", progress)) {
            downloadVerified(TOKENIZER_URL, tokenizer, TOKENIZER_BYTES, TOKENIZER_SHA256,
                tokenizerMarker, "Tokenizer", progress)
        }
        return CrazeLmFiles(model, tokenizer)
    }

    private fun copyBundledVerified(
        context: Context, assetPath: String, destination: File, expectedBytes: Long,
        expectedSha: String, marker: File, label: String, progress: (String) -> Unit
    ): Boolean {
        val asset = try {
            context.assets.open(assetPath)
        } catch (_: java.io.IOException) {
            return false
        }
        asset.use { input ->
            if (destination.isFile && destination.length() == expectedBytes &&
                marker.isFile && marker.readText().trim() == expectedSha) {
                progress("$label already verified")
                return true
            }
            progress("Installing bundled $label…")
            val temporary = File(destination.parentFile, destination.name + ".part")
            temporary.delete()
            val digest = MessageDigest.getInstance("SHA-256")
            var copied = 0L
            try {
                BufferedInputStream(input).use { source ->
                    FileOutputStream(temporary).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val count = source.read(buffer)
                            if (count < 0) break
                            copied += count
                            require(copied <= expectedBytes) { "$label asset exceeded expected size." }
                            digest.update(buffer, 0, count)
                            output.write(buffer, 0, count)
                        }
                        output.fd.sync()
                    }
                }
                val actualSha = digest.digest().joinToString("") { "%02x".format(it) }
                require(copied == expectedBytes && actualSha == expectedSha) {
                    "$label bundled asset failed SHA-256 verification."
                }
                if (destination.exists() && !destination.delete()) error("Cannot replace old $label file.")
                if (!temporary.renameTo(destination)) {
                    temporary.copyTo(destination, overwrite = true)
                    temporary.delete()
                }
                marker.writeText(expectedSha)
                progress("$label installed and verified")
                return true
            } catch (error: Throwable) {
                temporary.delete()
                throw error
            }
        }
    }

    private fun downloadVerified(
        url: String, destination: File, expectedBytes: Long, expectedSha: String,
        marker: File, label: String, progress: (String) -> Unit
    ) {
        if (destination.isFile && destination.length() == expectedBytes &&
            marker.isFile && marker.readText().trim() == expectedSha
        ) {
            progress("$label already verified")
            return
        }
        val temporary = File(destination.parentFile, destination.name + ".part")
        temporary.delete()
        progress("Downloading $label…")
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 45_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "CrazeKeyboard/1.6")
            setRequestProperty("Accept", "application/octet-stream")
        }
        try {
            connection.connect()
            val status = connection.responseCode
            require(status in 200..299) { "$label download failed: HTTP $status." }
            val contentLength = connection.contentLengthLong
            if (contentLength > 0L) require(contentLength == expectedBytes) {
                "$label size mismatch: server reports $contentLength bytes, expected $expectedBytes."
            }
            val digest = MessageDigest.getInstance("SHA-256")
            var copied = 0L
            var lastProgressPercent = -1
            BufferedInputStream(connection.inputStream).use { input ->
                FileOutputStream(temporary).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        copied += count
                        require(copied <= expectedBytes) { "$label download exceeded expected size." }
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                        val pct = ((copied * 100L) / expectedBytes).toInt().coerceIn(0, 100)
                        if (pct != lastProgressPercent) {
                            lastProgressPercent = pct
                            progress("Downloading $label… $pct%")
                        }
                    }
                    output.fd.sync()
                }
            }
            val actualSha = digest.digest().joinToString("") { "%02x".format(it) }
            require(copied == expectedBytes && actualSha == expectedSha) {
                "$label verification failed (size=$copied, SHA-256=$actualSha)."
            }
            if (destination.exists() && !destination.delete()) error("Cannot replace old $label file.")
            if (!temporary.renameTo(destination)) {
                temporary.copyTo(destination, overwrite = true)
                temporary.delete()
            }
            marker.writeText(expectedSha)
            progress("$label downloaded and verified")
        } catch (error: Throwable) {
            temporary.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }

    private fun readBundledText(context: Context, assetPath: String): String? =
        try {
            context.assets.open(assetPath).bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (_: java.io.IOException) {
            null
        }

    private fun readText(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "CrazeKeyboard/1.6")
        }
        return try {
            val status = connection.responseCode
            require(status in 200..299) {
                "Could not read the CrazeLM release manifest (HTTP " + status + ")."
            }
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
