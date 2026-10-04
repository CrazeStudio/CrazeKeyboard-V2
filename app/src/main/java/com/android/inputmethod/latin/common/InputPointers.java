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

package com.android.inputmethod.latin.common;

import java.util.Arrays;

public final class InputPointers {
    private static final int DEFAULT_CAPACITY = 48;

    private int mDefaultCapacity;
    private int[] mXCoordinates;
    private int[] mYCoordinates;
    private int[] mPointerIds;
    private int[] mTimes;
    private int mLength;

    public InputPointers() {
        this(DEFAULT_CAPACITY);
    }

    public InputPointers(final int defaultCapacity) {
        mDefaultCapacity = defaultCapacity;
        mXCoordinates = new int[defaultCapacity];
        mYCoordinates = new int[defaultCapacity];
        mPointerIds = new int[defaultCapacity];
        mTimes = new int[defaultCapacity];
        mLength = 0;
    }

    public void reset() {
        mLength = 0;
    }

    public int getPointerSize() {
        return mLength;
    }

    public int[] getXCoordinates() {
        return mXCoordinates;
    }

    public int[] getYCoordinates() {
        return mYCoordinates;
    }

    public int[] getPointerIds() {
        return mPointerIds;
    }

    public int[] getTimes() {
        return mTimes;
    }

    public void addPointerAt(final int index, final int x, final int y, final int pointerId, final int time) {
        if (mLength >= mXCoordinates.length) {
            final int newCapacity = mXCoordinates.length * 2;
            mXCoordinates = Arrays.copyOf(mXCoordinates, newCapacity);
            mYCoordinates = Arrays.copyOf(mYCoordinates, newCapacity);
            mPointerIds = Arrays.copyOf(mPointerIds, newCapacity);
            mTimes = Arrays.copyOf(mTimes, newCapacity);
        }
        mXCoordinates[index] = x;
        mYCoordinates[index] = y;
        mPointerIds[index] = pointerId;
        mTimes[index] = time;
        if (index >= mLength) {
            mLength = index + 1;
        }
    }

    public void appendPointer(final int x, final int y, final int pointerId, final int time) {
        addPointerAt(mLength, x, y, pointerId, time);
    }
}
