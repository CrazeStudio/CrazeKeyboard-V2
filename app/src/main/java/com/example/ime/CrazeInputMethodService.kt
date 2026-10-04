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
import com.android.inputmethod.latin.inputlogic.InputLogic
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

/** CrazeKeyboard IME service. Input commits stay independent from Compose/UI synchronization. */
class CrazeInputMethodService : InputMethodService(), KeyboardActionListener {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val imeLifecycleOwner = ImeLifecycleOwner()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val database: AppDatabase by lazy { AppDatabase.getInstance(applicationContext) }
    private val preferences: KeyboardPreferences by lazy { KeyboardPreferences.getInstance(applicationContext) }
    private val suggestionEngine: SuggestionEngine by lazy { SuggestionEngine(applicationContext, database.personalDictionaryDao()) }
    private val richImm: RichInputConnection by lazy { RichInputConnection(this) }
    private val inputLogic: InputLogic by lazy {
        val logic = InputLogic(richImm)
        logic.setDoubleSpacePeriodEnabled(preferences.isDoubleSpacePeriodEnabled)
        logic.setListener(object : InputLogic.Listener {
            override fun onWordComposed(word: String) {
                // Never mutate Compose state synchronously from the character hot path.
                requestUiSync()
            }
            override fun onAutoCorrectChanged(original: String?, correction: String?) {
                requestUiSync()
            }
            override fun onCapsModeChanged(sentenceStart: Boolean) {
                requestUiSync()
            }
        })
        logic
    }

    private var currentWordPrefix by mutableStateOf("")
    private var isSentenceStart by mutableStateOf(true)
    private var isActualSentenceStart by mutableStateOf(true)
    private var isPreviousUpper by mutableStateOf(false)
    private var isSelectModeActive by mutableStateOf(false)
    private var currentImeAction by mutableStateOf(EditorInfo.IME_ACTION_NONE)
    private var inlineSuggestionViews by mutableStateOf<List<View>>(emptyList())
    private var isPasswordField by mutableStateOf(false)
    private var isEmailField by mutableStateOf(false)
    private var isUriField by mutableStateOf(false)
    private var isNumericField by mutableStateOf(false)
    private var isMultilineField by mutableStateOf(false)
    private var autoCorrectNotice by mutableStateOf<Pair<String, String>?>(null)
    private var cachedComposeView: ComposeView? = null
    private var uiSyncPending = false
    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener { saveLatestClipboard() }

    override fun onCreate() {
        super.onCreate()
        imeLifecycleOwner.onCreate()
        // Do not eagerly create the entire Compose keyboard or load emoji data during IME startup.
        // Optional data is loaded when its UI is actually opened.
        (getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.addPrimaryClipChangedListener(clipboardListener)
    }

    private fun createKeyboardView(): ComposeView = ComposeView(this).apply {
        isHapticFeedbackEnabled = true
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnLifecycleDestroyed(imeLifecycleOwner))
        setViewTreeLifecycleOwner(imeLifecycleOwner)
        setViewTreeSavedStateRegistryOwner(imeLifecycleOwner)
        setViewTreeViewModelStoreOwner(imeLifecycleOwner)
        setContent {
            CrazeKeyboardView(
                context = this@CrazeInputMethodService,
                preferences = preferences,
                clipboardDao = database.clipboardDao(),
                suggestionEngine = suggestionEngine,
                currentWordPrefix = currentWordPrefix,
                isSentenceStart = isSentenceStart,
                isActualSentenceStart = isActualSentenceStart,
                isPreviousUpper = isPreviousUpper,
                isSelectModeActive = isSelectModeActive,
                imeAction = currentImeAction,
                inlineSuggestionViews = inlineSuggestionViews,
                isPasswordField = isPasswordField,
                isEmailField = isEmailField,
                isUriField = isUriField,
                isNumericField = isNumericField,
                isMultilineField = isMultilineField,
                listener = this@CrazeInputMethodService
            )
        }
    }

