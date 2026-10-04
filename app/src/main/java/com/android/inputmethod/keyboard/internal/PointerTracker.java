/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.inputmethod.keyboard.internal;

import java.util.ArrayList;
import java.util.HashMap;

public final class PointerTracker {
    public interface KeyEventHandler {
        void onDownKey(int code, String text, boolean isShift);
        void onUpKey(int code, String text, boolean isShift);
        void onLongPress(int code, String text);
        void onSwipeDelete();
        void onMoveCursor(int steps);
        void onHapticFeedback();
    }

    public static final class PointerState {
        public final int pointerId;
        public int downX;
        public int downY;
        public int currentX;
        public int currentY;
        public long downTime;
        public int keyCode;
        public String keyText;
        public boolean isCommitted;
        public boolean isLongPressFired;
        public boolean isDragHandled;
        public float lastSwipeX;

        public PointerState(final int id, final int x, final int y, final long time, final int code, final String text) {
            pointerId = id;
            downX = x;
            downY = y;
            currentX = x;
            currentY = y;
            downTime = time;
            keyCode = code;
            keyText = text;
            isCommitted = false;
            isLongPressFired = false;
            isDragHandled = false;
            lastSwipeX = x;
        }
    }

    private final HashMap<Integer, PointerState> mPointers = new HashMap<>(8);
    private KeyEventHandler mHandler;
    private boolean mIsShifted = false;

    public PointerTracker() {}

    public void setKeyEventHandler(final KeyEventHandler handler) {
        mHandler = handler;
    }

    public void setShifted(final boolean shifted) {
        mIsShifted = shifted;
    }

    public boolean isShifted() {
        return mIsShifted;
    }

    /**
     * AOSP LatinIME rollover typing: when a new pointer touches down, commit any prior
     * uncommitted pointer immediately so rapid S + M sequences cannot lose the first key.
     */
    public void onDown(final int pointerId, final int x, final int y, final long eventTime, final int keyCode, final String keyText) {
        for (final PointerState state : mPointers.values()) {
            if (!state.isCommitted && !state.isLongPressFired && !state.isDragHandled) {
                state.isCommitted = true;
                if (mHandler != null) mHandler.onUpKey(state.keyCode, state.keyText, mIsShifted);
            }
        }

        final PointerState newState = new PointerState(pointerId, x, y, eventTime, keyCode, keyText);
        mPointers.put(pointerId, newState);
        if (mHandler != null) mHandler.onDownKey(keyCode, keyText, mIsShifted);
    }

    /** Marks a pointer as already committed after a DOWN-time key dispatch. */
    public void markCommitted(final int pointerId) {
        final PointerState state = mPointers.get(pointerId);
        if (state != null) state.isCommitted = true;
    }

    public void onMove(final int pointerId, final int x, final int y) {
        final PointerState state = mPointers.get(pointerId);
        if (state == null) return;

        state.currentX = x;
        state.currentY = y;

        if (state.keyCode == ' ' && !state.isCommitted) {
            final float deltaFromLast = x - state.lastSwipeX;
            final float stepThresholdPx = 28f;
            if (Math.abs(deltaFromLast) >= stepThresholdPx) {
                state.isDragHandled = true;
                final int steps = (int) (deltaFromLast / stepThresholdPx);
                if (steps != 0) {
                    state.lastSwipeX += steps * stepThresholdPx;
                    if (mHandler != null) {
                        mHandler.onMoveCursor(steps);
                        mHandler.onHapticFeedback();
                    }
                }
            }
            return;
        }

        if (state.keyCode == -4 && !state.isDragHandled) {
            final int deltaX = x - state.downX;
            if (deltaX < -50) {
                state.isDragHandled = true;
                if (mHandler != null) {
                    mHandler.onSwipeDelete();
                    mHandler.onHapticFeedback();
                }
            }
        }
    }

    public void onUp(final int pointerId) {
        final PointerState state = mPointers.remove(pointerId);
        if (state == null) return;

        if (!state.isCommitted && !state.isLongPressFired && !state.isDragHandled) {
            state.isCommitted = true;
            if (mHandler != null) mHandler.onUpKey(state.keyCode, state.keyText, mIsShifted);
        }
    }

    public void onCancel() {
        mPointers.clear();
    }

    public boolean hasActivePointers() {
        return !mPointers.isEmpty();
    }

    public ArrayList<Integer> getActiveKeyCodes() {
        final ArrayList<Integer> list = new ArrayList<>(mPointers.size());
        for (final PointerState state : mPointers.values()) list.add(state.keyCode);
        return list;
    }
}
