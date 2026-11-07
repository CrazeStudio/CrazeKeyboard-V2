package studio.craze.keyboard.core;

/**
 * Reads the text before the cursor and pulls out the word being typed and the word before it.
 * Works on any script that {@link WordChars} accepts.
 */
public final class TextContext {
    /** The word the cursor is at the end of, or "" when the cursor follows a space or punctuation. */
    public final String word;
    /** The previous whole word, or "" after punctuation, a new line, or at the start of the text. */
    public final String prev;
    /** True when the word (or the next word, if {@link #word} is empty) begins a sentence. */
    public final boolean sentenceStart;
    /** Index in the source text where {@link #word} starts. */
    public final int wordStart;

    private TextContext(String word, String prev, boolean sentenceStart, int wordStart) {
        this.word = word;
        this.prev = prev;
        this.sentenceStart = sentenceStart;
        this.wordStart = wordStart;
    }

    public static TextContext parse(CharSequence before) {
        if (before == null) before = "";
        int end = before.length();
        int ws = scanWordBack(before, end);
        String word = before.subSequence(ws, end).toString();

        // skip whitespace before the word, remembering whether we crossed a line break
        int i = ws;
        int spaces = 0;
        boolean newline = false;
        while (i > 0) {
            char c = before.charAt(i - 1);
            if (c == '\n' || c == '\r') { newline = true; i--; spaces++; }
            else if (Character.isWhitespace(c)) { i--; spaces++; }
            else break;
        }

        String prev = "";
        if (spaces > 0 && !newline && i > 0) {
            int cp = Character.codePointBefore(before, i);
            if (WordChars.isWordChar(cp)) {
                int ps = scanWordBack(before, i);
                prev = before.subSequence(ps, i).toString();
            }
        }

        boolean sentenceStart;
        if (i == 0 || newline) {
            sentenceStart = true;
        } else {
            char last = before.charAt(i - 1);
            sentenceStart = last == '.' || last == '!' || last == '?' || last == '\u0964' || last == '\u2026';
            if (spaces == 0 && !word.isEmpty()) {
                // directly attached punctuation, e.g. "end.word": treat like a new sentence only after a full stop
                sentenceStart = sentenceStart && false;
            }
        }
        return new TextContext(word, prev, sentenceStart, ws);
    }

    private static int scanWordBack(CharSequence s, int end) {
        int i = end;
        while (i > 0) {
            int cp = Character.codePointBefore(s, i);
            if (WordChars.isWordChar(cp)) {
                i -= Character.charCount(cp);
                continue;
            }
            // an apostrophe belongs to the word only when letters sit on both sides
            if (WordChars.isApostrophe(cp) && i < end && i - 1 > 0) {
                int before = Character.codePointBefore(s, i - 1);
                if (WordChars.isWordChar(before)) { i -= 1; continue; }
            }
            break;
        }
        return i;
    }
}
