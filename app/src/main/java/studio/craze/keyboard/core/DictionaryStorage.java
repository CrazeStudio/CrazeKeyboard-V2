package studio.craze.keyboard.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;

/** Reads and writes a dictionary file. Writes go to a temp file first so a crash never leaves a half file. */
public final class DictionaryStorage {
    private DictionaryStorage() {}

    public static Dictionary load(File file) {
        if (file == null || !file.exists()) return new Dictionary();
        Reader r = null;
        try {
            r = new InputStreamReader(new FileInputStream(file), "UTF-8");
            return Dictionary.read(r);
        } catch (IOException e) {
            return new Dictionary();
        } finally {
            if (r != null) {
                try { r.close(); } catch (IOException ignored) { }
            }
        }
    }

    public static void save(Dictionary d, File file) throws IOException {
        File dir = file.getParentFile();
        if (dir != null && !dir.exists() && !dir.mkdirs()) throw new IOException("cannot create " + dir);
        File tmp = new File(file.getPath() + ".tmp");
        Writer w = null;
        try {
            w = new OutputStreamWriter(new FileOutputStream(tmp), "UTF-8");
            d.write(w);
        } finally {
            if (w != null) w.close();
        }
        if (file.exists() && !file.delete()) throw new IOException("cannot replace " + file);
        if (!tmp.renameTo(file)) throw new IOException("cannot rename " + tmp);
    }
}
