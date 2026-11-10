package com.crazestudio.crazekeyboard

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*

class SettingsActivity : Activity() {
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private val ink = Color.rgb(31, 37, 51)
    private val muted = Color.rgb(111, 119, 137)
    private val accent = Color.rgb(102, 83, 215)
    private val page = Color.rgb(243, 245, 250)

    private fun shape(color: Int, radius: Int = 18) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(radius).toFloat()
        setColor(color)
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }

    private fun action(label: String, primary: Boolean = false, click: () -> Unit) =
        TextView(this).apply {
            text = label
            textSize = 14f
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(if (primary) Color.WHITE else ink)
            background = shape(if (primary) accent else Color.rgb(237, 239, 246), 13)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setOnClickListener { click() }
        }

    private fun section(title: String, hint: String? = null): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = shape(Color.WHITE, 20)
            setPadding(dp(16), dp(15), dp(16), dp(15))
            addView(text(title, 16f, ink, true))
            if (hint != null) addView(text(hint, 12f, muted).apply {
                setPadding(0, dp(4), 0, dp(10))
            })
        }

    private fun settingSwitch(title: String, detail: String, key: String, default: Boolean) =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, dp(10))
            val labels = LinearLayout(this@SettingsActivity).apply { orientation = LinearLayout.VERTICAL }
            labels.addView(text(title, 14f, ink, true))
            labels.addView(text(detail, 11f, muted))
            addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
            addView(Switch(this@SettingsActivity).apply {
                isChecked = Prefs.b(this@SettingsActivity, key, default)
                setOnCheckedChangeListener { _, checked ->
                    Prefs.setB(this@SettingsActivity, key, checked)
                }
            })
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = page
        window.navigationBarColor = page
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(page)
        }
        val scroll = ScrollView(this).apply { clipToPadding = false }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(24), dp(18), dp(28))
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        content.addView(text("CrazeKeyboard", 28f, ink, true))
        content.addView(text("A cleaner, faster typing experience.", 13f, muted).apply {
            setPadding(0, dp(4), 0, dp(18))
        })

        val setup = section("Get started", "Enable CrazeKeyboard, then choose it as your active input method.")
        setup.addView(action("Open keyboard settings", true) {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }.apply { setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        } })
        setup.addView(action("Choose active keyboard") {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        }.apply { setOnClickListener {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        } }.apply { setPadding(dp(12), dp(10), dp(12), dp(10)) })
        content.addView(setup, margin())

        val prefs = section("Typing preferences", "Control the keyboard behaviour.")
        prefs.addView(settingSwitch("Number row", "Keep digits visible above letters.", "numrow", true))
        prefs.addView(divider())
        prefs.addView(settingSwitch("Word suggestions", "Show suggestions while typing.", "suggest", true))
        prefs.addView(divider())
        prefs.addView(settingSwitch("Haptic feedback", "A light vibration on key press.", "haptics", true))
        content.addView(prefs, margin())

        val privacy = section("Privacy & data", "Clipboard history is only collected while the keyboard is active. Password and no-personalized-learning fields are excluded.")
        privacy.addView(settingSwitch("Clipboard history", "Save copied text for quick access from the keyboard.", "clipboardHistory", true))
        privacy.addView(divider())
        privacy.addView(action("Clear clipboard history") {
            Prefs.clearClips(this)
            Toast.makeText(this, "Clipboard history cleared", Toast.LENGTH_SHORT).show()
        })
        content.addView(privacy, margin())

        val appearance = section("Appearance", "Choose a colour style for your keyboard.")
        Prefs.themeNames.forEachIndexed { index, name ->
            val selected = Prefs.i(this, "theme", 0) == index
            appearance.addView(action(
                (if (selected) "✓  " else "") + name,
                selected
            ) {
                Prefs.setI(this, "theme", index)
                recreate()
            }.apply {
                setOnClickListener {
                    Prefs.setI(this@SettingsActivity, "theme", index)
                    recreate()
                }
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) })
        }
        content.addView(appearance, margin())

        val size = section("Keyboard size", "Adjust key height. The keyboard automatically limits it on short screens.")
        val sizeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val sizeValue = text("${Prefs.keyH(this)} dp", 15f, ink, true)
        sizeRow.addView(sizeValue, LinearLayout.LayoutParams(0, -2, 1f))
        sizeRow.addView(action("−") {
            Prefs.setI(this, "keyh", (Prefs.keyH(this) - 2).coerceAtLeast(34))
            sizeValue.text = "${Prefs.keyH(this)} dp"
        })
        sizeRow.addView(Space(this), LinearLayout.LayoutParams(dp(8), 1))
        sizeRow.addView(action("+", true) {
            Prefs.setI(this, "keyh", (Prefs.keyH(this) + 2).coerceAtMost(64))
            sizeValue.text = "${Prefs.keyH(this)} dp"
        })
        size.addView(sizeRow)
        content.addView(size, margin())

        val layout = section("One-handed mode", "Cycle between full width, left aligned and right aligned.")
        val oneHandLabel = text(oneHandName(Prefs.i(this, "onehand", 0)), 14f, ink, true)
        layout.addView(LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(oneHandLabel, LinearLayout.LayoutParams(0, -2, 1f))
            addView(action("Change") {
                Prefs.nextOneHand(this@SettingsActivity)
                oneHandLabel.text = oneHandName(Prefs.i(this@SettingsActivity, "onehand", 0))
            })
        })
        content.addView(layout, margin())

        content.addView(text("CrazeStudio  •  Offline-first", 11f, muted).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(10))
        })

        setContentView(root)
    }

    private fun oneHandName(value: Int) = when (value) {
        1 -> "Left aligned"
        2 -> "Right aligned"
        else -> "Full width"
    }

    private fun margin() = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(13) }

    private fun divider() = View(this).apply {
        setBackgroundColor(Color.rgb(238, 240, 245))
        layoutParams = LinearLayout.LayoutParams(-1, dp(1))
    }
}
