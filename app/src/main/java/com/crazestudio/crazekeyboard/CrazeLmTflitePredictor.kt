package com.crazestudio.crazekeyboard

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.os.SystemClock
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Low-RAM on-device next-word prediction with one memory-mapped TensorFlow Lite Interpreter.
 *
 * Memory design:
 *  - Model weights are memory-mapped read-only (MappedByteBuffer) so the OS demand-pages and
 *    reclaims clean pages instead of pinning private dirty heap.
 *  - Low-RAM mode disables XNNPACK's large weight-packing scratch cache and auto-unloads the
 *    interpreter shortly after the keyboard closes.
 *  - Candidate vocabulary keeps only primitive IDs (~48 KB) and decodes Strings only for the
 *    top winning tokens per inference, eliminating ~12,000 retained String objects.
 */
internal object CrazeLmTflitePredictor {
    private const val SEQUENCE_LENGTH = 48
    private const val VOCAB_SIZE = 100_000
    private const val CANDIDATE_SCAN = 64
    private const val PRESSURE_COOLDOWN_MS = 180_000L
    private const val IDLE_UNLOAD_LOW_RAM_MS = 30_000L
    private const val IDLE_UNLOAD_BALANCED_MS = 120_000L
    private const val READY_STATUS = "CrazeLM ready · offline"

    @Volatile private var runtime: Runtime? = null
    @Volatile private var loading = false
    @Volatile private var files: CrazeLmFiles? = null
    @Volatile private var unloadedForMemory = false
    @Volatile private var reloadAllowedAt = 0L
    private var trimCallbacks: ComponentCallbacks2? = null
    private var idleUnloadFuture: ScheduledFuture<*>? = null
    @Volatile var status: String = "CrazeLM not downloaded"
        private set
    @Volatile private var observer: ((String) -> Unit)? = null
    private val executor = Executors.newSingleThreadScheduledExecutor { task ->
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

    fun refreshInitialStatus(context: Context) {
        val app = context.applicationContext
        if (runtime != null) {
            val modeLabel = if (Prefs.lowRamAi(app)) "low-RAM" else "balanced"
            publish("$READY_STATUS ($modeLabel)")
        } else if (CrazeLmAssets.hasLocalFiles(app)) {
            val sizeMb = CrazeLmAssets.localModelSizeMb(app)
            publish("CrazeLM downloaded (${sizeMb} MB) · sleeping (0 MB RAM)")
        } else {
            publish("CrazeLM not downloaded · using fast local engine")
        }
    }

    @Synchronized
    fun cancelIdleUnload() {
        idleUnloadFuture?.cancel(false)
        idleUnloadFuture = null
    }

    @Synchronized
    fun scheduleIdleUnload(context: Context) {
        idleUnloadFuture?.cancel(false)
        if (runtime == null) return
        val delayMs = if (Prefs.lowRamAi(context.applicationContext)) IDLE_UNLOAD_LOW_RAM_MS else IDLE_UNLOAD_BALANCED_MS
        idleUnloadFuture = executor.schedule({
            unload(0L, "CrazeLM sleeping (0 MB active RAM)")
        }, delayMs, TimeUnit.MILLISECONDS)
    }

    @Synchronized
    fun unloadNow(context: Context) {
        cancelIdleUnload()
        if (runtime != null) {
            unload(0L, "CrazeLM unloaded · 0 MB active RAM")
        } else {
            refreshInitialStatus(context)
        }
    }

    @Synchronized
    fun initializeAsync(context: Context, allowNetworkDownload: Boolean = false) {
        val app = context.applicationContext
        cancelIdleUnload()
        if (runtime != null) {
            val modeLabel = if (Prefs.lowRamAi(app)) "low-RAM" else "balanced"
            publish("$READY_STATUS ($modeLabel)")
            return
        }
        if (loading) return
        if (!allowNetworkDownload && !CrazeLmAssets.hasLocalFiles(app)) {
            refreshInitialStatus(app)
            return
        }
        loading = true
        publish("Preparing CrazeLM…")
        executor.execute {
            try {
                val known = files?.takeIf { it.model.isFile && it.tokenizer.isFile }
                val resolved = known ?: CrazeLmAssets.ensureAvailable(app, allowNetworkDownload) { message ->
                    publish(message)
                }?.also { files = it }
                if (resolved == null) {
                    refreshInitialStatus(app)
                    return@execute
                }
                publish("Loading compact tokenizer…")
                val tokenizer = CrazeLmTokenizer.load(resolved.tokenizer)
                require(tokenizer.vocabularySize == VOCAB_SIZE) {
                    "CrazeLM tokenizer vocabulary mismatch."
                }
                val lowRam = Prefs.lowRamAi(app)
                publish(if (lowRam) "Mapping model (low-RAM)…" else "Loading TensorFlow Lite model…")
                val nextRuntime = Runtime(resolved.model, tokenizer, lowRam)
                unloadedForMemory = false
                runtime = nextRuntime
                registerTrimCallbacks(app)
                val modeLabel = if (lowRam) "low-RAM" else "balanced"
                publish("$READY_STATUS ($modeLabel)")
            } catch (error: Throwable) {
                publish("CrazeLM unavailable: " + (error.message ?: error.javaClass.simpleName))
            } finally {
                loading = false
            }
        }
    }

    /** Registered once; releases model weights & scratch buffers when Android signals memory pressure. */
    @Synchronized
    private fun registerTrimCallbacks(app: Context) {
        if (trimCallbacks != null) return
        val callbacks = object : ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) {
                val lowRam = Prefs.lowRamAi(app)
                val shouldUnload = level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND ||
                    level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
                    level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
                    (lowRam && level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
                if (shouldUnload) {
                    unload(PRESSURE_COOLDOWN_MS, "CrazeLM paused (memory saver)")
                }
            }
            override fun onConfigurationChanged(newConfig: Configuration) = Unit
            override fun onLowMemory() {
                unload(PRESSURE_COOLDOWN_MS, "CrazeLM paused (low memory)")
            }
        }
        app.registerComponentCallbacks(callbacks)
        trimCallbacks = callbacks
    }

