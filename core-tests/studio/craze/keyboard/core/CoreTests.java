package studio.craze.keyboard.core;

import java.io.File;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;
import java.util.Random;

/** Plain-Java checks for the dictionary core. Run with run-core-tests.sh; exits 1 on any failure. */
public final class CoreTests {
    private static int passed, failed;

    private static void check(boolean ok, String name) {
        if (ok) { passed++; } else { failed++; System.out.println("FAIL: " + name); }
    }

    private static void eq(Object expected, Object actual, String name) {
        boolean ok = expected == null ? actual == null : expected.equals(actual);
        if (!ok) System.out.println("  expected <" + expected + "> got <" + actual + ">");
        check(ok, name);
    }

    private static void learnN(Dictionary d, String w, String prev, int n) {
        for (int i = 0; i < n; i++) d.learn(w, prev, 1000L + i, false);
    }

    public static void main(String[] args) throws Exception {
        wordChars();
        textContext();
        dictionary();
        storage();
        suggestions();
        autocorrect();
        glide();
        performance();
        System.out.println(passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }

    static void wordChars() {
        check(WordChars.isLearnable("hello"), "learnable word");
        check(!WordChars.isLearnable("abc123"), "digits rejected");
        check(!WordChars.isLearnable("aaaaa"), "mashing rejected");
        check(!WordChars.isLearnable("x"), "single latin letter rejected");
        check(WordChars.isLearnable("a"), "a allowed");
        check(WordChars.isLearnable("\u0993"), "single Bengali letter allowed");
        eq("don't", WordChars.normalize("Don\u2019t"), "normalize apostrophe and case");
        eq("Hello", WordChars.matchCase("Hel", "hello"), "matchCase capital");
        eq("HELLO", WordChars.matchCase("HEL", "hello"), "matchCase upper");
        eq("iPhone", WordChars.matchCase("Iph", "iPhone"), "matchCase keeps mixed form");
    }

    static void textContext() {
        TextContext c = TextContext.parse("hello wor");
        eq("wor", c.word, "ctx word");
        eq("hello", c.prev, "ctx prev");
        check(!c.sentenceStart, "ctx not sentence start");

        c = TextContext.parse("hello ");
        eq("", c.word, "ctx empty word after space");
        eq("hello", c.prev, "ctx prev after space");

        c = TextContext.parse("hello, ");
        eq("", c.prev, "punctuation breaks prev");

        c = TextContext.parse("it was fine. Wor");
        check(c.sentenceStart, "sentence start after full stop");

        c = TextContext.parse("I don't");
        eq("don't", c.word, "apostrophe inside word");

        c = TextContext.parse("rock'");
        eq("", c.word, "trailing apostrophe not part of word");

        c = TextContext.parse("\u0986\u09AE\u09BF \u09AD\u09BE\u09B2");
        eq("\u09AD\u09BE\u09B2", c.word, "Bengali word with vowel sign");
        eq("\u0986\u09AE\u09BF", c.prev, "Bengali previous word");

        c = TextContext.parse("line one\nwor");
        eq("", c.prev, "newline breaks prev");
        check(c.sentenceStart, "newline starts sentence");
        check(TextContext.parse("").sentenceStart, "empty text is sentence start");
    }

    static void dictionary() {
        Dictionary d = new Dictionary();
        eq(0, d.wordCount(), "starts empty");
        check(d.learn("Hello", "", 1, true), "learn returns true");
        learnN(d, "hello", "", 2);
        eq(3, d.count("HELLO"), "case-insensitive count");
        learnN(d, "world", "hello", 2);
        eq(2, d.bigramCount("hello", "world"), "bigram counted");
        eq("hello", d.displayForm("hello"), "sentence-start capital stays lower");

        for (int i = 0; i < 3; i++) d.learn("Craze", "", 5, false);
        eq("Craze", d.displayForm("craze"), "name keeps capital");
        for (int i = 0; i < 3; i++) d.learn("iPhone", "", 5, false);
        eq("iPhone", d.displayForm("iphone"), "mixed case kept");

        List<Dictionary.Entry> pre = d.withPrefix("hel", 10);
        eq(1, pre.size(), "prefix lookup");
        eq("hello", pre.get(0).word, "prefix word");
        eq("world", d.nextWords("hello", 3).get(0).word, "next words");

        check(d.addShortcut("omw", "on my way!"), "add shortcut");
        eq("on my way!", d.expansionFor("OMW"), "shortcut lookup");
        check(!d.addShortcut("two words", "x"), "shortcut with space rejected");
        check(d.removeShortcut("omw"), "remove shortcut");

        d.forget("hello");
        eq(0, d.count("hello"), "forget removes word");
        eq(0, d.nextWords("hello", 3).size(), "forget removes pairs");
        eq(0, d.bigramCount(), "pair count back to zero");

        d.addShortcut("brb", "be right back");
        d.clearWords();
        eq(0, d.wordCount(), "clearWords empties words");
        eq(1, d.shortcutList().size(), "clearWords keeps shortcuts");

        check(d.consumeDirty(), "dirty after edits");
        check(!d.consumeDirty(), "dirty resets");

        Dictionary big = new Dictionary();
        for (int i = 0; i < Dictionary.MAX_WORDS + 500; i++) big.learn("w" + toLetters(i), "", i, false);
        check(big.wordCount() <= Dictionary.MAX_WORDS, "word cap enforced");
    }

    static String toLetters(int n) {
        StringBuilder b = new StringBuilder();
        do { b.append((char) ('a' + n % 26)); n /= 26; } while (n > 0);
        return b.reverse().toString() + "x";
    }

    static void storage() throws Exception {
        Dictionary d = new Dictionary();
        learnN(d, "hello", "", 3);
        learnN(d, "world", "hello", 2);
        learnN(d, "\u0986\u09AE\u09BF", "", 2);
        d.learn("iPhone", "", 1, false);
        d.addShortcut("addr", "12 Main St\n\tApt 4 \\ done");

        StringWriter w = new StringWriter();
        d.write(w);
        Dictionary r = Dictionary.read(new StringReader(w.toString()));
        eq(d.wordCount(), r.wordCount(), "roundtrip word count");
        eq(3, r.count("hello"), "roundtrip count");
        eq(2, r.bigramCount("hello", "world"), "roundtrip pair");
        eq(2, r.count("\u0986\u09AE\u09BF"), "roundtrip Bengali");
        eq("12 Main St\n\tApt 4 \\ done", r.expansionFor("addr"), "roundtrip escaped shortcut");
        check(!r.consumeDirty(), "loaded dictionary is clean");

        Dictionary bad = Dictionary.read(new StringReader("CRAZEDICT\t1\nW\tfoo\tx\ny\nW\tok\t2\t0\t0\t5\t\nB\tonly\n"));
        eq(1, bad.wordCount(), "corrupt lines skipped");
        eq(0, Dictionary.read(new StringReader("garbage")).wordCount(), "wrong header gives empty");

        File f = File.createTempFile("craze", ".txt");
        f.delete();
        DictionaryStorage.save(d, f);
        eq(3, DictionaryStorage.load(f).count("hello"), "file save and load");
        DictionaryStorage.save(d, f);
        check(f.exists(), "file overwrite works");
        f.delete();
        eq(0, DictionaryStorage.load(f).wordCount(), "missing file gives empty dictionary");
    }

    static void suggestions() {
        Dictionary d = new Dictionary();
        List<SuggestionEngine.Suggestion> s = SuggestionEngine.suggest(d, "hel", "", false, 3);
        eq(1, s.size(), "empty dictionary shows just typed word");
        eq(0, SuggestionEngine.suggest(d, "", "", false, 3).size(), "empty dictionary, nothing to predict");

        learnN(d, "hello", "", 5);
        learnN(d, "help", "", 2);
        learnN(d, "world", "hello", 3);
        learnN(d, "the", "", 6);

        s = SuggestionEngine.suggest(d, "hel", "", false, 3);
        eq("hel", s.get(0).text, "typed word first");
        eq("hello", s.get(1).text, "most used completion first");
        eq("help", s.get(2).text, "second completion");

        s = SuggestionEngine.suggest(d, "Hel", "", false, 3);
        eq("Hello", s.get(1).text, "completion follows capital");

        s = SuggestionEngine.suggest(d, "", "hello", false, 3);
        eq("world", s.get(0).text, "next word after hello");
        s = SuggestionEngine.suggest(d, "", "hello", true, 3);
        eq("World", s.get(0).text, "next word capitalised at sentence start");

        s = SuggestionEngine.suggest(d, "teh", "", false, 3);
        boolean hasThe = false;
        for (SuggestionEngine.Suggestion x : s) if (x.text.equals("the")) hasThe = true;
        check(hasThe, "typo offers the learned word");
    }

    static void autocorrect() {
        Dictionary d = new Dictionary();
        check(AutoCorrector.correct(d, "teh", "", false) == null, "empty dictionary never corrects");

        learnN(d, "the", "", 3);
        check(AutoCorrector.correct(d, "teh", "", false) == null, "rarely typed word not trusted");
        learnN(d, "the", "", 2);
        eq("the", AutoCorrector.correct(d, "teh", "", false), "swap corrected");
        eq("The", AutoCorrector.correct(d, "Teh", "", true), "capital kept at sentence start");
        check(AutoCorrector.correct(d, "Teh", "", false) == null, "mid-sentence capital treated as a name");
        check(AutoCorrector.correct(d, "TEH", "", false) == null, "all caps left alone");
        check(AutoCorrector.correct(d, "te", "", false) == null, "short words left alone");

        learnN(d, "teh", "", 1);
        check(AutoCorrector.correct(d, "teh", "", false) == null, "known word never corrected");

        Dictionary e = new Dictionary();
        learnN(e, "zen", "", 10);
        eq("zen", AutoCorrector.correct(e, "zed", "", false), "frequent word corrects a near miss");
        check(AutoCorrector.correct(e, "xen", "", false) == null, "changed first letter needs a much more frequent word");
        learnN(e, "zen", "", 4);
        eq("zen", AutoCorrector.correct(e, "xen", "", false), "changed first letter allowed when very frequent");
        check(AutoCorrector.correct(e, "zxyw", "", false) == null, "far-off words are not corrected");
        d.addShortcut("thx", "thanks");
        learnN(d, "thanks", "", 10);
        check(AutoCorrector.correct(d, "thx", "", false) == null, "shortcut triggers are not corrected");
    }

    static GlideDecoder.Layout qwerty() {
        GlideDecoder.Layout l = new GlideDecoder.Layout(36, 48);
        String[] rows = {"qwertyuiop", "asdfghjkl", "zxcvbnm"};
        float[] offset = {0, 18, 54};
        for (int r = 0; r < rows.length; r++) {
            for (int c = 0; c < rows[r].length(); c++) {
                l.put(rows[r].charAt(c), offset[r] + c * 36 + 18, r * 48 + 24);
            }
        }
        return l;
    }

    static float[][] pathFor(String word, GlideDecoder.Layout l, float noise, long seed) {
        Random rnd = new Random(seed);
        java.util.ArrayList<float[]> pts = new java.util.ArrayList<float[]>();
        float[] prev = null;
        for (int i = 0; i < word.length(); i++) {
            float[] c = l.centers.get((int) word.charAt(i));
            if (prev != null && prev == c) continue;
            if (prev != null) {
                for (int s = 1; s <= 6; s++) {
                    float t = s / 6f;
                    pts.add(new float[]{prev[0] + (c[0] - prev[0]) * t + (rnd.nextFloat() - .5f) * noise,
                            prev[1] + (c[1] - prev[1]) * t + (rnd.nextFloat() - .5f) * noise});
                }
            } else {
                pts.add(new float[]{c[0], c[1]});
            }
            prev = c;
        }
        return pts.toArray(new float[0][]);
    }

    static void glide() {
        GlideDecoder.Layout l = qwerty();
        Dictionary d = new Dictionary();
        check(GlideDecoder.decode(pathFor("hello", l, 0, 1), l, d, 3).isEmpty(), "empty dictionary gives no glide words");

        learnN(d, "hello", "", 3);
        learnN(d, "help", "", 2);
        learnN(d, "world", "", 2);
        learnN(d, "would", "", 1);
        learnN(d, "hold", "", 1);
        learnN(d, "the", "", 5);
        learnN(d, "this", "", 2);

        eq("hello", GlideDecoder.decode(pathFor("hello", l, 0, 1), l, d, 3).get(0), "clean path: hello");
        eq("hello", GlideDecoder.decode(pathFor("hello", l, 12, 7), l, d, 3).get(0), "noisy path: hello");
        eq("world", GlideDecoder.decode(pathFor("world", l, 12, 3), l, d, 3).get(0), "noisy path: world");
        eq("the", GlideDecoder.decode(pathFor("the", l, 10, 5), l, d, 3).get(0), "noisy path: the");
        eq("help", GlideDecoder.decode(pathFor("help", l, 6, 9), l, d, 3).get(0), "noisy path: help");

        check(GlideDecoder.decode(new float[][]{{10, 10}, {12, 11}}, l, d, 3).isEmpty(), "tiny movement is not a glide");
        check(GlideDecoder.decode(null, l, d, 3).isEmpty(), "null path safe");
    }

    static void performance() {
        Dictionary d = new Dictionary();
        Random rnd = new Random(1);
        for (int i = 0; i < 25000; i++) {
            StringBuilder b = new StringBuilder();
            int n = 3 + rnd.nextInt(7);
            for (int k = 0; k < n; k++) b.append((char) ('a' + rnd.nextInt(26)));
            d.learn(b.toString(), "", i, false);
        }
        learnN(d, "hello", "", 5);
        long t0 = System.nanoTime();
        for (int i = 0; i < 20; i++) SuggestionEngine.suggest(d, "helo", "", false, 3);
        long suggestMs = (System.nanoTime() - t0) / 20 / 1000000;
        GlideDecoder.Layout l = qwerty();
        float[][] p = pathFor("hello", l, 8, 2);
        t0 = System.nanoTime();
        for (int i = 0; i < 10; i++) GlideDecoder.decode(p, l, d, 3);
        long glideMs = (System.nanoTime() - t0) / 10 / 1000000;
        System.out.println("timing with " + d.wordCount() + " words: suggest " + suggestMs + " ms, glide " + glideMs + " ms (desktop JVM, informational)");
        check(suggestMs < 200 && glideMs < 400, "performance sanity");
    }
}
