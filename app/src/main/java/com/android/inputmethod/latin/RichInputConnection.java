/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.inputmethod.latin;

import android.inputmethodservice.InputMethodService;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.inputmethod.InputConnection;
import com.android.inputmethod.latin.common.StringUtils;

public final class RichInputConnection {
    private final InputMethodService mParent;
    private InputConnection mInputConnection;
    private int mNestLevel = 0;

    private int mExpectedSelStart = -1;
    private int mExpectedSelEnd = -1;

    public RichInputConnection(final InputMethodService parent) {
        mParent = parent;
    }

    public void onConnect(final InputConnection ic) {
        if (mInputConnection == ic && ic != null) return;
        mInputConnection = ic;
        mNestLevel = 0;
        mExpectedSelStart = -1;
        mExpectedSelEnd = -1;
    }

    public void onDisconnect() {
        if (mNestLevel > 0) {
            try {
                if (mInputConnection != null) {
                    mInputConnection.endBatchEdit();
                }
            } catch (final Exception ignored) {}
        }
        mNestLevel = 0;
        mInputConnection = null;
        mExpectedSelStart = -1;
        mExpectedSelEnd = -1;
    }

    public boolean isConnected() {
        return mInputConnection != null;
    }

    public boolean beginBatchEdit() {
        if (mInputConnection == null) return false;
        if (mNestLevel == 0) {
            try {
                mInputConnection.beginBatchEdit();
            } catch (final Exception ignored) {}
        }
        mNestLevel++;
        return true;
    }

    public boolean endBatchEdit() {
        if (mInputConnection == null || mNestLevel <= 0) return false;
        mNestLevel--;
        if (mNestLevel == 0) {
            try {
                mInputConnection.endBatchEdit();
            } catch (final Exception ignored) {}
        }
        return true;
    }

    public boolean onUpdateSelection(final int newSelStart, final int newSelEnd) {
        final boolean isEcho = (newSelStart == mExpectedSelStart && newSelEnd == mExpectedSelEnd);
        mExpectedSelStart = newSelStart;
        mExpectedSelEnd = newSelEnd;
        return isEcho;
    }

    public int getExpectedSelectionStart() {
        return mExpectedSelStart;
    }

    public int getExpectedSelectionEnd() {
        return mExpectedSelEnd;
    }

    public void commitText(final CharSequence text) {
        commitText(text, 1);
    }

    public void commitText(final CharSequence text, final int newCursorPosition) {
        if (mInputConnection == null || text == null) return;
        try {
            mInputConnection.commitText(text, newCursorPosition);
            if (mExpectedSelStart >= 0 && mExpectedSelStart == mExpectedSelEnd) {
                mExpectedSelStart += text.length();
                mExpectedSelEnd = mExpectedSelStart;
            } else {
                mExpectedSelStart = -1;
                mExpectedSelEnd = -1;
            }
        } catch (final Exception ignored) {}
    }

    public void setComposingText(final CharSequence text, final int newCursorPosition) {
        if (mInputConnection == null || text == null) return;
        try {
            mInputConnection.setComposingText(text, newCursorPosition);
        } catch (final Exception ignored) {}
    }

    public void finishComposingText() {
        if (mInputConnection == null) return;
        try {
            mInputConnection.finishComposingText();
        } catch (final Exception ignored) {}
    }

    public void deleteTextBeforeCursor(final int charCount) {
        if (mInputConnection == null || charCount <= 0) return;
        beginBatchEdit();
        try {
            final CharSequence textBefore = getTextBeforeCursor(charCount * 2 + 2, 0);
            final int actualLengthToDelete = StringUtils.getTrailingCodePointCount(textBefore, charCount);
            final int charsToDelete = (actualLengthToDelete > 0) ? actualLengthToDelete : charCount;

            boolean success = false;
            try {
                success = mInputConnection.deleteSurroundingText(charsToDelete, 0);
            } catch (final Exception ignored) {}

            if (!success) {
                for (int i = 0; i < charCount; i++) {
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
                }
            } else if (mExpectedSelStart >= charsToDelete && mExpectedSelStart == mExpectedSelEnd) {
                mExpectedSelStart -= charsToDelete;
                mExpectedSelEnd = mExpectedSelStart;
            } else {
                mExpectedSelStart = -1;
                mExpectedSelEnd = -1;
            }
        } finally {
            endBatchEdit();
        }
    }

    public void deleteWordBeforeCursor() {
        if (mInputConnection == null) return;
        beginBatchEdit();
        try {
            final CharSequence textBefore = getTextBeforeCursor(64, 0);
            if (textBefore != null && textBefore.length() > 0) {
                int deleteCount = 0;
                int i = textBefore.length() - 1;
                while (i >= 0 && Character.isWhitespace(textBefore.charAt(i))) {
                    deleteCount++;
                    i--;
                }
                while (i >= 0 && !Character.isWhitespace(textBefore.charAt(i))) {
                    deleteCount++;
                    i--;
                }
                if (deleteCount > 0) {
                    deleteTextBeforeCursor(deleteCount);
                }
            }
        } finally {
            endBatchEdit();
        }
    }

    public CharSequence getTextBeforeCursor(final int n, final int flags) {
        if (mInputConnection == null || n <= 0) return "";
        try {
            final CharSequence res = mInputConnection.getTextBeforeCursor(n, flags);
            return res != null ? res : "";
        } catch (final Exception ignored) {
            return "";
        }
    }

    public CharSequence getTextAfterCursor(final int n, final int flags) {
        if (mInputConnection == null || n <= 0) return "";
        try {
            final CharSequence res = mInputConnection.getTextAfterCursor(n, flags);
            return res != null ? res : "";
        } catch (final Exception ignored) {
            return "";
        }
    }

    public CharSequence getSelectedText() {
        return getSelectedText(0);
    }

    public CharSequence getSelectedText(final int flags) {
        if (mInputConnection == null) return "";
        try {
            final CharSequence res = mInputConnection.getSelectedText(flags);
            return res != null ? res : "";
        } catch (final Exception ignored) {
            return "";
        }
    }

    public int getCursorCapsMode(final int reqModes) {
        if (mInputConnection == null) return 0;
        try {
            return mInputConnection.getCursorCapsMode(reqModes);
        } catch (final Exception ignored) {
            return 0;
        }
    }

    public boolean performEditorAction(final int action) {
        if (mInputConnection == null) return false;
        try {
            return mInputConnection.performEditorAction(action);
        } catch (final Exception ignored) {
            return false;
        }
    }

    public boolean performContextMenuAction(final int id) {
        if (mInputConnection == null) return false;
        try {
            return mInputConnection.performContextMenuAction(id);
        } catch (final Exception ignored) {
            return false;
        }
    }

    public void sendDownUpKeyEvents(final int keyEventCode) {
        if (mInputConnection != null) {
            final long eventTime = SystemClock.uptimeMillis();
            try {
                mInputConnection.sendKeyEvent(new KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, keyEventCode, 0, 0));
                mInputConnection.sendKeyEvent(new KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, keyEventCode, 0, 0));
            } catch (final Exception ignored) {}
        } else if (mParent != null) {
            try {
                mParent.sendDownUpKeyEvents(keyEventCode);
            } catch (final Exception ignored) {}
        }
    }

    public void sendKeyWithMeta(final int keyCode, final int metaState) {
        if (mInputConnection == null) return;
        final long now = SystemClock.uptimeMillis();
        try {
            mInputConnection.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, metaState));
            mInputConnection.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0, metaState));
        } catch (final Exception ignored) {}
    }
}
