package com.example.ime

import android.inputmethodservice.InputMethodService
import android.os.SystemClock
import android.text.TextUtils
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import kotlin.math.max
import kotlin.math.min

/**
 * Robust InputConnection wrapper inspired by AOSP LatinIME's RichInputConnection.
 *
 * Provides:
 * - Nested batch editing with safety depth counter to prevent unbalanced states
 * - Surrogate-pair aware deletion (never splits multi-unit Unicode characters like emojis)
 * - Fallback key events when target applications fail to process deleteSurroundingText
 * - Expected selection tracking to filter out IME self-echoes during fast typing
 * - Composing text and context extraction helpers
 */
class RichInputConnection(
    private val parentService: InputMethodService
) {
    private var inputConnection: InputConnection? = null
    private var nestLevel = 0

    // Expected cursor positions to avoid jitter and redundant queries from self-echoes
    var expectedSelStart = -1
        private set
    var expectedSelEnd = -1
        private set

    fun onConnect(ic: InputConnection?) {
        inputConnection = ic
        nestLevel = 0
        expectedSelStart = -1
        expectedSelEnd = -1
    }

    fun onDisconnect() {
        if (nestLevel > 0) {
            try {
                inputConnection?.endBatchEdit()
            } catch (_: Exception) {}
        }
        nestLevel = 0
        inputConnection = null
        expectedSelStart = -1
        expectedSelEnd = -1
    }

    val isConnected: Boolean
        get() = inputConnection != null

    /**
     * Starts a batch edit. Nested calls increase depth counter; only outermost call
     * triggers the underlying InputConnection.beginBatchEdit().
     */
    fun beginBatchEdit(): Boolean {
        val ic = inputConnection ?: return false
        if (nestLevel == 0) {
            ic.beginBatchEdit()
        }
        nestLevel++
        return true
    }

    /**
     * Ends a batch edit. Only when depth counter reaches 0 is the underlying
     * InputConnection.endBatchEdit() called.
     */
    fun endBatchEdit(): Boolean {
        val ic = inputConnection ?: return false
        if (nestLevel > 0) {
            nestLevel--
            if (nestLevel == 0) {
                ic.endBatchEdit()
            }
            return true
        }
        return false
    }

    /**
     * Notifies this wrapper that selection has been updated externally or via echo.
     */
    fun onUpdateSelection(newSelStart: Int, newSelEnd: Int): Boolean {
        val isEcho = (newSelStart == expectedSelStart && newSelEnd == expectedSelEnd)
        expectedSelStart = newSelStart
        expectedSelEnd = newSelEnd
        return isEcho
    }

    /**
     * Commits final text to the input connection.
     */
    fun commitText(text: CharSequence, newCursorPosition: Int = 1) {
        val ic = inputConnection ?: return
        beginBatchEdit()
        try {
            ic.commitText(text, newCursorPosition)
            if (expectedSelStart >= 0) {
                expectedSelStart += text.length
                expectedSelEnd = expectedSelStart
            }
        } finally {
            endBatchEdit()
        }
    }

    /**
     * Sets composing text (temporary preview text during word composition).
     */
    fun setComposingText(text: CharSequence, newCursorPosition: Int = 1) {
        val ic = inputConnection ?: return
        beginBatchEdit()
        try {
            ic.setComposingText(text, newCursorPosition)
        } finally {
            endBatchEdit()
        }
    }

    /**
     * Finishes composing text without changing its contents.
     */
    fun finishComposingText() {
        val ic = inputConnection ?: return
        beginBatchEdit()
        try {
            ic.finishComposingText()
        } finally {
            endBatchEdit()
        }
    }

    /**
     * Deletes [charactersToDelete] before cursor in a Unicode-safe manner.
     * Checks for UTF-16 surrogate pairs (e.g. emojis) to avoid leaving malformed surrogates.
     * Falls back to KeyEvent DEL if deleteSurroundingText fails or returns false.
     */
    fun deleteTextBeforeCursor(charactersToDelete: Int = 1) {
        val ic = inputConnection ?: return
        if (charactersToDelete <= 0) return

        beginBatchEdit()
        try {
            val textBefore = getTextBeforeCursor(charactersToDelete * 2 + 2, 0)
            var actualDeleteLength = 0
            var charsCounted = 0
            var i = textBefore.length - 1

            while (i >= 0 && charsCounted < charactersToDelete) {
                val c = textBefore[i]
                if (Character.isLowSurrogate(c) && i > 0 && Character.isHighSurrogate(textBefore[i - 1])) {
                    actualDeleteLength += 2
                    i -= 2
                } else {
                    actualDeleteLength += 1
                    i -= 1
                }
                charsCounted++
            }

            if (actualDeleteLength <= 0) {
                actualDeleteLength = charactersToDelete
            }

            val success = ic.deleteSurroundingText(actualDeleteLength, 0)
            if (!success) {
                // Fallback for non-compliant input fields (like certain web views and game engines)
                repeat(charactersToDelete) {
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
                }
            } else if (expectedSelStart >= actualDeleteLength) {
                expectedSelStart -= actualDeleteLength
                expectedSelEnd = expectedSelStart
            }
        } catch (_: Exception) {
            repeat(charactersToDelete) {
                sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
            }
        } finally {
            endBatchEdit()
        }
    }

    /**
     * Deletes entire word before cursor (e.g. on Backspace swipe gesture).
     */
    fun deleteWordBeforeCursor() {
        val ic = inputConnection ?: return
        beginBatchEdit()
        try {
            val textBefore = getTextBeforeCursor(64, 0)
            if (textBefore.isNotEmpty()) {
                var deleteCount = 0
                var i = textBefore.length - 1

                // 1. Skip trailing whitespace
                while (i >= 0 && textBefore[i].isWhitespace()) {
                    deleteCount++
                    i--
                }
                // 2. Consume word characters
                while (i >= 0 && !textBefore[i].isWhitespace()) {
                    deleteCount++
                    i--
                }

                if (deleteCount > 0) {
                    deleteTextBeforeCursor(deleteCount)
                }
            }
        } finally {
            endBatchEdit()
        }
    }

    /**
     * Gets text before cursor up to [n] characters.
     */
    fun getTextBeforeCursor(n: Int, flags: Int = 0): String {
        val ic = inputConnection ?: return ""
        return try {
            ic.getTextBeforeCursor(n, flags)?.toString() ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * Gets text after cursor up to [n] characters.
     */
    fun getTextAfterCursor(n: Int, flags: Int = 0): String {
        val ic = inputConnection ?: return ""
        return try {
            ic.getTextAfterCursor(n, flags)?.toString() ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * Gets currently selected text, if any.
     */
    fun getSelectedText(flags: Int = 0): String {
        val ic = inputConnection ?: return ""
        return try {
            ic.getSelectedText(flags)?.toString() ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * Queries cursor caps mode using AOSP TextUtils flags.
     */
    fun getCursorCapsMode(reqModes: Int): Int {
        val ic = inputConnection ?: return 0
        return try {
            ic.getCursorCapsMode(reqModes)
        } catch (_: Exception) {
            0
        }
    }

    /**
     * Performs an IME editor action (Search, Go, Send, Next, Done).
     */
    fun performEditorAction(action: Int): Boolean {
        val ic = inputConnection ?: return false
        return try {
            ic.performEditorAction(action)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Performs context menu actions like Cut, Copy, Paste, Select All.
     */
    fun performContextMenuAction(id: Int): Boolean {
        val ic = inputConnection ?: return false
        return try {
            ic.performContextMenuAction(id)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Sends a hardware key event (Action Down + Action Up).
     */
    fun sendDownUpKeyEvents(keyEventCode: Int) {
        val ic = inputConnection
        if (ic != null) {
            val eventTime = SystemClock.uptimeMillis()
            ic.sendKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, keyEventCode, 0, 0))
            ic.sendKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, keyEventCode, 0, 0))
        } else {
            parentService.sendDownUpKeyEvents(keyEventCode)
        }
    }

    /**
     * Sends a key event with meta modifiers (Shift, Ctrl, Alt).
     */
    fun sendKeyWithMeta(keyCode: Int, metaState: Int) {
        val ic = inputConnection ?: return
        val now = SystemClock.uptimeMillis()
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, metaState))
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0, metaState))
    }
}
