package com.crazestudio.crazekeyboard

import android.content.Context
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/**
 * On-device next-word prediction with one long-lived TensorFlow Lite Interpreter.
 * Model setup and inference run away from the keyboard's typing thread.
 */
internal object CrazeLmTflitePredictor {
    private const val SEQUENCE_LENGTH = 48
    private const val VOCAB_SIZE = 100_000
    // A smaller heap means fewer temporary objects and less dictionary I/O per keystroke.
    private const val CANDIDATE_SCAN = 128

    @Volatile private var runtime: Runtime? = null
    @Volatile private var loading = false
    @Volatile var status: String = "CrazeLM is not downloaded"
        private set
    @Volatile private var observer: ((String) -> Unit)? = null
    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "craze-lm-runtime").apply { isDaemon = true }
    }
    private val inferenceExecutor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "craze-lm-inference").apply { isDaemon = true }
    }
    private val inferenceRequestId = AtomicLong()

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

    fun predictNextWordsAsync(
        context: Context,
        beforeCursor: String,
        limit: Int = 3,
        onResult: (List<String>) -> Unit
    ) {
        if (limit <= 0 || beforeCursor.isBlank() || runtime == null) return
        val app = context.applicationContext
        val requestId = inferenceRequestId.incrementAndGet()
        inferenceExecutor.execute {
            if (requestId != inferenceRequestId.get()) return@execute
            val result = predictNextWords(app, beforeCursor, limit)
            if (requestId == inferenceRequestId.get()) {
                try { onResult(result) } catch (_: Exception) { }
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
        // Compact primitive/string arrays avoid one CandidatePiece object per vocabulary entry.
        private val candidateIds: IntArray
        private val candidateWords: Array<String>
        private val inputIds = Array(1) { IntArray(SEQUENCE_LENGTH) }
        private val inputLengths = intArrayOf(0)
        private val logitsOutput = Array(1) { FloatArray(VOCAB_SIZE) }
        private val inputs: Array<Any> = arrayOf(inputIds, inputLengths)
        private val outputs = hashMapOf<Int, Any>(0 to logitsOutput)
        private val resultCache = object : LinkedHashMap<String, List<String>>(24, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<String>>?): Boolean =
                size > 24
        }

        init {
            require(modelFile.isFile && modelFile.length() > 0L) {
                "TFLite model file is missing."
            }
            // One CPU thread minimizes concurrent working memory on low-RAM phones.
            val options = Interpreter.Options()
                .setNumThreads(1)
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
                "Unexpected lengths input tensor."
            }
            require(logitsOutput.dataType() == DataType.FLOAT32 &&
                logitsOutput.shape().contentEquals(intArrayOf(1, VOCAB_SIZE))) {
                "Unexpected logits tensor type or shape: " +
                    logitsOutput.dataType() + " " + logitsOutput.shape().contentToString()
            }

            val ids = ArrayList<Int>(12_000)
            val words = ArrayList<String>(12_000)
            for (id in 0 until VOCAB_SIZE) {
                val piece = tokenizer.piece(id)
                if (!piece.startsWith("▁")) continue
                val word = piece.substring(1)
                if (word.length !in 2..20 ||
                    !word.all { it.isLetter() || it == '\'' || it == '-' }) continue
                ids.add(id)
                words.add(word)
            }
            candidateIds = ids.toIntArray()
            candidateWords = words.toTypedArray()
        }

        fun predict(text: String, limit: Int): List<String> {
            if (limit <= 0) return emptyList()
            val cacheKey = text.takeLast(180)
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

            // Primitive min-heap: avoids allocating hundreds of ScoredCandidate objects each call.
            val heapIds = IntArray(CANDIDATE_SCAN)
            val heapScores = FloatArray(CANDIDATE_SCAN)
            var heapSize = 0
            for (i in candidateIds.indices) {
                val score = logits[candidateIds[i]]
                if (!score.isFinite()) continue
                if (heapSize < CANDIDATE_SCAN) {
                    var child = heapSize++
                    heapIds[child] = i
                    heapScores[child] = score
                    while (child > 0) {
                        val parent = (child - 1) ushr 1
                        if (heapScores[parent] <= heapScores[child]) break
                        swap(heapIds, heapScores, parent, child)
                        child = parent
                    }
                } else if (score > heapScores[0]) {
                    heapIds[0] = i
                    heapScores[0] = score
                    var parent = 0
                    while (true) {
                        val left = parent * 2 + 1
                        if (left >= heapSize) break
                        val right = left + 1
                        val child = if (right < heapSize && heapScores[right] < heapScores[left]) right else left
                        if (heapScores[parent] <= heapScores[child]) break
                        swap(heapIds, heapScores, parent, child)
                        parent = child
                    }
                }
            }

            val order = (0 until heapSize).sortedByDescending { heapScores[it] }
            val seen = HashSet<String>(limit * 2)
            val capitalize = text.lastOrNull()?.let { it == '.' || it == '!' || it == '?' } == true
            val result = ArrayList<String>(limit)
            for (slot in order) {
                val word = candidateWords[heapIds[slot]]
                if (!Words.known(word) || !seen.add(word.lowercase())) continue
                result.add(if (capitalize) word.replaceFirstChar { it.uppercase() } else word)
                if (result.size >= limit) break
            }
            val finalResult = result.toList()
            synchronized(lock) { resultCache[cacheKey] = finalResult }
            return finalResult
        }

        private fun swap(ids: IntArray, scores: FloatArray, a: Int, b: Int) {
            val id = ids[a]
            ids[a] = ids[b]
            ids[b] = id
            val score = scores[a]
            scores[a] = scores[b]
            scores[b] = score
        }
    }
}
