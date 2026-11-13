package com.crazestudio.crazekeyboard

import android.content.Context
import android.graphics.Color
import android.content.SharedPreferences

class Theme(val bg: Int, val key: Int, val special: Int, val text: Int, val accent: Int, val accentText: Int)

object Prefs {
    val themeNames = listOf("System", "Dark", "Craze", "🌿 Sage Garden", "🏜 Sandstone", "〰 Mist Blue")
    val themes = listOf(
        Theme(0xFFF1F3F8.toInt(), 0xFFFFFFFF.toInt(), 0xFFE2E7F0.toInt(), 0xFF202633.toInt(), 0xFF6656D9.toInt(), 0xFFFFFFFF.toInt()),
        Theme(0xFF17191F.toInt(), 0xFF292D36.toInt(), 0xFF222630.toInt(), 0xFFF0F2F7.toInt(), 0xFFB9A9FF.toInt(), 0xFF17191F.toInt()),
        Theme(0xFF100F1B.toInt(), 0xFF262238.toInt(), 0xFF1C192B.toInt(), 0xFFF5F0FF.toInt(), 0xFFFF75C8.toInt(), 0xFF231426.toInt()),
        Theme(0xFFE8F0E6.toInt(), 0xFFFFFFFF.toInt(), 0xFFD5E5D2.toInt(), 0xFF263328.toInt(), 0xFF4F8B58.toInt(), 0xFFFFFFFF.toInt()),
        Theme(0xFFF8EAE0.toInt(), 0xFFFFFCF8.toInt(), 0xFFF1D8C8.toInt(), 0xFF3A2B24.toInt(), 0xFFB9784F.toInt(), 0xFFFFFFFF.toInt()),
        Theme(0xFFE5F1FA.toInt(), 0xFFFFFFFF.toInt(), 0xFFC9E1F2.toInt(), 0xFF203344.toInt(), 0xFF438FC1.toInt(), 0xFFFFFFFF.toInt())
    )
    private const val SEP = "\u0001"
    val availableToolbarTools = listOf(
        "editor", "clipboard", "emoji", "voice", "theme", "onehand",
        "floating", "resize", "settings", "keyboard", "hide"
    )
    private val defaultToolbarTools = availableToolbarTools
    @Volatile private var learnedCache: List<String>? = null
    @Volatile private var shared: SharedPreferences? = null

    fun sp(c: Context): SharedPreferences =
        shared ?: c.applicationContext.getSharedPreferences("craze", Context.MODE_PRIVATE).also { shared = it }
    fun b(c: Context, k: String, d: Boolean) = sp(c).getBoolean(k, d)
    fun setB(c: Context, k: String, v: Boolean) = sp(c).edit().putBoolean(k, v).apply()
    fun i(c: Context, k: String, d: Int) = sp(c).getInt(k, d)
    fun setI(c: Context, k: String, v: Int) = sp(c).edit().putInt(k, v).apply()
    private fun systemTheme(c: Context): Theme {
        val attrs = c.obtainStyledAttributes(intArrayOf(
            android.R.attr.colorBackground,
            android.R.attr.textColorPrimary,
            android.R.attr.colorAccent,
            android.R.attr.colorControlHighlight
        ))
        val bg = attrs.getColor(0, Color.rgb(245, 246, 250))
        val text = attrs.getColor(1, Color.rgb(28, 32, 40))
        val accent = attrs.getColor(2, Color.rgb(80, 100, 210))
        val highlight = attrs.getColor(3, Color.argb(28, 80, 100, 210))
        attrs.recycle()
        val luminance = (0.2126 * Color.red(accent) + 0.7152 * Color.green(accent) +
            0.0722 * Color.blue(accent)) / 255.0
        return Theme(bg, bg, highlight, text, accent,
            if (luminance > 0.55) Color.BLACK else Color.WHITE)
    }

    // Theme index 0 follows Android's current system/dynamic palette (Material You on Android 12+).
    fun theme(c: Context): Theme {
        val index = i(c, "theme", 0).coerceIn(0, themes.size - 1)
        return if (index == 0) systemTheme(c) else themes[index]
    }
    fun nextTheme(c: Context) = setI(c, "theme", (i(c, "theme", 0) + 1) % themes.size)
    fun nextOneHand(c: Context) = setI(c, "onehand", (i(c, "onehand", 0) + 1) % 3)
    fun keyH(c: Context) = i(c, "keyh", 46).coerceIn(34, 64)
    fun keyWidth(c: Context) = i(c, "kbdwidth", 100).coerceIn(55, 100)
    fun sig(c: Context) = "${i(c, "theme", 0)}|${i(c, "onehand", 0)}|${keyH(c)}|${keyWidth(c)}|${b(c, "numrow", true)}"

    private fun list(c: Context, k: String) = (sp(c).getString(k, "") ?: "").split(SEP).filter { it.isNotEmpty() }
    private fun put(c: Context, k: String, l: List<String>) = sp(c).edit().putString(k, l.joinToString(SEP)).apply()
    fun clips(c: Context) = list(c, "clips")
    fun addClip(c: Context, s: String) {
        val safeText = s.take(4_000)
        if (safeText.isNotBlank()) put(c, "clips", (listOf(safeText) + clips(c).filter { it != safeText }).take(20))
    }
    fun removeClip(c: Context, value: String) {
        put(c, "clips", clips(c).filterNot { it == value })
        put(c, "pinnedClips", pinnedClips(c).filterNot { it == value })
    }
    fun clearClips(c: Context) {
        put(c, "clips", emptyList())
        put(c, "pinnedClips", emptyList())
    }
    fun pinnedClips(c: Context): List<String> = list(c, "pinnedClips").filter { it in clips(c) }
    fun togglePinnedClip(c: Context, value: String) {
        val current = pinnedClips(c)
        put(c, "pinnedClips", if (value in current) current - value else (listOf(value) + current).take(20))
    }
    fun toolbarTools(c: Context): List<String> {
        val saved = sp(c).getString("toolbarTools", null) ?: return defaultToolbarTools
        return saved.split(SEP).filter { it in availableToolbarTools }.distinct()
    }
    fun setToolbarTools(c: Context, tools: List<String>) {
        put(c, "toolbarTools", tools.filter { it in availableToolbarTools }.distinct())
    }

    fun recents(c: Context) = list(c, "recent")
    fun addRecent(c: Context, e: String) = put(c, "recent", (listOf(e) + recents(c).filter { it != e }).take(32))

    // Single source of truth for learned words (lowercase, newest first). Words.kt reads this directly,
    // so "Clear learned words" takes effect immediately instead of after a service restart.
    fun learned(c: Context): List<String> {
        learnedCache?.let { return it }
        return synchronized(this) { learnedCache ?: list(c, "learned").also { learnedCache = it } }
    }
    fun isLearned(c: Context, w: String) = learned(c).contains(w.lowercase())
    @Synchronized fun learn(c: Context, w: String) {
        val word = w.trim().lowercase()
        if (word.isEmpty() || word.length > 40 || !word.any { it.isLetter() }) return
        val current = learned(c)
        if (word in current) return
        val updated = (listOf(word) + current).take(500)
        put(c, "learned", updated)
        learnedCache = updated
    }
    @Synchronized fun clearLearned(c: Context) {
        put(c, "learned", emptyList())
        learnedCache = emptyList()
    }
}
