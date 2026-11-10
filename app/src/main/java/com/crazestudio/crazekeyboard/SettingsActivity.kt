package com.crazestudio.crazekeyboard

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*

class SettingsActivity : Activity() {
    private val voicePermissionRequest = 701
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private val ink = Color.rgb(31, 37, 51)
    private val muted = Color.rgb(111, 119, 137)
    private val accent = Color.rgb(102, 83, 215)
    private val page = Color.rgb(243, 245, 250)
    private val dividerColor = Color.rgb(238, 240, 245)
    private var enabledBadge: TextView? = null
    private var selectedBadge: TextView? = null
    private var setupAction: TextView? = null

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
            isClickable = true
            isFocusable = true
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

    private fun pill(label: String, positive: Boolean) = TextView(this).apply {
        text = label
        textSize = 11f
        gravity = Gravity.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setTextColor(if (positive) Color.rgb(37, 112, 78) else Color.rgb(105, 111, 126))
        background = shape(if (positive) Color.rgb(229, 246, 237) else Color.rgb(240, 242, 247), 30)
        setPadding(dp(11), dp(7), dp(11), dp(7))
    }

    private fun updatePill(view: TextView?, label: String, positive: Boolean) {
        view ?: return
        view.text = label
        view.setTextColor(if (positive) Color.rgb(37, 112, 78) else Color.rgb(105, 111, 126))
        view.background = shape(if (positive) Color.rgb(229, 246, 237) else Color.rgb(240, 242, 247), 30)
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
                contentDescription = title
                setOnCheckedChangeListener { _, checked ->
                    Prefs.setB(this@SettingsActivity, key, checked)
                }
            })
        }

    private fun keyboardEnabled(): Boolean {
        val manager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        return manager.enabledInputMethodList.any {
            it.packageName == packageName &&
                it.serviceInfo.name == CrazeKeyboardService::class.java.name
        }
    }

    private fun keyboardSelected(): Boolean {
        val selected = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        return selected?.startsWith("$packageName/") == true
    }

    private fun refreshKeyboardStatus() {
        val enabled = keyboardEnabled()
        val selected = keyboardSelected()
        updatePill(enabledBadge, if (enabled) "✓  Enabled" else "1  Not enabled", enabled)
        updatePill(selectedBadge, if (selected) "✓  Active" else "2  Not selected", selected)
        setupAction?.text = when {
            !enabled -> "Enable in Android settings"
            !selected -> "Choose CrazeKeyboard"
            else -> "Open keyboard picker"
        }
    }

    private fun hero() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(22), dp(20), dp(22))
        background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.rgb(42, 35, 82), Color.rgb(105, 82, 218))
        ).apply { cornerRadius = dp(24).toFloat() }
        addView(text("CRAZESTUDIO  •  ANDROID KEYBOARD", 10f, Color.rgb(224, 218, 255), true))
        addView(text("CrazeKeyboard", 29f, Color.WHITE, true).apply {
            setPadding(0, dp(7), 0, dp(2))
        })
        addView(text("Your typing space. Tuned by you.", 13f, Color.rgb(238, 235, 255)))
        addView(text("OFFLINE SUGGESTIONS   ·   VOICE TYPING   ·   QUICK EDITING", 10f, Color.WHITE, true).apply {
            setPadding(0, dp(17), 0, 0)
        })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = page
        window.navigationBarColor = page
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(page)
        }
        val scroll = ScrollView(this).apply {
            clipToPadding = false
            isFillViewport = true
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(17), dp(18), dp(17), dp(28))
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        content.addView(hero(), margin())

        val status = section("Keyboard status", "Complete both steps to use CrazeKeyboard in other apps.")
        status.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            enabledBadge = pill("Checking…", false)
            selectedBadge = pill("Checking…", false)
            addView(enabledBadge, LinearLayout.LayoutParams(-2, -2))
            addView(Space(this@SettingsActivity), LinearLayout.LayoutParams(dp(8), 1))
            addView(selectedBadge, LinearLayout.LayoutParams(-2, -2))
        })
        setupAction = action("Check keyboard status", true) {
            if (!keyboardEnabled()) {
                startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            } else {
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
            }
        }.apply { setPadding(dp(12), dp(11), dp(12), dp(11)) }
        status.addView(setupAction)
        content.addView(status, margin())

        val test = section("Try your keyboard", "Tap the field below to test typing, suggestions, cursor control and voice input.")
        test.addView(EditText(this).apply {
            hint = "Start typing here…"
            textSize = 15f
            setTextColor(ink)
            setHintTextColor(muted)
            setPadding(dp(14), dp(13), dp(14), dp(13))
            background = shape(Color.rgb(246, 247, 251), 14)
            minLines = 2
            maxLines = 4
            gravity = Gravity.TOP or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = EditorInfo.IME_ACTION_DONE
        })
        content.addView(test, margin())

        val setup = section("Setup shortcuts", "Android controls which keyboard is enabled and selected.")
        setup.addView(action("Open keyboard settings") {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        })
        setup.addView(Space(this), LinearLayout.LayoutParams(1, dp(8)))
        setup.addView(action("Choose active keyboard") {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        })
        content.addView(setup, margin())

        val voice = section(
            "Voice typing",
            "Uses Android's selected speech recognizer. Google voice recognition is used when Google is selected as the device provider."
        )
        val voiceGranted = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        voice.addView(action(
            if (voiceGranted) "✓  Microphone permission enabled" else "Enable microphone permission",
            true
        ) {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Voice typing is ready from the keyboard microphone", Toast.LENGTH_SHORT).show()
            } else {
                requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), voicePermissionRequest)
            }
        })
        content.addView(voice, margin())

        val prefs = section("Typing preferences", "Choose how CrazeKeyboard assists while you type.")
        prefs.addView(settingSwitch("Number row", "Keep digits visible above letters.", "numrow", true))
        prefs.addView(divider())
        prefs.addView(settingSwitch("Word suggestions", "Show completions and spelling alternatives.", "suggest", true))
        prefs.addView(divider())
        prefs.addView(settingSwitch("Auto-correction", "Correct close spelling mistakes when you press space.", "autocorrect", true))
        prefs.addView(divider())
        prefs.addView(settingSwitch("Next-word suggestions", "Suggest likely words after a space.", "nextWord", true))
        prefs.addView(divider())
        prefs.addView(settingSwitch("Double-space period", "Turn two quick spaces into a full stop.", "doubleSpacePeriod", true))
        prefs.addView(divider())
        prefs.addView(settingSwitch("Haptic feedback", "A light vibration on key press.", "haptics", true))
        content.addView(prefs, margin())

        val privacy = section(
            "Privacy & data",
            "Clipboard history stays on this device and is only collected while the keyboard is active. Password and no-personalized-learning fields are excluded."
        )
        privacy.addView(settingSwitch("Clipboard history", "Save copied text for quick access from the keyboard.", "clipboardHistory", true))
        privacy.addView(divider())
        privacy.addView(action("Clear clipboard history") {
            Prefs.clearClips(this)
            Toast.makeText(this, "Clipboard history cleared", Toast.LENGTH_SHORT).show()
        })
        privacy.addView(Space(this), LinearLayout.LayoutParams(1, dp(8)))
        privacy.addView(action("Clear learned words") {
            Prefs.clearLearned(this)
            Toast.makeText(this, "Learned words cleared", Toast.LENGTH_SHORT).show()
        })
        content.addView(privacy, margin())

        val appearance = section("Appearance", "Select the colour style used by your keyboard.")
        Prefs.themeNames.forEachIndexed { index, name ->
            val selectedTheme = Prefs.i(this, "theme", 0) == index
            appearance.addView(action(
                (if (selectedTheme) "✓  " else "") + name,
                selectedTheme
            ) {
                Prefs.setI(this, "theme", index)
                Toast.makeText(this, "$name theme selected", Toast.LENGTH_SHORT).show()
                recreate()
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
        refreshKeyboardStatus()
        if (intent.getBooleanExtra("request_voice_permission", false) &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), voicePermissionRequest)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshKeyboardStatus()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == voicePermissionRequest) {
            Toast.makeText(
                this,
                if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                    "Microphone enabled. Tap the keyboard microphone to speak."
                } else {
                    "Microphone permission was not granted."
                },
                Toast.LENGTH_SHORT
            ).show()
            recreate()
        }
    }

    private fun oneHandName(value: Int) = when (value) {
        1 -> "Left aligned"
        2 -> "Right aligned"
        else -> "Full width"
    }

    private fun margin() = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(13) }

    private fun divider() = View(this).apply {
        setBackgroundColor(dividerColor)
        layoutParams = LinearLayout.LayoutParams(-1, dp(1))
    }
}
