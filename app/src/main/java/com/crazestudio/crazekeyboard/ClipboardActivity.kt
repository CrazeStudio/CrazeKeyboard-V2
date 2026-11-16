package com.crazestudio.crazekeyboard

import android.app.Activity
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Full-screen privacy-first clipboard manager with dark/light mode, search, and SVG controls. */
class ClipboardActivity : Activity() {
    companion object { @Volatile var onPaste: ((String) -> Unit)? = null }

    private var night = false
    private var ink = 0
    private var muted = 0
    private var page = 0
    private var card = 0
    private var fieldBg = 0
    private var stroke = 0
    private var accent = 0
    private var bannerBg = 0

    private lateinit var contentList: LinearLayout
    private var showPinnedOnly = false
    private var searchQuery = ""
    private var pinFilterBtn: ImageView? = null

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun initPalette() {
        night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        if (night) {
            ink = Color.rgb(238, 240, 247)
            muted = Color.rgb(154, 161, 178)
            page = Color.rgb(18, 19, 26)
            card = Color.rgb(30, 32, 42)
            fieldBg = Color.rgb(25, 27, 36)
            stroke = Color.rgb(48, 52, 66)
            accent = Color.rgb(142, 127, 255)
            bannerBg = Color.rgb(34, 32, 54)
        } else {
            ink = Color.rgb(29, 34, 43)
            muted = Color.rgb(104, 111, 124)
            page = Color.rgb(245, 246, 250)
            card = Color.WHITE
            fieldBg = Color.WHITE
            stroke = Color.rgb(229, 232, 240)
            accent = Color.rgb(102, 83, 215)
            bannerBg = Color.rgb(235, 232, 252)
        }
    }

