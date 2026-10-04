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

import java.util.Locale;

public final class StringUtils {
    private StringUtils() {}

    public static boolean isLowSurrogate(final char c) {
        return Character.isLowSurrogate(c);
    }

    public static boolean isHighSurrogate(final char c) {
        return Character.isHighSurrogate(c);
    }

    public static int getCodePoint(final char high, final char low) {
        return Character.toCodePoint(high, low);
    }

    public static int getTrailingCodePoint(final CharSequence text) {
        if (text == null || text.length() == 0) return Constants.NOT_A_CODE;
        final int len = text.length();
        final char last = text.charAt(len - 1);
        if (isLowSurrogate(last) && len > 1) {
            final char prev = text.charAt(len - 2);
            if (isHighSurrogate(prev)) {
                return Character.toCodePoint(prev, last);
            }
        }
        return last;
    }

    public static int getTrailingCodePointCount(final CharSequence text, final int charCount) {
        if (text == null || text.length() == 0) return 0;
        final int len = text.length();
        int counted = 0;
        int chars = 0;
        int i = len - 1;
        while (i >= 0 && counted < charCount) {
            final char c = text.charAt(i);
            if (isLowSurrogate(c) && i > 0 && isHighSurrogate(text.charAt(i - 1))) {
                chars += 2;
                i -= 2;
            } else {
                chars += 1;
                i -= 1;
            }
            counted++;
        }
        return chars;
    }

    public static String toTitleCase(final String s, final Locale locale) {
        if (s == null || s.isEmpty()) return s;
        if (s.length() == 1) return s.toUpperCase(locale);
        return s.substring(0, 1).toUpperCase(locale) + s.substring(1).toLowerCase(locale);
    }

    public static boolean hasAtLeastOneLetter(final CharSequence text) {
        if (text == null) return false;
        final int len = text.length();
        for (int i = 0; i < len; ) {
            final int codePoint = Character.codePointAt(text, i);
            if (Character.isLetter(codePoint)) return true;
            i += Character.charCount(codePoint);
        }
        return false;
    }

    public static boolean isSentenceSeparator(final int codePoint) {
        return codePoint == '.' || codePoint == '!' || codePoint == '?' || codePoint == '\n';
    }
}
