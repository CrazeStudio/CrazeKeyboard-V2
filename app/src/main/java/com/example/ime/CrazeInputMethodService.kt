package com.example.ime

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Build
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
import com.example.MainActivity
import com.example.core.AppDatabase
import com.example.core.SoundFeedbackHelper
import com.example.developer.DevKeyItem
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
 * CrazeKeyboard IME Service powered by AOSP LatinIME input architecture.
 *
 * Employs RichInputConnection and InputLogic for:
 * - High-speed synchronous key commits
 * - Low-latency startup and pre-warmed Compose view
 * - AOSP-grade nested batch edit safety
 * - Unicode surrogate-pair aware backspace
 * - Immediate auto-correction undo on backspace
 * - Automatic double-space-to-period replacement
 * - Reliable cursor tracking and external selection change reconciliation
 */
class CrazeInputMethodService : InputMethodService(), KeyboardActionListener {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val imeLifecycleOwner = ImeLifecycleOwner()

    private val database: AppDatabase by lazy { AppDatabase.getInstance(applicationContext) }
    private val preferences: KeyboardPreferences by lazy { KeyboardPreferences.getInstance(applicationContext) }
    private val suggestionEngine: SuggestionEngine by lazy {
        SuggestionEngine(applicationContext, database.personalDictionaryDao())
    }

    // AOSP LatinIME core components
    private val richImm: RichInputConnection by lazy { RichInputConnection(this) }
    private val inputLogic: InputLogic by lazy {
        InputLogic(
            richImm = richImm,
            preferences = preferences,
            suggestionEngine = suggestionEngine,
            scope = serviceScope
        ) { wordPrefix, sentenceStart, acNotice ->
            currentWordPrefix = wordPrefix
            isSentenceStart = sentenceStart
            isActualSentenceStart = sentenceStart
            autoCorrectNotice = acNotice
        }
    }

