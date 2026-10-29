package studio.craze.keyboard;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.util.List;
import studio.craze.keyboard.core.Dictionary;

/**
 * Dictionary screen: typing features on/off, text shortcuts, the words the keyboard has learned, and
 * a way to delete them. The dictionary starts empty and fills from typing, so there is nothing to import.
 */
public class DictionaryActivity extends Activity {
    private static final String PREFS = "crazekeyboard";
    private static final int WORD_LIMIT = 40;

    private TextView summary;
    private LinearLayout shortcutsBox;
    private LinearLayout wordsBox;
    private EditText search;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setTitle("Dictionary");

        ScrollView scroll = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(16), dp(16), dp(32));
        scroll.addView(col, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        col.addView(heading("Dictionary", 24));
        summary = text("", 14);
        col.addView(summary);
        col.addView(text("The keyboard learns the words you type, on this phone only. Nothing is sent anywhere.", 14));

        col.addView(heading("Typing", 18));
        col.addView(toggle("Learn words as I type", "learn", true));
        col.addView(toggle("Suggestions", "suggestions", true));
        col.addView(toggle("Auto-correct learned words", "autocorrect", true));
        col.addView(toggle("Glide typing", "glide", true));

        col.addView(heading("Text shortcuts", 18));
        col.addView(text("Type a short word and a space to expand it, for example omw to On my way!", 14));
        shortcutsBox = new LinearLayout(this);
        shortcutsBox.setOrientation(LinearLayout.VERTICAL);
        col.addView(shortcutsBox);
        final EditText trigger = field("Shortcut (one word)", InputType.TYPE_CLASS_TEXT);
        final EditText expansion = field("Expands to", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        col.addView(trigger);
        col.addView(expansion);
        Button add = new Button(this);
        add.setText("Add shortcut");
        add.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                boolean ok = dictionary().addShortcut(trigger.getText().toString(), expansion.getText().toString());
                if (!ok) {
                    Toast.makeText(DictionaryActivity.this, "Use one word as the shortcut and add the text it expands to.", Toast.LENGTH_LONG).show();
                    return;
                }
                trigger.setText("");
                expansion.setText("");
                changed();
            }
        });
        col.addView(add);

        col.addView(heading("Learned words", 18));
        search = field("Search words", InputType.TYPE_CLASS_TEXT);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) { refreshWords(); }
        });
        col.addView(search);
        wordsBox = new LinearLayout(this);
        wordsBox.setOrientation(LinearLayout.VERTICAL);
        col.addView(wordsBox);

        Button clear = new Button(this);
        clear.setText("Delete all learned words");
        clear.setTextColor(Color.rgb(180, 35, 24));
        clear.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { confirmClear(); }
        });
        col.addView(clear);

        setContentView(scroll);
        refresh();
    }

    private Dictionary dictionary() { return DictionaryHolder.get(this); }

    private SharedPreferences prefs() { return getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    private void changed() {
        DictionaryHolder.saveAsync(this);
        refresh();
    }

    private void refresh() {
        Dictionary d = dictionary();
        summary.setText("Language: " + DictionaryHolder.currentLanguage(this) + "   Words: " + d.wordCount()
                + "   Word pairs: " + d.bigramCount());
        shortcutsBox.removeAllViews();
        List<String[]> list = d.shortcutList();
        if (list.isEmpty()) {
            shortcutsBox.addView(text("No shortcuts yet.", 14));
        }
        for (final String[] s : list) {
            TextView row = row(s[0] + "  \u2192  " + s[1]);
            row.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { confirmRemoveShortcut(s[0]); }
            });
            shortcutsBox.addView(row);
        }
        refreshWords();
    }

    private void refreshWords() {
        if (wordsBox == null) return;
        wordsBox.removeAllViews();
        List<Dictionary.Entry> found = dictionary().search(search.getText().toString(), WORD_LIMIT);
        if (found.isEmpty()) {
            boolean searching = search.getText().toString().trim().length() > 0;
            wordsBox.addView(text(searching ? "No matching words." : "Nothing learned yet. Words appear here as you type.", 14));
            return;
        }
        for (final Dictionary.Entry e : found) {
            TextView row = row(e.word + "   \u00D7" + e.count);
            row.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { confirmForget(e.word); }
            });
            wordsBox.addView(row);
        }
        wordsBox.addView(text("Tap a word to delete it.", 12));
    }

    private void confirmForget(final String word) {
        new AlertDialog.Builder(this)
                .setTitle("Delete \"" + word + "\"?")
                .setMessage("The keyboard will forget this word and the word pairs it appears in.")
                .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        dictionary().forget(word);
                        changed();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmRemoveShortcut(final String trigger) {
        new AlertDialog.Builder(this)
                .setTitle("Remove shortcut \"" + trigger + "\"?")
                .setPositiveButton("Remove", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        dictionary().removeShortcut(trigger);
                        changed();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmClear() {
        new AlertDialog.Builder(this)
                .setTitle("Delete all learned words?")
                .setMessage("This cannot be undone. Your shortcuts are kept.")
                .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        dictionary().clearWords();
                        changed();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ---- small view helpers ----

    private TextView heading(String value, int sp) {
        TextView t = text(value, sp);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(20), 0, dp(6));
        return t;
    }

    private TextView text(String value, int sp) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setPadding(0, dp(4), 0, dp(4));
        return t;
    }

    private TextView row(String value) {
        TextView t = text(value, 16);
        t.setPadding(0, dp(12), 0, dp(12));
        t.setMinHeight(dp(48));
        t.setClickable(true);
        return t;
    }

    private EditText field(String hint, int inputType) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setInputType(inputType);
        return e;
    }

    private Switch toggle(String title, final String key, boolean fallback) {
        Switch s = new Switch(this);
        s.setText(title);
        s.setTextSize(16);
        s.setMinHeight(dp(48));
        s.setChecked(prefs().getBoolean(key, fallback));
        s.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton button, boolean isChecked) {
                prefs().edit().putBoolean(key, isChecked).apply();
            }
        });
        return s;
    }

    private int dp(int n) { return (int) (n * getResources().getDisplayMetrics().density + 0.5f); }
}
