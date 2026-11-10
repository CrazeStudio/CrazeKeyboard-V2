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
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView

// A key = full-size touch cell (includes the gap around the key, so taps between keys still land) + the visible face.
internal class K(val cell: FrameLayout, val tv: TextView)

class CrazeKeyboardService : InputMethodService() {
    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    private val ctx: Context get() = this
    private val hd = Handler(Looper.getMainLooper())

    private lateinit var outer: FrameLayout
    private lateinit var top: FrameLayout
    private lateinit var panel: FrameLayout
    private val sugViews = ArrayList<TextView>()
    private val letterKeys = ArrayList<Pair<TextView, String>>()
    private val seen = HashMap<String, Int>()
    private var shiftKey: TextView? = null
    private var t = Prefs.themes[0]
    private var shift = 0
    private var autoShift = false
    private var mode = "letters"
    private var toolbarOpen = false
    private var suggestOn = true
    private var lastShiftTap = 0L
    private var popup: PopupWindow? = null
    private var preview: PopupWindow? = null
    private var prevTv: TextView? = null
    private var lastSig = ""
    private var kh = 46
    private var cellH = 0
    private var hapticsOn = true
    private var numRow = true
    private var clipListener: ClipboardManager.OnPrimaryClipChangedListener? = null

    private val emojiCats = linkedMapOf(
        "🕘" to "",
        "😀" to "😀 😃 😄 😁 😆 😅 😂 🤣 😊 😇 🙂 🙃 😉 😌 😍 🥰 😘 😗 😙 😚 😋 😛 😝 😜 🤪 🤨 🧐 🤓 😎 🤩 🥳 😏 😒 😞 😔 😟 😕 🙁 😣 😖 😫 😩 🥺 😢 😭 😤 😠 😡 🤬 🤯 😳 🥵 🥶 😱 😨 😰 😥 😓 🤗 🤔 🤭 🤫 🤥 😶 😐 😑 😬 🙄 😯 😦 😧 😮 😲 🥱 😴 🤤 😪 😵 🤐 🥴 🤢 🤮 🤧 😷",
        "👍" to "👍 👎 👌 🤞 🤟 🤘 🤙 👈 👉 👆 👇 ✋ 🤚 🖐 🖖 👋 🤝 🙏 💪 👏 🙌 👐 🤲 🤳 💅 👀 👅 👄 🧠 👶 👦 👧 👨 👩 🧑 👴 👵",
        "🐶" to "🐶 🐱 🐭 🐹 🐰 🦊 🐻 🐼 🐨 🐯 🦁 🐮 🐷 🐸 🐵 🐔 🐧 🐦 🐤 🦆 🦅 🦉 🦇 🐺 🐗 🐴 🦄 🐝 🐛 🦋 🐌 🐞 🐜 🐢 🐍 🦎 🐙 🦑 🦐 🦀 🐡 🐠 🐟 🐬 🐳 🐋 🦈 🐊 🐘 🦒 🌵 🌲 🌳 🌴 🌱 🌿 🍀 🍁 🍂 🌺 🌻 🌹 🌷 🌼 🌸",
        "🍔" to "🍏 🍎 🍐 🍊 🍋 🍌 🍉 🍇 🍓 🍒 🍑 🥭 🍍 🥥 🥝 🍅 🥑 🥦 🥕 🌽 🥔 🍞 🧀 🍗 🍖 🍔 🍟 🍕 🌭 🥪 🌮 🌯 🍝 🍜 🍣 🍤 🍦 🍩 🍪 🎂 🍰 🍫 🍬 🍭 ☕ 🍵 🍺 🍷 🥤",
        "⚽" to "⚽ 🏀 🏈 ⚾ 🎾 🏐 🏉 🎱 🏓 🏸 🥊 🎯 🎮 🎲 🎧 🎤 🎸 🎹 🎺 🎻 🥁 🎬 🎨 🏆 🥇 🥈 🥉 🚗 🚕 🚌 🚓 🚑 🚒 🚲 🚀 🚁 🚢 🏠 🌍 🌙 ⭐ 🌈 🔥 💧 ⚡",
        "💡" to "📱 💻 📷 📺 ⌚ 🔋 💡 🔦 📚 📖 📝 📎 🔑 🔒 🔔 💰 💳 🎁 🎈 🎉 ❤ 🧡 💛 💚 💙 💜 🖤 💔 💕 💞 💯 ✅ ❌ ⭕ ❗ ❓ ⚠ 💤 💬 💭"
    )

