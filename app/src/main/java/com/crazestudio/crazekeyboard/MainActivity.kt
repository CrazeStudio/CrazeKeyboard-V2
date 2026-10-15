package com.crazestudio.crazekeyboard

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("craze_keyboard", Context.MODE_PRIVATE) }
    private val bg = Color.rgb(16, 17, 24)
    private val surface = Color.rgb(29, 30, 42)
    private val accent = Color.rgb(167, 139, 250)
    private val text = Color.rgb(246, 245, 252)
    private val muted = Color.rgb(174, 174, 194)
    private lateinit var content: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showHome()
    }

    private fun base(titleText: String, subtitleText: String): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(24))
            setBackgroundColor(bg)
        }
        root.addView(label("CRAZE STUDIO  •  KEYBOARD", 11f, accent, true))
        root.addView(label(titleText, 29f, text, true).apply { setPadding(0, dp(10), 0, dp(3)) })
        root.addView(label(subtitleText, 14f, muted, false).apply { setPadding(0, 0, 0, dp(18)) })
        val scroll = ScrollView(this)
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(12), 0, 0) }
        listOf("Home", "Themes", "Typing", "Privacy").forEach { name ->
            nav.addView(Button(this).apply {
                text = name
                isAllCaps = false
                textSize = 11f
                setTextColor(this@MainActivity.text)
                background = rounded(surface, 18)
                setOnClickListener { when(name) { "Home" -> showHome(); "Themes" -> showThemes(); "Typing" -> showTyping(); else -> showPrivacy() } }
            }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
        }
        root.addView(nav)
        return root
    }

    private fun showHome() {
        val root = base("CrazeKeyboard", "Your keyboard, your style. Built for comfortable everyday typing.")
        content.addView(card {
            addView(label("READY WHEN YOU ARE", 11f, accent, true))
            addView(label("A calmer typing experience", 21f, text, true).apply { setPadding(0, dp(8), 0, dp(5)) })
            addView(label("Choose a theme, configure typing, then enable CrazeKeyboard in Android settings.", 14f, muted, false))
        })
        content.addView(sectionTitle("SETUP"))
        content.addView(action("1   Enable keyboard", "Open Android's keyboard settings") {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        })
        content.addView(action("2   Select CrazeKeyboard", "Open the system keyboard picker") {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        })
        content.addView(sectionTitle("LIVE PREVIEW"))
        content.addView(keyboardPreview())
        content.addView(sectionTitle("YOUR PREFERENCES"))
        content.addView(action("Current theme", prefs.getString("theme", "Midnight Glow") ?: "Midnight Glow") { showThemes() })
        content.addView(action("Typing settings", "Suggestions, autocorrection, haptics and sound") { showTyping() })
        setContentView(root)
    }

    private fun showThemes() {
        val root = base("Theme gallery", "Four colorways designed for the CrazeKeyboard key surface.")
        val themeColors = listOf(
            "Midnight Glow" to intArrayOf(Color.rgb(16,17,24), Color.rgb(39,40,54), Color.rgb(167,139,250)),
            "Arctic Glass" to intArrayOf(Color.rgb(13,24,36), Color.rgb(37,57,73), Color.rgb(103,210,255)),
            "Berry Pop" to intArrayOf(Color.rgb(35,17,34), Color.rgb(70,35,65), Color.rgb(255,119,190)),
            "Forest Calm" to intArrayOf(Color.rgb(14,29,24), Color.rgb(35,57,47), Color.rgb(120,215,155))
        )
        themeColors.forEach { (name, colors) ->
            content.addView(card {
                addView(LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    for (c in colors) addView(View(this@MainActivity).apply { background = rounded(c, 18) },
                        LinearLayout.LayoutParams(0, dp(44), 1f).apply { setMargins(dp(3), 0, dp(3), 0) })
                })
                addView(label(name, 18f, text, true).apply { setPadding(0, dp(10), 0, dp(3)) })
                addView(label(if ((prefs.getString("theme", "Midnight Glow") ?: "Midnight Glow") == name) "Currently selected" else "Tap to apply this theme", 13f, muted, false))
                addView(button(if ((prefs.getString("theme", "Midnight Glow") ?: "Midnight Glow") == name) "Selected ✓" else "Apply theme") {
                    prefs.edit().putString("theme", name).apply()
                    showThemes()
                })
            })
        }
        setContentView(root)
    }

    private fun showTyping() {
        val root = base("Typing settings", "Tune the keyboard feel. Preferences are stored on this device.")
        toggle("Suggestions", "Show a small suggestion strip above the keys", "suggestions", true)
        toggle("Autocorrection", "Preference saved; correction engine is not bundled yet", "autocorrect", true)
        toggle("Swipe typing", "Preference saved; gesture decoding is not bundled yet", "swipe", false)
        toggle("Haptic feedback", "Vibrate lightly when a key is pressed", "haptics", true)
        toggle("Key sounds", "Play the system key-click sound", "sound", false)
        content.addView(sectionTitle("LANGUAGES"))
        content.addView(action("English (US)", "QWERTY layout") { showHome() })
        content.addView(action("বাংলা (Bengali)", "Basic Bengali character layout available from the 🌐 key") { showHome() })
        setContentView(root)
    }

    private fun showPrivacy() {
        val root = base("Privacy", "Designed to keep configuration local and simple.")
        content.addView(card {
            addView(label("ON-DEVICE SETTINGS", 12f, accent, true))
            addView(label("CrazeKeyboard does not request network access in this build. Theme and typing preferences are stored locally on your device.", 15f, text, false).apply { setPadding(0, dp(9), 0, 0) })
        })
        content.addView(sectionTitle("IMPORTANT"))
        content.addView(label("This starter version does not send typed text to a server. Android may show a warning when you enable any third-party keyboard; only enable keyboards you trust.", 14f, muted, false).apply { setPadding(0, 0, 0, dp(16)) })
        content.addView(action("Open keyboard settings", "Manage enabled input methods") { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) })
        setContentView(root)
    }

    private fun keyboardPreview(): View = card {
        setBackgroundColor(Color.rgb(27, 28, 39))
        addView(label("☷       GIF       🎨       🌐       ⚙", 16f, muted, true))
        listOf("q  w  e  r  t  y  u  i  o  p", " a  s  d  f  g  h  j  k  l", "⇧  z  x  c  v  b  n  m  ⌫", "?123     ,      ☻      English      ↵").forEach {
            addView(label(it, 16f, text, true).apply {
                gravity = Gravity.CENTER
                background = rounded(surface, 12)
                setPadding(dp(5), dp(12), dp(5), dp(12))
            }, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(3), 0, dp(3)) })
        }
    }

    private fun toggle(title: String, desc: String, key: String, default: Boolean) {
        content.addView(card {
            addView(label(title, 17f, text, true))
            addView(label(desc, 13f, muted, false).apply { setPadding(0, dp(4), 0, dp(8)) })
            addView(Button(this@MainActivity).apply {
                text = if (prefs.getBoolean(key, default)) "ON  ✓" else "OFF"
                isAllCaps = false
                setTextColor(this@MainActivity.text)
                background = rounded(if (prefs.getBoolean(key, default)) accent else surface, 14)
                setOnClickListener {
                    val next = !prefs.getBoolean(key, default)
                    prefs.edit().putBoolean(key, next).apply()
                    this.text = if (next) "ON  ✓" else "OFF"
                    background = rounded(if (next) accent else surface, 14)
                }
            })
        })
    }

    private fun action(title: String, subtitle: String, onClick: () -> Unit): View = card {
        addView(label(title, 16f, text, true))
        addView(label(subtitle, 13f, muted, false).apply { setPadding(0, dp(4), 0, dp(9)) })
        addView(button("Open") { onClick() })
    }

    private fun sectionTitle(value: String) {
        content.addView(label(value, 11f, accent, true).apply { setPadding(dp(3), dp(18), 0, dp(8)) })
    }

    private fun card(build: LinearLayout.() -> Unit): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(15), dp(16), dp(15))
        background = rounded(surface, 22)
        build()
        val params = LinearLayout.LayoutParams(-1, -2)
        params.bottomMargin = dp(10)
        layoutParams = params
    }

    private fun label(value: String, size: Float, color: Int, bold: Boolean) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun button(value: String, onClick: () -> Unit) = Button(this).apply {
        text = value
        isAllCaps = false
        setTextColor(this@MainActivity.text)
        background = rounded(accent, 14)
        setOnClickListener { onClick() }
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