    private fun rounded(color: Int, radius: Int = 18, border: Int = stroke) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(radius).toFloat()
        setColor(color)
        if (border != 0) setStroke(dp(1), border)
    }

    private fun ripple(view: View, radius: Int) {
        view.foreground = RippleDrawable(ColorStateList.valueOf(Color.argb(44, 128, 128, 128)), null, rounded(Color.WHITE, radius, 0))
    }

    private fun label(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    private fun iconButton(res: Int, tint: Int, description: String, click: () -> Unit) = ImageView(this).apply {
        setImageResource(res)
        setColorFilter(tint)
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        setPadding(dp(11), dp(11), dp(11), dp(11))
        minimumWidth = dp(48)
        minimumHeight = dp(48)
        contentDescription = description
        isClickable = true
        isFocusable = true
        ripple(this, 12)
        setOnClickListener { click() }
    }

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initPalette()
        window.statusBarColor = page
        window.navigationBarColor = page
        window.decorView.systemUiVisibility =
            if (night) 0 else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        val shell = FrameLayout(this).apply {
            setBackgroundColor(page)
            fitsSystemWindows = true
        }
        val maxCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(12))
        }
        val maxWidth = minOf(resources.displayMetrics.widthPixels, dp(600))
        shell.addView(maxCol, FrameLayout.LayoutParams(maxWidth, -1, Gravity.CENTER_HORIZONTAL))

        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(iconButton(R.drawable.ic_arrow_left, ink, "Back") { finish() }, LinearLayout.LayoutParams(dp(48), dp(48)))
        header.addView(label("Clipboard", 21f, ink, true), LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(8) })
        val pinBtn = iconButton(R.drawable.ic_pin, muted, "Show pinned only") {
            showPinnedOnly = !showPinnedOnly
            pinFilterBtn?.setColorFilter(if (showPinnedOnly) accent else muted)
            render()
        }
        pinFilterBtn = pinBtn
        header.addView(pinBtn, LinearLayout.LayoutParams(dp(48), dp(48)))
        header.addView(iconButton(R.drawable.ic_trash, muted, "Clear clipboard history") { clearHistory() }, LinearLayout.LayoutParams(dp(48), dp(48)))
        maxCol.addView(header, LinearLayout.LayoutParams(-1, dp(52)))

        val banner = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = rounded(bannerBg, 20, 0)
        }
        banner.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_clip)
            setColorFilter(accent)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }, LinearLayout.LayoutParams(dp(26), dp(26)))
        val bannerText = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, 0, 0) }
        bannerText.addView(label("Local Clipboard Vault", 15.5f, ink, true))
        bannerText.addView(label("Snippets stay on this device. Password fields are excluded.", 12f, muted))
        banner.addView(bannerText)
        maxCol.addView(banner, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8); bottomMargin = dp(12) })

        val searchBox = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(fieldBg, 14)
            setPadding(dp(12), dp(2), dp(12), dp(2))
        }
        searchBox.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_search)
            setColorFilter(muted)
        }, LinearLayout.LayoutParams(dp(20), dp(20)).apply { rightMargin = dp(8) })
        searchBox.addView(EditText(this).apply {
            hint = "Filter clipboard items…"
            textSize = 14f
            isSingleLine = true
            background = null
            setTextColor(ink)
            setHintTextColor(muted)
            setPadding(0, dp(10), 0, dp(10))
            inputType = InputType.TYPE_CLASS_TEXT
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    searchQuery = s?.toString()?.trim()?.lowercase().orEmpty()
                    render()
                }
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }, LinearLayout.LayoutParams(0, -2, 1f))
        maxCol.addView(searchBox, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })

        val scroll = ScrollView(this).apply { clipToPadding = false; overScrollMode = View.OVER_SCROLL_NEVER }
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(list)
        maxCol.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(shell)
        contentList = list
        render()
    }

    private fun sectionTitle(value: String) {
        contentList.addView(label(value, 11f, muted, true).apply {
            letterSpacing = 0.08f
            setPadding(dp(6), dp(8), 0, dp(6))
        })
    }

    private fun item(value: String, pinned: Boolean) {
        val row = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(8), dp(6), dp(8))
            background = rounded(card, 16, if (pinned) accent else stroke)
        }
        val preview = label(value, 14.5f, ink).apply {
            maxLines = 3
            setPadding(0, dp(4), dp(8), dp(4))
            isClickable = true
            ripple(this, 10)
            setOnClickListener { onPaste?.invoke(value); finish() }
        }
        row.addView(preview, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(iconButton(R.drawable.ic_pin, if (pinned) accent else muted, if (pinned) "Unpin" else "Pin") {
            Prefs.togglePinnedClip(this, value)
            render()
        }, LinearLayout.LayoutParams(dp(44), dp(44)))
        row.addView(iconButton(R.drawable.ic_editor_copy, ink, "Paste item") {
            onPaste?.invoke(value)
            finish()
        }, LinearLayout.LayoutParams(dp(44), dp(44)))
        row.addView(iconButton(R.drawable.ic_close, muted, "Delete item") {
            Prefs.removeClip(this, value)
            render()
        }, LinearLayout.LayoutParams(dp(44), dp(44)))
        contentList.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
    }

    private fun render() {
        if (!::contentList.isInitialized) return
        contentList.removeAllViews()
        val clips = Prefs.clips(this).filter { searchQuery.isEmpty() || it.lowercase().contains(searchQuery) }
        val pinned = Prefs.pinnedClips(this).filter { searchQuery.isEmpty() || it.lowercase().contains(searchQuery) }

        if (!showPinnedOnly) {
            sectionTitle("PINNED")
            if (pinned.isEmpty()) contentList.addView(label("Pinned snippets appear here", 13f, muted).apply { setPadding(dp(8), dp(6), 0, dp(10)) })
            pinned.forEach { item(it, true) }
        }
        sectionTitle(if (showPinnedOnly) "PINNED ITEMS" else "RECENT")
        val recent = if (showPinnedOnly) pinned else clips.filterNot { it in pinned }
        if (recent.isEmpty()) {
            contentList.addView(label(if (searchQuery.isNotEmpty()) "No matching snippets" else "Copied text will appear here", 13.5f, muted).apply {
                setPadding(dp(8), dp(10), 0, dp(12))
            })
        }
        recent.forEach { item(it, it in pinned) }
    }

    private fun clearHistory() {
        AlertDialog.Builder(this)
            .setTitle("Clear clipboard history?")
            .setMessage("This removes saved recent and pinned text from this device.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Clear") { _, _ -> Prefs.clearClips(this); render() }
            .show()
    }
}