    // ---------- sizing helpers ----------
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun lp(w: Int, h: Int, wt: Float = 0f) = LinearLayout.LayoutParams(w, h, wt)
    private fun landscape() = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    private fun loadPrefs() {
        kh = if (landscape()) minOf(Prefs.keyH(this), 38) else Prefs.keyH(this)
        cellH = dp(kh) + dp(6)
        hapticsOn = Prefs.b(this, "haptics", true)
        numRow = Prefs.b(this, "numrow", true)
    }
    private fun rowCount() = if (numRow) 5 else 4
    private fun panelH() = rowCount() * cellH + dp(4)   // every page uses this, so switching pages never makes the keyboard jump
    private fun topH() = dp(42)

    // ---------- drawing helpers ----------
    private fun darken(c: Int, f: Float = 0.78f): Int {
        val r = ((c shr 16 and 0xFF) * f).toInt()
        val g = ((c shr 8 and 0xFF) * f).toInt()
        val b = ((c and 0xFF) * f).toInt()
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
    private fun rounded(c: Int, r: Int = 9) = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; cornerRadius = dp(r).toFloat(); setColor(c) }
    // Key face with a thin darker lower edge for depth.
    private fun keyBg(c: Int, r: Int = 9): Drawable =
        LayerDrawable(arrayOf<Drawable>(rounded(darken(c), r), rounded(c, r))).apply { setLayerInset(1, 0, 0, 0, dp(2)) }

    override fun onCreate() {
        super.onCreate()
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipListener = ClipboardManager.OnPrimaryClipChangedListener {
            try { cm.primaryClip?.getItemAt(0)?.text?.toString()?.let { Prefs.addClip(ctx, it) } } catch (e: Exception) { }
        }
        cm.addPrimaryClipChangedListener(clipListener)
    }

