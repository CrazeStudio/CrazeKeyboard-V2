package com.crazestudio.crazekeyboard

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.*

class SettingsActivity : Activity() {
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(24), dp(20), dp(24)) }
        fun title(s: String) = TextView(this).apply { text = s; textSize = 22f; setPadding(0, dp(12), 0, dp(6)) }
        fun btn(s: String, a: () -> Unit) = Button(this).apply { text = s; setOnClickListener { a() } }
        fun sw(s: String, k: String, d: Boolean) = Switch(this).apply {
            text = s; isChecked = Prefs.b(context, k, d); setOnCheckedChangeListener { _, v -> Prefs.setB(context, k, v) }
        }

        col.addView(title("CrazeKeyboard"))
        col.addView(btn("1. Enable keyboard") { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) })
        col.addView(btn("2. Select keyboard") { (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker() })
        col.addView(title("Preferences"))
        col.addView(sw("Haptic feedback", "haptics", true))
        col.addView(sw("Number row", "numrow", true))
        col.addView(sw("Suggestions", "suggest", true))
        col.addView(title("Theme"))
        Prefs.themeNames.forEachIndexed { i, n -> col.addView(btn(n) { Prefs.setI(this, "theme", i) }) }
        col.addView(title("Key height"))
        val row = LinearLayout(this)
        row.addView(btn("−") { Prefs.setI(this, "keyh", (Prefs.keyH(this) - 4).coerceAtLeast(34)) })
        row.addView(btn("+") { Prefs.setI(this, "keyh", (Prefs.keyH(this) + 4).coerceAtMost(64)) })
        col.addView(row)
        col.addView(title("Try it"))
        col.addView(EditText(this).apply { hint = "Type here to test" })
        setContentView(ScrollView(this).apply { addView(col) })
    }
}
