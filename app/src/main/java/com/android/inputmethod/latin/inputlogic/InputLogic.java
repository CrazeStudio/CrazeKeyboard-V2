/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.inputmethod.latin.inputlogic;

import android.text.InputType;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import com.android.inputmethod.latin.RichInputConnection;
import com.android.inputmethod.latin.common.Constants;

/**
 * LatinIME input core adapted for CrazeKeyboard.
 *
 * The typing hot path intentionally avoids querying the target editor after every character.
 * Editor-context/caps queries are expensive cross-process operations and must not be chained
 * behind every key press. Local composition state is used for normal character input and the
 * editor is queried only at session boundaries, explicit selection changes, and operations that
 * genuinely require surrounding text.
 */
public final class InputLogic {
    public interface Listener {
        void onWordComposed(String word);
        void onAutoCorrectChanged(String original, String correction);
        void onCapsModeChanged(boolean isSentenceStart);
    }

    private final RichInputConnection mConnection;
    private final WordComposer mWordComposer = new WordComposer();
    private Listener mListener;

    private int mSpaceState = SpaceState.NONE;
    private long mLastSpaceTime = 0L;
    private boolean mDoubleSpacePeriodEnabled = true;

    private String mLastOriginalWord = null;
    private String mLastCorrectedWord = null;
    private long mLastAutoCorrectTime = 0L;

    private boolean mIsPasswordField = false;
    private boolean mIsEmailField = false;
    private boolean mIsUriField = false;
    private boolean mIsNumericField = false;
    private boolean mIsMultilineField = false;
    private int mCurrentImeAction = EditorInfo.IME_ACTION_NONE;
    private boolean mIsSentenceStart = true;
    private boolean mSentenceBoundaryPending = false;
    private android.util.Pair<String, String> mAutoCorrectNotice = null;

    public InputLogic(final RichInputConnection connection) {
        mConnection = connection;
    }

    public void setListener(final Listener listener) { mListener = listener; }

    public void setDoubleSpacePeriodEnabled(final boolean enabled) {
        mDoubleSpacePeriodEnabled = enabled;
    }

    public void onStartInput(final EditorInfo info) { startInput(info); }

    public void onFinishInput() { finishInput(); }

    /** Explicit refresh; do not call this for every normal character. */
    public void updateWordPrefixAndCaps() { updateCapsModeFromEditor(); }

    public void onBackspace() { handleBackspace(); }

    public void onDeleteWord() { deleteWord(); }

    public void onEnter() { handleEnter(); }

    public void onReplaceCurrentWord(final String replacement) {
        onAutoCorrection(getCurrentWord(), replacement);
    }

    public void onMoveCursor(final int offset) {
        if (offset == 0) return;
        final int keyCode = offset < 0 ? KeyEvent.KEYCODE_DPAD_LEFT : KeyEvent.KEYCODE_DPAD_RIGHT;
        final int count = Math.abs(offset);
        for (int i = 0; i < count; i++) {
            mConnection.sendDownUpKeyEvents(keyCode);
        }
        updateCapsModeFromEditor();
    }

    public boolean isSentenceStart() { return mIsSentenceStart; }
    public String getCurrentWordPrefix() { return getCurrentWord(); }
    public android.util.Pair<String, String> getAutoCorrectNotice() { return mAutoCorrectNotice; }

    public void startInput(final EditorInfo info) {
        evaluateEditorInfo(info);
        mWordComposer.reset();
        mSpaceState = SpaceState.NONE;
        mLastSpaceTime = 0L;
        mLastOriginalWord = null;
        mLastCorrectedWord = null;
        mAutoCorrectNotice = null;
        mSentenceBoundaryPending = false;
        updateCapsModeFromEditor();
        notifyWordComposed();
    }

    public void finishInput() {
        mWordComposer.reset();
        mSpaceState = SpaceState.NONE;
        mLastOriginalWord = null;
        mLastCorrectedWord = null;
        mAutoCorrectNotice = null;
        mSentenceBoundaryPending = false;
        notifyWordComposed();
    }

