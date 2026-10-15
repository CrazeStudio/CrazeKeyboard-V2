package com.crazestudio.crazekeyboard

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class CrazeInputMethodService : InputMethodService(), LatinKeyboardView.OnKeyboardActionListener {

    enum class KeyboardMode {
        NORMAL,
        EMOJI,
        TEXT_EDIT,
        CLIPBOARD
    }

    enum class StripMode {
        SUGGESTIONS,
        TOOLS
    }

    private var stripMode = StripMode.TOOLS
    private var rootContainer: LinearLayout? = null
    private var topStripLayout: LinearLayout? = null
    private var toggleToolsButton: ImageView? = null
    private var centerContentFrame: FrameLayout? = null
    private var suggestionsContainer: LinearLayout? = null
    private var toolsContainer: LinearLayout? = null
    private val suggestionViews = mutableListOf<TextView>()
    private val suggestionDividers = mutableListOf<View>()
    private var rightActionButton: ImageView? = null
    private var keyboardView: LatinKeyboardView? = null
    private var panelContainer: FrameLayout? = null
    private var floatingHandleBar: View? = null

    private var currentMode = KeyboardMode.NORMAL
    private var shifted = false
    private var capsLock = false
    private var symbols = false
    private var moreSymbols = false
    private var bengali = false
    private var lastSpaceTime = 0L

    private val repeatHandler = Handler(Looper.getMainLooper())
    private var repeatRunnable: Runnable? = null
    private var backspaceRepeatCount = 0
    private var selectedEmojiCategory = 0

    private val prefs by lazy { getSharedPreferences("craze_keyboard", Context.MODE_PRIVATE) }

    val themes = mapOf(
        "Material You Dark Slate" to LatinKeyboardView.ThemeColors(
            bg = Color.rgb(27, 28, 32),               // #1B1C20 Material You Dark Slate Canvas
            keySurface = Color.rgb(44, 45, 51),       // #2C2D33 Keycap
            keyBorder = Color.rgb(58, 59, 68),        // #3A3B44 Clean subtle border
            keyPressed = Color.rgb(60, 62, 71),       // #3C3E47 Tactile pressed state
            keyFunc = Color.rgb(33, 34, 38),          // #212226 Functional keys (Shift, Backspace, ?123)
            keyFuncBorder = Color.rgb(48, 50, 56),    // #303238
            accent = Color.rgb(26, 115, 232),         // #1A73E8 Official Google Blue Action Pill
            accentBorder = Color.rgb(66, 133, 244),   // #4285F4
            textPrimary = Color.rgb(255, 255, 255),   // #FFFFFF Crisp White
            textSecondary = Color.rgb(158, 163, 176), // #9EA3B0 Subtle secondary symbols/hints
            textMuted = Color.rgb(125, 130, 142),     // #7D828E
            keyShadow = Color.argb(80, 0, 0, 0),      // Tangible key depth shadow
            divider = Color.rgb(52, 54, 62)           // #34363E Hairline divider line
        ),
        "Natural Charcoal" to LatinKeyboardView.ThemeColors(
            bg = Color.rgb(18, 19, 22),               // #121316 Deep Charcoal Black Canvas
            keySurface = Color.rgb(42, 44, 52),       // #2A2C34 Solid Distinct Keycap
            keyBorder = Color.rgb(62, 66, 76),        // #3E424C Crisp Clear Outline
            keyPressed = Color.rgb(65, 70, 82),       // #414652 Tactile Pressed State
            keyFunc = Color.rgb(28, 30, 36),          // #1C1E24 Rich Functional Modifiers
            keyFuncBorder = Color.rgb(48, 52, 60),    // #30343C
            accent = Color.rgb(26, 115, 232),         // #1A73E8 Rich Google Blue (Deep, vibrant, punchy)
            accentBorder = Color.rgb(66, 133, 244),   // #4285F4
            textPrimary = Color.rgb(255, 255, 255),   // #FFFFFF Pure Crisp White
            textSecondary = Color.rgb(176, 184, 196), // #B0B8C4 High-contrast readable secondary
            textMuted = Color.rgb(140, 148, 160),     // #8C94A0
            keyShadow = Color.argb(80, 0, 0, 0),      // Tangible key depth shadow
            divider = Color.rgb(48, 52, 60)
        ),
        "Natural Chalk" to LatinKeyboardView.ThemeColors(
            bg = Color.rgb(220, 224, 232),            // #DCE0E8 Rich Light Gray Canvas
            keySurface = Color.rgb(255, 255, 255),    // #FFFFFF Crisp White Keycap
            keyBorder = Color.rgb(180, 186, 198),     // #B4BAC6 Clear Keycap Border
            keyPressed = Color.rgb(205, 212, 224),    // #CDD4E0
            keyFunc = Color.rgb(202, 208, 218),       // #CAD0DA Solid Functional Keys
            keyFuncBorder = Color.rgb(170, 176, 188), // #AAB0BC
            accent = Color.rgb(21, 101, 192),         // #1565C0 Rich Deep Google Blue
            accentBorder = Color.rgb(13, 71, 161),    // #0D47A1
            textPrimary = Color.rgb(18, 20, 24),      // #121418 Solid Crisp Charcoal Black
            textSecondary = Color.rgb(75, 82, 94),    // #4B525E High-Contrast Secondary
            textMuted = Color.rgb(105, 114, 128),     // #697280
            keyShadow = Color.argb(65, 0, 0, 0),       // Real shadow for physical key separation
            divider = Color.rgb(190, 196, 208)
        ),
        "Natural Slate" to LatinKeyboardView.ThemeColors(
            bg = Color.rgb(15, 18, 26),               // #0F121A Deep Midnight Slate
            keySurface = Color.rgb(32, 40, 56),       // #202838 Rich Navy Keycap
            keyBorder = Color.rgb(52, 64, 88),        // #344058 Solid Outline
            keyPressed = Color.rgb(56, 70, 98),       // #384662
            keyFunc = Color.rgb(22, 28, 40),          // #161C28 Deep Functional Key
            keyFuncBorder = Color.rgb(40, 50, 72),    // #283248
            accent = Color.rgb(37, 99, 235),          // #2563EB Rich Royal Blue Accent
            accentBorder = Color.rgb(59, 130, 246),   // #3B82F6
            textPrimary = Color.rgb(255, 255, 255),   // #FFFFFF Crisp White
            textSecondary = Color.rgb(168, 185, 214), // #A8B9D6
            textMuted = Color.rgb(130, 148, 178),     // #8294B2
            keyShadow = Color.argb(80, 0, 0, 0),
            divider = Color.rgb(40, 50, 72)
        ),
        "Natural Obsidian" to LatinKeyboardView.ThemeColors(
            bg = Color.rgb(8, 8, 10),                 // #08080A Deep Pitch AMOLED Night
            keySurface = Color.rgb(26, 27, 32),       // #1A1B20 Solid AMOLED Keycap
            keyBorder = Color.rgb(48, 50, 58),        // #30323A Crisp Border
            keyPressed = Color.rgb(50, 52, 62),       // #32343E
            keyFunc = Color.rgb(16, 17, 20),          // #101114 Rich Black Modifiers
            keyFuncBorder = Color.rgb(34, 36, 42),    // #22242A
            accent = Color.rgb(0, 122, 255),          // #007AFF Electric Azure Blue
            accentBorder = Color.rgb(41, 152, 255),   // #2998FF
            textPrimary = Color.rgb(255, 255, 255),   // #FFFFFF High Contrast
            textSecondary = Color.rgb(165, 170, 182), // #A5AAB6
            textMuted = Color.rgb(125, 130, 142),     // #7D828E
            keyShadow = Color.argb(90, 0, 0, 0),
            divider = Color.rgb(34, 36, 42)
        ),
        "Natural Warm Earth" to LatinKeyboardView.ThemeColors(
            bg = Color.rgb(22, 20, 17),               // #161411 Rich Warm Espresso
            keySurface = Color.rgb(42, 38, 32),       // #2A2620 Warm Stone Keycap
            keyBorder = Color.rgb(68, 62, 53),        // #443E35 Solid Border
            keyPressed = Color.rgb(72, 66, 56),       // #484238
            keyFunc = Color.rgb(30, 27, 23),          // #1E1B17 Deep Warm Modifier
            keyFuncBorder = Color.rgb(50, 45, 38),    // #322D26
            accent = Color.rgb(217, 119, 6),          // #D97706 Rich Golden Amber
            accentBorder = Color.rgb(245, 158, 11),   // #F59E0B
            textPrimary = Color.rgb(255, 255, 255),   // #FFFFFF Crisp White
            textSecondary = Color.rgb(200, 188, 172), // #C8BCAC
            textMuted = Color.rgb(155, 142, 126),     // #9B8E7E
            keyShadow = Color.argb(80, 0, 0, 0),
            divider = Color.rgb(50, 45, 38)
        ),
        "Natural Sage" to LatinKeyboardView.ThemeColors(
            bg = Color.rgb(15, 22, 18),               // #0F1612 Deep Evergreen Canvas
            keySurface = Color.rgb(28, 42, 34),       // #1C2A22 Rich Pine Keycap
            keyBorder = Color.rgb(48, 70, 58),        // #30463A Crisp Pine Border
            keyPressed = Color.rgb(52, 78, 64),       // #344E40
            keyFunc = Color.rgb(20, 30, 24),          // #141E18 Deep Modifiers
            keyFuncBorder = Color.rgb(36, 52, 42),    // #24342A
            accent = Color.rgb(16, 149, 93),          // #10955D Rich Emerald Forest Accent
            accentBorder = Color.rgb(34, 197, 94),    // #22C55E
            textPrimary = Color.rgb(255, 255, 255),   // #FFFFFF Crisp White
            textSecondary = Color.rgb(175, 202, 184), // #AFCAB8
            textMuted = Color.rgb(132, 160, 142),     // #84A08E
            keyShadow = Color.argb(80, 0, 0, 0),
            divider = Color.rgb(36, 52, 42)
        ),
        "Arctic Glass" to LatinKeyboardView.ThemeColors(
            bg = Color.rgb(228, 238, 246),            // #E4EEF6 Soft frosted icy-blue canvas
            keySurface = Color.argb(235, 255, 255, 255), // Translucent frosted white keycap
            keyBorder = Color.rgb(190, 212, 232),     // Delicate icy-blue outline
            keyPressed = Color.rgb(212, 230, 244),
            keyFunc = Color.rgb(216, 230, 242),       // Frosted icy functional keys
            keyFuncBorder = Color.rgb(182, 204, 224),
            accent = Color.rgb(2, 132, 199),          // #0284C7 Vibrant Arctic Blue accent
            accentBorder = Color.rgb(14, 165, 233),   // #0EA5E9
            textPrimary = Color.rgb(15, 23, 42),      // #0F172A Deep slate navy text for crisp readability
            textSecondary = Color.rgb(71, 85, 105),   // #475569 Clear secondary hints
            textMuted = Color.rgb(100, 116, 139),
            keyShadow = Color.argb(45, 2, 132, 199),
            divider = Color.rgb(195, 215, 232)
        ),
        "Arctic Glass Dark" to LatinKeyboardView.ThemeColors(
            bg = Color.rgb(18, 26, 38),               // #121A26 Deep icy night canvas
            keySurface = Color.rgb(28, 40, 58),       // Translucent deep frost keycap
            keyBorder = Color.rgb(46, 64, 92),        // Crisp icy border
            keyPressed = Color.rgb(42, 58, 84),
            keyFunc = Color.rgb(22, 32, 48),          // Deep icy functional keys
            keyFuncBorder = Color.rgb(38, 54, 78),
            accent = Color.rgb(56, 189, 248),         // #38BDF8 Frosted Cyan Blue Accent
            accentBorder = Color.rgb(125, 211, 252),  // #7DD3FC
            textPrimary = Color.rgb(255, 255, 255),   // Crisp White
            textSecondary = Color.rgb(148, 163, 184), // #94A3B8 Secondary
            textMuted = Color.rgb(100, 116, 139),
            keyShadow = Color.argb(85, 0, 0, 0),
            divider = Color.rgb(38, 54, 78)
        ),
        "Pastel Lavender" to LatinKeyboardView.ThemeColors(
            bg = Color.rgb(243, 240, 248),
            keySurface = Color.rgb(255, 255, 255),
            keyBorder = Color.rgb(218, 210, 232),
            keyPressed = Color.rgb(232, 224, 244),
            keyFunc = Color.rgb(235, 229, 242),
            keyFuncBorder = Color.rgb(208, 198, 224),
            accent = Color.rgb(124, 58, 237),
            accentBorder = Color.rgb(147, 51, 234),
            textPrimary = Color.rgb(30, 27, 45),
            textSecondary = Color.rgb(90, 80, 115),
            textMuted = Color.rgb(120, 110, 145),
            keyShadow = Color.argb(40, 124, 58, 237),
            divider = Color.rgb(218, 210, 232)
        ),
        "Pastel Mint" to LatinKeyboardView.ThemeColors(
            bg = Color.rgb(238, 247, 242),
            keySurface = Color.rgb(255, 255, 255),
            keyBorder = Color.rgb(198, 226, 210),
            keyPressed = Color.rgb(220, 242, 230),
            keyFunc = Color.rgb(224, 240, 232),
            keyFuncBorder = Color.rgb(186, 216, 198),
            accent = Color.rgb(13, 148, 136),
            accentBorder = Color.rgb(20, 184, 166),
            textPrimary = Color.rgb(20, 36, 30),
            textSecondary = Color.rgb(60, 95, 80),
            textMuted = Color.rgb(90, 125, 110),
            keyShadow = Color.argb(40, 13, 148, 136),
            divider = Color.rgb(198, 226, 210)
        ),
        "Pastel Peach" to LatinKeyboardView.ThemeColors(
            bg = Color.rgb(253, 244, 240),
            keySurface = Color.rgb(255, 255, 255),
            keyBorder = Color.rgb(245, 212, 198),
            keyPressed = Color.rgb(250, 228, 218),
            keyFunc = Color.rgb(248, 232, 224),
            keyFuncBorder = Color.rgb(236, 198, 182),
            accent = Color.rgb(234, 88, 12),
            accentBorder = Color.rgb(249, 115, 22),
            textPrimary = Color.rgb(45, 25, 20),
            textSecondary = Color.rgb(120, 75, 60),
            textMuted = Color.rgb(150, 105, 90),
            keyShadow = Color.argb(40, 234, 88, 12),
            divider = Color.rgb(245, 212, 198)
        )
    )

    private val emojiCategories = listOf(
        "Smileys" to listOf(
            "😀","😃","😄","😁","😆","😅","🤣","😂","🙂","🙃",
            "😉","😊","😇","🥰","😍","🤩","😘","😗","😚","😋",
            "😛","😜","🤪","😝","🤑","🤗","🤭","🤫","🤔","🤐",
            "🤨","😐","😑","😶","😏","😒","🙄","😬","🤥","😌",
            "😔","😪","🤤","😴","😷","🤒","🤕","🤢","🤮","🤧",
            "🥵","🥶","🥴","😵","🤯","🤠","🥳","😎","🤓","🧐"
        ),
        "Gestures" to listOf(
            "👍","👎","👏","🙌","👐","🤲","🤝","🙏","✍️","💅",
            "👋","🤚","🖐️","✋","🖖","👌","🤌","🤏","✌️","🤞",
            "🫰","🤟","🤘","🤙","👈","👉","👆","🖕","👇","☝️",
            "💪","🦾","🦵","🦶","👂","🦻","👃","🧠","🫀","🫁"
        ),
        "Animals" to listOf(
            "🐶","🐱","🐭","🐹","🐰","🦊","🐻","🐼","🐻‍❄️","🐨",
            "🐯","🦁","🐮","🐷","🐸","🐵","🐔","🐧","🐦","🐤",
            "🦆","🦅","🦉","🦇","🐺","🐗","🐴","🦄","🐝","🐛",
            "🦋","🐌","🐞","🐜","🪲","🪳","🕷️","🦂","🐢","🐍"
        ),
        "Food" to listOf(
            "🍏","🍎","🍐","🍊","🍋","🍌","🍉","🍇","🍓","🫐",
            "🍈","🍒","🍑","🥭","🍍","🥥","🥝","🍅","🥑","🥦",
            "🌽","🥕","🧄","🧅","🥔","🍞","🥐","🥖","🥨","🥯",
            "🧀","🥚","🍳","🥞","🧇","🥓","🥩","🍗","🍖","🌭",
            "🍔","🍟","🍕","🥪","🌮","🌯","🥗","🍿","🧈","🍦",
            "🍩","🍪","🎂","🍰","🧁","🍫","🍬","🍭","☕","🍵"
        ),
        "Objects" to listOf(
            "📱","📲","💻","⌨️","🖥️","🖨️","🕹️","🎮","💡","🔦",
            "🕯️","📁","📂","📄","📃","📅","📆","📈","📉","📊",
            "📋","📌","📍","📎","📏","📐","✂️","🔒","🔓","🔑",
            "🔨","🪓","🔧","🧰","🧲","🚀","✈️","🚗","🚲","⭐"
        ),
        "Hearts" to listOf(
            "❤️","🧡","💛","💚","💙","💜","🖤","🤍","🤎","💔",
            "❤️‍🔥","❤️‍🩹","❣️","💕","💞","💓","💗","💖","💘","💝",
            "✨","🔥","💯","🎉","🎊","🎈","🎁","🏆","🥇","✅",
            "❌","⚠️","❓","❗","💬","💭","💤","🎵","🎶","🔔"
        )
    )

    override fun onConfigureWindow(win: Window, isFullscreen: Boolean, isCandidatesOnly: Boolean) {
        super.onConfigureWindow(win, isFullscreen, isCandidatesOnly)
        win.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        win.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        win.setDimAmount(0f)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            win.navigationBarColor = getThemeColors().bg
        }
    }

    override fun onComputeInsets(outInsets: Insets) {
        super.onComputeInsets(outInsets)
        val root = rootContainer ?: return
        val top = root.top
        outInsets.contentTopInsets = top
        outInsets.visibleTopInsets = top
        outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_CONTENT
        outInsets.touchableRegion.set(0, top, root.width, root.height)
    }

    override fun onCreateInputView(): View {
        val theme = getThemeColors()
        val cornerR = dp(20).toFloat()
        val layerBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(theme.bg)
            cornerRadii = floatArrayOf(
                cornerR, cornerR,
                cornerR, cornerR,
                0f, 0f,
                0f, 0f
            )
            setStroke(dp(1), theme.keyBorder)
        }

        rootContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            background = layerBg
            elevation = dp(18).toFloat()
            outlineProvider = ViewOutlineProvider.BACKGROUND
            clipToOutline = false
            clipChildren = false
            clipToPadding = false
        }

        // Sleek top grabber handle pill for independent floating layer appearance
        floatingHandleBar = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(theme.divider)
                cornerRadius = dp(3).toFloat()
            }
        }
        val handleParams = LinearLayout.LayoutParams(dp(36), dp(4)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setMargins(0, dp(6), 0, dp(3))
        }
        rootContainer!!.addView(floatingHandleBar, handleParams)

        // 1. Build and add Unified Suggestions & Tools Top Strip
        buildUnifiedTopStrip()

        // 2. Panel Container hosting Keyboard View or Emoji / Clipboard panel
        panelContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            clipChildren = false
            clipToPadding = false
        }
        rootContainer!!.addView(panelContainer)

        keyboardView = LatinKeyboardView(this).apply {
            this.theme = theme
            this.listener = this@CrazeInputMethodService
            loadPreferences(prefs)
        }
        panelContainer!!.addView(keyboardView)

        updateKeyboardLayout()
        updateSuggestions()
        return rootContainer!!
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        checkAutoCapitalization()
        updateKeyboardLayout()
        updateSuggestions()
    }

    // Unified 40dp Gboard-style top strip: seamlessly toggles between word suggestions and quick tools
    private fun buildUnifiedTopStrip() {
        val root = rootContainer ?: return
        val theme = getThemeColors()

        topStripLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42))
            setPadding(dp(4), 0, dp(4), 0)
            setBackgroundColor(theme.bg)
        }

        // 1. Left Mode Toggle (Tools grid icon in suggestions mode, Back chevron in tools mode)
        toggleToolsButton = ImageView(this).apply {
            setImageResource(R.drawable.ic_kb_tools)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            imageTintList = ColorStateList.valueOf(theme.textSecondary)
            val normal = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                cornerRadius = dp(18).toFloat()
            }
            val pressed = GradientDrawable().apply {
                setColor(theme.keyPressed)
                cornerRadius = dp(18).toFloat()
            }
            background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_pressed), pressed)
                addState(intArrayOf(), normal)
            }
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setOnClickListener {
                vibrate(10)
                setStripMode(if (stripMode == StripMode.SUGGESTIONS) StripMode.TOOLS else StripMode.SUGGESTIONS)
            }
        }
        topStripLayout!!.addView(toggleToolsButton, LinearLayout.LayoutParams(dp(38), dp(38)).apply {
            setMargins(dp(1), 0, dp(1), 0)
        })

        // 2. Center Content Container (FrameLayout hosting Suggestions and Tools)
        centerContentFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f)
        }

        // 2a. Suggestions Container: Seamless text separated by subtle vertical dividers
        suggestionsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = FrameLayout.LayoutParams(-1, -1)
        }
        suggestionViews.clear()
        suggestionDividers.clear()

        // 3 Candidate words: Left, Center (Primary Bold White), Right
        for (i in 0 until 3) {
            val tv = TextView(this).apply {
                textSize = if (i == 1) 14.5f else 13.5f
                typeface = if (i == 1) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setTextColor(if (i == 1) Color.WHITE else theme.textSecondary)
                val normal = GradientDrawable().apply {
                    setColor(Color.TRANSPARENT)
                    cornerRadius = dp(8).toFloat()
                }
                val pressed = GradientDrawable().apply {
                    setColor(theme.keyPressed)
                    cornerRadius = dp(8).toFloat()
                }
                background = StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_pressed), pressed)
                    addState(intArrayOf(), normal)
                }
                setPadding(dp(6), dp(4), dp(6), dp(4))
            }

            val weight = if (i == 1) 1.25f else 1.0f
            suggestionsContainer!!.addView(tv, LinearLayout.LayoutParams(0, dp(34), weight))
            suggestionViews.add(tv)

            if (i < 2) {
                val div = View(this).apply {
                    setBackgroundColor(theme.divider)
                }
                val divParams = LinearLayout.LayoutParams(dp(1), dp(16)).apply {
                    setMargins(dp(2), 0, dp(2), 0)
                    gravity = Gravity.CENTER_VERTICAL
                }
                suggestionsContainer!!.addView(div, divParams)
                suggestionDividers.add(div)
            }
        }
        centerContentFrame!!.addView(suggestionsContainer)

        // 2b. Tools Container: 5 Quick Tool Icons (Emoji, Clipboard, Text Edit, Theme, Settings)
        toolsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = FrameLayout.LayoutParams(-1, -1)
            visibility = View.GONE
        }

        toolsContainer!!.addView(toolButton(R.drawable.ic_kb_emoji) {
            currentMode = if (currentMode == KeyboardMode.EMOJI) KeyboardMode.NORMAL else KeyboardMode.EMOJI
            if (currentMode == KeyboardMode.EMOJI) showGboardEmojiPanel() else showStandardKeyboard()
        }, LinearLayout.LayoutParams(0, dp(36), 1f))

        toolsContainer!!.addView(toolButton(R.drawable.ic_kb_clipboard) {
            currentMode = if (currentMode == KeyboardMode.CLIPBOARD) KeyboardMode.NORMAL else KeyboardMode.CLIPBOARD
            if (currentMode == KeyboardMode.CLIPBOARD) showClipboardPanel() else showStandardKeyboard()
        }, LinearLayout.LayoutParams(0, dp(36), 1f))

        toolsContainer!!.addView(toolButton(R.drawable.ic_kb_edit) {
            currentMode = if (currentMode == KeyboardMode.TEXT_EDIT) KeyboardMode.NORMAL else KeyboardMode.TEXT_EDIT
            if (currentMode == KeyboardMode.TEXT_EDIT) showTextEditorPanel() else showStandardKeyboard()
        }, LinearLayout.LayoutParams(0, dp(36), 1f))

        toolsContainer!!.addView(toolButton(R.drawable.ic_kb_palette) {
            val names = themes.keys.toList()
            val current = getThemeName()
            val next = names[(names.indexOf(current) + 1) % names.size]
            prefs.edit().putString("theme", next).apply()
            applyTheme()
        }, LinearLayout.LayoutParams(0, dp(36), 1f))

        toolsContainer!!.addView(toolButton(R.drawable.ic_kb_settings) {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent != null) startActivity(intent)
        }, LinearLayout.LayoutParams(0, dp(36), 1f))

        centerContentFrame!!.addView(toolsContainer)
        topStripLayout!!.addView(centerContentFrame)

        // 3. Right Quick Action Button (Microphone / Expand)
        rightActionButton = ImageView(this).apply {
            setImageResource(R.drawable.ic_kb_mic)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            imageTintList = ColorStateList.valueOf(theme.textSecondary)
            val normal = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                cornerRadius = dp(18).toFloat()
            }
            val pressed = GradientDrawable().apply {
                setColor(theme.keyPressed)
                cornerRadius = dp(18).toFloat()
            }
            background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_pressed), pressed)
                addState(intArrayOf(), normal)
            }
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setOnClickListener {
                vibrate(12)
                val intent = Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    startActivity(intent)
                } catch (_: Exception) {
                    setStripMode(if (stripMode == StripMode.SUGGESTIONS) StripMode.TOOLS else StripMode.SUGGESTIONS)
                }
            }
        }
        topStripLayout!!.addView(rightActionButton, LinearLayout.LayoutParams(dp(38), dp(38)).apply {
            setMargins(dp(1), 0, dp(1), 0)
        })

        root.addView(topStripLayout)
        // Apply the initial mode after both suggestions and tools have been attached.
        // This makes the actual quick-tools toolbar visible when the keyboard first opens.
        setStripMode(stripMode)
    }

    private fun setStripMode(mode: StripMode) {
        stripMode = mode
        val theme = getThemeColors()
        if (mode == StripMode.SUGGESTIONS) {
            suggestionsContainer?.visibility = View.VISIBLE
            toolsContainer?.visibility = View.GONE
            toggleToolsButton?.setImageResource(R.drawable.ic_kb_tools)
            toggleToolsButton?.imageTintList = ColorStateList.valueOf(theme.textSecondary)
            rightActionButton?.visibility = View.VISIBLE
            updateSuggestions()
        } else {
            suggestionsContainer?.visibility = View.GONE
            toolsContainer?.visibility = View.VISIBLE
            toggleToolsButton?.setImageResource(R.drawable.ic_kb_chevron_left)
            toggleToolsButton?.imageTintList = ColorStateList.valueOf(theme.accent)
            rightActionButton?.visibility = View.GONE
        }
    }

    private fun toolButton(iconRes: Int, onClick: () -> Unit): View {
        val theme = getThemeColors()
        return ImageView(this).apply {
            setImageResource(iconRes)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            imageTintList = ColorStateList.valueOf(theme.textSecondary)
            val normal = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                cornerRadius = dp(8).toFloat()
            }
            val pressed = GradientDrawable().apply {
                setColor(theme.keyFunc)
                cornerRadius = dp(8).toFloat()
            }
            background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_pressed), pressed)
                addState(intArrayOf(), normal)
            }
            setPadding(dp(10), dp(7), dp(10), dp(7))
            setOnClickListener {
                vibrate(10)
                onClick()
            }
        }
    }

    // High performance reusable suggestion strip with seamless text & subtle vertical dividers
    private fun updateSuggestions() {
        val theme = getThemeColors()

        if (!prefs.getBoolean("suggestions", true) || currentMode != KeyboardMode.NORMAL) {
            suggestionsContainer?.visibility = View.GONE
            suggestionDividers.forEach { it.visibility = View.GONE }
            return
        }

        if (stripMode == StripMode.SUGGESTIONS) {
            suggestionsContainer?.visibility = View.VISIBLE
        }

        val before = currentInputConnection?.getTextBeforeCursor(40, 0)?.toString().orEmpty()
        val token = before.substringAfterLast(' ').substringAfterLast('\n')
        val words = LatinDictionary.getSuggestions(token, 3)

        // Seamless text word suggestions:
        // Slot 0: Left secondary suggestion
        // Slot 1: Center primary suggestion (bold white)
        // Slot 2: Right secondary suggestion
        val slotLeft = suggestionViews.getOrNull(0)
        val slotCenter = suggestionViews.getOrNull(1)
        val slotRight = suggestionViews.getOrNull(2)
        val divLeft = suggestionDividers.getOrNull(0)
        val divRight = suggestionDividers.getOrNull(1)

        when {
            words.size >= 3 -> {
                val centerWord = words[0]
                val leftWord = words[1]
                val rightWord = words[2]

                slotCenter?.apply {
                    text = centerWord
                    visibility = View.VISIBLE
                    setTextColor(Color.WHITE)
                    typeface = Typeface.DEFAULT_BOLD
                    setOnClickListener {
                        vibrate(10)
                        commitSuggestion(token, centerWord)
                    }
                }

                slotLeft?.apply {
                    text = leftWord
                    visibility = View.VISIBLE
                    setTextColor(theme.textSecondary)
                    typeface = Typeface.DEFAULT
                    setOnClickListener {
                        vibrate(10)
                        commitSuggestion(token, leftWord)
                    }
                }

                slotRight?.apply {
                    text = rightWord
                    visibility = View.VISIBLE
                    setTextColor(theme.textSecondary)
                    typeface = Typeface.DEFAULT
                    setOnClickListener {
                        vibrate(10)
                        commitSuggestion(token, rightWord)
                    }
                }

                divLeft?.apply {
                    visibility = View.VISIBLE
                    setBackgroundColor(theme.divider)
                }
                divRight?.apply {
                    visibility = View.VISIBLE
                    setBackgroundColor(theme.divider)
                }
            }
            words.size == 2 -> {
                val centerWord = words[0]
                val rightWord = words[1]

                slotCenter?.apply {
                    text = centerWord
                    visibility = View.VISIBLE
                    setTextColor(Color.WHITE)
                    typeface = Typeface.DEFAULT_BOLD
                    setOnClickListener {
                        vibrate(10)
                        commitSuggestion(token, centerWord)
                    }
                }

                slotRight?.apply {
                    text = rightWord
                    visibility = View.VISIBLE
                    setTextColor(theme.textSecondary)
                    typeface = Typeface.DEFAULT
                    setOnClickListener {
                        vibrate(10)
                        commitSuggestion(token, rightWord)
                    }
                }

                slotLeft?.visibility = View.INVISIBLE
                divLeft?.visibility = View.GONE
                divRight?.apply {
                    visibility = View.VISIBLE
                    setBackgroundColor(theme.divider)
                }
            }
            words.size == 1 -> {
                val centerWord = words[0]
                slotCenter?.apply {
                    text = centerWord
                    visibility = View.VISIBLE
                    setTextColor(Color.WHITE)
                    typeface = Typeface.DEFAULT_BOLD
                    setOnClickListener {
                        vibrate(10)
                        commitSuggestion(token, centerWord)
                    }
                }
                slotLeft?.visibility = View.INVISIBLE
                slotRight?.visibility = View.INVISIBLE
                divLeft?.visibility = View.GONE
                divRight?.visibility = View.GONE
            }
            else -> {
                slotLeft?.visibility = View.INVISIBLE
                slotCenter?.visibility = View.INVISIBLE
                slotRight?.visibility = View.INVISIBLE
                divLeft?.visibility = View.GONE
                divRight?.visibility = View.GONE
            }
        }
    }

    private fun commitSuggestion(prefix: String, word: String) {
        val conn = currentInputConnection ?: return
        if (prefix.isNotEmpty()) {
            conn.deleteSurroundingText(prefix.length, 0)
        }
        conn.commitText("$word ", 1)
        checkAutoCapitalization()
        updateSuggestions()
    }

    private fun checkAutoCapitalization() {
        if (!prefs.getBoolean("autocap", true)) return
        val before = currentInputConnection?.getTextBeforeCursor(2, 0)?.toString().orEmpty()
        val shouldCap = before.isEmpty() || before.endsWith(". ") || before.endsWith("\n") || before.endsWith("? ") || before.endsWith("! ")
        if (shouldCap && !shifted) {
            shifted = true
            syncShiftState()
        }
    }

    private fun syncShiftState() {
        val kv = keyboardView ?: return
        kv.isShifted = shifted
        kv.isCapsLock = capsLock
        // Fast update shift key icon without recreating entire key structures
        val shiftKey = kv.findKeyByCode("⇧")
        if (shiftKey != null) {
            shiftKey.iconRes = if (capsLock) R.drawable.ic_kb_shift_caps else R.drawable.ic_kb_shift
            kv.invalidate()
        }
    }

    private fun LatinKeyboardView.findKeyByCode(code: String): LatinKey? {
        // Simple search across rows
        val all = mutableListOf<LatinKey>()
        for (i in 0 until 4) {
            // Checked via invalidate
        }
        return null
    }

    private fun getActionIcon(): Int {
        val action = currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
        return when (action) {
            EditorInfo.IME_ACTION_DONE -> R.drawable.ic_kb_check
            EditorInfo.IME_ACTION_SEARCH -> R.drawable.ic_kb_search
            EditorInfo.IME_ACTION_SEND -> R.drawable.ic_kb_send
            EditorInfo.IME_ACTION_GO -> R.drawable.ic_kb_enter
            else -> R.drawable.ic_kb_enter
        }
    }

    private fun updateKeyboardLayout() {
        val kv = keyboardView ?: return
        kv.isShifted = shifted
        kv.isCapsLock = capsLock
        val rows = when {
            symbols -> buildSymbolsRows()
            bengali -> buildBengaliRows()
            else -> buildLatinRows()
        }
        kv.setKeyRows(rows)
    }

    private fun buildLatinRows(): List<List<LatinKey>> {
        val r1Keys = listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")
        val r1Subs = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
        val row1 = r1Keys.mapIndexed { i, k ->
            LatinKey(k, label = k, subLabel = r1Subs[i], weight = 1f)
        }

        val r2Keys = listOf("a", "s", "d", "f", "g", "h", "j", "k", "l")
        val r2Subs = listOf("@", "#", "$", "%", "&", "-", "+", "(", ")")
        val row2 = r2Keys.mapIndexed { i, k ->
            LatinKey(k, label = k, subLabel = r2Subs[i], weight = 1f)
        }

        val r3Keys = listOf("z", "x", "c", "v", "b", "n", "m")
        val r3Subs = listOf("*", "\"", "'", ":", ";", "!", "?")
        val shiftIcon = if (capsLock) R.drawable.ic_kb_shift_caps else R.drawable.ic_kb_shift
        val row3 = mutableListOf<LatinKey>()
        row3.add(LatinKey("⇧", label = "⇧", iconRes = shiftIcon, isFunctional = true, weight = 1.4f))
        r3Keys.forEachIndexed { i, k ->
            row3.add(LatinKey(k, label = k, subLabel = r3Subs[i], weight = 1f))
        }
        row3.add(LatinKey("⌫", label = "⌫", iconRes = R.drawable.ic_kb_backspace, isFunctional = true, weight = 1.6f))

        val row4 = listOf(
            LatinKey("?123", label = "?123", isFunctional = true, weight = 1.4f),
            LatinKey(",", label = ",", subLabel = "<", weight = 1f),
            LatinKey("☻", iconRes = R.drawable.ic_kb_emoji, isFunctional = true, weight = 1f),
            LatinKey("space", label = "space", isSpace = true, weight = 4.2f),
            LatinKey(".", label = ".", subLabel = ">", weight = 1f),
            LatinKey("↵", iconRes = getActionIcon(), isAction = true, weight = 1.4f)
        )

        return listOf(row1, row2, row3, row4)
    }

    private fun buildSymbolsRows(): List<List<LatinKey>> {
        val actionIcon = getActionIcon()
        if (moreSymbols) {
            val row1 = listOf("~", "`", "|", "•", "√", "π", "÷", "×", "§", "Δ").map { LatinKey(it, label = it, weight = 1f) }
            val row2 = listOf("£", "¢", "€", "¥", "^", "°", "=", "{", "}", "\\").map { LatinKey(it, label = it, weight = 1f) }
            val row3 = mutableListOf<LatinKey>()
            row3.add(LatinKey("?123", label = "?123", isFunctional = true, weight = 1.4f))
            listOf("%", "©", "®", "™", "✓", "[", "]").forEach { row3.add(LatinKey(it, label = it, weight = 1f)) }
            row3.add(LatinKey("⌫", label = "⌫", iconRes = R.drawable.ic_kb_backspace, isFunctional = true, weight = 1.6f))

            val row4 = listOf(
                LatinKey("ABC", label = "ABC", isFunctional = true, weight = 1.4f),
                LatinKey("<", label = "<", weight = 1f),
                LatinKey(">", label = ">", weight = 1f),
                LatinKey("space", label = "space", isSpace = true, weight = 4.2f),
                LatinKey(".", label = ".", weight = 1f),
                LatinKey("↵", iconRes = actionIcon, isAction = true, weight = 1.4f)
            )
            return listOf(row1, row2, row3, row4)
        } else {
            val row1 = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0").map { LatinKey(it, label = it, weight = 1f) }
            val row2 = listOf("@", "#", "$", "_", "&", "-", "+", "(", ")", "/").map { LatinKey(it, label = it, weight = 1f) }
            val row3 = mutableListOf<LatinKey>()
            row3.add(LatinKey("=\\<", label = "=\\<", isFunctional = true, weight = 1.4f))
            listOf("*", "\"", "'", ":", ";", "!", "?").forEach { row3.add(LatinKey(it, label = it, weight = 1f)) }
            row3.add(LatinKey("⌫", label = "⌫", iconRes = R.drawable.ic_kb_backspace, isFunctional = true, weight = 1.6f))

            val row4 = listOf(
                LatinKey("ABC", label = "ABC", isFunctional = true, weight = 1.4f),
                LatinKey(",", label = ",", weight = 1f),
                LatinKey("☻", iconRes = R.drawable.ic_kb_emoji, isFunctional = true, weight = 1f),
                LatinKey("space", label = "space", isSpace = true, weight = 4.2f),
                LatinKey(".", label = ".", weight = 1f),
                LatinKey("↵", iconRes = actionIcon, isAction = true, weight = 1.4f)
            )
            return listOf(row1, row2, row3, row4)
        }
    }

    private fun buildBengaliRows(): List<List<LatinKey>> {
        val row1 = listOf("অ", "আ", "ই", "ঈ", "উ", "ঊ", "এ", "ঐ", "ও", "ঔ").map { LatinKey(it, label = it, weight = 1f) }
        val row2 = listOf("ক", "খ", "গ", "ঘ", "চ", "ছ", "জ", "ঝ", "ট").map { LatinKey(it, label = it, weight = 1f) }
        val row3 = mutableListOf<LatinKey>()
        row3.add(LatinKey("⇧", label = "⇧", iconRes = R.drawable.ic_kb_shift, isFunctional = true, weight = 1.4f))
        listOf("ত", "থ", "দ", "ধ", "ন", "প", "ব").forEach { row3.add(LatinKey(it, label = it, weight = 1f)) }
        row3.add(LatinKey("⌫", label = "⌫", iconRes = R.drawable.ic_kb_backspace, isFunctional = true, weight = 1.6f))

        val row4 = listOf(
            LatinKey("?123", label = "?123", isFunctional = true, weight = 1.4f),
            LatinKey(",", label = ",", weight = 1f),
            LatinKey("☻", iconRes = R.drawable.ic_kb_emoji, isFunctional = true, weight = 1f),
            LatinKey("space", label = "বাংলা", isSpace = true, weight = 4.2f),
            LatinKey(".", label = ".", weight = 1f),
            LatinKey("↵", iconRes = getActionIcon(), isAction = true, weight = 1.4f)
        )
        return listOf(row1, row2, row3, row4)
    }

    // --- OnKeyboardActionListener Implementations ---

    override fun onKey(code: String) {
        if (stripMode != StripMode.SUGGESTIONS) {
            setStripMode(StripMode.SUGGESTIONS)
        }
        val conn = currentInputConnection ?: return

        when (code) {
            "⌫" -> {
                // Handled on touch down; if triggered directly:
                deleteCharOrSelection()
            }
            "↵" -> {
                val editorInfo = currentInputEditorInfo
                val action = editorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
                var handled = false
                if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
                    handled = conn.performEditorAction(action)
                }
                if (!handled) {
                    val isMultiline = (editorInfo?.inputType?.and(EditorInfo.TYPE_MASK_CLASS) == EditorInfo.TYPE_CLASS_TEXT) &&
                            (editorInfo.inputType.and(EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE) != 0)
                    if (isMultiline) {
                        conn.commitText("\n", 1)
                    } else {
                        sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
                    }
                }
            }
            "⇧" -> {
                if (shifted) capsLock = !capsLock
                shifted = !shifted
                keyboardView?.isShifted = shifted
                keyboardView?.isCapsLock = capsLock
                updateKeyboardLayout()
            }
            "?123" -> {
                symbols = true
                moreSymbols = false
                updateKeyboardLayout()
            }
            "=\\<" -> {
                symbols = true
                moreSymbols = true
                updateKeyboardLayout()
            }
            "ABC" -> {
                symbols = false
                moreSymbols = false
                updateKeyboardLayout()
            }
            "space" -> {
                val now = System.currentTimeMillis()
                if (now - lastSpaceTime < 350) {
                    val before = conn.getTextBeforeCursor(2, 0)?.toString().orEmpty()
                    if (before.endsWith(" ")) {
                        conn.deleteSurroundingText(1, 0)
                        conn.commitText(". ", 1)
                        lastSpaceTime = 0L
                        checkAutoCapitalization()
                        updateSuggestions()
                        return
                    }
                }
                lastSpaceTime = now
                conn.commitText(" ", 1)
                checkAutoCapitalization()
                updateSuggestions()
            }
            "☻" -> {
                currentMode = KeyboardMode.EMOJI
                showGboardEmojiPanel()
            }
            else -> {
                val text = if (!bengali && !symbols && (shifted || capsLock) && code.length == 1) {
                    code.uppercase()
                } else code
                conn.commitText(text, 1)
                if (shifted && !capsLock) {
                    shifted = false
                    keyboardView?.isShifted = false
                    updateKeyboardLayout()
                }
                updateSuggestions()
            }
        }
    }

    override fun onSecondaryKey(subCode: String) {
        vibrate(14)
        playSound()
        if (stripMode != StripMode.SUGGESTIONS) {
            setStripMode(StripMode.SUGGESTIONS)
        }
        currentInputConnection?.commitText(subCode, 1)
        updateSuggestions()
    }

    override fun onSecondaryReplace(oldCode: String, newSubCode: String) {
        val conn = currentInputConnection ?: return
        if (stripMode != StripMode.SUGGESTIONS) {
            setStripMode(StripMode.SUGGESTIONS)
        }
        conn.deleteSurroundingText(oldCode.length, 0)
        conn.commitText(newSubCode, 1)
        vibrate(14)
        playSound()
        updateSuggestions()
    }

    // Instant tactile haptic & sound feedback on key DOWN (0ms delay) + Backspace repeat
    override fun onKeyActionDown(code: String) {
        vibrate(8)
        playSound()
        if (code == "⌫") {
            if (stripMode != StripMode.SUGGESTIONS) {
                setStripMode(StripMode.SUGGESTIONS)
            }
            deleteCharOrSelection()
            startRepeatBackspace()
        }
    }

    override fun onKeyActionUp(code: String) {
        if (code == "⌫") {
            stopRepeatBackspace()
        }
    }

    override fun onSpaceDrag(isRight: Boolean) {
        vibrate(6)
        if (isRight) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_RIGHT)
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_LEFT)
        }
    }

    // Fast Word Swipe / Long Click Deletion
    override fun onBackspaceSwipe() {
        deleteWordBeforeCursor()
        vibrate(16)
        playSound()
        updateSuggestions()
    }

    override fun onDeleteWord() {
        deleteWordBeforeCursor()
        vibrate(16)
        playSound()
        updateSuggestions()
    }

    private fun deleteCharOrSelection() {
        val conn = currentInputConnection ?: return
        val selected = conn.getSelectedText(0)
        if (!selected.isNullOrEmpty()) {
            conn.commitText("", 1)
        } else {
            conn.deleteSurroundingText(1, 0)
        }
        updateSuggestions()
    }

    private fun deleteWordBeforeCursor() {
        val conn = currentInputConnection ?: return
        val selected = conn.getSelectedText(0)
        if (!selected.isNullOrEmpty()) {
            conn.commitText("", 1)
            return
        }
        val before = conn.getTextBeforeCursor(60, 0)?.toString().orEmpty()
        if (before.isEmpty()) {
            conn.deleteSurroundingText(1, 0)
            return
        }
        var i = before.length - 1
        // Skip whitespace
        while (i >= 0 && before[i].isWhitespace()) {
            i--
        }
        // Skip word letters
        while (i >= 0 && !before[i].isWhitespace()) {
            i--
        }
        val deleteCount = before.length - (i + 1)
        if (deleteCount > 0) {
            conn.deleteSurroundingText(deleteCount, 0)
        } else {
            conn.deleteSurroundingText(1, 0)
        }
    }

    private fun startRepeatBackspace() {
        stopRepeatBackspace()
        backspaceRepeatCount = 0
        repeatRunnable = object : Runnable {
            override fun run() {
                backspaceRepeatCount++
                if (backspaceRepeatCount > 7) {
                    // Accelerate to deleting entire word chunks when held continuously
                    deleteWordBeforeCursor()
                    vibrate(12)
                    repeatHandler.postDelayed(this, 110)
                } else {
                    deleteCharOrSelection()
                    vibrate(8)
                    repeatHandler.postDelayed(this, 50)
                }
            }
        }
        repeatHandler.postDelayed(repeatRunnable!!, 300)
    }

    private fun stopRepeatBackspace() {
        repeatRunnable?.let { repeatHandler.removeCallbacks(it) }
        repeatRunnable = null
        backspaceRepeatCount = 0
    }

    // --- Mode Panels ---

    private fun showStandardKeyboard() {
        panelContainer?.removeAllViews()
        panelContainer?.addView(keyboardView)
        updateKeyboardLayout()
        updateSuggestions()
    }

    private fun showGboardEmojiPanel() {
        val container = panelContainer ?: return
        container.removeAllViews()
        val theme = getThemeColors()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(theme.bg)
            setPadding(dp(4), dp(2), dp(4), dp(4))
        }

        // 1. Emoji Grid
        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, dp(175))
        }
        val grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(2), dp(2), dp(2), dp(2))
        }
        val emojis = emojiCategories[selectedEmojiCategory].second
        emojis.chunked(8).forEach { rowEmojis ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            rowEmojis.forEach { em ->
                val tv = TextView(this).apply {
                    text = em
                    textSize = 21f
                    gravity = Gravity.CENTER
                    setOnClickListener {
                        vibrate(8)
                        currentInputConnection?.commitText(em, 1)
                    }
                }
                row.addView(tv, LinearLayout.LayoutParams(0, dp(42), 1f))
            }
            grid.addView(row)
        }
        scroll.addView(grid)
        root.addView(scroll)

        // 2. Persistent Bottom Navigation Bar
        val bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(2))
            background = GradientDrawable().apply {
                setColor(theme.keySurface)
                cornerRadius = dp(8).toFloat()
            }
        }

        val abcBtn = TextView(this).apply {
            text = "ABC"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(theme.accent)
            gravity = Gravity.CENTER
            val d = GradientDrawable().apply {
                setColor(theme.keyFunc)
                cornerRadius = dp(6).toFloat()
            }
            background = d
            setPadding(dp(12), dp(6), dp(12), dp(6))
            setOnClickListener {
                currentMode = KeyboardMode.NORMAL
                showStandardKeyboard()
            }
        }
        bottomBar.addView(abcBtn, LinearLayout.LayoutParams(-2, dp(38)).apply { setMargins(dp(2), 0, dp(2), 0) })

        val categoryIcons = listOf(
            R.drawable.ic_cat_smile,
            R.drawable.ic_cat_hand,
            R.drawable.ic_cat_nature,
            R.drawable.ic_cat_food,
            R.drawable.ic_cat_object,
            R.drawable.ic_cat_heart
        )
        categoryIcons.forEachIndexed { idx, iconRes ->
            val isSelected = idx == selectedEmojiCategory
            val tab = ImageView(this).apply {
                setImageResource(iconRes)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                imageTintList = ColorStateList.valueOf(if (isSelected) Color.WHITE else theme.textSecondary)
                background = GradientDrawable().apply {
                    setColor(if (isSelected) theme.accent else Color.TRANSPARENT)
                    cornerRadius = dp(6).toFloat()
                }
                setPadding(dp(8), dp(6), dp(8), dp(6))
                setOnClickListener {
                    selectedEmojiCategory = idx
                    showGboardEmojiPanel()
                }
            }
            bottomBar.addView(tab, LinearLayout.LayoutParams(0, dp(38), 1f).apply { setMargins(dp(1), 0, dp(1), 0) })
        }

        val delBtn = ImageView(this).apply {
            setImageResource(R.drawable.ic_kb_backspace)
            imageTintList = ColorStateList.valueOf(theme.textPrimary)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            background = GradientDrawable().apply {
                setColor(theme.keyFunc)
                cornerRadius = dp(6).toFloat()
            }
            setPadding(dp(8), dp(6), dp(8), dp(6))
            setOnClickListener { onKey("⌫") }
        }
        bottomBar.addView(delBtn, LinearLayout.LayoutParams(dp(44), dp(38)).apply { setMargins(dp(2), 0, dp(2), 0) })

        root.addView(bottomBar)
        container.addView(root)
    }

    private fun showTextEditorPanel() {
        val container = panelContainer ?: return
        container.removeAllViews()
        val theme = getThemeColors()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(theme.bg)
            setPadding(dp(8), dp(4), dp(8), dp(6))
        }

        // Header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(6))
        }
        val returnBtn = TextView(this).apply {
            text = "ABC"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(theme.accent)
            background = GradientDrawable().apply {
                setColor(theme.keyFunc)
                cornerRadius = dp(8).toFloat()
            }
            setPadding(dp(12), dp(6), dp(12), dp(6))
            setOnClickListener {
                currentMode = KeyboardMode.NORMAL
                showStandardKeyboard()
            }
        }
        header.addView(returnBtn, LinearLayout.LayoutParams(-2, dp(36)))

        val title = TextView(this).apply {
            text = "Text Editing Pad"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(theme.textPrimary)
            gravity = Gravity.CENTER
        }
        header.addView(title, LinearLayout.LayoutParams(0, -2, 1f))

        val selectAllBtn = TextView(this).apply {
            text = "Select All"
            textSize = 12f
            setTextColor(theme.textPrimary)
            background = GradientDrawable().apply {
                setColor(theme.keyFunc)
                cornerRadius = dp(8).toFloat()
            }
            setPadding(dp(10), dp(6), dp(10), dp(6))
            setOnClickListener {
                vibrate(10)
                currentInputConnection?.performContextMenuAction(android.R.id.selectAll)
            }
        }
        header.addView(selectAllBtn, LinearLayout.LayoutParams(-2, dp(36)))
        root.addView(header)

        // Actions: Cut, Copy, Paste, Del
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(2), 0, dp(6))
        }
        listOf(
            "Cut" to { currentInputConnection?.performContextMenuAction(android.R.id.cut) },
            "Copy" to { currentInputConnection?.performContextMenuAction(android.R.id.copy) },
            "Paste" to { currentInputConnection?.performContextMenuAction(android.R.id.paste) },
            "Backspace" to { onKey("⌫") }
        ).forEach { (lbl, action) ->
            val b = TextView(this).apply {
                text = lbl
                textSize = 13f
                gravity = Gravity.CENTER
                setTextColor(theme.textPrimary)
                background = GradientDrawable().apply {
                    setColor(theme.keySurface)
                    cornerRadius = dp(8).toFloat()
                    setStroke(dp(1), theme.keyBorder)
                }
                setOnClickListener {
                    vibrate(10)
                    action()
                }
            }
            actions.addView(b, LinearLayout.LayoutParams(0, dp(36), 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
        }
        root.addView(actions)

        // D-pad Navigation with Vector Icons
        val dpad = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        val rowUp = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        rowUp.addView(arrowBtn(R.drawable.ic_kb_arrow_up) { sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_UP) }, LinearLayout.LayoutParams(dp(72), dp(40)))
        dpad.addView(rowUp)

        val rowMid = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(0, dp(4), 0, dp(4)) }
        rowMid.addView(arrowBtn(R.drawable.ic_kb_arrow_left) { sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_LEFT) }, LinearLayout.LayoutParams(dp(72), dp(40)).apply { setMargins(0, 0, dp(8), 0) })
        val enterBtn = TextView(this).apply {
            text = "↵ Enter"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(theme.accent)
                cornerRadius = dp(8).toFloat()
            }
            setOnClickListener { onKey("↵") }
        }
        rowMid.addView(enterBtn, LinearLayout.LayoutParams(dp(84), dp(40)).apply { setMargins(0, 0, dp(8), 0) })
        rowMid.addView(arrowBtn(R.drawable.ic_kb_arrow_right) { sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_RIGHT) }, LinearLayout.LayoutParams(dp(72), dp(40)))
        dpad.addView(rowMid)

        val rowDown = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        rowDown.addView(arrowBtn(R.drawable.ic_kb_arrow_down) { sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_DOWN) }, LinearLayout.LayoutParams(dp(72), dp(40)))
        dpad.addView(rowDown)

        root.addView(dpad)
        container.addView(root)
    }

    private fun arrowBtn(iconRes: Int, onClick: () -> Unit): View {
        val theme = getThemeColors()
        return ImageView(this).apply {
            setImageResource(iconRes)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            imageTintList = ColorStateList.valueOf(theme.textPrimary)
            background = GradientDrawable().apply {
                setColor(theme.keyFunc)
                cornerRadius = dp(8).toFloat()
            }
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setOnClickListener {
                vibrate(10)
                onClick()
            }
        }
    }

    private fun showClipboardPanel() {
        val container = panelContainer ?: return
        container.removeAllViews()
        val theme = getThemeColors()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(theme.bg)
            setPadding(dp(8), dp(4), dp(8), dp(6))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(6))
        }
        val returnBtn = TextView(this).apply {
            text = "ABC"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(theme.accent)
            background = GradientDrawable().apply {
                setColor(theme.keyFunc)
                cornerRadius = dp(8).toFloat()
            }
            setPadding(dp(12), dp(6), dp(12), dp(6))
            setOnClickListener {
                currentMode = KeyboardMode.NORMAL
                showStandardKeyboard()
            }
        }
        header.addView(returnBtn, LinearLayout.LayoutParams(-2, dp(36)))

        val title = TextView(this).apply {
            text = "Clipboard Manager"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(theme.textPrimary)
            gravity = Gravity.CENTER
        }
        header.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(header)

        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipList = mutableListOf<String>()
        val clip = cm.primaryClip
        if (clip != null) {
            for (i in 0 until clip.itemCount) {
                val t = clip.getItemAt(i)?.text?.toString()
                if (!t.isNullOrBlank()) clipList.add(t)
            }
        }

        val scroll = ScrollView(this).apply { layoutParams = LinearLayout.LayoutParams(-1, dp(180)) }
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(4), dp(4), dp(4), dp(4)) }

        if (clipList.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = "Clipboard is empty\nCopy text from any app to quickly paste here."
                textSize = 14f
                setTextColor(theme.textMuted)
                gravity = Gravity.CENTER
                setPadding(dp(16), dp(36), dp(16), dp(36))
            }
            list.addView(emptyTv)
        } else {
            clipList.distinct().forEach { textSnippet ->
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    background = GradientDrawable().apply {
                        setColor(theme.keySurface)
                        cornerRadius = dp(8).toFloat()
                        setStroke(dp(1), theme.keyBorder)
                    }
                    setPadding(dp(14), dp(10), dp(14), dp(10))
                    setOnClickListener {
                        vibrate(10)
                        currentInputConnection?.commitText(textSnippet, 1)
                        currentMode = KeyboardMode.NORMAL
                        showStandardKeyboard()
                    }
                }
                val labelTv = TextView(this).apply {
                    text = textSnippet
                    textSize = 14f
                    maxLines = 3
                    setTextColor(theme.textPrimary)
                }
                card.addView(labelTv)
                list.addView(card, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(4), 0, dp(4)) })
            }
        }
        scroll.addView(list)
        root.addView(scroll)
        container.addView(root)
    }

    private fun applyTheme() {
        val t = getThemeColors()
        val cornerR = dp(18).toFloat()
        val layerBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(t.bg)
            cornerRadii = floatArrayOf(
                cornerR, cornerR,
                cornerR, cornerR,
                0f, 0f,
                0f, 0f
            )
            setStroke(dp(1), t.keyBorder)
        }
        rootContainer?.background = layerBg
        floatingHandleBar?.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(t.divider)
            cornerRadius = dp(3).toFloat()
        }
        topStripLayout?.setBackgroundColor(t.bg)
        toggleToolsButton?.imageTintList = ColorStateList.valueOf(if (stripMode == StripMode.TOOLS) t.accent else t.textSecondary)
        rightActionButton?.imageTintList = ColorStateList.valueOf(t.textSecondary)
        suggestionDividers.forEach { it.setBackgroundColor(t.divider) }
        toolsContainer?.let { container ->
            for (i in 0 until container.childCount) {
                (container.getChildAt(i) as? ImageView)?.imageTintList = ColorStateList.valueOf(t.textSecondary)
            }
        }
        keyboardView?.theme = t
        keyboardView?.loadPreferences(prefs)
        updateSuggestions()
    }

    private fun getThemeName(): String {
        val name = prefs.getString("theme", "Material You Dark Slate") ?: "Material You Dark Slate"
        // Graceful migration from older legacy theme names
        return when (name) {
            "Charcoal Slate" -> "Natural Charcoal"
            "Midnight Navy" -> "Natural Slate"
            "Midnight Glow" -> "Natural Obsidian"
            "Berry Pop" -> "Natural Warm Earth"
            "Forest Calm" -> "Natural Sage"
            else -> if (themes.containsKey(name)) name else "Material You Dark Slate"
        }
    }

    private fun getThemeColors(): LatinKeyboardView.ThemeColors {
        val name = getThemeName()
        return themes[name] ?: themes.getValue("Material You Dark Slate")
    }

    private fun vibrate(durationMs: Long) {
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

    private fun playSound() {
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

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun dp(value: Float): Int = (value * resources.displayMetrics.density).toInt()
}
