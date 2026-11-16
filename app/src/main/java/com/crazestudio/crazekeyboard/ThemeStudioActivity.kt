package com.crazestudio.crazekeyboard

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
import android.widget.Toast

/** Local theme editor. Custom palettes are stored only in the app's private preferences. */
class ThemeStudioActivity : Activity() {
    private val fields = LinkedHashMap<String, EditText>()
    private val swatches = LinkedHashMap<String, View>()
    private val colors = listOf(
        "Background" to "bg",
        "Key surface" to "key",
        "Special key" to "special",
        "Text" to "text",
        "Accent" to "accent",
        "Accent text" to "accentText"
    )
    private lateinit var nameInput: EditText
    private lateinit var preview: LinearLayout
    private lateinit var previewTitle: TextView
    private var uiPage = Color.rgb(18, 19, 26)
    private var uiCard = Color.rgb(30, 32, 42)
    private var uiInk = Color.rgb(238, 240, 247)
    private var uiMuted = Color.rgb(154, 161, 178)
    private var uiAccent = Color.rgb(142, 127, 255)
    private var loading = false

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun shape(color: Int, radius: Int = 16, stroke: Int = 0) =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radius).toFloat()
            setColor(color)
            if (stroke != 0) setStroke(dp(1), stroke)
        }

    private fun label(value: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }

    private fun button(value: String, primary: Boolean = false, click: () -> Unit) =
        TextView(this).apply {
            text = value
            textSize = 13f
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(if (primary) Color.WHITE else uiInk)
            background = shape(if (primary) uiAccent else uiCard, 13)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            minHeight = dp(44)
            isClickable = true
            isFocusable = true
            setOnClickListener { click() }
        }

    private fun parseColor(value: String, fallback: Int): Int {
        val raw = value.trim().removePrefix("#")
        return try {
            when (raw.length) {
                6 -> Color.parseColor("#FF" + raw)
                8 -> Color.parseColor("#" + raw)
                else -> fallback
            }
        } catch (_: Exception) { fallback }
    }

    private fun colorText(value: Int): String =
        "#" + Integer.toHexString(value).takeLast(6).uppercase()

    private fun readTheme(): Theme {
        fun color(key: String, fallback: Int) = parseColor(fields[key]?.text?.toString().orEmpty(), fallback)
        val base = Prefs.theme(this)
        return Theme(
            color("bg", base.bg),
            color("key", base.key),
            color("special", base.special),
            color("text", base.text),
            color("accent", base.accent),
            color("accentText", base.accentText)
        )
    }

    private fun setThemeFields(theme: Theme, name: String) {
        loading = true
        nameInput.setText(name)
        val values = listOf(theme.bg, theme.key, theme.special, theme.text, theme.accent, theme.accentText)
        colors.forEachIndexed { index, item -> fields[item.second]?.setText(colorText(values[index])) }
        loading = false
        updatePreview()
    }

    private fun updatePreview() {
        if (!::preview.isInitialized) return
        val th = readTheme()
        preview.background = shape(th.bg, 20)
        previewTitle.setTextColor(th.text)
        val accentView = preview.findViewWithTag<View>("preview_accent")
        accentView?.background = shape(th.accent, 8)
        val textView = preview.findViewWithTag<TextView>("preview_sample_text")
        textView?.setTextColor(th.text)
        for (i in 0 until preview.childCount) {
            val child = preview.getChildAt(i)
            if (child.tag == "preview_key_row" && child is LinearLayout) {
                for (j in 0 until child.childCount) {
                    child.getChildAt(j).background = shape(th.key, 8)
                    (child.getChildAt(j) as? TextView)?.setTextColor(th.text)
                }
            }
        }
        swatches.forEach { (key, view) ->
            val value = fields[key]?.text?.toString().orEmpty()
            view.background = shape(parseColor(value, th.key), 12, uiMuted)
        }
    }

    private fun section(title: String, subtitle: String? = null) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = shape(uiCard, 18)
        setPadding(dp(15), dp(14), dp(15), dp(14))
        addView(label(title, 15f, uiInk, true))
        if (subtitle != null) addView(label(subtitle, 11f, uiMuted).apply {
            setPadding(0, dp(4), 0, dp(10))
        })
    }

    private fun chooseColor(title: String, key: String) {
        val palette = listOf(
            "Violet" to Color.rgb(145, 104, 255),
            "Blue" to Color.rgb(68, 139, 238),
            "Teal" to Color.rgb(35, 166, 153),
            "Rose" to Color.rgb(230, 91, 145),
            "Orange" to Color.rgb(238, 135, 65),
            "Green" to Color.rgb(75, 160, 102),
            "Gold" to Color.rgb(207, 162, 61),
            "White" to Color.WHITE,
            "Charcoal" to Color.rgb(38, 40, 49)
        )
        AlertDialog.Builder(this)
            .setTitle("Choose " + title.lowercase())
            .setItems(palette.map { it.first }.toTypedArray()) { _, which ->
                fields[key]?.setText(colorText(palette[which].second))
                updatePreview()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun addColorField(container: LinearLayout, labelText: String, key: String, defaultColor: Int) {
        val row = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, dp(6))
        }
        val swatch = View(this).apply {
            background = shape(defaultColor, 12, uiMuted)
            isClickable = true
            setOnClickListener { chooseColor(labelText, key) }
            contentDescription = "Choose " + labelText.lowercase() + " colour"
        }
        row.addView(swatch, LinearLayout.LayoutParams(dp(38), dp(38)).apply { rightMargin = dp(11) })
        swatches[key] = swatch
        row.addView(label(labelText, 12.5f, uiInk, true), LinearLayout.LayoutParams(0, -2, 1f))
        val input = EditText(this).apply {
            setText(colorText(defaultColor))
            textSize = 12f
            setTextColor(uiInk)
            setHintTextColor(uiMuted)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            isSingleLine = true
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(7), dp(8), dp(7))
            background = shape(uiPage, 10, Color.argb(70, Color.red(uiInk), Color.green(uiInk), Color.blue(uiInk)))
            contentDescription = labelText + " hex colour"
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (!loading) updatePreview()
                }
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        fields[key] = input
        row.addView(input, LinearLayout.LayoutParams(dp(104), dp(42)))
        container.addView(row)
    }

    private fun renderSavedThemes(list: LinearLayout) {
        list.removeAllViews()
        val saved = Prefs.customThemes(this)
        if (saved.isEmpty()) {
            list.addView(label("No saved themes yet. Your first theme will appear here.", 12f, uiMuted).apply {
                setPadding(0, dp(8), 0, dp(4))
            })
            return
        }
        saved.forEach { entry ->
            val row = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                background = shape(uiPage, 13)
                setPadding(dp(10), dp(8), dp(8), dp(8))
            }
            val previewBar = View(this).apply { background = shape(entry.theme.accent, 8) }
            row.addView(previewBar, LinearLayout.LayoutParams(dp(9), dp(32)).apply { rightMargin = dp(10) })
            row.addView(label(entry.name, 13f, uiInk, true), LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(button("Load") {
                Prefs.selectCustomTheme(this@ThemeStudioActivity, entry.name)
                setThemeFields(entry.theme, entry.name)
                Toast.makeText(this@ThemeStudioActivity, "Loaded " + entry.name, Toast.LENGTH_SHORT).show()
            })
            row.addView(button("×") {
                AlertDialog.Builder(this@ThemeStudioActivity)
                    .setTitle("Delete " + entry.name + "?")
                    .setMessage("This removes the saved theme from this device.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Delete") { _, _ ->
                        Prefs.deleteCustomTheme(this@ThemeStudioActivity, entry.name)
                        renderSavedThemes(list)
                    }
                    .show()
            }.apply { setTextColor(uiMuted) }, LinearLayout.LayoutParams(dp(42), dp(44)).apply { leftMargin = dp(5) })
            list.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(7) })
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val active = Prefs.theme(this)
        uiPage = active.bg
        uiCard = active.key
        uiInk = active.text
        uiAccent = active.accent
        uiMuted = Color.argb(175, Color.red(uiInk), Color.green(uiInk), Color.blue(uiInk))
        window.statusBarColor = uiPage
        window.navigationBarColor = uiPage
        val luminance = (0.2126 * Color.red(uiPage) + 0.7152 * Color.green(uiPage) +
            0.0722 * Color.blue(uiPage)) / 255.0
        window.decorView.systemUiVisibility = if (luminance < 0.34) 0 else
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        val shell = FrameLayout(this).apply { setBackgroundColor(uiPage); fitsSystemWindows = true }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(12))
        }
        shell.addView(root, FrameLayout.LayoutParams(minOf(resources.displayMetrics.widthPixels, dp(620)), -1, Gravity.CENTER))

        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = shape(uiCard, 16)
            setPadding(dp(3), dp(3), dp(12), dp(3))
        }
        header.addView(button("‹") { finish() }, LinearLayout.LayoutParams(dp(44), dp(44)))
        header.addView(label("Theme Studio", 19f, uiInk, true), LinearLayout.LayoutParams(0, -2, 1f).apply {
            leftMargin = dp(9)
        })
        header.addView(label("LOCAL", 9f, uiMuted, true))
        root.addView(header, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) })

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(2), dp(2), dp(2), dp(14))
        }
        val scroll = ScrollView(this).apply { clipToPadding = false; overScrollMode = View.OVER_SCROLL_NEVER }
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val previewSection = section("Live preview", "Changes appear here before you save or apply them.")
        preview = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(13), dp(13), dp(13), dp(13))
            gravity = Gravity.CENTER
        }
        previewTitle = label("CrazeKeyboard", 16f, active.text, true)
        preview.addView(previewTitle)
        preview.addView(label("Quick brown fox · 123", 11f, active.text).apply {
            tag = "preview_sample_text"
            setPadding(0, dp(4), 0, dp(9))
        })
        val keyRow = LinearLayout(this).apply { gravity = Gravity.CENTER; tag = "preview_key_row" }
        listOf("Q", "W", "E", "R", "T", "Y", "U", "I").forEach { character ->
            keyRow.addView(label(character, 11f, active.text, true).apply {
                gravity = Gravity.CENTER
                background = shape(active.key, 8)
            }, LinearLayout.LayoutParams(0, dp(32), 1f).apply {
                setMargins(dp(2), 0, dp(2), 0)
            })
        }
        preview.addView(keyRow, LinearLayout.LayoutParams(-1, -2))
        val accentBar = View(this).apply { tag = "preview_accent"; background = shape(active.accent, 8) }
        preview.addView(accentBar, LinearLayout.LayoutParams(dp(110), dp(7)).apply { topMargin = dp(10) })
        previewSection.addView(preview, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        body.addView(previewSection, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })

        val identity = section("Theme identity", "Give your palette a name so you can find it later.")
        nameInput = EditText(this).apply {
            hint = "My purple theme"
            setSingleLine(true)
            textSize = 14f
            setTextColor(uiInk)
            setHintTextColor(uiMuted)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setPadding(dp(12), dp(11), dp(12), dp(11))
            background = shape(uiPage, 12)
        }
        identity.addView(nameInput, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(10) })
        body.addView(identity, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })

        val colours = section("Colour controls", "Enter #RRGGBB values or tap a swatch to choose a colour.")
        addColorField(colours, "Background", "bg", active.bg)
        addColorField(colours, "Key surface", "key", active.key)
        addColorField(colours, "Special key", "special", active.special)
        addColorField(colours, "Text", "text", active.text)
        addColorField(colours, "Accent", "accent", active.accent)
        addColorField(colours, "Accent text", "accentText", active.accentText)
        body.addView(colours, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })

        val saved = section("Saved themes", "Load or remove custom themes stored on this device.")
        val savedList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        saved.addView(savedList, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(5) })
        body.addView(saved, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        renderSavedThemes(savedList)

        val footer = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        footer.addView(button("Reset fields") {
            val current = Prefs.theme(this@ThemeStudioActivity)
            setThemeFields(current, Prefs.activeCustomTheme(this@ThemeStudioActivity).ifBlank { "My Theme" })
        }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { rightMargin = dp(5) })
        footer.addView(button("Save & Apply", true) {
            val themeName = nameInput.text.toString().trim().ifBlank { "My Theme" }
            val th = readTheme()
            Prefs.saveCustomTheme(this@ThemeStudioActivity, themeName, th, activate = true)
            Toast.makeText(this@ThemeStudioActivity, "Theme saved and applied", Toast.LENGTH_SHORT).show()
            finish()
        }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { leftMargin = dp(5) })
        body.addView(footer, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })

        setContentView(shell)
        val activeName = Prefs.activeCustomTheme(this)
        setThemeFields(active, activeName.ifBlank { "My Theme" })
        updatePreview()
    }
}
