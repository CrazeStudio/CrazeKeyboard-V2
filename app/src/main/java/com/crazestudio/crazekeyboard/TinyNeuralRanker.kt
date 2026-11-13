package com.crazestudio.crazekeyboard

import android.content.Context
import kotlin.math.exp
import kotlin.math.max

/**
 * Tiny on-device neural reranker (6 inputs -> 8 hidden units -> 1 score).
 *
 * It does not generate text. It learns which candidate the user accepts and
 * nudges future ranking using local context statistics. Only model weights are
 * persisted here; no sentences or keystrokes are stored. Training is called
 * only for non-private, personalized fields.
 */
internal object TinyNeuralRanker {
    private const val PREFS = "craze_tiny_neural"
    private const val INPUTS = 6
    private const val HIDDEN = 8
    private const val LEARNING_RATE = 0.035f
    private val initialW1 = Array(HIDDEN) { h ->
        FloatArray(INPUTS) { i -> (((h * 13 + i * 7) % 11) - 5) * 0.012f }
    }
    private val initialB1 = FloatArray(HIDDEN) { 0.02f }
    private val initialW2 = FloatArray(HIDDEN) { i -> if (i == 0) 0.12f else 0.01f }
    private var w1 = Array(HIDDEN) { initialW1[it].copyOf() }
    private var b1 = initialB1.copyOf()
    private var w2 = initialW2.copyOf()
    private var b2 = 0f
    private var loaded = false

    private fun load(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            fun read(key: String, fallback: FloatArray): FloatArray {
                val values = prefs.getString(key, null)?.split(',')?.mapNotNull { it.toFloatOrNull() }
                return if (values?.size == fallback.size) values.toFloatArray() else fallback.copyOf()
            }
            val flat = read("w1", initialW1.flatMap { it.asIterable() }.toFloatArray())
            w1 = Array(HIDDEN) { h -> FloatArray(INPUTS) { i -> flat[h * INPUTS + i] } }
            b1 = read("b1", initialB1)
            w2 = read("w2", initialW2)
            b2 = prefs.getFloat("b2", 0f)
            loaded = true
        }
    }

    private fun sigmoid(value: Float): Float =
        (1.0 / (1.0 + exp(-value.coerceIn(-12f, 12f).toDouble()))).toFloat()

    private fun forward(features: FloatArray): Pair<Float, FloatArray> {
        val hidden = FloatArray(HIDDEN) { h ->
            var sum = b1[h]
            for (i in 0 until INPUTS) sum += w1[h][i] * features[i]
            kotlin.math.tanh(sum)
        }
        var output = b2
        for (h in 0 until HIDDEN) output += w2[h] * hidden[h]
        return sigmoid(output) to hidden
    }

    @Synchronized
    fun rank(context: Context, precedingWords: List<String>, candidates: List<String>, limit: Int = 3): List<String> {
        if (candidates.size < 2 || limit <= 0) return candidates.take(limit)
        load(context)
        return candidates.withIndex().sortedByDescending { item ->
            val features = UserLanguageModel.rankingFeatures(
                context, precedingWords, item.value, item.index, candidates.size
            )
            forward(features).first
        }.take(limit).map { it.value }
    }

    /** Pairwise online learning: accepted candidate positive, visible alternatives negative. */
    @Synchronized
    fun learn(context: Context, precedingWords: List<String>, accepted: String, alternatives: List<String>) {
        if (accepted.isBlank() || alternatives.isEmpty()) return
        load(context)
        val all = (listOf(accepted) + alternatives.filter { !it.equals(accepted, true) }).distinct().take(4)
        if (all.size < 2) return
        val features = all.mapIndexed { index, word ->
            UserLanguageModel.rankingFeatures(context, precedingWords, word, index, all.size)
        }
        repeat(2) {
            for (negativeIndex in 1 until all.size) {
                val (positiveScore, positiveHidden) = forward(features[0])
                val (negativeScore, negativeHidden) = forward(features[negativeIndex])
                val error = (positiveScore - negativeScore - 0.25f).coerceIn(-1f, 1f)
                val outputGradient = (1f - sigmoid(error)) * LEARNING_RATE
                val oldW2 = w2.copyOf()
                for (h in 0 until HIDDEN) {
                    w2[h] += outputGradient * (positiveHidden[h] - negativeHidden[h])
                    val positiveGrad = outputGradient * oldW2[h] * (1f - positiveHidden[h] * positiveHidden[h])
                    val negativeGrad = -outputGradient * oldW2[h] * (1f - negativeHidden[h] * negativeHidden[h])
                    for (i in 0 until INPUTS) {
                        w1[h][i] += positiveGrad * features[0][i] + negativeGrad * features[negativeIndex][i]
                    }
                    b1[h] += positiveGrad + negativeGrad
                }
                b2 += outputGradient * (positiveScore - negativeScore)
            }
        }
        persist(context)
    }

    private fun persist(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putString("w1", w1.flatMap { it.asIterable() }.joinToString(","))
            .putString("b1", b1.joinToString(","))
            .putString("w2", w2.joinToString(","))
            .putFloat("b2", b2)
            .apply()
    }

    @Synchronized
    fun clear(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        w1 = Array(HIDDEN) { initialW1[it].copyOf() }
        b1 = initialB1.copyOf()
        w2 = initialW2.copyOf()
        b2 = 0f
        loaded = true
    }
}
