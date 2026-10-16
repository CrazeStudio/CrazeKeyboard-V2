package com.crazestudio.crazekeyboard

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val prefs by lazy { getSharedPreferences("craze_keyboard", Context.MODE_PRIVATE) }

    // Design System Tokens (Material You Dark Slate)
    private val bg = Color.rgb(27, 28, 32)               // #1B1C20 Canvas
    private val surface = Color.rgb(44, 45, 51)          // #2C2D33 Keycap / Elevated Surface
    private val border = Color.rgb(58, 59, 68)           // #3A3B44 Hairline Border
    private val borderLight = Color.rgb(78, 81, 96)      // #4E5160 Active Border
    private val cardSurface = Color.rgb(33, 34, 38)      // #212226 Card Canvas
    private val accent = Color.rgb(26, 115, 232)         // #1A73E8 Official Google Blue
    private val accentBorder = Color.rgb(66, 133, 244)   // #4285F4
    private val textPrimary = Color.rgb(255, 255, 255)   // #FFFFFF Crisp White
    private val textSecondary = Color.rgb(158, 163, 176) // #9EA3B0 Secondary & Hints
    private val textMuted = Color.rgb(125, 130, 142)     // #7D828E
    private val dividerColor = Color.rgb(52, 54, 62)     // #34363E Hairline Divider
    private val greenActive = Color.rgb(16, 185, 129)    // #10B981 Status Active
    private val greenSurface = Color.rgb(19, 46, 36)     // #132E24 Status Pill

    private enum class Screen {
        HOME, THEMES, CUSTOMIZE, LANGUAGES, SOUND_HAPTICS, TYPING, TEST_PLAYGROUND, PRIVACY, ABOUT
    }

    private var currentScreen = Screen.HOME
    private lateinit var mainFrame: FrameLayout
    private lateinit var rootContainer: LinearLayout
    private lateinit var contentContainer: LinearLayout

    private var activeThemeCategory = "All"
    private var themeSearchQuery = ""
    private var languageSearchQuery = ""

    data class ThemeDescriptor(
        val name: String,
        val category: String,
        val description: String,
        val bg: Int,
        val keySurface: Int,
        val accent: Int,
        val textPrimary: Int = Color.WHITE
    )

    private val allThemes = listOf(
        ThemeDescriptor(
            name = "Material You Dark Slate",
            category = "Dark",
            description = "Official Gboard Material You dark slate canvas with vivid Google Blue action pill.",
            bg = Color.rgb(27, 28, 32),
            keySurface = Color.rgb(44, 45, 51),
            accent = Color.rgb(26, 115, 232)
        ),
        ThemeDescriptor(
            name = "Arctic Glass",
            category = "Arctic Glass",
            description = "Translucent frosted white keycaps with soft icy-blue accents and crisp dark lettering.",
            bg = Color.rgb(228, 238, 246),
            keySurface = Color.rgb(255, 255, 255),
            accent = Color.rgb(2, 132, 199),
            textPrimary = Color.rgb(15, 23, 42)
        ),
        ThemeDescriptor(
            name = "Arctic Glass Dark",
            category = "Arctic Glass",
            description = "Frosted icy midnight slate with translucent key surfaces and glowing cyan frost accent.",
            bg = Color.rgb(18, 26, 38),
            keySurface = Color.rgb(28, 40, 58),
            accent = Color.rgb(56, 189, 248)
        ),
        ThemeDescriptor(
            name = "Natural Charcoal",
            category = "Dark",
            description = "Deep dark charcoal canvas with rich contrast and royal Google Blue accent.",
            bg = Color.rgb(18, 19, 22),
            keySurface = Color.rgb(42, 44, 52),
            accent = Color.rgb(26, 115, 232)
        ),
        ThemeDescriptor(
            name = "Natural Chalk",
            category = "Light",
            description = "High-contrast clean light slate with deep navy Google Blue action pill.",
            bg = Color.rgb(220, 224, 232),
            keySurface = Color.rgb(255, 255, 255),
            accent = Color.rgb(21, 101, 192),
            textPrimary = Color.rgb(18, 20, 24)
        ),
        ThemeDescriptor(
            name = "Natural Obsidian",
            category = "Dark",
            description = "Deep AMOLED pitch black canvas with sharp electric azure blue highlights.",
            bg = Color.rgb(8, 8, 10),
            keySurface = Color.rgb(26, 27, 32),
            accent = Color.rgb(0, 122, 255)
        ),
        ThemeDescriptor(
            name = "Natural Slate",
            category = "Dark",
            description = "Refined midnight navy slate with rich vibrant royal blue keys.",
            bg = Color.rgb(15, 18, 26),
            keySurface = Color.rgb(32, 40, 56),
            accent = Color.rgb(37, 99, 235)
        ),
        ThemeDescriptor(
            name = "Natural Sage",
            category = "Nature",
            description = "Deep evergreen pine forest with soothing emerald green accents.",
            bg = Color.rgb(15, 22, 18),
            keySurface = Color.rgb(28, 42, 34),
            accent = Color.rgb(16, 149, 93)
        ),
        ThemeDescriptor(
            name = "Natural Warm Earth",
            category = "Nature",
            description = "Rich espresso stone with golden amber highlights for comfortable night typing.",
            bg = Color.rgb(22, 20, 17),
            keySurface = Color.rgb(42, 38, 32),
            accent = Color.rgb(217, 119, 6)
        ),
        ThemeDescriptor(
            name = "Pastel Lavender",
            category = "Pastel",
            description = "Soothing soft lavender canvas with rich violet accent pill.",
            bg = Color.rgb(243, 240, 248),
            keySurface = Color.rgb(255, 255, 255),
            accent = Color.rgb(124, 58, 237),
            textPrimary = Color.rgb(30, 27, 45)
        ),
        ThemeDescriptor(
            name = "Pastel Mint",
            category = "Pastel",
            description = "Crisp, fresh soft spearmint palette with calming deep teal accent.",
            bg = Color.rgb(238, 247, 242),
            keySurface = Color.rgb(255, 255, 255),
            accent = Color.rgb(13, 148, 136),
            textPrimary = Color.rgb(20, 36, 30)
        ),
        ThemeDescriptor(
            name = "Pastel Peach",
            category = "Pastel",
            description = "Warm soft pastel coral with vibrant sunset orange action key.",
            bg = Color.rgb(253, 244, 240),
            keySurface = Color.rgb(255, 255, 255),
            accent = Color.rgb(234, 88, 12),
            textPrimary = Color.rgb(45, 25, 20)
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = Color.rgb(22, 23, 27)
        buildRootView()
        navigateTo(Screen.HOME)
    }

    override fun onResume() {
        super.onResume()
        if (currentScreen == Screen.HOME) {
            renderHomeScreen()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (currentScreen != Screen.HOME) {
            navigateTo(Screen.HOME)
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    private fun buildRootView() {
        mainFrame = FrameLayout(this).apply {
            setBackgroundColor(bg)
            layoutParams = ViewGroup.LayoutParams(-1, -1)
        }

        rootContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            layoutParams = FrameLayout.LayoutParams(-1, -1)
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }

        contentContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(80))
        }
        scroll.addView(contentContainer)
        rootContainer.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        // On-Screen Interactive CrazeKeyboard VW Toolbar & Keypad
        rootContainer.addView(createInAppKeyboardDrawer())

        // Persistent Bottom Navigation Bar
        rootContainer.addView(createBottomNav())
        mainFrame.addView(rootContainer)

        setContentView(mainFrame)
    }

    private fun playInAppSound() {
        if (prefs.getBoolean("sound", false)) {
            val audio = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val volume = prefs.getInt("pref_sound_volume", 80) / 100f
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                audio?.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, volume)
            } else {
                audio?.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD)
            }
        }
    }

    private fun vibrateInApp(durationMs: Long = 18L) {
        if (prefs.getBoolean("haptics", true)) {
            val strength = prefs.getString("vibration_strength", "Medium") ?: "Medium"
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            } ?: return

            if (!vibrator.hasVibrator()) return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val effect = when (strength) {
                    "Light" -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                    "Strong" -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                    else -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                }
                vibrator.vibrate(effect)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val (dur, amp) = when (strength) {
                    "Light" -> 10L to 60
                    "Strong" -> 30L to 255
                    else -> 18L to 160
                }
                vibrator.vibrate(VibrationEffect.createOneShot(dur, amp))
            } else {
                @Suppress("DEPRECATION")
                val dur = when (strength) {
                    "Light" -> 10L
                    "Strong" -> 32L
                    else -> 18L
                }
                vibrator.vibrate(dur)
            }
        }
    }

    private fun createBottomNav(): View {
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.rgb(22, 23, 27))
            setPadding(dp(8), dp(8), dp(8), dp(10))
        }

        val navItems = listOf(
            Triple(Screen.HOME, "Home", R.drawable.ic_nav_home),
            Triple(Screen.THEMES, "Themes", R.drawable.ic_kb_palette),
            Triple(Screen.CUSTOMIZE, "Customize", R.drawable.ic_nav_tune),
            Triple(Screen.LANGUAGES, "Languages", R.drawable.ic_kb_globe),
            Triple(Screen.SOUND_HAPTICS, "Settings", R.drawable.ic_kb_settings)
        )

        navItems.forEach { (screen, labelStr, iconRes) ->
            val isSelected = currentScreen == screen
            val itemContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                background = flatPill(if (isSelected) surface else Color.TRANSPARENT, if (isSelected) borderLight else Color.TRANSPARENT, 14)
                setPadding(dp(6), dp(6), dp(6), dp(6))
                setOnClickListener { navigateTo(screen) }
            }

            val icon = ImageView(this).apply {
                setImageResource(iconRes)
                imageTintList = ColorStateList.valueOf(if (isSelected) accent else textSecondary)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
            }
            itemContainer.addView(icon, LinearLayout.LayoutParams(dp(22), dp(22)))

            val text = TextView(this).apply {
                this.text = labelStr
                textSize = 10.5f
                setTextColor(if (isSelected) textPrimary else textMuted)
                if (isSelected) typeface = Typeface.DEFAULT_BOLD
                setPadding(0, dp(2), 0, 0)
            }
            itemContainer.addView(text)

            nav.addView(itemContainer, LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                setMargins(dp(2), 0, dp(2), 0)
            })
        }
        return nav
    }

    private fun navigateTo(screen: Screen) {
        currentScreen = screen
        contentContainer.removeAllViews()

        // Recreate bottom nav to update active tab highlight
        if (rootContainer.childCount > 1) {
            rootContainer.removeViewAt(1)
            rootContainer.addView(createBottomNav())
        }

        when (screen) {
            Screen.HOME -> renderHomeScreen()
            Screen.THEMES -> renderThemesScreen()
            Screen.CUSTOMIZE -> renderCustomizeScreen()
            Screen.LANGUAGES -> renderLanguagesScreen()
            Screen.SOUND_HAPTICS -> renderSoundHapticsScreen()
            Screen.TYPING -> renderTypingScreen()
            Screen.TEST_PLAYGROUND -> renderPlaygroundScreen()
            Screen.PRIVACY -> renderPrivacyScreen()
            Screen.ABOUT -> renderAboutScreen()
        }
    }

    // =========================================================================
    // 1. HOME DASHBOARD SCREEN
    // =========================================================================
    private fun renderHomeScreen() {
        contentContainer.removeAllViews()

        // Brand Banner Header
        val brandRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(4))
        }
        val logoContainer = FrameLayout(this).apply {
            background = flatPill(accent, accentBorder, 14)
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        val logoIcon = ImageView(this).apply {
            setImageResource(R.drawable.ic_kb_keyboard)
            imageTintList = ColorStateList.valueOf(Color.WHITE)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }
        logoContainer.addView(logoIcon, FrameLayout.LayoutParams(dp(24), dp(24)))
        brandRow.addView(logoContainer)

        val brandCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
            addView(label("CrazeKeyboard", 22f, textPrimary, true))
            addView(label("v2.1 • Gboard Edition", 12.5f, textSecondary, false))
        }
        brandRow.addView(brandCol, LinearLayout.LayoutParams(0, -2, 1f))

        val isEnabled = isImeEnabled()
        val isSelected = isImeSelected()

        val statusBadge = TextView(this).apply {
            text = if (isEnabled && isSelected) "READY" else "SETUP"
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (isEnabled && isSelected) greenActive else accent)
            background = flatPill(if (isEnabled && isSelected) greenSurface else surface, border, 10)
            setPadding(dp(10), dp(4), dp(10), dp(4))
        }
        brandRow.addView(statusBadge)
        contentContainer.addView(brandRow)
        contentContainer.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(-1, dp(14)) })

        // 1. Setup Status Stepper Card (if not ready)
        if (!isEnabled || !isSelected) {
            contentContainer.addView(card {
                val statusHeader = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                val statusIcon = ImageView(this@MainActivity).apply {
                    setImageResource(R.drawable.ic_ui_info)
                    imageTintList = ColorStateList.valueOf(accent)
                }
                statusHeader.addView(statusIcon, LinearLayout.LayoutParams(dp(20), dp(20)))

                val statusTitle = TextView(this@MainActivity).apply {
                    text = "  Setup Required"
                    textSize = 15.5f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(textPrimary)
                }
                statusHeader.addView(statusTitle, LinearLayout.LayoutParams(0, -2, 1f))
                addView(statusHeader)

                addView(label("Complete two simple steps to activate CrazeKeyboard on your device:", 13f, textSecondary, false).apply {
                    setPadding(0, dp(8), 0, dp(12))
                })

                // Step 1
                val step1Row = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(4), 0, dp(8))
                }
                step1Row.addView(stepPill(1, isEnabled))
                val step1Text = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(label("Enable in System Settings", 14f, textPrimary, true))
                    addView(label(if (isEnabled) "Already enabled" else "Turn on CrazeKeyboard in Android", 12f, textSecondary, false))
                }
                step1Row.addView(step1Text, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(10), 0, dp(10), 0) })
                step1Row.addView(button(if (isEnabled) "Done" else "Enable") {
                    startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                })
                addView(step1Row)

                // Step 2
                val step2Row = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(4), 0, 0)
                }
                step2Row.addView(stepPill(2, isSelected))
                val step2Text = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(label("Select Input Method", 14f, textPrimary, true))
                    addView(label(if (isSelected) "Active input method" else "Choose CrazeKeyboard as default", 12f, textSecondary, false))
                }
                step2Row.addView(step2Text, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(10), 0, dp(10), 0) })
                step2Row.addView(button(if (isSelected) "Active" else "Switch") {
                    (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
                })
                addView(step2Row)
            })
        } else {
            // Already active confirmation pill
            contentContainer.addView(card {
                val row = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                val checkIcon = ImageView(this@MainActivity).apply {
                    setImageResource(R.drawable.ic_ui_check_circle)
                    imageTintList = ColorStateList.valueOf(greenActive)
                }
                row.addView(checkIcon, LinearLayout.LayoutParams(dp(22), dp(22)))

                val col = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(10), 0, dp(10), 0)
                    addView(label("CrazeKeyboard is Active & Ready", 15f, greenActive, true))
                    addView(label("Default input method with independent layer styling", 12.5f, textSecondary, false))
                }
                row.addView(col, LinearLayout.LayoutParams(0, -2, 1f))

                val switchBtn = Button(this@MainActivity).apply {
                    text = "Switch"
                    textSize = 12f
                    isAllCaps = false
                    setTextColor(textPrimary)
                    background = flatPill(surface, border, 12)
                    setOnClickListener {
                        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
                    }
                }
                row.addView(switchBtn, LinearLayout.LayoutParams(-2, dp(36)))
                addView(row)
            })
        }

        // 2. Interactive Test Bench Card
        contentContainer.addView(sectionHeader("INTERACTIVE TEST BENCH", "Tap to open the keyboard and experience fast tactile typing"))
        contentContainer.addView(renderQuickTypingCard())

        // 3. 1:1 Authentic Keyboard Showcase Card
        contentContainer.addView(sectionHeader("KEYBOARD SHOWCASE", "Official Gboard Dark Slate layout & unified top strip"))
        contentContainer.addView(renderKeyboardShowcaseCard())

        // 4. Settings Categories List (M3 Rows with SVG icons)
        contentContainer.addView(sectionHeader("SETTINGS & PREFERENCES", "Customize appearance, layouts, and typing behavior"))
        contentContainer.addView(renderSettingsListCard())
    }

    private fun renderQuickTypingCard(): View = card {
        val testInput = EditText(this@MainActivity).apply {
            hint = "Tap here to test typing with CrazeKeyboard..."
            setHintTextColor(textMuted)
            setTextColor(textPrimary)
            textSize = 14.5f
            background = flatPill(surface, border, 12)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        addView(testInput, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })

        val bottomRow = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val charCounter = TextView(this@MainActivity).apply {
            text = "0 characters"
            textSize = 12f
            setTextColor(textSecondary)
        }
        bottomRow.addView(charCounter, LinearLayout.LayoutParams(0, -2, 1f))

        testInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val len = s?.length ?: 0
                val words = s?.toString().orEmpty().trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }.size
                charCounter.text = "$len chars • $words words"
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        val clearBtn = Button(this@MainActivity).apply {
            text = "Clear"
            textSize = 12f
            isAllCaps = false
            setTextColor(textSecondary)
            background = flatPill(surface, border, 12)
            setOnClickListener { testInput.setText("") }
        }
        bottomRow.addView(clearBtn, LinearLayout.LayoutParams(-2, dp(34)).apply { rightMargin = dp(6) })

        val copyBtn = Button(this@MainActivity).apply {
            text = "Copy"
            textSize = 12f
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = flatPill(accent, accentBorder, 12)
            setOnClickListener {
                val text = testInput.text.toString()
                if (text.isNotEmpty()) {
                    val clip = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clip.setPrimaryClip(ClipData.newPlainText("CrazeKeyboard", text))
                    Toast.makeText(this@MainActivity, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                }
            }
        }
        bottomRow.addView(copyBtn, LinearLayout.LayoutParams(-2, dp(34)))
        addView(bottomRow)
    }

    private fun renderKeyboardShowcaseCard(): View = card {
        val activeThemeDesc = getActiveThemeDescriptor()

        // Card Header Row
        val headerRow = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(10))
        }
        val paletteIcon = ImageView(this@MainActivity).apply {
            setImageResource(R.drawable.ic_kb_palette)
            imageTintList = ColorStateList.valueOf(accent)
        }
        headerRow.addView(paletteIcon, LinearLayout.LayoutParams(dp(20), dp(20)))

        val titleCol = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), 0, dp(8), 0)
            addView(label(activeThemeDesc.name, 15.5f, textPrimary, true))
            addView(label(activeThemeDesc.description, 12f, textSecondary, false))
        }
        headerRow.addView(titleCol, LinearLayout.LayoutParams(0, -2, 1f))

        val themeBadge = TextView(this@MainActivity).apply {
            text = activeThemeDesc.category
            textSize = 10f
            setTextColor(accent)
            background = flatPill(surface, border, 8)
            setPadding(dp(8), dp(3), dp(8), dp(3))
        }
        headerRow.addView(themeBadge)
        addView(headerRow)

        // Authentic Visual Keyboard Replica Container
        val keyboardContainer = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            val cornerR = dp(14).toFloat()
            background = GradientDrawable().apply {
                setColor(activeThemeDesc.bg)
                cornerRadii = floatArrayOf(
                    cornerR, cornerR,
                    cornerR, cornerR,
                    0f, 0f,
                    0f, 0f
                )
                setStroke(dp(1), border)
            }
            setPadding(dp(6), dp(6), dp(6), dp(8))
        }

        // 1. Top Grabber Handle Pill
        val grabber = View(this@MainActivity).apply {
            background = flatPill(dividerColor, dividerColor, 3)
        }
        keyboardContainer.addView(grabber, LinearLayout.LayoutParams(dp(36), dp(4)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(4)
        })

        // 2. Unified Top Strip (Tools Toggle | Candidate Suggestions / Tools Strip | Mic)
        var showcaseToolsMode = false
        val strip = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(-1, dp(34))
        }

        val toolsToggleIcon = ImageView(this@MainActivity).apply {
            setImageResource(R.drawable.ic_kb_tools)
            imageTintList = ColorStateList.valueOf(textSecondary)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(2), dp(2), dp(2), dp(2))
        }

        val centerFrame = FrameLayout(this@MainActivity).apply {
            layoutParams = LinearLayout.LayoutParams(0, -1, 1f)
        }

        val suggestionsStrip = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = FrameLayout.LayoutParams(-1, -1)
        }

        val cand1 = TextView(this@MainActivity).apply {
            text = "the"
            textSize = 12f
            setTextColor(textSecondary)
            gravity = Gravity.CENTER
            setOnClickListener { vibrateInApp(10L); playInAppSound(); Toast.makeText(this@MainActivity, "Selected word: the", Toast.LENGTH_SHORT).show() }
        }
        suggestionsStrip.addView(cand1, LinearLayout.LayoutParams(0, -1, 1f))

        suggestionsStrip.addView(View(this@MainActivity).apply {
            setBackgroundColor(dividerColor)
        }, LinearLayout.LayoutParams(dp(1), dp(14)))

        val cand2 = TextView(this@MainActivity).apply {
            text = "keyboard"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setOnClickListener { vibrateInApp(10L); playInAppSound(); Toast.makeText(this@MainActivity, "Selected word: keyboard", Toast.LENGTH_SHORT).show() }
        }
        suggestionsStrip.addView(cand2, LinearLayout.LayoutParams(0, -1, 1.3f))

        suggestionsStrip.addView(View(this@MainActivity).apply {
            setBackgroundColor(dividerColor)
        }, LinearLayout.LayoutParams(dp(1), dp(14)))

        val cand3 = TextView(this@MainActivity).apply {
            text = "smart"
            textSize = 12f
            setTextColor(textSecondary)
            gravity = Gravity.CENTER
            setOnClickListener { vibrateInApp(10L); playInAppSound(); Toast.makeText(this@MainActivity, "Selected word: smart", Toast.LENGTH_SHORT).show() }
        }
        suggestionsStrip.addView(cand3, LinearLayout.LayoutParams(0, -1, 1f))

        val toolsStrip = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = FrameLayout.LayoutParams(-1, -1)
            visibility = View.GONE
        }

        val toolIcons = listOf(
            Triple(R.drawable.ic_kb_emoji, "Emoji Panel") { Toast.makeText(this@MainActivity, "Emoji Tool Activated", Toast.LENGTH_SHORT).show() },
            Triple(R.drawable.ic_kb_clipboard, "Clipboard") { Toast.makeText(this@MainActivity, "Clipboard Tool Activated", Toast.LENGTH_SHORT).show() },
            Triple(R.drawable.ic_kb_edit, "Text Editor") { Toast.makeText(this@MainActivity, "Text Editor Tool Activated", Toast.LENGTH_SHORT).show() },
            Triple(R.drawable.ic_kb_palette, "Switch Theme") {
                val names = allThemes.map { it.name }
                val curr = prefs.getString("theme", "Material You Dark Slate")
                val next = names[(names.indexOf(curr) + 1) % names.size]
                prefs.edit().putString("theme", next).apply()
                renderHomeScreen()
            },
            Triple(R.drawable.ic_kb_settings, "Settings") { navigateTo(Screen.SOUND_HAPTICS) }
        )

        toolIcons.forEach { (iconRes, name, action) ->
            val toolBtn = ImageView(this@MainActivity).apply {
                setImageResource(iconRes)
                imageTintList = ColorStateList.valueOf(textSecondary)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setPadding(dp(4), dp(2), dp(4), dp(2))
                setOnClickListener {
                    vibrateInApp(12L)
                    playInAppSound()
                    action()
                }
            }
            toolsStrip.addView(toolBtn, LinearLayout.LayoutParams(0, -1, 1f))
        }

        centerFrame.addView(suggestionsStrip)
        centerFrame.addView(toolsStrip)

        toolsToggleIcon.setOnClickListener {
            vibrateInApp(12L)
            playInAppSound()
            showcaseToolsMode = !showcaseToolsMode
            if (showcaseToolsMode) {
                toolsToggleIcon.setImageResource(R.drawable.ic_kb_chevron_left)
                toolsToggleIcon.imageTintList = ColorStateList.valueOf(accent)
                suggestionsStrip.visibility = View.GONE
                toolsStrip.visibility = View.VISIBLE
            } else {
                toolsToggleIcon.setImageResource(R.drawable.ic_kb_tools)
                toolsToggleIcon.imageTintList = ColorStateList.valueOf(textSecondary)
                suggestionsStrip.visibility = View.VISIBLE
                toolsStrip.visibility = View.GONE
            }
        }

        strip.addView(toolsToggleIcon, LinearLayout.LayoutParams(dp(28), dp(28)))
        strip.addView(centerFrame)
        val micIcon = ImageView(this@MainActivity).apply {
            setImageResource(R.drawable.ic_kb_mic)
            imageTintList = ColorStateList.valueOf(textSecondary)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }
        strip.addView(micIcon, LinearLayout.LayoutParams(dp(28), dp(28)))
        keyboardContainer.addView(strip)

        // 3. Realistic QWERTY Keycap Rows
        val row1Keys = listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")
        val r1 = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        row1Keys.forEach { k ->
            r1.addView(TextView(this@MainActivity).apply {
                text = k
                textSize = 11f
                setTextColor(activeThemeDesc.textPrimary)
                gravity = Gravity.CENTER
                background = flatPill(activeThemeDesc.keySurface, border, 5)
            }, LinearLayout.LayoutParams(0, dp(26), 1f).apply { setMargins(dp(1), dp(1), dp(1), dp(1)) })
        }
        keyboardContainer.addView(r1)

        val row2Keys = listOf("a", "s", "d", "f", "g", "h", "j", "k", "l")
        val r2 = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        row2Keys.forEach { k ->
            r2.addView(TextView(this@MainActivity).apply {
                text = k
                textSize = 11f
                setTextColor(activeThemeDesc.textPrimary)
                gravity = Gravity.CENTER
                background = flatPill(activeThemeDesc.keySurface, border, 5)
            }, LinearLayout.LayoutParams(0, dp(26), 1f).apply { setMargins(dp(1), dp(1), dp(1), dp(1)) })
        }
        keyboardContainer.addView(r2)

        val r3 = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        r3.addView(ImageView(this@MainActivity).apply {
            setImageResource(R.drawable.ic_kb_shift)
            imageTintList = ColorStateList.valueOf(activeThemeDesc.textPrimary)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            background = flatPill(cardSurface, border, 5)
        }, LinearLayout.LayoutParams(0, dp(26), 1.4f).apply { setMargins(dp(1), dp(1), dp(1), dp(1)) })

        listOf("z", "x", "c", "v", "b", "n", "m").forEach { k ->
            r3.addView(TextView(this@MainActivity).apply {
                text = k
                textSize = 11f
                setTextColor(activeThemeDesc.textPrimary)
                gravity = Gravity.CENTER
                background = flatPill(activeThemeDesc.keySurface, border, 5)
            }, LinearLayout.LayoutParams(0, dp(26), 1f).apply { setMargins(dp(1), dp(1), dp(1), dp(1)) })
        }

        r3.addView(ImageView(this@MainActivity).apply {
            setImageResource(R.drawable.ic_kb_backspace)
            imageTintList = ColorStateList.valueOf(activeThemeDesc.textPrimary)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            background = flatPill(cardSurface, border, 5)
        }, LinearLayout.LayoutParams(0, dp(26), 1.5f).apply { setMargins(dp(1), dp(1), dp(1), dp(1)) })
        keyboardContainer.addView(r3)

        // Bottom action row
        val r4 = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        r4.addView(TextView(this@MainActivity).apply {
            text = "?123"
            textSize = 9.5f
            setTextColor(activeThemeDesc.textPrimary)
            gravity = Gravity.CENTER
            background = flatPill(cardSurface, border, 5)
        }, LinearLayout.LayoutParams(0, dp(26), 1.4f).apply { setMargins(dp(1), dp(1), dp(1), dp(1)) })

        r4.addView(TextView(this@MainActivity).apply {
            text = ","
            textSize = 11f
            setTextColor(activeThemeDesc.textPrimary)
            gravity = Gravity.CENTER
            background = flatPill(activeThemeDesc.keySurface, border, 5)
        }, LinearLayout.LayoutParams(0, dp(26), 1f).apply { setMargins(dp(1), dp(1), dp(1), dp(1)) })

        r4.addView(TextView(this@MainActivity).apply {
            text = "space"
            textSize = 10f
            setTextColor(textSecondary)
            gravity = Gravity.CENTER
            background = flatPill(activeThemeDesc.keySurface, border, 5)
        }, LinearLayout.LayoutParams(0, dp(26), 4.5f).apply { setMargins(dp(1), dp(1), dp(1), dp(1)) })

        r4.addView(TextView(this@MainActivity).apply {
            text = "."
            textSize = 11f
            setTextColor(activeThemeDesc.textPrimary)
            gravity = Gravity.CENTER
            background = flatPill(activeThemeDesc.keySurface, border, 5)
        }, LinearLayout.LayoutParams(0, dp(26), 1f).apply { setMargins(dp(1), dp(1), dp(1), dp(1)) })

        r4.addView(ImageView(this@MainActivity).apply {
            setImageResource(R.drawable.ic_kb_check)
            imageTintList = ColorStateList.valueOf(Color.WHITE)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            background = flatPill(activeThemeDesc.accent, activeThemeDesc.accent, 5)
        }, LinearLayout.LayoutParams(0, dp(26), 1.5f).apply { setMargins(dp(1), dp(1), dp(1), dp(1)) })
        keyboardContainer.addView(r4)

        addView(keyboardContainer)

        // Action Buttons Row Below Preview
        val btnRow = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(12), 0, 0)
        }
        val themesBtn = Button(this@MainActivity).apply {
            text = "Theme Gallery"
            textSize = 12.5f
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = flatPill(accent, accentBorder, 12)
            setOnClickListener { navigateTo(Screen.THEMES) }
        }
        btnRow.addView(themesBtn, LinearLayout.LayoutParams(0, dp(40), 1f).apply { rightMargin = dp(6) })

        val customizeBtn = Button(this@MainActivity).apply {
            text = "Customize Keys"
            textSize = 12.5f
            isAllCaps = false
            setTextColor(textPrimary)
            background = flatPill(surface, border, 12)
            setOnClickListener { navigateTo(Screen.CUSTOMIZE) }
        }
        btnRow.addView(customizeBtn, LinearLayout.LayoutParams(0, dp(40), 1f))
        addView(btnRow)
    }

    private fun renderSettingsListCard(): View = card {
        val settingsItems = listOf(
            Triple("Theme Gallery", "Arctic Glass, Material You & 10 natural palettes", R.drawable.ic_kb_palette) to { navigateTo(Screen.THEMES) },
            Triple("Keypad Customization", "Key height, corner radius, spacing, borders", R.drawable.ic_nav_tune) to { navigateTo(Screen.CUSTOMIZE) },
            Triple("Languages & Layouts", "English, Bengali, Spanish, French, German", R.drawable.ic_kb_globe) to { navigateTo(Screen.LANGUAGES) },
            Triple("Sound & Vibration", "Tactile haptic feedback and click audio", R.drawable.ic_ui_vibrate) to { navigateTo(Screen.SOUND_HAPTICS) },
            Triple("Typing Preferences", "Predictions, auto-capitalization, gestures", R.drawable.ic_kb_edit) to { navigateTo(Screen.TYPING) },
            Triple("Full Test Playground", "Test chamber with multi-field inputs", R.drawable.ic_ui_test) to { navigateTo(Screen.TEST_PLAYGROUND) },
            Triple("Privacy & Offline", "100% on-device, zero data collection", R.drawable.ic_ui_privacy) to { navigateTo(Screen.PRIVACY) },
            Triple("About CrazeKeyboard", "Version 2.1 specs and credits", R.drawable.ic_ui_info) to { navigateTo(Screen.ABOUT) }
        )

        settingsItems.forEachIndexed { index, (info, action) ->
            val (title, subtitle, iconRes) = info
            val row = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                isClickable = true
                isFocusable = true
                setPadding(dp(4), dp(10), dp(4), dp(10))
                setOnClickListener { action() }
            }

            val iconBox = FrameLayout(this@MainActivity).apply {
                background = flatPill(surface, border, 10)
                setPadding(dp(8), dp(8), dp(8), dp(8))
            }
            val icon = ImageView(this@MainActivity).apply {
                setImageResource(iconRes)
                imageTintList = ColorStateList.valueOf(accent)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
            }
            iconBox.addView(icon, FrameLayout.LayoutParams(dp(20), dp(20)))
            row.addView(iconBox)

            val textCol = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), 0, dp(8), 0)
                addView(label(title, 14.5f, textPrimary, true))
                addView(label(subtitle, 12f, textSecondary, false))
            }
            row.addView(textCol, LinearLayout.LayoutParams(0, -2, 1f))

            val arrow = ImageView(this@MainActivity).apply {
                setImageResource(R.drawable.ic_ui_arrow_forward)
                imageTintList = ColorStateList.valueOf(textMuted)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
            }
            row.addView(arrow, LinearLayout.LayoutParams(dp(18), dp(18)))
            addView(row)

            if (index < settingsItems.size - 1) {
                addView(View(this@MainActivity).apply {
                    setBackgroundColor(dividerColor)
                }, LinearLayout.LayoutParams(-1, dp(1)).apply { setMargins(dp(44), 0, 0, 0) })
            }
        }
    }

    // =========================================================================
    // 2. THEME STORE SCREEN
    // =========================================================================
    private fun renderThemesScreen() {
        contentContainer.removeAllViews()
        renderSubHeader("Theme Gallery", "Select from Material You Dark Slate, Arctic Glass, pastels, and natural tones.")

        // Category Filter Chips
        val chipScroll = HorizontalScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            setPadding(0, 0, 0, dp(12))
        }
        val chipRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val categories = listOf("All", "Dark", "Light", "Arctic Glass", "Pastel", "Nature")

        categories.forEach { cat ->
            val isSelected = activeThemeCategory == cat
            val chip = Button(this).apply {
                text = cat
                textSize = 12f
                isAllCaps = false
                setTextColor(if (isSelected) Color.WHITE else textSecondary)
                background = flatPill(if (isSelected) accent else surface, if (isSelected) accentBorder else border, 16)
                setPadding(dp(14), dp(6), dp(14), dp(6))
                setOnClickListener {
                    activeThemeCategory = cat
                    renderThemesScreen()
                }
            }
            chipRow.addView(chip, LinearLayout.LayoutParams(-2, -2).apply { setMargins(0, 0, dp(8), 0) })
        }
        chipScroll.addView(chipRow)
        contentContainer.addView(chipScroll)

        // Search Input
        val searchBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = flatPill(surface, border, 12)
            setPadding(dp(12), dp(2), dp(12), dp(2))
        }
        val searchIcon = ImageView(this).apply {
            setImageResource(R.drawable.ic_ui_search)
            imageTintList = ColorStateList.valueOf(textSecondary)
        }
        searchBox.addView(searchIcon, LinearLayout.LayoutParams(dp(20), dp(20)))

        val searchInput = EditText(this).apply {
            hint = "Search themes by name..."
            setHintTextColor(textMuted)
            setTextColor(textPrimary)
            textSize = 13.5f
            background = null
            setPadding(dp(10), dp(8), dp(10), dp(8))
            setText(themeSearchQuery)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    themeSearchQuery = s?.toString().orEmpty()
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
        searchBox.addView(searchInput, LinearLayout.LayoutParams(0, -2, 1f))
        contentContainer.addView(searchBox)
        contentContainer.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(-1, dp(12)) })

        // Filter themes
        val currentActiveTheme = prefs.getString("theme", "Material You Dark Slate") ?: "Material You Dark Slate"
        val filtered = allThemes.filter { t ->
            val matchCat = (activeThemeCategory == "All" || t.category == activeThemeCategory)
            val matchQuery = themeSearchQuery.isEmpty() || t.name.contains(themeSearchQuery, ignoreCase = true)
            matchCat && matchQuery
        }

        filtered.forEach { theme ->
            val isCurrent = theme.name == currentActiveTheme
            contentContainer.addView(card {
                // Miniature Keyboard Visual Preview Bar
                val previewBox = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    background = flatPill(theme.bg, Color.argb(40, 255, 255, 255), 10)
                    setPadding(dp(10), dp(8), dp(10), dp(8))
                }

                // Sample key row
                val sampleRow1 = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                }
                listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p").forEach { k ->
                    sampleRow1.addView(TextView(this@MainActivity).apply {
                        text = k
                        textSize = 10f
                        setTextColor(theme.textPrimary)
                        gravity = Gravity.CENTER
                        background = flatPill(theme.keySurface, Color.argb(30, 255, 255, 255), 4)
                    }, LinearLayout.LayoutParams(0, dp(22), 1f).apply { setMargins(dp(1), dp(1), dp(1), dp(1)) })
                }
                previewBox.addView(sampleRow1)

                // Sample bottom row with accent enter key
                val sampleRow2 = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                    setPadding(0, dp(3), 0, 0)
                }
                sampleRow2.addView(TextView(this@MainActivity).apply {
                    text = "?123"
                    textSize = 9f
                    setTextColor(theme.textPrimary)
                    gravity = Gravity.CENTER
                    background = flatPill(theme.keySurface, Color.argb(30, 255, 255, 255), 4)
                }, LinearLayout.LayoutParams(0, dp(22), 1.5f).apply { setMargins(dp(1), 0, dp(1), 0) })

                sampleRow2.addView(TextView(this@MainActivity).apply {
                    text = "space"
                    textSize = 9f
                    setTextColor(Color.argb(160, Color.red(theme.textPrimary), Color.green(theme.textPrimary), Color.blue(theme.textPrimary)))
                    gravity = Gravity.CENTER
                    background = flatPill(theme.keySurface, Color.argb(30, 255, 255, 255), 4)
                }, LinearLayout.LayoutParams(0, dp(22), 5f).apply { setMargins(dp(1), 0, dp(1), 0) })

                sampleRow2.addView(TextView(this@MainActivity).apply {
                    text = "↵"
                    textSize = 11f
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                    background = flatPill(theme.accent, theme.accent, 4)
                }, LinearLayout.LayoutParams(0, dp(22), 1.5f).apply { setMargins(dp(1), 0, dp(1), 0) })
                previewBox.addView(sampleRow2)

                addView(previewBox)

                // Theme Details
                val titleRow = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(10), 0, dp(2))
                }
                titleRow.addView(label(theme.name, 17f, textPrimary, true), LinearLayout.LayoutParams(0, -2, 1f))
                titleRow.addView(TextView(this@MainActivity).apply {
                    text = theme.category
                    textSize = 10f
                    setTextColor(accent)
                    background = flatPill(surface, border, 8)
                    setPadding(dp(8), dp(3), dp(8), dp(3))
                })
                addView(titleRow)

                addView(label(theme.description, 13f, textSecondary, false).apply { setPadding(0, 0, 0, dp(10)) })

                // Swatch Dots
                val swatchRow = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, 0, 0, dp(10))
                }
                listOf(theme.bg, theme.keySurface, theme.accent).forEach { col ->
                    swatchRow.addView(View(this@MainActivity).apply {
                        background = flatPill(col, border, 10)
                    }, LinearLayout.LayoutParams(dp(18), dp(18)).apply { setMargins(0, 0, dp(8), 0) })
                }
                swatchRow.addView(label("Canvas • Keycap • Accent", 11.5f, textMuted, false))
                addView(swatchRow)

                // Action Buttons Row
                val btnRow = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                }

                val applyBtn = Button(this@MainActivity).apply {
                    text = if (isCurrent) "Active Theme" else "Apply Theme"
                    isAllCaps = false
                    textSize = 12.5f
                    setTextColor(Color.WHITE)
                    background = flatPill(if (isCurrent) greenActive else accent, if (isCurrent) greenActive else accentBorder, 12)
                    setOnClickListener {
                        prefs.edit().putString("theme", theme.name).apply()
                        renderThemesScreen()
                    }
                }
                btnRow.addView(applyBtn, LinearLayout.LayoutParams(0, dp(40), 1f).apply { rightMargin = dp(6) })

                val testBtn = Button(this@MainActivity).apply {
                    text = "Test Typing"
                    isAllCaps = false
                    textSize = 12.5f
                    setTextColor(textPrimary)
                    background = flatPill(surface, border, 12)
                    setOnClickListener {
                        prefs.edit().putString("theme", theme.name).apply()
                        navigateTo(Screen.TEST_PLAYGROUND)
                    }
                }
                btnRow.addView(testBtn, LinearLayout.LayoutParams(0, dp(40), 1f))
                addView(btnRow)
            })
        }
    }

    // =========================================================================
    // 3. KEYBOARD CUSTOMIZATION SCREEN
    // =========================================================================
    private fun renderCustomizeScreen() {
        contentContainer.removeAllViews()
        renderSubHeader("Customization", "Tune keyboard sizing, corner radius, key spacing, and hints.")

        val activeThemeDesc = getActiveThemeDescriptor()
        contentContainer.addView(card {
            addView(label("ACTIVE THEME: ${activeThemeDesc.name}", 11.5f, accent, true))
            addView(label("Custom Key Geometry", 16f, textPrimary, true).apply { setPadding(0, dp(2), 0, dp(4)) })
            addView(label("Changes take effect instantly on CrazeKeyboard when typing.", 12.5f, textSecondary, false))
        })

        contentContainer.addView(sectionHeader("GEOMETRY & DIMENSIONS", "Key proportions and tactile touch response"))

        // Key Height
        val currentHeight = prefs.getInt("pref_key_height", 226)
        contentContainer.addView(card {
            addView(label("Keyboard Height", 16f, textPrimary, true))
            addView(label("Adjust overall keyboard height on screen", 12.5f, textSecondary, false).apply { setPadding(0, dp(2), 0, dp(8)) })

            val options = listOf(210 to "Compact (210dp)", 226 to "Standard (226dp)", 250 to "Tall (250dp)")
            val row = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL }
            options.forEach { (h, labelStr) ->
                val isSel = currentHeight == h
                row.addView(Button(this@MainActivity).apply {
                    text = labelStr
                    textSize = 11.5f
                    isAllCaps = false
                    setTextColor(if (isSel) Color.WHITE else textSecondary)
                    background = flatPill(if (isSel) accent else surface, if (isSel) accentBorder else border, 12)
                    setOnClickListener {
                        prefs.edit().putInt("pref_key_height", h).apply()
                        renderCustomizeScreen()
                    }
                }, LinearLayout.LayoutParams(0, dp(40), 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
            }
            addView(row)
        })

        // Corner Radius
        val currentRadius = prefs.getFloat("pref_key_radius", 7.5f)
        contentContainer.addView(card {
            addView(label("Key Corner Radius", 16f, textPrimary, true))
            addView(label("Shape of alphanumeric keycaps", 12.5f, textSecondary, false).apply { setPadding(0, dp(2), 0, dp(8)) })

            val radiusOptions = listOf(4f to "Sharp (4dp)", 7.5f to "Default (8dp)", 12f to "Pill (12dp)")
            val row = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL }
            radiusOptions.forEach { (r, labelStr) ->
                val isSel = Math.abs(currentRadius - r) < 0.5f
                row.addView(Button(this@MainActivity).apply {
                    text = labelStr
                    textSize = 11.5f
                    isAllCaps = false
                    setTextColor(if (isSel) Color.WHITE else textSecondary)
                    background = flatPill(if (isSel) accent else surface, if (isSel) accentBorder else border, 12)
                    setOnClickListener {
                        prefs.edit().putFloat("pref_key_radius", r).apply()
                        renderCustomizeScreen()
                    }
                }, LinearLayout.LayoutParams(0, dp(40), 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
            }
            addView(row)
        })

        // Key Spacing
        val currentSpacing = prefs.getFloat("pref_key_spacing_x", 3f)
        contentContainer.addView(card {
            addView(label("Key Margin & Spacing", 16f, textPrimary, true))
            addView(label("Horizontal gap between adjacent keys", 12.5f, textSecondary, false).apply { setPadding(0, dp(2), 0, dp(8)) })

            val spacingOptions = listOf(2f to "Tight (2dp)", 3f to "Standard (3dp)", 5f to "Spacious (5dp)")
            val row = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL }
            spacingOptions.forEach { (sp, labelStr) ->
                val isSel = Math.abs(currentSpacing - sp) < 0.5f
                row.addView(Button(this@MainActivity).apply {
                    text = labelStr
                    textSize = 11.5f
                    isAllCaps = false
                    setTextColor(if (isSel) Color.WHITE else textSecondary)
                    background = flatPill(if (isSel) accent else surface, if (isSel) accentBorder else border, 12)
                    setOnClickListener {
                        prefs.edit().putFloat("pref_key_spacing_x", sp).apply()
                        renderCustomizeScreen()
                    }
                }, LinearLayout.LayoutParams(0, dp(40), 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
            }
            addView(row)
        })

        // Secondary Hints and Popups
        contentContainer.addView(sectionHeader("VISUAL FEEDBACK", "Key previews and long-press hints"))
        toggleOption("Show Key Popups", "Enlarged bubble above keys on touch", "popups", true)
        toggleOption("Secondary Key Symbols", "Show numbers & punctuation on keycaps", "longpress", true)
    }

    // =========================================================================
    // 4. LANGUAGES SCREEN (ALL SVG FLAGS - NO EMOJIS)
    // =========================================================================
    private fun renderLanguagesScreen() {
        contentContainer.removeAllViews()
        renderSubHeader("Languages & Layouts", "Manage supported languages and keyboard script layouts.")

        val activeLang = prefs.getString("language", "English (US)") ?: "English (US)"

        val languages = listOf(
            Triple("English (US)", "QWERTY • Primary Latin Dictionary", R.drawable.ic_flag_us),
            Triple("বাংলা (Bengali)", "Bengali Script Layout • অ আ ক খ", R.drawable.ic_flag_bd),
            Triple("Español", "Spanish QWERTY • Caracteres con acentos", R.drawable.ic_flag_es),
            Triple("Français", "French Layout • Clavier avec cédille", R.drawable.ic_flag_fr),
            Triple("Deutsch", "German Layout • Deutsche Umlaute (ä, ö, ü)", R.drawable.ic_flag_de)
        )

        // Search Box
        val searchBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = flatPill(surface, border, 12)
            setPadding(dp(12), dp(2), dp(12), dp(2))
        }
        val searchIcon = ImageView(this).apply {
            setImageResource(R.drawable.ic_ui_search)
            imageTintList = ColorStateList.valueOf(textSecondary)
        }
        searchBox.addView(searchIcon, LinearLayout.LayoutParams(dp(20), dp(20)))

        val searchInput = EditText(this).apply {
            hint = "Search language..."
            setHintTextColor(textMuted)
            setTextColor(textPrimary)
            textSize = 13.5f
            background = null
            setPadding(dp(10), dp(8), dp(10), dp(8))
            setText(languageSearchQuery)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    languageSearchQuery = s?.toString().orEmpty()
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
        searchBox.addView(searchInput, LinearLayout.LayoutParams(0, -2, 1f))
        contentContainer.addView(searchBox)
        contentContainer.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(-1, dp(12)) })

        val filtered = languages.filter {
            languageSearchQuery.isEmpty() || it.first.contains(languageSearchQuery, ignoreCase = true)
        }

        filtered.forEach { (name, subtitle, flagDrawableRes) ->
            val isCurrent = name == activeLang
            contentContainer.addView(card {
                val row = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }

                val flagView = ImageView(this@MainActivity).apply {
                    setImageResource(flagDrawableRes)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }
                row.addView(flagView, LinearLayout.LayoutParams(dp(32), dp(22)).apply { rightMargin = dp(12) })

                val textCol = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(label(name, 16f, textPrimary, true))
                    addView(label(subtitle, 12f, textSecondary, false))
                }
                row.addView(textCol, LinearLayout.LayoutParams(0, -2, 1f))

                val selectBtn = Button(this@MainActivity).apply {
                    text = if (isCurrent) "Active" else "Select"
                    textSize = 12f
                    isAllCaps = false
                    setTextColor(Color.WHITE)
                    background = flatPill(if (isCurrent) greenActive else accent, if (isCurrent) greenActive else accentBorder, 12)
                    setOnClickListener {
                        prefs.edit().putString("language", name).apply()
                        renderLanguagesScreen()
                    }
                }
                row.addView(selectBtn, LinearLayout.LayoutParams(-2, dp(36)))
                addView(row)
            })
        }
    }

    // =========================================================================
    // 5. SOUND & HAPTICS SCREEN
    // =========================================================================
    private fun renderSoundHapticsScreen() {
        contentContainer.removeAllViews()
        renderSubHeader("Sound & Vibration", "Configure physical tactile haptics and audio click volume.")

        contentContainer.addView(sectionHeader("HAPTIC VIBRATION", "Tactile feedback on every keypress"))
        toggleOption("Vibrate on Keypress", "Instant mechanical vibration on touch down", "haptics", true)

        val currentStrength = prefs.getString("vibration_strength", "Medium") ?: "Medium"
        contentContainer.addView(card {
            addView(label("Vibration Intensity", 16f, textPrimary, true))
            addView(label("Select keypress haptic feedback strength", 12.5f, textSecondary, false).apply { setPadding(0, dp(2), 0, dp(8)) })

            val options = listOf("Light", "Medium", "Strong")
            val row = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL }
            options.forEach { s ->
                val isSel = currentStrength == s
                row.addView(Button(this@MainActivity).apply {
                    text = s
                    textSize = 12f
                    isAllCaps = false
                    setTextColor(if (isSel) Color.WHITE else textSecondary)
                    background = flatPill(if (isSel) accent else surface, if (isSel) accentBorder else border, 12)
                    setOnClickListener {
                        prefs.edit().putString("vibration_strength", s).apply()
                        vibrateInApp(25)
                        renderSoundHapticsScreen()
                    }
                }, LinearLayout.LayoutParams(0, dp(40), 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
            }
            addView(row)
        })

        contentContainer.addView(sectionHeader("AUDIO FEEDBACK", "Mechanical typewriter & click sounds"))
        toggleOption("Sound on Keypress", "Play audible click on key down", "sound", false)

        val currentVol = prefs.getInt("pref_sound_volume", 80)
        contentContainer.addView(card {
            addView(label("Audio Volume", 16f, textPrimary, true))
            addView(label("Key click sound volume percentage", 12.5f, textSecondary, false).apply { setPadding(0, dp(2), 0, dp(8)) })

            val volOptions = listOf(40 to "Soft (40%)", 80 to "Standard (80%)", 100 to "Loud (100%)")
            val row = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL }
            volOptions.forEach { (vol, labelStr) ->
                val isSel = currentVol == vol
                row.addView(Button(this@MainActivity).apply {
                    text = labelStr
                    textSize = 11.5f
                    isAllCaps = false
                    setTextColor(if (isSel) Color.WHITE else textSecondary)
                    background = flatPill(if (isSel) accent else surface, if (isSel) accentBorder else border, 12)
                    setOnClickListener {
                        prefs.edit().putInt("pref_sound_volume", vol).apply()
                        playInAppSound()
                        renderSoundHapticsScreen()
                    }
                }, LinearLayout.LayoutParams(0, dp(40), 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
            }
            addView(row)
        })

        // Verification Trigger Card
        contentContainer.addView(card {
            addView(label("TEST FEEDBACK INSTANTLY", 11.5f, accent, true))
            addView(label("Tap buttons below to preview vibration and audio:", 13f, textSecondary, false).apply { setPadding(0, dp(4), 0, dp(10)) })

            val btnRow = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL }
            btnRow.addView(Button(this@MainActivity).apply {
                text = "Test Haptic Pulse"
                textSize = 12.5f
                isAllCaps = false
                setTextColor(Color.WHITE)
                background = flatPill(accent, accentBorder, 12)
                setOnClickListener { vibrateInApp(20) }
            }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { rightMargin = dp(4) })

            btnRow.addView(Button(this@MainActivity).apply {
                text = "Test Audio Click"
                textSize = 12.5f
                isAllCaps = false
                setTextColor(textPrimary)
                background = flatPill(surface, border, 12)
                setOnClickListener { playInAppSound() }
            }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { leftMargin = dp(4) })

            addView(btnRow)
        })
    }

    // =========================================================================
    // 6. TYPING PREFERENCES SCREEN
    // =========================================================================
    private fun renderTypingScreen() {
        contentContainer.removeAllViews()
        renderSubHeader("Typing Preferences", "Predictions, auto-capitalization, gestures, and backspace.")

        contentContainer.addView(sectionHeader("INTELLIGENT INPUT", "Word predictions and autocorrection"))
        toggleOption("Real-time Word Predictions", "Suggest candidate words in the unified top strip", "suggestions", true)
        toggleOption("Auto-Capitalization", "Automatically capitalize the first letter of sentences", "autocap", true)
        toggleOption("Double-Tap Space for Period", "Insert a period followed by a space on double spacebar tap", "double_space_period", true)

        contentContainer.addView(sectionHeader("GESTURE & NAVIGATION", "Spacebar cursor gliding and deletion"))
        toggleOption("Spacebar Cursor Glide", "Drag horizontally on spacebar to scrub cursor left/right", "space_glide", true)
        toggleOption("Auto-Repeat Rapid Backspace", "Continuously delete words while holding backspace key", "repeat_backspace", true)
        toggleOption("Key Popup Magnifier", "Show enlarged character bubble directly above pressed keys", "popups", true)
        toggleOption("Secondary Symbol Hints", "Display numbers & symbols on keycaps, hold to type", "longpress", true)
    }

    // =========================================================================
    // 7. TYPING TEST PLAYGROUND SCREEN
    // =========================================================================
    private fun renderPlaygroundScreen() {
        contentContainer.removeAllViews()
        renderSubHeader("Test Playground", "Verify keyboard typing, predictions, and independent layer styling.")

        var f1: EditText? = null
        var f2: EditText? = null
        var f3: EditText? = null
        var f4: EditText? = null

        val statsCard = card {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val col1 = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(label("0", 22f, accent, true).apply { tag = "stat_chars" })
                addView(label("Characters", 11.5f, textSecondary, false))
            }
            addView(col1, LinearLayout.LayoutParams(0, -2, 1f))

            val col2 = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(label("0", 22f, greenActive, true).apply { tag = "stat_words" })
                addView(label("Words Typed", 11.5f, textSecondary, false))
            }
            addView(col2, LinearLayout.LayoutParams(0, -2, 1f))

            addView(button("Clear All") {
                f1?.setText("")
                f2?.setText("")
                f3?.setText("")
                f4?.setText("")
            })
        }
        contentContainer.addView(statsCard)

        // Field 1: General Message
        contentContainer.addView(sectionHeader("1. GENERAL CHAT / MESSAGE", "Tests normal QWERTY typing & word suggestions"))
        f1 = EditText(this).apply {
            hint = "Type a message here with CrazeKeyboard..."
            setHintTextColor(textMuted)
            setTextColor(textPrimary)
            textSize = 14.5f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            background = flatPill(surface, border, 12)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val str = s?.toString().orEmpty()
                    statsCard.findViewWithTag<TextView>("stat_chars")?.text = str.length.toString()
                    val words = str.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }.size
                    statsCard.findViewWithTag<TextView>("stat_words")?.text = words.toString()
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
        contentContainer.addView(f1)

        // Field 2: Web Search
        contentContainer.addView(sectionHeader("2. WEB SEARCH QUERY", "Tests Action/Search enter keycap"))
        f2 = EditText(this).apply {
            hint = "Search keywords, topics, or recipes..."
            setHintTextColor(textMuted)
            setTextColor(textPrimary)
            textSize = 14.5f
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
            background = flatPill(surface, border, 12)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        contentContainer.addView(f2)

        // Field 3: Numeric PIN
        contentContainer.addView(sectionHeader("3. NUMERIC / PIN FIELD", "Tests numeric entry mode"))
        f3 = EditText(this).apply {
            hint = "Enter numbers or PIN code..."
            setHintTextColor(textMuted)
            setTextColor(textPrimary)
            textSize = 14.5f
            inputType = InputType.TYPE_CLASS_NUMBER
            background = flatPill(surface, border, 12)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        contentContainer.addView(f3)

        // Field 4: Multi-line Memo
        contentContainer.addView(sectionHeader("4. MULTI-LINE MEMO", "Tests enter key newline insertion"))
        f4 = EditText(this).apply {
            hint = "Draft notes, paragraphs, and long-form thoughts..."
            setHintTextColor(textMuted)
            setTextColor(textPrimary)
            textSize = 14f
            minLines = 3
            gravity = Gravity.TOP or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            background = flatPill(surface, border, 12)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        contentContainer.addView(f4)
    }

    // =========================================================================
    // 8. PRIVACY SCREEN
    // =========================================================================
    private fun renderPrivacyScreen() {
        contentContainer.removeAllViews()
        renderSubHeader("Privacy & Security", "Zero telemetry, 100% on-device keyboard input guarantee.")

        contentContainer.addView(card {
            addView(label("LOCAL-ONLY GUARANTEE", 12f, greenActive, true))
            addView(label("No Network Connectivity Declared", 18f, textPrimary, true).apply { setPadding(0, dp(4), 0, dp(8)) })
            addView(label(
                "CrazeKeyboard does not request or possess android.permission.INTERNET in AndroidManifest.xml. Every keystroke, clipboard item, and prediction dictionary lookup is processed 100% locally on your phone processor.",
                13.5f, textSecondary, false
            ))
        })

        contentContainer.addView(sectionHeader("PERMISSIONS AUDIT", "Zero network socket declarations in manifest"))
        contentContainer.addView(card {
            addView(permissionAuditRow("android.permission.INTERNET", "Not Declared (Safe)", "App cannot open sockets or transmit HTTP packets."))
            addView(permissionAuditRow("android.permission.ACCESS_NETWORK_STATE", "Not Declared (Safe)", "App has no visibility into WiFi or cellular network state."))
            addView(permissionAuditRow("android.permission.VIBRATE", "Granted", "Required solely for tactile haptic keypress feedback."))
        })
    }

    // =========================================================================
    // 9. ABOUT SCREEN
    // =========================================================================
    private fun renderAboutScreen() {
        contentContainer.removeAllViews()
        renderSubHeader("About CrazeKeyboard", "Engine specs, Material You design tokens, and credits.")

        contentContainer.addView(card {
            val titleRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val logo = ImageView(this@MainActivity).apply {
                setImageResource(R.drawable.ic_kb_keyboard)
                imageTintList = ColorStateList.valueOf(accent)
            }
            titleRow.addView(logo, LinearLayout.LayoutParams(dp(26), dp(26)))

            val tCol = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(10), 0, 0, 0)
                addView(label("CrazeKeyboard V2", 18f, textPrimary, true))
                addView(label("Version 2.1 (Build 2026.10) • Gboard Architecture", 12f, textSecondary, false))
            }
            titleRow.addView(tCol)
            addView(titleRow)

            addView(label(
                "CrazeKeyboard V2 is built with modern Android InputMethodService architecture, zero-allocation custom drawing, low-latency rollover typing, and an independent floating layer sheet design.",
                13f, textSecondary, false
            ).apply { setPadding(0, dp(12), 0, dp(12)) })

            addView(diagnosticRow("UI Design Language", "Material You Dark Slate & Arctic Glass"))
            addView(diagnosticRow("Input Method Service", "CrazeInputMethodService"))
            addView(diagnosticRow("Touch Engine", "LatinKeyboardView Multi-Touch Rollover"))
            addView(diagnosticRow("Haptic Driver", "VibrationEffect Audio/Haptic Synchronizer"))
            addView(diagnosticRow("Offline Status", "100% Local (Zero Network Permissions)"))
        })
    }

    // =========================================================================
    // UI BUILDER HELPERS
    // =========================================================================
    private fun renderSubHeader(title: String, subtitle: String) {
        contentContainer.addView(label(title, 22f, textPrimary, true).apply { setPadding(0, 0, 0, dp(4)) })
        contentContainer.addView(label(subtitle, 13f, textSecondary, false).apply { setPadding(0, 0, 0, dp(14)) })
    }

    private fun getActiveThemeDescriptor(): ThemeDescriptor {
        val themeName = prefs.getString("theme", "Material You Dark Slate") ?: "Material You Dark Slate"
        return allThemes.find { it.name == themeName } ?: allThemes[0]
    }

    private fun toggleOption(title: String, desc: String, key: String, default: Boolean, onToggle: (() -> Unit)? = null) {
        contentContainer.addView(card {
            val hRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val textCol = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(label(title, 15.5f, textPrimary, true))
                addView(label(desc, 12.5f, textSecondary, false).apply { setPadding(0, dp(2), 0, 0) })
            }
            hRow.addView(textCol, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(10) })

            val isOn = prefs.getBoolean(key, default)
            val btn = Button(this@MainActivity).apply {
                text = if (isOn) "ON" else "OFF"
                textSize = 12.5f
                isAllCaps = false
                setTextColor(if (isOn) Color.WHITE else textSecondary)
                background = flatPill(if (isOn) accent else surface, if (isOn) accentBorder else border, 12)
                setOnClickListener {
                    val next = !prefs.getBoolean(key, default)
                    prefs.edit().putBoolean(key, next).apply()
                    text = if (next) "ON" else "OFF"
                    setTextColor(if (next) Color.WHITE else textSecondary)
                    background = flatPill(if (next) accent else surface, if (next) accentBorder else border, 12)
                    onToggle?.invoke()
                }
            }
            hRow.addView(btn)
            addView(hRow)
        })
    }

    private fun stepPill(num: Int, done: Boolean): View {
        return if (done) {
            FrameLayout(this).apply {
                background = flatPill(greenSurface, greenActive, 16)
                layoutParams = LinearLayout.LayoutParams(dp(28), dp(28))
                val img = ImageView(this@MainActivity).apply {
                    setImageResource(R.drawable.ic_ui_check_circle)
                    imageTintList = ColorStateList.valueOf(greenActive)
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                    setPadding(dp(4), dp(4), dp(4), dp(4))
                }
                addView(img, FrameLayout.LayoutParams(-1, -1))
            }
        } else {
            TextView(this).apply {
                text = num.toString()
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(textPrimary)
                background = flatPill(surface, border, 16)
                layoutParams = LinearLayout.LayoutParams(dp(28), dp(28))
            }
        }
    }

    private fun permissionAuditRow(permName: String, status: String, desc: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dp(4), 0, dp(8))
        val row = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(label(permName, 13f, textPrimary, true), LinearLayout.LayoutParams(0, -2, 1f))
        val isGranted = status.contains("Granted") || status.contains("Safe")
        row.addView(TextView(this@MainActivity).apply {
            text = status
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (isGranted) greenActive else accent)
            background = flatPill(if (isGranted) greenSurface else surface, border, 8)
            setPadding(dp(8), dp(3), dp(8), dp(3))
        })
        addView(row)
        addView(label(desc, 12f, textSecondary, false))
    }

    private fun diagnosticRow(key: String, value: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(4), 0, dp(4))
        addView(label(key, 13f, textSecondary, false), LinearLayout.LayoutParams(0, -2, 1f))
        addView(label(value, 13f, textPrimary, true))
    }

    private fun isImeEnabled(): Boolean {
        val enabledMethods = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_INPUT_METHODS) ?: ""
        return enabledMethods.contains(packageName)
    }

    private fun isImeSelected(): Boolean {
        val defaultMethod = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: ""
        return defaultMethod.contains(packageName)
    }

    private fun sectionHeader(title: String, subtitle: String? = null): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(2), dp(16), 0, dp(8))
        addView(label(title, 11.5f, accent, true))
        if (!subtitle.isNullOrEmpty()) {
            addView(label(subtitle, 12.5f, textSecondary, false).apply { setPadding(0, dp(2), 0, 0) })
        }
    }

    private fun card(build: LinearLayout.() -> Unit): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(15), dp(16), dp(15))
        background = flatPill(cardSurface, border, 16)
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
        textSize = 13f
        setTextColor(Color.WHITE)
        background = flatPill(accent, accentBorder, 14)
        setOnClickListener { onClick() }
    }

    private fun flatPill(color: Int, borderColor: Int, radiusDp: Int): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
        setStroke(dp(0.8f), borderColor)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun dp(value: Float): Int = (value * resources.displayMetrics.density).toInt()

    private fun ThemeDescriptor.toThemeColors(): LatinKeyboardView.ThemeColors {
        return LatinKeyboardView.ThemeColors(
            bg = bg,
            keySurface = keySurface,
            keyBorder = border,
            keyPressed = surface,
            keyFunc = cardSurface,
            keyFuncBorder = borderLight,
            accent = accent,
            accentBorder = accentBorder,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            divider = dividerColor
        )
    }

    private fun createInAppKeyboardDrawer(): View {
        val themeDesc = getActiveThemeDescriptor()
        var isDrawerToolsMode = false
        var isDrawerExpanded = true

        val drawerRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val cornerR = dp(16).toFloat()
            background = GradientDrawable().apply {
                setColor(themeDesc.bg)
                cornerRadii = floatArrayOf(
                    cornerR, cornerR,
                    cornerR, cornerR,
                    0f, 0f,
                    0f, 0f
                )
                setStroke(dp(1), border)
            }
            elevation = dp(12).toFloat()
            setPadding(dp(8), dp(4), dp(8), dp(6))
        }

        // 1. Top Grabber Handle & Header Toggle
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(2), dp(4), dp(2))
        }

        val grabberPill = View(this).apply {
            background = flatPill(dividerColor, dividerColor, 3)
        }
        val grabberContainer = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            addView(grabberPill, LinearLayout.LayoutParams(dp(36), dp(4)))
        }
        headerRow.addView(grabberContainer, LinearLayout.LayoutParams(0, -2, 1f))

        val toggleExpandBtn = TextView(this).apply {
            text = "⌨ CrazeKeyboard VW Toolbar"
            textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(accent)
            background = flatPill(surface, border, 10)
            setPadding(dp(8), dp(3), dp(8), dp(3))
        }
        headerRow.addView(toggleExpandBtn)
        drawerRoot.addView(headerRow)

        // 2. Keyboard Body Container (Top Strip Toolbar + Keypad)
        val bodyContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        toggleExpandBtn.setOnClickListener {
            vibrateInApp(10L)
            playInAppSound()
            isDrawerExpanded = !isDrawerExpanded
            bodyContainer.visibility = if (isDrawerExpanded) View.VISIBLE else View.GONE
            toggleExpandBtn.text = if (isDrawerExpanded) "⌨ CrazeKeyboard VW Toolbar" else "⌨ Show CrazeKeyboard VW"
        }

        // Top Strip Toolbar
        val topStrip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(-1, dp(38))
            setBackgroundColor(themeDesc.bg)
        }

        val toolsToggleBtn = ImageView(this).apply {
            setImageResource(R.drawable.ic_kb_tools)
            imageTintList = ColorStateList.valueOf(textSecondary)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(6), dp(6), dp(6), dp(6))
        }

        val centerFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, -1, 1f)
        }

        val suggestionsStrip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = FrameLayout.LayoutParams(-1, -1)
        }

        val cand1 = TextView(this).apply {
            text = "the"
            textSize = 13f
            setTextColor(textSecondary)
            gravity = Gravity.CENTER
            setOnClickListener { vibrateInApp(10L); playInAppSound(); Toast.makeText(this@MainActivity, "Word selected: the", Toast.LENGTH_SHORT).show() }
        }
        suggestionsStrip.addView(cand1, LinearLayout.LayoutParams(0, -1, 1f))

        suggestionsStrip.addView(View(this).apply {
            setBackgroundColor(dividerColor)
        }, LinearLayout.LayoutParams(dp(1), dp(16)))

        val cand2 = TextView(this).apply {
            text = "keyboard"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setOnClickListener { vibrateInApp(10L); playInAppSound(); Toast.makeText(this@MainActivity, "Word selected: keyboard", Toast.LENGTH_SHORT).show() }
        }
        suggestionsStrip.addView(cand2, LinearLayout.LayoutParams(0, -1, 1.3f))

        suggestionsStrip.addView(View(this).apply {
            setBackgroundColor(dividerColor)
        }, LinearLayout.LayoutParams(dp(1), dp(16)))

        val cand3 = TextView(this).apply {
            text = "smart"
            textSize = 13f
            setTextColor(textSecondary)
            gravity = Gravity.CENTER
            setOnClickListener { vibrateInApp(10L); playInAppSound(); Toast.makeText(this@MainActivity, "Word selected: smart", Toast.LENGTH_SHORT).show() }
        }
        suggestionsStrip.addView(cand3, LinearLayout.LayoutParams(0, -1, 1f))

        val toolsStrip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = FrameLayout.LayoutParams(-1, -1)
            visibility = View.GONE
        }

        val toolIcons = listOf(
            Triple(R.drawable.ic_kb_emoji, "Emoji") { Toast.makeText(this@MainActivity, "Emoji Toolbar Tool", Toast.LENGTH_SHORT).show() },
            Triple(R.drawable.ic_kb_clipboard, "Clipboard") { Toast.makeText(this@MainActivity, "Clipboard Toolbar Tool", Toast.LENGTH_SHORT).show() },
            Triple(R.drawable.ic_kb_edit, "Text Edit") { Toast.makeText(this@MainActivity, "Text Editor Toolbar Tool", Toast.LENGTH_SHORT).show() },
            Triple(R.drawable.ic_kb_palette, "Theme") {
                val names = allThemes.map { it.name }
                val curr = prefs.getString("theme", "Material You Dark Slate")
                val next = names[(names.indexOf(curr) + 1) % names.size]
                prefs.edit().putString("theme", next).apply()
                renderHomeScreen()
            },
            Triple(R.drawable.ic_kb_settings, "Settings") { navigateTo(Screen.SOUND_HAPTICS) }
        )

        toolIcons.forEach { (iconRes, name, action) ->
            val toolBtn = ImageView(this).apply {
                setImageResource(iconRes)
                imageTintList = ColorStateList.valueOf(textSecondary)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setPadding(dp(8), dp(6), dp(8), dp(6))
                setOnClickListener {
                    vibrateInApp(12L)
                    playInAppSound()
                    action()
                }
            }
            toolsStrip.addView(toolBtn, LinearLayout.LayoutParams(0, -1, 1f))
        }

        centerFrame.addView(suggestionsStrip)
        centerFrame.addView(toolsStrip)

        toolsToggleBtn.setOnClickListener {
            vibrateInApp(12L)
            playInAppSound()
            isDrawerToolsMode = !isDrawerToolsMode
            if (isDrawerToolsMode) {
                toolsToggleBtn.setImageResource(R.drawable.ic_kb_chevron_left)
                toolsToggleBtn.imageTintList = ColorStateList.valueOf(accent)
                suggestionsStrip.visibility = View.GONE
                toolsStrip.visibility = View.VISIBLE
            } else {
                toolsToggleBtn.setImageResource(R.drawable.ic_kb_tools)
                toolsToggleBtn.imageTintList = ColorStateList.valueOf(textSecondary)
                suggestionsStrip.visibility = View.VISIBLE
                toolsStrip.visibility = View.GONE
            }
        }

        val micBtn = ImageView(this).apply {
            setImageResource(R.drawable.ic_kb_mic)
            imageTintList = ColorStateList.valueOf(textSecondary)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(6), dp(6), dp(6), dp(6))
            setOnClickListener {
                vibrateInApp(12L)
                playInAppSound()
                Toast.makeText(this@MainActivity, "Voice Dictation Active", Toast.LENGTH_SHORT).show()
            }
        }

        topStrip.addView(toolsToggleBtn, LinearLayout.LayoutParams(dp(36), dp(36)))
        topStrip.addView(centerFrame)
        topStrip.addView(micBtn, LinearLayout.LayoutParams(dp(36), dp(36)))

        bodyContainer.addView(topStrip)

        // LatinKeyboardView Keypad
        val kbView = LatinKeyboardView(this).apply {
            this.theme = themeDesc.toThemeColors()
            this.listener = object : LatinKeyboardView.OnKeyboardActionListener {
                override fun onKey(code: String) {
                    vibrateInApp(10L)
                    playInAppSound()
                }
                override fun onSecondaryKey(subCode: String) {}
                override fun onSecondaryReplace(oldCode: String, newSubCode: String) {}
                override fun onKeyActionDown(code: String) { vibrateInApp(8L); playInAppSound() }
                override fun onKeyActionUp(code: String) {}
                override fun onSpaceDrag(isRight: Boolean) {}
                override fun onBackspaceSwipe() {}
                override fun onDeleteWord() {}
            }
        }
        bodyContainer.addView(kbView)
        drawerRoot.addView(bodyContainer)

        return drawerRoot
    }
}
