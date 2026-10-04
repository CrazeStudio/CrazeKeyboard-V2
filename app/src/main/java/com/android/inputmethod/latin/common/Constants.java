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

public final class Constants {
    private Constants() {}

    public static final int NOT_A_CODE = -1;
    public static final int NOT_A_COORDINATE = -1;

    public static final int CODE_ENTER = '\n';
    public static final int CODE_TAB = '\t';
    public static final int CODE_SPACE = ' ';
    public static final int CODE_PERIOD = '.';
    public static final int CODE_COMMA = ',';
    public static final int CODE_DASH = '-';
    public static final int CODE_SINGLE_QUOTE = '\'';
    public static final int CODE_DOUBLE_QUOTE = '"';
    public static final int CODE_SLASH = '/';
    public static final int CODE_BACKSLASH = '\\';

    public static final int CODE_SHIFT = -1;
    public static final int CODE_SWITCH_ALPHA_SYMBOL = -2;
    public static final int CODE_OUTPUT_TEXT = -3;
    public static final int CODE_DELETE = -4;
    public static final int CODE_SETTINGS = -5;
    public static final int CODE_SHORTCUT = -6;
    public static final int CODE_ACTION_ENTER = -7;
    public static final int CODE_ACTION_NEXT = -8;
    public static final int CODE_ACTION_PREVIOUS = -9;
    public static final int CODE_LANGUAGE_SWITCH = -10;
    public static final int CODE_EMOJI = -11;
    public static final int CODE_ALPHA_FROM_SYMBOL = -12;
    public static final int CODE_SYMBOL_FROM_ALPHA = -13;

    public static final int SUGGESTION_COUNT = 3;
    public static final int MAX_PREV_KEY_COUNT_BY_POINTER = 2;
}
