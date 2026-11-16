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
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*

class SettingsActivity : Activity() {
    private val voicePermissionRequest = 701
    private val mainHandler = Handler(Looper.getMainLooper())
    private var crazeLmStatus: TextView? = null
    private var crazeLmMemoryView: TextView? = null
    private val settingsCategoryViews = LinkedHashMap<String, TextView>()

    private fun startCrazeLmDownload() {
        CrazeLmTflitePredictor.observe { message ->
            mainHandler.post {
                if (!isFinishing) {
                    crazeLmStatus?.text = message
                    refreshMemoryReadout()
                }
            }
        }
        CrazeLmTflitePredictor.initializeAsync(applicationContext, allowNetworkDownload = true)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

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
            minHeight = dp(48)
            isClickable = true
            isFocusable = true
            ripple(this, 13)
            setOnClickListener { click() }
        }

    private fun iconAction(res: Int, description: String, enabled: Boolean, click: () -> Unit) =
        ImageView(this).apply {
            setImageResource(res)
            setColorFilter(if (enabled) ink else muted)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            background = shape(actionBg, 10)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            minimumWidth = dp(44)
            minimumHeight = dp(44)
            contentDescription = description
            isClickable = enabled
            isFocusable = enabled
            alpha = if (enabled) 1f else 0.4f
            if (enabled) {
                ripple(this, 10)
                setOnClickListener { click() }
            }
        }

    private fun category(label: String) = TextView(this).apply {
        text = label.uppercase()
        textSize = 10f
        letterSpacing = 0.10f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setTextColor(muted)
        setPadding(dp(4), dp(7), dp(4), dp(8))
        tag = "settings_category"
        contentDescription = label
        settingsCategoryViews[label] = this
    }

    private fun section(title: String, hint: String? = null): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = shape(card, 20)
            setPadding(dp(16), dp(15), dp(16), dp(15))
            tag = "settings_section"
            contentDescription = "settings_section:" + title + " " + hint.orEmpty()
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