    override fun onDestroy() {
        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).removePrimaryClipChangedListener(clipListener)
        super.onDestroy()
    }

    // Fullscreen extract mode in landscape is slow and covers the app, so turn it off.
    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onCreateInputView(): View {
        t = Prefs.theme(this)
        lastSig = Prefs.sig(this)
        loadPrefs()
        dismissPopups()
        outer = FrameLayout(this)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(t.bg); setPadding(0, 0, 0, dp(6)) }
        top = FrameLayout(this)
        panel = FrameLayout(this)
        root.addView(top, lp(MATCH, topH()))
        root.addView(panel, lp(MATCH, panelH()))
        val oh = Prefs.i(this, "onehand", 0)
        val w = if (oh == 0) MATCH else (resources.displayMetrics.widthPixels * 0.82f).toInt()
        outer.addView(root, FrameLayout.LayoutParams(w, WRAP, if (oh == 2) Gravity.END else Gravity.START))
        buildTop()
        showPanel(mode)
        return outer
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (Prefs.sig(this) != lastSig) setInputView(onCreateInputView())
        else loadPrefs()
        val cls = info.inputType and InputType.TYPE_MASK_CLASS
        val v = info.inputType and InputType.TYPE_MASK_VARIATION
        val noSug = listOf(InputType.TYPE_TEXT_VARIATION_PASSWORD, InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD, InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS, InputType.TYPE_TEXT_VARIATION_URI)
        suggestOn = Prefs.b(this, "suggest", true) && cls == InputType.TYPE_CLASS_TEXT && v !in noSug &&
            (info.inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) == 0
        shift = 0
        autoShift = false
        toolbarOpen = false
        buildTop()
        mode = if (cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE) "symbols" else "letters"
        showPanel(mode)
        updateSuggestions()
    }

    // Cursor moved / text changed outside the keyboard. Debounced so rapid edits cost one refresh.
    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        if (::top.isInitialized) scheduleRefresh()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        hd.removeCallbacks(refreshR)
        dismissPopups()
        super.onFinishInputView(finishingInput)
    }

    // ---------- debounced refresh (suggestions + auto-capitalize) ----------
    private val refreshR = Runnable { autoCap(); updateSuggestions() }
    private fun scheduleRefresh() { hd.removeCallbacks(refreshR); hd.postDelayed(refreshR, 40) }

    // ---------- top bar (suggestions / toolbar) ----------
    private fun iconBtn(label: String, size: Float = 18f, a: () -> Unit) = TextView(ctx).apply {
        text = label; textSize = size; gravity = Gravity.CENTER; setTextColor(t.text)
        setOnClickListener { a() }
    }

    private fun buildTop() {
        top.removeAllViews()
        sugViews.clear()
        val row = LinearLayout(ctx).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(4), dp(2), dp(4), dp(2)) }
        row.addView(iconBtn(if (toolbarOpen) "✕" else "⋮⋮", 16f) { toolbarOpen = !toolbarOpen; buildTop() }, lp(dp(40), MATCH))
        if (toolbarOpen) {
            val items = listOf<Pair<String, () -> Unit>>(
                "😀" to { showPanel("emoji") },
                "📋" to { showPanel("clip") },
                "🎤" to { startVoice() },
                "🎨" to { Prefs.nextTheme(ctx); setInputView(onCreateInputView()) },
                "◧" to { Prefs.nextOneHand(ctx); setInputView(onCreateInputView()) },
                "⚙" to { startActivity(Intent(ctx, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            )
            items.forEach { (l, a) -> row.addView(iconBtn(l, 18f, a), lp(0, MATCH, 1f)) }
        } else {
            for (i in 0..2) {
                val tv = TextView(ctx).apply {
                    gravity = Gravity.CENTER; textSize = 15f; setTextColor(t.text); maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END; setPadding(dp(6), 0, dp(6), 0)
                    if (i == 1) typeface = Typeface.DEFAULT_BOLD
                    setOnClickListener { pickSuggestion(text.toString()) }
                }
                sugViews.add(tv)
                row.addView(tv, lp(0, MATCH, 1f))
                if (i < 2) row.addView(View(ctx).apply { setBackgroundColor(t.special) },
                    lp(dp(1), dp(18)).apply { gravity = Gravity.CENTER_VERTICAL })
            }
        }
        top.addView(row, FrameLayout.LayoutParams(MATCH, MATCH))
        updateSuggestions()
    }

    private fun currentWord(): String {
        val before = currentInputConnection?.getTextBeforeCursor(30, 0)?.toString() ?: ""
        return before.takeLastWhile { it.isLetter() || it == '\'' }
    }

    private fun updateSuggestions() {
        if (toolbarOpen || sugViews.isEmpty()) return
        val list = if (suggestOn) Words.suggest(this, currentWord()) else emptyList()
        for (i in 0..2) sugViews[i].text = list.getOrNull(i) ?: ""
    }

    private fun pickSuggestion(s: String) {
        val ic = currentInputConnection ?: return
        if (s.isEmpty()) return
        val w = currentWord()
        ic.beginBatchEdit()
        ic.deleteSurroundingText(w.length, 0)
        ic.commitText("$s ", 1)
        ic.endBatchEdit()
        scheduleRefresh()
    }

    // Only learn a new word once it has been typed twice, so one-off typos don't pollute suggestions.
    private fun noteWord(w: String) {
        if (w.length < 3 || Words.known(w)) return
        val k = w.lowercase()
        val n = (seen[k] ?: 0) + 1
        seen[k] = n
        if (n >= 2) Words.learn(this, w)
    }

    // ---------- typing actions ----------
    private fun autoCap() {
        if (shift == 2 || mode != "letters") return
        val ei = currentInputEditorInfo ?: return
        val ic = currentInputConnection ?: return
        val want = ei.inputType != 0 && ic.getCursorCapsMode(ei.inputType) != 0
        if (want && shift == 0) { shift = 1; autoShift = true; updateCase() }
        else if (!want && shift == 1 && autoShift) { shift = 0; autoShift = false; updateCase() }
    }

    private fun updateCase() {
        letterKeys.forEach { (tv, c) -> tv.text = if (shift > 0) c.uppercase() else c }
        shiftKey?.apply {
            text = when (shift) { 2 -> "⇪"; 1 -> "⬆"; else -> "⇧" }
            background = keyBg(if (shift > 0) t.accent else t.special)
            setTextColor(if (shift > 0) t.accentText else t.text)
        }
    }

    private fun type(s: String) {
        dismissAlts()
        val ic = currentInputConnection ?: return
        if (s == " ") { space(ic); return }
        ic.commitText(if (shift > 0 && mode == "letters") s.uppercase() else s, 1)
        if (shift == 1) { shift = 0; autoShift = false; updateCase() }
        scheduleRefresh()
    }

    private fun space(ic: InputConnection) {
        val before = ic.getTextBeforeCursor(30, 0)?.toString() ?: ""
        noteWord(before.takeLastWhile { it.isLetter() || it == '\'' })
        // Second space right after a word turns into ". "
        if (before.length >= 2 && before[before.length - 1] == ' ' && before[before.length - 2].isLetterOrDigit()) {
            ic.deleteSurroundingText(1, 0); ic.commitText(". ", 1)
        } else ic.commitText(" ", 1)
        scheduleRefresh()
    }

    // Key event instead of reading the selection first: no blocking round-trip, handles selections and emoji itself.
    private fun del() {
        sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        scheduleRefresh()
    }

    private fun actionId(): Int = (currentInputEditorInfo?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION

    private fun enter() {
        val ei = currentInputEditorInfo
        val a = actionId()
        if (ei == null || (ei.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0 ||
            a == EditorInfo.IME_ACTION_NONE || a == EditorInfo.IME_ACTION_UNSPECIFIED) sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        else currentInputConnection?.performEditorAction(a)
        scheduleRefresh()
    }

    private fun enterLabel() = when (actionId()) {
        EditorInfo.IME_ACTION_SEARCH -> "🔍"; EditorInfo.IME_ACTION_SEND -> "➤"; EditorInfo.IME_ACTION_GO -> "→"
        EditorInfo.IME_ACTION_NEXT -> "⇥"; EditorInfo.IME_ACTION_DONE -> "✓"; else -> "↵"
    }

    private fun startVoice() {
        VoiceActivity.callback = { txt -> currentInputConnection?.commitText("$txt ", 1); scheduleRefresh() }
        startActivity(Intent(this, VoiceActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun switchKeyboard() {
        if (!switchToNextInputMethod(false)) (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
    }

    // ---------- key building ----------
    private fun mk(label: String, w: Float, bg: Int, fg: Int, size: Float = 20f): K {
        val tv = TextView(ctx).apply {
            text = label; gravity = Gravity.CENTER; setTextColor(fg); textSize = size
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            includeFontPadding = false
            background = keyBg(bg)
        }
        val cell = FrameLayout(ctx).apply {
            setPadding(dp(2), dp(3), dp(2), dp(3))
            layoutParams = lp(0, cellH, w)
            addView(tv, FrameLayout.LayoutParams(MATCH, MATCH))
        }
        return K(cell, tv)
    }

    private fun dismissAlts() { popup?.dismiss(); popup = null }
    private fun dismissPopups() { dismissAlts(); preview?.dismiss(); preview = null; prevTv = null }

    // One preview window is created once and then only moved/re-texted: creating a window per keypress is what made typing laggy.
    private fun showPreview(k: K) {
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
    }

    private fun hidePreview() { prevTv?.visibility = View.INVISIBLE }

    // fast = type on finger-down (plain keys). Keys with a long-press type on release, unless the long-press fired.
    private fun attach(k: K, repeat: Boolean = false, showPrev: Boolean = false, fast: Boolean = false, onLong: (() -> Unit)? = null, tap: () -> Unit) {
        var fired = false
        val longR = Runnable { fired = true; hidePreview(); onLong?.invoke() }
        val repR = object : Runnable { override fun run() { tap(); hd.postDelayed(this, 45) } }
        k.cell.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    fired = false; k.tv.alpha = 0.65f
                    if (hapticsOn) k.cell.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    if (showPrev) showPreview(k)
                    if (repeat) { tap(); hd.postDelayed(repR, 380) }
                    else if (fast) tap()
                    else if (onLong != null) hd.postDelayed(longR, 350)
                }
                MotionEvent.ACTION_UP -> {
                    k.tv.alpha = 1f; hd.removeCallbacks(longR); hd.removeCallbacks(repR); hidePreview()
                    if (!repeat && !fast && !fired) tap()
                }
                MotionEvent.ACTION_CANCEL -> {
                    k.tv.alpha = 1f; hd.removeCallbacks(longR); hd.removeCallbacks(repR); hidePreview()
                }
            }
            true
        }
    }

    private fun showAlts(anchor: View, alts: String) {
        dismissAlts()
        val row = LinearLayout(ctx).apply { background = keyBg(t.special, 12); setPadding(dp(4), dp(4), dp(4), dp(6)) }
        alts.forEach { ch ->
            val s = if (shift > 0 && mode == "letters") ch.toString().uppercase() else ch.toString()
            row.addView(TextView(ctx).apply {
                text = s; textSize = 22f; gravity = Gravity.CENTER; setTextColor(t.text)
                setPadding(dp(10), dp(8), dp(10), dp(8)); setOnClickListener { type(ch.toString()) }
            })
        }
        popup = PopupWindow(row, WRAP, WRAP).apply {
            setBackgroundDrawable(ColorDrawable(0)); isOutsideTouchable = true
            showAsDropDown(anchor, 0, -(anchor.height + dp(52)))
        }
    }

    private fun hrow() = LinearLayout(ctx).apply { gravity = Gravity.CENTER }
    private fun spacer(w: Float) = View(ctx).apply { layoutParams = lp(0, 1, w) }

    private fun charRow(chars: String, pad: Float = 0f, letters: Boolean = true): LinearLayout = hrow().apply {
        if (pad > 0) addView(spacer(pad))
        chars.forEach { ch ->
            val s = ch.toString()
            val isLetter = letters && ch.isLetter()
            val k = mk(s, 1f, t.key, t.text, if (isLetter) 22f else 18f)
            if (isLetter) letterKeys.add(k.tv to s)
            val alts = Alts.map[s]
            attach(k, showPrev = isLetter, fast = alts == null, onLong = alts?.let { a -> { showAlts(k.cell, a) } }) { type(s) }
            addView(k.cell)
        }
        if (pad > 0) addView(spacer(pad))
    }

    private fun bottomRow(left: String, leftTarget: String): LinearLayout = hrow().apply {
        val l = mk(left, 1.4f, t.special, t.text, 14f); attach(l) { showPanel(leftTarget) }; addView(l.cell)
        val c = mk(",", 0.9f, t.special, t.text); attach(c, onLong = { showPanel("emoji") }) { type(",") }; addView(c.cell)
        val g = mk("🌐", 0.9f, t.special, t.text, 17f); attach(g) { switchKeyboard() }; addView(g.cell)
        val sp = mk("CrazeKeyboard", 4f, t.key, t.text, 11f); sp.tv.alpha = 1f
        attach(sp, onLong = { (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker() }) { type(" ") }; addView(sp.cell)
        val d = mk(".", 0.9f, t.special, t.text); attach(d, onLong = { showAlts(d.cell, Alts.map["."]!!) }) { type(".") }; addView(d.cell)
        val e = mk(enterLabel(), 1.4f, t.accent, t.accentText, 18f); attach(e) { enter() }; addView(e.cell)
    }

    private fun backKey(w: Float): View { val b = mk("⌫", w, t.special, t.text, 18f); attach(b, repeat = true) { del() }; return b.cell }

    private fun column() = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(3), dp(2), dp(3), 0) }

    private fun buildLetters(): View {
        val col = column()
        if (numRow) col.addView(charRow("1234567890", 0f, false))
        col.addView(charRow("qwertyuiop"))
        col.addView(charRow("asdfghjkl", 0.5f))
        col.addView(hrow().apply {
            val s = mk("⇧", 1.4f, t.special, t.text, 18f); shiftKey = s.tv
            attach(s) {
                val now = SystemClock.uptimeMillis()
                // Single tap toggles shift; a quick second tap turns on caps lock.
                shift = if (shift == 2) 0 else if (shift == 1 && now - lastShiftTap < 350) 2 else if (shift == 1) 0 else 1
                autoShift = false; lastShiftTap = now; updateCase()
            }
            addView(s.cell)
            "zxcvbnm".forEach { ch ->
                val k = mk(ch.toString(), 1f, t.key, t.text, 22f); letterKeys.add(k.tv to ch.toString())
                val alts = Alts.map[ch.toString()]
                attach(k, showPrev = true, fast = alts == null, onLong = alts?.let { a -> { showAlts(k.cell, a) } }) { type(ch.toString()) }
                addView(k.cell)
            }
            addView(backKey(1.4f))
        })
        col.addView(bottomRow("?123", "symbols"))
        updateCase()
        return col
    }

    private fun symKey(row: LinearLayout, ch: Char) {
        val k = mk(ch.toString(), 1f, t.key, t.text, 18f)
        attach(k, fast = true) { type(ch.toString()) }
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
            col.addView(charRow("~`|•√π÷×¶∆", 0f, false))
            col.addView(charRow("£¢€¥^°={}\\", 0f, false))
            col.addView(hrow().apply {
                val p = mk("?123", 1.4f, t.special, t.text, 14f); attach(p) { showPanel("symbols") }; addView(p.cell)
                "©®™[]<>".forEach { symKey(this, it) }
                addView(backKey(1.4f))
            })
        }
        col.addView(bottomRow("ABC", "letters"))
        return col
    }

    // ---------- emoji + clipboard ----------
    private fun backBar(): View = hrow().apply {
        val b = mk("ABC", 1.4f, t.special, t.text, 14f); attach(b) { showPanel("letters") }; addView(b.cell)
        addView(spacer(5f))
        addView(backKey(1.4f))
    }

    private fun bodyHeight() = panelH() - dp(40) - cellH - dp(2)

    private fun buildEmoji(): View {
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(3), dp(2), dp(3), 0) }
        val tabs = LinearLayout(ctx).apply { gravity = Gravity.CENTER_VERTICAL }
        val body = ScrollView(ctx)
        fun fill(list: List<String>) {
            val g = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
            if (list.isEmpty()) g.addView(TextView(ctx).apply { text = "No recent emoji yet"; setTextColor(t.text); setPadding(dp(12), dp(12), dp(12), dp(12)) })
            list.chunked(8).forEach { r ->
                val row = LinearLayout(ctx)
                r.forEach { e ->
                    row.addView(TextView(ctx).apply {
                        text = e; textSize = 24f; gravity = Gravity.CENTER; setPadding(0, dp(6), 0, dp(6))
                        setOnClickListener { currentInputConnection?.commitText(e, 1); Prefs.addRecent(ctx, e) }
                    }, lp(0, WRAP, 1f))
                }
                repeat(8 - r.size) { row.addView(View(ctx), lp(0, 1, 1f)) }
                g.addView(row)
            }
            body.removeAllViews(); body.addView(g)
        }
        val startIcon = if (Prefs.recents(this).isEmpty()) "😀" else "🕘"
        var selected: TextView? = null
        emojiCats.forEach { (icon, data) ->
            val tb = iconBtn(icon, 18f) { }
            tb.alpha = 0.45f
            tb.setOnClickListener {
                selected?.alpha = 0.45f; tb.alpha = 1f; selected = tb
                fill(if (icon == "🕘") Prefs.recents(ctx) else data.split(" "))
            }
            if (icon == startIcon) { tb.alpha = 1f; selected = tb }
            tabs.addView(tb, lp(0, dp(40), 1f))
        }
        col.addView(tabs, lp(MATCH, dp(40)))
        col.addView(body, lp(MATCH, bodyHeight()))
        col.addView(backBar())
        fill(if (startIcon == "🕘") Prefs.recents(this) else emojiCats["😀"]!!.split(" "))
        return col
    }

    private fun buildClip(): View {
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(3), dp(2), dp(3), 0) }
        val head = LinearLayout(ctx).apply { gravity = Gravity.CENTER_VERTICAL }
        head.addView(TextView(ctx).apply { text = "  Clipboard"; textSize = 15f; setTextColor(t.text); gravity = Gravity.CENTER_VERTICAL }, lp(0, dp(40), 1f))
        head.addView(iconBtn("🗑", 16f) { Prefs.clearClips(ctx); showPanel("clip") }, lp(dp(48), dp(40)))
        col.addView(head, lp(MATCH, dp(40)))
        val body = ScrollView(ctx)
        val list = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val clips = Prefs.clips(this)
        if (clips.isEmpty()) list.addView(TextView(ctx).apply { text = "Copied text will show up here"; setTextColor(t.text); setPadding(dp(12), dp(12), dp(12), dp(12)) })
        clips.forEach { c ->
            list.addView(TextView(ctx).apply {
                text = c; maxLines = 2; ellipsize = TextUtils.TruncateAt.END; setTextColor(t.text); background = keyBg(t.key)
                setPadding(dp(12), dp(8), dp(12), dp(10)); setOnClickListener { currentInputConnection?.commitText(c, 1); showPanel("letters") }
            }, lp(MATCH, WRAP).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) })
        }
        body.addView(list)
        col.addView(body, lp(MATCH, bodyHeight()))
        col.addView(backBar())
        return col
    }

    private fun showPanel(m: String) {
        dismissPopups()
        mode = m
        panel.removeAllViews()
        letterKeys.clear()   // stale keys from other pages must not be touched by updateCase()
        shiftKey = null
        val page: View = when (m) {
            "symbols" -> buildSymbols(1)
            "symbols2" -> buildSymbols(2)
            "emoji" -> buildEmoji()
            "clip" -> buildClip()
            else -> buildLetters()
        }
        panel.addView(page, FrameLayout.LayoutParams(MATCH, panelH()))
        if (m == "letters") scheduleRefresh()
    }
}