    override fun onCreateInputView(): View {
        imeLifecycleOwner.onStart()
        val composeView = cachedComposeView ?: createKeyboardView().also { cachedComposeView = it }
        window?.window?.let { win ->
            win.addFlags(android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
            win.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            win.decorView.isHapticFeedbackEnabled = true
            win.decorView.setViewTreeLifecycleOwner(imeLifecycleOwner)
            win.decorView.setViewTreeSavedStateRegistryOwner(imeLifecycleOwner)
            win.decorView.setViewTreeViewModelStoreOwner(imeLifecycleOwner)
        }
        return composeView
    }

    override fun onEvaluateFullscreenMode(): Boolean = false
    override fun onShowInputRequested(flags: Int, configChange: Boolean): Boolean = true
    override fun onEvaluateInputViewShown(): Boolean = super.onEvaluateInputViewShown()

    override fun onStartInput(info: EditorInfo?, restarting: Boolean) {
        super.onStartInput(info, restarting)
        SoundFeedbackHelper.getInstance(this).refreshVolume()
        richImm.onConnect(currentInputConnection)
        inputLogic.onStartInput(info)
        syncEditorFields()
    }

    override fun onFinishInput() {
        super.onFinishInput()
        inputLogic.onFinishInput()
        richImm.onDisconnect()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        // Loading suggestions is intentionally outside the character insertion path.
        serviceScope.launch(Dispatchers.Default) { suggestionEngine.ensureLoaded() }
        imeLifecycleOwner.onStart()
        richImm.onConnect(currentInputConnection)
        inputLogic.updateWordPrefixAndCaps()
        requestUiSync()
        inlineSuggestionViews = emptyList()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        imeLifecycleOwner.onStop()
        inlineSuggestionViews = emptyList()
    }

    override fun onWindowShown() {
        super.onWindowShown()
        imeLifecycleOwner.onStart()
        richImm.onConnect(currentInputConnection)
        inputLogic.updateWordPrefixAndCaps()
        requestUiSync()
    }

    override fun onWindowHidden() { super.onWindowHidden(); imeLifecycleOwner.onStop() }

    /** Coalesces rapid state changes so a burst of key presses does not trigger one recomposition per key. */
    private fun requestUiSync() {
        if (uiSyncPending) return
        uiSyncPending = true
        mainHandler.postDelayed({
            uiSyncPending = false
            if (!isDestroyed) syncEditorFields()
        }, 12L)
    }

    private fun syncEditorFields() {
        isPasswordField = inputLogic.isPasswordField
        isEmailField = inputLogic.isEmailField
        isUriField = inputLogic.isUriField
        isNumericField = inputLogic.isNumericField
        isMultilineField = inputLogic.isMultilineField
        currentImeAction = inputLogic.currentImeAction
        currentWordPrefix = inputLogic.currentWordPrefix
        isSentenceStart = inputLogic.isSentenceStart
        isActualSentenceStart = inputLogic.isSentenceStart
        val notice = inputLogic.autoCorrectNotice
        autoCorrectNotice = if (notice != null && notice.first != null && notice.second != null) Pair(notice.first, notice.second) else null
    }

    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        if (oldSelStart == newSelStart && oldSelEnd == newSelEnd) return
        if (!richImm.onUpdateSelection(newSelStart, newSelEnd)) {
            inputLogic.updateWordPrefixAndCaps()
            requestUiSync()
        }
    }

