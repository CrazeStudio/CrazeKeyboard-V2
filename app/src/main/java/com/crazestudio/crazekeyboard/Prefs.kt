package com.crazestudio.crazekeyboard

import android.content.Context

class Theme(val bg: Int, val key: Int, val special: Int, val text: Int, val accent: Int, val accentText: Int)

object Prefs {
    val themeNames = listOf("Light", "Dark", "Craze")
    val themes = listOf(
        Theme(0xFFECEFF1.toInt(), 0xFFFFFFFF.toInt(), 0xFFCFD8DC.toInt(), 0xFF202124.toInt(), 0xFF6C4DFF.toInt(), 0xFFFFFFFF.toInt()),
        Theme(0xFF1E1F24.toInt(), 0xFF3A3C43.toInt(), 0xFF2B2D33.toInt(), 0xFFECECEC.toInt(), 0xFFB39DFF.toInt(), 0xFF1E1F24.toInt()),
        Theme(0xFF120A2A.toInt(), 0xFF2A1B54.toInt(), 0xFF1D1240.toInt(), 0xFFF3E8FF.toInt(), 0xFFFF4FD8.toInt(), 0xFF120A2A.toInt())
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
    fun sig(c: Context) = "${i(c, "theme", 0)}|${i(c, "onehand", 0)}|${keyH(c)}|${b(c, "numrow", true)}"

    private fun list(c: Context, k: String) = (sp(c).getString(k, "") ?: "").split(SEP).filter { it.isNotEmpty() }
    private fun put(c: Context, k: String, l: List<String>) = sp(c).edit().putString(k, l.joinToString(SEP)).apply()
    fun clips(c: Context) = list(c, "clips")
    fun addClip(c: Context, s: String) { if (s.isNotBlank()) put(c, "clips", (listOf(s) + clips(c).filter { it != s }).take(20)) }
    fun clearClips(c: Context) = put(c, "clips", emptyList())
    fun recents(c: Context) = list(c, "recent")
    fun addRecent(c: Context, e: String) = put(c, "recent", (listOf(e) + recents(c).filter { it != e }).take(32))
    fun learned(c: Context): List<String> {
        learnedCache?.let { return it }
        return synchronized(this) {
            learnedCache ?: list(c, "learned").also { learnedCache = it }
        }
    }
    fun isLearned(w: String) = learnedCache?.contains(w) == true

    fun learn(c: Context, w: String) {
        val current = learned(c)
        if (w !in current) {
            val updated = (listOf(w) + current).take(500)
            put(c, "learned", updated)
            learnedCache = updated
        }
    }
}
