package com.example.ime

import android.text.TextUtils
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import com.example.developer.DevKeyItem
import com.example.developer.DevKeyType
import com.example.developer.ModifierState
import com.example.keyboard.CursorAction
import com.example.settings.KeyboardPreferences
import com.example.suggestions.SuggestionEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Record of the most recent auto-correction to allow instant reversal on Backspace.
 */
data class AutoCorrectRecord(
    val originalWord: String,
    val correctedWord: String,
    val timestamp: Long
)

/**
 * AOSP LatinIME-based InputLogic engine.
 *
 * Coordinates:
 * - Word composition & prefix extraction
 * - AOSP-compliant Auto-Capitalization (Characters, Words, Sentences)
 * - Double-space-to-period insertion
 * - Auto-correct and immediate Backspace reversal (Undo Auto-Correction)
 * - Safe cursor movement and context editing
 * - Non-blocking asynchronous suggestions
 */
class InputLogic(
    private val richImm: RichInputConnection,
    private val preferences: KeyboardPreferences,
    private val suggestionEngine: SuggestionEngine,
    private val scope: CoroutineScope,
    private val onStateUpdated: (wordPrefix: String, isSentenceStart: Boolean, autoCorrectNotice: Pair<String, String>?) -> Unit
) {
    // Current word state
    var currentWordPrefix: String = ""
        private set
    var isSentenceStart: Boolean = true
        private set
    var autoCorrectNotice: Pair<String, String>? = null
        private set

    // Editor attributes
    var isPasswordField: Boolean = false
        private set
    var isEmailField: Boolean = false
        private set
    var isUriField: Boolean = false
        private set
    var isNumericField: Boolean = false
        private set
    var isMultilineField: Boolean = false
        private set
    var currentImeAction: Int = EditorInfo.IME_ACTION_NONE
        private set

    private var lastAutoCorrectRecord: AutoCorrectRecord? = null
    private var lastSpaceTime: Long = 0L
    private var suggestionJob: Job? = null

    fun onStartInput(info: EditorInfo?) {
        evaluateEditorInfo(info)
        lastAutoCorrectRecord = null
        currentWordPrefix = ""
        updateWordPrefixAndCaps()
    }

    fun onFinishInput() {
        currentWordPrefix = ""
        isSentenceStart = true
        autoCorrectNotice = null
        lastAutoCorrectRecord = null
        suggestionJob?.cancel()
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

    /**
     * Handles typing a text string (single character or committed string).
     */
    fun onTextInput(text: String) {
        if (text.isEmpty()) return

        // 1. Double-space-to-period optimization
        if (text == " " && preferences.isDoubleSpacePeriodEnabled) {
            val now = System.currentTimeMillis()
            if (now - lastSpaceTime < 350L) {
                val textBefore = richImm.getTextBeforeCursor(3, 0)
                if (textBefore.endsWith(" ") && textBefore.length >= 2 && !textBefore[textBefore.length - 2].let { it == '.' || it == '?' || it == '!' }) {
                    richImm.deleteTextBeforeCursor(1)
                    richImm.commitText(". ")
                    lastSpaceTime = 0L
                    updateWordPrefixAndCaps()
                    return
                }
            }
            lastSpaceTime = now
        } else {
            lastSpaceTime = 0L
        }

        // 2. Commit the text synchronously to avoid any UI-thread lag
        richImm.commitText(text)

        // 3. Clear auto-correction notice on new manual key press
        autoCorrectNotice = null

        // 4. Update word buffer and capitalization state
        if (text.length == 1) {
            val c = text[0]
            if (c.isLetterOrDigit()) {
                currentWordPrefix += text
                isSentenceStart = false
            } else {
                currentWordPrefix = ""
                updateWordPrefixAndCaps()
            }
        } else {
            currentWordPrefix = ""
            updateWordPrefixAndCaps()
        }

        notifyState()
    }

    /**
     * Handles Backspace press.
     * Checks if we just performed an auto-correction; if so, undoes the auto-correction.
     */
    fun onBackspace() {
        val lastAc = lastAutoCorrectRecord
        val now = System.currentTimeMillis()

        // AOSP LatinIME feature: Undo Auto-Correction on immediate Backspace
        if (lastAc != null && (now - lastAc.timestamp < 3500L)) {
            val checkBefore = richImm.getTextBeforeCursor(lastAc.correctedWord.length + 1, 0)
            if (checkBefore == "${lastAc.correctedWord} ") {
                richImm.beginBatchEdit()
                try {
                    richImm.deleteTextBeforeCursor(lastAc.correctedWord.length + 1)
                    richImm.commitText(lastAc.originalWord)
                } finally {
                    richImm.endBatchEdit()
                }
                lastAutoCorrectRecord = null
                autoCorrectNotice = null
                currentWordPrefix = lastAc.originalWord
                updateWordPrefixAndCaps()
                return
            }
        }

        // Standard Unicode-safe backspace
        lastAutoCorrectRecord = null
        autoCorrectNotice = null
        richImm.deleteTextBeforeCursor(1)
        updateWordPrefixAndCaps()
    }

    /**
     * Handles word deletion (e.g. Backspace swipe gesture).
     */
    fun onDeleteWord() {
        lastAutoCorrectRecord = null
        autoCorrectNotice = null
        richImm.deleteWordBeforeCursor()
        currentWordPrefix = ""
        updateWordPrefixAndCaps()
    }

    /**
     * Replaces the currently typed prefix with a chosen word (suggestion or autocorrection).
     */
    fun onReplaceCurrentWord(replacement: String, isAutoCorrect: Boolean = false, original: String = "") {
        richImm.beginBatchEdit()
        try {
            val textBefore = richImm.getTextBeforeCursor(maxOf(currentWordPrefix.length, 30), 0)
            val prefixToDelete = if (currentWordPrefix.isNotEmpty() && textBefore.endsWith(currentWordPrefix)) {
                currentWordPrefix.length
            } else {
                val lastWord = textBefore.takeLastWhile { it.isLetterOrDigit() }
                lastWord.length
            }

            if (prefixToDelete > 0) {
                richImm.deleteTextBeforeCursor(prefixToDelete)
            }
            richImm.commitText(replacement)

            if (isAutoCorrect && original.isNotEmpty()) {
                val trimmedReplacement = replacement.trim()
                lastAutoCorrectRecord = AutoCorrectRecord(
                    originalWord = original,
                    correctedWord = trimmedReplacement,
                    timestamp = System.currentTimeMillis()
                )
                autoCorrectNotice = original to trimmedReplacement
            } else {
                lastAutoCorrectRecord = null
                autoCorrectNotice = null
            }
        } finally {
            richImm.endBatchEdit()
        }

        currentWordPrefix = ""
        updateWordPrefixAndCaps()
    }

    /**
     * Handles Enter key action or fallback newline.
     */
    fun onEnter() {
        lastAutoCorrectRecord = null
        autoCorrectNotice = null
        val action = currentImeAction
        if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            val handled = richImm.performEditorAction(action)
            if (!handled) {
                richImm.sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
            }
        } else {
            richImm.sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        }
        currentWordPrefix = ""
        updateWordPrefixAndCaps()
    }

    /**
     * Moves cursor by [offset] characters.
     */
    fun onMoveCursor(offset: Int) {
        if (offset > 0) {
            repeat(offset) { richImm.sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_RIGHT) }
        } else if (offset < 0) {
            repeat(-offset) { richImm.sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_LEFT) }
        }
        updateWordPrefixAndCaps()
    }

    /**
     * Special cursor navigation and selection actions.
     */
    fun onSpecialKey(action: CursorAction, isSelectMode: Boolean) {
        when (action) {
            CursorAction.LEFT -> {
                if (isSelectMode) richImm.sendKeyWithMeta(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.META_SHIFT_ON)
                else richImm.sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_LEFT)
            }
            CursorAction.RIGHT -> {
                if (isSelectMode) richImm.sendKeyWithMeta(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.META_SHIFT_ON)
                else richImm.sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_RIGHT)
            }
            CursorAction.UP -> {
                if (isSelectMode) richImm.sendKeyWithMeta(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.META_SHIFT_ON)
                else richImm.sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_UP)
            }
            CursorAction.DOWN -> {
                if (isSelectMode) richImm.sendKeyWithMeta(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.META_SHIFT_ON)
                else richImm.sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_DOWN)
            }
            CursorAction.HOME -> {
                if (isSelectMode) richImm.sendKeyWithMeta(KeyEvent.KEYCODE_MOVE_HOME, KeyEvent.META_SHIFT_ON)
                else richImm.sendDownUpKeyEvents(KeyEvent.KEYCODE_MOVE_HOME)
            }
            CursorAction.END -> {
                if (isSelectMode) richImm.sendKeyWithMeta(KeyEvent.KEYCODE_MOVE_END, KeyEvent.META_SHIFT_ON)
                else richImm.sendDownUpKeyEvents(KeyEvent.KEYCODE_MOVE_END)
            }
            CursorAction.SELECT_ALL -> richImm.performContextMenuAction(android.R.id.selectAll)
            CursorAction.CUT -> richImm.performContextMenuAction(android.R.id.cut)
            CursorAction.COPY -> richImm.performContextMenuAction(android.R.id.copy)
            CursorAction.PASTE -> richImm.performContextMenuAction(android.R.id.paste)
            CursorAction.DELETE -> {
                val selected = richImm.getSelectedText()
                if (selected.isNotEmpty()) {
                    richImm.commitText("")
                } else {
                    richImm.sendDownUpKeyEvents(KeyEvent.KEYCODE_FORWARD_DEL)
                }
            }
            else -> {}
        }
        updateWordPrefixAndCaps()
    }

    /**
     * Developer toolbar hardware-like key events.
     */
    fun onDevKey(item: DevKeyItem, modifierState: ModifierState) {
        var meta = 0
        if (modifierState.isCtrlActive) meta = meta or KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        if (modifierState.isAltActive) meta = meta or KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
        if (modifierState.isShiftActive) meta = meta or KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON

        when (item.type) {
            DevKeyType.ESC -> richImm.sendKeyWithMeta(KeyEvent.KEYCODE_ESCAPE, meta)
            DevKeyType.TAB -> richImm.sendKeyWithMeta(KeyEvent.KEYCODE_TAB, meta)
            DevKeyType.ARROW_LEFT -> richImm.sendKeyWithMeta(KeyEvent.KEYCODE_DPAD_LEFT, meta)
            DevKeyType.ARROW_RIGHT -> richImm.sendKeyWithMeta(KeyEvent.KEYCODE_DPAD_RIGHT, meta)
            DevKeyType.ARROW_UP -> richImm.sendKeyWithMeta(KeyEvent.KEYCODE_DPAD_UP, meta)
            DevKeyType.ARROW_DOWN -> richImm.sendKeyWithMeta(KeyEvent.KEYCODE_DPAD_DOWN, meta)
            DevKeyType.HOME -> richImm.sendKeyWithMeta(KeyEvent.KEYCODE_MOVE_HOME, meta)
            DevKeyType.END -> richImm.sendKeyWithMeta(KeyEvent.KEYCODE_MOVE_END, meta)
            DevKeyType.TEXT -> {
                if (modifierState.isCtrlActive) {
                    when (item.label.lowercase()) {
                        "c" -> richImm.performContextMenuAction(android.R.id.copy)
                        "v" -> richImm.performContextMenuAction(android.R.id.paste)
                        "x" -> richImm.performContextMenuAction(android.R.id.cut)
                        "a" -> richImm.performContextMenuAction(android.R.id.selectAll)
                        "z" -> richImm.performContextMenuAction(android.R.id.undo)
                        "y" -> richImm.performContextMenuAction(android.R.id.redo)
                        else -> richImm.commitText(item.insertText)
                    }
                } else {
                    richImm.commitText(item.insertText)
                }
            }
            else -> {}
        }
        updateWordPrefixAndCaps()
    }

    /**
     * Updates current word prefix and checks cursor capitalization mode.
     */
    fun updateWordPrefixAndCaps() {
        val textBefore = richImm.getTextBeforeCursor(32, 0)
        currentWordPrefix = textBefore.takeLastWhile { it.isLetterOrDigit() }

        val capsMode = if (!isEmailField && !isUriField && !isPasswordField && !isNumericField) {
            richImm.getCursorCapsMode(
                TextUtils.CAP_MODE_CHARACTERS or TextUtils.CAP_MODE_WORDS or TextUtils.CAP_MODE_SENTENCES
            )
        } else 0

        isSentenceStart = computeSentenceStart(textBefore, capsMode)
        notifyState()
    }

    private fun computeSentenceStart(textBefore: String, capsMode: Int): Boolean {
        if (isEmailField || isUriField || isPasswordField || isNumericField) return false
        if (capsMode != 0) return true
        if (textBefore.isEmpty()) return true
        if (textBefore.endsWith('\n')) return true

        val len = textBefore.length
        var i = len - 1
        while (i >= 0 && textBefore[i].isWhitespace()) {
            i--
        }
        if (i < 0) return true // only whitespace before cursor
        val lastNonWs = textBefore[i]
        if (lastNonWs == '.' || lastNonWs == '!' || lastNonWs == '?') {
            if (i < len - 1) { // whitespace follows punctuation
                return true
            }
        }
        return false
    }

    private fun notifyState() {
        onStateUpdated(currentWordPrefix, isSentenceStart, autoCorrectNotice)
    }
}
