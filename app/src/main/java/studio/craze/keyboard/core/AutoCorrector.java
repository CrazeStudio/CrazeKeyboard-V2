package studio.craze.keyboard.core;

import java.util.List;

/**
 * Decides whether a just-finished word should be swapped for a learned one. Deliberately cautious:
 * with an empty dictionary it never changes anything, and it only trusts words the person has typed
 * several times.
 */
public final class AutoCorrector {
    private AutoCorrector() {}
    public static final int MIN_COUNT = 4;

    /** @return the replacement, or null to leave the word alone */
    public static String correct(Dictionary d, String typed, String prev, boolean sentenceStart) {
        if (typed == null || WordChars.codePointCount(typed) < 3) return null;
        if (WordChars.isAllUpper(typed) || WordChars.hasInnerUpper(typed)) return null;
        if (!sentenceStart && WordChars.startsUpper(typed)) return null;
        if (d.contains(typed) || d.expansionFor(typed) != null) return null;
        String key = WordChars.normalize(typed);
        List<SuggestionEngine.Scored> options = SuggestionEngine.corrections(d, key, prev, 5);
        SuggestionEngine.Scored best = null;
        for (SuggestionEngine.Scored s : options) {
            if (s.distance != 1) continue;
            int count = d.count(s.key);
            if (count < MIN_COUNT) continue;
            if (s.key.charAt(0) != key.charAt(0) && count < MIN_COUNT * 3) continue;
            best = s;
            break;
        }
        return best == null ? null : WordChars.matchCase(typed, best.display);
    }
}