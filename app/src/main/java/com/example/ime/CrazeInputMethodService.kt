package com.example.ime

import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import android.content.ClipDescription
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.MainActivity
import com.example.clipboard.ClipboardItemEntity
import com.example.core.AppDatabase
import com.example.developer.DevKeyItem
import com.example.developer.DevKeyType
import com.example.developer.ModifierState
import com.example.core.SoundFeedbackHelper
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
import kotlinx.coroutines.withContext

class CrazeInputMethodService : InputMethodService(), KeyboardActionListener {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val imeLifecycleOwner = ImeLifecycleOwner()

    private val database: AppDatabase by lazy { AppDatabase.getInstance(applicationContext) }
    private val preferences: KeyboardPreferences by lazy { KeyboardPreferences.getInstance(applicationContext) }
    private val suggestionEngine: SuggestionEngine by lazy { 
        SuggestionEngine(applicationContext, database.personalDictionaryDao()) 
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

    private var cachedComposeView: ComposeView? = null

    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        saveLatestClipboard()
    }

    override fun onCreate() {
        super.onCreate()
        imeLifecycleOwner.onCreate()

        // Defer non-critical startup work to background
        serviceScope.launch(Dispatchers.IO) {
            try {
                database.clipboardDao().trimUnpinned(10)
                // Warm up emoji data for fast loading later
                com.example.emoji.EmojiData.allEmojis.size
                com.example.emoji.LocalEmojiAiEngine.predictEmojisForText("warmup")
            } catch (_: Exception) {}
        }

        // Pre-create and warm up the ComposeView asynchronously to prevent blocking IME service startup
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

    private fun evaluateEditorInfo(info: EditorInfo?) {
        if (info == null) {
            isPasswordField = false
            isEmailField = false
            isUriField = false
            isNumericField = false
            isMultilineField = false
            currentImeAction = EditorInfo.IME_ACTION_NONE
            return
        }

        currentImeAction = info.imeOptions and EditorInfo.IME_MASK_ACTION
        val inputType = info.inputType
        val inputClass = inputType and android.text.InputType.TYPE_MASK_CLASS
        val variation = inputType and android.text.InputType.TYPE_MASK_VARIATION

        isPasswordField = (inputClass == android.text.InputType.TYPE_CLASS_TEXT && (
            variation == android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
        )) || (inputClass == android.text.InputType.TYPE_CLASS_NUMBER && (
            variation == android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
        ))

        isEmailField = inputClass == android.text.InputType.TYPE_CLASS_TEXT && (
            variation == android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
            variation == android.text.InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
        )

        isUriField = inputClass == android.text.InputType.TYPE_CLASS_TEXT && (
            variation == android.text.InputType.TYPE_TEXT_VARIATION_URI
        )

        isNumericField = inputClass == android.text.InputType.TYPE_CLASS_NUMBER ||
            inputClass == android.text.InputType.TYPE_CLASS_PHONE ||
            inputClass == android.text.InputType.TYPE_CLASS_DATETIME

        isMultilineField = (inputType and android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0
    }

    override fun onStartInput(info: EditorInfo?, restarting: Boolean) {
        super.onStartInput(info, restarting)
        SoundFeedbackHelper.getInstance(this).refreshVolume()
        evaluateEditorInfo(info)
        updateCurrentWordPrefix()
    }

    override fun onFinishInput() {
        super.onFinishInput()
        currentWordPrefix = ""
        isSentenceStart = true
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        suggestionEngine.ensureLoaded()
        imeLifecycleOwner.onStart()
        evaluateEditorInfo(info)
        inlineSuggestionViews = emptyList()
        updateCurrentWordPrefix()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        imeLifecycleOwner.onStop()
        inlineSuggestionViews = emptyList()
    }

    override fun onWindowShown() {
        super.onWindowShown()
        imeLifecycleOwner.onStart()
        currentImeAction = currentInputEditorInfo?.imeOptions ?: EditorInfo.IME_ACTION_NONE
        updateCurrentWordPrefix()
    }

    override fun onWindowHidden() {
        super.onWindowHidden()
        imeLifecycleOwner.onStop()
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

    private var lastLocalActionTime = 0L
    private var lastObservedSelectionStart = -1
    private var lastObservedSelectionEnd = -1

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        
        lastObservedSelectionStart = newSelStart
        lastObservedSelectionEnd = newSelEnd

        // Performance: Skip prefix update if the selection hasn't actually moved (e.g. cursor blink)
        if (oldSelStart == newSelStart && oldSelEnd == newSelEnd) return

        // Debounce selection updates caused by our own keystrokes to prevent prefix-flicker
        val now = System.currentTimeMillis()
        if (now - lastLocalActionTime < 60L) {
            return
        }
        
        updateCurrentWordPrefix()
    }

    private fun updateCurrentWordPrefix() {
        val ic = currentInputConnection ?: return
        
        // Performance: Reduce character lookback for prefix detection to improve speed
        // 20 characters is enough for any reasonable prefix/word detection
        val textBefore = ic.getTextBeforeCursor(20, 0)?.toString() ?: ""
        
        val lastWord = textBefore.takeLastWhile { !it.isWhitespace() && it.isLetterOrDigit() }
        
        if (currentWordPrefix != lastWord) {
            currentWordPrefix = lastWord
        }
        
        val prevChar = textBefore.lastOrNull()
        val newIsPreviousUpper = prevChar != null && prevChar.isUpperCase()
        if (isPreviousUpper != newIsPreviousUpper) {
            isPreviousUpper = newIsPreviousUpper
        }

        // Optimized sentence detection: check only the last 3 characters
        val newIsActualSentenceStart = if (textBefore.isEmpty()) {
            true
        } else {
            val trimmed = textBefore.trimEnd()
            if (trimmed.isEmpty()) {
                true
            } else {
                val lastChar = trimmed.last()
                (lastChar == '.' || lastChar == '!' || lastChar == '?' || lastChar == '\n')
            }
        }
        if (isActualSentenceStart != newIsActualSentenceStart) {
            isActualSentenceStart = newIsActualSentenceStart
        }

        val editorInfo = currentInputEditorInfo
        val capsMode = if (editorInfo != null && !isEmailField && !isUriField && !isPasswordField) {
            ic.getCursorCapsMode(editorInfo.inputType)
        } else 0

        val newIsSentenceStart = if (isEmailField || isUriField || isPasswordField) {
            false
        } else if (capsMode != 0) {
            true
        } else {
            newIsActualSentenceStart
        }
        if (isSentenceStart != newIsSentenceStart) {
            isSentenceStart = newIsSentenceStart
        }
    }

    override fun onText(text: String) {
        val ic = currentInputConnection ?: return
        lastLocalActionTime = System.currentTimeMillis()
        
        ic.beginBatchEdit()
        try {
            ic.commitText(text, 1)
            if (text.length == 1) {
                val c = text[0]
                if (c.isLetterOrDigit()) {
                    currentWordPrefix += text
                    isPreviousUpper = c.isUpperCase()
                    isSentenceStart = false
                    isActualSentenceStart = false
                } else {
                    currentWordPrefix = ""
                    isPreviousUpper = false
                    val isStop = (c == '.' || c == '!' || c == '?' || c == '\n')
                    isSentenceStart = isStop
                    isActualSentenceStart = isStop
                }
            } else {
                currentWordPrefix = ""
                isPreviousUpper = false
                isSentenceStart = false
                isActualSentenceStart = false
            }
        } finally {
            ic.endBatchEdit()
        }
    }

    override fun onReplaceCurrentWord(replacement: String) {
        val ic = currentInputConnection ?: return
        lastLocalActionTime = System.currentTimeMillis()
        
        ic.beginBatchEdit()
        try {
            // Verify the prefix is still what we think it is before deleting
            val textBefore = ic.getTextBeforeCursor(currentWordPrefix.length, 0)?.toString() ?: ""
            if (textBefore == currentWordPrefix) {
                ic.deleteSurroundingText(currentWordPrefix.length, 0)
            } else {
                // If text changed externally, find the actual word to replace
                val actualBefore = ic.getTextBeforeCursor(20, 0)?.toString() ?: ""
                val actualWord = actualBefore.takeLastWhile { it.isLetterOrDigit() }
                if (actualWord.isNotEmpty()) {
                    ic.deleteSurroundingText(actualWord.length, 0)
                }
            }
            ic.commitText(replacement, 1)
        } catch (_: Exception) {
        } finally {
            ic.endBatchEdit()
        }
        
        currentWordPrefix = ""
        isPreviousUpper = false
        isSentenceStart = false
        isActualSentenceStart = false
    }

    override fun onBackspace() {
        val ic = currentInputConnection ?: return
        lastLocalActionTime = System.currentTimeMillis()
        
        ic.beginBatchEdit()
        try {
            // Standard backspace handling. If selection exists, it deletes it.
            // Using KEYCODE_DEL is robust for most editors.
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        } finally {
            ic.endBatchEdit()
        }
        
        // We'll let onUpdateSelection resync the prefix to be safe after a backspace
    }

    override fun onDeleteWord() {
        val ic = currentInputConnection ?: return
        lastLocalActionTime = System.currentTimeMillis()
        ic.beginBatchEdit()
        try {
            val textBefore = ic.getTextBeforeCursor(50, 0)?.toString() ?: ""
            if (textBefore.isNotEmpty()) {
                var deleteCount = 0
                var i = textBefore.length - 1
                // Skip trailing whitespace
                while (i >= 0 && textBefore[i].isWhitespace()) {
                    deleteCount++
                    i--
                }
                // Delete the word
                while (i >= 0 && !textBefore[i].isWhitespace()) {
                    deleteCount++
                    i--
                }
                if (deleteCount > 0) {
                    ic.deleteSurroundingText(deleteCount, 0)
                }
            }
        } catch (_: Exception) {
        } finally {
            ic.endBatchEdit()
        }
        currentWordPrefix = ""
    }

    override fun onEnter() {
        val ic = currentInputConnection ?: return
        val editorInfo = currentInputEditorInfo
        val action = if (editorInfo != null) {
            editorInfo.imeOptions and EditorInfo.IME_MASK_ACTION
        } else EditorInfo.IME_ACTION_NONE
        
        ic.beginBatchEdit()
        try {
            if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
                ic.performEditorAction(action)
            } else {
                sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
            }
        } finally {
            ic.endBatchEdit()
        }
    }

    override fun onMoveCursor(offset: Int) {
        val ic = currentInputConnection ?: return
        lastLocalActionTime = System.currentTimeMillis()
        if (offset > 0) {
            repeat(offset) { sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_RIGHT) }
        } else if (offset < 0) {
            repeat(-offset) { sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_LEFT) }
        }
    }

    override fun onSpecialKey(action: CursorAction) {
        val ic = currentInputConnection ?: return
        lastLocalActionTime = System.currentTimeMillis()
        when (action) {
            CursorAction.TOGGLE_SELECT -> {
                isSelectModeActive = !isSelectModeActive
            }
            CursorAction.LEFT -> {
                if (isSelectModeActive) {
                    sendKeyWithMeta(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.META_SHIFT_ON)
                } else {
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_LEFT)
                }
            }
            CursorAction.RIGHT -> {
                if (isSelectModeActive) {
                    sendKeyWithMeta(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.META_SHIFT_ON)
                } else {
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_RIGHT)
                }
            }
            CursorAction.UP -> {
                if (isSelectModeActive) {
                    sendKeyWithMeta(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.META_SHIFT_ON)
                } else {
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_UP)
                }
            }
            CursorAction.DOWN -> {
                if (isSelectModeActive) {
                    sendKeyWithMeta(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.META_SHIFT_ON)
                } else {
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_DOWN)
                }
            }
            CursorAction.HOME -> {
                if (isSelectModeActive) {
                    sendKeyWithMeta(KeyEvent.KEYCODE_MOVE_HOME, KeyEvent.META_SHIFT_ON)
                } else {
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_MOVE_HOME)
                }
            }
            CursorAction.END -> {
                if (isSelectModeActive) {
                    sendKeyWithMeta(KeyEvent.KEYCODE_MOVE_END, KeyEvent.META_SHIFT_ON)
                } else {
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_MOVE_END)
                }
            }
            CursorAction.SELECT_ALL -> {
                ic.performContextMenuAction(android.R.id.selectAll)
            }
            CursorAction.CUT -> {
                ic.performContextMenuAction(android.R.id.cut)
                isSelectModeActive = false
            }
            CursorAction.COPY -> {
                ic.performContextMenuAction(android.R.id.copy)
                isSelectModeActive = false
            }
            CursorAction.PASTE -> {
                ic.performContextMenuAction(android.R.id.paste)
            }
            CursorAction.DELETE -> {
                ic.beginBatchEdit()
                try {
                    val sel = ic.getSelectedText(0)
                    if (!sel.isNullOrEmpty()) {
                        ic.commitText("", 1)
                    } else {
                        ic.deleteSurroundingText(0, 1) // Forward delete
                    }
                } finally {
                    ic.endBatchEdit()
                }
            }
            else -> {}
        }
    }

    private fun sendKeyWithMeta(keyCode: Int, metaState: Int) {
        val ic = currentInputConnection ?: return
        val now = System.currentTimeMillis()
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, metaState))
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0, metaState))
    }

    override fun onDevKey(item: DevKeyItem, modifierState: ModifierState) {
        val ic = currentInputConnection ?: return
        lastLocalActionTime = System.currentTimeMillis()
        
        var meta = 0
        if (modifierState.isCtrlActive) meta = meta or KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        if (modifierState.isAltActive) meta = meta or KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
        if (modifierState.isShiftActive) meta = meta or KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON

        when (item.type) {
            DevKeyType.ESC -> sendKeyWithMeta(KeyEvent.KEYCODE_ESCAPE, meta)
            DevKeyType.TAB -> sendKeyWithMeta(KeyEvent.KEYCODE_TAB, meta)
            DevKeyType.ARROW_LEFT -> sendKeyWithMeta(KeyEvent.KEYCODE_DPAD_LEFT, meta)
            DevKeyType.ARROW_RIGHT -> sendKeyWithMeta(KeyEvent.KEYCODE_DPAD_RIGHT, meta)
            DevKeyType.ARROW_UP -> sendKeyWithMeta(KeyEvent.KEYCODE_DPAD_UP, meta)
            DevKeyType.ARROW_DOWN -> sendKeyWithMeta(KeyEvent.KEYCODE_DPAD_DOWN, meta)
            DevKeyType.HOME -> sendKeyWithMeta(KeyEvent.KEYCODE_MOVE_HOME, meta)
            DevKeyType.END -> sendKeyWithMeta(KeyEvent.KEYCODE_MOVE_END, meta)
            DevKeyType.TEXT -> {
                if (modifierState.isCtrlActive) {
                    when (item.label.lowercase()) {
                        "c" -> ic.performContextMenuAction(android.R.id.copy)
                        "v" -> ic.performContextMenuAction(android.R.id.paste)
                        "x" -> ic.performContextMenuAction(android.R.id.cut)
                        "a" -> ic.performContextMenuAction(android.R.id.selectAll)
                        "z" -> ic.performContextMenuAction(android.R.id.undo)
                        "y" -> ic.performContextMenuAction(android.R.id.redo)
                        else -> ic.commitText(item.insertText, 1)
                    }
                } else {
                    ic.commitText(item.insertText, 1)
                }
            }
            else -> {}
        }
    }

    override fun onVoiceRequested() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        // In production, we'd use recognizer intent or a specific voice IME package if known.
        // For now, we'll try to find any IME with "voice" in its ID or name.
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
                    // Fallback for older versions
                    @Suppress("DEPRECATION")
                    window.window?.attributes?.token?.let { token ->
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
        if (com.example.core.NetworkCheck.enforceOfflineMode(this)) {
            android.widget.Toast.makeText(this, "Offline Mode: Media downloads disabled", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val ic = currentInputConnection ?: return
        val editorInfo = currentInputEditorInfo ?: return

        serviceScope.launch(Dispatchers.IO) {
            try {
                val gifsDir = File(cacheDir, "gifs")
                if (!gifsDir.exists()) gifsDir.mkdirs()
                
                // Use unique filename to avoid races
                val timestamp = System.currentTimeMillis()
                val gifFile = File(gifsDir, "gif_$timestamp.gif")
                
                URL(url).openStream().use { input ->
                    gifFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                
                val uri = FileProvider.getUriForFile(this@CrazeInputMethodService, "com.example.fileprovider", gifFile)
                val contentInfo = InputContentInfoCompat(uri, ClipDescription("GIF", arrayOf("image/gif")), null)
                
                val flags = InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION
                val success = InputConnectionCompat.commitContent(ic, editorInfo, contentInfo, flags, null)
                
                if (!success) {
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(this@CrazeInputMethodService, "App does not support GIF insertion", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
                
                // Optional: Clean up old files in background
                gifsDir.listFiles()?.forEach { file ->
                    if (file.lastModified() < System.currentTimeMillis() - 3600000) { // 1 hour old
                        file.delete()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(this@CrazeInputMethodService, "Failed to send GIF", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    override fun onSettingsRequested() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        }
        startActivity(intent)
    }

    override fun onAiPolishRequested() {
        if (!preferences.isAiFeatureEnabled) return
        val ic = currentInputConnection ?: return
        // Get up to 500 characters before cursor to polish the current context
        val textBefore = ic.getTextBeforeCursor(500, 0)?.toString() ?: ""
        if (textBefore.isBlank()) return

        serviceScope.launch(Dispatchers.Main) {
            val polished = com.example.ai.AiRepository.polishText(textBefore)
            if (polished.isNotBlank() && !polished.startsWith("Error:") && polished != "API Key not configured.") {
                ic.beginBatchEdit()
                ic.deleteSurroundingText(textBefore.length, 0)
                ic.commitText(polished, 1)
                ic.endBatchEdit()
            }
        }
    }

    override fun onAiChatRequested(prompt: String) {
        // This can be used if we want to trigger chat from elsewhere
    }

    override fun getCurrentContextText(): String {
        val ic = currentInputConnection ?: return ""
        val selected = ic.getSelectedText(0)?.toString()
        if (!selected.isNullOrBlank()) return selected.trim()
        val textBefore = ic.getTextBeforeCursor(2000, 0)?.toString() ?: ""
        return textBefore.trim()
    }

    override fun onReplaceContextText(newText: String) {
        val ic = currentInputConnection ?: return
        val selected = ic.getSelectedText(0)?.toString()
        ic.beginBatchEdit()
        if (!selected.isNullOrBlank()) {
            ic.commitText(newText, 1)
        } else {
            val textBefore = ic.getTextBeforeCursor(2000, 0)?.toString() ?: ""
            if (textBefore.isNotEmpty()) {
                ic.deleteSurroundingText(textBefore.length, 0)
            }
            ic.commitText(newText, 1)
        }
        ic.endBatchEdit()
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
        super.onDestroy()
    }
}
