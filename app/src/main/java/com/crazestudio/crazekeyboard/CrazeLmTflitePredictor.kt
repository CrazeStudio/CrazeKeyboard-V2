package com.crazestudio.crazekeyboard

import android.content.Context
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.File
import java.util.PriorityQueue
import java.util.concurrent.Executors

/**
 * On-device next-word prediction with one long-lived TensorFlow Lite Interpreter.
 * Download and initialization are asynchronous; keyboard typing never waits for setup.
 */
internal object CrazeLmTflitePredictor {
    private const val SEQUENCE_LENGTH = 48
    private const val VOCAB_SIZE = 100_000
    private const val CANDIDATE_SCAN = 192

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
        private val interpreter: Interpreter
        private val lock = Any()

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
        }

        fun predict(text: String, limit: Int): List<String> {
            val tokens = tokenizer.encode(text).takeLast(SEQUENCE_LENGTH)
            if (tokens.isEmpty()) return emptyList()
            val padded = IntArray(SEQUENCE_LENGTH)
            tokens.forEachIndexed { index, id -> padded[index] = id }
            val lengths = intArrayOf(tokens.size)
            val logitsOutput = Array(1) { FloatArray(VOCAB_SIZE) }
            val inputs = arrayOf<Any>(arrayOf(padded), lengths)
            val outputs = hashMapOf<Int, Any>(0 to logitsOutput)

            synchronized(lock) {
                interpreter.runForMultipleInputsOutputs(inputs, outputs)
            }
            val logits = logitsOutput[0]

            data class Candidate(val word: String, val score: Float)
            val best = PriorityQueue<Candidate>(CANDIDATE_SCAN, compareBy { it.score })
            for (id in logits.indices) {
                val score = logits[id]
                if (!score.isFinite()) continue
                val piece = tokenizer.piece(id)
                if (!piece.startsWith("▁")) continue
                val word = piece.substring(1)
                if (word.length !in 2..20 ||
                    !word.all { it.isLetter() || it == '\'' || it == '-' }) continue
                if (!Words.known(word)) continue
                val candidate = Candidate(word, score)
                if (best.size < CANDIDATE_SCAN) best.add(candidate)
                else if (score > best.peek().score) {
                    best.poll()
                    best.add(candidate)
                }
            }
            val seen = HashSet<String>()
            return best.toList().sortedByDescending { it.score }
                .map { it.word }
                .filter { seen.add(it.lowercase()) }
                .take(limit)
                .map { candidate ->
                    if (text.lastOrNull()?.let { it == '.' || it == '!' || it == '?' } == true) {
                        candidate.replaceFirstChar { it.uppercase() }
                    } else candidate
                }
        }
    }
}
