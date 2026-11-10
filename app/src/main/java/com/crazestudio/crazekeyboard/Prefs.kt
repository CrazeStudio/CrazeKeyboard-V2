package com.crazestudio.crazekeyboard

import android.content.Context

class Theme(val bg: Int, val key: Int, val special: Int, val text: Int, val accent: Int, val accentText: Int)

object Prefs {
    val themeNames = listOf("Light", "Dark", "Craze")
    val themes = listOf(
        Theme(0xFFF1F3F8.toInt(), 0xFFFFFFFF.toInt(), 0xFFE2E7F0.toInt(), 0xFF202633.toInt(), 0xFF6656D9.toInt(), 0xFFFFFFFF.toInt()),
        Theme(0xFF17191F.toInt(), 0xFF292D36.toInt(), 0xFF222630.toInt(), 0xFFF0F2F7.toInt(), 0xFFB9A9FF.toInt(), 0xFF17191F.toInt()),
        Theme(0xFF100F1B.toInt(), 0xFF262238.toInt(), 0xFF1C192B.toInt(), 0xFFF5F0FF.toInt(), 0xFFFF75C8.toInt(), 0xFF231426.toInt())
    )
    private const val SEP = "\u0001"
    @Volatile private var learnedCache: List<String>? = null
    fun sp(c: Context) = c.getSharedPreferences("craze", Context.MODE_PRIVATE)
    fun b(c: Context, k: String, d: Boolean) = sp(c).getBoolean(k, d)
    fun setB(c: Context, k: String, v: Boolean) = sp(c).edit().putBoolean(k, v).apply()
    fun i(c: Context, k: String, d: Int) = sp(c).getInt(k, d)
    fun setI(c: Context, k: String, v: Int) = sp(c).edit().putInt(k, v).apply()
    fun theme(c: Context) = themes[i(c, "theme", 0).coerceIn(0, themes.size - 1)]
    fun nextTheme(c: Context) = setI(c, "theme", (i(c, "theme", 0) + 1) % themes.size)
    fun nextOneHand(c: Context) = setI(c, "onehand", (i(c, "onehand", 0) + 1) % 3)
    fun keyH(c: Context) = i(c, "keyh", 46)
    fun sig(c: Context) = "${i(c, "theme", 0)}|${i(c, "onehand", 0)}|${keyH(c)}|${b(c, "numrow", true)}|${b(c, "clipboardHistory", true)}|${b(c, "autocorrect", true)}|${b(c, "nextWord", true)}"

    private fun list(c: Context, k: String) = (sp(c).getString(k, "") ?: "").split(SEP).filter { it.isNotEmpty() }
    private fun put(c: Context, k: String, l: List<String>) = sp(c).edit().putString(k, l.joinToString(SEP)).apply()
    fun clips(c: Context) = list(c, "clips")
    fun addClip(c: Context, s: String) {
        val safeText = s.take(4_000)
        if (safeText.isNotBlank()) put(c, "clips", (listOf(safeText) + clips(c).filter { it != safeText }).take(20))
    }
    fun clearClips(c: Context) = put(c, "clips", emptyList())
    fun recents(c: Context) = list(c, "recent")
    fun addRecent(c: Context, e: String) = put(c, "recent", (listOf(e) + recents(c).filter { it != e }).take(32))
    fun learned(c: Context): List<String> {
        learnedCache?.let { return it }
        return synchronized(this) { learnedCache ?: list(c, "learned").also { learnedCache = it } }
    }
    fun isLearned(w: String) = learnedCache?.any { it.equals(w, ignoreCase = true) } == true
    fun learn(c: Context, w: String) {
        val word = w.trim().lowercase()
        if (word.isEmpty() || word.length > 40 || !word.any { it.isLetter() }) return
        val current = learned(c).map { it.lowercase() }
        if (word !in current) {
            val updated = (listOf(word) + current).take(500)
            put(c, "learned", updated)
            learnedCache = updated
        }
    }
    fun clearLearned(c: Context) {
        put(c, "learned", emptyList())
        learnedCache = emptyList()
    }
}