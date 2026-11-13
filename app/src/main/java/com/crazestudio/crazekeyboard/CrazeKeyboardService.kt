package com.crazestudio.crazekeyboard

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView

// A key = full-size touch cell (includes the gap around the key, so taps between keys still land) + the visible face (+ optional SVG icon).
internal class K(val cell: FrameLayout, val tv: TextView, val iv: ImageView?)

class CrazeKeyboardService : InputMethodService() {
    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    private val ctx: Context get() = this
    private val hd = Handler(Looper.getMainLooper())
    // Editor reads and ranking are isolated from the keyboard UI thread.
    private val suggestionThread = HandlerThread("craze-suggestions", Process.THREAD_PRIORITY_BACKGROUND).apply { start() }
    private val suggestionWorker = Handler(suggestionThread.looper)
    @Volatile private var cachedBeforeCursor = ""
    @Volatile private var cachedHasEditorText = false
    @Volatile private var contextEpoch = 0L
    @Volatile private var predictionEpoch = 0L
    @Volatile private var refinedPredictionEpoch = -1L
    private var predictionTask: Runnable? = null
    private var waitingForPhraseModel = false
    private var waitingForVocabulary = false
    @Volatile private var cachedCorrection: Pair<String, String>? = null
    private var pendingCorrectedWord: Pair<List<String>, String>? = null

    private val contextSyncR = Runnable {
        val epoch = contextEpoch
        val connection = currentInputConnection
        if (connection != null) {
            val before = try { connection.getTextBeforeCursor(160, 0)?.toString().orEmpty() } catch (_: Exception) { "" }
            val hasAfter = try { !connection.getTextAfterCursor(1, 0).isNullOrEmpty() } catch (_: Exception) { false }
            hd.post {
                if (epoch == contextEpoch && connection === currentInputConnection) {
                    cachedBeforeCursor = before
                    cachedHasEditorText = before.isNotEmpty() || hasAfter
                    scheduleRefresh()
                }
            }
        }
    }

    private lateinit var outer: FrameLayout
    private lateinit var kbd: LinearLayout
    private lateinit var top: FrameLayout
    private lateinit var panel: FrameLayout
    private var targetInputConnection: InputConnection? = null
    private val sugViews = ArrayList<TextView>()
    // Keep the last completed model result visible while the next inference is running.
    private val lastSuggestions = arrayOf("", "", "")
    private var lastTopState = ""
    private val letterKeys = ArrayList<Pair<TextView, String>>()
    private val enterKeys = ArrayList<K>()
    private val seen = HashMap<String, Int>()
    // Pages are built once per input view and reused: switching ?123 / ABC / menu no longer rebuilds ~40 views each time.
    private val pageCache = HashMap<String, View>()
    private var shiftKey: K? = null
    private var t = Prefs.themes[0]
    private var shift = 0
    private var autoShift = false
    private var mode = "letters"
    private var menuCategory = "All"
    private var floating = false
    private var suggestOn = true
    private var incognito = false
    private var autoCorrectOn = true
    private var doubleSpaceOn = true
    private var nextWordOn = true
    private var lastCorrection: Pair<String, String>? = null
    private var lastShiftTap = 0L
    private var popup: PopupWindow? = null
    private var preview: PopupWindow? = null
    private var prevTv: TextView? = null
    private var lastSig = ""
    private var builtOrientation = -1
    private var kh = 46
    private var cellH = 0
    private var hapticsOn = true
    private var keySoundOn = false
    private var numRow = true
    private var emojiSearchQuery = ""
    private var emojiSearchActive = false
    private var clipListener: ClipboardManager.OnPrimaryClipChangedListener? = null
    private val noPersonalizedLearning = 0x1000000   // EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING (API 26) without the API-level gate

