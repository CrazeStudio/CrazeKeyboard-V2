package com.crazestudio.crazekeyboard

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.os.Vibrator
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class CrazeInputMethodService : InputMethodService() {
    private var root: LinearLayout? = null
    private var keysContainer: LinearLayout? = null
    private var suggestions: LinearLayout? = null
    private var shifted = false
    private var symbols = false
    private var bengali = false
    private var capsLock = false

    private val prefs by lazy { getSharedPreferences("craze_keyboard", Context.MODE_PRIVATE) }
    private val themes = mapOf(
        "Midnight Glow" to intArrayOf(Color.rgb(16,17,24), Color.rgb(39,40,54), Color.rgb(167,139,250)),
        "Arctic Glass" to intArrayOf(Color.rgb(13,24,36), Color.rgb(37,57,73), Color.rgb(103,210,255)),
        "Berry Pop" to intArrayOf(Color.rgb(35,17,34), Color.rgb(70,35,65), Color.rgb(255,119,190)),
        "Forest Calm" to intArrayOf(Color.rgb(14,29,24), Color.rgb(35,57,47), Color.rgb(120,215,155))
    )

    override fun onCreateInputView(): View {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(5), dp(7), dp(5), dp(7))
        }
        rebuild()
        return root!!
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        updateSuggestions()
    }

    private fun rebuild() {
        val host = root ?: return
        host.removeAllViews()
        val palette = palette()
        host.setBackgroundColor(palette[0])
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        listOf("☷", "GIF", "🎨", "🌐", "⚙").forEachIndexed { index, label ->
            toolbar.addView(toolButton(label, palette, index), LinearLayout.LayoutParams(0, dp(40), 1f))
        }
        host.addView(toolbar)
        suggestions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        host.addView(suggestions, LinearLayout.LayoutParams(-1, dp(36)))
        keysContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        host.addView(keysContainer, LinearLayout.LayoutParams(-1, -2))
        buildKeys(palette)
        updateSuggestions()
    }

    private fun toolButton(label: String, palette: IntArray, action: Int) = keyButton(label, palette, true).apply {
        setOnClickListener {
            when (action) {
                0 -> { symbols = !symbols; rebuild() }
                1 -> insert("GIF")
                2 -> {
                    val names = themes.keys.toList()
                    val current = prefs.getString("theme", names.first()) ?: names.first()
                    val next = names[(names.indexOf(current) + 1) % names.size]
                    prefs.edit().putString("theme", next).apply()
                    rebuild()
                }
                3 -> { bengali = !bengali; shifted = false; symbols = false; rebuild() }
                4 -> {
                    val intent = packageManager.getLaunchIntentForPackage(packageName)
                    intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    if (intent != null) startActivity(intent)
                }
            }
        }
    }

    private fun buildKeys(palette: IntArray) {
        val box = keysContainer ?: return
        box.removeAllViews()
        val rows = if (symbols) {
            listOf(listOf("1","2","3","4","5","6","7","8","9","0"),
                listOf("@","#","$","%","&","-","+","(",")","/"),
                listOf("= ","*","\"","'",":",";","!","?","⌫"),
                listOf("ABC",",","☻","🌐","space",".","↵"))
        } else if (bengali) {
            listOf(listOf("অ","আ","ই","ঈ","উ","ঊ","এ","ঐ","ও","ঔ"),
                listOf("ক","খ","গ","ঘ","চ","ছ","জ","ঝ","ট"),
                listOf("⇧","ত","থ","দ","ধ","ন","প","ব","⌫"),
                listOf("?123",",","☻","English","space",".","↵"))
        } else {
            listOf(listOf("q","w","e","r","t","y","u","i","o","p"),
                listOf("a","s","d","f","g","h","j","k","l"),
                listOf("⇧","z","x","c","v","b","n","m","⌫"),
                listOf("?123",",","☻","🌐","space",".","↵"))
        }
        rows.forEachIndexed { rowIndex, rowKeys ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
            rowKeys.forEach { key ->
                val button = keyButton(displayKey(key), palette, key in listOf("⇧","⌫","?123","↵","ABC","🌐"))
                val weight = when (key) {
                    "space" -> 3.6f
                    "⇧", "⌫", "?123", "↵", "ABC", "🌐" -> 1.35f
                    "English" -> 1.7f
                    else -> 1f
                }
                button.setOnClickListener { handleKey(key) }
                row.addView(button, LinearLayout.LayoutParams(0, dp(if (rowIndex == 3) 45 else 47), weight).apply {
                    setMargins(dp(2), dp(3), dp(2), dp(3))
                })
            }
            box.addView(row)
        }
    }

    private fun displayKey(key: String): String = when (key) {
        "space" -> if (bengali) "বাংলা" else "English"
        else -> if (!bengali && !symbols && shifted && key.length == 1 && key[0].isLetter()) key.uppercase() else key
    }

    private fun handleKey(key: String) {
        val connection = currentInputConnection ?: return
        if (prefs.getBoolean("haptics", true)) {
            @Suppress("DEPRECATION")
            (getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)?.let { if (it.hasVibrator()) it.vibrate(8) }
        }
        if (prefs.getBoolean("sound", false)) {
            val audio = getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
            audio?.playSoundEffect(android.media.AudioManager.FX_KEY_CLICK)
        }
        when (key) {
            "⌫" -> {
                val selected = connection.getSelectedText(0)
                if (selected != null) connection.commitText("", 1) else connection.deleteSurroundingText(1, 0)
            }
            "↵" -> {
                val action = currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
                if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) connection.performEditorAction(action)
                else connection.commitText("\n", 1)
            }
            "⇧" -> { if (shifted) capsLock = !capsLock; shifted = !shifted; rebuild() }
            "?123" -> { symbols = true; rebuild() }
            "ABC" -> { symbols = false; rebuild() }
            "🌐", "English" -> { bengali = !bengali; symbols = false; rebuild() }
            "space" -> connection.commitText(" ", 1)
            "☻" -> insert("😊")
            "," -> connection.commitText(",", 1)
            "." -> connection.commitText(".", 1)
            else -> {
                val text = if (!bengali && shifted && key.length == 1) key.uppercase() else key
                connection.commitText(text, 1)
                if (shifted && !capsLock) { shifted = false; rebuild() }
            }
        }
    }

    private fun insert(value: String) { currentInputConnection?.commitText(value, 1) }

    private fun updateSuggestions() {
        val bar = suggestions ?: return
        bar.removeAllViews()
        if (!prefs.getBoolean("suggestions", true)) {
            bar.visibility = View.GONE
            return
        }
        bar.visibility = View.VISIBLE
        val before = currentInputConnection?.getTextBeforeCursor(40, 0)?.toString().orEmpty()
        val token = before.substringAfterLast(' ').substringAfterLast('\n')
        val options = listOf(token, token.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }, "😊")
            .distinct().filter { it.isNotBlank() }.ifEmpty { listOf("CrazeKeyboard", "😊") }
        val palette = palette()
        options.take(3).forEach { word ->
            val item = TextView(this).apply {
                text = word
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                setOnClickListener { currentInputConnection?.commitText(word, 1) }
            }
            bar.addView(item, LinearLayout.LayoutParams(0, dp(34), 1f))
        }
    }

    private fun keyButton(label: String, palette: IntArray, special: Boolean): Button = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = if (label.length > 4) 11f else 17f
        typeface = Typeface.DEFAULT
        setTextColor(Color.WHITE)
        stateListAnimator = null
        background = GradientDrawable().apply {
            cornerRadius = dp(10).toFloat()
            setColor(if (special) palette[2].let { blend(it, palette[1]) } else palette[1])
        }
        setPadding(0, 0, 0, 0)
        minWidth = 0
        minimumWidth = 0
        minHeight = 0
        minimumHeight = 0
    }

    private fun palette(): IntArray {
        val name = prefs.getString("theme", "Midnight Glow") ?: "Midnight Glow"
        return themes[name] ?: themes.getValue("Midnight Glow")
    }

    private fun blend(fg: Int, bg: Int): Int = Color.rgb(
        (Color.red(fg) * .45 + Color.red(bg) * .55).toInt(),
        (Color.green(fg) * .45 + Color.green(bg) * .55).toInt(),
        (Color.blue(fg) * .45 + Color.blue(bg) * .55).toInt()
    )

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
