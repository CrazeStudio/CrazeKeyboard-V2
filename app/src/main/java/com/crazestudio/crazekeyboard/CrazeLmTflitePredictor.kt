package com.crazestudio.crazekeyboard

import android.content.Context
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.File
import java.util.concurrent.Executors

/**
 * On-device next-word prediction with one long-lived TensorFlow Lite Interpreter.
 * Download and initialization are asynchronous; keyboard typing never waits for setup.
 */
internal object CrazeLmTflitePredictor {
    private const val SEQUENCE_LENGTH = 48
    private const val VOCAB_SIZE = 100_000

    @Volatile private var runtime: Runtime? = null
    @Volatile private var loading = false
    @Volatile var status: String = "CrazeLM is not downloaded"
        private set
    @Volatile private var observer: ((String) -> Unit)? = null
    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "craze-lm-runtime").apply { isDaemon = true }
    }

    fun observe(callback: ((String) -> Unit)?) {
        observer = callback
        callback?.invoke(status)
    }

    private fun publish(value: String) {
        status = value
        observer?.invoke(value)
    }

    fun isReady(): Boolean = runtime != null

    @Synchronized
    fun initializeAsync(context: Context) {
        if (runtime != null) {
            publish("CrazeLM is ready · offline")
            return
        }
        if (loading) return
        loading = true
        publish("Preparing CrazeLM…")
        val app = context.applicationContext
        executor.execute {
            try {
                val files = CrazeLmAssets.ensureDownloaded(app) { message -> publish(message) }
                publish("Loading SentencePiece tokenizer…")
                val tokenizer = CrazeLmTokenizer.load(files.tokenizer)
                require(tokenizer.vocabularySize == VOCAB_SIZE) {
                    "CrazeLM tokenizer vocabulary mismatch."
                }
                publish("Loading TensorFlow Lite model…")
                val nextRuntime = Runtime(files.model, tokenizer)
                runtime = nextRuntime
                publish("CrazeLM is ready · offline")
            } catch (error: Throwable) {
                publish("CrazeLM unavailable: " + (error.message ?: error.javaClass.simpleName))
            } finally {
                loading = false
            }
        }
    }

    fun predictNextWords(context: Context, beforeCursor: String, limit: Int = 3): List<String> {
        if (limit <= 0 || beforeCursor.isBlank()) return emptyList()
        val active = runtime ?: return emptyList()
        return try {
            active.predict(beforeCursor, limit)
        } catch (error: Throwable) {
            publish("CrazeLM inference failed: " + (error.message ?: error.javaClass.simpleName))
            emptyList()
        }
    }

    private class Runtime(modelFile: File, private val tokenizer: CrazeLmTokenizer) {
        private data class CandidatePiece(val id: Int, val word: String)
        private val interpreter: Interpreter
        private val lock = Any()
        private val candidatePieces: List<CandidatePiece>
        private val inputIds = Array(1) { IntArray(SEQUENCE_LENGTH) }
        private val inputLengths = intArrayOf(0)
        private val logitsOutput = Array(1) { FloatArray(VOCAB_SIZE) }
        private val inputs: Array<Any> = arrayOf(inputIds, inputLengths)
        private val outputs: Map<Int, Any> = hashMapOf(0 to logitsOutput)
        private val resultCache = object : LinkedHashMap<String, List<String>>(48, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<String>>?): Boolean =
                size > 48
        }

        init {
            require(modelFile.isFile && modelFile.length() > 0L) {
                "TFLite model file is missing."
            }
            val options = Interpreter.Options()
                .setNumThreads(2)
                .setUseXNNPACK(true)
            interpreter = Interpreter(modelFile, options)

            require(interpreter.inputTensorCount == 2) {
                "Expected input_ids and lengths model inputs."
            }
            require(interpreter.outputTensorCount == 1) {
                "Expected one next-token logits output."
            }
            val idsInput = interpreter.getInputTensor(0)
            val lengthsInput = interpreter.getInputTensor(1)
            val logitsOutput = interpreter.getOutputTensor(0)
            require(idsInput.dataType() == DataType.INT32 &&
                idsInput.shape().contentEquals(intArrayOf(1, SEQUENCE_LENGTH))) {
                "Unexpected input_ids tensor type or shape: " +
                    idsInput.dataType() + " " + idsInput.shape().contentToString()
            }
            require(lengthsInput.dataType() == DataType.INT32 &&
                lengthsInput.shape().contentEquals(intArrayOf(1))) {
                "Unexpected lengths tensor type or shape."
            }
            require(logitsOutput.dataType() == DataType.FLOAT32 &&
                logitsOutput.shape().contentEquals(intArrayOf(1, VOCAB_SIZE))) {
                "Unexpected logits tensor type or shape: " +
                    logitsOutput.dataType() + " " + logitsOutput.shape().contentToString()
            }

            // Resolve token strings once instead of allocating 100,000 strings per prediction.
            candidatePieces = (0 until VOCAB_SIZE).mapNotNull { id ->
                val piece = tokenizer.piece(id)
                if (!piece.startsWith("▁")) return@mapNotNull null
                val word = piece.substring(1)
                if (word.length !in 2..20 ||
                    !word.all { it.isLetter() || it == '\'' || it == '-' }) null
                else CandidatePiece(id, word)
            }
        }

        fun predict(text: String, limit: Int): List<String> {
            if (limit <= 0) return emptyList()
            val cacheKey = text.takeLast(220)
            synchronized(lock) { resultCache[cacheKey]?.let { return it.take(limit) } }

            val tokens = tokenizer.encode(cacheKey).takeLast(SEQUENCE_LENGTH)
            if (tokens.isEmpty()) return emptyList()
            val padded = inputIds[0]
            padded.fill(0)
            tokens.forEachIndexed { index, id -> padded[index] = id }
            inputLengths[0] = tokens.size

            synchronized(lock) {
                interpreter.runForMultipleInputsOutputs(inputs, outputs)
            }
            val logits = logitsOutput[0]
            val bestWords = arrayOfNulls<String>(3)
            val bestScores = FloatArray(3) { Float.NEGATIVE_INFINITY }
            val bestKeys = HashSet<String>(3)

            for (candidate in candidatePieces) {
                val score = logits[candidate.id]
                if (!score.isFinite() || !Words.known(candidate.word)) continue
                val normalized = candidate.word.lowercase()
                if (normalized in bestKeys) continue
                var insertAt = -1
                for (i in 0 until 3) {
                    if (score > bestScores[i]) { insertAt = i; break }
                }
                if (insertAt < 0) continue
                for (i in 2 downTo insertAt + 1) {
                    bestScores[i] = bestScores[i - 1]
                    bestWords[i] = bestWords[i - 1]
                }
                bestScores[insertAt] = score
                bestWords[insertAt] = candidate.word
                bestKeys.clear()
                bestWords.filterNotNull().forEach { bestKeys.add(it.lowercase()) }
            }
            val capitalize = text.lastOrNull()?.let { it == '.' || it == '!' || it == '?' } == true
            val result = bestWords.filterNotNull().map { word ->
                if (capitalize) word.replaceFirstChar { it.uppercase() } else word
            }
            synchronized(lock) { resultCache[cacheKey] = result }
            return result.take(limit)
        }
    }
}
