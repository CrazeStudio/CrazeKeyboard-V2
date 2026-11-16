package com.crazestudio.crazekeyboard

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
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
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

internal class K(val cell: FrameLayout, val tv: TextView, val iv: ImageView?)

class CrazeKeyboardService : InputMethodService() {
    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    private val ctx: Context get() = this
    private val hd = Handler(Looper.getMainLooper())
    private val suggestionThread = HandlerThread("craze-suggestions", Process.THREAD_PRIORITY_BACKGROUND).apply { start() }
    private val suggestionWorker = Handler(suggestionThread.looper)

    @Volatile private var cachedBeforeCursor = ""
    @Volatile private var cachedHasEditorText = false
    @Volatile private var contextEpoch = 0L
    @Volatile private var predictionEpoch = 0L
    private var predictionTask: Runnable? = null
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
    private val lastSuggestions = arrayOf("", "", "")
    private var lastTopState = ""
    private val letterKeys = ArrayList<Pair<TextView, String>>()
    private val enterKeys = ArrayList<K>()
    private val pageCache = HashMap<String, View>()
    private var shiftKey: K? = null
    private var t = Prefs.themes[0]
    private var shift = 0
    private var autoShift = false
    private var mode = "letters"
    private var menuCategory = "All"
    private var floating = false
    private var suggestOn = true
    @Volatile private var incognito = false
    @Volatile private var manualIncognito = false
    private var autoCorrectOn = true
    private var doubleSpaceOn = true
    private var nextWordOn = true
    private var swipeBackspaceOn = true
    private var keyPopupOn = true
    private var autoCapOn = true
    private var numberHintsOn = true
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
    private val noPersonalizedLearning = 0x1000000

