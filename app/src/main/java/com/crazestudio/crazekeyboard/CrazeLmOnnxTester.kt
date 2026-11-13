package com.crazestudio.crazekeyboard

import android.os.SystemClock
import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

/**
 * Developer-only smoke test for a locally selected CrazeLM ONNX file.
 *
 * This intentionally tests the exported graph independently from SentencePiece:
 * IDs are valid-shaped sample inputs, not encoded words. It must not be wired into
 * live suggestions until tokenizer parity and word-candidate decoding are tested.
 */
internal object CrazeLmOnnxTester {
    private const val INPUT_NAME = "input_ids"
    private const val OUTPUT_NAME = "logits"
    private const val EXPECTED_VOCAB_SIZE = 100_000
    private const val SAMPLE_SEQUENCE_LENGTH = 5
    private const val TOP_K = 5

    fun smokeTest(modelFile: File): String {
        require(modelFile.isFile && modelFile.length() > 0L) {
            "The selected ONNX file is missing or empty."
        }

        val environment = OrtEnvironment.getEnvironment()
        val options = OrtSession.SessionOptions()
        options.setIntraOpNumThreads(2)

        val loadStarted = SystemClock.elapsedRealtime()
        val session = try {
            environment.createSession(modelFile.absolutePath, options)
        } finally {
            options.close()
        }
        val loadMillis = SystemClock.elapsedRealtime() - loadStarted

        try {
            val inputInfo = session.getInputInfo()[INPUT_NAME]?.getInfo() as? TensorInfo
                ?: error("Model input '$INPUT_NAME' is missing or is not a tensor.")
            val outputInfo = session.getOutputInfo()[OUTPUT_NAME]?.getInfo() as? TensorInfo
                ?: error("Model output '$OUTPUT_NAME' is missing or is not a tensor.")

            require(inputInfo.getType() == OnnxJavaType.INT64) {
                "Expected input_ids to be INT64, found ${inputInfo.getType()}."
            }
            require(outputInfo.getShape().size == 3) {
                "Expected logits to have [batch, sequence, vocabulary] dimensions."
            }
            val declaredVocab = outputInfo.getShape().last()
            require(declaredVocab == -1L || declaredVocab == EXPECTED_VOCAB_SIZE.toLong()) {
                "Expected a $EXPECTED_VOCAB_SIZE-token vocabulary, found $declaredVocab."
            }
            require(
                outputInfo.getType() == OnnxJavaType.FLOAT ||
                    outputInfo.getType() == OnnxJavaType.FLOAT16 ||
                    outputInfo.getType() == OnnxJavaType.BFLOAT16
            ) {
                "Unsupported logits type: ${outputInfo.getType()}."
            }

            // Stable, in-range IDs only. This verifies graph execution, not text quality.
            val sampleIds = longArrayOf(2L, 42L, 314L, 2718L, 1618L)
            val inputBytes = ByteBuffer.allocateDirect(sampleIds.size * java.lang.Long.BYTES)
                .order(ByteOrder.nativeOrder())
            val inputBuffer = inputBytes.asLongBuffer().apply { put(sampleIds); flip() }
            val inputTensor = OnnxTensor.createTensor(
                environment,
                inputBuffer,
                longArrayOf(1L, sampleIds.size.toLong())
            )

            val inferenceStarted = SystemClock.elapsedRealtime()
            val topTokens = inputTensor.use { tensor ->
                session.run(mapOf(INPUT_NAME to tensor)).use { result ->
                    val output = result.get(OUTPUT_NAME).orElse(null) as? OnnxTensor
                        ?: error("Model did not return a tensor named '$OUTPUT_NAME'.")
                    val actualInfo = output.getInfo() as TensorInfo
                    val actualShape = actualInfo.getShape()
                    require(actualShape.size == 3 &&
                        actualShape[0] == 1L &&
                        actualShape[1] == sampleIds.size.toLong() &&
                        actualShape[2] == EXPECTED_VOCAB_SIZE.toLong()
                    ) {
                        "Unexpected runtime logits shape: ${actualShape.contentToString()}."
                    }

                    // ORT converts FP16/BF16 output to float when using getFloatBuffer().
                    val logits = output.getFloatBuffer()
                        ?: error("ONNX Runtime could not expose logits as float values.")
                    val lastStepOffset = (sampleIds.size - 1) * EXPECTED_VOCAB_SIZE
                    require(logits.remaining() >= lastStepOffset + EXPECTED_VOCAB_SIZE) {
                        "Logits buffer is shorter than the declared output shape."
                    }

                    val best = ArrayList<Pair<Int, Float>>(TOP_K)
                    for (tokenId in 0 until EXPECTED_VOCAB_SIZE) {
                        val score = logits.get(lastStepOffset + tokenId)
                        if (!score.isFinite()) continue
                        if (best.size < TOP_K || score > best.last().second) {
                            best.add(tokenId to score)
                            best.sortByDescending { it.second }
                            if (best.size > TOP_K) best.removeAt(best.lastIndex)
                        }
                    }
                    require(best.isNotEmpty()) { "The model returned no finite logits." }
                    best
                }
            }
            val inferenceMillis = SystemClock.elapsedRealtime() - inferenceStarted
            val sizeMiB = modelFile.length() / (1024.0 * 1024.0)
            val tokens = topTokens.joinToString(", ") {
                "${it.first} (${String.format(Locale.US, "%.3f", it.second)})"
            }

            return buildString {
                appendLine("PASS · ONNX Runtime loaded the model and completed CPU inference.")
                appendLine("File: ${modelFile.name} · ${String.format(Locale.US, "%.2f", sizeMiB)} MiB")
                appendLine("Input: INT64 [1, $SAMPLE_SEQUENCE_LENGTH]")
                appendLine("Output: FLOAT/FLOAT16 logits [1, $SAMPLE_SEQUENCE_LENGTH, $EXPECTED_VOCAB_SIZE]")
                appendLine("Session load: ${loadMillis} ms · inference: ${inferenceMillis} ms")
                appendLine("Top token IDs at final position: $tokens")
                append("Smoke test only: the sample IDs are not words. SentencePiece encoding, token decoding, and live keyboard suggestions are not enabled yet.")
            }
        } finally {
            session.close()
        }
    }
}
