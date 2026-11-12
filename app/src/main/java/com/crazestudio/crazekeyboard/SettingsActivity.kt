package com.crazestudio.crazekeyboard

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
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

    // Palette follows the system light/dark setting (set in initPalette before any view is built).
    private var night = false
    private var ink = 0
    private var muted = 0
    private val accent = Color.rgb(102, 83, 215)
    private var page = 0
    private var card = 0
    private var dividerColor = 0
    private var fieldBg = 0
    private var actionBg = 0
    private var pillPosBg = 0
    private var pillPosFg = 0
    private var pillNegBg = 0
    private var pillNegFg = 0

    private var enabledBadge: TextView? = null
    private var selectedBadge: TextView? = null
    private var setupAction: TextView? = null
    private var voicePermissionButton: TextView? = null

    private fun initPalette() {
        night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        if (night) {
            ink = Color.rgb(238, 240, 247); muted = Color.rgb(154, 161, 178)
            page = Color.rgb(18, 19, 26); card = Color.rgb(30, 32, 42)
            dividerColor = Color.rgb(46, 49, 62); fieldBg = Color.rgb(24, 26, 35); actionBg = Color.rgb(44, 47, 61)
            pillPosBg = Color.rgb(28, 62, 46); pillPosFg = Color.rgb(142, 224, 180)
            pillNegBg = Color.rgb(44, 47, 61); pillNegFg = Color.rgb(154, 161, 178)
        } else {
            ink = Color.rgb(31, 37, 51); muted = Color.rgb(111, 119, 137)
            page = Color.rgb(243, 245, 250); card = Color.WHITE
            dividerColor = Color.rgb(238, 240, 245); fieldBg = Color.rgb(246, 247, 251); actionBg = Color.rgb(237, 239, 246)
            pillPosBg = Color.rgb(229, 246, 237); pillPosFg = Color.rgb(37, 112, 78)
            pillNegBg = Color.rgb(240, 242, 247); pillNegFg = Color.rgb(105, 111, 126)
        }
    }

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

    // Tap feedback: a rounded ripple on top of the button so presses are visible.
    private fun ripple(view: View, radius: Int) {
        view.foreground = RippleDrawable(ColorStateList.valueOf(Color.argb(48, 128, 128, 128)), null, shape(Color.WHITE, radius))
    }

    private fun action(label: String, primary: Boolean = false, click: () -> Unit) =
        TextView(this).apply {
            text = label
            textSize = 14f
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(if (primary) Color.WHITE else ink)
            background = shape(if (primary) accent else actionBg, 13)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            minHeight = dp(44)
            isClickable = true
            isFocusable = true
            ripple(this, 13)
            setOnClickListener { click() }
        }

    private fun section(title: String, hint: String? = null): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = shape(card, 20)
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
        setTextColor(if (positive) pillPosFg else pillNegFg)
        background = shape(if (positive) pillPosBg else pillNegBg, 30)
        setPadding(dp(11), dp(7), dp(11), dp(7))
    }

    private fun updatePill(view: TextView?, label: String, positive: Boolean) {
        view ?: return
        view.text = label
        view.setTextColor(if (positive) pillPosFg else pillNegFg)
        view.background = shape(if (positive) pillPosBg else pillNegBg, 30)
    }

    private fun settingSwitch(title: String, detail: String, key: String, default: Boolean) =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, dp(10))
            val labels = LinearLayout(this@SettingsActivity).apply { orientation = LinearLayout.VERTICAL }
            labels.addView(text(title, 14f, ink, true))
            labels.addView(text(detail, 11f, muted))
            addView(labels, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(12) })
            addView(Switch(this@SettingsActivity).apply {
                isChecked = Prefs.b(this@SettingsActivity, key, default)
                contentDescription = title
                // Accent-coloured switch that is readable in both light and dark mode.
                thumbTintList = ColorStateList(
                    arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                    intArrayOf(accent, if (night) Color.rgb(150, 156, 172) else Color.rgb(250, 250, 252))
                )
                trackTintList = ColorStateList(
                    arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                    intArrayOf(Color.argb(110, 102, 83, 215), if (night) Color.rgb(64, 68, 84) else Color.rgb(205, 209, 220))
                )
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
        addView(text("OFFLINE SUGGESTIONS  ·  VOICE TYPING  ·  QUICK EDITING", 10f, Color.WHITE, true).apply {
            setPadding(0, dp(17), 0, 0)
        })
    }

    // Mini keyboard swatch for a theme: shows its real background, key and accent colours.
    private fun themeCard(index: Int, name: String, selected: Boolean): View {
        val th = Prefs.themes[index]
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(8), dp(12), dp(8), dp(10))
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(th.bg)
                setStroke(dp(if (selected) 3 else 1), if (selected) accent else dividerColor)
            }
            isClickable = true
            isFocusable = true
            contentDescription = "$name theme" + if (selected) ", selected" else ""
            setOnClickListener {
                Prefs.setI(this@SettingsActivity, "theme", index)
                recreate()
            }
        }
        val keys = LinearLayout(this).apply { gravity = Gravity.CENTER }
        for (i in 0..3) {
            keys.addView(View(this).apply { background = shape(if (i == 3) th.accent else th.key, 4) },
                LinearLayout.LayoutParams(dp(14), dp(18)).apply { setMargins(dp(2), 0, dp(2), 0) })
        }
        box.addView(keys)
        box.addView(text((if (selected) "✓ " else "") + name, 12f, th.text, true).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, 0)
        })
        return box
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initPalette()
        window.statusBarColor = page
        window.navigationBarColor = page
        window.decorView.systemUiVisibility =
            if (night) 0 else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

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
            val enabledStatus = pill("Checking…", false)
            val selectedStatus = pill("Checking…", false)
            enabledBadge = enabledStatus
            selectedBadge = selectedStatus
            addView(enabledStatus, LinearLayout.LayoutParams(-2, -2))
            addView(Space(this@SettingsActivity), LinearLayout.LayoutParams(dp(8), 1))
            addView(selectedStatus, LinearLayout.LayoutParams(-2, -2))
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        val setupButton = action("Check keyboard status", true) {
            if (!keyboardEnabled()) {
                startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            } else {
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
            }
        }
        setupAction = setupButton
        status.addView(setupButton)
        content.addView(status, margin())

        val test = section("Try your keyboard", "Tap the field below to test typing, suggestions, cursor control and voice input.")
        test.addView(EditText(this).apply {
            hint = "Start typing here…"
            textSize = 15f
            setTextColor(ink)
            setHintTextColor(muted)
            setPadding(dp(14), dp(13), dp(14), dp(13))
            background = shape(fieldBg, 14)
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
        val voiceButton = action(
            if (voiceGranted) "✓  Microphone permission enabled" else "Enable microphone permission",
            true
        ) {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Voice typing is ready from the keyboard microphone", Toast.LENGTH_SHORT).show()
            } else {
                requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), voicePermissionRequest)
            }
        }
        voicePermissionButton = voiceButton
        voice.addView(voiceButton)
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
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        privacy.addView(Space(this), LinearLayout.LayoutParams(1, dp(8)))
        privacy.addView(action("Clear learned words") {
            Prefs.clearLearned(this)
            Toast.makeText(this, "Learned words cleared", Toast.LENGTH_SHORT).show()
        })
        content.addView(privacy, margin())

        val appearance = section("Appearance", "Select the colour style used by your keyboard.")
        val themeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val currentTheme = Prefs.i(this, "theme", 0)
        Prefs.themeNames.forEachIndexed { index, name ->
            themeRow.addView(themeCard(index, name, currentTheme == index),
                LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(4), 0, dp(4), 0) })
        }
        appearance.addView(themeRow)
        content.addView(appearance, margin())

        val size = section("Keyboard size", "Adjust key height. The keyboard automatically limits it on short screens.")
        val keyPreview = View(this).apply { background = shape(actionBg, 9) }
        val previewBox = FrameLayout(this).apply {
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = shape(fieldBg, 14)
            addView(keyPreview, FrameLayout.LayoutParams(-1, dp(Prefs.keyH(this@SettingsActivity))))
        }
        size.addView(previewBox, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        val sizeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val sizeValue = text("${Prefs.keyH(this)} dp", 15f, ink, true)
        fun applySize(v: Int) {
            Prefs.setI(this, "keyh", v.coerceIn(34, 64))
            sizeValue.text = "${Prefs.keyH(this)} dp"
            keyPreview.layoutParams = (keyPreview.layoutParams as FrameLayout.LayoutParams).apply { height = dp(Prefs.keyH(this@SettingsActivity)) }
        }
        sizeRow.addView(sizeValue, LinearLayout.LayoutParams(0, -2, 1f))
        sizeRow.addView(action("−") { applySize(Prefs.keyH(this) - 2) }, LinearLayout.LayoutParams(dp(56), -2))
        sizeRow.addView(Space(this), LinearLayout.LayoutParams(dp(8), 1))
        sizeRow.addView(action("+", true) { applySize(Prefs.keyH(this) + 2) }, LinearLayout.LayoutParams(dp(56), -2))
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
            val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
            Toast.makeText(
                this,
                if (granted) "Microphone enabled. Tap the keyboard microphone to speak." else "Microphone permission was not granted.",
                Toast.LENGTH_SHORT
            ).show()
            voicePermissionButton?.text = if (granted) "✓  Microphone permission enabled" else "Enable microphone permission"
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
