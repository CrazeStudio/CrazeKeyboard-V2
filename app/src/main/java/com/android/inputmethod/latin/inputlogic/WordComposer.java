/*
 * Copyright (C) 2008 The Android Open Source Project
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

import com.android.inputmethod.latin.common.Constants;
import com.android.inputmethod.latin.common.InputPointers;

import java.util.ArrayList;

public final class WordComposer {
    public static final int CAPS_MODE_OFF = 0;
    public static final int CAPS_MODE_MANUAL_SHIFTED = 1;
    public static final int CAPS_MODE_AUTO_SHIFTED = 2;
    public static final int CAPS_MODE_CAPSLOCK = 3;

    private final ArrayList<Integer> mPrimaryCodePoints = new ArrayList<>(48);
    private final InputPointers mInputPointers = new InputPointers();
    private final StringBuilder mTypedWord = new StringBuilder(48);

    private int mCapsMode = CAPS_MODE_OFF;
    private boolean mIsComposing = false;
    private int mTrailingSingleQuotesCount = 0;

    public WordComposer() {}

    public void reset() {
        mPrimaryCodePoints.clear();
        mInputPointers.reset();
        mTypedWord.setLength(0);
        mCapsMode = CAPS_MODE_OFF;
        mIsComposing = false;
        mTrailingSingleQuotesCount = 0;
    }

    public void add(final int primaryCodePoint, final int keyX, final int keyY) {
        mPrimaryCodePoints.add(primaryCodePoint);
        mTypedWord.appendCodePoint(primaryCodePoint);
        mInputPointers.appendPointer(keyX, keyY, 0, (int) System.currentTimeMillis());
        mIsComposing = true;
        if (primaryCodePoint == Constants.CODE_SINGLE_QUOTE) {
            mTrailingSingleQuotesCount++;
        } else {
            mTrailingSingleQuotesCount = 0;
        }
    }

    public void deleteLast() {
        final int size = size();
        if (size > 0) {
            final int lastCodePoint = mPrimaryCodePoints.remove(size - 1);
            final int charCount = Character.charCount(lastCodePoint);
            mTypedWord.delete(mTypedWord.length() - charCount, mTypedWord.length());
            if (mPrimaryCodePoints.isEmpty()) {
                mIsComposing = false;
            }
        }
    }

    public int size() {
        return mPrimaryCodePoints.size();
    }

    public boolean isComposingWord() {
        return mIsComposing && size() > 0;
    }

    public String getTypedWord() {
        return mTypedWord.toString();
    }

    public int getCodePointAt(final int index) {
        if (index < 0 || index >= mPrimaryCodePoints.size()) return Constants.NOT_A_CODE;
        return mPrimaryCodePoints.get(index);
    }

    public void setCapsMode(final int capsMode) {
        mCapsMode = capsMode;
    }

    public int getCapsMode() {
        return mCapsMode;
    }

    public boolean isAllUpperCase() {
        if (mTypedWord.length() == 0) return false;
        for (int i = 0; i < mTypedWord.length(); ) {
            final int cp = mTypedWord.codePointAt(i);
            if (Character.isLetter(cp) && !Character.isUpperCase(cp)) return false;
            i += Character.charCount(cp);
        }
        return true;
    }

    public boolean isFirstCharCapitalized() {
        if (mTypedWord.length() == 0) return false;
        return Character.isUpperCase(mTypedWord.codePointAt(0));
    }
}