    private void evaluateEditorInfo(final EditorInfo info) {
        if (info == null) {
            mIsPasswordField = false;
            mIsEmailField = false;
            mIsUriField = false;
            mIsNumericField = false;
            mIsMultilineField = false;
            mCurrentImeAction = EditorInfo.IME_ACTION_NONE;
            return;
        }

        mCurrentImeAction = info.imeOptions & EditorInfo.IME_MASK_ACTION;
        final int inputType = info.inputType;
        final int inputClass = inputType & InputType.TYPE_MASK_CLASS;
        final int variation = inputType & InputType.TYPE_MASK_VARIATION;

        mIsPasswordField = (inputClass == InputType.TYPE_CLASS_TEXT &&
                (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                 variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                 variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)) ||
                (inputClass == InputType.TYPE_CLASS_NUMBER &&
                 variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD);

        mIsEmailField = inputClass == InputType.TYPE_CLASS_TEXT &&
                (variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
                 variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS);

        mIsUriField = inputClass == InputType.TYPE_CLASS_TEXT &&
                variation == InputType.TYPE_TEXT_VARIATION_URI;

        mIsNumericField = inputClass == InputType.TYPE_CLASS_NUMBER ||
                inputClass == InputType.TYPE_CLASS_PHONE ||
                inputClass == InputType.TYPE_CLASS_DATETIME;

        mIsMultilineField = (inputType & InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0;
    }

    public boolean isPasswordField() { return mIsPasswordField; }
    public boolean isEmailField() { return mIsEmailField; }
    public boolean isUriField() { return mIsUriField; }
    public boolean isNumericField() { return mIsNumericField; }
    public boolean isMultilineField() { return mIsMultilineField; }
    public int getCurrentImeAction() { return mCurrentImeAction; }

    public void onCodeInput(final int primaryCode, final int x, final int y, final boolean isShifted) {
        if (primaryCode == Constants.CODE_SPACE) {
            handleSpace();
        } else if (primaryCode == Constants.CODE_ENTER) {
            handleEnter();
        } else if (primaryCode == Constants.CODE_DELETE) {
            handleBackspace();
        } else if (primaryCode > 0) {
            int code = primaryCode;
            if (isShifted && Character.isLetter(code)) code = Character.toUpperCase(code);
            onTextInput(new String(Character.toChars(code)));
        }
    }

    /** Hot path: commit first, then update only local state. */
    public void onTextInput(final CharSequence text) {
        if (text == null || text.length() == 0) return;
        final String str = text.toString();

        if (" ".equals(str)) {
            handleSpace();
            return;
        }

        mConnection.commitText(str, 1);
        mLastOriginalWord = null;
        mLastCorrectedWord = null;
        mAutoCorrectNotice = null;
        if (mListener != null) mListener.onAutoCorrectChanged(null, null);

        if (str.length() == 1) {
            final char c = str.charAt(0);
            if (Character.isLetterOrDigit(c)) {
                mWordComposer.add(c, 0, 0);
                mIsSentenceStart = false;
                mSentenceBoundaryPending = false;
            } else {
                mWordComposer.reset();
                if (c == '.' || c == '!' || c == '?') {
                    mSentenceBoundaryPending = true;
                } else if (c == '\n') {
                    mSentenceBoundaryPending = false;
                    mIsSentenceStart = true;
                } else {
                    mSentenceBoundaryPending = false;
                    mIsSentenceStart = false;
                }
            }
        } else {
            mWordComposer.reset();
            mIsSentenceStart = false;
            mSentenceBoundaryPending = false;
        }

        mSpaceState = SpaceState.NONE;
        notifyCapsState();
        notifyWordComposed();
    }

    public void handleSpace() {
        final long now = System.currentTimeMillis();

        if (mDoubleSpacePeriodEnabled &&
                (now - mLastSpaceTime < 350L) &&
                mSpaceState == SpaceState.DOUBLE_SPACE_TO_PERIOD) {
            final CharSequence textBefore = mConnection.getTextBeforeCursor(3, 0);
            if (textBefore != null && textBefore.length() >= 2 && textBefore.charAt(textBefore.length() - 1) == ' ') {
                final char charBeforeSpace = textBefore.charAt(textBefore.length() - 2);
                if (charBeforeSpace != '.' && charBeforeSpace != '?' &&
                        charBeforeSpace != '!' && charBeforeSpace != ',') {
                    mConnection.deleteTextBeforeCursor(1);
                    mConnection.commitText(". ", 1);
                    mSpaceState = SpaceState.NONE;
                    mLastSpaceTime = 0L;
                    mWordComposer.reset();
                    mSentenceBoundaryPending = false;
                    mIsSentenceStart = true;
                    notifyCapsState();
                    notifyWordComposed();
                    return;
                }
            }
        }

        mLastSpaceTime = now;
        mSpaceState = SpaceState.DOUBLE_SPACE_TO_PERIOD;
        mConnection.commitText(" ", 1);
        mWordComposer.reset();
        mLastOriginalWord = null;
        mLastCorrectedWord = null;
        mAutoCorrectNotice = null;

        if (mSentenceBoundaryPending) {
            mIsSentenceStart = true;
            mSentenceBoundaryPending = false;
        } else {
            mIsSentenceStart = false;
        }
        notifyCapsState();
        notifyWordComposed();
    }

    public void handleBackspace() {
        final long now = System.currentTimeMillis();

        if (mLastCorrectedWord != null && (now - mLastAutoCorrectTime < 3500L)) {
            final CharSequence textBefore = mConnection.getTextBeforeCursor(mLastCorrectedWord.length() + 1, 0);
            if (textBefore != null && textBefore.toString().equals(mLastCorrectedWord + " ")) {
                mConnection.beginBatchEdit();
                try {
                    mConnection.deleteTextBeforeCursor(mLastCorrectedWord.length() + 1);
                    mConnection.commitText(mLastOriginalWord, 1);
                } finally {
                    mConnection.endBatchEdit();
                }
                mWordComposer.reset();
                for (int i = 0; i < mLastOriginalWord.length();) {
                    final int cp = mLastOriginalWord.codePointAt(i);
                    mWordComposer.add(cp, 0, 0);
                    i += Character.charCount(cp);
                }
                mLastOriginalWord = null;
                mLastCorrectedWord = null;
                mAutoCorrectNotice = null;
                if (mListener != null) mListener.onAutoCorrectChanged(null, null);
                mIsSentenceStart = false;
                notifyCapsState();
                notifyWordComposed();
                return;
            }
        }

        mLastOriginalWord = null;
        mLastCorrectedWord = null;
        mAutoCorrectNotice = null;
        if (mListener != null) mListener.onAutoCorrectChanged(null, null);

        final boolean hadWord = mWordComposer.size() > 0;
        if (hadWord) mWordComposer.deleteLast();
        mConnection.deleteTextBeforeCursor(1);
        mSpaceState = SpaceState.NONE;
        mSentenceBoundaryPending = false;

        // Keep deletion fast. Only re-query editor context when local state is exhausted.
        if (mWordComposer.size() == 0) {
            updateCapsModeFromEditor();
        } else {
            mIsSentenceStart = false;
            notifyCapsState();
        }
        notifyWordComposed();
    }

    public void deleteWord() {
        mWordComposer.reset();
        mConnection.deleteWordBeforeCursor();
        mLastOriginalWord = null;
        mLastCorrectedWord = null;
        mAutoCorrectNotice = null;
        if (mListener != null) mListener.onAutoCorrectChanged(null, null);
        updateCapsModeFromEditor();
        notifyWordComposed();
    }

    public void handleEnter() {
        mWordComposer.reset();
        mLastOriginalWord = null;
        mLastCorrectedWord = null;
        mAutoCorrectNotice = null;
        if (mCurrentImeAction != EditorInfo.IME_ACTION_NONE &&
                mCurrentImeAction != EditorInfo.IME_ACTION_UNSPECIFIED) {
            final boolean handled = mConnection.performEditorAction(mCurrentImeAction);
            if (!handled) mConnection.sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER);
        } else {
            mConnection.sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER);
        }
        mSentenceBoundaryPending = false;
        mIsSentenceStart = true;
        notifyCapsState();
        notifyWordComposed();
    }

    public void onAutoCorrection(final String original, final String replacement) {
        if (replacement == null || replacement.isEmpty()) return;

        mConnection.beginBatchEdit();
        try {
            final String currentWord = mWordComposer.getTypedWord();
            final int deleteCount = currentWord.length() > 0 ? currentWord.length() :
                    (original != null ? original.length() : 0);
            if (deleteCount > 0) mConnection.deleteTextBeforeCursor(deleteCount);
            mConnection.commitText(replacement, 1);

            if (original != null && !original.isEmpty()) {
                mLastOriginalWord = original;
                mLastCorrectedWord = replacement.trim();
                mLastAutoCorrectTime = System.currentTimeMillis();
                mAutoCorrectNotice = new android.util.Pair<>(original, mLastCorrectedWord);
                if (mListener != null) mListener.onAutoCorrectChanged(original, mLastCorrectedWord);
            } else {
                mAutoCorrectNotice = null;
            }
        } finally {
            mConnection.endBatchEdit();
        }

        mWordComposer.reset();
        mIsSentenceStart = false;
        mSentenceBoundaryPending = false;
        notifyCapsState();
        notifyWordComposed();
    }

    /**
     * Explicit editor synchronization. This is intentionally not part of the normal character
     * insertion path because InputConnection editor queries can cross process boundaries.
     */
    public void updateCapsModeFromEditor() {
        final CharSequence textBefore = mConnection.getTextBeforeCursor(32, 0);
        final int reqModes = TextUtils.CAP_MODE_CHARACTERS | TextUtils.CAP_MODE_WORDS | TextUtils.CAP_MODE_SENTENCES;
        final int caps = (!mIsEmailField && !mIsUriField && !mIsPasswordField && !mIsNumericField)
                ? mConnection.getCursorCapsMode(reqModes) : 0;
        mIsSentenceStart = computeSentenceStart(textBefore, caps);
        mSentenceBoundaryPending = false;
        notifyCapsState();
    }

    private boolean computeSentenceStart(final CharSequence textBefore, final int capsMode) {
        if (mIsEmailField || mIsUriField || mIsPasswordField || mIsNumericField) return false;
        if (capsMode != 0) return true;
        if (textBefore == null || textBefore.length() == 0) return true;

        final int len = textBefore.length();
        if (textBefore.charAt(len - 1) == '\n') return true;
        int i = len - 1;
        while (i >= 0 && Character.isWhitespace(textBefore.charAt(i))) i--;
        if (i < 0) return true;
        final char lastNonWs = textBefore.charAt(i);
        return (lastNonWs == '.' || lastNonWs == '!' || lastNonWs == '?') && i < len - 1;
    }

    private void notifyCapsState() {
        if (mListener != null) mListener.onCapsModeChanged(mIsSentenceStart);
    }

    private void notifyWordComposed() {
        if (mListener != null) mListener.onWordComposed(mWordComposer.getTypedWord());
    }

    public String getCurrentWord() { return mWordComposer.getTypedWord(); }

    public void onExternalSelectionChange() {
        final CharSequence textBefore = mConnection.getTextBeforeCursor(32, 0);
        final String currentTyped = mWordComposer.getTypedWord();
        if (currentTyped.length() > 0 &&
                (textBefore == null || !textBefore.toString().endsWith(currentTyped))) {
            mWordComposer.reset();
            notifyWordComposed();
        }
        updateCapsModeFromEditor();
    }
}
