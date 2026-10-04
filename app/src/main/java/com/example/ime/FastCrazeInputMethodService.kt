package com.example.ime

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.android.inputmethod.latin.RichInputConnection
import com.example.MainActivity
import com.example.core.AppDatabase
import com.example.core.SoundFeedbackHelper
import com.example.developer.DevKeyItem
import com.example.developer.DevKeyType
import com.example.developer.ModifierState
import com.example.keyboard.CrazeKeyboardView
import com.example.keyboard.CursorAction
import com.example.keyboard.KeyboardActionListener
import com.example.settings.KeyboardPreferences
import com.example.suggestions.SuggestionEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File

/**
 * Lightweight IME service for CrazeKeyboard-v2.
 * Normal character input never enters the AOSP InputLogic pipeline.
 * AOSP RichInputConnection remains available only for explicit editor operations.
 */
class FastCrazeInputMethodService : InputMethodService(), KeyboardActionListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val lifecycleOwner = ImeLifecycleOwner()
    private val handler = Handler(Looper.getMainLooper())
    private val database by lazy { AppDatabase.getInstance(applicationContext) }
    private val preferences by lazy { KeyboardPreferences.getInstance(applicationContext) }
    private val suggestionEngine by lazy { SuggestionEngine(applicationContext, database.personalDictionaryDao()) }
    private val richConnection by lazy { RichInputConnection(this) }

    private var composeView: ComposeView? = null
    private var uiRefreshPending = false
    private var contextRefreshPending = false
    private var localWordPrefix = ""
    private var localSentenceStart = true
    private var imeAction = EditorInfo.IME_ACTION_NONE
    private var passwordField = false
    private var emailField = false
    private var uriField = false
    private var numericField = false
    private var multilineField = false
    private var selectMode = false
    private var inlineViews by mutableStateOf<List<View>>(emptyList())
    private var wordPrefix by mutableStateOf("")
    private var sentenceStart by mutableStateOf(true)
    private var actualSentenceStart by mutableStateOf(true)
    private var autoCorrectNotice by mutableStateOf<Pair<String, String>?>(null)

    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener { saveLatestClipboard() }

    override fun onCreate() {
        super.onCreate()
        lifecycleOwner.onCreate()
        (getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.addPrimaryClipChangedListener(clipboardListener)
    }

    private fun createKeyboardView() = ComposeView(this).apply {
        isHapticFeedbackEnabled = true
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnLifecycleDestroyed(lifecycleOwner))
        setViewTreeLifecycleOwner(lifecycleOwner)
        setViewTreeSavedStateRegistryOwner(lifecycleOwner)
        setViewTreeViewModelStoreOwner(lifecycleOwner)
        setContent {
            CrazeKeyboardView(
                context = this@FastCrazeInputMethodService,
                preferences = preferences,
                clipboardDao = database.clipboardDao(),
                suggestionEngine = suggestionEngine,
                currentWordPrefix = wordPrefix,
                isSentenceStart = sentenceStart,
                isActualSentenceStart = actualSentenceStart,
                isSelectModeActive = selectMode,
                imeAction = imeAction,
                inlineSuggestionViews = inlineViews,
                isPasswordField = passwordField,
                isEmailField = emailField,
                isUriField = uriField,
                isNumericField = numericField,
                isMultilineField = multilineField,
                listener = this@FastCrazeInputMethodService
            )
        }
    }

    override fun onCreateInputView(): View {
        lifecycleOwner.onStart()
        val view = composeView ?: createKeyboardView().also { composeView = it }
        window?.window?.apply {
            addFlags(android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        }
        return view
    }

    override fun onEvaluateFullscreenMode() = false
    override fun onShowInputRequested(flags: Int, configChange: Boolean) = true

    override fun onStartInput(info: EditorInfo?, restarting: Boolean) {
        super.onStartInput(info, restarting)
        SoundFeedbackHelper.getInstance(this).refreshVolume()
        readEditorInfo(info)
        richConnection.onConnect(currentInputConnection)
        localWordPrefix = ""
        localSentenceStart = true
        queryInitialContext()
    }

    override fun onFinishInput() {
        super.onFinishInput()
        richConnection.onDisconnect()
        localWordPrefix = ""
        wordPrefix = ""
        lifecycleOwner.onStop()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        lifecycleOwner.onStart()
        inlineViews = emptyList()
        scope.launch(Dispatchers.Default) { suggestionEngine.ensureLoaded() }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        inlineViews = emptyList()
    }

    override fun onWindowShown() {
        super.onWindowShown()
        lifecycleOwner.onStart()
    }

    override fun onWindowHidden() {
        super.onWindowHidden()
        lifecycleOwner.onStop()
    }

    private fun readEditorInfo(info: EditorInfo?) {
        val inputType = info?.inputType ?: 0
        val cls = inputType and android.text.InputType.TYPE_MASK_CLASS
        val variation = inputType and android.text.InputType.TYPE_MASK_VARIATION
        passwordField = (cls == android.text.InputType.TYPE_CLASS_TEXT && variation in intArrayOf(
            android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD,
            android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
        )) || (cls == android.text.InputType.TYPE_CLASS_NUMBER && variation == android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        emailField = cls == android.text.InputType.TYPE_CLASS_TEXT && variation in intArrayOf(
            android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            android.text.InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
        )
        uriField = cls == android.text.InputType.TYPE_CLASS_TEXT && variation == android.text.InputType.TYPE_TEXT_VARIATION_URI
        numericField = cls == android.text.InputType.TYPE_CLASS_NUMBER || cls == android.text.InputType.TYPE_CLASS_PHONE || cls == android.text.InputType.TYPE_CLASS_DATETIME
        multilineField = (inputType and android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0
        imeAction = info?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
    }

    private fun queryInitialContext() {
        val ic = currentInputConnection ?: return
        scope.launch(Dispatchers.Default) {
            val before = try { ic.getTextBeforeCursor(64, 0)?.toString().orEmpty() } catch (_: Exception) { "" }
            val word = before.takeLastWhile { !it.isWhitespace() }
            val start = before.isEmpty() || before.lastOrNull() == '\n' || before.trimEnd().lastOrNull() in charArrayOf('.', '!', '?')
            handler.post {
                localWordPrefix = word
                localSentenceStart = start
                publishLocalState()
            }
        }
    }

    private fun publishLocalState() {
        wordPrefix = localWordPrefix
        sentenceStart = localSentenceStart
        actualSentenceStart = localSentenceStart
    }

    private fun scheduleUiRefresh() {
        if (uiRefreshPending) return
        uiRefreshPending = true
        handler.postDelayed({
            uiRefreshPending = false
            if (!isDestroyed) publishLocalState()
        }, 80L)
    }

    private fun scheduleContextRefresh() {
        if (contextRefreshPending) return
        contextRefreshPending = true
        handler.postDelayed({
            contextRefreshPending = false
            if (!isDestroyed) queryInitialContext()
        }, 120L)
    }

    override fun onText(text: String) {
        if (text.isEmpty()) return
        currentInputConnection?.commitText(text, 1)
        updateLocalText(text)
        scheduleUiRefresh()
    }

    private fun updateLocalText(text: String) {
        if (text == " ") {
            localWordPrefix = " "
            return
        }
        if (text.contains('\n')) {
            localWordPrefix = ""
            localSentenceStart = true
            return
        }
        localWordPrefix += text
        val last = text.lastOrNull()
        if (last == '.' || last == '!' || last == '?') localSentenceStart = true
        else if (last?.isLetterOrDigit() == true) localSentenceStart = false
    }

    override fun onBackspace() {
        val ic = currentInputConnection ?: return
        try { ic.deleteSurroundingTextInCodePoints(1, 0) } catch (_: Exception) { ic.deleteSurroundingText(1, 0) }
        if (localWordPrefix.isNotEmpty() && localWordPrefix != " ") localWordPrefix = localWordPrefix.dropLast(1) else scheduleContextRefresh()
        scheduleUiRefresh()
    }

    override fun onDeleteWord() {
        val ic = currentInputConnection ?: return
        try {
            val before = ic.getTextBeforeCursor(128, 0)?.toString().orEmpty()
            val word = before.takeLastWhile { !it.isWhitespace() }
            val count = word.codePointCount(0, word.length)
            ic.deleteSurroundingTextInCodePoints(if (count > 0) count else 1, 0)
        } catch (_: Exception) { }
        localWordPrefix = ""
        scheduleContextRefresh()
    }

    override fun onReplaceCurrentWord(replacement: String) {
        val ic = currentInputConnection ?: return
        try {
            val before = ic.getTextBeforeCursor(128, 0)?.toString().orEmpty()
            val word = before.takeLastWhile { !it.isWhitespace() }
            if (word.isNotEmpty()) ic.deleteSurroundingTextInCodePoints(word.codePointCount(0, word.length), 0)
            ic.commitText(replacement, 1)
            localWordPrefix = replacement.trimEnd()
        } catch (_: Exception) { }
        scheduleUiRefresh()
    }

    override fun onEnter() {
        val ic = currentInputConnection ?: return
        if (imeAction != EditorInfo.IME_ACTION_NONE && imeAction != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(imeAction)
        } else {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        }
        localWordPrefix = ""
        localSentenceStart = true
        scheduleUiRefresh()
    }

    override fun onMoveCursor(offset: Int) {
        val ic = currentInputConnection ?: return
        val code = if (offset < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
        repeat(kotlin.math.abs(offset).coerceAtMost(16)) {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        }
        scheduleContextRefresh()
    }

    override fun onSpecialKey(action: CursorAction) {
        if (action == CursorAction.TOGGLE_SELECT) {
            selectMode = !selectMode
            scheduleUiRefresh()
            return
        }
        when (action) {
            CursorAction.LEFT -> move(KeyEvent.KEYCODE_DPAD_LEFT)
            CursorAction.RIGHT -> move(KeyEvent.KEYCODE_DPAD_RIGHT)
            CursorAction.UP -> move(KeyEvent.KEYCODE_DPAD_UP)
            CursorAction.DOWN -> move(KeyEvent.KEYCODE_DPAD_DOWN)
            CursorAction.HOME, CursorAction.START -> move(KeyEvent.KEYCODE_MOVE_HOME)
            CursorAction.END -> move(KeyEvent.KEYCODE_MOVE_END)
            CursorAction.SELECT_ALL -> richConnection.performContextMenuAction(android.R.id.selectAll)
            CursorAction.CUT -> richConnection.performContextMenuAction(android.R.id.cut)
            CursorAction.COPY -> richConnection.performContextMenuAction(android.R.id.copy)
            CursorAction.PASTE -> richConnection.performContextMenuAction(android.R.id.paste)
            CursorAction.DELETE -> move(KeyEvent.KEYCODE_FORWARD_DEL)
            CursorAction.CLEAR -> { richConnection.beginBatchEdit(); try { richConnection.deleteTextBeforeCursor(2000) } finally { richConnection.endBatchEdit() } }
            CursorAction.TOGGLE_SELECT -> Unit
        }
        scheduleContextRefresh()
    }

    private fun move(code: Int) {
        if (selectMode) richConnection.sendKeyWithMeta(code, KeyEvent.META_SHIFT_ON) else richConnection.sendDownUpKeyEvents(code)
    }

    override fun onDevKey(item: DevKeyItem, modifierState: ModifierState) {
        when (item.type) {
            DevKeyType.ESC -> move(KeyEvent.KEYCODE_ESCAPE)
            DevKeyType.TAB -> move(KeyEvent.KEYCODE_TAB)
            DevKeyType.ARROW_LEFT -> move(KeyEvent.KEYCODE_DPAD_LEFT)
            DevKeyType.ARROW_RIGHT -> move(KeyEvent.KEYCODE_DPAD_RIGHT)
            DevKeyType.ARROW_UP -> move(KeyEvent.KEYCODE_DPAD_UP)
            DevKeyType.ARROW_DOWN -> move(KeyEvent.KEYCODE_DPAD_DOWN)
            DevKeyType.HOME -> move(KeyEvent.KEYCODE_MOVE_HOME)
            DevKeyType.END -> move(KeyEvent.KEYCODE_MOVE_END)
            DevKeyType.CTRL -> modifierState.toggleCtrl()
            DevKeyType.ALT -> modifierState.toggleAlt()
            DevKeyType.TEXT -> {
                val text = item.insertText.ifEmpty { item.label }
                currentInputConnection?.commitText(text, 1)
                modifierState.resetTransientModifiers()
            }
        }
        scheduleContextRefresh()
    }

    override fun onVoiceRequested() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        val voiceIme = imm.enabledInputMethodList.firstOrNull { it.id.contains("voice", true) || it.loadLabel(packageManager).toString().contains("voice", true) }
        if (voiceIme != null) try { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) switchInputMethod(voiceIme.id) } catch (_: Exception) { }
    }

    override fun onMedia(url: String) {
        if (url.isBlank()) return
        val ic = currentInputConnection ?: return
        val editorInfo = currentInputEditorInfo ?: return
        scope.launch(Dispatchers.IO) {
            try {
                val file = File(url)
                if (file.exists()) {
                    val uri = FileProvider.getUriForFile(this@FastCrazeInputMethodService, "com.example.fileprovider", file)
                    val info = InputContentInfoCompat(uri, ClipDescription("Media", arrayOf("image/*")), null)
                    InputConnectionCompat.commitContent(ic, editorInfo, info, InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION, null)
                }
            } catch (_: Exception) { }
        }
    }

    override fun onSettingsRequested() {
        startActivity(Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED })
    }

    override fun getCurrentContextText(): String = try { richConnection.getSelectedText()?.toString().orEmpty() } catch (_: Exception) { "" }

    override fun onReplaceContextText(newText: String) {
        currentInputConnection?.commitText(newText, 1)
        scheduleContextRefresh()
    }

    private fun saveLatestClipboard() {
        try {
            val manager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
            val clip = manager.primaryClip ?: return
            if (clip.itemCount > 0) {
                val text = clip.getItemAt(0).coerceToText(this).toString().trim()
                if (text.isNotEmpty() && text.length < 5000) scope.launch(Dispatchers.IO) {
                    try { database.clipboardDao().saveClip(text, preferences.clipboardUnpinnedLimit.coerceIn(5, 50)) } catch (_: Exception) { }
                }
            }
        } catch (_: Exception) { }
    }

    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
    }

    override fun onCreateInlineSuggestionsRequest(uiExtras: android.os.Bundle): android.view.inputmethod.InlineSuggestionsRequest? = null
    override fun onInlineSuggestionsResponse(response: android.view.inputmethod.InlineSuggestionsResponse): Boolean = false

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        (getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.removePrimaryClipChangedListener(clipboardListener)
        lifecycleOwner.onDestroy()
        richConnection.onDisconnect()
        scope.cancel()
        composeView = null
        super.onDestroy()
    }
}