    private data class KeyCenter(val char: String, val cx: Float, val cy: Float)
    private var swipeKeyCenters: List<KeyCenter> = emptyList()

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
        swipeBackspaceOn = Prefs.swipeBackspace(this)
        keyPopupOn = Prefs.keyPopup(this)
        autoCapOn = Prefs.autoCap(this)
        numberHintsOn = Prefs.numberHints(this)
        manualIncognito = Prefs.manualIncognito(this)
    }
    private fun rowCountFor(page: String): Int = when (page) {
        "letters" -> if (numRow) 5 else 4
        "symbols" -> 4
        "symbols2" -> if (numRow) 5 else 4
        "numpad" -> 4
        else -> 5
    }
    private fun baseCellH() = dp(kh) + dp(6)
    private fun panelH() = baseCellH() * 5 + dp(4)
    private fun topH() = dp(44)

    // ---------- drawing & color helpers ----------
    private fun blend(fg: Int, bg: Int, fgWeight: Float): Int {
        val w = fgWeight.coerceIn(0f, 1f)
        val r = (Color.red(fg) * w + Color.red(bg) * (1f - w)).toInt()
        val g = (Color.green(fg) * w + Color.green(bg) * (1f - w)).toInt()
        val b = (Color.blue(fg) * w + Color.blue(bg) * (1f - w)).toInt()
        return Color.rgb(r, g, b)
    }
    private fun mutedInk() = blend(t.text, t.bg, 0.56f)
    private fun darken(c: Int, f: Float = 0.78f): Int {
        val r = ((c shr 16 and 0xFF) * f).toInt()
        val g = ((c shr 8 and 0xFF) * f).toInt()
        val b = ((c and 0xFF) * f).toInt()
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
    private fun rounded(c: Int, r: Int = 11, strokeColor: Int = 0) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(r).toFloat()
        setColor(c)
        if (strokeColor != 0) setStroke(dp(1), strokeColor)
    }
    private fun keyBg(c: Int, r: Int = 11): Drawable =
        LayerDrawable(arrayOf<Drawable>(rounded(darken(c), r), rounded(c, r))).apply { setLayerInset(1, 0, 0, 0, dp(2)) }

    private fun rippleFg(radius: Int) = RippleDrawable(
        ColorStateList.valueOf(Color.argb(42, Color.red(t.accent), Color.green(t.accent), Color.blue(t.accent))),
        null,
        rounded(Color.WHITE, radius)
    )

    private fun iconImg(res: Int, tint: Int = t.text, padDp: Int = 10, a: () -> Unit) = ImageView(ctx).apply {
        setImageResource(res)
        setColorFilter(tint)
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        setPadding(dp(padDp), dp(padDp), dp(padDp), dp(padDp))
        isClickable = true
        isFocusable = true
        foreground = rippleFg(12)
        setOnClickListener { a() }
    }

    override fun onCreate() {
        super.onCreate()
        CrazeLmTflitePredictor.refreshInitialStatus(applicationContext)
        DownloadedVocabularyIndex.loadInstalledAsync(applicationContext)
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipListener = ClipboardManager.OnPrimaryClipChangedListener {
            try {
                if (incognito || manualIncognito || !Prefs.b(ctx, "clipboardHistory", false)) return@OnPrimaryClipChangedListener
                val clip = cm.primaryClip ?: return@OnPrimaryClipChangedListener
                if (Build.VERSION.SDK_INT >= 33 &&
                    clip.description?.extras?.getBoolean("android.content.extra.IS_SENSITIVE", false) == true) return@OnPrimaryClipChangedListener
                if (clip.itemCount > 0) clip.getItemAt(0)?.text?.toString()?.let { Prefs.addClip(ctx, it) }
            } catch (_: Exception) { }
        }
        cm.addPrimaryClipChangedListener(clipListener)
    }

    override fun onDestroy() {
        try { (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).removePrimaryClipChangedListener(clipListener) } catch (_: Exception) { }
        UserLanguageModel.flush(applicationContext)
        TinyNeuralRanker.flush(applicationContext)
        CrazeLmTflitePredictor.unloadNow(applicationContext)
        hd.removeCallbacksAndMessages(null)
        dismissPopups()
        VoiceActivity.callback = null
        TextEditorActivity.callback = null
        suggestionWorker.removeCallbacksAndMessages(null)
        suggestionThread.quitSafely()
        pageCache.clear()
        super.onDestroy()
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

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
        swipeKeyCenters = emptyList()
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
            setBackgroundColor(Color.TRANSPARENT)
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

    private fun buildHandle(): View {
        val row = FrameLayout(ctx)
        val grip = ImageView(ctx).apply {
            setImageResource(R.drawable.ic_grip)
            setColorFilter(t.text)
            alpha = 0.65f
            contentDescription = "Move floating keyboard"
        }
        row.addView(grip, FrameLayout.LayoutParams(dp(38), dp(22), Gravity.CENTER))
        val dock = iconImg(R.drawable.ic_dock, padDp = 4) {
            Prefs.setB(ctx, "float", false)
            rebuild()
        }.apply { contentDescription = "Dock keyboard" }
        row.addView(dock, FrameLayout.LayoutParams(dp(40), dp(26), Gravity.END or Gravity.CENTER_VERTICAL))
        val resize = TextView(ctx).apply {
            text = "↔"
            textSize = 18f
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
        incognito = manualIncognito || isPasswordField(cls, v) || (info.imeOptions and noPersonalizedLearning) != 0

        CrazeLmTflitePredictor.cancelIdleUnload()
        if (suggestOn && nextWordOn && Prefs.string(this, "activeLanguage", "en") == "en" &&
            CrazeLmAssets.hasLocalFiles(applicationContext)) {
            CrazeLmTflitePredictor.initializeAsync(applicationContext, allowNetworkDownload = false)
        }

        shift = 0
        autoShift = false
        editorSelecting = false
        lastSuggestions.fill("")
        cachedCorrection = null
        lastCorrection = null
        pendingCorrectedWord = null
        cachedBeforeCursor = ""
        cachedHasEditorText = false
        contextEpoch++
        mode = when (cls) {
            InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_PHONE -> "numpad"
            else -> "letters"
        }
        buildTop()
        showPanel(mode)
        syncContextAsync()
        updateSuggestions()
        deliverVoice()
        deliverEditor()
    }

    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        syncContextAsync()
        if (::top.isInitialized) scheduleRefresh()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        hd.removeCallbacksAndMessages(null)
        suggestionWorker.removeCallbacks(contextSyncR)
        predictionTask?.let(suggestionWorker::removeCallbacks)
        predictionEpoch++
        contextEpoch++
        dismissPopups()
        lastCorrection = null
        cachedCorrection = null
        pendingCorrectedWord = null
        UserLanguageModel.flush(applicationContext)
        TinyNeuralRanker.flush(applicationContext)
        CrazeLmTflitePredictor.scheduleIdleUnload(applicationContext)
        super.onFinishInputView(finishingInput)
    }

    private val refreshR = Runnable { if (::top.isInitialized) { autoCap(); updateSuggestions() } }
    private fun scheduleRefresh() {
        hd.removeCallbacks(refreshR)
        hd.postDelayed(refreshR, 34)
    }

    private fun hasEditorText(): Boolean = cachedHasEditorText || cachedBeforeCursor.isNotEmpty()

    private fun syncContextAsync() {
        suggestionWorker.removeCallbacks(contextSyncR)
        suggestionWorker.postDelayed(contextSyncR, 40)
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
        !suggestOn || !hasEditorText() -> "tools"
        else -> "suggestions"
    }

    private fun isToolActive(id: String): Boolean = when (id) {
        "incognito" -> manualIncognito
        "onehand" -> Prefs.i(ctx, "onehand", 0) != 0
        "floating" -> floating
        else -> false
    }

    private fun toolLabel(id: String): String = when (id) {
        "editor" -> "Edit text"
        "fulleditor" -> "Full editor"
        "clipboard" -> "Clipboard"
        "emoji" -> "Emoji"
        "numpad" -> "Numpad"
        "incognito" -> if (manualIncognito) "Private ON" else "Private"
        "voice" -> "Voice"
        "theme" -> "Theme Studio"
        "onehand" -> when (Prefs.i(ctx, "onehand", 0)) {
            1 -> "Left hand"
            2 -> "Right hand"
            else -> "One-handed"
        }
        "floating" -> if (floating) "Dock" else "Floating"
        "resize" -> "Resize"
        "settings" -> "Settings"
        "keyboard" -> "Keyboard"
        "hide" -> "Hide"
        else -> "Tool"
    }

    private fun toolIcon(id: String): Int = when (id) {
        "editor" -> R.drawable.ic_edit
        "fulleditor" -> R.drawable.ic_editor_select_all
        "clipboard" -> R.drawable.ic_clip
        "emoji" -> R.drawable.ic_emoji
        "numpad" -> R.drawable.ic_numpad
        "incognito" -> R.drawable.ic_shield_private
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

    private fun toggleManualIncognito() {
        manualIncognito = !manualIncognito
        Prefs.setB(ctx, "manualIncognito", manualIncognito)
        val ei = currentInputEditorInfo
        val cls = (ei?.inputType ?: 0) and InputType.TYPE_MASK_CLASS
        val v = (ei?.inputType ?: 0) and InputType.TYPE_MASK_VARIATION
        incognito = manualIncognito || isPasswordField(cls, v) || ((ei?.imeOptions ?: 0) and noPersonalizedLearning) != 0
        Toast.makeText(
            ctx,
            if (manualIncognito) "Private mode ON — zero learning or clipboard saving" else "Private mode OFF",
            Toast.LENGTH_SHORT
        ).show()
        buildTop()
        if (mode == "menu") showPanel("menu") else scheduleRefresh()
    }

    private fun runTool(id: String) {
        when (id) {
            "editor" -> showPanel("editor")
            "fulleditor" -> { showPanel("letters"); launchTextEditor() }
            "clipboard" -> {
                if (incognito) Toast.makeText(ctx, "Clipboard is disabled in private mode", Toast.LENGTH_SHORT).show()
                else showPanel("clip")
            }
            "emoji" -> showPanel("emoji")
            "numpad" -> showPanel("numpad")
            "incognito" -> toggleManualIncognito()
            "voice" -> { showPanel("letters"); startVoice() }
            "theme" -> startActivity(Intent(ctx, ThemeStudioActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
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
            setPadding(dp(6), dp(3), dp(6), dp(3))
        }
        if (state != "emojiSearch") {
            val menuBtn = FrameLayout(ctx).apply {
                background = rounded(if (state == "menu") t.accent else t.special, 12)
                foreground = rippleFg(12)
                isClickable = true
                isFocusable = true
                contentDescription = if (state == "menu") "Close tools" else "Open tools"
                setOnClickListener { showPanel(if (mode == "menu") "letters" else "menu") }
                addView(ImageView(ctx).apply {
                    setImageResource(if (state == "menu") R.drawable.ic_close else R.drawable.ic_menu)
                    setColorFilter(if (state == "menu") t.accentText else t.text)
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                    setPadding(dp(7), dp(7), dp(7), dp(7))
                }, FrameLayout.LayoutParams(MATCH, MATCH))
            }
            row.addView(menuBtn, lp(dp(38), dp(34)).apply { rightMargin = dp(4) })

            if (incognito && state != "menu") {
                val shieldBadge = ImageView(ctx).apply {
                    setImageResource(R.drawable.ic_shield_private)
                    setColorFilter(t.accent)
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                    setPadding(dp(5), dp(5), dp(5), dp(5))
                    background = rounded(t.special, 10)
                    contentDescription = "Private typing active"
                    setOnClickListener { toggleManualIncognito() }
                }
                row.addView(shieldBadge, lp(dp(30), dp(30)).apply { rightMargin = dp(4) })
            }
        }

        when (state) {
            "emojiSearch" -> {
                row.addView(iconImg(R.drawable.ic_close, padDp = 8) {
                    emojiSearchActive = false
                    emojiSearchQuery = ""
                    buildTop()
                    showPanel("letters")
                }.apply { contentDescription = "Close emoji search" }, lp(dp(38), MATCH))
                row.addView(TextView(ctx).apply {
                    text = if (emojiSearchQuery.isBlank()) "Search emoji…" else emojiSearchQuery
                    textSize = 14f
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    gravity = Gravity.CENTER_VERTICAL
                    setTextColor(if (emojiSearchQuery.isBlank()) mutedInk() else t.text)
                }, lp(0, MATCH, 1f))
                emojiSearchMatches(emojiSearchQuery).take(3).forEach { emoji ->
                    row.addView(TextView(ctx).apply {
                        text = emoji
                        textSize = 21f
                        gravity = Gravity.CENTER
                        foreground = rippleFg(10)
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
                    text = if (incognito) "Tools · Private Mode" else "Keyboard Tools"
                    textSize = 14f
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    setTextColor(t.text)
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(6), 0, 0, 0)
                }, lp(0, MATCH, 1f))
                val settingsShortcut = iconImg(R.drawable.ic_settings, padDp = 7) {
                    runTool("settings")
                }.apply {
                    contentDescription = "Settings"
                    background = rounded(t.special, 10)
                }
                row.addView(settingsShortcut, lp(dp(36), dp(34)))
            }
            "tools" -> {
                val quickTools = Prefs.toolbarTools(ctx).take(5)
                quickTools.forEach { id ->
                    val active = isToolActive(id)
                    val btn = FrameLayout(ctx).apply {
                        if (active) background = rounded(t.accent, 11)
                        foreground = rippleFg(11)
                        isClickable = true
                        isFocusable = true
                        contentDescription = toolLabel(id)
                        setOnClickListener { runTool(id) }
                        addView(ImageView(ctx).apply {
                            setImageResource(toolIcon(id))
                            setColorFilter(if (active) t.accentText else t.text)
                            scaleType = ImageView.ScaleType.CENTER_INSIDE
                            setPadding(dp(7), dp(7), dp(7), dp(7))
                        }, FrameLayout.LayoutParams(MATCH, MATCH))
                    }
                    row.addView(btn, lp(0, dp(36), 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
                }
                if (quickTools.isEmpty()) {
                    row.addView(TextView(ctx).apply {
                        text = "CrazeKeyboard"
                        textSize = 13f
                        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                        setTextColor(t.text)
                        gravity = Gravity.CENTER
                    }, lp(0, MATCH, 1f))
                }
            }
            else -> {
                for (i in 0..2) {
                    val tv = TextView(ctx).apply {
                        gravity = Gravity.CENTER
                        textSize = 14.5f
                        setTextColor(t.text)
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                        setPadding(dp(6), 0, dp(6), 0)
                        foreground = rippleFg(10)
                        if (i == 1) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                        text = lastSuggestions.getOrElse(i) { "" }
                        setOnClickListener { pickSuggestion(text.toString()) }
                        setOnLongClickListener {
                            val word = text?.toString()?.trim().orEmpty()
                            if (word.isNotEmpty() && word in Prefs.learned(ctx)) {
                                Prefs.unlearn(ctx, word)
                                Toast.makeText(ctx, "Removed \"$word\" from learned words", Toast.LENGTH_SHORT).show()
                                scheduleRefresh()
                                true
                            } else false
                        }
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

    private fun applySuggestionRow(words: List<String>) {
        for (i in 0..2) {
            val nextVal = words.getOrNull(i).orEmpty()
            lastSuggestions[i] = nextVal
            if (i < sugViews.size) {
                val view = sugViews[i]
                if (view.text?.toString() != nextVal) {
                    view.text = nextVal
                }
            }
        }
    }

    private fun updateSuggestions() {
        val state = desiredTopState()
        if (state != lastTopState) buildTop()
        if (state != "suggestions" || sugViews.isEmpty()) {
            if (state != "suggestions") {
                ++predictionEpoch
                predictionTask?.let(suggestionWorker::removeCallbacks)
                predictionTask = null
                cachedCorrection = null
                lastSuggestions.fill("")
            }
            return
        }

        val snapshot = cachedBeforeCursor
        if (snapshot.isBlank()) {
            ++predictionEpoch
            predictionTask?.let(suggestionWorker::removeCallbacks)
            predictionTask = null
            cachedCorrection = null
            applySuggestionRow(emptyList())
            return
        }

        val epoch = ++predictionEpoch
        predictionTask?.let(suggestionWorker::removeCallbacks)
        val task = Runnable {
            val activeLanguage = Prefs.string(ctx, "activeLanguage", "en")
            val current = wordAtCursor(snapshot)

            if (activeLanguage != "en") {
                cachedCorrection = null
                val local = if (activeLanguage == "bn" || activeLanguage == "hi") {
                    DownloadedVocabularyIndex.suggest(activeLanguage, current, 3)
                } else emptyList()
                hd.post {
                    if (epoch == predictionEpoch && ::top.isInitialized && desiredTopState() == "suggestions") {
                        applySuggestionRow(local)
                    }
                }
                return@Runnable
            }

            if (current.isNotEmpty()) {
                // Typing a word: compute instant local completions + auto-correction.
                val fix = if (autoCorrectOn && !incognito) Words.correction(ctx, current) else null
                cachedCorrection = fix?.let { current to it }
                val previous = tokensBeforeCursor(snapshot).let {
                    if (it.lastOrNull() == current.lowercase()) it.dropLast(1).takeLast(2) else it.takeLast(2)
                }
                val completions = if (!incognito && Prefs.b(ctx, "personalized", true)) {
                    UserLanguageModel.rankPrefix(ctx, previous, current, 3)
                        .ifEmpty { Words.suggestPool(ctx, current, 3) }
                } else {
                    Words.suggestPool(ctx, current, 3)
                }
                val merged = ArrayList<String>(3)
                if (fix != null) merged.add(fix)
                completions.forEach { w -> if (merged.size < 3 && !merged.any { it.equals(w, true) }) merged.add(w) }
                if (merged.isEmpty()) merged.add(current)
                hd.post {
                    if (epoch == predictionEpoch && ::top.isInitialized && desiredTopState() == "suggestions") {
                        applySuggestionRow(merged)
                    }
                }
                return@Runnable
            }

            // Space / punctuation boundary: show fast local n-gram first, then refine with CrazeLM if loaded.
            cachedCorrection = null
            val previous = tokensBeforeCursor(snapshot).takeLast(2)
            val localNext = if (!incognito && Prefs.b(ctx, "personalized", true)) {
                UserLanguageModel.rank(ctx, previous, listOf("I", "the", "to"), 3)
            } else {
                listOf("I", "The", "Thanks")
            }
            if (localNext.isNotEmpty()) {
                hd.post {
                    if (epoch == predictionEpoch && ::top.isInitialized && desiredTopState() == "suggestions") {
                        applySuggestionRow(localNext)
                    }
                }
            }

            if (nextWordOn && CrazeLmTflitePredictor.isReady()) {
                CrazeLmTflitePredictor.predictNextWordsAsync(applicationContext, snapshot, 3) { modelWords ->
                    if (epoch != predictionEpoch) return@predictNextWordsAsync
                    val clean = modelWords.asSequence()
                        .map { it.trim() }
                        .filter { it.length in 1..24 && it.all { ch -> ch.isLetter() || ch == '\'' || ch == '-' } }
                        .distinctBy { it.lowercase() }
                        .take(3)
                        .toList()
                    val ranked = if (!incognito && Prefs.b(ctx, "personalized", true) && clean.isNotEmpty()) {
                        UserLanguageModel.rank(ctx, previous, clean, 3).ifEmpty { clean }
                    } else clean
                    hd.post {
                        if (epoch == predictionEpoch && ::top.isInitialized &&
                            desiredTopState() == "suggestions" && ranked.isNotEmpty()) {
                            applySuggestionRow(ranked)
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
        cachedCorrection = null
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
        if (!autoCapOn || shift == 2 || mode != "letters") return
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
            k.cell.contentDescription = when (shift) {
                2 -> "Caps lock on"
                1 -> "Shift on"
                else -> "Shift"
            }
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
        cachedCorrection = null
        scheduleRefresh()
    }

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

    private fun deletePreviousWord() {
        val ic = currentInputConnection ?: return
        val before = cachedBeforeCursor
        if (before.isEmpty()) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
            return
        }

        fun previousStart(end: Int): Int {
            if (end <= 0) return 0
            return end - Character.charCount(Character.codePointBefore(before, end))
        }
        fun previousCodePoint(end: Int): Int = Character.codePointBefore(before, end)
        fun isWordCodePoint(codePoint: Int): Boolean =
            Character.isLetterOrDigit(codePoint) || codePoint == '\''.code || codePoint == '’'.code

        var idx = before.length
        while (idx > 0 && Character.isWhitespace(previousCodePoint(idx))) idx = previousStart(idx)
        if (idx > 0) {
            if (isWordCodePoint(previousCodePoint(idx))) {
                while (idx > 0 && isWordCodePoint(previousCodePoint(idx))) idx = previousStart(idx)
            } else {
                idx = previousStart(idx)
            }
        }
        val deleteCodePoints = before.codePointCount(idx, before.length)
        if (deleteCodePoints > 0) {
            try {
                ic.deleteSurroundingTextInCodePoints(deleteCodePoints, 0)
            } catch (_: Exception) {
                ic.deleteSurroundingText(before.length - idx, 0)
            }
            setLocalContext(before.substring(0, idx))
            if (hapticsOn) panel.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            scheduleRefresh()
        }
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

    private fun enterDescription() = when (actionId()) {
        EditorInfo.IME_ACTION_SEARCH -> "Search"
        EditorInfo.IME_ACTION_SEND -> "Send"
        EditorInfo.IME_ACTION_GO -> "Go"
        EditorInfo.IME_ACTION_NEXT -> "Next"
        EditorInfo.IME_ACTION_DONE -> "Done"
        else -> "Enter"
    }

    private fun refreshEnter() {
        if (enterKeys.isEmpty()) return
        val icon = enterIcon()
        val description = enterDescription()
        enterKeys.forEach {
            it.iv?.setImageResource(icon)
            it.cell.contentDescription = description
        }
    }

    private fun launchTextEditor() {
        if (incognito) {
            Toast.makeText(ctx, "Text editor is disabled in private mode", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(ctx, "Could not open the text editor", Toast.LENGTH_SHORT).show()
        }
    }

    private fun deliverEditor() {
        if (currentInputEditorInfo?.packageName == packageName) return
        val edited = TextEditorActivity.take() ?: return
        val ic = currentInputConnection
        if (ic == null) {
            TextEditorActivity.post(edited)
            return
        }
        ic.commitText(edited, 1)
        setLocalContext(cachedBeforeCursor + edited)
        scheduleRefresh()
        TextEditorActivity.callback = null
    }

    private fun startVoice() {
        VoiceActivity.callback = { deliverVoice() }
        try {
            startActivity(Intent(this, VoiceActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) { }
    }

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

    private fun dismissAlts() { try { popup?.dismiss() } catch (_: Exception) { }; popup = null }
    private fun dismissPopups() { dismissAlts(); try { preview?.dismiss() } catch (_: Exception) { }; preview = null; prevTv = null }

    private fun showPreview(k: K) {
        if (!keyPopupOn) return
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
        } catch (_: Exception) {
            preview = null; prevTv = null
        }
    }

    private fun hidePreview() { prevTv?.visibility = View.INVISIBLE }

    private fun captureSwipeKeyCenters() {
        val location = IntArray(2)
        swipeKeyCenters = letterKeys.mapNotNull { (view, value) ->
            if (view.visibility != View.VISIBLE || view.width == 0 || view.height == 0) null
            else {
                view.getLocationOnScreen(location)
                KeyCenter(value.lowercase(), location[0] + view.width / 2f, location[1] + view.height / 2f)
            }
        }
    }

    private fun nearestLetterAt(rawX: Float, rawY: Float): String? {
        var best = dp(52) * dp(52).toFloat()
        var selected: String? = null
        swipeKeyCenters.forEach { key ->
            val dx = rawX - key.cx
            val dy = rawY - key.cy
            val distance = dx * dx + dy * dy
            if (distance < best) { best = distance; selected = key.char }
        }
        return selected
    }

    private fun commitSwipe(sequence: String): Boolean {
        if (sequence.length < 3) return false
        val word = Words.swipeCandidates(this, sequence, 3).firstOrNull() ?: return false
        val connection = currentInputConnection ?: targetInputConnection ?: return false
        val previous = tokensBeforeCursor(cachedBeforeCursor).takeLast(2)
        return try {
            acceptPendingCorrection()
            connection.commitText("$word ", 1)
            setLocalContext(cachedBeforeCursor + "$word ")
            if (!incognito && Prefs.b(this, "personalized", true)) {
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
        val longR = Runnable {
            fired = true
            swipeTracking = false
            swipePath.setLength(0)
            hidePreview()
            onLong?.invoke()
        }
        val repR = object : Runnable { override fun run() { tap(); hd.postDelayed(this, 45) } }
        k.cell.isClickable = true
        k.cell.isFocusable = true
        k.cell.setOnClickListener { tap() }
        k.cell.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    fired = false; swiped = false; swipeMoved = false
                    swipeTracking = letterKey && Prefs.b(ctx, "swipeTyping", false) && Prefs.string(ctx, "activeLanguage", "en") == "en"
                    swipePath.setLength(0)
                    if (swipeTracking) {
                        captureSwipeKeyCenters()
                        swipeLastKey = nearestLetterAt(e.rawX, e.rawY).orEmpty()
                        if (swipeLastKey.isNotEmpty()) swipePath.append(swipeLastKey)
                    } else {
                        swipeLastKey = ""
                    }
                    k.tv.alpha = 0.65f
                    if (hapticsOn) k.cell.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    if (keySoundOn) {
                        try { (getSystemService(Context.AUDIO_SERVICE) as AudioManager).playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, 0.45f) }
                        catch (_: Exception) { }
                    }
                    if (showPrev) showPreview(k)
                    if (repeat) { tap(); hd.postDelayed(repR, 380) }
                    else if (fast) tap()
                    else if (onLong != null) hd.postDelayed(longR, 350)
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
                foreground = rippleFg(8)
                setPadding(dp(10), dp(8), dp(10), dp(8)); setOnClickListener { type(ch.toString()) }
            })
        }
        try {
            popup = PopupWindow(row, WRAP, WRAP).apply {
                setBackgroundDrawable(ColorDrawable(0)); isOutsideTouchable = true
                showAsDropDown(anchor, 0, -(anchor.height + dp(52)))
            }
        } catch (_: Exception) { popup = null }
    }

    private fun hrow() = LinearLayout(ctx).apply { gravity = Gravity.CENTER }
    private fun spacer(w: Float) = View(ctx).apply { layoutParams = lp(0, 1, w) }

    private fun addSecondaryHint(key: K, value: String) {
        if (value.isEmpty()) return
        key.cell.addView(TextView(ctx).apply {
            text = value
            textSize = 8.5f
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(mutedInk())
            isClickable = false
            isFocusable = false
        }, FrameLayout.LayoutParams(dp(13), dp(13), Gravity.TOP or Gravity.END).apply {
            topMargin = dp(3)
            rightMargin = dp(5)
        })
    }

    private fun attachLetterKey(key: K, value: String) {
        val alts = Alts.map[value]
        val number = if (numberHintsOn) Alts.numberHints[value] else null
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
        val sp = mk(if (incognito) "Private" else "CrazeKeyboard", 4.0f, t.key, t.text, 11f)
        var cursorDrag = false
        var cursorStartX = 0f
        var cursorLastStep = 0
        sp.cell.isClickable = true
        sp.cell.isFocusable = true
        sp.cell.setOnClickListener { type(" ") }
        sp.cell.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    cursorDrag = false
                    cursorStartX = event.rawX
                    cursorLastStep = 0
                    sp.tv.alpha = 0.72f
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

    private fun backKey(w: Float): View {
        val b = mk("", w, t.special, t.text, 18f, R.drawable.ic_backspace)
        b.cell.contentDescription = "Backspace. Swipe left to delete word"
        b.cell.isClickable = true
        b.cell.isFocusable = true
        b.cell.setOnClickListener { del() }
        var startX = 0f
        var swipingWord = false
        var lastStep = 0
        val repR = object : Runnable { override fun run() { if (!swipingWord) { del(); hd.postDelayed(this, 45) } } }
        b.cell.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = e.rawX
                    swipingWord = false
                    lastStep = 0
                    b.tv.alpha = 0.65f
                    if (hapticsOn) b.cell.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    del()
                    hd.postDelayed(repR, 380)
                }
                MotionEvent.ACTION_MOVE -> {
                    if (swipeBackspaceOn) {
                        val dx = startX - e.rawX
                        if (!swipingWord && dx >= dp(26)) {
                            swipingWord = true
                            hd.removeCallbacks(repR)
                        }
                        if (swipingWord) {
                            val step = (dx / dp(28).toFloat()).toInt()
                            if (step > lastStep) {
                                repeat((step - lastStep).coerceAtMost(4)) { deletePreviousWord() }
                                lastStep = step
                            }
                        }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    b.tv.alpha = 1f
                    hd.removeCallbacks(repR)
                    swipingWord = false
                }
            }
            true
        }
        return b.cell
    }

    private fun column() = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(3), dp(2), dp(3), 0) }

    private fun buildLetters(): View {
        val col = column()
        if (numRow) col.addView(charRow("1234567890", 0f, false))
        col.addView(charRow("qwertyuiop"))
        col.addView(charRow("asdfghjkl", 0.5f))
        col.addView(hrow().apply {
            val s = mk("", 1.4f, t.special, t.text, 18f, R.drawable.ic_shift); shiftKey = s
            s.cell.contentDescription = "Shift"
            attach(s) {
                val now = SystemClock.uptimeMillis()
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

    private fun buildNumpad(): View {
        val col = column()
        val rows = listOf(
            listOf("1", "2", "3", "+"),
            listOf("4", "5", "6", "-"),
            listOf("7", "8", "9", "*"),
            listOf(".", "0", "/", "=")
        )
        rows.forEachIndexed { idx, items ->
            val r = hrow()
            items.forEachIndexed { cIdx, label ->
                val isOp = cIdx == 3 || (idx == 3 && cIdx != 1)
                val k = mk(label, if (cIdx == 3) 0.9f else 1.15f, if (isOp) t.special else t.key, t.text, 20f)
                attach(k, fast = true) { type(label) }
                r.addView(k.cell)
            }
            when (idx) {
                0 -> {
                    val back = backKey(1.1f)
                    r.addView(back)
                }
                1 -> {
                    val sp = mk("Space", 1.1f, t.special, t.text, 12f)
                    attach(sp, fast = true) { type(" ") }
                    r.addView(sp.cell)
                }
                2 -> {
                    val abc = mk("ABC", 1.1f, t.special, t.text, 13f)
                    attach(abc) { showPanel("letters") }
                    r.addView(abc.cell)
                }
                3 -> {
                    val e = mk("", 1.1f, t.accent, t.accentText, 18f, enterIcon())
                    attach(e) { enter() }
                    enterKeys.add(e)
                    r.addView(e.cell)
                }
            }
            col.addView(r)
        }
        return col
    }

    // ---------- upgraded customisable tool menu ----------
    private fun tile(icon: Int, label: String, active: Boolean, a: () -> Unit): View {
        val cardBg = if (active) blend(t.accent, t.key, 0.22f) else t.key
        val iconCircleBg = if (active) t.accent else t.special
        val iconTint = if (active) t.accentText else t.text
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = keyBg(cardBg, 14)
            foreground = rippleFg(14)
            setPadding(dp(4), dp(6), dp(4), dp(6))
            isClickable = true
            isFocusable = true
            contentDescription = label
            setOnClickListener { a() }
        }
        box.addView(FrameLayout(ctx).apply {
            background = rounded(iconCircleBg, 11)
            addView(ImageView(ctx).apply {
                setImageResource(icon)
                setColorFilter(iconTint)
                contentDescription = label
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setPadding(dp(6), dp(6), dp(6), dp(6))
            }, FrameLayout.LayoutParams(dp(32), dp(32), Gravity.CENTER))
        }, lp(dp(34), dp(34)))
        box.addView(TextView(ctx).apply {
            text = label
            textSize = 10.5f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(if (active) t.accent else t.text)
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, 0)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        })
        val wrap = FrameLayout(ctx).apply { setPadding(dp(3), dp(3), dp(3), dp(3)) }
        wrap.addView(box, FrameLayout.LayoutParams(MATCH, dp(64)))
        wrap.layoutParams = lp(0, WRAP, 1f)
        return wrap
    }

    private fun buildMenu(): View {
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(6), dp(8), dp(4))
        }
        val intro = LinearLayout(ctx).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(2), dp(4), dp(7))
        }
        intro.addView(ImageView(ctx).apply {
            setImageResource(R.drawable.ic_menu)
            setColorFilter(t.accent)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(5), dp(5), dp(5), dp(5))
        }, lp(dp(38), dp(38)))
        intro.addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(ctx).apply {
                text = "Keyboard Tools"
                textSize = 14f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(t.text)
            })
            addView(TextView(ctx).apply {
                text = "Quick actions · built for offline use"
                textSize = 10.5f
                setTextColor(mutedInk())
            })
        }, lp(0, WRAP, 1f).apply { leftMargin = dp(7) })
        col.addView(intro, lp(MATCH, dp(42)))

        val userTools = Prefs.toolbarTools(ctx).filter { it != "settings" && it != "fulleditor" }
        val available = (userTools + "fulleditor" + "settings").distinct()
        val categories = listOf("All", "Typing", "Editing", "Layout")
        val tabs = LinearLayout(ctx).apply { gravity = Gravity.CENTER_VERTICAL }
        val tabViews = LinkedHashMap<String, TextView>()
        val scroll = ScrollView(ctx).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
        }
        val grid = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(grid, FrameLayout.LayoutParams(MATCH, WRAP))

        fun updateTabs() {
            tabViews.forEach { (name, view) ->
                val active = name == menuCategory
                view.background = rounded(if (active) t.accent else t.special, 18)
                view.setTextColor(if (active) t.accentText else t.text)
                view.alpha = if (active) 1f else 0.8f
                view.isSelected = active
            }
        }
        fun renderTools() {
            grid.removeAllViews()
            val grouped = when (menuCategory) {
                "Typing" -> listOf("keyboard", "emoji", "numpad", "voice", "incognito", "theme")
                "Editing" -> listOf("editor", "fulleditor", "clipboard")
                "Layout" -> listOf("onehand", "floating", "resize", "hide", "settings")
                else -> available
            }.filter { it in available }.distinct()
            grouped.chunked(3).forEach { ids ->
                val row = LinearLayout(ctx).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(3), 0, dp(3)) }
                ids.forEach { id -> row.addView(tile(toolIcon(id), toolLabel(id), isToolActive(id)) { runTool(id) }) }
                repeat(3 - ids.size) { row.addView(View(ctx), lp(0, 1, 1f)) }
                grid.addView(row, lp(MATCH, WRAP))
            }
            scroll.scrollTo(0, 0)
        }
        categories.forEach { label ->
            val tab = TextView(ctx).apply {
                text = label
                textSize = 12f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                gravity = Gravity.CENTER
                setPadding(dp(12), 0, dp(12), 0)
                foreground = rippleFg(18)
                isClickable = true
                isFocusable = true
                contentDescription = "$label tools category"
                setOnClickListener { menuCategory = label; updateTabs(); renderTools() }
            }
            tabViews[label] = tab
            tabs.addView(tab, lp(0, dp(36), 1f).apply { if (label != "All") leftMargin = dp(6) })
        }
        col.addView(tabs, lp(MATCH, dp(40)).apply { bottomMargin = dp(6) })
        col.addView(scroll, lp(MATCH, 0, 1f))
        updateTabs()
        renderTools()
        return col
    }

    // ---------- emoji + clipboard ----------
    private fun backBar(): View = hrow().apply {
        val b = mk("ABC", 1.4f, t.special, t.text, 14f); attach(b) { showPanel("letters") }; addView(b.cell)
        val sp = mk("Space", 5.0f, t.key, t.text, 11f); attach(sp, fast = true) { type(" ") }; addView(sp.cell)
        addView(backKey(1.4f))
    }

    private fun emojiExtraHeight() = dp(if (landscape()) 44 else 112)
    private fun bodyHeight() = panelH() + (if (mode == "emoji") emojiExtraHeight() else 0) -
        (if (mode == "emoji") dp(84) else dp(44)) - cellH - dp(2)

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
                    textSize = 27f
                    gravity = Gravity.CENTER
                    foreground = rippleFg(10)
                    setPadding(0, dp(6), 0, dp(6))
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
                setTextColor(mutedInk())
                setPadding(dp(12), dp(14), dp(12), dp(12))
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

    private fun buildEmoji(): View {
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(5), dp(3), dp(5), 0)
        }

        val search = LinearLayout(ctx).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(t.key, 14)
            foreground = rippleFg(14)
            setPadding(dp(10), 0, dp(12), 0)
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
        search.addView(ImageView(ctx).apply {
            setImageResource(R.drawable.ic_search)
            setColorFilter(mutedInk())
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }, lp(dp(28), dp(28)).apply { rightMargin = dp(6) })
        search.addView(TextView(ctx).apply {
            text = if (emojiSearchQuery.isBlank()) "Search emoji" else emojiSearchQuery
            textSize = 13.5f
            setTextColor(if (emojiSearchQuery.isBlank()) mutedInk() else t.text)
            gravity = Gravity.CENTER_VERTICAL
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }, lp(0, MATCH, 1f))
        col.addView(search, lp(MATCH, dp(38)).apply { bottomMargin = dp(4) })

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
        tabsScroll.addView(tabs, FrameLayout.LayoutParams(WRAP, MATCH))
        col.addView(tabsScroll, lp(MATCH, dp(42)))

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
            val values = when {
                query.isNotEmpty() -> emojiSearchMatches(query)
                aiMatches.isNotEmpty() -> (aiMatches + if (selectedKey == "🕘") Prefs.recents(ctx) else emojiLists[selectedKey].orEmpty()).distinct()
                selectedKey == "🕘" -> Prefs.recents(ctx)
                else -> emojiLists[selectedKey].orEmpty()
            }
            content.addView(TextView(ctx).apply {
                text = when {
                    query.isNotEmpty() -> "Results for \"$query\""
                    aiMatches.isNotEmpty() -> "Suggested · ${emojiCategoryName(selectedKey)}"
                    else -> emojiCategoryName(selectedKey)
                }
                textSize = 11.5f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(mutedInk())
                setPadding(dp(8), dp(6), dp(8), dp(4))
            })
            content.addView(emojiGrid(values))
            body.scrollTo(0, 0)
        }

        fun selectTab(key: String) {
            selectedKey = key
            tabViews.forEach { (name, view) ->
                val active = name == key
                view.alpha = if (active) 1f else 0.56f
                view.background = rounded(if (active) blend(t.accent, t.special, 0.35f) else Color.TRANSPARENT, 12)
            }
            render()
        }

        categoryKeys.forEach { key ->
            val tab = TextView(ctx).apply {
                text = key
                textSize = 18.5f
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
            tabs.addView(tab, lp(WRAP, dp(36)).apply { leftMargin = dp(2); rightMargin = dp(2) })
        }
        if (selectedKey !in categoryKeys) selectedKey = "😀"
        selectTab(selectedKey)

        if (cachedBeforeCursor.isNotBlank() && CrazeLmTflitePredictor.isReady()) {
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
            setPadding(dp(8), dp(6), dp(8), dp(4))
        }
        fun connection(): InputConnection? = currentInputConnection ?: targetInputConnection

        val header = LinearLayout(ctx).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(iconImg(R.drawable.ic_close, padDp = 7) {
            editorSelecting = false
            showPanel("letters")
        }.apply {
            contentDescription = "Close text tools"
            background = rounded(t.special, 10)
        }, lp(dp(36), dp(34)).apply { rightMargin = dp(8) })
        val status = TextView(ctx).apply {
            text = if (editorSelecting) "Selection ON · tap arrows to select" else "Text & Cursor Precision"
            textSize = 13f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(if (editorSelecting) t.accent else t.text)
            gravity = Gravity.CENTER_VERTICAL
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        header.addView(status, lp(0, dp(34), 1f))
        val done = mk("", 0f, t.accent, t.accentText, 14f, R.drawable.ic_done)
        done.cell.layoutParams = lp(dp(44), dp(36))
        done.cell.contentDescription = "Finish editing"
        attach(done) {
            editorSelecting = false
            showPanel("letters")
            syncContextAsync()
        }
        header.addView(done.cell)
        col.addView(header, lp(MATCH, dp(38)).apply { bottomMargin = dp(6) })

        var selectionKey: K? = null
        fun setSelectionMode(enabled: Boolean) {
            editorSelecting = enabled
            selectionKey?.tv?.background = keyBg(if (enabled) t.accent else t.special)
            selectionKey?.iv?.setColorFilter(if (enabled) t.accentText else t.text)
            status.text = if (enabled) "Selection ON · arrows extend selection" else "Text & Cursor Precision"
            status.setTextColor(if (enabled) t.accent else t.text)
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
        fun sendCtrlZ(withShift: Boolean) {
            val ic = connection() ?: return
            val now = SystemClock.uptimeMillis()
            val meta = KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON or (if (withShift) KeyEvent.META_SHIFT_ON else 0)
            try {
                ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_Z, 0, meta))
                ic.sendKeyEvent(KeyEvent(now, now + 1, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_Z, 0, meta))
                status.text = if (withShift) "Redo sent" else "Undo sent"
                syncContextAsync()
            } catch (_: Exception) {
                status.text = "Undo unavailable in this app"
            }
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
            status.text = if (selected) "All text selected" else "Select all unavailable here"
        }
        fun copyOrCut(cut: Boolean) {
            if (incognito) {
                Toast.makeText(ctx, "Clipboard actions are disabled in private mode", Toast.LENGTH_SHORT).show()
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
                status.text = if (cut) "Cut to clipboard" else "Copied to clipboard"
                syncContextAsync()
            } catch (_: Exception) {
                status.text = "Clipboard action unavailable"
            }
        }
        fun pasteText() {
            if (incognito) {
                Toast.makeText(ctx, "Clipboard actions are disabled in private mode", Toast.LENGTH_SHORT).show()
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
                status.text = "Text polish disabled in private mode"
                return
            }
            val ic = connection() ?: return
            try {
                val selected = ic.getSelectedText(0)?.toString().orEmpty()
                if (selected.isNotEmpty()) {
                    val polished = LocalTextPolisher.polish(selected)
                    if (polished == selected) {
                        status.text = "Already clean · no changes needed"
                    } else {
                        ic.commitText(polished, 1)
                        status.text = "Selection polished on-device"
                        syncContextAsync()
                    }
                    return
                }
                val extracted = ic.getExtractedText(android.view.inputmethod.ExtractedTextRequest(), 0)
                val original = extracted?.text?.toString()
                if (original.isNullOrEmpty() || extracted.partialStartOffset >= 0) {
                    status.text = "Select text first to polish"
                    return
                }
                val polished = LocalTextPolisher.polish(original)
                if (polished == original) {
                    status.text = "Already clean · no changes needed"
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
                status.text = "Polish unavailable in this field"
            }
        }

        fun sectionLabel(value: String): TextView = TextView(ctx).apply {
            text = value.uppercase()
            textSize = 9.5f
            letterSpacing = 0.08f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(mutedInk())
            setPadding(dp(4), dp(5), 0, dp(4))
        }

        col.addView(sectionLabel("Cursor & selection"))
        val cursorRow = hrow()
        fun addArrowKey(icon: Int, keyCode: Int, desc: String) {
            val k = mk("", 1.1f, t.key, t.text, 15f, icon)
            k.cell.contentDescription = desc
            attach(k, repeat = true, onLong = { setSelectionMode(true) }) { moveCursor(keyCode) }
            cursorRow.addView(k.cell)
        }
        fun addTextCursorKey(label: String, keyCode: Int, desc: String) {
            val k = mk(label, 1.1f, t.special, t.text, 12f)
            k.cell.contentDescription = desc
            attach(k, onLong = { setSelectionMode(true) }) { moveCursor(keyCode) }
            cursorRow.addView(k.cell)
        }
        addTextCursorKey("Home", KeyEvent.KEYCODE_MOVE_HOME, "Move to line start")
        addArrowKey(R.drawable.ic_arrow_left, KeyEvent.KEYCODE_DPAD_LEFT, "Move cursor left")
        addArrowKey(R.drawable.ic_arrow_up, KeyEvent.KEYCODE_DPAD_UP, "Move cursor up")
        addArrowKey(R.drawable.ic_arrow_down, KeyEvent.KEYCODE_DPAD_DOWN, "Move cursor down")
        addArrowKey(R.drawable.ic_arrow_right, KeyEvent.KEYCODE_DPAD_RIGHT, "Move cursor right")
        addTextCursorKey("End", KeyEvent.KEYCODE_MOVE_END, "Move to line end")
        col.addView(cursorRow, lp(MATCH, dp(40)).apply { bottomMargin = dp(3) })

        col.addView(sectionLabel("Text actions"))
        val actionsRow = hrow()
        val actionsRow2 = hrow()
        fun iconAction(row: LinearLayout, icon: Int, label: String, action: () -> Unit): K {
            val key = mk("", 1f, t.special, t.text, 12f, icon)
            key.cell.contentDescription = label
            attach(key) { action() }
            row.addView(key.cell)
            return key
        }
        selectionKey = iconAction(actionsRow, R.drawable.ic_editor_select, "Toggle selection mode") {
            setSelectionMode(!editorSelecting)
        }
        iconAction(actionsRow, R.drawable.ic_editor_select_all, "Select all") { selectAllText() }
        iconAction(actionsRow, R.drawable.ic_editor_cut, "Cut selected text") { copyOrCut(true) }
        iconAction(actionsRow, R.drawable.ic_editor_copy, "Copy selected text") { copyOrCut(false) }
        iconAction(actionsRow2, R.drawable.ic_editor_paste, "Paste text") { pasteText() }
        iconAction(actionsRow2, R.drawable.ic_editor_undo, "Undo") { sendCtrlZ(false) }
        iconAction(actionsRow2, R.drawable.ic_editor_redo, "Redo") { sendCtrlZ(true) }
        iconAction(actionsRow2, R.drawable.ic_trash, "Delete selection or previous character") {
            val ic = connection() ?: return@iconAction
            try {
                val selected = ic.getSelectedText(0)
                if (!selected.isNullOrEmpty()) ic.commitText("", 1)
                else ic.deleteSurroundingText(1, 0)
                syncContextAsync()
            } catch (_: Exception) { }
        }
        col.addView(actionsRow, lp(MATCH, dp(42)).apply { bottomMargin = dp(2) })
        col.addView(actionsRow2, lp(MATCH, dp(42)).apply { bottomMargin = dp(5) })

        col.addView(sectionLabel("More editing"))
        val footer = hrow()
        val polish = mk("✦ Quick Polish", 1.2f, t.accent, t.accentText, 13f)
        polish.cell.contentDescription = "Apply on-device punctuation, spacing and capitalization polish"
        attach(polish) { quickPolish() }
        footer.addView(polish.cell)
        val clearAll = mk("Clear All", 1f, t.special, t.text, 12f, R.drawable.ic_trash)
        clearAll.cell.contentDescription = "Clear all text in the current field"
        attach(clearAll) {
            val ic = connection()
            val extracted = try { ic?.getExtractedText(android.view.inputmethod.ExtractedTextRequest(), 0) } catch (_: Exception) { null }
            val value = extracted?.text?.toString()
            if (!value.isNullOrEmpty() && extracted.partialStartOffset < 0) {
                ic?.beginBatchEdit()
                try {
                    ic?.setSelection(0, value.length)
                    ic?.commitText("", 1)
                } finally { ic?.endBatchEdit() }
                status.text = "All text cleared"
                syncContextAsync()
            } else status.text = "Clear all unavailable here"
        }
        footer.addView(clearAll.cell)
        val fullEditor = mk("Full Editor ↗", 1f, t.special, t.text, 12f, R.drawable.ic_editor_select_all)
        fullEditor.cell.contentDescription = "Open full-screen text editor companion"
        attach(fullEditor) { editorSelecting = false; launchTextEditor() }
        footer.addView(fullEditor.cell)
        col.addView(footer, lp(MATCH, dp(42)))
        val scroll = ScrollView(ctx).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
        }
        scroll.addView(col, FrameLayout.LayoutParams(MATCH, WRAP))
        return scroll
    }

    private fun buildResize(): View {
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(6))
        }
        var heightValue = Prefs.keyH(ctx)
        val header = LinearLayout(ctx).apply { gravity = Gravity.CENTER_VERTICAL }
        val value = TextView(ctx).apply {
            text = "Keyboard Height · $heightValue dp"
            textSize = 14f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(t.text)
        }
        header.addView(value, lp(0, WRAP, 1f))

        fun applyHeight(nextVal: Int) {
            val clamped = nextVal.coerceIn(34, 64)
            heightValue = clamped
            Prefs.setI(ctx, "keyh", clamped)
            kh = if (floating) (clamped * 0.84f).toInt() else clamped
            cellH = baseCellH()
            value.text = "Keyboard Height · $clamped dp"
            panel.layoutParams = panel.layoutParams.apply { height = panelH() }
            panel.getChildAt(0)?.let { child -> child.layoutParams = child.layoutParams.apply { height = panelH() } }
            panel.requestLayout()
            kbd.requestLayout()
        }

        val minusBtn = TextView(ctx).apply {
            text = "−"
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(t.text)
            background = rounded(t.special, 12)
            foreground = rippleFg(12)
            setOnClickListener { applyHeight(heightValue - 2) }
        }
        val plusBtn = TextView(ctx).apply {
            text = "+"
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(t.text)
            background = rounded(t.special, 12)
            foreground = rippleFg(12)
            setOnClickListener { applyHeight(heightValue + 2) }
        }
        header.addView(minusBtn, lp(dp(42), dp(36)).apply { rightMargin = dp(8) })
        header.addView(plusBtn, lp(dp(42), dp(36)))
        col.addView(header, lp(MATCH, dp(40)).apply { bottomMargin = dp(8) })

        val handle = TextView(ctx).apply {
            text = "↕   Drag anywhere here to resize live   ↕"
            textSize = 13f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
            setTextColor(t.accentText)
            background = rounded(t.accent, 16)
            contentDescription = "Drag vertically to resize keyboard height"
        }
        var lastY = 0f
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
                        kh = if (floating) (next * 0.84f).toInt() else next
                        cellH = baseCellH()
                        value.text = "Keyboard Height · $next dp"
                        panel.layoutParams = panel.layoutParams.apply { height = panelH() }
                        panel.getChildAt(0)?.let { child -> child.layoutParams = child.layoutParams.apply { height = panelH() } }
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
        col.addView(handle, LinearLayout.LayoutParams(MATCH, dp(54)).apply { bottomMargin = dp(8) })

        val presets = hrow()
        listOf("Compact (40dp)" to 40, "Standard (46dp)" to 46, "Tall (54dp)" to 54).forEach { (label, target) ->
            val pKey = mk(label, 1f, t.special, t.text, 12f)
            attach(pKey) { applyHeight(target) }
            presets.addView(pKey.cell)
        }
        col.addView(presets, lp(MATCH, dp(44)).apply { bottomMargin = dp(6) })

        val row = hrow()
        val floatToggle = mk(if (floating) "Dock Keyboard" else "Float Keyboard", 1f, if (floating) t.accent else t.special, if (floating) t.accentText else t.text, 12.5f)
        attach(floatToggle) { Prefs.setB(ctx, "float", !floating); rebuild() }
        row.addView(floatToggle.cell)

        val reset = mk("Reset Default", 1f, t.special, t.text, 12.5f)
        attach(reset) {
            hd.removeCallbacks(persistHeight)
            Prefs.setI(ctx, "kbdwidth", 100)
            Prefs.setI(ctx, "onehand", 0)
            Prefs.setB(ctx, "float", false)
            applyHeight(46)
        }
        row.addView(reset.cell)

        val done = mk("Done ✕", 1f, t.accent, t.accentText, 12.5f)
        attach(done) { showPanel("letters") }
        row.addView(done.cell)
        col.addView(row, lp(MATCH, dp(44)))
        return col
    }

    private fun buildClip(): View {
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), dp(3), dp(6), 0)
        }
        if (incognito) {
            col.addView(TextView(ctx).apply {
                text = "Clipboard paused"
                textSize = 14.5f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(t.text)
                setPadding(dp(8), dp(8), 0, dp(3))
            })
            col.addView(TextView(ctx).apply {
                text = "Saved snippets are hidden while Private Mode is active."
                textSize = 11.5f
                setTextColor(mutedInk())
                setPadding(dp(8), dp(3), dp(8), dp(12))
            })
            col.addView(backBar())
            return col
        }
        val head = LinearLayout(ctx).apply { gravity = Gravity.CENTER_VERTICAL }
        head.addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(ctx).apply {
                text = "Clipboard"
                textSize = 14.5f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(t.text)
            })
            addView(TextView(ctx).apply {
                text = if (incognito) "Private mode active · history paused"
                else "${Prefs.clips(ctx).size} snippets · stored locally"
                textSize = 10.5f
                setTextColor(mutedInk())
            })
        }, lp(0, dp(40), 1f))
        head.addView(iconImg(R.drawable.ic_editor_select_all, padDp = 8) {
            val connection = currentInputConnection ?: targetInputConnection
            ClipboardActivity.onPaste = { value ->
                if (!incognito) {
                    connection?.commitText(value, 1)
                    setLocalContext(cachedBeforeCursor + value)
                    scheduleRefresh()
                }
            }
            startActivity(Intent(ctx, ClipboardActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.apply { contentDescription = "Open full clipboard manager" }, lp(dp(38), dp(38)))
        head.addView(iconImg(R.drawable.ic_trash, padDp = 8) {
            Prefs.clearClips(ctx)
            showPanel("clip")
        }.apply { contentDescription = "Clear clipboard history" }, lp(dp(38), dp(38)))
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
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                letterSpacing = 0.08f
                setTextColor(mutedInk())
                setPadding(dp(7), dp(6), 0, dp(4))
            })
        }
        fun clipRow(value: String, isPinned: Boolean) {
            val row = LinearLayout(ctx).apply {
                gravity = Gravity.CENTER_VERTICAL
                background = keyBg(if (isPinned) blend(t.accent, t.key, 0.14f) else t.key, 13)
                setPadding(dp(10), dp(4), dp(4), dp(4))
            }
            val preview = TextView(ctx).apply {
                text = value
                textSize = 12.5f
                setTextColor(t.text)
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                gravity = Gravity.CENTER_VERTICAL
                foreground = rippleFg(10)
                setPadding(dp(3), dp(5), dp(5), dp(5))
                contentDescription = "Paste clipboard item"
                setOnClickListener {
                    currentInputConnection?.commitText(value, 1)
                    setLocalContext(cachedBeforeCursor + value)
                    showPanel("letters")
                }
            }
            row.addView(preview, lp(0, WRAP, 1f))
            row.addView(iconImg(R.drawable.ic_pin, tint = if (isPinned) t.accent else mutedInk(), padDp = 7) {
                Prefs.togglePinnedClip(ctx, value)
                showPanel("clip")
            }.apply { contentDescription = if (isPinned) "Unpin item" else "Pin item" }, lp(dp(34), dp(36)))
            row.addView(iconImg(R.drawable.ic_close, tint = mutedInk(), padDp = 8) {
                Prefs.removeClip(ctx, value)
                showPanel("clip")
            }.apply { contentDescription = "Remove clipboard item" }, lp(dp(34), dp(36)))
            list.addView(row, lp(MATCH, WRAP).apply { setMargins(dp(2), dp(2), dp(2), dp(4)) })
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
                text = when {
                    !Prefs.b(ctx, "clipboardHistory", false) -> "Clipboard history is off. Enable it in Settings → Privacy & data to save snippets."
                    incognito -> "Private mode is active. Clipboard history is paused."
                    else -> "Saved snippets stay on this device. Tap any item to paste."
                }
                textSize = 12f
                setTextColor(mutedInk())
                setPadding(dp(12), dp(16), dp(12), dp(12))
            })
        }
        body.addView(list)
        col.addView(body, lp(MATCH, bodyHeight()))
        col.addView(backBar())
        return col
    }

    private fun pageFor(m: String): View {
        val cacheable = m != "emoji" && m != "clip" && m != "menu" && m != "resize" && m != "editor"
        if (cacheable) pageCache[m]?.let { return it }
        val v = when (m) {
            "symbols" -> buildSymbols(1)
            "symbols2" -> buildSymbols(2)
            "numpad" -> buildNumpad()
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
            "emoji", "clip", "menu", "resize" -> baseCellH()
            else -> ((panelH() - dp(4)) / rowCountFor(m)).coerceAtLeast(dp(28))
        }
        val page = pageFor(m)
        (page.parent as? ViewGroup)?.removeView(page)
        panel.removeAllViews()
        val targetPanelHeight = if (m == "emoji") panelH() + emojiExtraHeight() else panelH()
        panel.layoutParams = panel.layoutParams.apply { height = targetPanelHeight }
        page.alpha = 0f
        page.translationY = dp(5).toFloat()
        panel.addView(page, FrameLayout.LayoutParams(MATCH, targetPanelHeight))
        page.animate().cancel()
        page.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(140L)
            .setInterpolator(DecelerateInterpolator())
            .start()
        if (m == "letters") updateCase()
        if (m != "editor") editorSelecting = false
        refreshEnter()
        if (m == "menu" || prev == "menu") buildTop()
        if (m == "letters") scheduleRefresh()
    }
}
