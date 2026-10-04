package com.example.ime

import android.inputmethodservice.InputMethodService
import android.os.SystemClock
import android.view.KeyEvent
import android.view.inputmethod.InputConnection

/**
 * Lightweight InputConnection wrapper for the CrazeKeyboard IME.
 *
 * Keeps batch-edit safety for compound operations while keeping the hottest
 * path (normal key commits) direct and allocation-light.
 */
class RichInputConnection(
    private val parentService: InputMethodService
) {
    private var inputConnection: InputConnection? = null
    private var nestLevel = 0

    var expectedSelStart = -1
        private set
    var expectedSelEnd = -1
        private set

    fun onConnect(ic: InputConnection?) {
        if (inputConnection === ic && ic != null) return
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

    fun beginBatchEdit(): Boolean {
        val ic = inputConnection ?: return false
        if (nestLevel == 0) ic.beginBatchEdit()
        nestLevel++
        return true
    }

    fun endBatchEdit(): Boolean {
        val ic = inputConnection ?: return false
        if (nestLevel <= 0) return false
        nestLevel--
        if (nestLevel == 0) ic.endBatchEdit()
        return true
    }

    fun onUpdateSelection(newSelStart: Int, newSelEnd: Int): Boolean {
        val isEcho = newSelStart == expectedSelStart && newSelEnd == expectedSelEnd
        expectedSelStart = newSelStart
        expectedSelEnd = newSelEnd
        return isEcho
    }

    /**
     * Hot path: normal character insertion must not open a batch edit for every key.
     * Compound operations explicitly use beginBatchEdit()/endBatchEdit().
     */
    fun commitText(text: CharSequence, newCursorPosition: Int = 1) {
        val ic = inputConnection ?: return
        try {
            ic.commitText(text, newCursorPosition)
            if (expectedSelStart >= 0 && expectedSelStart == expectedSelEnd) {
                val delta = text.length
                expectedSelStart += delta
                expectedSelEnd = expectedSelStart
            } else {
                expectedSelStart = -1
                expectedSelEnd = -1
            }
        } catch (_: Exception) {
            // IMEs must never crash the keyboard because an editor rejected input.
        }
    }

    fun setComposingText(text: CharSequence, newCursorPosition: Int = 1) {
        val ic = inputConnection ?: return
        try {
            ic.setComposingText(text, newCursorPosition)
        } catch (_: Exception) {}
    }

    fun finishComposingText() {
        val ic = inputConnection ?: return
        try {
            ic.finishComposingText()
        } catch (_: Exception) {}
    }

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
                    actualDeleteLength++
                    i--
                }
                charsCounted++
            }

            if (actualDeleteLength <= 0) actualDeleteLength = charactersToDelete

            val success = try {
                ic.deleteSurroundingText(actualDeleteLength, 0)
            } catch (_: Exception) {
                false
            }

            if (!success) {
                repeat(charactersToDelete) {
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
                }
            } else if (expectedSelStart >= actualDeleteLength && expectedSelStart == expectedSelEnd) {
                expectedSelStart -= actualDeleteLength
                expectedSelEnd = expectedSelStart
            } else {
                expectedSelStart = -1
                expectedSelEnd = -1
            }
        } finally {
            endBatchEdit()
        }
    }

    fun deleteWordBeforeCursor() {
        val ic = inputConnection ?: return
        beginBatchEdit()
        try {
            val textBefore = getTextBeforeCursor(64, 0)
            if (textBefore.isNotEmpty()) {
                var deleteCount = 0
                var i = textBefore.length - 1
                while (i >= 0 && textBefore[i].isWhitespace()) {
                    deleteCount++
                    i--
                }
                while (i >= 0 && !textBefore[i].isWhitespace()) {
                    deleteCount++
                    i--
                }
                if (deleteCount > 0) deleteTextBeforeCursor(deleteCount)
            }
        } finally {
            endBatchEdit()
        }
    }

    fun getTextBeforeCursor(n: Int, flags: Int = 0): String = try {
        inputConnection?.getTextBeforeCursor(n, flags)?.toString() ?: ""
    } catch (_: Exception) { "" }

    fun getTextAfterCursor(n: Int, flags: Int = 0): String = try {
        inputConnection?.getTextAfterCursor(n, flags)?.toString() ?: ""
    } catch (_: Exception) { "" }

    fun getSelectedText(flags: Int = 0): String = try {
        inputConnection?.getSelectedText(flags)?.toString() ?: ""
    } catch (_: Exception) { "" }

    fun getCursorCapsMode(reqModes: Int): Int = try {
        inputConnection?.getCursorCapsMode(reqModes) ?: 0
    } catch (_: Exception) { 0 }

    fun performEditorAction(action: Int): Boolean = try {
        inputConnection?.performEditorAction(action) ?: false
    } catch (_: Exception) { false }

    fun performContextMenuAction(id: Int): Boolean = try {
        inputConnection?.performContextMenuAction(id) ?: false
    } catch (_: Exception) { false }

    fun sendDownUpKeyEvents(keyEventCode: Int) {
        val ic = inputConnection
        if (ic != null) {
            val eventTime = SystemClock.uptimeMillis()
            try {
                ic.sendKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, keyEventCode, 0, 0))
                ic.sendKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, keyEventCode, 0, 0))
            } catch (_: Exception) {}
        } else {
            try { parentService.sendDownUpKeyEvents(keyEventCode) } catch (_: Exception) {}
        }
    }

    fun sendKeyWithMeta(keyCode: Int, metaState: Int) {
        val ic = inputConnection ?: return
        val now = SystemClock.uptimeMillis()
        try {
            ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, metaState))
            ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0, metaState))
        } catch (_: Exception) {}
    }
}
