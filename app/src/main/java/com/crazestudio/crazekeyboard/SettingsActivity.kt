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
import android.os.Looper
import android.os.Handler
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*

class SettingsActivity : Activity() {
    private val voicePermissionRequest = 701
    private val mainHandler = Handler(Looper.getMainLooper())
    private var crazeLmStatus: TextView? = null

    private fun startCrazeLmDownload() {
        CrazeLmTflitePredictor.observe { message ->
            mainHandler.post {
                if (!isFinishing) crazeLmStatus?.text = message
            }
        }
        CrazeLmTflitePredictor.initializeAsync(applicationContext)
    }

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
        setPadding(dp(20), dp(20), dp(20), dp(18))
        background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.rgb(31, 28, 61), Color.rgb(93, 72, 193), Color.rgb(126, 79, 191))
        ).apply { cornerRadius = dp(26).toFloat() }
        addView(text("CRAZESTUDIO  /  INPUT STUDIO", 10f, Color.rgb(222, 216, 255), true))
        addView(text("CrazeKeyboard", 29f, Color.WHITE, true).apply {
            setPadding(0, dp(6), 0, dp(2))
        })
        addView(text("A calmer, faster space to type.", 13f, Color.rgb(240, 237, 255)))
        val mock = LinearLayout(this@SettingsActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(7), dp(7), dp(7), dp(7))
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(Color.argb(42, 255, 255, 255))
            }
        }
        listOf("q w e r t y u i o p", "a s d f g h j k l", "⇧ z x c v b n m ⌫").forEach { line ->
            val keys = LinearLayout(this@SettingsActivity).apply { gravity = Gravity.CENTER }
            line.split(" ").filter { it.isNotEmpty() }.forEach { key ->
                keys.addView(
                    TextView(this@SettingsActivity).apply {
                        text = key
                        gravity = Gravity.CENTER
                        textSize = 10f
                        setTextColor(Color.WHITE)
                        background = GradientDrawable().apply {
                            cornerRadius = dp(5).toFloat()
                            setColor(Color.argb(54, 255, 255, 255))
                        }
                    },
                    LinearLayout.LayoutParams(0, dp(24), 1f).apply {
                        setMargins(dp(2), 0, dp(2), dp(3))
                    }
                )
            }
            mock.addView(keys)
        }
        addView(mock, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
        addView(text("OFFLINE-FIRST  ·  PERSONALISABLE  ·  PRIVATE", 10f, Color.WHITE, true).apply {
            setPadding(0, dp(13), 0, 0)
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

        val packsSection = section(
            "Language packs",
            "Download compressed vocabulary packs once. They are verified and stored on this device for offline suggestions."
        )
        fun addPackRow(code: String, label: String) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(7), 0, dp(7))
            }
            val details = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            details.addView(text(label, 14f, ink, true))
            val info = text(
                if (LanguagePackManager.isInstalled(this, code)) "Installed · offline ready" else "Not downloaded",
                11f, muted
            )
            details.addView(info)
            row.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(action(
                if (LanguagePackManager.isInstalled(this, code)) "Update" else "Download",
                true
            ) {
                info.text = "Downloading and verifying…"
                LanguagePackManager.download(this, code) { success, message ->
                    runOnUiThread {
                        info.text = if (success) "$message · offline ready" else "Failed: $message"
                    }
                }
            })
            packsSection.addView(row)
        }
        addPackRow("en", "English")
        packsSection.addView(divider())
        addPackRow("bn", "Bengali")
        packsSection.addView(divider())
        addPackRow("hi", "Hindi")
        packsSection.addView(divider())
        packsSection.addView(settingSwitch(
            "Online prefix suggestions",
            "Send only the current word prefix and language to CrazePredictionAPI. Never sends the sentence.",
            "onlinepred",
            true
        ))
        content.addView(packsSection, margin())

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

        val crazeLm = section(
            "CrazeLM · Offline AI",
            "Download the 30M English model and its matching tokenizer. Predictions run locally on this device; the model is stored privately and is not bundled into the APK."
        )
        val modelStatus = text(CrazeLmTflitePredictor.status, 11f, muted)
        crazeLmStatus = modelStatus
        crazeLm.addView(modelStatus)
        crazeLm.addView(action("Download / Repair AI model", true) {
            startCrazeLmDownload()
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        content.addView(crazeLm, margin())

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
        prefs.addView(settingSwitch("Personalized predictions", "Learn confirmed word patterns locally to improve suggestions.", "personalized", true))
        prefs.addView(divider())
        prefs.addView(settingSwitch("Double-space period", "Turn two quick spaces into a full stop.", "doubleSpacePeriod", true))
        prefs.addView(divider())
        prefs.addView(settingSwitch("Haptic feedback", "A light vibration on key press.", "haptics", true))
        prefs.addView(divider())
        prefs.addView(settingSwitch("Key sounds", "Play a soft system key-click while typing.", "keysound", false))
        content.addView(prefs, margin())


        val toolbarSection = section(
            "Customise keyboard tools",
            "Choose which shortcuts appear before typing, then move enabled tools to set their order. The menu keeps Settings available."
        )
        val toolbarRows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val toolOptions = linkedMapOf(
            "editor" to ("Text editor" to R.drawable.ic_edit),
            "clipboard" to ("Clipboard" to R.drawable.ic_clip),
            "emoji" to ("Emoji" to R.drawable.ic_emoji),
            "voice" to ("Voice typing" to R.drawable.ic_mic),
            "theme" to ("Theme" to R.drawable.ic_palette),
            "onehand" to ("One-handed mode" to R.drawable.ic_onehand),
            "floating" to ("Floating / dock" to R.drawable.ic_float),
            "resize" to ("Keyboard resizer" to R.drawable.ic_grip),
            "settings" to ("Settings" to R.drawable.ic_settings),
            "keyboard" to ("Show keyboard" to R.drawable.ic_activity),
            "hide" to ("Hide keyboard" to R.drawable.ic_hide)
        )
        fun renderToolbarRows() {
            toolbarRows.removeAllViews()
            val active = Prefs.toolbarTools(this).toMutableList()
            val ordered = active + toolOptions.keys.filterNot { it in active }
            ordered.forEach { id ->
                val option = toolOptions[id] ?: return@forEach
                val enabled = id in active
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(7), 0, dp(7))
                }
                row.addView(ImageView(this).apply {
                    setImageResource(option.second)
                    setColorFilter(ink)
                    setPadding(dp(4), dp(4), dp(4), dp(4))
                    contentDescription = option.first
                }, LinearLayout.LayoutParams(dp(34), dp(34)))
                row.addView(text(option.first, 13f, ink, enabled),
                    LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(8) })
                if (enabled) {
                    val index = active.indexOf(id)
                    val up = text("↑", 19f, if (index > 0) ink else muted, true).apply {
                        gravity = Gravity.CENTER
                        background = shape(actionBg, 9)
                        isClickable = index > 0
                        setPadding(dp(10), dp(5), dp(10), dp(5))
                        setOnClickListener {
                            val list = Prefs.toolbarTools(this@SettingsActivity).toMutableList()
                            if (index > 0) {
                                val item = list.removeAt(index)
                                list.add(index - 1, item)
                                Prefs.setToolbarTools(this@SettingsActivity, list)
                                renderToolbarRows()
                            }
                        }
                    }
                    val down = text("↓", 19f, if (index < active.lastIndex) ink else muted, true).apply {
                        gravity = Gravity.CENTER
                        background = shape(actionBg, 9)
                        isClickable = index < active.lastIndex
                        setPadding(dp(10), dp(5), dp(10), dp(5))
                        setOnClickListener {
                            val list = Prefs.toolbarTools(this@SettingsActivity).toMutableList()
                            if (index < list.lastIndex) {
                                val item = list.removeAt(index)
                                list.add(index + 1, item)
                                Prefs.setToolbarTools(this@SettingsActivity, list)
                                renderToolbarRows()
                            }
                        }
                    }
                    row.addView(up, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(5) })
                    row.addView(down, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(6) })
                }
                row.addView(Switch(this).apply {
                    isChecked = enabled
                    contentDescription = "Show ${option.first}"
                    setOnCheckedChangeListener { _, checked ->
                        val list = Prefs.toolbarTools(this@SettingsActivity).toMutableList()
                        if (checked && id !in list) list.add(id)
                        if (!checked) list.remove(id)
                        Prefs.setToolbarTools(this@SettingsActivity, list)
                        renderToolbarRows()
                    }
                })
                toolbarRows.addView(row)
                if (id != ordered.last()) toolbarRows.addView(divider())
            }
        }
        renderToolbarRows()
        toolbarSection.addView(toolbarRows)
        content.addView(toolbarSection, margin())

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
            UserLanguageModel.clear(this)
            Toast.makeText(this, "Learned words and typing patterns cleared", Toast.LENGTH_SHORT).show()
        })
        content.addView(privacy, margin())

        val appearance = section("Appearance", "Select the colour style used by your keyboard.")
        val themeGrid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val currentTheme = Prefs.i(this, "theme", 0)
        Prefs.themeNames.chunked(3).forEachIndexed { rowIndex, names ->
            val themeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            names.forEachIndexed { columnIndex, name ->
                val index = rowIndex * 3 + columnIndex
                themeRow.addView(themeCard(index, name, currentTheme == index),
                    LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) })
            }
            repeat(3 - names.size) { themeRow.addView(Space(this), LinearLayout.LayoutParams(0, 1, 1f)) }
            themeGrid.addView(themeRow)
        }
        appearance.addView(themeGrid)
        content.addView(appearance, margin())

        val resizeSection = section(
            "Keyboard studio",
            "Resize visually. Width controls floating and one-handed layouts; height changes every keyboard key."
        )
        val previewFrame = FrameLayout(this).apply {
            background = shape(fieldBg, 16)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            minimumHeight = dp(116)
        }
        val previewBoard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = shape(actionBg, 12)
            setPadding(dp(5), dp(6), dp(5), dp(6))
            elevation = dp(2).toFloat()
        }
        val previewRows = ArrayList<LinearLayout>()
        listOf("qwertyuiop", "asdfghjkl", "⇧zxcvbnm⌫").forEach { chars ->
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER }
            chars.forEach { character ->
                row.addView(TextView(this).apply {
                    text = character.toString()
                    textSize = 10f
                    gravity = Gravity.CENTER
                    setTextColor(ink)
                    background = shape(card, 5)
                    includeFontPadding = false
                }, LinearLayout.LayoutParams(0, dp(23), 1f).apply {
                    setMargins(dp(1), 0, dp(1), 0)
                })
            }
            previewBoard.addView(row, LinearLayout.LayoutParams(-1, dp(23)).apply {
                bottomMargin = dp(4)
            })
            previewRows.add(row)
        }
        previewFrame.addView(previewBoard, FrameLayout.LayoutParams(dp(270), -2, Gravity.CENTER))
        resizeSection.addView(previewFrame, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(14)
        })

        val widthHeader = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(text("Keyboard width", 14f, ink, true), LinearLayout.LayoutParams(0, -2, 1f))
        }
        val widthValue = text("${Prefs.keyWidth(this)}%", 13f, accent, true)
        widthHeader.addView(widthValue)
        resizeSection.addView(widthHeader)
        val widthSeek = SeekBar(this).apply {
            max = 45
            progress = Prefs.keyWidth(this@SettingsActivity) - 55
            progressTintList = ColorStateList.valueOf(accent)
            thumbTintList = ColorStateList.valueOf(accent)
        }
        resizeSection.addView(widthSeek, LinearLayout.LayoutParams(-1, -2))
        resizeSection.addView(text("55% — 100%  ·  Compact to full width", 11f, muted).apply {
            setPadding(0, dp(1), 0, dp(10))
        })

        val heightHeader = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(text("Key height", 14f, ink, true), LinearLayout.LayoutParams(0, -2, 1f))
        }
        val heightValue = text("${Prefs.keyH(this)} dp", 13f, accent, true)
        heightHeader.addView(heightValue)
        resizeSection.addView(heightHeader)
        val heightSeek = SeekBar(this).apply {
            max = 30
            progress = Prefs.keyH(this@SettingsActivity) - 34
            progressTintList = ColorStateList.valueOf(accent)
            thumbTintList = ColorStateList.valueOf(accent)
        }
        resizeSection.addView(heightSeek, LinearLayout.LayoutParams(-1, -2))
        resizeSection.addView(text("34 dp — 64 dp  ·  Smaller keys save screen space", 11f, muted).apply {
            setPadding(0, dp(1), 0, dp(10))
        })

        fun updateKeyboardPreview() {
            val width = Prefs.keyWidth(this)
            val height = Prefs.keyH(this)
            widthValue.text = "${width}%"
            heightValue.text = "${height} dp"
            val availableWidth = (previewFrame.width - dp(12)).coerceAtLeast(dp(190))
            val previewWidth = (availableWidth * width / 100f).toInt().coerceIn(dp(180), availableWidth)
            (previewBoard.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
                params.width = previewWidth
                previewBoard.layoutParams = params
            }
            val previewKeyHeight = dp((height * 0.52f).toInt().coerceIn(18, 34))
            previewRows.forEach { row ->
                (row.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
                    params.height = previewKeyHeight
                    row.layoutParams = params
                }
                for (index in 0 until row.childCount) {
                    row.getChildAt(index).layoutParams = LinearLayout.LayoutParams(
                        0, previewKeyHeight, 1f
                    ).apply { setMargins(dp(1), 0, dp(1), 0) }
                }
            }
        }
        widthSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                Prefs.setI(this@SettingsActivity, "kbdwidth", progress + 55)
                updateKeyboardPreview()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
        heightSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                Prefs.setI(this@SettingsActivity, "keyh", progress + 34)
                updateKeyboardPreview()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        // Gboard-inspired visual resize frame: drag the top/bottom edges for height,
        // or the side handles for width. The preview and numeric controls stay in sync.
        fun resizeHandle(label: String, horizontal: Boolean, direction: Int): TextView =
            text(label, 17f, accent, true).apply {
                gravity = Gravity.CENTER
                background = shape(card, 10)
                elevation = dp(4).toFloat()
                contentDescription = if (horizontal) "Drag to resize keyboard width" else "Drag to resize keyboard height"
                setOnTouchListener { view, event ->
                    when (event.actionMasked) {
                        android.view.MotionEvent.ACTION_DOWN -> {
                            view.tag = if (horizontal) event.rawX else event.rawY
                            true
                        }
                        android.view.MotionEvent.ACTION_MOVE -> {
                            val start = (view.tag as? Float) ?: (if (horizontal) event.rawX else event.rawY)
                            val now = if (horizontal) event.rawX else event.rawY
                            val delta = ((now - start) / resources.displayMetrics.density * direction).toInt()
                            if (horizontal) widthSeek.progress = (widthSeek.progress + delta).coerceIn(0, 45)
                            else heightSeek.progress = (heightSeek.progress + delta).coerceIn(0, 30)
                            view.tag = now
                            true
                        }
                        android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> true
                        else -> true
                    }
                }
            }
        previewFrame.addView(resizeHandle("↕", false, -1), FrameLayout.LayoutParams(dp(44), dp(28), Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        previewFrame.addView(resizeHandle("↕", false, 1), FrameLayout.LayoutParams(dp(44), dp(28), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
        previewFrame.addView(resizeHandle("↔", true, -1), FrameLayout.LayoutParams(dp(32), dp(44), Gravity.START or Gravity.CENTER_VERTICAL))
        previewFrame.addView(resizeHandle("↔", true, 1), FrameLayout.LayoutParams(dp(32), dp(44), Gravity.END or Gravity.CENTER_VERTICAL))

        val resizeActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        resizeActions.addView(action("Reset", false) {
            widthSeek.progress = 45
            heightSeek.progress = 12
            Prefs.setI(this@SettingsActivity, "onehand", 0)
            Prefs.setB(this@SettingsActivity, "float", false)
            Prefs.setI(this@SettingsActivity, "fx", 0)
            Prefs.setI(this@SettingsActivity, "fy", 0)
            updateKeyboardPreview()
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(5) })
        resizeActions.addView(action("Move", false) {
            Prefs.setB(this@SettingsActivity, "float", true)
            Toast.makeText(this@SettingsActivity, "Floating mode enabled. Drag the keyboard's top grip to move it.", Toast.LENGTH_LONG).show()
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(5); rightMargin = dp(5) })
        resizeActions.addView(action("Done", true) {
            Toast.makeText(this@SettingsActivity, "Keyboard size saved", Toast.LENGTH_SHORT).show()
            finish()
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(5) })
        resizeSection.addView(resizeActions, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })

        resizeSection.addView(text("Quick height presets", 13f, ink, true).apply {
            setPadding(0, dp(6), 0, dp(8))
        })
        val heightPresets = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("Compact" to 40, "Standard" to 46, "Tall" to 54).forEachIndexed { index, preset ->
            heightPresets.addView(action(preset.first) {
                heightSeek.progress = preset.second - 34
                updateKeyboardPreview()
            }, LinearLayout.LayoutParams(0, -2, 1f).apply {
                if (index > 0) leftMargin = dp(5)
                if (index < 2) rightMargin = dp(5)
            })
        }
        resizeSection.addView(heightPresets)
        content.addView(resizeSection, margin())
        previewFrame.post { updateKeyboardPreview() }
        val layout = section("Keyboard modes", "Choose how the keyboard sits above the app.")
        layout.addView(settingSwitch("Floating keyboard", "Move the keyboard independently without resizing the app underneath.", "float", false))
        layout.addView(divider())
        layout.addView(text("One-handed layout", 14f, ink, true).apply {
            setPadding(0, dp(10), 0, dp(8))
        })
        val handButtons = ArrayList<Pair<Int, TextView>>()
        fun refreshHandButtons() {
            val selected = Prefs.i(this@SettingsActivity, "onehand", 0)
            handButtons.forEach { (value, button) ->
                val active = value == selected
                button.background = shape(if (active) accent else actionBg, 12)
                button.setTextColor(if (active) Color.WHITE else ink)
            }
        }
        val handRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("Full" to 0, "Left" to 1, "Right" to 2).forEach { (label, value) ->
            val button = action(label, Prefs.i(this, "onehand", 0) == value) {
                Prefs.setI(this@SettingsActivity, "onehand", value)
                refreshHandButtons()
            }
            handButtons.add(value to button)
            handRow.addView(button, LinearLayout.LayoutParams(0, -2, 1f).apply {
                setMargins(dp(3), 0, dp(3), 0)
            })
        }
        layout.addView(handRow)
        refreshHandButtons()
        content.addView(layout, margin())

        content.addView(text("CrazeStudio  •  Offline-first", 11f, muted).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(10))
        })

        setContentView(root)
        refreshKeyboardStatus()
        if (intent.getBooleanExtra("open_resizer", false)) {
            scroll.post { scroll.smoothScrollTo(0, resizeSection.top) }
        }
        if (intent.getBooleanExtra("request_voice_permission", false) &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), voicePermissionRequest)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshKeyboardStatus()
        CrazeLmTflitePredictor.observe { message ->
            mainHandler.post { if (!isFinishing) crazeLmStatus?.text = message }
        }
    }

    override fun onDestroy() {
        CrazeLmTflitePredictor.observe(null)
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
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