    @Synchronized
    private fun unload(cooldownMs: Long, statusMessage: String) {
        val active = runtime ?: return
        runtime = null
        unloadedForMemory = true
        reloadAllowedAt = SystemClock.elapsedRealtime() + cooldownMs
        inferenceRequestId.incrementAndGet()
        executor.execute { active.close() }
        publish(statusMessage)
    }

    fun predictNextWordsAsync(
        context: Context,
        beforeCursor: String,
        limit: Int = 3,
        onResult: (List<String>) -> Unit
    ) {
        if (limit <= 0 || beforeCursor.isBlank()) return
        val app = context.applicationContext
        if (runtime == null) {
            if (unloadedForMemory && SystemClock.elapsedRealtime() >= reloadAllowedAt && CrazeLmAssets.hasLocalFiles(app)) {
                initializeAsync(app, allowNetworkDownload = false)
            }
            return
        }
        val requestId = inferenceRequestId.incrementAndGet()
        inferenceExecutor.execute {
            if (requestId != inferenceRequestId.get()) return@execute
            val result = predictNextWords(beforeCursor, limit)
            if (requestId == inferenceRequestId.get()) {
                try { onResult(result) } catch (_: Exception) { }
            }
        }
    }

    fun predictNextWords(beforeCursor: String, limit: Int = 3): List<String> {
        if (limit <= 0 || beforeCursor.isBlank()) return emptyList()
        val active = runtime ?: return emptyList()
        return try {
            active.predict(beforeCursor, limit)
        } catch (error: Throwable) {
            publish("CrazeLM inference failed: " + (error.message ?: error.javaClass.simpleName))
            emptyList()
        }
    }

    private class Runtime(modelFile: File, private val tokenizer: CrazeLmTokenizer, lowRam: Boolean) {
        private val interpreter: Interpreter
        private var mappedModel: MappedByteBuffer?
        private val lock = Any()
        private var closed = false
        private val candidateIds: IntArray
        private val inputIds = Array(1) { IntArray(SEQUENCE_LENGTH) }
        private val inputLengths = intArrayOf(0)
        private val logitsOutput = Array(1) { FloatArray(VOCAB_SIZE) }
        private val inputs: Array<Any> = arrayOf(inputIds, inputLengths)
        private val outputs = hashMapOf<Int, Any>(0 to logitsOutput)
        private val heapIds = IntArray(CANDIDATE_SCAN)
        private val heapScores = FloatArray(CANDIDATE_SCAN)
        private val resultCache = object : LinkedHashMap<String, List<String>>(8, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<String>>?): Boolean =
                size > 8
        }

