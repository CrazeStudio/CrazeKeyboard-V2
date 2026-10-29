package studio.craze.keyboard;

import android.content.Context;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import studio.craze.keyboard.core.Dictionary;
import studio.craze.keyboard.core.DictionaryStorage;

final class DictionaryHolder {
    private static final String PREFS = "crazekeyboard";
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static Dictionary dictionary;
    private static String language;
    private DictionaryHolder() {}

    static String currentLanguage(Context context) {
        String lang = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("language", "en");
        if (lang == null) return "en";
        lang = lang.replaceAll("[^A-Za-z0-9_-]", "");
        return lang.isEmpty() ? "en" : lang;
    }

    private static File file(Context context, String lang) {
        return new File(context.getFilesDir(), "dictionary_" + lang + ".txt");
    }

    static synchronized Dictionary get(Context context) {
        Context app = context.getApplicationContext();
        String lang = currentLanguage(app);
        if (dictionary == null || !lang.equals(language)) {
            if (dictionary != null) saveBlocking(app, dictionary, language);
            dictionary = DictionaryStorage.load(file(app, lang));
            language = lang;
        }
        return dictionary;
    }

    static void saveAsync(Context context) {
        final Context app = context.getApplicationContext();
        final Dictionary d;
        final String lang;
        synchronized (DictionaryHolder.class) { d = dictionary; lang = language; }
        if (d == null || !d.consumeDirty()) return;
        IO.execute(new Runnable() {
            @Override public void run() {
                try { DictionaryStorage.save(d, file(app, lang)); }
                catch (IOException e) { d.markDirty(); }
            }
        });
    }

    static synchronized void saveNow(Context context) {
        if (dictionary != null) saveBlocking(context.getApplicationContext(), dictionary, language);
    }

    private static void saveBlocking(Context app, Dictionary d, String lang) {
        if (!d.consumeDirty()) return;
        try { DictionaryStorage.save(d, file(app, lang)); }
        catch (IOException e) { d.markDirty(); }
    }
}