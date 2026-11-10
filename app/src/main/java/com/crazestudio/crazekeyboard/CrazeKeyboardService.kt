package com.crazestudio.crazekeyboard

import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class CrazeKeyboardService : InputMethodService() {
    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    private val ctx: Context get() = this
    private val hd = Handler(Looper.getMainLooper())
    private val refreshSuggestions = Runnable { autoCap(); updateSuggestions() }

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
    private var suggestionPrefix: String? = null
    private var hapticsEnabled = true
    private var lastShiftTap = 0L
    private var lastSpaceTap = 0L
    private var popup: PopupWindow? = null
    private var lastSig = ""
    private var clipboardManager: ClipboardManager? = null
    private var clipListener: ClipboardManager.OnPrimaryClipChangedListener? = null
    private var clipboardListening = false
    private var clipboardCaptureAllowed = false
    private var inputViewVisible = false
    private var voiceAllowed = false
    private var voiceListening = false
    private var speechRecognizer: SpeechRecognizer? = null
    private var micView: ImageView? = null

    private val emojiCats = linkedMapOf(
        "recent" to "", 
        "smile" to "😀 😃 😄 😁 😆 😅 😂 🤣 😊 😇 🙂 🙃 😉 😌 😍 🥰 😘 😗 😙 😚 😋 😛 😝 😜 🤪 🤨 🧐 🤓 😎 🤩 🥳 😏 😒 😞 😔 😟 😕 🙁 😣 😖 😫 😩 🥺 😢 😭 😤 😠 😡 🤬 🤯 😳 🥵 🥶 😱 😨 😰 😥 😓 🤗 🤔 🤭 🤫 🤥 😶 😐 😑 😬 🙄 😯 😦 😧 😮 😲 🥱 😴 🤤 😪 😵 🤐 🥴 🤢 🤮 🤧 😷",
        "people" to "👍 👎 👌 🤞 🤟 🤘 🤙 👈 👉 👆 👇 ✋ 🤚 🖐 🖖 👋 🤝 🙏 💪 👏 🙌 👐 🤲 🤳 💅 👀 👅 👄 🧠 👶 👦 👧 👨 👩 🧑 👴 👵",
        "nature" to "🐶 🐱 🐭 🐹 🐰 🦊 🐻 🐼 🐨 🐯 🦁 🐮 🐷 🐸 🐵 🐔 🐧 🐦 🐤 🦆 🦅 🦉 🦇 🐺 🐗 🐴 🦄 🐝 🐛 🦋 🐌 🐞 🐜 🐢 🐍 🦎 🐙 🦑 🦐 🦀 🐡 🐠 🐟 🐬 🐳 🐋 🦈 🐊 🐘 🦒 🌵 🌲 🌳 🌴 🌱 🌿 🍀 🍁 🍂 🌺 🌻 🌹 🌷 🌼 🌸 🌙 ⭐ 🌈 🔥 💧 ⚡",
        "food" to "🍏 🍎 🍐 🍊 🍋 🍌 🍉 🍇 🍓 🍒 🍑 🥭 🍍 🥥 🥝 🍅 🥑 🥦 🥕 🌽 🥔 🍞 🧀 🍗 🍖 🍔 🍟 🍕 🌭 🥪 🌮 🌯 🍝 🍜 🍣 🍤 🍦 🍩 🍪 🎂 🍰 🍫 🍬 🍭 ☕ 🍵 🍺 🍷 🥤",
        "activity" to "⚽ 🏀 🏈 ⚾ 🎾 🏐 🏉 🎱 🏓 🏸 🥊 🎯 🎮 🎲 🎧 🎤 🎸 🎹 🎺 🎻 🥁 🎬 🎨 🏆 🥇 🥈 🥉",
        "travel" to "🚗 🚕 🚌 🚓 🚑 🚒 🚲 🚀 🚁 🚢 🏠 🌍",
        "objects" to "📱 💻 📷 📺 ⌚ 🔋 💡 🔦 📚 📖 📝 📎 🔑 🔒 🔔 💰 💳 🎁 🎈 🎉 ❤ 🧡 💛 💚 💙 💜 🖤 💔 💕 💞 💯 ✅ ❌ ⭕ ❗ ❓ ⚠ 💤 💬 💭"
    )

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun lp(w: Int, h: Int, wt: Float = 0f) = LinearLayout.LayoutParams(w, h, wt)
    private fun rounded(c: Int) = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; cornerRadius = dp(11).toFloat(); setColor(c) }

    override fun onCreate() {
        super.onCreate()
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager = cm
        clipListener = ClipboardManager.OnPrimaryClipChangedListener {
            if (clipboardCaptureAllowed) {
                try {
                    val item = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)
                    item?.text?.toString()?.takeIf { it.isNotBlank() }?.let { Prefs.addClip(ctx, it) }
                } catch (_: Exception) {
                    // Clipboard access can be restricted by the OS or the source app.
                }
            }
        }
    }

    private fun setClipboardMonitoring(enabled: Boolean) {
        clipboardCaptureAllowed = enabled
        val cm = clipboardManager ?: return
        val listener = clipListener ?: return
        if (clipboardListening == enabled) return
        if (enabled) cm.addPrimaryClipChangedListener(listener)
        else cm.removePrimaryClipChangedListener(listener)
        clipboardListening = enabled
    }

    private fun configureEditor(info: EditorInfo) {
        val cls = info.inputType and InputType.TYPE_MASK_CLASS
        val variation = info.inputType and InputType.TYPE_MASK_VARIATION
        val noSuggestions = listOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_URI
        )
        hapticsEnabled = Prefs.b(this, "haptics", true)
        val passwordField =
            (cls == InputType.TYPE_CLASS_TEXT && variation in listOf(
                InputType.TYPE_TEXT_VARIATION_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
            )) ||
            (cls == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        val noPersonalizedLearning =
            (info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0
        suggestOn = Prefs.b(this, "suggest", true) && cls == InputType.TYPE_CLASS_TEXT &&
            variation !in noSuggestions && !noPersonalizedLearning &&
            (info.inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) == 0
        voiceAllowed = cls == InputType.TYPE_CLASS_TEXT &&
            variation !in noSuggestions && !passwordField && !noPersonalizedLearning
        if (!voiceAllowed) cancelVoiceInput()
        setClipboardMonitoring(
            inputViewVisible && Prefs.b(this, "clipboardHistory", true) &&
                !passwordField && !noPersonalizedLearning
        )
    }

    override fun onStartInput(attribute: EditorInfo, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        cancelVoiceInput()
        configureEditor(attribute)
    }

    override fun onDestroy() {
        inputViewVisible = false
        setClipboardMonitoring(false)
        clipboardManager = null
        cancelVoiceInput()
        speechRecognizer?.destroy()
        speechRecognizer = null
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        t = Prefs.theme(this)
        lastSig = Prefs.sig(this)
        outer = FrameLayout(this)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(t.bg) }
        top = FrameLayout(this)
        panel = FrameLayout(this)
        root.addView(top, lp(MATCH, dp(48)))
        root.addView(panel, lp(MATCH, totalH()))
        val oh = Prefs.i(this, "onehand", 0)
        val w = if (oh == 0) MATCH else (resources.displayMetrics.widthPixels * 0.82f).toInt()
        outer.addView(root, FrameLayout.LayoutParams(w, dp(48) + totalH(), if (oh == 2) Gravity.END else Gravity.START))
        buildTop()
        showPanel(mode)
        return outer
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        inputViewVisible = true
        if (Prefs.sig(this) != lastSig) setInputView(onCreateInputView())
        val cls = info.inputType and InputType.TYPE_MASK_CLASS
        configureEditor(info)
        shift = 0
        toolbarOpen = false
        buildTop()
        showPanel(if (cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE) "symbols" else "letters")
        autoCap()
        updateSuggestions()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        inputViewVisible = false
        setClipboardMonitoring(false)
        dismissPopups()
        hd.removeCallbacks(refreshSuggestions)
        suggestionPrefix = null
        cancelVoiceInput()
        super.onFinishInputView(finishingInput)
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        if (oldSelStart != newSelStart || oldSelEnd != newSelEnd) afterEdit()
    }

    // ---------- top bar (suggestions / toolbar) ----------
    private fun iconBtn(label: String, a: () -> Unit) = TextView(ctx).apply {
        text = label; textSize = 18f; gravity = Gravity.CENTER; setTextColor(t.text); typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        setOnClickListener { a() }
    }

    private fun iconImage(res: Int, a: () -> Unit) = ImageView(ctx).apply {
        setImageResource(res)
        imageTintList = android.content.res.ColorStateList.valueOf(t.text)
        scaleType = ImageView.ScaleType.CENTER
        setPadding(dp(9), dp(9), dp(9), dp(9)); alpha = 0.92f
        setOnClickListener { a() }
        isClickable = true
        isFocusable = true
    }

    private fun iconKey(res: Int, w: Float, bg: Int, fg: Int): TextView = mk("", w, bg, fg).apply {
        setCompoundDrawablesWithIntrinsicBounds(res, 0, 0, 0)
        compoundDrawableTintList = android.content.res.ColorStateList.valueOf(fg)
    }

    private fun buildTop() {
        top.removeAllViews()
        sugViews.clear()
        val row = LinearLayout(ctx).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(5), 0, dp(5), 0) }
        row.addView(iconImage(if (toolbarOpen) R.drawable.ic_close else R.drawable.ic_menu) {
            toolbarOpen = !toolbarOpen
            buildTop()
            updateSuggestions()
        }, lp(dp(44), MATCH))
        if (toolbarOpen) {
            val items = listOf<Pair<Int, () -> Unit>>(
                R.drawable.ic_smile to { showPanel("emoji") },
                R.drawable.ic_clipboard to { showPanel("clip") },
                R.drawable.ic_palette to { Prefs.nextTheme(ctx); setInputView(onCreateInputView()) },
                R.drawable.ic_onehand to { Prefs.nextOneHand(ctx); setInputView(onCreateInputView()) },
                R.drawable.ic_settings to { startActivity(Intent(ctx, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            )
            items.forEach { (res, a) -> row.addView(iconImage(res, a), lp(0, MATCH, 1f)) }
        } else {
            for (i in 0..2) {
                val tv = TextView(ctx).apply {
                    gravity = Gravity.CENTER; textSize = 15f; setTextColor(t.text); maxLines = 1; typeface = android.graphics.Typeface.create("sans-serif-medium", 0)
                    ellipsize = TextUtils.TruncateAt.END
                    setOnClickListener { pickSuggestion(text.toString()) }
                }
                sugViews.add(tv)
                row.addView(tv, lp(0, MATCH, 1f))
                if (i < 2) row.addView(View(ctx).apply { setBackgroundColor(t.special) },
                    lp(dp(1), dp(24)).apply { gravity = Gravity.CENTER_VERTICAL })
            }
        }
        val mic = iconImage(R.drawable.ic_mic) { startVoiceInput() }.apply {
            contentDescription = "Voice typing"
        }
        micView = mic
        row.addView(mic, lp(dp(42), MATCH))
        top.addView(row, FrameLayout.LayoutParams(MATCH, MATCH))
        updateMicButton()
    }

    private fun updateMicButton() {
        micView?.imageTintList = android.content.res.ColorStateList.valueOf(
            if (voiceListening) t.accent else t.text
        )
        micView?.alpha = if (voiceAllowed) 1f else 0.45f
    }

    private fun startVoiceInput() {
        if (!voiceAllowed) {
            Toast.makeText(this, "Voice typing is unavailable in this field", Toast.LENGTH_SHORT).show()
            return
        }
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            startActivity(
                Intent(this, SettingsActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra("request_voice_permission", true)
            )
            return
        }
        if (voiceListening) {
            cancelVoiceInput()
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "No speech recognition service is installed", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val recognizer = speechRecognizer ?: SpeechRecognizer.createSpeechRecognizer(this).also { created ->
                created.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        voiceListening = true
                        updateMicButton()
                    }
                    override fun onBeginningOfSpeech() {
                        voiceListening = true
                        updateMicButton()
                    }
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onError(error: Int) {
                        val wasListening = voiceListening
                        voiceListening = false
                        updateMicButton()
                        if (wasListening && inputViewVisible) {
                            val message = if (error == SpeechRecognizer.ERROR_NO_MATCH ||
                                error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                                "Didn't catch that. Try again."
                            } else {
                                "Voice typing stopped."
                            }
                            Toast.makeText(this@CrazeKeyboardService, message, Toast.LENGTH_SHORT).show()
                        }
                    }
                    override fun onResults(results: Bundle?) {
                        val active = voiceListening && inputViewVisible && voiceAllowed
                        voiceListening = false
                        updateMicButton()
                        if (!active) return
                        val spoken = results
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()?.trim().orEmpty()
                        if (spoken.isEmpty()) return
                        val ic = currentInputConnection ?: return
                        val before = ic.getTextBeforeCursor(1, 0)?.toString().orEmpty()
                        val prefix = if (before.isNotEmpty() &&
                            (before.last().isLetterOrDigit() || before.last() == ')')) " " else ""
                        val suffix = if (spoken.last().isWhitespace()) "" else " "
                        ic.beginBatchEdit()
                        try {
                            ic.commitText(prefix + spoken + suffix, 1)
                        } finally {
                            ic.endBatchEdit()
                        }
                        afterEdit()
                    }
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }.also { speechRecognizer = it }
            val locale = currentInputEditorInfo?.hintLocales?.get(0)?.toLanguageTag()
                ?: Locale.getDefault().toLanguageTag()
            val request = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            }
            voiceListening = true
            updateMicButton()
            recognizer.startListening(request)
        } catch (_: Exception) {
            voiceListening = false
            updateMicButton()
            Toast.makeText(this, "Could not start voice typing", Toast.LENGTH_SHORT).show()
        }
    }

    private fun cancelVoiceInput() {
        val wasListening = voiceListening
        voiceListening = false
        if (wasListening) {
            try { speechRecognizer?.cancel() } catch (_: Exception) {}
        }
        updateMicButton()
    }

    private fun currentWord(): String {
        val before = currentInputConnection?.getTextBeforeCursor(64, 0)?.toString() ?: ""
        return before.takeLastWhile { it.isLetter() || it == '\'' || it == '-' }
    }

    private fun previousWord(): String {
        val before = currentInputConnection?.getTextBeforeCursor(96, 0)?.toString() ?: ""
        return before.trimEnd().split(Regex("[^\\p{L}'-]+")).lastOrNull { it.isNotBlank() } ?: ""
    }

    private fun updateSuggestions() {
        if (toolbarOpen || sugViews.isEmpty()) return
        val word = currentWord()
        val hasSelection = !currentInputConnection?.getSelectedText(0).isNullOrEmpty()
        // An empty prefix is intentional: it requests context-aware next-word candidates.
        val canSuggest = suggestOn && !hasSelection && word.length < 48 &&
            (word.isNotEmpty() || Prefs.b(this, "nextWord", true))
        suggestionPrefix = if (canSuggest) word else null
        val list = if (canSuggest) Words.suggest(this, word, previousWord()) else emptyList()
        for (i in 0..2) sugViews[i].text = list.getOrNull(i) ?: ""
    }

    private fun pickSuggestion(s: String) {
        val ic = currentInputConnection ?: return
        if (s.isEmpty()) return
        val w = currentWord()
        if (w.length >= 48 || w != suggestionPrefix) return
        ic.beginBatchEdit()
        try {
            if (!ic.getSelectedText(0).isNullOrEmpty()) {
                ic.commitText("$s ", 1)
            } else {
                if (w.isNotEmpty()) ic.deleteSurroundingText(w.length, 0)
                ic.commitText("$s ", 1)
            }
        } finally {
            ic.endBatchEdit()
        }
        if (suggestOn && s.length >= 3 && !Words.known(this, s)) Prefs.learn(this, s)
        afterEdit()
    }

    // ---------- typing actions ----------
    // Cursor queries and dictionary matching are deliberately debounced off the key-down path.
    private fun afterEdit() {
        hd.removeCallbacks(refreshSuggestions)
        hd.postDelayed(refreshSuggestions, 85)
    }

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

    private fun space(ic: android.view.inputmethod.InputConnection) {
        // Correction and learning are restricted to ordinary text fields by configureEditor().
        val selected = !ic.getSelectedText(0).isNullOrEmpty()
        val now = SystemClock.uptimeMillis()
        val previousChar = ic.getTextBeforeCursor(1, 0)?.toString()
        if (!selected && Prefs.b(this, "doubleSpacePeriod", true) &&
            previousChar == " " && now - lastSpaceTap in 1L..350L) {
            ic.deleteSurroundingText(1, 0)
            ic.commitText(". ", 1)
            lastSpaceTap = 0L
            afterEdit()
            return
        }
        if (!selected && suggestOn) {
            val typed = currentWord()
            if (typed.length in 2..40 && typed.any { it.isLetter() }) {
                val correction = if (Prefs.b(this, "autocorrect", true)) Words.correction(this, typed) else null
                if (correction != null && !correction.equals(typed, ignoreCase = true)) {
                    ic.beginBatchEdit()
                    try {
                        ic.deleteSurroundingText(typed.length, 0)
                        ic.commitText(correction, 1)
                        ic.commitText(" ", 1)
                    } finally {
                        ic.endBatchEdit()
                    }
                } else {
                    if (!Words.known(this, typed)) Prefs.learn(this, typed)
                    ic.commitText(" ", 1)
                }
            } else {
                ic.commitText(" ", 1)
            }
        } else {
            ic.commitText(" ", 1)
        }
        lastSpaceTap = now
        afterEdit()
    }

    private fun del() {
        val ic = currentInputConnection ?: return
        if (!ic.getSelectedText(0).isNullOrEmpty()) ic.commitText("", 1)
        else if (!ic.deleteSurroundingTextInCodePoints(1, 0)) sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        lastSpaceTap = 0L
        afterEdit()
    }

    private fun deleteWord() {
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(96, 0)?.toString() ?: return
        val spaces = before.takeLastWhile { it.isWhitespace() }
        val preceding = before.dropLast(spaces.length)
        val word = preceding.takeLastWhile { it.isLetterOrDigit() || it == '\'' || it == '_' }
        val count = spaces.length + word.length
        if (count > 0) ic.deleteSurroundingText(count, 0)
        lastSpaceTap = 0L
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
        EditorInfo.IME_ACTION_SEARCH -> "Search"
        EditorInfo.IME_ACTION_SEND -> "Send"
        EditorInfo.IME_ACTION_GO -> "Go"
        EditorInfo.IME_ACTION_NEXT -> "Next"
        EditorInfo.IME_ACTION_DONE -> "Done"
        else -> "Enter"
    }

     private fun switchKeyboard() {
        if (!switchToNextInputMethod(false)) (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
    }

    // ---------- key building ----------
    private fun mk(label: String, w: Float, bg: Int, fg: Int, size: Float = 20f) = TextView(ctx).apply {
        text = label; gravity = Gravity.CENTER; setTextColor(fg); textSize = size
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(11).toFloat()
            setColor(bg)
            setStroke(dp(1), if (bg == t.key) 0x18000000 else 0x12000000)
        }
        elevation = dp(if (bg == t.accent) 1 else 2).toFloat()
        includeFontPadding = false
        isClickable = true
        layoutParams = lp(0, dp(keyHeight()), w).apply { setMargins(dp(2), dp(3), dp(2), dp(3)) }
    }

    private fun dismissPopups() { popup?.dismiss(); popup = null }

    private fun attach(v: TextView, repeat: Boolean = false, showPrev: Boolean = false, onLong: (() -> Unit)? = null, tap: () -> Unit) {
        var fired = false
        var cancelled = false
        v.setOnClickListener { }
        val longR = Runnable { fired = true; onLong?.invoke() }
        val repR = object : Runnable { override fun run() { tap(); hd.postDelayed(this, 65) } }
        v.setOnTouchListener { view, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    fired = false
                    cancelled = false
                    view.animate().cancel()
                    view.animate().scaleX(0.96f).scaleY(0.96f).alpha(0.78f).setDuration(55).start()
                    if (hapticsEnabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    if (repeat) {
                        tap()
                        hd.postDelayed(repR, 400)
                        if (onLong != null) hd.postDelayed(longR, 500)
                    } else if (onLong != null) hd.postDelayed(longR, 350)
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!cancelled && (e.x < 0f || e.y < 0f || e.x > view.width || e.y > view.height)) {
                        cancelled = true
                        view.animate().cancel()
                        view.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(65).start()
                        hd.removeCallbacks(longR)
                        hd.removeCallbacks(repR)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    hd.removeCallbacks(longR)
                    hd.removeCallbacks(repR)
                    view.animate().cancel()
                    view.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(65).start()
                    if (!repeat && !fired && !cancelled) {
                        view.performClick()
                        tap()
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    cancelled = true
                    view.animate().cancel()
                    view.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(65).start()
                    hd.removeCallbacks(longR)
                    hd.removeCallbacks(repR)
                }
            }
            true
        }
    }

    private fun showAlts(anchor: View, alts: String) {
        popup?.dismiss()
        val row = LinearLayout(ctx).apply { background = rounded(t.special); setPadding(dp(4), dp(4), dp(4), dp(4)) }
        alts.forEach { ch ->
            val s = if (shift > 0 && mode == "letters") ch.toString().uppercase() else ch.toString()
            row.addView(TextView(ctx).apply {
                text = s; textSize = 22f; gravity = Gravity.CENTER; setTextColor(t.text)
                setPadding(dp(10), dp(8), dp(10), dp(8)); setOnClickListener { type(ch.toString()) }
            })
        }
        popup = PopupWindow(row, WRAP, WRAP).apply {
            setBackgroundDrawable(ColorDrawable(0)); isOutsideTouchable = true
            showAsDropDown(anchor, 0, -(anchor.height + dp(56)))
        }
    }

    private fun hrow() = LinearLayout(ctx).apply { gravity = Gravity.CENTER }
    private fun spacer(w: Float) = View(ctx).apply { layoutParams = lp(0, 1, w) }

    private fun charRow(chars: String, pad: Float = 0f, letters: Boolean = true): LinearLayout = hrow().apply {
        if (pad > 0) addView(spacer(pad))
        chars.forEach { ch ->
            val s = ch.toString()
            val k = mk(s, 1f, t.key, t.text, if (s[0].isDigit() && letters) 18f else 20f)
            if (letters && s[0].isLetter()) letterKeys.add(k to s)
            attach(k, showPrev = letters && s[0].isLetter(), onLong = Alts.map[s]?.let { a -> { showAlts(k, a) } }) { type(s) }
            addView(k)
        }
        if (pad > 0) addView(spacer(pad))
    }

    private fun bottomRow(left: String, leftTarget: String): LinearLayout = hrow().apply {
        val l = mk(left, 1.5f, t.special, t.text, 16f); attach(l) { showPanel(leftTarget) }; addView(l)
        val c = mk(",", 1f, t.special, t.text); attach(c, onLong = { showPanel("emoji") }) { type(",") }; addView(c)
        val g = iconKey(R.drawable.ic_globe, 1f, t.special, t.text); attach(g) { switchKeyboard() }; addView(g)
        val sp = mk("craze", 4f, t.key, t.text, 13f)
        attach(sp, onLong = { (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker() }) { type(" ") }; addView(sp)
        val d = mk(".", 1f, t.special, t.text); attach(d, onLong = { showAlts(d, Alts.map["."]!!) }) { type(".") }; addView(d)
        val e = mk(enterLabel(), 1.5f, t.accent, t.accentText); attach(e) { enter() }; addView(e)
    }

    private fun backKey(w: Float): TextView {
        val b = iconKey(R.drawable.ic_backspace, w, t.special, t.text)
        attach(b, repeat = true, onLong = { deleteWord() }) { del() }
        return b
    }

    private fun rowCount() = if (Prefs.b(this, "numrow", true)) 5 else 4

    // Keep every panel on the same measured key grid, while shrinking keys on short displays.
    private fun keyHeight(): Int {
        val screenDp = resources.displayMetrics.heightPixels / resources.displayMetrics.density
        val availableForKeys = screenDp * 0.42f - 48f
        val heightLimit = (availableForKeys / rowCount() - 6f).toInt().coerceAtLeast(34)
        return minOf(Prefs.keyH(this), heightLimit).coerceIn(34, 64)
    }

    private fun totalH() = rowCount() * (dp(keyHeight()) + dp(6)) + dp(4)

    private fun column() = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(3), 0, dp(3), dp(5)) }

    private fun buildLetters(): View {
        letterKeys.clear()
        val col = column()
        if (Prefs.b(this, "numrow", true)) col.addView(charRow("1234567890", 0f, false))
        col.addView(charRow("qwertyuiop"))
        col.addView(charRow("asdfghjkl", 0.5f))
        col.addView(hrow().apply {
            val s = mk("⇧", 1.5f, t.special, t.text); shiftKey = s
            attach(s) {
                val now = SystemClock.uptimeMillis()
                shift = if (shift == 2) 0 else if (shift == 1 && now - lastShiftTap < 350) 2 else if (shift == 1) 0 else 1
                lastShiftTap = now; updateCase()
            }
            addView(s)
            "zxcvbnm".forEach { ch ->
                val k = mk(ch.toString(), 1f, t.key, t.text); letterKeys.add(k to ch.toString())
                attach(k, showPrev = true, onLong = Alts.map[ch.toString()]?.let { a -> { showAlts(k, a) } }) { type(ch.toString()) }
                addView(k)
            }
            addView(backKey(1.5f))
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
                val p = mk("=\\<", 1.5f, t.special, t.text, 16f); attach(p) { showPanel("symbols2") }; addView(p)
                "*\"':;!?".forEach { ch -> val k = mk(ch.toString(), 1f, t.key, t.text); attach(k) { type(ch.toString()) }; addView(k) }
                addView(backKey(1.5f))
            })
        } else {
            col.addView(charRow("~`|•√π÷×¶∆", 0f, false))
            col.addView(charRow("£¢€¥^°={}\\", 0f, false))
            col.addView(hrow().apply {
                val p = mk("?123", 1.5f, t.special, t.text, 16f); attach(p) { showPanel("symbols") }; addView(p)
                "©®™[]<>".forEach { ch -> val k = mk(ch.toString(), 1f, t.key, t.text); attach(k) { type(ch.toString()) }; addView(k) }
                addView(backKey(1.5f))
            })
        }
        col.addView(bottomRow("ABC", "letters"))
        return col
    }

    // ---------- emoji + clipboard ----------
    private fun backBar(): View = hrow().apply {
        val b = mk("ABC", 1.5f, t.special, t.text, 16f); attach(b) { showPanel("letters") }; addView(b)
        addView(spacer(5f))
        addView(backKey(1.5f))
    }

    private fun bodyHeight() = (totalH() - dp(40) - (dp(keyHeight()) + dp(6))).coerceAtLeast(dp(72))

    private fun buildEmoji(): View {
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(2), 0, dp(2), dp(4)) }
        val tabs = LinearLayout(ctx).apply { gravity = Gravity.CENTER_VERTICAL }
        val body = ScrollView(ctx)
        fun fill(list: List<String>) {
            val g = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
            list.chunked(8).forEach { r ->
                val row = LinearLayout(ctx)
                r.forEach { e ->
                    row.addView(TextView(ctx).apply {
                        text = e; textSize = 24f; gravity = Gravity.CENTER; setPadding(0, dp(8), 0, dp(8))
                        setOnClickListener { currentInputConnection?.commitText(e, 1); Prefs.addRecent(ctx, e) }
                    }, lp(0, WRAP, 1f))
                }
                repeat(8 - r.size) { row.addView(View(ctx), lp(0, 1, 1f)) }
                g.addView(row)
            }
            body.removeAllViews(); body.addView(g)
        }
        val iconIds = listOf(R.drawable.ic_recent, R.drawable.ic_smile, R.drawable.ic_people, R.drawable.ic_nature,
            R.drawable.ic_food, R.drawable.ic_activity, R.drawable.ic_travel, R.drawable.ic_objects)
        emojiCats.entries.forEachIndexed { index, entry ->
            val (category, data) = entry
            tabs.addView(iconImage(iconIds[index]) {
                fill(if (category == "recent") Prefs.recents(ctx) else data.split(" ").filter { it.isNotBlank() })
            }, lp(0, MATCH, 1f))
        }
        col.addView(tabs, lp(MATCH, dp(42)))
        col.addView(body, lp(MATCH, bodyHeight()))
        col.addView(backBar())
        fill(Prefs.recents(this).ifEmpty { emojiCats["smile"]!!.split(" ") })
        return col
    }

    private fun buildClip(): View {
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(2), 0, dp(2), dp(4)) }
        val head = LinearLayout(ctx).apply { gravity = Gravity.CENTER_VERTICAL }
        head.addView(TextView(ctx).apply { text = "  Clipboard"; textSize = 16f; setTextColor(t.text) }, lp(0, MATCH, 1f))
        head.addView(iconImage(R.drawable.ic_trash) { Prefs.clearClips(ctx); showPanel("clip") }, lp(dp(48), MATCH))
        col.addView(head, lp(MATCH, dp(40)))
        val body = ScrollView(ctx)
        val list = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val clips = Prefs.clips(this)
        if (clips.isEmpty()) list.addView(TextView(ctx).apply { text = "Copied text will show up here"; setTextColor(t.text); setPadding(dp(12), dp(12), dp(12), dp(12)) })
        clips.forEach { c ->
            list.addView(TextView(ctx).apply {
                text = c; maxLines = 2; ellipsize = TextUtils.TruncateAt.END; setTextColor(t.text); background = rounded(t.key)
                setPadding(dp(12), dp(10), dp(12), dp(10)); setOnClickListener { currentInputConnection?.commitText(c, 1); showPanel("letters") }
            }, lp(MATCH, WRAP).apply { setMargins(dp(4), dp(3), dp(4), dp(3)) })
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
        shiftKey = null
        panel.addView(when (m) {
            "symbols" -> buildSymbols(1)
            "symbols2" -> buildSymbols(2)
            "emoji" -> buildEmoji()
            "clip" -> buildClip()
            else -> buildLetters()
        }, FrameLayout.LayoutParams(MATCH, totalH()))
        if (m == "letters") {
            autoCap()
            updateSuggestions()
        }
    }
}
