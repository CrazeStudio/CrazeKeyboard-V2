package studio.craze.keyboard;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends Activity {
    private static final int ACCENT = Color.rgb(104, 82, 168);
    private static final int BG = Color.rgb(247, 247, 250);
    private static final int SURFACE = Color.WHITE;
    private static final int INK = Color.rgb(31, 31, 37);
    private static final int MUTED = Color.rgb(101, 101, 113);
    private static final int LINE = Color.rgb(228, 227, 233);
    private LinearLayout page;
    private LinearLayout content;
    private EditText search;
    private String currentSection = "Home";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        showSection("Home");
    }

    private void showSection(String section) {
        currentSection = section;
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20), dp(18), dp(20), dp(28));
        page.setBackgroundColor(BG);
        scroll.addView(page);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setOrientation(LinearLayout.HORIZONTAL);
        TextView brand = text("C", 22, Color.WHITE, true);
        brand.setGravity(Gravity.CENTER);
        brand.setBackground(round(ACCENT, dp(13)));
        top.addView(brand, new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout brandText = new LinearLayout(this);
        brandText.setOrientation(LinearLayout.VERTICAL);
        brandText.setPadding(dp(12), 0, 0, 0);
        brandText.addView(text("CrazeKeyboard", 18, INK, true));
        brandText.addView(text("OFFLINE INPUT", 11, MUTED, true));
        top.addView(brandText, new LinearLayout.LayoutParams(0, -2, 1));
        if (!"Home".equals(section)) {
            Button back = button("Back", Color.rgb(237, 234, 245), INK);
            top.addView(back, new LinearLayout.LayoutParams(dp(72), dp(42)));
            back.setOnClickListener(v -> showSection("Home"));
        }
        page.addView(top);
        if ("Home".equals(section)) renderHome();
        else renderDetail(section);
        setContentView(scroll);
    }

    private void renderHome() {
        addSpace(24);
        page.addView(text("A keyboard that puts you in control.", 27, INK, true));
        addSpace(8);
        page.addView(text("Private by default. Your typing stays on your device.", 15, MUTED, false));
        addSpace(20);

        LinearLayout setup = card();
        setup.addView(text("GET STARTED", 11, ACCENT, true));
        addSpaceTo(setup, 8);
        setup.addView(text("Enable CrazeKeyboard, then select it as your current input method.", 14, MUTED, false));
        addSpaceTo(setup, 14);
        Button enable = button("Enable keyboard", ACCENT, Color.WHITE);
        setup.addView(enable, new LinearLayout.LayoutParams(-1, dp(48)));
        enable.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        addSpaceTo(setup, 8);
        Button choose = button("Choose keyboard", Color.rgb(237,234,245), INK);
        setup.addView(choose, new LinearLayout.LayoutParams(-1, dp(48)));
        choose.setOnClickListener(v -> {
            InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showInputMethodPicker();
        });
        page.addView(setup);

        addSpace(18);
        page.addView(text("SETTINGS", 12, MUTED, true));
        addSpace(8);
        search = new EditText(this);
        search.setSingleLine(true);
        search.setTextSize(15);
        search.setHint("Search settings");
        search.setCompoundDrawablePadding(dp(8));
        search.setPadding(dp(14), 0, dp(14), 0);
        search.setBackground(round(SURFACE, dp(14)));
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(-1, dp(48));
        searchLp.bottomMargin = dp(10);
        page.addView(search, searchLp);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        page.addView(content);
        renderCategoryList("");
        search.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { renderCategoryList(s.toString()); }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });

        addSpace(16);
        LinearLayout privacy = card();
        privacy.addView(text("PRIVACY", 11, ACCENT, true));
        addSpaceTo(privacy, 7);
        privacy.addView(text("This build requests no Internet permission and has no account or cloud-sync feature. Voice input and full language models are not bundled yet.", 14, MUTED, false));
        page.addView(privacy);
        addSpace(18);
        page.addView(text("CrazeKeyboard · Preview build", 12, MUTED, false));
    }

    private void renderCategoryList(String query) {
        if (content == null) return;
        content.removeAllViews();
        List<String[]> rows = Arrays.asList(
            new String[]{"Languages & models", "Choose keyboard languages and local models", "Aa"},
            new String[]{"Keyboard & typing", "Size, key behaviour and layout", "⌨"},
            new String[]{"Text prediction", "Suggestions and typing assistance", "✧"},
            new String[]{"Voice input", "Offline speech input status", "◖"},
            new String[]{"Personal dictionary", "Words you want to keep available", "＋"},
            new String[]{"Theme", "Colors, dark mode and key borders", "◐"},
            new String[]{"Help & feedback", "Privacy notes and troubleshooting", "?"},
            new String[]{"Developer settings", "Build and diagnostic information", "</>"}
        );
        for (String[] row : rows) {
            if (!query.isEmpty() && !(row[0] + " " + row[1]).toLowerCase().contains(query.toLowerCase())) continue;
            LinearLayout item = new LinearLayout(this);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(dp(14), dp(12), dp(12), dp(12));
            item.setBackground(round(SURFACE, dp(15)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = dp(8);
            content.addView(item, lp);
            TextView icon = text(row[2], 20, ACCENT, true);
            icon.setGravity(Gravity.CENTER);
            item.addView(icon, new LinearLayout.LayoutParams(dp(42), dp(42)));
            LinearLayout words = new LinearLayout(this);
            words.setOrientation(LinearLayout.VERTICAL);
            words.setPadding(dp(10), 0, dp(6), 0);
            words.addView(text(row[0], 15, INK, true));
            addSpaceTo(words, 3);
            words.addView(text(row[1], 12, MUTED, false));
            item.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
            item.addView(text("›", 25, MUTED, false));
            item.setClickable(true);
            item.setFocusable(true);
            item.setOnClickListener(v -> showSection(row[0]));
        }
        if (content.getChildCount() == 0) content.addView(text("No settings match that search.", 14, MUTED, false));
    }

    private void renderDetail(String section) {
        addSpace(24);
        page.addView(text(section, 27, INK, true));
        addSpace(8);
        String description;
        switch (section) {
            case "Languages & models": description = "Select the character layout used by the keyboard."; break;
            case "Keyboard & typing": description = "Adjust the keyboard surface and key behaviour."; break;
            case "Text prediction": description = "Manage the local suggestion strip. No text is sent to a server."; break;
            case "Voice input": description = "Offline speech recognition is not bundled in this preview."; break;
            case "Personal dictionary": description = "Personal word storage is planned. No words are uploaded."; break;
            case "Theme": description = "Choose a comfortable appearance for your keyboard."; break;
            case "Help & feedback": description = "CrazeKeyboard is an independent, privacy-first keyboard project."; break;
            default: description = "Local build information and developer options."; break;
        }
        page.addView(text(description, 15, MUTED, false));
        addSpace(20);

        switch (section) {
            case "Languages & models": {
                String lang = prefs().getString("language", "en");
                option("English (US)", "QWERTY layout", "en".equals(lang), () -> setPref("language", "en"));
                option("বাংলা", "Direct Bengali character layout; phonetic transliteration is not included", "bn".equals(lang), () -> setPref("language", "bn"));
                infoCard("Language models", "The current build uses a small built-in English word list. Downloadable model support is not included.");
                break;
            }
            case "Keyboard & typing": {
                int height = prefs().getInt("height", 304);
                LinearLayout panel = card();
                panel.addView(text("Keyboard height", 16, INK, true));
                TextView heightLabel = text(height + " dp", 14, MUTED, false);
                panel.addView(heightLabel);
                SeekBar bar = new SeekBar(this);
                bar.setMax(100);
                bar.setProgress(Math.max(0, Math.min(100, (height - 260) * 100 / 100)));
                panel.addView(bar);
                bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        int value = 260 + progress;
                        heightLabel.setText(value + " dp");
                        if (fromUser) setPref("height", value);
                    }
                    public void onStartTrackingTouch(SeekBar seekBar) {}
                    public void onStopTrackingTouch(SeekBar seekBar) {}
                });
                page.addView(panel);
                toggle("Key borders", "Show a subtle outline around keys", "key_borders", false, null);
                toggle("Haptic feedback", "Setting is stored; vibration feedback is not wired yet", "haptics", false, null);
                break;
            }
            case "Text prediction":
                toggle("Show suggestions", "Show local word matches above the keyboard", "suggestions", true, null);
                infoCard("Offline prediction", "This preview uses a small static word list. Full autocorrection, next-word prediction and on-device learning are not implemented yet.");
                break;
            case "Voice input":
                infoCard("Not available yet", "Voice input needs an offline speech model and runtime integration. This build does not record or transmit audio.");
                break;
            case "Personal dictionary": {
                EditText word = new EditText(this);
                word.setSingleLine(true); word.setTextSize(15); word.setHint("Add a word (planned)");
                word.setPadding(dp(14), 0, dp(14), 0); word.setBackground(round(SURFACE, dp(12)));
                page.addView(word, new LinearLayout.LayoutParams(-1, dp(48)));
                addSpace(10);
                Button add = button("Save word", ACCENT, Color.WHITE);
                page.addView(add, new LinearLayout.LayoutParams(-1, dp(48)));
                add.setOnClickListener(v -> infoCard("Not available yet", "Personal dictionary storage is not implemented in this preview."));
                break;
            }
            case "Theme":
                toggle("Dark keyboard", "Use a darker keyboard surface", "dark", false, null);
                toggle("Key borders", "Separate keys with a fine outline", "key_borders", false, null);
                infoCard("Theme editor", "Custom background images and a full color editor are planned for a later build.");
                break;
            case "Help & feedback":
                infoCard("Privacy", "The Android manifest has no Internet permission. Core typing works locally. Avoid using preview software for critical or sensitive workflows until tested.");
                infoCard("Known limitations", "No glide typing, no voice model, no full emoji picker, no clipboard history, and no phonetic Bengali input yet.");
                break;
            default:
                infoCard("Build", "CrazeKeyboard preview · Java / Android InputMethodService");
                infoCard("Network", "No Internet permission is declared.");
                infoCard("Diagnostics", "Runtime diagnostics and exportable logs are not implemented.");
                break;
        }
    }

    private void option(String title, String subtitle, boolean selected, Runnable action) {
        LinearLayout item = card();
        item.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.addView(text(title, 16, INK, true));
        addSpaceTo(words, 4);
        words.addView(text(subtitle, 13, MUTED, false));
        item.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        TextView mark = text(selected ? "●" : "○", 21, ACCENT, true);
        item.addView(mark);
        item.setOnClickListener(v -> { action.run(); showSection(currentSection); });
        page.addView(item);
        addSpace(8);
    }

    private void toggle(String title, String subtitle, String key, boolean defaultValue, Runnable onChange) {
        LinearLayout item = card();
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.addView(text(title, 15, INK, true));
        addSpaceTo(words, 3);
        words.addView(text(subtitle, 12, MUTED, false));
        item.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        Switch sw = new Switch(this);
        sw.setChecked(prefs().getBoolean(key, defaultValue));
        item.addView(sw);
        sw.setOnCheckedChangeListener((button, checked) -> {
            prefs().edit().putBoolean(key, checked).apply();
            if (onChange != null) onChange.run();
        });
        page.addView(item);
        addSpace(8);
    }

    private void infoCard(String title, String body) {
        LinearLayout panel = card();
        panel.addView(text(title, 15, INK, true));
        addSpaceTo(panel, 6);
        panel.addView(text(body, 13, MUTED, false));
        page.addView(panel);
        addSpace(10);
    }

    private void addSpace(int n) {
        View v = new View(this);
        page.addView(v, new LinearLayout.LayoutParams(1, dp(n)));
    }
    private void addSpaceTo(LinearLayout parent, int n) {
        View v = new View(this);
        parent.addView(v, new LinearLayout.LayoutParams(1, dp(n)));
    }
    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(16), dp(16), dp(16));
        c.setBackground(round(SURFACE, dp(17)));
        return c;
    }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value); t.setTextSize(size); t.setTextColor(color);
        t.setIncludeFontPadding(true);
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }
    private Button button(String value, int bg, int fg) {
        Button b = new Button(this);
        b.setText(value); b.setTextColor(fg); b.setAllCaps(false); b.setTextSize(14);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setBackground(round(bg, dp(13)));
        return b;
    }
    private GradientDrawable round(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color); d.setCornerRadius(radius);
        d.setStroke(dp(1), LINE);
        return d;
    }
    private android.content.SharedPreferences prefs() {
        return getSharedPreferences("crazekeyboard", MODE_PRIVATE);
    }
    private void setPref(String key, String value) {
        prefs().edit().putString(key, value).apply();
    }
    private void setPref(String key, int value) {
        prefs().edit().putInt(key, value).apply();
    }
    private int dp(int n) {
        return (int)(n * getResources().getDisplayMetrics().density + 0.5f);
    }
}