    private data class EmojiGroup(val key: String, val name: String, val terms: String, val emojis: List<String>)
    private val emojiGroups: List<EmojiGroup> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val metadata = mapOf(
            "smile" to Triple("😀", "Smileys and emotion", "smile happy face laugh sad emotion"),
            "people" to Triple("👋", "People and body", "people person body hand gesture family"),
            "nature" to Triple("🐶", "Animals and nature", "animal pet plant flower nature"),
            "food" to Triple("🍔", "Food and drink", "food drink fruit vegetable meal"),
            "activity" to Triple("⚽", "Activity", "sport game music art celebration"),
            "travel" to Triple("🚗", "Travel and places", "travel place transport vehicle building weather"),
            "objects" to Triple("📱", "Objects", "object technology tool household"),
            "symbols" to Triple("✅", "Symbols", "symbol sign warning heart arrow number"),
            "flags" to Triple("🇺🇳", "Flags", "flag country region")
        )
        val parsed = LinkedHashMap<String, MutableList<String>>()
        var current = ""
        assets.open("emoji_catalog.txt").bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.forEach { line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                    current = trimmed.removePrefix("[").removeSuffix("]")
                    parsed.getOrPut(current) { ArrayList() }
                } else if (current.isNotEmpty() && trimmed.isNotEmpty()) {
                    parsed.getOrPut(current) { ArrayList() }.addAll(trimmed.split(" ").filter { it.isNotBlank() })
                }
            }
        }
        parsed.mapNotNull { (id, values) ->
            val meta = metadata[id] ?: return@mapNotNull null
            EmojiGroup(meta.first, meta.second, meta.third, values.distinct())
        }
    }
    private val emojiCats: Map<String, String> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        linkedMapOf<String, String>().apply {
            put("🕘", "")
            emojiGroups.forEach { put(it.key, it.emojis.joinToString(" ")) }
        }
    }
    private val emojiLists: Map<String, List<String>> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        emojiCats.mapValues { (_, value) -> value.split(" ").filter { it.isNotEmpty() } }
    }
    private val emojiNames: Map<String, Pair<String, String>> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        HashMap<String, Pair<String, String>>().apply {
            assets.open("emoji_names.tsv").bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.forEach { line ->
                    val parts = line.split('\t')
                    if (parts.size >= 3) put(parts[0], parts[1] to parts.drop(2).joinToString(" "))
                }
            }
        }
    }
    private fun emojiInfo(value: String): Pair<String, String> =
        emojiNames[value] ?: emojiNames[value.replace("\uFE0F", "")] ?: ("Emoji" to "")
    // ---------- sizing helpers ----------
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun lp(w: Int, h: Int, wt: Float = 0f) = LinearLayout.LayoutParams(w, h, wt)
    private fun landscape() = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    private fun loadPrefs() {
        val base = if (landscape()) minOf(Prefs.keyH(this), 38) else Prefs.keyH(this)
        kh = if (floating) (base * 0.84f).toInt() else base
        cellH = baseCellH()
        hapticsOn = Prefs.b(this, "haptics", true)
        keySoundOn = Prefs.b(this, "keysound", false)
        numRow = Prefs.b(this, "numrow", true)
        autoCorrectOn = Prefs.b(this, "autocorrect", true)
        doubleSpaceOn = Prefs.b(this, "doubleSpacePeriod", true)
        nextWordOn = Prefs.b(this, "nextWord", true)
    }
    private fun rowCountFor(page: String): Int = when (page) {
        "letters" -> if (numRow) 5 else 4
        "symbols" -> 4
        "symbols2" -> if (numRow) 5 else 4
        else -> 5
    }
    private fun baseCellH() = dp(kh) + dp(6)
    // Keep the keyboard's total height fixed. When the number row is hidden, the remaining
    // letter rows expand evenly instead of the whole keyboard collapsing upward.
    private fun panelH() = baseCellH() * 5 + dp(4)
    private fun topH() = dp(42)

    // ---------- drawing helpers ----------
    private fun darken(c: Int, f: Float = 0.78f): Int {
        val r = ((c shr 16 and 0xFF) * f).toInt()
        val g = ((c shr 8 and 0xFF) * f).toInt()
        val b = ((c and 0xFF) * f).toInt()
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
    private fun rounded(c: Int, r: Int = 11) = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; cornerRadius = dp(r).toFloat(); setColor(c) }
    // Key face with a soft lower edge and more generous corner radius.
    private fun keyBg(c: Int, r: Int = 11): Drawable =
        LayerDrawable(arrayOf<Drawable>(rounded(darken(c), r), rounded(c, r))).apply { setLayerInset(1, 0, 0, 0, dp(2)) }

    // Tinted SVG (vector drawable) button
    private fun iconImg(res: Int, a: () -> Unit) = ImageView(ctx).apply {
        setImageResource(res); setColorFilter(t.text); scaleType = ImageView.ScaleType.CENTER_INSIDE
        setPadding(dp(10), dp(10), dp(10), dp(10)); setOnClickListener { a() }
    }

    override fun onCreate() {
        super.onCreate()
        CrazeLmTflitePredictor.initializeAsync(applicationContext)
        DownloadedVocabularyIndex.loadInstalledAsync(applicationContext)
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipListener = ClipboardManager.OnPrimaryClipChangedListener {
            try {
                if (incognito || !Prefs.b(ctx, "clipboardHistory", true)) return@OnPrimaryClipChangedListener
                val clip = cm.primaryClip ?: return@OnPrimaryClipChangedListener
                // Android 13+ marks copied passwords / OTPs as sensitive: never store those.
                if (Build.VERSION.SDK_INT >= 33 &&
                    clip.description?.extras?.getBoolean("android.content.extra.IS_SENSITIVE", false) == true) return@OnPrimaryClipChangedListener
                if (clip.itemCount > 0) clip.getItemAt(0)?.text?.toString()?.let { Prefs.addClip(ctx, it) }
            } catch (e: Exception) { }
        }
        cm.addPrimaryClipChangedListener(clipListener)
    }

    override fun onDestroy() {
        try { (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).removePrimaryClipChangedListener(clipListener) } catch (e: Exception) { }
        hd.removeCallbacksAndMessages(null)
        dismissPopups()
        VoiceActivity.callback = null   // the callback captures this service; don't leak it
        TextEditorActivity.callback = null
        suggestionWorker.removeCallbacksAndMessages(null)
        suggestionThread.quitSafely()
        pageCache.clear()
        super.onDestroy()
    }

    // Fullscreen extract mode in landscape is slow and covers the app, so turn it off.
    override fun onEvaluateFullscreenMode(): Boolean = false

    // Floating mode: the keyboard window is tall and transparent, but only the keyboard itself takes touches
    // and the app underneath is not resized.
    override fun onComputeInsets(outInsets: InputMethodService.Insets) {
        super.onComputeInsets(outInsets)
        if (floating && ::kbd.isInitialized && ::outer.isInitialized && kbd.height > 0) {
            val h = maxOf(outer.height, window.window?.decorView?.height ?: 0)
            outInsets.contentTopInsets = h
            outInsets.visibleTopInsets = h
            val loc = IntArray(2)
            kbd.getLocationInWindow(loc)
            outInsets.touchableInsets = InputMethodService.Insets.TOUCHABLE_INSETS_REGION
            outInsets.touchableRegion.set(loc[0], loc[1], loc[0] + kbd.width, loc[1] + kbd.height)
        }
    }

    private fun resetPages() {
        pageCache.clear()
        letterKeys.clear(); enterKeys.clear()
        shiftKey = null
    }

    override fun onCreateInputView(): View {
        t = Prefs.theme(this)
        try {
            window.window?.navigationBarColor = t.bg
            if (Build.VERSION.SDK_INT >= 29) window.window?.isNavigationBarContrastEnforced = false
        } catch (_: Exception) { }
        lastSig = Prefs.sig(this)
        floating = Prefs.b(this, "float", false)
        builtOrientation = resources.configuration.orientation
        loadPrefs()
        dismissPopups()
        resetPages()
        val sw = resources.displayMetrics.widthPixels
        val sh = resources.displayMetrics.heightPixels
        outer = FrameLayout(this).apply {
            clipChildren = false
            clipToPadding = false
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        kbd = root
        top = FrameLayout(this)
        panel = FrameLayout(this)
        if (floating) {
            root.background = rounded(t.bg, 18)
            root.elevation = dp(8).toFloat()
            root.setPadding(dp(2), 0, dp(2), dp(6))
            root.addView(buildHandle(), lp(MATCH, dp(26)))
        } else {
            root.setBackgroundColor(t.bg)
            root.setPadding(0, 0, 0, dp(6))
        }
        root.addView(top, lp(MATCH, topH()))
        root.addView(panel, lp(MATCH, panelH()))
        if (floating) {
            val minWidth = minOf(dp(220), sw)
            val fw = (sw * Prefs.keyWidth(this) / 100f).toInt().coerceIn(minWidth, sw)
            outer.minimumHeight = (sh * 0.92f).toInt()
            val p = FrameLayout.LayoutParams(fw, WRAP, Gravity.TOP or Gravity.START)
            p.leftMargin = Prefs.i(this, "fx", (sw - fw) / 2).coerceIn(0, maxOf(0, sw - fw))
            p.topMargin = Prefs.i(this, "fy", (sh * 0.30f).toInt()).coerceIn(0, (sh * 0.5f).toInt())
            outer.addView(root, p)
        } else {
            val oh = Prefs.i(this, "onehand", 0)
            val minWidth = minOf(dp(220), sw)
            // One-handed width is independent of floating-keyboard width; the old default was 100%,
            // which made one-handed mode look enabled while leaving the keyboard full-width.
            val w = if (oh == 0) MATCH else
                (sw * Prefs.oneHandWidth(this) / 100f).toInt().coerceIn(minWidth, sw)
            outer.addView(root, FrameLayout.LayoutParams(w, WRAP, if (oh == 2) Gravity.END else Gravity.START))
        }
        buildTop()
        showPanel(mode)
        return outer
    }

    private fun rebuild() {
        mode = "letters"
        setInputView(onCreateInputView())
    }

    // Drag bar of the floating keyboard: drag anywhere on it to move, tap the dock icon to dock again.
    private fun buildHandle(): View {
        val row = FrameLayout(ctx)
        val grip = ImageView(ctx).apply {
            setImageResource(R.drawable.ic_grip)
            setColorFilter(t.text)
            alpha = 0.65f
            contentDescription = "Move floating keyboard"
        }
        row.addView(grip, FrameLayout.LayoutParams(dp(38), dp(22), Gravity.CENTER))
        val dock = iconImg(R.drawable.ic_dock) {
            Prefs.setB(ctx, "float", false)
            rebuild()
        }.apply {
            contentDescription = "Dock keyboard"
            setPadding(dp(4), dp(3), dp(4), dp(3))
        }
        row.addView(dock, FrameLayout.LayoutParams(dp(40), dp(26), Gravity.END or Gravity.CENTER_VERTICAL))
        val resize = TextView(ctx).apply {
            text = "↔"
            textSize = 19f
            gravity = Gravity.CENTER
            setTextColor(t.text)
            background = rounded(t.special, 8)
            contentDescription = "Resize floating keyboard"
            setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        val params = kbd.layoutParams as? FrameLayout.LayoutParams
                            ?: return@setOnTouchListener false
                        resizeStartWidth = if (params.width > 0) params.width else kbd.width
                        resizeStartX = event.rawX
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val params = kbd.layoutParams as? FrameLayout.LayoutParams
                            ?: return@setOnTouchListener false
                        val screenWidth = resources.displayMetrics.widthPixels
                        val minWidth = minOf(dp(220), screenWidth)
                        params.width = (resizeStartWidth + (event.rawX - resizeStartX).toInt())
                            .coerceIn(minWidth, screenWidth)
                        kbd.layoutParams = params
                        Prefs.setI(ctx, "kbdwidth", (params.width * 100 / screenWidth).coerceIn(55, 100))
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> true
                    else -> true
                }
            }
        }
        row.addView(resize, FrameLayout.LayoutParams(dp(36), dp(26), Gravity.END or Gravity.CENTER_VERTICAL).apply {
            rightMargin = dp(42)
        })
        var downX = 0f
        var downY = 0f
        var startL = 0
        var startT = 0
        row.setOnTouchListener { _, event ->
            val params = kbd.layoutParams as? FrameLayout.LayoutParams
                ?: return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX; downY = event.rawY
                    startL = params.leftMargin; startT = params.topMargin
                }
                MotionEvent.ACTION_MOVE -> {
                    val maxX = maxOf(0, outer.width - kbd.width)
                    val maxY = maxOf(0, outer.height - kbd.height)
                    params.leftMargin = (startL + (event.rawX - downX).toInt()).coerceIn(0, maxX)
                    params.topMargin = (startT + (event.rawY - downY).toInt()).coerceIn(0, maxY)
                    kbd.layoutParams = params
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    Prefs.setI(ctx, "fx", params.leftMargin)
                    Prefs.setI(ctx, "fy", params.topMargin)
                }
            }
            true
        }
        return row
    }

    private var resizeStartX = 0f
    private var resizeStartWidth = 0
    private var editorSelecting = false

    private fun isPasswordField(cls: Int, v: Int): Boolean =
        (cls == InputType.TYPE_CLASS_TEXT && (v == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            v == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD || v == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)) ||
            (cls == InputType.TYPE_CLASS_NUMBER && v == InputType.TYPE_NUMBER_VARIATION_PASSWORD)

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        emojiSearchActive = false
        emojiSearchQuery = ""
        if (info.packageName != packageName) targetInputConnection = currentInputConnection
        val orient = resources.configuration.orientation
        if (!::outer.isInitialized || Prefs.sig(this) != lastSig || Prefs.b(this, "float", false) != floating || orient != builtOrientation) {
            setInputView(onCreateInputView())
        } else loadPrefs()
        val cls = info.inputType and InputType.TYPE_MASK_CLASS
        val v = info.inputType and InputType.TYPE_MASK_VARIATION
        val noSug = listOf(InputType.TYPE_TEXT_VARIATION_PASSWORD, InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD, InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS, InputType.TYPE_TEXT_VARIATION_URI)
        suggestOn = Prefs.b(this, "suggest", true) && cls == InputType.TYPE_CLASS_TEXT && v !in noSug &&
            (info.inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) == 0
        // Private fields: don't learn words from them and don't record the clipboard while they are focused.
        incognito = isPasswordField(cls, v) || (info.imeOptions and noPersonalizedLearning) != 0
        shift = 0
        autoShift = false
        editorSelecting = false
        lastSuggestions.fill("")
        lastCorrection = null
        pendingCorrectedWord = null
        cachedBeforeCursor = ""
        cachedHasEditorText = false
        contextEpoch++
        mode = if (cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE) "symbols" else "letters"
        buildTop()
        showPanel(mode)
        syncContextAsync()
        updateSuggestions()
        deliverVoice()
    }

    // Cursor moved / text changed outside the keyboard. Debounced so rapid edits cost one refresh.
    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        syncContextAsync()
        if (::top.isInitialized) scheduleRefresh()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        hd.removeCallbacksAndMessages(null)   // pending refresh, key-repeat and long-press timers must not outlive the input view
        suggestionWorker.removeCallbacks(contextSyncR)
        predictionTask?.let(suggestionWorker::removeCallbacks)
        predictionEpoch++
        contextEpoch++
        dismissPopups()
        lastCorrection = null
        pendingCorrectedWord = null
        super.onFinishInputView(finishingInput)
    }

    // ---------- debounced refresh (suggestions + auto-capitalize) ----------
    private val refreshR = Runnable { if (::top.isInitialized) { autoCap(); updateSuggestions() } }
    private fun scheduleRefresh() {
        hd.removeCallbacks(refreshR)
        hd.postDelayed(refreshR, 38)
    }

    // ---------- top bar: menu button + suggestions ----------
    private fun iconBtn(label: String, size: Float = 18f, a: () -> Unit) = TextView(ctx).apply {
        text = label; textSize = size; gravity = Gravity.CENTER; setTextColor(t.text)
        setOnClickListener { a() }
    }

    // ---------- adaptive top bar ----------
    private fun hasEditorText(): Boolean = cachedHasEditorText || cachedBeforeCursor.isNotEmpty()

    /** Read target-editor context off the UI thread; callers only read the local snapshot. */
    private fun syncContextAsync() {
        suggestionWorker.removeCallbacks(contextSyncR)
        suggestionWorker.postDelayed(contextSyncR, 45)
    }

    private fun setLocalContext(value: String) {
        cachedBeforeCursor = value.takeLast(160)
        cachedHasEditorText = cachedBeforeCursor.isNotEmpty()
        contextEpoch++
    }

    private fun tokensBeforeCursor(value: String): List<String> =
        value.lowercase().split(Regex("[^\\p{L}']+")).filter { it.isNotBlank() }.takeLast(3)

    private fun wordAtCursor(value: String): String =
        value.takeLastWhile { it.isLetter() || it == '\'' }

    private fun desiredTopState(): String = when {
        emojiSearchActive -> "emojiSearch"
        mode == "menu" -> "menu"
        // Private fields still receive generic dictionary/model suggestions.
        // Incognito only disables personalization and all persistent learning.
        !suggestOn || !hasEditorText() -> "tools"
        else -> "suggestions"
    }

    private fun toolLabel(id: String): String = when (id) {
        "editor" -> "Text editor"
        "clipboard" -> "Clipboard"
        "emoji" -> "Emoji"
        "voice" -> "Voice"
        "theme" -> "Theme"
        "onehand" -> "One-handed"
        "floating" -> if (floating) "Dock" else "Floating"
        "resize" -> "Resize"
        "settings" -> "Settings"
        "keyboard" -> "Keyboard"
        "hide" -> "Hide"
        else -> "Tool"
    }

    private fun toolIcon(id: String): Int = when (id) {
        "editor" -> R.drawable.ic_edit
        "clipboard" -> R.drawable.ic_clip
        "emoji" -> R.drawable.ic_emoji
        "voice" -> R.drawable.ic_mic
        "theme" -> R.drawable.ic_palette
        "onehand" -> R.drawable.ic_onehand
        "floating" -> if (floating) R.drawable.ic_dock else R.drawable.ic_float
        "resize" -> R.drawable.ic_grip
        "settings" -> R.drawable.ic_settings
        "keyboard" -> R.drawable.ic_activity
        "hide" -> R.drawable.ic_hide
        else -> R.drawable.ic_menu
    }

    private fun openClipboard() {
        val connection = currentInputConnection ?: targetInputConnection
        ClipboardActivity.onPaste = { value ->
            connection?.commitText(value, 1)
            cachedBeforeCursor += value
            scheduleRefresh()
        }
        startActivity(Intent(ctx, ClipboardActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    private fun runTool(id: String) {
        when (id) {
            "editor" -> showPanel("editor")
            "clipboard" -> showPanel("clip")
            "emoji" -> showPanel("emoji")
            "voice" -> { showPanel("letters"); startVoice() }
            "theme" -> { Prefs.nextTheme(ctx); rebuild() }
            "onehand" -> { Prefs.nextOneHand(ctx); rebuild() }
            "floating" -> { Prefs.setB(ctx, "float", !floating); rebuild() }
            "resize" -> showPanel("resize")
            "settings" -> startActivity(Intent(ctx, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            "keyboard" -> showPanel("letters")
            "hide" -> { showPanel("letters"); requestHideSelf(0) }
        }
    }

    private fun buildTop() {
        top.removeAllViews()
        sugViews.clear()
        val state = desiredTopState()
        lastTopState = state
        val row = LinearLayout(ctx).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(2), dp(4), dp(2))
        }
        if (state != "emojiSearch") {
            row.addView(iconImg(if (state == "menu") R.drawable.ic_close else R.drawable.ic_menu) {
                showPanel(if (mode == "menu") "letters" else "menu")
            }.apply { contentDescription = if (state == "menu") "Close tools" else "Open tools" },
                lp(dp(44), MATCH))
        }

        when (state) {
            "emojiSearch" -> {
                row.addView(TextView(ctx).apply {
                    text = "×"
                    textSize = 25f
                    gravity = Gravity.CENTER
                    setTextColor(t.text)
                    setOnClickListener {
                        emojiSearchActive = false
                        emojiSearchQuery = ""
                        buildTop()
                        showPanel("letters")
                    }
                }, lp(dp(38), MATCH))
                row.addView(TextView(ctx).apply {
                    text = if (emojiSearchQuery.isBlank()) "Search emoji…" else emojiSearchQuery
                    textSize = 14f
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    gravity = Gravity.CENTER_VERTICAL
                    setTextColor(t.text)
                }, lp(0, MATCH, 1f))
                emojiSearchMatches(emojiSearchQuery).take(3).forEach { emoji ->
                    row.addView(TextView(ctx).apply {
                        text = emoji
                        textSize = 21f
                        gravity = Gravity.CENTER
                        setOnClickListener {
                            emojiSearchActive = false
                            emojiSearchQuery = ""
                            insertEmoji(emoji)
                            showPanel("letters")
                        }
                    }, lp(dp(38), MATCH))
                }
            }
            "menu" -> {
                row.addView(TextView(ctx).apply {
                    text = "Tools"
                    textSize = 15f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(t.text)
                    gravity = Gravity.CENTER
                }, lp(0, MATCH, 1f))
                row.addView(View(ctx), lp(dp(44), 1))
            }
            "tools" -> {
                val quickTools = Prefs.toolbarTools(ctx).take(4)
                quickTools.forEach { id ->
                    row.addView(iconImg(toolIcon(id)) { runTool(id) }.apply {
                        contentDescription = toolLabel(id)
                    }, lp(0, MATCH, 1f))
                }
                if (quickTools.isEmpty()) {
                    row.addView(TextView(ctx).apply {
                        text = "CrazeKeyboard"
                        textSize = 13f
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(t.text)
                        gravity = Gravity.CENTER
                    }, lp(0, MATCH, 1f))
                }
                row.addView(View(ctx), lp(dp(4), 1))
            }
            else -> {
                for (i in 0..2) {
                    val tv = TextView(ctx).apply {
                        gravity = Gravity.CENTER
                        textSize = 15f
                        setTextColor(t.text)
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                        setPadding(dp(6), 0, dp(6), 0)
                        if (i == 1) typeface = Typeface.DEFAULT_BOLD
                        text = lastSuggestions.getOrElse(i) { "" }
                        setOnClickListener { pickSuggestion(text.toString()) }
                    }
                    sugViews.add(tv)
                    row.addView(tv, lp(0, MATCH, 1f))
                    if (i < 2) row.addView(View(ctx).apply { setBackgroundColor(t.special) },
                        lp(dp(1), dp(18)).apply { gravity = Gravity.CENTER_VERTICAL })
                }
            }
        }
        top.addView(row, FrameLayout.LayoutParams(MATCH, MATCH))
    }

    private fun currentWord(): String = wordAtCursor(cachedBeforeCursor)

    private fun updateSuggestions() {
        val state = desiredTopState()
        if (state != lastTopState) buildTop()
        if (state != "suggestions" || sugViews.isEmpty()) {
            if (state != "suggestions") {
                ++predictionEpoch
                predictionTask?.let(suggestionWorker::removeCallbacks)
                predictionTask = null
                lastSuggestions.fill("")
            }
            return
        }

        val snapshot = cachedBeforeCursor
        if (snapshot.isBlank()) {
            ++predictionEpoch
            predictionTask?.let(suggestionWorker::removeCallbacks)
            predictionTask = null
            lastSuggestions.fill("")
            sugViews.forEach { it.text = "" }
            return
        }

        val epoch = ++predictionEpoch
        predictionTask?.let(suggestionWorker::removeCallbacks)
        // Keep the previous completed row visible during inference. Clearing it here caused
        // the suggestion strip to flash empty on every cursor/text update.
        val task = Runnable {
            CrazeLmTflitePredictor.predictNextWordsAsync(applicationContext, snapshot, 3) { modelWords ->
                if (epoch != predictionEpoch) return@predictNextWordsAsync
                val clean = modelWords.asSequence()
                    .map { it.trim() }
                    .filter { it.length in 1..24 && it.all { ch -> ch.isLetter() || ch == '\'' || ch == '-' } }
                    .distinctBy { it.lowercase() }
                    .take(3)
                    .toList()
                hd.post {
                    if (epoch != predictionEpoch || !::top.isInitialized ||
                        desiredTopState() != "suggestions") return@post
                    if (clean.isNotEmpty()) {
                        for (i in 0..2) lastSuggestions[i] = clean.getOrNull(i).orEmpty()
                        for (i in 0..2) if (i < sugViews.size) {
                            val view = sugViews[i]
                            if (view.text?.toString() != lastSuggestions[i]) {
                                view.animate().cancel()
                                view.animate().alpha(0.62f).setDuration(55L).withEndAction {
                                    view.text = lastSuggestions[i]
                                    view.animate().alpha(1f).setDuration(95L).start()
                                }.start()
                            }
                        }
                    }
                }
            }
        }
        predictionTask = task
        suggestionWorker.post(task)
    }

    private fun pickSuggestion(s: String) {
        val ic = currentInputConnection ?: return
        if (s.isEmpty()) return
        val before = cachedBeforeCursor
        val w = currentWord()
        val previous = tokensBeforeCursor(before).let {
            if (w.isNotEmpty() && it.lastOrNull() == w.lowercase()) it.dropLast(1) else it
        }.takeLast(2)
        acceptPendingCorrection()
        lastCorrection = null
        ic.beginBatchEdit()
        ic.deleteSurroundingText(w.length, 0)
        ic.commitText("$s ", 1)
        ic.endBatchEdit()
        setLocalContext((if (w.isNotEmpty()) before.dropLast(w.length) else before) + "$s ")
        if (!incognito && Prefs.b(this, "personalized", true)) {
            val alternatives = sugViews.map { it.text?.toString().orEmpty() }.filter { it.isNotBlank() }
            val count = UserLanguageModel.observe(this, previous, s)
            TinyNeuralRanker.learn(this, previous, s, alternatives)
            if (!Words.known(s) && count >= 2) Words.learn(this, s)
        }
        scheduleRefresh()
    }

    private fun confirmWord(word: String, before: String) {
        if (incognito || word.length < 2 || !Prefs.b(this, "personalized", true)) return
        val tokens = tokensBeforeCursor(before)
        val previous = if (tokens.lastOrNull() == word.lowercase()) tokens.dropLast(1).takeLast(2) else tokens.takeLast(2)
        val alternatives = sugViews.map { it.text?.toString().orEmpty() }.filter { it.isNotBlank() }
        val count = UserLanguageModel.observe(this, previous, word)
        TinyNeuralRanker.learn(this, previous, word, alternatives)
        if (!Words.known(word) && count >= 2) Words.learn(this, word)
    }

    private fun acceptPendingCorrection() {
        val pending = pendingCorrectedWord ?: return
        pendingCorrectedWord = null
        if (!incognito && Prefs.b(this, "personalized", true)) {
            val count = UserLanguageModel.observe(this, pending.first, pending.second)
            if (!Words.known(pending.second) && count >= 2) Words.learn(this, pending.second)
        }
        lastCorrection = null
    }

    // ---------- typing actions ----------
    private fun autoCap() {
        if (shift == 2 || mode != "letters") return
        // Ignore ordinary trailing spaces, but preserve newlines so a new paragraph
        // is capitalized. Never treat the letter "n" as a sentence boundary.
        val before = cachedBeforeCursor.trimEnd(' ', '\t', '\r')
        val last = before.lastOrNull()
        val want = last == null || last == '.' || last == '!' || last == '?' || last == '\n'
        if (want && shift == 0) { shift = 1; autoShift = true; updateCase() }
        else if (!want && shift == 1 && autoShift) { shift = 0; autoShift = false; updateCase() }
    }

    private fun updateCase() {
        letterKeys.forEach { (tv, c) -> tv.text = if (shift > 0) c.uppercase() else c }
        shiftKey?.let { k ->
            val fg = if (shift > 0) t.accentText else t.text
            k.iv?.setImageResource(when (shift) { 2 -> R.drawable.ic_caps; 1 -> R.drawable.ic_shift_on; else -> R.drawable.ic_shift })
            k.iv?.setColorFilter(fg)
            k.tv.background = keyBg(if (shift > 0) t.accent else t.special)
        }
    }

    private fun type(s: String) {
        dismissAlts()
        if (emojiSearchActive) {
            emojiSearchQuery = (emojiSearchQuery + s).take(40)
            buildTop()
            return
        }
        val ic = currentInputConnection ?: return
        if (s == " ") { space(ic); return }
        acceptPendingCorrection()
        lastCorrection = null
        val inserted = if (shift > 0 && mode == "letters") s.uppercase() else s
        ic.commitText(inserted, 1)
        setLocalContext(cachedBeforeCursor + inserted)
        if (shift == 1) { shift = 0; autoShift = false; updateCase() }
        scheduleRefresh()
    }

    private fun space(ic: InputConnection) {
        if (emojiSearchActive) {
            emojiSearchQuery = (emojiSearchQuery + " ").take(40)
            buildTop()
            return
        }
        val before = cachedBeforeCursor
        val word = wordAtCursor(before)
        val previousTokens = tokensBeforeCursor(before).let {
            if (word.isNotEmpty() && it.lastOrNull() == word.lowercase()) it.dropLast(1).takeLast(2) else it.takeLast(2)
        }

        // A subsequent space accepts the displayed correction, while backspace can still undo it.
        acceptPendingCorrection()
        if (doubleSpaceOn && before.length >= 2 && before[before.length - 1] == ' ' && before[before.length - 2].isLetterOrDigit()) {
            ic.deleteSurroundingText(1, 0)
            ic.commitText(". ", 1)
            setLocalContext(before.dropLast(1) + ". ")
        } else {
            val fix = if (autoCorrectOn && suggestOn && !incognito && word.isNotEmpty())
            cachedCorrection?.takeIf { it.first.equals(word, ignoreCase = true) }?.second else null
            if (fix != null) {
                ic.beginBatchEdit()
                ic.deleteSurroundingText(word.length, 0)
                ic.commitText("$fix ", 1)
                ic.endBatchEdit()
                lastCorrection = word to fix
                pendingCorrectedWord = previousTokens to fix
                setLocalContext(before.dropLast(word.length) + "$fix ")
            } else {
                confirmWord(word, before)
                ic.commitText(" ", 1)
                setLocalContext(before + " ")
            }
        }
        scheduleRefresh()
    }

    // Key event instead of reading the selection first: no blocking round-trip, handles selections and emoji itself.
    // Backspace right after an auto-correction restores what was actually typed (and stops correcting that word).
    private fun del() {
        if (emojiSearchActive) {
            if (emojiSearchQuery.isNotEmpty()) emojiSearchQuery = emojiSearchQuery.dropLast(1)
            buildTop()
            return
        }
        val lc = lastCorrection
        lastCorrection = null
        if (lc != null) {
            val ic = currentInputConnection
            val before = cachedBeforeCursor
            if (ic != null && before.endsWith(lc.second + " ")) {
                ic.beginBatchEdit()
                ic.deleteSurroundingText(lc.second.length + 1, 0)
                ic.commitText(lc.first, 1)
                ic.endBatchEdit()
                pendingCorrectedWord = null
                setLocalContext(before.dropLast(lc.second.length + 1) + lc.first)
                scheduleRefresh()
                return
            }
        }
        val before = cachedBeforeCursor
        sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        val end = if (before.isEmpty()) 0 else before.offsetByCodePoints(before.length, -1)
        setLocalContext(before.substring(0, end))
        scheduleRefresh()
    }

    private fun actionId(): Int = (currentInputEditorInfo?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION

    private fun enter() {
        if (emojiSearchActive) {
            emojiSearchActive = false
            emojiSearchQuery = ""
            buildTop()
            showPanel("letters")
            return
        }
        acceptPendingCorrection()
        lastCorrection = null
        val ei = currentInputEditorInfo
        val a = actionId()
        if (ei == null || (ei.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0 ||
            a == EditorInfo.IME_ACTION_NONE || a == EditorInfo.IME_ACTION_UNSPECIFIED) sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        else currentInputConnection?.performEditorAction(a)
        scheduleRefresh()
    }

    private fun enterIcon() = when (actionId()) {
        EditorInfo.IME_ACTION_SEARCH -> R.drawable.ic_search
        EditorInfo.IME_ACTION_SEND -> R.drawable.ic_send
        EditorInfo.IME_ACTION_GO, EditorInfo.IME_ACTION_NEXT -> R.drawable.ic_go
        EditorInfo.IME_ACTION_DONE -> R.drawable.ic_done
        else -> R.drawable.ic_enter
    }

    // Pages are cached, so the enter key's icon has to follow the focused field instead of being fixed at build time.
    private fun refreshEnter() {
        if (enterKeys.isEmpty()) return
        val icon = enterIcon()
        enterKeys.forEach { it.iv?.setImageResource(icon) }
    }

    private fun launchTextEditor() {
        if (incognito) {
            android.widget.Toast.makeText(ctx, "Text editor is disabled in private fields", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val selected = currentInputConnection?.getSelectedText(0)?.toString().orEmpty()
        TextEditorActivity.callback = { deliverEditor() }
        try {
            startActivity(Intent(ctx, TextEditorActivity::class.java)
                .putExtra(TextEditorActivity.EXTRA_INITIAL_TEXT, selected)
                .putExtra(TextEditorActivity.EXTRA_REPLACE_SELECTION, selected.isNotEmpty())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            android.widget.Toast.makeText(ctx, "Could not open the text editor", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // Commit after Android restores the original app's InputConnection, never into the editor's own field.
    private fun deliverEditor() {
        if (currentInputEditorInfo?.packageName == packageName) return
        val edited = TextEditorActivity.take() ?: return
        val ic = currentInputConnection ?: return
        ic.commitText(edited, 1)
        setLocalContext(cachedBeforeCursor + edited)
        scheduleRefresh()
        TextEditorActivity.callback = null
    }

    private fun startVoice() {
        VoiceActivity.callback = { deliverVoice() }
        try {
            startActivity(Intent(this, VoiceActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) { }
    }

    // Voice text waits in a mailbox until the editor has focus again; whichever of the two triggers comes first commits it.
    private fun deliverVoice() {
        val txt = VoiceActivity.take() ?: return
        val ic = currentInputConnection
        if (ic == null) { VoiceActivity.post(txt); return }
        ic.commitText("$txt ", 1)
        setLocalContext(cachedBeforeCursor + "$txt ")
        scheduleRefresh()
    }

    // ---------- key building ----------
    private fun mk(label: String, w: Float, bg: Int, fg: Int, size: Float = 20f, icon: Int = 0): K {
        val tv = TextView(ctx).apply {
            text = label; gravity = Gravity.CENTER; setTextColor(fg); textSize = size
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            includeFontPadding = false
            background = keyBg(bg)
        }
        var iv: ImageView? = null
        val cell = FrameLayout(ctx).apply {
            setPadding(dp(2), dp(3), dp(2), dp(3))
            contentDescription = label.ifEmpty { "Keyboard key" }
            layoutParams = lp(0, cellH, w)
            addView(tv, FrameLayout.LayoutParams(MATCH, MATCH))
            if (icon != 0) {
                iv = ImageView(ctx).apply { setImageResource(icon); setColorFilter(fg); scaleType = ImageView.ScaleType.FIT_CENTER }
                addView(iv, FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
            }
        }
        return K(cell, tv, iv)
    }

    private fun dismissAlts() { try { popup?.dismiss() } catch (e: Exception) { }; popup = null }
    private fun dismissPopups() { dismissAlts(); try { preview?.dismiss() } catch (e: Exception) { }; preview = null; prevTv = null }

    // One preview window is created once and then only moved/re-texted: creating a window per keypress is what made typing laggy.
    // Window calls can throw (BadTokenException) if the keyboard is hidden mid-touch, so a failed preview is simply skipped.
    private fun showPreview(k: K) {
        try {
            val pw = dp(52)
            val ph = dp(56)
            val xo = (k.cell.width - pw) / 2
            val yo = -(k.cell.height + ph - dp(4))
            val p = preview
            if (p == null || !p.isShowing) {
                val tv = TextView(ctx).apply {
                    textSize = 26f; gravity = Gravity.CENTER; setTextColor(t.text); background = keyBg(t.key, 12)
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                }
                tv.text = k.tv.text
                prevTv = tv
                preview = PopupWindow(tv, pw, ph).apply {
                    isClippingEnabled = false; isTouchable = false
                    showAsDropDown(k.cell, xo, yo)
                }
            } else {
                prevTv?.apply { text = k.tv.text; visibility = View.VISIBLE }
                p.update(k.cell, xo, yo, pw, ph)
            }
        } catch (e: Exception) {
            preview = null; prevTv = null
        }
    }

    private fun hidePreview() { prevTv?.visibility = View.INVISIBLE }

    // fast = type on finger-down (plain keys). Keys with a long-press type on release, unless the long-press fired.
    private fun nearestLetterAt(rawX: Float, rawY: Float): String? {
        var best = dp(52) * dp(52).toFloat()
        var selected: String? = null
        val location = IntArray(2)
        letterKeys.forEach { (view, value) ->
            if (view.visibility != View.VISIBLE || view.width == 0 || view.height == 0) return@forEach
            view.getLocationOnScreen(location)
            val dx = rawX - (location[0] + view.width / 2f)
            val dy = rawY - (location[1] + view.height / 2f)
            val distance = dx * dx + dy * dy
            if (distance < best) { best = distance; selected = value.lowercase() }
        }
        return selected
    }

    private fun commitSwipe(sequence: String): Boolean {
        if (sequence.length < 3) return false
        val word = Words.swipeCandidates(this, sequence, 3).firstOrNull() ?: return false
        val connection = currentInputConnection ?: targetInputConnection ?: return false
        return try {
            acceptPendingCorrection()
            connection.commitText("$word ", 1)
            setLocalContext(cachedBeforeCursor + "$word ")
            if (!incognito && Prefs.b(this, "personalized", true)) {
                val previous = tokensBeforeCursor(cachedBeforeCursor).takeLast(2)
                UserLanguageModel.observe(this, previous, word)
                if (!Words.known(word)) Words.learn(this, word)
            }
            scheduleRefresh()
            true
        } catch (_: Exception) { false }
    }

    private fun attach(k: K, repeat: Boolean = false, showPrev: Boolean = false, fast: Boolean = false, onLong: (() -> Unit)? = null, tap: () -> Unit) {
        var fired = false; var swiped = false; var swipeMoved = false; var swipeTracking = false
        var swipeLastKey = ""
        val swipePath = StringBuilder()
        val letterKey = letterKeys.any { it.first === k.tv }
        val longR = Runnable { fired = true; hidePreview(); onLong?.invoke() }
        val repR = object : Runnable { override fun run() { tap(); hd.postDelayed(this, 45) } }
        k.cell.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    fired = false; swiped = false; swipeMoved = false
                    swipeTracking = letterKey && Prefs.b(ctx, "swipeTyping", false)
                    swipePath.setLength(0)
                    swipeLastKey = if (swipeTracking) nearestLetterAt(e.rawX, e.rawY).orEmpty() else ""
                    if (swipeLastKey.isNotEmpty()) swipePath.append(swipeLastKey)
                    k.tv.alpha = 0.65f
                    if (hapticsOn) k.cell.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    if (keySoundOn) {
                        try { (getSystemService(Context.AUDIO_SERVICE) as AudioManager).playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, 0.45f) }
                        catch (_: Exception) { }
                    }
                    if (showPrev) showPreview(k)
                    if (repeat) { tap(); hd.postDelayed(repR, 380) }
                    else if (fast) tap()
                    else if (onLong != null && !swipeTracking) hd.postDelayed(longR, 350)
                }
                MotionEvent.ACTION_MOVE -> {
                    if (swipeTracking && e.pointerCount == 1) {
                        val nearest = nearestLetterAt(e.rawX, e.rawY)
                        if (!nearest.isNullOrEmpty() && nearest != swipeLastKey) {
                            swipePath.append(nearest); swipeLastKey = nearest
                            swipeMoved = true; fired = true
                            hd.removeCallbacks(longR); hidePreview()
                        }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    k.tv.alpha = 1f; hd.removeCallbacks(longR); hd.removeCallbacks(repR); hidePreview()
                    if (swipeTracking && swipeMoved) swiped = commitSwipe(swipePath.toString())
                    swipeTracking = false
                    if (!repeat && !fast && !fired) tap()
                    if (swipeMoved && !swiped && !repeat && !fast) tap()
                }
                MotionEvent.ACTION_CANCEL -> {
                    k.tv.alpha = 1f; hd.removeCallbacks(longR); hd.removeCallbacks(repR); hidePreview()
                    swipeTracking = false
                }
            }
            true
        }
    }

    private fun showAlts(anchor: View, alts: String) {
        dismissAlts()
        if (alts.isEmpty()) return
        val row = LinearLayout(ctx).apply { background = keyBg(t.special, 12); setPadding(dp(4), dp(4), dp(4), dp(6)) }
        alts.forEach { ch ->
            val s = if (shift > 0 && mode == "letters") ch.toString().uppercase() else ch.toString()
            row.addView(TextView(ctx).apply {
                text = s; textSize = 22f; gravity = Gravity.CENTER; setTextColor(t.text)
                setPadding(dp(10), dp(8), dp(10), dp(8)); setOnClickListener { type(ch.toString()) }
            })
        }
        try {
            popup = PopupWindow(row, WRAP, WRAP).apply {
                setBackgroundDrawable(ColorDrawable(0)); isOutsideTouchable = true
                showAsDropDown(anchor, 0, -(anchor.height + dp(52)))
            }
        } catch (e: Exception) { popup = null }
    }

    private fun hrow() = LinearLayout(ctx).apply { gravity = Gravity.CENTER }
    private fun spacer(w: Float) = View(ctx).apply { layoutParams = lp(0, 1, w) }

    private fun addSecondaryHint(key: K, value: String) {
        if (value.isEmpty()) return
        key.cell.addView(TextView(ctx).apply {
            text = value
            textSize = 8f
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(t.text)
            alpha = 0.72f
            isClickable = false
            isFocusable = false
        }, FrameLayout.LayoutParams(dp(13), dp(13), Gravity.TOP or Gravity.END).apply {
            topMargin = dp(3)
            rightMargin = dp(5)
        })
    }

    private fun attachLetterKey(key: K, value: String) {
        val alts = Alts.map[value]
        val number = Alts.numberHints[value]
        val hint = number ?: alts?.firstOrNull()?.toString()
        if (hint != null) addSecondaryHint(key, hint)
        attach(
            key,
            showPrev = true,
            fast = alts == null && number == null,
            onLong = when {
                number != null -> ({ type(number) })
                alts != null -> ({
                    if (alts.length == 1) type(alts)
                    else showAlts(key.cell, alts)
                })
                else -> null
            }
        ) { type(value) }
    }

    private fun charRow(chars: String, pad: Float = 0f, letters: Boolean = true): LinearLayout = hrow().apply {
        if (pad > 0) addView(spacer(pad))
        chars.forEach { ch ->
            val value = ch.toString()
            val isLetter = letters && ch.isLetter()
            val key = mk(value, 1f, t.key, t.text, if (isLetter) 22f else 18f)
            if (isLetter) {
                letterKeys.add(key.tv to value)
                attachLetterKey(key, value)
            } else {
                val alts = Alts.map[value]
                attach(key, fast = alts == null, onLong = alts?.let { alternatives ->
                    { showAlts(key.cell, alternatives) }
                }) { type(value) }
            }
            addView(key.cell)
        }
        if (pad > 0) addView(spacer(pad))
    }

    private fun bottomRow(left: String, leftTarget: String): LinearLayout = hrow().apply {
        val l = mk(left, 1.4f, t.special, t.text, 14f); attach(l) { showPanel(leftTarget) }; addView(l.cell)
        val emoji = mk("", 0.9f, t.special, t.text, 18f, R.drawable.ic_emoji)
        attach(emoji) { emojiSearchActive = false; emojiSearchQuery = ""; showPanel("emoji") }
        emoji.cell.contentDescription = "Open emoji keyboard"
        addView(emoji.cell)
        val c = mk(",", 0.8f, t.special, t.text); attach(c, onLong = { emojiSearchActive = false; showPanel("emoji") }) { type(",") }; addView(c.cell)
        val sp = mk("CrazeKeyboard", 4.0f, t.key, t.text, 11f)
        // Gboard-style spacebar cursor control: drag horizontally to move the
        // target editor cursor without inserting spaces.
        var cursorDrag = false
        var cursorStartX = 0f
        var cursorLastStep = 0
        attach(sp, fast = true) { type(" ") }
        sp.cell.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    cursorDrag = false
                    cursorStartX = event.rawX
                    cursorLastStep = 0
                    sp.tv.alpha = 0.72f
                    // This custom touch listener replaces attach()'s listener,
                    // so reproduce the same haptic and key-click feedback here.
                    if (hapticsOn) sp.cell.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    if (keySoundOn) {
                        try {
                            (getSystemService(Context.AUDIO_SERVICE) as AudioManager)
                                .playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, 0.45f)
                        } catch (_: Exception) { }
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - cursorStartX
                    if (!cursorDrag && kotlin.math.abs(dx) >= dp(8)) cursorDrag = true
                    if (cursorDrag) {
                        val step = (dx / dp(12).toFloat()).toInt()
                        val delta = step - cursorLastStep
                        if (delta != 0) {
                            val keyCode = if (delta > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
                            repeat(kotlin.math.abs(delta).coerceAtMost(12)) {
                                sendDownUpKeyEvents(keyCode)
                            }
                            cursorLastStep = step
                            syncContextAsync()
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    sp.tv.alpha = 1f
                    if (!cursorDrag) type(" ")
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    sp.tv.alpha = 1f
                    true
                }
                else -> true
            }
        }
        sp.cell.contentDescription = "Space. Hold and slide left or right to move cursor"
        addView(sp.cell)
        val d = mk(".", 0.9f, t.special, t.text); attach(d, onLong = { showAlts(d.cell, Alts.map["."].orEmpty()) }) { type(".") }; addView(d.cell)
        val e = mk("", 1.4f, t.accent, t.accentText, 18f, enterIcon()); attach(e) { enter() }; addView(e.cell)
        enterKeys.add(e)
    }

    private fun backKey(w: Float): View { val b = mk("", w, t.special, t.text, 18f, R.drawable.ic_backspace); attach(b, repeat = true) { del() }; return b.cell }

    private fun column() = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(3), dp(2), dp(3), 0) }

    private fun buildLetters(): View {
        val col = column()
        if (numRow) col.addView(charRow("1234567890", 0f, false))
        col.addView(charRow("qwertyuiop"))
        col.addView(charRow("asdfghjkl", 0.5f))
        col.addView(hrow().apply {
            val s = mk("", 1.4f, t.special, t.text, 18f, R.drawable.ic_shift); shiftKey = s
            attach(s) {
                val now = SystemClock.uptimeMillis()
                // Single tap toggles shift; a quick second tap turns on caps lock.
                shift = if (shift == 2) 0 else if (shift == 1 && now - lastShiftTap < 350) 2 else if (shift == 1) 0 else 1
                autoShift = false; lastShiftTap = now; updateCase()
            }
            addView(s.cell)
            "zxcvbnm".forEach { ch ->
                val value = ch.toString()
                val k = mk(value, 1f, t.key, t.text, 22f)
                letterKeys.add(k.tv to value)
                attachLetterKey(k, value)
                addView(k.cell)
            }
            addView(backKey(1.4f))
        })
        col.addView(bottomRow("?123", "symbols"))
        return col
    }

    private fun symKey(row: LinearLayout, ch: Char) {
        val value = ch.toString()
        val k = mk(value, 1f, t.key, t.text, 18f)
        val alts = Alts.map[value]
        attach(k, fast = alts == null, onLong = alts?.let { options -> { showAlts(k.cell, options) } }) {
            type(value)
        }
        row.addView(k.cell)
    }

    private fun buildSymbols(page: Int): View {
        val col = column()
        if (numRow || page == 1) col.addView(charRow("1234567890", 0f, false))
        if (page == 1) {
            col.addView(charRow("@#$%&-+()/", 0f, false))
            col.addView(hrow().apply {
                val p = mk("=\\<", 1.4f, t.special, t.text, 14f); attach(p) { showPanel("symbols2") }; addView(p.cell)
                "*\"':;!?".forEach { symKey(this, it) }
                addView(backKey(1.4f))
            })
        } else {
            col.addView(charRow("~`|•√π÷×^∆", 0f, false))
            col.addView(charRow("£¢€¥₹°={}\\", 0f, false))
            col.addView(hrow().apply {
                val p = mk("?123", 1.4f, t.special, t.text, 14f); attach(p) { showPanel("symbols") }; addView(p.cell)
                "©®™_–—[]".forEach { symKey(this, it) }
                addView(backKey(1.4f))
            })
        }
        col.addView(bottomRow("ABC", "letters"))
        return col
    }

    // ---------- customisable tool panel ----------
    private fun tile(icon: Int, label: String, a: () -> Unit): View {
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = keyBg(t.key, 16)
            setPadding(dp(4), dp(5), dp(4), dp(5))
            isClickable = true
            isFocusable = true
            contentDescription = label
            setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> view.animate().scaleX(0.97f).scaleY(0.97f)
                        .setDuration(65L).start()
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.animate()
                        .scaleX(1f).scaleY(1f).setDuration(95L).start()
                }
                false
            }
            setOnClickListener { a() }
        }
        box.addView(FrameLayout(ctx).apply {
            background = rounded(t.special, 13)
            addView(ImageView(ctx).apply {
                setImageResource(icon)
                setColorFilter(t.text)
                contentDescription = label
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setPadding(dp(7), dp(7), dp(7), dp(7))
            }, FrameLayout.LayoutParams(dp(40), dp(40), Gravity.CENTER))
        }, lp(dp(48), dp(48)))
        box.addView(TextView(ctx).apply {
            text = label
            textSize = 11f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(t.text)
            gravity = Gravity.CENTER
            setPadding(0, dp(5), 0, 0)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        })
        val wrap = FrameLayout(ctx).apply { setPadding(dp(3), dp(3), dp(3), dp(3)) }
        wrap.addView(box, FrameLayout.LayoutParams(MATCH, MATCH))
        wrap.layoutParams = lp(0, MATCH, 1f)
        return wrap
    }

    private fun buildMenu(): View {
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(4), dp(4), dp(4), dp(2)) }
        val available = (Prefs.toolbarTools(ctx) + "settings").distinct().take(12)
        val categories = listOf("All", "Typing", "Editing", "Layout")
        val tabs = LinearLayout(ctx).apply { gravity = Gravity.CENTER_VERTICAL }
        val tabViews = LinkedHashMap<String, TextView>()
        val grid = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        fun updateTabs() {
            tabViews.forEach { (name, view) ->
                val active = name == menuCategory
                view.background = rounded(if (active) t.special else t.bg, 10)
                view.alpha = if (active) 1f else 0.62f
            }
        }
        fun renderTools() {
            grid.removeAllViews()
            val grouped = when (menuCategory) {
                "Typing" -> listOf("keyboard", "emoji", "voice", "theme")
                "Editing" -> listOf("editor", "clipboard", "settings")
                "Layout" -> listOf("onehand", "floating", "resize", "hide", "settings")
                else -> available
            }.filter { it in available }.distinct()
            val rows = grouped.chunked(4)
            val rowCount = maxOf(3, rows.size)
            repeat(rowCount) { rowIndex ->
                val ids = rows.getOrNull(rowIndex).orEmpty()
                val row = LinearLayout(ctx).apply { gravity = Gravity.CENTER_VERTICAL }
                ids.forEach { id -> row.addView(tile(toolIcon(id), toolLabel(id)) { runTool(id) }) }
                repeat(4 - ids.size) { row.addView(View(ctx), lp(0, 1, 1f)) }
                grid.addView(row, lp(MATCH, 0, 1f))
            }
        }
        categories.forEach { label ->
            val tab = TextView(ctx).apply {
                text = label; textSize = 11f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                gravity = Gravity.CENTER; setTextColor(t.text)
                setPadding(dp(5), 0, dp(5), 0); minHeight = dp(32)
                isClickable = true; isFocusable = true
                setOnClickListener { menuCategory = label; updateTabs(); renderTools() }
            }
            tabViews[label] = tab
            tabs.addView(tab, lp(0, dp(34), 1f).apply { if (label != "All") leftMargin = dp(4) })
        }
        col.addView(tabs, lp(MATCH, dp(38)))
        col.addView(grid, lp(MATCH, 0, 1f))
        updateTabs(); renderTools()
        return col
    }

    // ---------- emoji + clipboard ----------
    private fun backBar(): View = hrow().apply {
        val b = mk("ABC", 1.4f, t.special, t.text, 14f); attach(b) { showPanel("letters") }; addView(b.cell)
        addView(spacer(5f))
        addView(backKey(1.4f))
    }

    private fun emojiExtraHeight() = dp(if (landscape()) 44 else 112)
    private fun bodyHeight() = panelH() + (if (mode == "emoji") emojiExtraHeight() else 0) -
        (if (mode == "emoji") dp(82) else dp(40)) - cellH - dp(2)

    // Each emoji category grid (~100 views) is built once and reused; only the "recent" grid is rebuilt because it changes.
    private val emojiAliases = mapOf(
        "😀" to "grinning smile happy face", "😃" to "smile happy face", "😄" to "smile laugh happy",
        "😁" to "beaming grin happy", "😆" to "laugh laugh-tears", "😅" to "sweat smile laugh",
        "😂" to "joy laugh tears funny", "🤣" to "rofl rolling laugh", "😊" to "blush smile happy",
        "😉" to "wink", "😍" to "heart eyes love", "🥰" to "smiling hearts love", "😘" to "kiss love",
        "😎" to "cool sunglasses", "🥳" to "party celebrate", "🤔" to "thinking wonder",
        "😭" to "cry sad tears", "😢" to "cry sad", "😡" to "angry mad", "🤯" to "mind blown shock",
        "👍" to "thumbs up yes approve good", "👎" to "thumbs down no bad", "🙏" to "pray please thanks",
        "👏" to "clap applause", "💪" to "strong muscle flex", "👋" to "wave hello goodbye",
        "❤️" to "red heart love", "❤" to "heart love", "💔" to "broken heart sad",
        "💕" to "two hearts love", "💯" to "hundred perfect score", "🔥" to "fire hot lit",
        "⭐" to "star favorite", "✅" to "check done yes", "❌" to "cross no wrong",
        "🐶" to "dog puppy pet animal", "🐱" to "cat kitten pet animal", "🐼" to "panda animal",
        "🦊" to "fox animal", "🐸" to "frog animal", "🦄" to "unicorn fantasy animal",
        "🍎" to "apple fruit food", "🍕" to "pizza food", "🍔" to "burger hamburger food",
        "🍟" to "fries food", "🍰" to "cake dessert sweet", "☕" to "coffee drink",
        "⚽" to "soccer football sport", "🏀" to "basketball sport", "🎮" to "game controller gaming",
        "🎧" to "headphones music", "🚗" to "car vehicle", "✈" to "airplane plane travel",
        "🌍" to "earth globe world", "🌈" to "rainbow", "🌙" to "moon night",
        "💡" to "light bulb idea", "📱" to "phone mobile", "💻" to "laptop computer",
        "📷" to "camera photo", "🎉" to "party celebration", "🎂" to "birthday cake"
    )

    private fun insertEmoji(value: String) {
        val connection = targetInputConnection ?: currentInputConnection ?: return
        acceptPendingCorrection()
        connection.commitText(value, 1)
        if (!incognito) Prefs.addRecent(ctx, value)
        setLocalContext(cachedBeforeCursor + value)
        scheduleRefresh()
    }

    private fun emojiGrid(list: List<String>): View {
        val grid = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        list.chunked(8).forEach { rowValues ->
            val row = LinearLayout(ctx).apply { gravity = Gravity.CENTER_VERTICAL }
            rowValues.forEach { emoji ->
                row.addView(TextView(ctx).apply {
                    text = emoji
                    textSize = 29f
                    gravity = Gravity.CENTER
                    setPadding(0, dp(8), 0, dp(8))
                    minHeight = dp(48)
                    contentDescription = emojiInfo(emoji).first.ifBlank { emojiAliases[emoji] ?: "Emoji" }
                    setOnClickListener { insertEmoji(emoji) }
                }, lp(0, dp(48), 1f))
            }
            repeat(8 - rowValues.size) { row.addView(View(ctx), lp(0, 1, 1f)) }
            grid.addView(row)
        }
        if (list.isEmpty()) {
            grid.addView(TextView(ctx).apply {
                text = "No matching emoji"
                setTextColor(t.text)
                setPadding(dp(12), dp(12), dp(12), dp(12))
            })
        }
        return grid
    }

    private fun emojiSearchMatches(query: String): List<String> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        val terms = q.split(" ").filter { it.isNotBlank() }
        val matches = LinkedHashSet<String>()
        emojiGroups.forEach { group ->
            group.emojis.forEach { emoji ->
                val info = emojiInfo(emoji)
                val searchable = (info.first + " " + info.second + " " + emojiAliases[emoji].orEmpty() + " " + group.name + " " + group.terms + " " + emoji).lowercase()
                if (terms.all { searchable.contains(it) }) matches.add(emoji)
            }
        }
        Prefs.recents(ctx).forEach { emoji ->
            val info = emojiInfo(emoji)
            val searchable = (info.first + " " + info.second + " " + emojiAliases[emoji].orEmpty() + " " + emoji).lowercase()
            if (terms.all { searchable.contains(it) }) matches.add(emoji)
        }
        return matches.take(96)
    }

    private fun emojiCategoryName(key: String): String =
        if (key == "🕘") "Recent" else emojiGroups.firstOrNull { it.key == key }?.name ?: "Emoji"

    private fun emojiCategoryTerms(key: String): String =
        emojiGroups.firstOrNull { it.key == key }?.terms.orEmpty()

    private fun buildEmoji(): View {
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(5), dp(3), dp(5), 0)
        }

        val search = LinearLayout(ctx).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(t.key, 14)
            setPadding(dp(12), 0, dp(12), 0)
            isClickable = true
            isFocusable = true
            contentDescription = "Search emoji"
            setOnClickListener {
                emojiSearchActive = true
                emojiSearchQuery = ""
                showPanel("letters")
                buildTop()
            }
        }
        search.addView(TextView(ctx).apply {
            text = "⌕"
            textSize = 23f
            setTextColor(t.text)
            gravity = Gravity.CENTER
        }, lp(dp(30), MATCH))
        search.addView(TextView(ctx).apply {
            text = if (emojiSearchQuery.isBlank()) "Search emoji" else emojiSearchQuery
            textSize = 14f
            setTextColor(if (emojiSearchQuery.isBlank()) darken(t.text, 0.58f) else t.text)
            gravity = Gravity.CENTER_VERTICAL
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }, lp(0, MATCH, 1f))
        col.addView(search, lp(MATCH, dp(40)))

        val categoryKeys = ArrayList<String>().apply {
            if (Prefs.recents(ctx).isNotEmpty()) add("🕘")
            addAll(emojiCats.keys.filter { it != "🕘" })
        }
        val tabsScroll = HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
        }
        val tabs = LinearLayout(ctx).apply { gravity = Gravity.CENTER_VERTICAL }
        tabsScroll.addView(tabs, android.widget.FrameLayout.LayoutParams(WRAP, MATCH))
        col.addView(tabsScroll, lp(MATCH, dp(44)))

        val body = ScrollView(ctx).apply {
            isFillViewport = true
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val content = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(2), 0, dp(4))
        }
        body.addView(content)
        col.addView(body, lp(MATCH, bodyHeight()))
        col.addView(backBar())

        val tabViews = LinkedHashMap<String, TextView>()
        var selectedKey = categoryKeys.firstOrNull() ?: "😀"

        var aiMatches: List<String> = emptyList()
        fun render() {
            content.removeAllViews()
            val query = emojiSearchQuery.trim()
            val values = if (query.isNotEmpty()) {
                emojiSearchMatches(query)
            } else if (aiMatches.isNotEmpty()) {
                (aiMatches + if (selectedKey == "🕘") Prefs.recents(ctx) else emojiLists[selectedKey].orEmpty()).distinct()
            } else if (selectedKey == "🕘") {
                Prefs.recents(ctx)
            } else {
                emojiLists[selectedKey].orEmpty()
            }
            if (query.isNotEmpty()) {
                content.addView(TextView(ctx).apply {
                    text = "Results for \"$query\""
                    textSize = 12f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(t.text)
                    setPadding(dp(8), dp(8), dp(8), dp(6))
                })
            } else {
                content.addView(TextView(ctx).apply {
                    text = if (aiMatches.isNotEmpty()) "AI picks · ${emojiCategoryName(selectedKey)}" else emojiCategoryName(selectedKey)
                    textSize = 12f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(t.text)
                    setPadding(dp(8), dp(8), dp(8), dp(6))
                })
            }
            content.addView(emojiGrid(values))
            body.scrollTo(0, 0)
        }

        fun selectTab(key: String) {
            selectedKey = key
            tabViews.forEach { (name, view) ->
                val active = name == key
                view.alpha = if (active) 1f else 0.58f
                view.background = if (active) rounded(t.special, 12) else rounded(t.bg, 12)
            }
            render()
        }

        categoryKeys.forEach { key ->
            val tab = TextView(ctx).apply {
                text = key
                textSize = 19f
                gravity = Gravity.CENTER
                setTextColor(t.text)
                setPadding(dp(11), 0, dp(11), 0)
                isClickable = true
                isFocusable = true
                contentDescription = emojiCategoryName(key)
                setOnClickListener {
                    emojiSearchQuery = ""
                    selectTab(key)
                }
            }
            tabViews[key] = tab
            tabs.addView(tab, lp(WRAP, dp(38)).apply {
                leftMargin = dp(2)
                rightMargin = dp(2)
            })
        }
        if (selectedKey !in categoryKeys) selectedKey = "😀"
        tabViews.forEach { (name, view) ->
            val active = name == selectedKey
            view.alpha = if (active) 1f else 0.58f
            view.background = if (active) rounded(t.special, 12) else rounded(t.bg, 12)
        }
        render()
        // CrazeLM predicts likely words from the current sentence; local aliases map
        // those concepts to relevant emoji without sending text off-device.
        if (cachedBeforeCursor.isNotBlank()) {
            CrazeLmTflitePredictor.predictNextWordsAsync(applicationContext, cachedBeforeCursor, 6) { words ->
                val related = words.flatMap { emojiSearchMatches(it) }.distinct().take(18)
                if (related.isNotEmpty()) {
                    hd.post {
                        if (mode == "emoji" && ::panel.isInitialized) {
                            aiMatches = related
                            render()
                        }
                    }
                }
            }
        }
        return col
    }

    private fun buildEditor(): View {
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), dp(2), dp(6), dp(1))
        }
        fun connection(): InputConnection? = currentInputConnection ?: targetInputConnection

        val header = hrow()
        header.addView(iconImg(R.drawable.ic_close) {
            editorSelecting = false
            showPanel("letters")
        }.apply { contentDescription = "Close text tools" }, lp(dp(34), dp(30)))
        header.addView(TextView(ctx).apply {
            text = "Edit text"
            textSize = 13f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(t.text)
            gravity = Gravity.CENTER_VERTICAL
        }, lp(0, dp(30), 1f))
        val done = mk("", 0.8f, t.accent, t.accentText, 14f, R.drawable.ic_done)
        done.cell.contentDescription = "Finish editing"
        attach(done) {
            editorSelecting = false
            showPanel("letters")
            syncContextAsync()
        }
        header.addView(done.cell)
        col.addView(header, lp(MATCH, dp(30)))

        val status = TextView(ctx).apply {
            text = if (editorSelecting) "Selection mode on" else "Cursor and selection tools"
            textSize = 9f
            setTextColor(t.text)
            alpha = 0.72f
            gravity = Gravity.CENTER
            maxLines = 1
        }
        var selectionKey: K? = null
        fun setSelectionMode(enabled: Boolean) {
            editorSelecting = enabled
            selectionKey?.tv?.background = keyBg(if (enabled) t.accent else t.special)
            selectionKey?.iv?.setColorFilter(if (enabled) t.accentText else t.text)
            status.text = if (enabled) "Selection mode · arrows extend selection" else "Cursor and selection tools"
        }
        fun moveCursor(keyCode: Int) {
            val ic = connection() ?: return
            val now = SystemClock.uptimeMillis()
            val meta = if (editorSelecting) KeyEvent.META_SHIFT_ON else 0
            try {
                ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, meta))
                ic.sendKeyEvent(KeyEvent(now, now + 1, KeyEvent.ACTION_UP, keyCode, 0, meta))
                syncContextAsync()
            } catch (_: Exception) { }
        }
        fun selectAllText() {
            val ic = connection() ?: return
            var selected = false
            try { selected = ic.performContextMenuAction(android.R.id.selectAll) } catch (_: Exception) { }
            if (!selected) {
                try {
                    val extracted = ic.getExtractedText(android.view.inputmethod.ExtractedTextRequest(), 0)
                    val text = extracted?.text
                    if (text != null && extracted.partialStartOffset < 0) {
                        ic.setSelection(0, text.length)
                        selected = true
                    }
                } catch (_: Exception) { }
            }
            setSelectionMode(false)
            status.text = if (selected) "Text selected" else "Select all unavailable in this app"
        }
        fun copyOrCut(cut: Boolean) {
            if (incognito) {
                android.widget.Toast.makeText(ctx, "Clipboard actions are disabled in private fields", android.widget.Toast.LENGTH_SHORT).show()
                return
            }
            val ic = connection() ?: return
            val selected = try { ic.getSelectedText(0)?.toString().orEmpty() } catch (_: Exception) { "" }
            if (selected.isEmpty()) {
                status.text = "Select text first"
                return
            }
            try {
                (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(android.content.ClipData.newPlainText("CrazeKeyboard", selected))
                if (cut) ic.commitText("", 1)
                status.text = if (cut) "Cut" else "Copied"
                syncContextAsync()
            } catch (_: Exception) {
                status.text = "Clipboard action unavailable"
            }
        }
        fun pasteText() {
            if (incognito) {
                android.widget.Toast.makeText(ctx, "Clipboard actions are disabled in private fields", android.widget.Toast.LENGTH_SHORT).show()
                return
            }
            val ic = connection() ?: return
            try {
                val clip = (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
                if (clip == null || clip.itemCount == 0) {
                    status.text = "Clipboard empty"
                    return
                }
                val value = clip.getItemAt(0).coerceToText(ctx)?.toString().orEmpty()
                if (value.isNotEmpty()) {
                    ic.commitText(value, 1)
                    status.text = "Pasted"
                    syncContextAsync()
                }
            } catch (_: Exception) {
                status.text = "Paste unavailable"
            }
        }
        fun quickPolish() {
            if (incognito) {
                status.text = "Text improvements are disabled in private fields"
                return
            }
            val ic = connection() ?: return
            try {
                val selected = ic.getSelectedText(0)?.toString().orEmpty()
                if (selected.isNotEmpty()) {
                    val polished = LocalTextPolisher.polish(selected)
                    if (polished == selected) {
                        status.text = "No changes needed"
                    } else {
                        ic.commitText(polished, 1)
                        status.text = "Selected text polished on-device"
                        syncContextAsync()
                    }
                    return
                }
                val extracted = ic.getExtractedText(android.view.inputmethod.ExtractedTextRequest(), 0)
                val original = extracted?.text?.toString()
                if (original.isNullOrEmpty() || extracted.partialStartOffset >= 0) {
                    status.text = "Select text first; this app does not expose the full field"
                    return
                }
                val polished = LocalTextPolisher.polish(original)
                if (polished == original) {
                    status.text = "No changes needed"
                    return
                }
                ic.beginBatchEdit()
                try {
                    ic.setSelection(0, original.length)
                    ic.commitText(polished, 1)
                    val cursor = polished.length
                    ic.setSelection(cursor, cursor)
                } finally {
                    ic.endBatchEdit()
                }
                status.text = "Text polished on-device"
                syncContextAsync()
            } catch (_: Exception) {
                status.text = "Polish unavailable in this text field"
            }
        }
        fun addCursorKey(row: LinearLayout, label: String, keyCode: Int, description: String) {
            val key = mk(label, 1f, t.special, t.text, 15f)
            key.cell.contentDescription = description
            attach(key, onLong = { setSelectionMode(true) }) { moveCursor(keyCode) }
            row.addView(key.cell)
        }
        val cursor = hrow()
        addCursorKey(cursor, "←", KeyEvent.KEYCODE_DPAD_LEFT, "Move cursor left; long press to extend selection")
        addCursorKey(cursor, "↑", KeyEvent.KEYCODE_DPAD_UP, "Move cursor up; long press to extend selection")
        addCursorKey(cursor, "↓", KeyEvent.KEYCODE_DPAD_DOWN, "Move cursor down; long press to extend selection")
        addCursorKey(cursor, "→", KeyEvent.KEYCODE_DPAD_RIGHT, "Move cursor right; long press to extend selection")
        addCursorKey(cursor, "Home", KeyEvent.KEYCODE_MOVE_HOME, "Move to line start")
        addCursorKey(cursor, "End", KeyEvent.KEYCODE_MOVE_END, "Move to line end")
        col.addView(cursor, lp(MATCH, dp(36)))

        val actions = hrow()
        fun iconAction(row: LinearLayout, icon: Int, label: String, action: () -> Unit): K {
            val key = mk("", 1f, t.special, t.text, 12f, icon)
            key.cell.contentDescription = label
            attach(key) { action() }
            row.addView(key.cell)
            return key
        }
        selectionKey = iconAction(actions, R.drawable.ic_editor_select, "Toggle selection mode") {
            setSelectionMode(!editorSelecting)
        }
        iconAction(actions, R.drawable.ic_editor_select_all, "Select all") { selectAllText() }
        iconAction(actions, R.drawable.ic_editor_cut, "Cut selected text") { copyOrCut(true) }
        iconAction(actions, R.drawable.ic_editor_copy, "Copy selected text") { copyOrCut(false) }
        iconAction(actions, R.drawable.ic_editor_paste, "Paste text") { pasteText() }
        iconAction(actions, R.drawable.ic_trash, "Delete selection or previous character") {
            val ic = connection() ?: return@iconAction
            try {
                val selected = ic.getSelectedText(0)
                if (!selected.isNullOrEmpty()) ic.commitText("", 1)
                else ic.deleteSurroundingText(1, 0)
                syncContextAsync()
            } catch (_: Exception) { }
        }
        col.addView(actions, lp(MATCH, dp(38)))

        val footer = hrow()
        val polish = mk("Quick polish", 1.4f, t.accent, t.accentText, 10f)
        polish.cell.contentDescription = "Apply on-device punctuation, spacing and sentence-capitalization fixes"
        attach(polish) { quickPolish() }
        footer.addView(polish.cell)
        col.addView(footer, lp(MATCH, dp(32)))
        col.addView(status, lp(MATCH, dp(16)))
        return col
    }

    private fun buildResize(): View {
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(10), dp(5), dp(10), dp(2)) }
        val value = TextView(ctx).apply {
            text = "Keyboard height  ·  ${Prefs.keyH(ctx)} dp"
            textSize = 13f
            setTextColor(t.text)
            gravity = Gravity.CENTER
        }
        col.addView(value, lp(MATCH, dp(34)))
        val handle = TextView(ctx).apply {
            text = "↕   Drag up or down to resize   ↕"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(t.text)
            background = rounded(t.special, 14)
            contentDescription = "Drag to resize keyboard height"
        }
        var lastY = 0f
        var heightValue = Prefs.keyH(ctx)
        val persistHeight = Runnable { Prefs.setI(ctx, "keyh", heightValue) }
        handle.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { lastY = event.rawY; true }
                MotionEvent.ACTION_MOVE -> {
                    val delta = ((lastY - event.rawY) / resources.displayMetrics.density).toInt()
                    if (delta != 0) {
                        val next = (heightValue + delta).coerceIn(34, 64)
                        heightValue = next
                        hd.removeCallbacks(persistHeight)
                        hd.postDelayed(persistHeight, 180L)
                        kh = next
                        cellH = baseCellH()
                        value.text = "Keyboard height  ·  $next dp"
                        panel.layoutParams = panel.layoutParams.apply { height = panelH() }
                        panel.getChildAt(0)?.let { child ->
                            child.layoutParams = child.layoutParams.apply { height = panelH() }
                        }
                        panel.requestLayout()
                        kbd.requestLayout()
                        lastY = event.rawY
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    hd.removeCallbacks(persistHeight)
                    Prefs.setI(ctx, "keyh", heightValue)
                    true
                }
                else -> true
            }
        }
        col.addView(handle, LinearLayout.LayoutParams(MATCH, 0, 1f).apply { topMargin = dp(8); bottomMargin = dp(8) })
        val row = hrow()
        val reset = mk("Standard", 1f, t.special, t.text, 12f); attach(reset) {
            hd.removeCallbacks(persistHeight)
            heightValue = 46
            Prefs.setI(ctx, "keyh", 46)
            Prefs.setI(ctx, "kbdwidth", 100)
            Prefs.setI(ctx, "onehand", 0)
            Prefs.setB(ctx, "float", false)
            kh = 46
            cellH = baseCellH()
            value.text = "Keyboard height  ·  46 dp"
            panel.layoutParams = panel.layoutParams.apply { height = panelH() }
            panel.getChildAt(0)?.let { child -> child.layoutParams = child.layoutParams.apply { height = panelH() } }
            panel.requestLayout()
            kbd.requestLayout()
        }; row.addView(reset.cell)
        val done = mk("Done", 1f, t.accent, t.accentText, 12f); attach(done) { showPanel("letters") }; row.addView(done.cell)
        col.addView(row, lp(MATCH, dp(42)))
        return col
    }

    private fun buildClip(): View {
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(5), dp(2), dp(5), 0)
        }
        val head = LinearLayout(ctx).apply { gravity = Gravity.CENTER_VERTICAL }
        head.addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(ctx).apply {
                text = "Clipboard"
                textSize = 15f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(t.text)
            })
            addView(TextView(ctx).apply {
                text = "${Prefs.clips(ctx).size} saved snippets · stored on this device"
                textSize = 10f
                setTextColor(darken(t.text, 0.62f))
            })
        }, lp(0, dp(40), 1f))
        head.addView(iconImg(R.drawable.ic_trash) {
            Prefs.clearClips(ctx)
            showPanel("clip")
        }.apply { contentDescription = "Clear clipboard history" }, lp(dp(42), dp(40)))
        col.addView(head, lp(MATCH, dp(42)))

        val body = ScrollView(ctx).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val list = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val clips = Prefs.clips(this)
        val pinned = Prefs.pinnedClips(this)
        fun section(label: String) {
            list.addView(TextView(ctx).apply {
                text = label
                textSize = 10f
                typeface = Typeface.DEFAULT_BOLD
                letterSpacing = 0.08f
                setTextColor(darken(t.text, 0.62f))
                setPadding(dp(7), dp(7), 0, dp(5))
            })
        }
        fun clipRow(value: String, isPinned: Boolean) {
            val row = LinearLayout(ctx).apply {
                gravity = Gravity.CENTER_VERTICAL
                background = keyBg(t.key, 13)
                setPadding(dp(10), dp(3), dp(4), dp(3))
            }
            val preview = TextView(ctx).apply {
                text = value
                textSize = 12f
                setTextColor(t.text)
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(3), dp(5), dp(5), dp(5))
                contentDescription = "Paste clipboard item"
                setOnClickListener {
                    currentInputConnection?.commitText(value, 1)
                    setLocalContext(cachedBeforeCursor + value)
                    showPanel("letters")
                }
            }
            row.addView(preview, lp(0, WRAP, 1f))
            row.addView(TextView(ctx).apply {
                text = if (isPinned) "📌" else "☆"
                textSize = 17f
                gravity = Gravity.CENTER
                setTextColor(t.text)
                contentDescription = if (isPinned) "Unpin item" else "Pin item"
                setOnClickListener {
                    Prefs.togglePinnedClip(ctx, value)
                    showPanel("clip")
                }
            }, lp(dp(34), dp(38)))
            row.addView(TextView(ctx).apply {
                text = "×"
                textSize = 21f
                gravity = Gravity.CENTER
                setTextColor(darken(t.text, 0.65f))
                contentDescription = "Remove clipboard item"
                setOnClickListener {
                    Prefs.removeClip(ctx, value)
                    showPanel("clip")
                }
            }, lp(dp(30), dp(38)))
            list.addView(row, lp(MATCH, WRAP).apply {
                setMargins(dp(2), dp(2), dp(2), dp(4))
            })
        }

        if (pinned.isNotEmpty()) {
            section("PINNED")
            pinned.forEach { clipRow(it, true) }
        }
        val recent = clips.filterNot { it in pinned }
        if (recent.isNotEmpty()) {
            section("RECENT")
            recent.forEach { clipRow(it, false) }
        }
        if (clips.isEmpty()) {
            list.addView(TextView(ctx).apply {
                text = "Your copied text appears here. Tap an item to paste it."
                textSize = 12f
                setTextColor(darken(t.text, 0.62f))
                setPadding(dp(12), dp(16), dp(12), dp(12))
            })
        }
        body.addView(list)
        col.addView(body, lp(MATCH, bodyHeight()))
        col.addView(backBar())
        return col
    }

    // Letters, symbols and menu pages are cached; emoji (recents) and clipboard (history) must be fresh every time.
    private fun pageFor(m: String): View {
        val cacheable = m != "emoji" && m != "clip" && m != "menu"
        if (cacheable) pageCache[m]?.let { return it }
        val v = when (m) {
            "symbols" -> buildSymbols(1)
            "symbols2" -> buildSymbols(2)
            "emoji" -> buildEmoji()
            "clip" -> buildClip()
            "editor" -> buildEditor()
            "resize" -> buildResize()
            "menu" -> buildMenu()
            else -> buildLetters()
        }
        if (cacheable) pageCache[m] = v
        return v
    }

    private fun showPanel(m: String) {
        dismissPopups()
        val prev = mode
        mode = m
        cellH = when (m) {
            "emoji", "clip", "menu" -> baseCellH()
            "editor" -> dp(38)
            else -> ((panelH() - dp(4)) / rowCountFor(m)).coerceAtLeast(dp(28))
        }
        // Reuse the already-built page and emoji category grids. Clearing these on every
        // switch defeated the cache and rebuilt dozens of views during ordinary typing.
        val page = pageFor(m)
        (page.parent as? ViewGroup)?.removeView(page)
        panel.removeAllViews()
        val targetPanelHeight = when (m) {
            "emoji" -> panelH() + emojiExtraHeight()
            "editor" -> dp(158)
            else -> panelH()
        }
        panel.layoutParams = panel.layoutParams.apply { height = targetPanelHeight }
        page.alpha = 0f
        page.translationY = dp(5).toFloat()
        panel.addView(page, FrameLayout.LayoutParams(MATCH, targetPanelHeight))
        page.animate().cancel()
        page.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(145L)
            .setInterpolator(DecelerateInterpolator())
            .start()
        if (m == "letters") updateCase()
        if (m != "editor") editorSelecting = false
        refreshEnter()
        if (m == "menu" || prev == "menu") buildTop()
        if (m == "letters") scheduleRefresh()
    }
}
