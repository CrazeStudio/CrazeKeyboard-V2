package com.crazestudio.crazekeyboard

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Full-screen clipboard manager styled to match the CrazeKeyboard gallery reference. */
class ClipboardActivity : Activity() {
    companion object { @Volatile var onPaste: ((String) -> Unit)? = null }
    private val ink = Color.rgb(29, 34, 43)
    private val muted = Color.rgb(104, 111, 124)
    private val page = Color.rgb(247, 248, 250)
    private val card = Color.WHITE
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun rounded(color: Int, radius: Int = 18) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(radius).toFloat()
        setColor(color)
        setStroke(dp(1), Color.rgb(229, 232, 236))
    }
    private fun label(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private fun button(value: String, click: () -> Unit) = TextView(this).apply {
        text = value
        textSize = 20f
        gravity = Gravity.CENTER
        setTextColor(ink)
        minWidth = dp(42)
        minHeight = dp(42)
        setOnClickListener { click() }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = page
        window.navigationBarColor = page
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(12))
            setBackgroundColor(page)
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(button("‹") { finish() })
        header.addView(label("Clipboard", 21f, ink, true), LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(12) })
        header.addView(button("📌") { showPinnedOnly = !showPinnedOnly; render() })
        header.addView(button("⋮") { clearHistory() })
        root.addView(header, LinearLayout.LayoutParams(-1, dp(54)))
        val banner = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = rounded(Color.rgb(232, 239, 233), 24)
        }
        banner.addView(label("▢", 25f, Color.rgb(58, 102, 70), true))
        val bannerText = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, 0, 0) }
        bannerText.addView(label("Clipboard", 17f, ink, true))
        bannerText.addView(label("Copy text and images to keep them here", 12f, muted))
        banner.addView(bannerText)
        root.addView(banner, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12); bottomMargin = dp(18) })
        val scroll = ScrollView(this).apply { clipToPadding = false }
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        contentList = list
        render()
    }
    private lateinit var contentList: LinearLayout
    private var showPinnedOnly = false
    private fun sectionTitle(value: String) {
        contentList.addView(label(value, 13f, ink, true).apply { setPadding(dp(8), dp(9), 0, dp(7)) })
    }
    private fun item(value: String, pinned: Boolean) {
        val row = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(7), dp(8), dp(7))
            background = rounded(card, 15)
        }
        val preview = label(value, 15f, ink).apply {
            maxLines = 3
            setPadding(0, dp(4), dp(8), dp(4))
            setOnClickListener { onPaste?.invoke(value); finish() }
        }
        row.addView(preview, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(button(if (pinned) "📌" else "＋") { Prefs.togglePinnedClip(this, value); render() })
        row.addView(button("⧉") { onPaste?.invoke(value); finish() })
        contentList.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(7) })
    }
    private fun render() {
        if (!::contentList.isInitialized) return
        contentList.removeAllViews()
        val clips = Prefs.clips(this)
        val pinned = Prefs.pinnedClips(this)
        if (!showPinnedOnly) {
            sectionTitle("PINNED")
            if (pinned.isEmpty()) contentList.addView(label("Pinned text will appear here", 13f, muted).apply { setPadding(dp(10), dp(8), 0, dp(12)) })
            pinned.forEach { item(it, true) }
        }
        sectionTitle(if (showPinnedOnly) "PINNED ITEMS" else "RECENT")
        val recent = if (showPinnedOnly) pinned else clips.filterNot { it in pinned }
        if (recent.isEmpty()) contentList.addView(label("Copied text will appear here", 14f, muted).apply { setPadding(dp(10), dp(10), 0, dp(12)) })
        recent.forEach { item(it, it in pinned) }
    }
    private fun clearHistory() {
        android.app.AlertDialog.Builder(this)
            .setTitle("Clear clipboard history?")
            .setMessage("This removes saved recent and pinned text from this device.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Clear") { _, _ -> Prefs.clearClips(this); render() }
            .show()
    }
}