    private fun settingSwitch(title: String, detail: String, key: String, default: Boolean, onChanged: ((Boolean) -> Unit)? = null) =
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
                    onChanged?.invoke(checked)
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
        updatePill(enabledBadge, if (enabled) "Enabled" else "1 · Not enabled", enabled)
        updatePill(selectedBadge, if (selected) "Active" else "2 · Not selected", selected)
        setupAction?.text = when {
            !enabled -> "Enable in Android settings"
            !selected -> "Choose CrazeKeyboard"
            else -> "Open keyboard picker"
        }
    }

    private fun refreshMemoryReadout() {
        val runtime = Runtime.getRuntime()
        val usedMb = ((runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L)).toInt()
        val maxMb = (runtime.maxMemory() / (1024L * 1024L)).toInt()
        val aiState = if (CrazeLmTflitePredictor.isReady()) "Model active in RAM" else "Model sleeping (0 MB AI overhead)"
        crazeLmMemoryView?.text = "App heap: ${usedMb} MB / ${maxMb} MB  ·  $aiState"
    }

    private fun hero() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(20), dp(20), dp(18))
        background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.rgb(28, 25, 58), Color.rgb(79, 60, 173), Color.rgb(112, 74, 186))
        ).apply { cornerRadius = dp(26).toFloat() }

        val topRow = LinearLayout(this@SettingsActivity).apply { gravity = Gravity.CENTER_VERTICAL }
        val logoWrap = FrameLayout(this@SettingsActivity).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(Color.rgb(30, 27, 58))
                setStroke(dp(1), Color.argb(90, 255, 255, 255))
            }
            addView(ImageView(this@SettingsActivity).apply {
                setImageResource(R.drawable.ic_craze_keyboard)
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = "CrazeKeyboard Logo"
            }, FrameLayout.LayoutParams(dp(44), dp(44), Gravity.CENTER))
        }
        topRow.addView(logoWrap, LinearLayout.LayoutParams(dp(56), dp(56)).apply { rightMargin = dp(14) })

        val titleCol = LinearLayout(this@SettingsActivity).apply { orientation = LinearLayout.VERTICAL }
        titleCol.addView(text("CRAZESTUDIO  ·  PRIVACY INPUT", 10f, Color.rgb(218, 210, 255), true))
        titleCol.addView(text("CrazeKeyboard", 26f, Color.WHITE, true))
        titleCol.addView(text("Fast, private, low-RAM typing.", 13f, Color.rgb(236, 233, 255)))
        topRow.addView(titleCol, LinearLayout.LayoutParams(0, -2, 1f))
        addView(topRow)

        val mock = LinearLayout(this@SettingsActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(7), dp(7), dp(7), dp(7))
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(Color.argb(38, 255, 255, 255))
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
                    LinearLayout.LayoutParams(0, dp(23), 1f).apply {
                        setMargins(dp(2), 0, dp(2), dp(3))
                    }
                )
            }
            mock.addView(keys)
        }
        addView(mock, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })

        val badges = LinearLayout(this@SettingsActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(12), 0, 0)
        }
        listOf("OFFLINE-FIRST", "ZERO TELEMETRY", "LOW-RAM AI").forEachIndexed { idx, tag ->
            badges.addView(TextView(this@SettingsActivity).apply {
                text = tag
                textSize = 9.5f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    cornerRadius = dp(20).toFloat()
                    setColor(Color.argb(46, 255, 255, 255))
                }
                setPadding(dp(9), dp(5), dp(9), dp(5))
            }, LinearLayout.LayoutParams(-2, -2).apply { if (idx > 0) leftMargin = dp(6) })
        }
        addView(badges)
    }

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
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(page)
        }
        val maxWidth = minOf(resources.displayMetrics.widthPixels, dp(600))
        shell.addView(root, FrameLayout.LayoutParams(maxWidth, -1, Gravity.CENTER_HORIZONTAL))

        val scroll = ScrollView(this).apply {
            clipToPadding = false
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(28))
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        content.addView(hero(), margin())

        // ---- Setup ----
        val status = section("Keyboard setup", "Enable and select CrazeKeyboard to use it in every app.")
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
        val setupLinks = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, 0)
        }
        setupLinks.addView(action("Input settings") {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(4) })
        setupLinks.addView(action("Switch input") {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(4) })
        status.addView(setupLinks)
        content.addView(category("Setup & testing"))
        content.addView(status, margin())

        // ---- Interactive Tester ----
        val test = section("Test your keyboard", "Tap the box to test typing, swipe-backspace, numpad, and suggestions.")
        val testInput = EditText(this).apply {
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
        }
        test.addView(testInput)
        val testActions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, 0)
        }
        testActions.addView(action("Open keyboard", true) {
            testInput.requestFocus()
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                .showSoftInput(testInput, InputMethodManager.SHOW_IMPLICIT)
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(4) })
        testActions.addView(action("Clear") {
            testInput.setText("")
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(4) })
        test.addView(testActions)
        content.addView(test, margin())

        // ---- AI & Low-RAM Control ----
        val crazeLm = section(
            "CrazeLM · Low-RAM Offline AI",
            "On-device 30M next-word prediction with memory-mapped weights. Low-RAM mode sleeps the model automatically when the keyboard closes."
        )
        val modelStatus = text(CrazeLmTflitePredictor.status, 11.5f, ink, true)
        crazeLmStatus = modelStatus
        crazeLm.addView(modelStatus)
        val memReadout = text("", 11f, muted).apply { setPadding(0, dp(3), 0, dp(8)) }
        crazeLmMemoryView = memReadout
        crazeLm.addView(memReadout)
        refreshMemoryReadout()

        crazeLm.addView(settingSwitch(
            "Low-RAM AI mode",
            "Skips heavy XNNPACK scratch caches and auto-unloads AI after 30s idle.",
            "lowRamAi",
            true
        ) {
            CrazeLmTflitePredictor.unloadNow(applicationContext)
            refreshMemoryReadout()
        })

        val aiButtons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, 0)
        }
        aiButtons.addView(action("Download / Load AI", true) {
            startCrazeLmDownload()
        }, LinearLayout.LayoutParams(0, -2, 1.3f).apply { rightMargin = dp(4) })
        aiButtons.addView(action("Unload RAM") {
            CrazeLmTflitePredictor.unloadNow(applicationContext)
            refreshMemoryReadout()
            Toast.makeText(this, "AI model released from RAM", Toast.LENGTH_SHORT).show()
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(4) })
        crazeLm.addView(aiButtons)
        content.addView(category("AI & voice"))
        content.addView(crazeLm, margin())

        val voice = section(
            "Voice typing",
            "Uses Android's selected on-device or system speech recognizer."
        )
        val voiceGranted = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val voiceButton = action(
            if (voiceGranted) "Microphone permission enabled" else "Enable microphone permission",
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

        // ---- Languages ----
        val packsSection = section(
            "Language packs",
            "Download verified offline vocabulary dictionaries."
        )
        val packsRows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        packsSection.addView(packsRows)
        fun addPackRow(info: LanguageInfo) {
            val installed = LanguagePackManager.isInstalled(this, info.code)
            val selected = Prefs.string(this, "activeLanguage", "en") == info.code
            val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(8), 0, dp(8)) }
            val details = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            details.addView(text(info.name, 14f, ink, true))
            val statusText = when {
                installed && selected && info.modelAvailable -> "Selected · offline vocabulary · CrazeLM ready"
                installed && selected && info.suggestionsEnabled -> "Selected · offline prefix suggestions"
                installed && selected -> "Selected · vocabulary only"
                installed && info.modelAvailable -> "Installed · CrazeLM supported"
                installed && info.suggestionsEnabled -> "Installed · local prefix suggestions"
                installed -> "Installed"
                info.vocabularyAvailable -> "Available to download · " + info.script + " script"
                else -> "Pack not published yet · " + info.script + " script"
            }
            val statusView = text(statusText, 11f, muted)
            details.addView(statusView)
            row.addView(details)
            val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(7), 0, 0) }
            if (info.vocabularyAvailable || installed) {
                actions.addView(action(if (installed) "Update pack" else "Download", true) {
                    statusView.text = "Downloading and verifying SHA-256…"
                    LanguagePackManager.download(this@SettingsActivity, info.code) { success, message ->
                        runOnUiThread { statusView.text = if (success) "$message · offline ready" else "Failed: $message" }
                    }
                }, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(6) })
            }
            actions.addView(action(if (selected) "Selected" else "Use dictionary") {
                Prefs.setString(this@SettingsActivity, "activeLanguage", info.code)
                Toast.makeText(this@SettingsActivity, info.name + " selected as active dictionary", Toast.LENGTH_SHORT).show()
                recreate()
            }, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(actions)
            packsRows.addView(row)
            packsRows.addView(divider())
        }
        val fallbackLanguages = listOf(
            LanguageInfo("en", "English", "latin", "ltr", true, true, true),
            LanguageInfo("bn", "Bengali", "bengali", "ltr", true, true, false),
            LanguageInfo("hi", "Hindi", "devanagari", "ltr", true, true, false)
        )
        fallbackLanguages.forEach(::addPackRow)
        content.addView(category("Languages & downloads"))
        content.addView(packsSection, margin())
        LanguagePackManager.fetchLanguages(this) { success, catalog, error ->
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (success && catalog.isNotEmpty()) {
                    packsRows.removeAllViews()
                    catalog.forEach(::addPackRow)
                } else if (error.isNotBlank()) {
                    packsRows.addView(text("Offline mode · catalog refresh skipped", 11f, muted))
                }
            }
        }

        // ---- Typing Preferences ----
        val prefs = section("Typing preferences", "Customise typing intelligence, gestures, and feedback.")
        prefs.addView(settingSwitch("Number row", "Keep digits visible above letters.", "numrow", true))
        prefs.addView(divider())
        prefs.addView(settingSwitch("Number hints on letters", "Long-press top letter keys for digits 1–0.", "numberHints", true))
        prefs.addView(divider())
        prefs.addView(settingSwitch("Word suggestions", "Show completions and spelling alternatives.", "suggest", true))
        prefs.addView(divider())
        prefs.addView(settingSwitch("Auto-correction", "Correct close spelling mistakes on space.", "autocorrect", true))
        prefs.addView(divider())
        prefs.addView(settingSwitch("Next-word suggestions", "Predict likely next words after space.", "nextWord", true))
        prefs.addView(divider())
        prefs.addView(settingSwitch("Personalized predictions", "Learn confirmed word patterns locally on-device.", "personalized", true))
        prefs.addView(divider())
        prefs.addView(settingSwitch("Swipe-left backspace", "Slide left from Backspace to delete whole words.", "swipeBackspace", true))
        prefs.addView(divider())
        prefs.addView(settingSwitch("Swipe typing", "Glide across letter keys to enter words.", "swipeTyping", false))
        prefs.addView(divider())
        prefs.addView(settingSwitch("Auto-capitalization", "Capitalize the first word of each sentence.", "autoCap", true))
        prefs.addView(divider())
        prefs.addView(settingSwitch("Double-space period", "Turn two quick spaces into a full stop.", "doubleSpacePeriod", true))
        prefs.addView(divider())
        prefs.addView(settingSwitch("Key popup preview", "Show magnified key preview while typing.", "keyPopup", true))
        prefs.addView(divider())
        prefs.addView(settingSwitch("Haptic feedback", "Light vibration on key press.", "haptics", true))
        prefs.addView(divider())
        prefs.addView(settingSwitch("Key sounds", "Play a soft click on key press.", "keysound", false))
        content.addView(category("Typing & suggestions"))
        content.addView(prefs, margin())

        // ---- Customisable Toolbar ----
        val toolbarSection = section(
            "Customise keyboard tools",
            "Top 5 enabled tools appear on the quick bar; all enabled tools appear in the Tools menu."
        )
        val previewStrip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = shape(fieldBg, 14)
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }
        toolbarSection.addView(previewStrip, LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(10) })

        val toolbarRows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val toolOptions = linkedMapOf(
            "clipboard" to ("Clipboard" to R.drawable.ic_clip),
            "emoji" to ("Emoji" to R.drawable.ic_emoji),
            "voice" to ("Voice typing" to R.drawable.ic_mic),
            "incognito" to ("Private mode" to R.drawable.ic_shield_private),
            "editor" to ("Edit text" to R.drawable.ic_edit),
            "numpad" to ("Numpad" to R.drawable.ic_numpad),
            "theme" to ("Theme" to R.drawable.ic_palette),
            "onehand" to ("One-handed mode" to R.drawable.ic_onehand),
            "floating" to ("Floating / dock" to R.drawable.ic_float),
            "resize" to ("Keyboard resizer" to R.drawable.ic_grip),
            "settings" to ("Settings" to R.drawable.ic_settings),
            "fulleditor" to ("Full editor" to R.drawable.ic_editor_select_all),
            "keyboard" to ("Show keyboard" to R.drawable.ic_activity),
            "hide" to ("Hide keyboard" to R.drawable.ic_hide)
        )
        fun renderToolbarPreview(active: List<String>) {
            previewStrip.removeAllViews()
            previewStrip.addView(ImageView(this).apply {
                setImageResource(R.drawable.ic_menu)
                setColorFilter(accent)
                setPadding(dp(6), dp(6), dp(6), dp(6))
            }, LinearLayout.LayoutParams(dp(34), dp(34)).apply { rightMargin = dp(6) })
            active.take(5).forEach { id ->
                val iconRes = toolOptions[id]?.second ?: R.drawable.ic_menu
                previewStrip.addView(ImageView(this).apply {
                    setImageResource(iconRes)
                    setColorFilter(ink)
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                    setPadding(dp(6), dp(6), dp(6), dp(6))
                }, LinearLayout.LayoutParams(0, dp(34), 1f))
            }
        }
        fun renderToolbarRows() {
            toolbarRows.removeAllViews()
            val active = Prefs.toolbarTools(this).toMutableList()
            renderToolbarPreview(active)
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
                    setColorFilter(if (enabled) accent else muted)
                    setPadding(dp(5), dp(5), dp(5), dp(5))
                    contentDescription = option.first
                }, LinearLayout.LayoutParams(dp(34), dp(34)))
                row.addView(text(option.first, 13.5f, ink, enabled),
                    LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(8) })
                if (enabled) {
                    val index = active.indexOf(id)
                    row.addView(iconAction(R.drawable.ic_arrow_up, "Move ${option.first} up", index > 0) {
                        val list = Prefs.toolbarTools(this@SettingsActivity).toMutableList()
                        if (index > 0) {
                            val item = list.removeAt(index)
                            list.add(index - 1, item)
                            Prefs.setToolbarTools(this@SettingsActivity, list)
                            renderToolbarRows()
                        }
                    }, LinearLayout.LayoutParams(dp(40), dp(40)).apply { rightMargin = dp(4) })
                    row.addView(iconAction(R.drawable.ic_arrow_down, "Move ${option.first} down", index < active.lastIndex) {
                        val list = Prefs.toolbarTools(this@SettingsActivity).toMutableList()
                        if (index < list.lastIndex) {
                            val item = list.removeAt(index)
                            list.add(index + 1, item)
                            Prefs.setToolbarTools(this@SettingsActivity, list)
                            renderToolbarRows()
                        }
                    }, LinearLayout.LayoutParams(dp(40), dp(40)).apply { rightMargin = dp(6) })
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
        content.addView(category("Toolbar & shortcuts"))
        content.addView(toolbarSection, margin())

        // ---- Privacy & Data ----
        val privacy = section(
            "Privacy & data",
            "100% local storage. Password fields and Private Mode never save words or clipboard snippets."
        )
        privacy.addView(settingSwitch("Private typing mode", "Pause all learning and clipboard capture across apps.", "manualIncognito", false))
        privacy.addView(divider())
        privacy.addView(settingSwitch("Clipboard history", "Save copied text locally for quick pasting.", "clipboardHistory", true))
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
        content.addView(category("Privacy & data"))
        content.addView(privacy, margin())

        // ---- Appearance ----
        val appearance = section("Appearance", "Select the colour theme used by your keyboard.")
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
        content.addView(category("Appearance"))
        content.addView(appearance, margin())

        // ---- Keyboard Size & Layout ----
        val resizeSection = section(
            "Keyboard studio",
            "Adjust floating width, one-handed width, and key height independently."
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
                }, LinearLayout.LayoutParams(0, dp(23), 1f).apply { setMargins(dp(1), 0, dp(1), 0) })
            }
            previewBoard.addView(row, LinearLayout.LayoutParams(-1, dp(23)).apply { bottomMargin = dp(4) })
            previewRows.add(row)
        }
        previewFrame.addView(previewBoard, FrameLayout.LayoutParams(dp(270), -2, Gravity.CENTER))
        resizeSection.addView(previewFrame, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })

        val widthHeader = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(text("Floating keyboard width", 14f, ink, true), LinearLayout.LayoutParams(0, -2, 1f))
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
        resizeSection.addView(text("55% — 100%  ·  Applies in floating mode", 11f, muted).apply {
            setPadding(0, dp(1), 0, dp(10))
        })

        val oneHandWidthHeader = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(text("One-handed width", 14f, ink, true), LinearLayout.LayoutParams(0, -2, 1f))
        }
        val oneHandWidthValue = text("${Prefs.oneHandWidth(this)}%", 13f, accent, true)
        oneHandWidthHeader.addView(oneHandWidthValue)
        resizeSection.addView(oneHandWidthHeader)
        val oneHandWidthSeek = SeekBar(this).apply {
            max = 30
            progress = Prefs.oneHandWidth(this@SettingsActivity) - 60
            progressTintList = ColorStateList.valueOf(accent)
            thumbTintList = ColorStateList.valueOf(accent)
        }
        resizeSection.addView(oneHandWidthSeek, LinearLayout.LayoutParams(-1, -2))
        resizeSection.addView(text("60% — 90%  ·  Independent of floating width", 11f, muted).apply {
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

        fun updateKeyboardPreview(useOneHandWidth: Boolean = Prefs.i(this, "onehand", 0) != 0) {
            val width = if (useOneHandWidth) Prefs.oneHandWidth(this) else Prefs.keyWidth(this)
            val height = Prefs.keyH(this)
            widthValue.text = "${Prefs.keyWidth(this)}%"
            oneHandWidthValue.text = "${Prefs.oneHandWidth(this)}%"
            heightValue.text = "$height dp"
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
                updateKeyboardPreview(false)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
        oneHandWidthSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                Prefs.setI(this@SettingsActivity, "onehandWidth", progress + 60)
                updateKeyboardPreview(true)
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
                            if (horizontal) {
                                if (Prefs.i(this@SettingsActivity, "onehand", 0) != 0) {
                                    oneHandWidthSeek.progress = (oneHandWidthSeek.progress + delta).coerceIn(0, 30)
                                } else {
                                    widthSeek.progress = (widthSeek.progress + delta).coerceIn(0, 45)
                                }
                            } else heightSeek.progress = (heightSeek.progress + delta).coerceIn(0, 30)
                            view.tag = now
                            true
                        }
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
            oneHandWidthSeek.progress = 18
            heightSeek.progress = 12
            Prefs.setI(this@SettingsActivity, "onehand", 0)
            Prefs.setB(this@SettingsActivity, "float", false)
            Prefs.setI(this@SettingsActivity, "fx", 0)
            Prefs.setI(this@SettingsActivity, "fy", 0)
            updateKeyboardPreview()
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(5) })
        resizeActions.addView(action("Float", false) {
            Prefs.setB(this@SettingsActivity, "float", true)
            Toast.makeText(this@SettingsActivity, "Floating mode enabled. Drag top grip to move.", Toast.LENGTH_LONG).show()
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(5); rightMargin = dp(5) })
        resizeActions.addView(action("Done", true) {
            Toast.makeText(this@SettingsActivity, "Keyboard size saved", Toast.LENGTH_SHORT).show()
            finish()
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(5) })
        resizeSection.addView(resizeActions, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })

        resizeSection.addView(text("Quick height presets", 13f, ink, true).apply { setPadding(0, dp(10), 0, dp(8)) })
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
        content.addView(category("Keyboard size & layout"))
        content.addView(resizeSection, margin())
        previewFrame.post { updateKeyboardPreview() }

        val layout = section("Keyboard modes", "Choose how the keyboard sits above the app.")
        layout.addView(settingSwitch("Floating keyboard", "Move the keyboard freely without resizing the app.", "float", false))
        layout.addView(divider())
        layout.addView(text("One-handed layout", 14f, ink, true).apply { setPadding(0, dp(10), 0, dp(8)) })
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
                updateKeyboardPreview(value != 0)
            }
            handButtons.add(value to button)
            handRow.addView(button, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(3), 0, dp(3), 0) })
        }
        layout.addView(handRow)
        refreshHandButtons()
        content.addView(category("Keyboard behaviour"))
        content.addView(layout, margin())

        content.addView(text("CrazeStudio  ·  Privacy-First Keyboard", 11f, muted).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(10))
        })

        // Search & Quick Category Navigation at top
        val settingsSearch = EditText(this).apply {
            hint = "Search settings"
            setSingleLine(true)
            textSize = 14f
            setTextColor(ink)
            setHintTextColor(muted)
            setPadding(dp(15), dp(11), dp(15), dp(11))
            background = shape(fieldBg, 14)
            inputType = InputType.TYPE_CLASS_TEXT
            tag = "settings_search"
            contentDescription = "Search all settings"
        }
        content.addView(settingsSearch, 1, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        val categoryScroller = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            tag = "settings_category_nav"
        }
        val categoryStrip = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val categoryLabels = listOf(
            "Setup & testing", "AI & voice", "Languages & downloads",
            "Typing & suggestions", "Toolbar & shortcuts", "Privacy & data",
            "Appearance", "Keyboard size & layout", "Keyboard behaviour"
        )
        categoryLabels.forEach { label ->
            val chip = TextView(this).apply {
                text = label
                textSize = 11f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(ink)
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(9), dp(12), dp(9))
                background = shape(actionBg, 18)
                isClickable = true
                isFocusable = true
                ripple(this, 18)
                setOnClickListener { settingsCategoryViews[label]?.let { target -> scroll.smoothScrollTo(0, target.top) } }
            }
            categoryStrip.addView(chip, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(6) })
        }
        categoryScroller.addView(categoryStrip)
        content.addView(categoryScroller, 2, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        settingsSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim()?.lowercase().orEmpty()
                for (i in 0 until content.childCount) {
                    val child = content.getChildAt(i)
                    when (child.tag as? String) {
                        "settings_search", "settings_category_nav" -> child.visibility = View.VISIBLE
                        "settings_section" -> {
                            val terms = child.contentDescription?.toString().orEmpty().lowercase()
                            child.visibility = if (query.isEmpty() || terms.contains(query)) View.VISIBLE else View.GONE
                        }
                        "settings_category" -> {
                            val label = child.contentDescription?.toString().orEmpty().lowercase()
                            var match = query.isEmpty() || label.contains(query)
                            var j = i + 1
                            while (!match && j < content.childCount && content.getChildAt(j).tag != "settings_category") {
                                val candidate = content.getChildAt(j)
                                if (candidate.tag == "settings_section") {
                                    match = candidate.contentDescription?.toString().orEmpty().lowercase().contains(query)
                                }
                                j++
                            }
                            child.visibility = if (match) View.VISIBLE else View.GONE
                        }
                        else -> child.visibility = if (query.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        setContentView(shell)
        content.alpha = 0f
        content.translationY = dp(8).toFloat()
        content.post {
            content.animate().alpha(1f).translationY(0f).setDuration(220L)
                .setInterpolator(android.view.animation.DecelerateInterpolator()).start()
        }
        refreshKeyboardStatus()
        CrazeLmTflitePredictor.refreshInitialStatus(applicationContext)
        refreshMemoryReadout()
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
        CrazeLmTflitePredictor.refreshInitialStatus(applicationContext)
        CrazeLmTflitePredictor.observe { message ->
            mainHandler.post {
                if (!isFinishing) {
                    crazeLmStatus?.text = message
                    refreshMemoryReadout()
                }
            }
        }
        refreshMemoryReadout()
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
            voicePermissionButton?.text = if (granted) "Microphone permission enabled" else "Enable microphone permission"
        }
    }

    private fun margin() = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(13) }

    private fun divider() = View(this).apply {
        setBackgroundColor(dividerColor)
        layoutParams = LinearLayout.LayoutParams(-1, dp(1))
    }
}
