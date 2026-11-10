package com.crazestudio.crazekeyboard

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
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
    private var shiftKey: TextView? = null
    private var t = Prefs.themes[0]
    private var shift = 0
    private var mode = "letters"
    private var toolbarOpen = false
    private var suggestOn = true
    private var lastShiftTap = 0L
    private var popup: PopupWindow? = null
    private var preview: PopupWindow? = null
    private var lastSig = ""
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

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun lp(w: Int, h: Int, wt: Float = 0f) = LinearLayout.LayoutParams(w, h, wt)
    private fun rounded(c: Int, r: Int = 8) = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; cornerRadius = dp(r).toFloat(); setColor(c) }
    private fun landscape() = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    // Key height adapts: smaller in landscape so the keyboard doesn't eat the screen.
    private fun keyHeight(): Int = if (landscape()) minOf(Prefs.keyH(this), 38) else Prefs.keyH(this)
    private fun rowCount() = if (Prefs.b(this, "numrow", true)) 5 else 4
    // Every panel gets exactly this height so switching pages never makes the keyboard jump.
    private fun panelH() = rowCount() * (dp(keyHeight()) + dp(6)) + dp(4)
    private fun topH() = dp(40)

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

    override fun onCreateInputView(): View {
        t = Prefs.theme(this)
        lastSig = Prefs.sig(this)
        outer = FrameLayout(this)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(t.bg); setPadding(0, 0, 0, dp(4)) }
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
        val cls = info.inputType and InputType.TYPE_MASK_CLASS
        val v = info.inputType and InputType.TYPE_MASK_VARIATION
        val noSug = listOf(InputType.TYPE_TEXT_VARIATION_PASSWORD, InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD, InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS, InputType.TYPE_TEXT_VARIATION_URI)
        suggestOn = Prefs.b(this, "suggest", true) && cls == InputType.TYPE_CLASS_TEXT && v !in noSug &&
            (info.inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) == 0
        shift = 0
        toolbarOpen = false
        buildTop()
        mode = if (cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE) "symbols" else "letters"
        showPanel(mode)
        updateSuggestions()
    }

    // Keeps suggestions and auto-capitals correct when the cursor moves or text changes outside the keyboard.
    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        if (::top.isInitialized) { autoCap(); updateSuggestions() }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        dismissPopups()
        super.onFinishInputView(finishingInput)
    }

    // ---------- top bar (suggestions / toolbar) ----------
    private fun iconBtn(label: String, size: Float = 18f, a: () -> Unit) = TextView(ctx).apply {
        text = label; textSize = size; gravity = Gravity.CENTER; setTextColor(t.text)
        setOnClickListener { a() }
    }

    private fun buildTop() {
        top.removeAllViews()
        sugViews.clear()
        val row = LinearLayout(ctx).apply { gravity = Gravity.CENTER_VERTICAL }
        row.addView(iconBtn(if (toolbarOpen) "✕" else "⋮⋮", 16f) { toolbarOpen = !toolbarOpen; buildTop(); updateSuggestions() }, lp(dp(44), MATCH))
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
                    ellipsize = TextUtils.TruncateAt.END; setPadding(dp(4), 0, dp(4), 0)
                    setOnClickListener { pickSuggestion(text.toString()) }
                }
                sugViews.add(tv)
                row.addView(tv, lp(0, MATCH, 1f))
                if (i < 2) row.addView(View(ctx).apply { setBackgroundColor(t.special) },
                    lp(dp(1), dp(20)).apply { gravity = Gravity.CENTER_VERTICAL })
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
        if (s.length >= 3 && !Words.known(s)) Prefs.learn(this, s)
        afterEdit()
    }

    // ---------- typing actions ----------
    private fun afterEdit() { autoCap(); updateSuggestions() }

    private fun autoCap() {
        if (shift == 2 || mode != "letters") return
        val ei = currentInputEditorInfo ?: return
        val ic = currentInputConnection ?: return
        val ns = if (ei.inputType != 0 && ic.getCursorCapsMode(ei.inputType) != 0) 1 else 0
        if (ns != shift) { shift = ns; updateCase() }
    }

    private fun updateCase() {
        letterKeys.forEach { (tv, c) -> tv.text = if (shift > 0) c.uppercase() else c }
        shiftKey?.apply {
            text = when (shift) { 2 -> "⇪"; 1 -> "⬆"; else -> "⇧" }
            background = rounded(if (shift > 0) t.accent else t.special)
            setTextColor(if (shift > 0) t.accentText else t.text)
        }
    }

    private fun type(s: String) {
        dismissPopups()
        val ic = currentInputConnection ?: return
        if (s == " ") { space(ic); return }
        val out = if (shift > 0 && mode == "letters") s.uppercase() else s
        ic.commitText(out, 1)
        if (shift == 1) { shift = 0; updateCase() }
        afterEdit()
    }

    private fun space(ic: InputConnection) {
        val w = currentWord()
        if (w.length >= 3 && !Words.known(w)) Prefs.learn(this, w)
        val before = ic.getTextBeforeCursor(2, 0)?.toString() ?: ""
        // Double-tap space after a word inserts ". " (only when the word is not already punctuated).
        if (before.length == 2 && before[1] == ' ' && before[0].isLetterOrDigit()) {
            ic.deleteSurroundingText(1, 0); ic.commitText(". ", 1)
        } else ic.commitText(" ", 1)
        afterEdit()
    }

    private fun del() {
        val ic = currentInputConnection ?: return
        if (!ic.getSelectedText(0).isNullOrEmpty()) ic.commitText("", 1)
        else if (!ic.deleteSurroundingTextInCodePoints(1, 0)) sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        afterEdit()
    }

    private fun actionId(): Int = (currentInputEditorInfo?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION

    private fun enter() {
        val ei = currentInputEditorInfo
        val a = actionId()
        if (ei == null || (ei.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0 ||
            a == EditorInfo.IME_ACTION_NONE || a == EditorInfo.IME_ACTION_UNSPECIFIED) sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        else currentInputConnection?.performEditorAction(a)
        afterEdit()
    }

    private fun enterLabel() = when (actionId()) {
        EditorInfo.IME_ACTION_SEARCH -> "🔍"; EditorInfo.IME_ACTION_SEND -> "➤"; EditorInfo.IME_ACTION_GO -> "→"
        EditorInfo.IME_ACTION_NEXT -> "⇥"; EditorInfo.IME_ACTION_DONE -> "✓"; else -> "↵"
    }

    private fun startVoice() {
        VoiceActivity.callback = { txt -> currentInputConnection?.commitText("$txt ", 1); afterEdit() }
        startActivity(Intent(this, VoiceActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun switchKeyboard() {
        if (!switchToNextInputMethod(false)) (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
    }

    // ---------- key building ----------
    private fun mk(label: String, w: Float, bg: Int, fg: Int, size: Float = 20f) = TextView(ctx).apply {
        text = label; gravity = Gravity.CENTER; setTextColor(fg); textSize = size; background = rounded(bg)
        layoutParams = lp(0, dp(keyHeight()), w).apply { setMargins(dp(2), dp(3), dp(2), dp(3)) }
    }

    private fun dismissPopups() { popup?.dismiss(); popup = null; preview?.dismiss(); preview = null }

    private fun showPreview(v: TextView) {
        preview?.dismiss()
        val tv = TextView(ctx).apply {
            text = v.text; textSize = 26f; gravity = Gravity.CENTER; setTextColor(t.text); background = rounded(t.key, 10)
        }
        preview = PopupWindow(tv, dp(50), dp(54)).apply {
            isClippingEnabled = false
            showAsDropDown(v, (v.width - dp(50)) / 2, -(v.height + dp(58)))
        }
    }

    private fun attach(v: TextView, repeat: Boolean = false, showPrev: Boolean = false, onLong: (() -> Unit)? = null, tap: () -> Unit) {
        var fired = false
        val longR = Runnable { fired = true; preview?.dismiss(); onLong?.invoke() }
        val repR = object : Runnable { override fun run() { tap(); hd.postDelayed(this, 50) } }
        v.setOnTouchListener { view, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    fired = false; view.alpha = 0.6f
                    if (Prefs.b(ctx, "haptics", true)) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    if (showPrev) showPreview(v)
                    if (repeat) { tap(); hd.postDelayed(repR, 400) } else if (onLong != null) hd.postDelayed(longR, 350)
                }
                MotionEvent.ACTION_UP -> {
                    view.alpha = 1f; hd.removeCallbacks(longR); hd.removeCallbacks(repR); preview?.dismiss()
                    // Tap fires on release, but only if long-press did not already fire.
                    if (!repeat && !fired) tap()
                }
                MotionEvent.ACTION_CANCEL -> { view.alpha = 1f; hd.removeCallbacks(longR); hd.removeCallbacks(repR); preview?.dismiss() }
            }
            true
        }
    }

    private fun showAlts(anchor: View, alts: String) {
        popup?.dismiss()
        val row = LinearLayout(ctx).apply { background = rounded(t.special, 10); setPadding(dp(4), dp(4), dp(4), dp(4)) }
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
            val k = mk(s, 1f, t.key, t.text, if (s[0].isDigit() && letters.not()) 18f else 20f)
            if (letters && s[0].isLetter()) letterKeys.add(k to s)
            attach(k, showPrev = letters && s[0].isLetter(), onLong = Alts.map[s]?.let { a -> { showAlts(k, a) } }) { type(s) }
            addView(k)
        }
        if (pad > 0) addView(spacer(pad))
    }

    private fun bottomRow(left: String, leftTarget: String): LinearLayout = hrow().apply {
        val l = mk(left, 1.4f, t.special, t.text, 15f); attach(l) { showPanel(leftTarget) }; addView(l)
        val c = mk(",", 0.9f, t.special, t.text); attach(c, onLong = { showPanel("emoji") }) { type(",") }; addView(c)
        val g = mk("🌐", 0.9f, t.special, t.text, 17f); attach(g) { switchKeyboard() }; addView(g)
        val sp = mk("CrazeKeyboard", 4f, t.key, t.text, 12f)
        attach(sp, onLong = { (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker() }) { type(" ") }; addView(sp)
        val d = mk(".", 0.9f, t.special, t.text); attach(d, onLong = { showAlts(d, Alts.map["."]!!) }) { type(".") }; addView(d)
        val e = mk(enterLabel(), 1.4f, t.accent, t.accentText, 17f); attach(e) { enter() }; addView(e)
    }

    private fun backKey(w: Float): TextView { val b = mk("⌫", w, t.special, t.text, 18f); attach(b, repeat = true) { del() }; return b }

    private fun column() = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(3), 0, dp(3), 0) }

    private fun buildLetters(): View {
        val col = column()
        if (Prefs.b(this, "numrow", true)) col.addView(charRow("1234567890", 0f, false))
        col.addView(charRow("qwertyuiop"))
        col.addView(charRow("asdfghjkl", 0.5f))
        col.addView(hrow().apply {
            val s = mk("⇧", 1.4f, t.special, t.text, 18f); shiftKey = s
            attach(s) {
                val now = SystemClock.uptimeMillis()
                // Single tap toggles shift; a quick second tap turns on caps lock.
                shift = if (shift == 2) 0 else if (shift == 1 && now - lastShiftTap < 350) 2 else if (shift == 1) 0 else 1
                lastShiftTap = now; updateCase()
            }
            addView(s)
            "zxcvbnm".forEach { ch ->
                val k = mk(ch.toString(), 1f, t.key, t.text); letterKeys.add(k to ch.toString())
                attach(k, showPrev = true, onLong = Alts.map[ch.toString()]?.let { a -> { showAlts(k, a) } }) { type(ch.toString()) }
                addView(k)
            }
            addView(backKey(1.4f))
        })
        col.addView(bottomRow("?123", "symbols"))
        updateCase()
        return col
    }

    private fun buildSymbols(page: Int): View {
        val col = column()
        if (Prefs.b(this, "numrow", true) || page == 1) col.addView(charRow("1234567890", 0f, false))
        if (page == 1) {
            col.addView(charRow("@#$%&-+()/", 0f, false))
            col.addView(hrow().apply {
                val p = mk("=\\<", 1.4f, t.special, t.text, 15f); attach(p) { showPanel("symbols2") }; addView(p)
                "*\"':;!?".forEach { ch -> val k = mk(ch.toString(), 1f, t.key, t.text); attach(k) { type(ch.toString()) }; addView(k) }
                addView(backKey(1.4f))
            })
        } else {
            col.addView(charRow("~`|•√π÷×¶∆", 0f, false))
            col.addView(charRow("£¢€¥^°={}\\", 0f, false))
            col.addView(hrow().apply {
                val p = mk("?123", 1.4f, t.special, t.text, 15f); attach(p) { showPanel("symbols") }; addView(p)
                "©®™[]<>".forEach { ch -> val k = mk(ch.toString(), 1f, t.key, t.text); attach(k) { type(ch.toString()) }; addView(k) }
                addView(backKey(1.4f))
            })
        }
        col.addView(bottomRow("ABC", "letters"))
        return col
    }

    // ---------- emoji + clipboard ----------
    private fun backBar(): View = hrow().apply {
        val b = mk("ABC", 1.4f, t.special, t.text, 15f); attach(b) { showPanel("letters") }; addView(b)
        addView(spacer(5f))
        addView(backKey(1.4f))
    }

    private fun bodyHeight() = panelH() - dp(40) - (dp(keyHeight()) + dp(6))

    private fun buildEmoji(): View {
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(3), 0, dp(3), 0) }
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
        emojiCats.forEach { (icon, data) ->
            tabs.addView(iconBtn(icon, 18f) { fill(if (icon == "🕘") Prefs.recents(ctx) else data.split(" ")) }, lp(0, dp(40), 1f))
        }
        col.addView(tabs, lp(MATCH, dp(40)))
        col.addView(body, lp(MATCH, bodyHeight()))
        col.addView(backBar())
        fill(Prefs.recents(this).ifEmpty { emojiCats["😀"]!!.split(" ") })
        return col
    }

    private fun buildClip(): View {
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(3), 0, dp(3), 0) }
        val head = LinearLayout(ctx).apply { gravity = Gravity.CENTER_VERTICAL }
        head.addView(TextView(ctx).apply { text = "  Clipboard"; textSize = 15f; setTextColor(t.text) }, lp(0, dp(40), 1f))
        head.addView(iconBtn("🗑", 16f) { Prefs.clearClips(ctx); showPanel("clip") }, lp(dp(48), dp(40)))
        col.addView(head, lp(MATCH, dp(40)))
        val body = ScrollView(ctx)
        val list = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val clips = Prefs.clips(this)
        if (clips.isEmpty()) list.addView(TextView(ctx).apply { text = "Copied text will show up here"; setTextColor(t.text); setPadding(dp(12), dp(12), dp(12), dp(12)) })
        clips.forEach { c ->
            list.addView(TextView(ctx).apply {
                text = c; maxLines = 2; ellipsize = TextUtils.TruncateAt.END; setTextColor(t.text); background = rounded(t.key)
                setPadding(dp(12), dp(8), dp(12), dp(8)); setOnClickListener { currentInputConnection?.commitText(c, 1); showPanel("letters") }
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
        letterKeys.clear()   // stale key references from other pages would be updated by updateCase()
        shiftKey = null
        val page: View = when (m) {
            "symbols" -> buildSymbols(1)
            "symbols2" -> buildSymbols(2)
            "emoji" -> buildEmoji()
            "clip" -> buildClip()
            else -> buildLetters()
        }
        panel.addView(page, FrameLayout.LayoutParams(MATCH, panelH()))
        if (m == "letters") autoCap()
    }
}