        init {
            require(modelFile.isFile && modelFile.length() > 0L) {
                "TFLite model file is missing."
            }
            val mapped = FileInputStream(modelFile).use { stream ->
                stream.channel.map(FileChannel.MapMode.READ_ONLY, 0, modelFile.length())
            }
            mappedModel = mapped
            val options = Interpreter.Options()
                .setNumThreads(1)
                .setUseXNNPACK(!lowRam)
            interpreter = Interpreter(mapped, options)

            require(interpreter.inputTensorCount == 2) {
                "Expected input_ids and lengths model inputs."
            }
            require(interpreter.outputTensorCount == 1) {
                "Expected one next-token logits output."
            }
            val idsInput = interpreter.getInputTensor(0)
            val lengthsInput = interpreter.getInputTensor(1)
            val logitsTensor = interpreter.getOutputTensor(0)
            require(idsInput.dataType() == DataType.INT32 &&
                idsInput.shape().contentEquals(intArrayOf(1, SEQUENCE_LENGTH))) {
                "Unexpected input_ids tensor type or shape."
            }
            require(lengthsInput.dataType() == DataType.INT32 &&
                lengthsInput.shape().contentEquals(intArrayOf(1))) {
                "Unexpected lengths input tensor."
            }
            require(logitsTensor.dataType() == DataType.FLOAT32 &&
                logitsTensor.shape().contentEquals(intArrayOf(1, VOCAB_SIZE))) {
                "Unexpected logits tensor type or shape."
            }

            // Store only primitive IDs (~48 KB) — zero String allocations during setup.
            val ids = IntArray(16_000)
            var count = 0
            for (id in 0 until VOCAB_SIZE) {
                if (!tokenizer.isCandidateWordPiece(id)) continue
                if (count < ids.size) ids[count++] = id
            }
            candidateIds = ids.copyOf(count)
        }

        fun close() {
            synchronized(lock) {
                if (closed) return
                closed = true
                resultCache.clear()
                try { interpreter.close() } catch (_: Throwable) { }
                mappedModel = null
            }
        }

        fun predict(text: String, limit: Int): List<String> {
            if (limit <= 0) return emptyList()
            val cacheKey = text.takeLast(180)
            synchronized(lock) {
                if (closed) return emptyList()
                resultCache[cacheKey]?.let { return it.take(limit) }
            }

            val tokens = tokenizer.encode(cacheKey).takeLast(SEQUENCE_LENGTH)
            if (tokens.isEmpty()) return emptyList()

            synchronized(lock) {
                if (closed) return emptyList()
                val padded = inputIds[0]
                padded.fill(0)
                tokens.forEachIndexed { index, id -> padded[index] = id }
                inputLengths[0] = tokens.size
                interpreter.runForMultipleInputsOutputs(inputs, outputs)
                val logits = logitsOutput[0]

                var heapSize = 0
                for (i in candidateIds.indices) {
                    val score = logits[candidateIds[i]]
                    if (!score.isFinite()) continue
                    if (heapSize < CANDIDATE_SCAN) {
                        var child = heapSize++
                        heapIds[child] = candidateIds[i]
                        heapScores[child] = score
                        while (child > 0) {
                            val parent = (child - 1) ushr 1
                            if (heapScores[parent] <= heapScores[child]) break
                            swap(parent, child)
                            child = parent
                        }
                    } else if (score > heapScores[0]) {
                        heapIds[0] = candidateIds[i]
                        heapScores[0] = score
                        var parent = 0
                        while (true) {
                            val left = parent * 2 + 1
                            if (left >= heapSize) break
                            val right = left + 1
                            val child = if (right < heapSize && heapScores[right] < heapScores[left]) right else left
                            if (heapScores[parent] <= heapScores[child]) break
                            swap(parent, child)
                            parent = child
                        }
                    }
                }

                // Insertion sort the small heap in descending score order without extra object allocation.
                val order = IntArray(heapSize) { it }
                for (i in 1 until heapSize) {
                    val idx = order[i]
                    val s = heapScores[idx]
                    var j = i - 1
                    while (j >= 0 && heapScores[order[j]] < s) {
                        order[j + 1] = order[j]
                        j--
                    }
                    order[j + 1] = idx
                }

                val seen = HashSet<String>(limit * 2)
                val capitalize = text.lastOrNull()?.let { it == '.' || it == '!' || it == '?' } == true
                val result = ArrayList<String>(limit)
                for (slot in order) {
                    val word = tokenizer.wordPiece(heapIds[slot]) ?: continue
                    if (!Words.known(word) || !seen.add(word.lowercase())) continue
                    result.add(if (capitalize) word.replaceFirstChar { it.uppercase() } else word)
                    if (result.size >= limit) break
                }
                val finalResult = result.toList()
                resultCache[cacheKey] = finalResult
                return finalResult
            }
        }

        private fun swap(a: Int, b: Int) {
            val id = heapIds[a]
            heapIds[a] = heapIds[b]
            heapIds[b] = id
            val score = heapScores[a]
            heapScores[a] = heapScores[b]
            heapScores[b] = score
        }
    }
}