    // Observable states for CrazeKeyboardView
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

    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        saveLatestClipboard()
    }

    override fun onCreate() {
        super.onCreate()
        imeLifecycleOwner.onCreate()

        // Background non-critical initialization for rapid cold-start
        serviceScope.launch(Dispatchers.IO) {
            try {
                database.clipboardDao().trimUnpinned(10)
                com.example.emoji.EmojiData.allEmojis.size
            } catch (_: Exception) {}
        }

        // Warm up ComposeView on Main thread to avoid layout stutter
        serviceScope.launch(Dispatchers.Main) {
            try {
                if (cachedComposeView == null) {
                    cachedComposeView = createAndWarmUpComposeView()
                }
            } catch (_: Exception) {}
        }

        val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboardManager?.addPrimaryClipChangedListener(clipboardListener)
    }

    private fun createAndWarmUpComposeView(): ComposeView {
        return ComposeView(this).apply {
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
    }

    override fun onCreateInputView(): View {
        imeLifecycleOwner.onStart()

        val composeView = cachedComposeView ?: createAndWarmUpComposeView().also { cachedComposeView = it }

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

    override fun onEvaluateFullscreenMode(): Boolean {
        // Never enter fullscreen extract mode in landscape so custom keyboard is always visible
        return false
    }

    override fun onShowInputRequested(flags: Int, configChange: Boolean): Boolean {
        return true
    }

    override fun onEvaluateInputViewShown(): Boolean {
        return super.onEvaluateInputViewShown()
    }

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
        suggestionEngine.ensureLoaded()
        imeLifecycleOwner.onStart()
        richImm.onConnect(currentInputConnection)
        inputLogic.onStartInput(info)
        syncEditorFields()
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
        inputLogic.onStartInput(currentInputEditorInfo)
        syncEditorFields()
    }

    override fun onWindowHidden() {
        super.onWindowHidden()
        imeLifecycleOwner.onStop()
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
        autoCorrectNotice = inputLogic.autoCorrectNotice
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

        // Performance: Skip if cursor has not actually changed
        if (oldSelStart == newSelStart && oldSelEnd == newSelEnd) return

        // AOSP LatinIME cursor confirmation: check if selection update is self-generated echo
        val isEcho = richImm.onUpdateSelection(newSelStart, newSelEnd)
        if (!isEcho) {
            // External cursor movement (e.g. user tapped elsewhere in the text field)
            inputLogic.updateWordPrefixAndCaps()
            syncEditorFields()
        }
    }

    override fun onCreateInlineSuggestionsRequest(uiExtras: android.os.Bundle): android.view.inputmethod.InlineSuggestionsRequest? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val stylesBundle = android.os.Bundle()
            val inlinePresentationSpec = android.widget.inline.InlinePresentationSpec.Builder(
                android.util.Size(100, 36),
                android.util.Size(700, 48)
            ).setStyle(stylesBundle).build()

            return android.view.inputmethod.InlineSuggestionsRequest.Builder(listOf(inlinePresentationSpec))
                .setMaxSuggestionCount(3)
                .build()
        }
        return null
    }

    override fun onInlineSuggestionsResponse(response: android.view.inputmethod.InlineSuggestionsResponse): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val suggestions = response.inlineSuggestions
            if (suggestions.isEmpty()) {
                inlineSuggestionViews = emptyList()
                return true
            }
            val views = mutableListOf<View>()
            var count = 0
            for (suggestion in suggestions) {
                suggestion.inflate(this, android.util.Size(200, 36), mainExecutor) { view ->
                    if (view != null) {
                        views.add(view)
                    }
                    count++
                    if (count == suggestions.size) {
                        inlineSuggestionViews = views.toList()
                    }
                }
            }
            return true
        }
        return false
    }

    // KeyboardActionListener implementations delegating to AOSP LatinIME core

    override fun onText(text: String) {
        inputLogic.onTextInput(text)
        syncEditorFields()
    }

    override fun onReplaceCurrentWord(replacement: String) {
        inputLogic.onReplaceCurrentWord(replacement)
        syncEditorFields()
    }

    override fun onBackspace() {
        inputLogic.onBackspace()
        syncEditorFields()
    }

    override fun onDeleteWord() {
        inputLogic.onDeleteWord()
        syncEditorFields()
    }

    override fun onEnter() {
        inputLogic.onEnter()
        syncEditorFields()
    }

    override fun onMoveCursor(offset: Int) {
        inputLogic.onMoveCursor(offset)
        syncEditorFields()
    }

    override fun onSpecialKey(action: CursorAction) {
        if (action == CursorAction.TOGGLE_SELECT) {
            isSelectModeActive = !isSelectModeActive
        } else {
            inputLogic.onSpecialKey(action, isSelectModeActive)
        }
        syncEditorFields()
    }

    override fun onDevKey(item: DevKeyItem, modifierState: ModifierState) {
        inputLogic.onDevKey(item, modifierState)
        syncEditorFields()
    }

    override fun onVoiceRequested() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        val imes = imm.enabledInputMethodList
        val voiceIme = imes.firstOrNull {
            it.id.contains("voice", ignoreCase = true) ||
            it.loadLabel(packageManager).toString().contains("voice", ignoreCase = true)
        }

        if (voiceIme != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    switchInputMethod(voiceIme.id)
                } else {
                    @Suppress("DEPRECATION")
                    window?.window?.attributes?.token?.let { token ->
                        imm.setInputMethod(token, voiceIme.id)
                    }
                }
            } catch (e: Exception) {
                android.widget.Toast.makeText(this, "Voice input switch failed", android.widget.Toast.LENGTH_SHORT).show()
            }
        } else {
            android.widget.Toast.makeText(this, "No voice input engine detected on this device", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    override fun onMedia(url: String) {
        // Local offline media only
        if (url.isBlank()) return
        val ic = currentInputConnection ?: return
        val editorInfo = currentInputEditorInfo ?: return

        serviceScope.launch(Dispatchers.IO) {
            try {
                val file = File(url)
                if (file.exists()) {
                    val uri = FileProvider.getUriForFile(this@CrazeInputMethodService, "com.example.fileprovider", file)
                    val contentInfo = InputContentInfoCompat(uri, ClipDescription("Media", arrayOf("image/*")), null)
                    val flags = InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION
                    InputConnectionCompat.commitContent(ic, editorInfo, contentInfo, flags, null)
                }
            } catch (_: Exception) {}
        }
    }

    override fun onSettingsRequested() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        }
        startActivity(intent)
    }

    override fun getCurrentContextText(): String {
        val selected = richImm.getSelectedText()
        if (selected.isNotBlank()) return selected.trim()
        val textBefore = richImm.getTextBeforeCursor(2000, 0)
        return textBefore.trim()
    }

    override fun onReplaceContextText(newText: String) {
        val selected = richImm.getSelectedText()
        richImm.beginBatchEdit()
        try {
            if (selected.isNotBlank()) {
                richImm.commitText(newText)
            } else {
                val textBefore = richImm.getTextBeforeCursor(2000, 0)
                if (textBefore.isNotEmpty()) {
                    richImm.deleteTextBeforeCursor(textBefore.length)
                }
                richImm.commitText(newText)
            }
        } finally {
            richImm.endBatchEdit()
        }
        inputLogic.updateWordPrefixAndCaps()
        syncEditorFields()
    }

    private fun saveLatestClipboard() {
        try {
            val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
            val clip = clipboardManager.primaryClip ?: return
            if (clip.itemCount > 0) {
                val text = clip.getItemAt(0).coerceToText(this).toString().trim()
                if (text.isNotEmpty() && text.length < 5000) {
                    serviceScope.launch(Dispatchers.IO) {
                        try {
                            val limit = preferences.clipboardUnpinnedLimit.coerceIn(5, 50)
                            database.clipboardDao().saveClip(text, unpinnedLimit = limit)
                        } catch (_: Exception) {}
                    }
                }
            }
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboardManager?.removePrimaryClipChangedListener(clipboardListener)
        imeLifecycleOwner.onDestroy()
        serviceScope.cancel()
        suggestionEngine.close()
        cachedComposeView = null
        richImm.onDisconnect()
        super.onDestroy()
    }
}