    override fun onCreateInlineSuggestionsRequest(uiExtras: android.os.Bundle): android.view.inputmethod.InlineSuggestionsRequest? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val spec = android.widget.inline.InlinePresentationSpec.Builder(android.util.Size(100, 36), android.util.Size(700, 48)).setStyle(android.os.Bundle()).build()
        return android.view.inputmethod.InlineSuggestionsRequest.Builder(listOf(spec)).setMaxSuggestionCount(3).build()
    }

    override fun onInlineSuggestionsResponse(response: android.view.inputmethod.InlineSuggestionsResponse): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        val suggestions = response.inlineSuggestions
        if (suggestions.isEmpty()) { inlineSuggestionViews = emptyList(); return true }
        val views = mutableListOf<View>()
        var count = 0
        for (suggestion in suggestions) {
            suggestion.inflate(this, android.util.Size(200, 36), mainExecutor) { view ->
                if (view != null) views.add(view)
                count++
                if (count == suggestions.size) inlineSuggestionViews = views.toList()
            }
        }
        return true
    }

    // These callbacks deliberately do not synchronously synchronize the Compose tree.
    override fun onText(text: String) { inputLogic.onTextInput(text) }
    override fun onReplaceCurrentWord(replacement: String) { inputLogic.onReplaceCurrentWord(replacement); requestUiSync() }
    override fun onBackspace() { inputLogic.onBackspace(); requestUiSync() }
    override fun onDeleteWord() { inputLogic.onDeleteWord(); requestUiSync() }
    override fun onEnter() { inputLogic.onEnter(); requestUiSync() }
    override fun onMoveCursor(offset: Int) { inputLogic.onMoveCursor(offset); requestUiSync() }

    override fun onSpecialKey(action: CursorAction) {
        if (action == CursorAction.TOGGLE_SELECT) {
            isSelectModeActive = !isSelectModeActive
        } else {
            handleSpecialKey(action, isSelectModeActive)
        }
        requestUiSync()
    }

    private fun handleSpecialKey(action: CursorAction, selectMode: Boolean) {
        when (action) {
            CursorAction.LEFT -> if (selectMode) richImm.sendKeyWithMeta(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.META_SHIFT_ON) else richImm.sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_LEFT)
            CursorAction.RIGHT -> if (selectMode) richImm.sendKeyWithMeta(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.META_SHIFT_ON) else richImm.sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_RIGHT)
            CursorAction.UP -> if (selectMode) richImm.sendKeyWithMeta(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.META_SHIFT_ON) else richImm.sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_UP)
            CursorAction.DOWN -> if (selectMode) richImm.sendKeyWithMeta(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.META_SHIFT_ON) else richImm.sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_DOWN)
            CursorAction.HOME, CursorAction.START -> if (selectMode) richImm.sendKeyWithMeta(KeyEvent.KEYCODE_MOVE_HOME, KeyEvent.META_SHIFT_ON) else richImm.sendDownUpKeyEvents(KeyEvent.KEYCODE_MOVE_HOME)
            CursorAction.END -> if (selectMode) richImm.sendKeyWithMeta(KeyEvent.KEYCODE_MOVE_END, KeyEvent.META_SHIFT_ON) else richImm.sendDownUpKeyEvents(KeyEvent.KEYCODE_MOVE_END)
            CursorAction.SELECT_ALL -> richImm.performContextMenuAction(android.R.id.selectAll)
            CursorAction.CUT -> richImm.performContextMenuAction(android.R.id.cut)
            CursorAction.COPY -> richImm.performContextMenuAction(android.R.id.copy)
            CursorAction.PASTE -> richImm.performContextMenuAction(android.R.id.paste)
            CursorAction.DELETE -> richImm.sendDownUpKeyEvents(KeyEvent.KEYCODE_FORWARD_DEL)
            CursorAction.CLEAR -> {
                richImm.beginBatchEdit()
                try { richImm.deleteTextBeforeCursor(2000) } finally { richImm.endBatchEdit() }
            }
            CursorAction.TOGGLE_SELECT -> Unit
        }
        inputLogic.updateWordPrefixAndCaps()
    }

    override fun onDevKey(item: DevKeyItem, modifierState: ModifierState) {
        handleDevKey(item, modifierState)
        requestUiSync()
    }

    private fun handleDevKey(item: DevKeyItem, modifierState: ModifierState) {
        when (item.type) {
            DevKeyType.ESC -> richImm.sendDownUpKeyEvents(KeyEvent.KEYCODE_ESCAPE)
            DevKeyType.TAB -> richImm.sendDownUpKeyEvents(KeyEvent.KEYCODE_TAB)
            DevKeyType.ARROW_LEFT -> richImm.sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_LEFT)
            DevKeyType.ARROW_RIGHT -> richImm.sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_RIGHT)
            DevKeyType.ARROW_UP -> richImm.sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_UP)
            DevKeyType.ARROW_DOWN -> richImm.sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_DOWN)
            DevKeyType.HOME -> richImm.sendDownUpKeyEvents(KeyEvent.KEYCODE_MOVE_HOME)
            DevKeyType.END -> richImm.sendDownUpKeyEvents(KeyEvent.KEYCODE_MOVE_END)
            DevKeyType.CTRL -> modifierState.toggleCtrl()
            DevKeyType.ALT -> modifierState.toggleAlt()
            DevKeyType.TEXT -> {
                val text = item.insertText.ifEmpty { item.label }
                val metaState = (if (modifierState.isCtrlActive) KeyEvent.META_CTRL_ON else 0) or
                    (if (modifierState.isAltActive) KeyEvent.META_ALT_ON else 0) or
                    (if (modifierState.isShiftActive) KeyEvent.META_SHIFT_ON else 0)
                if (metaState != 0 && text.length == 1) {
                    val ch = text[0].lowercaseChar()
                    val keyCode = when (ch) {
                        in 'a'..'z' -> KeyEvent.KEYCODE_A + (ch - 'a')
                        in '0'..'9' -> KeyEvent.KEYCODE_0 + (ch - '0')
                        else -> 0
                    }
                    if (keyCode != 0) {
                        richImm.sendKeyWithMeta(keyCode, metaState)
                        modifierState.resetTransientModifiers()
                        return
                    }
                }
                richImm.commitText(text, 1)
                modifierState.resetTransientModifiers()
            }
        }
        inputLogic.updateWordPrefixAndCaps()
    }

    override fun onVoiceRequested() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        val voiceIme = imm.enabledInputMethodList.firstOrNull { it.id.contains("voice", true) || it.loadLabel(packageManager).toString().contains("voice", true) }
        if (voiceIme != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) switchInputMethod(voiceIme.id)
                else { @Suppress("DEPRECATION") window?.window?.attributes?.token?.let { imm.setInputMethod(it, voiceIme.id) } }
            } catch (_: Exception) { android.widget.Toast.makeText(this, "Voice input switch failed", android.widget.Toast.LENGTH_SHORT).show() }
        } else android.widget.Toast.makeText(this, "No voice input engine detected on this device", android.widget.Toast.LENGTH_SHORT).show()
    }

    override fun onMedia(url: String) {
        if (url.isBlank()) return
        val ic = currentInputConnection ?: return
        val editorInfo = currentInputEditorInfo ?: return
        serviceScope.launch(Dispatchers.IO) {
            try {
                val file = File(url)
                if (file.exists()) {
                    val uri = FileProvider.getUriForFile(this@CrazeInputMethodService, "com.example.fileprovider", file)
                    val contentInfo = InputContentInfoCompat(uri, ClipDescription("Media", arrayOf("image/*")), null)
                    InputConnectionCompat.commitContent(ic, editorInfo, contentInfo, InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION, null)
                }
            } catch (_: Exception) {}
        }
    }

    override fun onSettingsRequested() {
        startActivity(Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED })
    }

    override fun getCurrentContextText(): String {
        val selected = richImm.getSelectedText().toString()
        return if (selected.isNotBlank()) selected.trim() else richImm.getTextBeforeCursor(2000, 0).toString().trim()
    }

    override fun onReplaceContextText(newText: String) {
        val selected = richImm.getSelectedText().toString()
        richImm.beginBatchEdit()
        try {
            if (selected.isNotBlank()) {
                richImm.commitText(newText, 1)
            } else {
                val textBefore = richImm.getTextBeforeCursor(2000, 0).toString()
                if (textBefore.isNotEmpty()) richImm.deleteTextBeforeCursor(textBefore.length)
                richImm.commitText(newText, 1)
            }
        } finally {
            richImm.endBatchEdit()
        }
        inputLogic.updateWordPrefixAndCaps()
        requestUiSync()
    }

    private fun saveLatestClipboard() {
        try {
            val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
            val clip = clipboardManager.primaryClip ?: return
            if (clip.itemCount > 0) {
                val text = clip.getItemAt(0).coerceToText(this).toString().trim()
                if (text.isNotEmpty() && text.length < 5000) serviceScope.launch(Dispatchers.IO) {
                    try { database.clipboardDao().saveClip(text, unpinnedLimit = preferences.clipboardUnpinnedLimit.coerceIn(5, 50)) } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        (getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.removePrimaryClipChangedListener(clipboardListener)
        imeLifecycleOwner.onDestroy()
        serviceScope.cancel()
        suggestionEngine.close()
        cachedComposeView = null
        richImm.onDisconnect()
        super.onDestroy()
    }
}
