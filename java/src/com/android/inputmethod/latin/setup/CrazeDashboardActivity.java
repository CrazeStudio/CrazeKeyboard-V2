/*
 * Copyright (C) 2026 The CrazeStudio Authors
 * Licensed under the Apache License, Version 2.0.
 */
package com.android.inputmethod.latin.setup;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.android.inputmethod.keyboard.KeyboardTheme;
import com.android.inputmethod.latin.CBoardTheme;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.settings.Settings;
import com.android.inputmethod.latin.settings.SettingsActivity;
import java.util.List;

/** CrazeKeyboard "Aurora" companion app: playground, theme studio, settings and feedback tuning. */
public final class CrazeDashboardActivity extends Activity {
    private static final int KEYBOARD = 0, THEMES = 1, SETTINGS = 2, HAPTICS = 3;
    private static final int TYPING = 4, LAYOUTS = 5, CORRECTION = 6, ADVANCED = 7, DEBUG = 8;
    public static final String EXTRA_START_TAB = "craze_start_tab";
    public static final String EXTRA_START_PAGE = "craze_start_page";

    // Instance tokens are resolved from system appearance, dynamic colors or saved custom colors.
    private int BG, SURFACE, HIGH, INPUT, TEXT, MUTED, BORDER, INDIGO, INDIGO_BG;
    private int ORANGE, CORAL, ON_ORANGE, OK = 0xFF168454, OK_BG = 0xFFE4F5EC;
    private CBoardTheme palette;
    private LinearLayout rootView;

    private int tab = KEYBOARD, page = KEYBOARD;
    private String playgroundText = "";
    private SharedPreferences prefs;
    private FrameLayout pageHost;
    private TextView subtitle;
    private LinearLayout[] navItems;
    private TextView[] navIcons, navLabels;

    private static final class Preset {
        final int id, panel, key, edge, accent;
        final String name, description;
        final boolean dark;
        Preset(int id, String name, String description, int panel, int key, int edge, int accent, boolean dark) {
            this.id = id; this.name = name; this.description = description;
            this.panel = panel; this.key = key; this.edge = edge; this.accent = accent; this.dark = dark;
        }
    }

    private final Preset[] presets = {
        new Preset(3, "Aurora Light", "Clean white keys, soft gray panel, orange accents",
                0xFFD5D9E0, 0xFFFAFBFD, 0xFFB8BEC8, ORANGE, false),
        new Preset(4, "Aurora Dark", "Deep midnight keys with glowing orange accents",
                0xFF151927, 0xFF2A3042, 0xFF0B0D14, ORANGE, true)
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        applyUiTheme();
        setTheme(palette.dark ? R.style.CrazeDashboardDarkTheme : R.style.CrazeDashboardTheme);
        tab = Math.max(KEYBOARD, Math.min(SETTINGS,
                getIntent().getIntExtra(EXTRA_START_TAB, KEYBOARD)));
        page = Math.max(KEYBOARD, Math.min(DEBUG, getIntent().getIntExtra(EXTRA_START_PAGE, tab)));
        if (page > SETTINGS) tab = SETTINGS;
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        LinearLayout root = new LinearLayout(this);
        rootView = root;
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        setContentView(root);
        buildHeader(root);
        pageHost = new FrameLayout(this);
        root.addView(pageHost, new LinearLayout.LayoutParams(-1, 0, 1f));
        buildNav(root);
        render();
    }

    @Override protected void onResume() {
        super.onResume();
        if (pageHost != null) render();
    }

    // ---------------------------------------------------------------- chrome

    private void applyUiTheme() {
        palette = CBoardTheme.resolve(this);
        BG = palette.background; SURFACE = palette.surface; HIGH = palette.panel;
        INPUT = palette.key; TEXT = palette.text; MUTED = palette.secondaryText;
        BORDER = palette.border; INDIGO = palette.accent; ORANGE = palette.accent;
        CORAL = palette.accent; ON_ORANGE = palette.accentText;
        INDIGO_BG = blendColor(INDIGO, SURFACE, .13f);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        int bars = palette.dark ? 0 : (View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        getWindow().getDecorView().setSystemUiVisibility(bars);
        if (rootView != null) rootView.setBackgroundColor(BG);
    }

    private int blendColor(int a, int b, float amount) {
        int r = Math.round(Color.red(a) * (1 - amount) + Color.red(b) * amount);
        int g = Math.round(Color.green(a) * (1 - amount) + Color.green(b) * amount);
        int bl = Math.round(Color.blue(a) * (1 - amount) + Color.blue(b) * amount);
        return Color.rgb(r, g, bl);
    }

    private void buildHeader(LinearLayout root) {
        LinearLayout h = row();
        h.setPadding(dp(18), dp(14), dp(16), dp(12));
        TextView logo = txt("C", 20, ON_ORANGE, true);
        logo.setGravity(Gravity.CENTER);
        logo.setBackground(gradient(12));
        h.addView(logo, new LinearLayout.LayoutParams(dp(38), dp(38)));
        LinearLayout brand = col();
        brand.setPadding(dp(12), 0, 0, 0);
        brand.addView(txt("CrazeKeyboard", 18, TEXT, true));
        subtitle = txt("", 9, INDIGO, true);
        subtitle.setLetterSpacing(.1f);
        brand.addView(subtitle);
        h.addView(brand, new LinearLayout.LayoutParams(0, -2, 1f));
        h.addView(chip("⌨  Switch", new View.OnClickListener() {
            @Override public void onClick(View v) { pickKeyboard(); }
        }));
        root.addView(h);
    }

    private void buildNav(LinearLayout root) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setBackgroundColor(BG);
        wrap.setPadding(dp(16), dp(8), dp(16), dp(12));
        LinearLayout nav = row();
        nav.setPadding(dp(4), dp(4), dp(4), dp(4));
        nav.setBackground(bg(SURFACE, 24, BORDER));
        String[] icons = {"⌨", "◈", "☷"}, names = {"Keyboard", "Themes", "Settings"};
        navItems = new LinearLayout[3]; navIcons = new TextView[3]; navLabels = new TextView[3];
        for (int i = 0; i < 3; i++) {
            final int dest = i;
            LinearLayout item = col();
            item.setGravity(Gravity.CENTER);
            item.setPadding(0, dp(7), 0, dp(7));
            TextView icon = txt(icons[i], 18, MUTED, true);
            icon.setGravity(Gravity.CENTER);
            TextView label = txt(names[i], 10, MUTED, true);
            label.setGravity(Gravity.CENTER);
            item.addView(icon); item.addView(label);
            item.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { tab = dest; page = dest; render(); }
            });
            nav.addView(item, new LinearLayout.LayoutParams(0, -2, 1f));
            navItems[i] = item; navIcons[i] = icon; navLabels[i] = label;
        }
        wrap.addView(nav, new LinearLayout.LayoutParams(-1, -2));
        root.addView(wrap);
    }

    private void render() {
        if (pageHost == null) return;
        applyUiTheme();
        if (rootView != null) rootView.setBackgroundColor(BG);
        LinearLayout content = col();
        content.setPadding(dp(16), dp(8), dp(16), dp(24));
        if (page == HAPTICS) buildHaptics(content);
        else if (page == TYPING) buildTyping(content);
        else if (page == LAYOUTS) buildLayouts(content);
        else if (page == CORRECTION) buildCorrection(content);
        else if (page == ADVANCED) buildAdvanced(content);
        else if (page == DEBUG) buildDeveloper(content);
        else if (tab == THEMES) buildThemes(content);
        else if (tab == SETTINGS) buildSettings(content);
        else buildKeyboard(content);
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(content);
        pageHost.removeAllViews();
        pageHost.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        updateNav();
    }

    private void updateNav() {
        subtitle.setText(page == HAPTICS ? "HAPTICS & SOUNDS" : tab == THEMES ? "THEME STUDIO"
                : tab == SETTINGS ? "CONTROL CENTER" : "PLAYGROUND");
        for (int i = 0; i < 3; i++) {
            boolean active = page != HAPTICS && tab == i;
            navItems[i].setBackground(active ? gradient(20) : null);
            navIcons[i].setTextColor(active ? ON_ORANGE : MUTED);
            navLabels[i].setTextColor(active ? ON_ORANGE : MUTED);
        }
    }

    // ----------------------------------------------------------------- pages

    private void buildKeyboard(LinearLayout c) {
        boolean enabled = imeEnabled(), selected = imeSelected();

        LinearLayout hero = col();
        hero.setPadding(dp(18), dp(18), dp(18), dp(18));
        hero.setBackground(heroBg());
        hero.addView(txt("Type fast. Type beautiful.", 22, ON_ORANGE, true));
        gap(hero, 4);
        hero.addView(txt("Aurora keys, fully offline, built for speed.", 12, 0xCC2A1405, false));
        gap(hero, 14);
        TextView open = button("Start typing", ON_ORANGE, 0xFFFFE2C7, new View.OnClickListener() {
            @Override public void onClick(View v) { page = KEYBOARD; tab = KEYBOARD; render(); focusPlayground(); }
        });
        hero.addView(open, new LinearLayout.LayoutParams(-2, -2));
        c.addView(hero);
        gap(c, 12);

        LinearLayout setup = card();
        setup.addView(txt("SETUP", 10, INDIGO, true));
        gap(setup, 8);
        stepRow(setup, "Enable CrazeKeyboard", "Turn it on in Android input settings", enabled, "Enable",
                new View.OnClickListener() { @Override public void onClick(View v) { openImeSettings(); } });
        divider(setup);
        stepRow(setup, "Choose as current keyboard", "Pick CrazeKeyboard in the input picker", selected, "Choose",
                new View.OnClickListener() { @Override public void onClick(View v) { pickKeyboard(); } });
        c.addView(setup);
        gap(c, 12);

        LinearLayout stage = card();
        LinearLayout head = row();
        head.addView(txt("Live stage", 15, TEXT, true), new LinearLayout.LayoutParams(0, -2, 1f));
        head.addView(pill("REAL IME", INDIGO, INDIGO_BG));
        stage.addView(head);
        gap(stage, 10);
        final EditText edit = editor(playgroundText, "Tap here and type with CrazeKeyboard…", 3);
        stage.addView(edit, new LinearLayout.LayoutParams(-1, -2));
        final TextView count = txt(counts(playgroundText), 11, MUTED, false);
        gap(stage, 6);
        stage.addView(count);
        edit.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int d) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int d) {
                playgroundText = s.toString();
                count.setText(counts(playgroundText));
            }
            @Override public void afterTextChanged(Editable e) {}
        });
        gap(stage, 10);
        LinearLayout actions = row();
        addWeight(actions, button("Clear", HIGH, TEXT, new View.OnClickListener() {
            @Override public void onClick(View v) { edit.setText(""); edit.requestFocus(); }
        }));
        gapX(actions, 8);
        addWeight(actions, button("Copy", HIGH, TEXT, new View.OnClickListener() {
            @Override public void onClick(View v) { copy(edit.getText().toString()); }
        }));
        gapX(actions, 8);
        addWeight(actions, primary("Open keyboard", new View.OnClickListener() {
            @Override public void onClick(View v) {
                edit.requestFocus();
                InputMethodManager m = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (m != null) m.showSoftInput(edit, InputMethodManager.SHOW_IMPLICIT);
            }
        }));
        stage.addView(actions);
        c.addView(stage);
        gap(c, 12);

        LinearLayout quick = row();
        addWeight(quick, button("◈  Themes", HIGH, ORANGE, new View.OnClickListener() {
            @Override public void onClick(View v) { tab = THEMES; page = THEMES; render(); }
        }));
        gapX(quick, 8);
        addWeight(quick, button("◉  Haptics", HIGH, INDIGO, new View.OnClickListener() {
            @Override public void onClick(View v) { page = HAPTICS; render(); }
        }));
        c.addView(quick);
    }

    private void focusPlayground() { /* the editor requests focus when tapped; kept for future autofocus */ }

    private void buildThemes(LinearLayout c) {
        heading(c, "Theme Studio", "Choose how CBoard follows your colors.");
        gap(c, 14);
        LinearLayout modes = card();
        modes.addView(txt("COLOR MODE", 10, INDIGO, true));
        gap(modes, 8);
        String mode = CBoardTheme.mode(this);
        addThemeMode(modes, "System", CBoardTheme.SYSTEM, mode);
        addThemeMode(modes, "Light", CBoardTheme.LIGHT, mode);
        addThemeMode(modes, "Dark", CBoardTheme.DARK, mode);
        addThemeMode(modes, "Custom", CBoardTheme.CUSTOM, mode);
        c.addView(modes);
        if (CBoardTheme.CUSTOM.equals(mode)) {
            gap(c, 12);
            LinearLayout custom = card();
            custom.addView(txt("CUSTOM COLORS", 10, INDIGO, true));
            gap(custom, 8);
            colorOption(custom, "Accent", CBoardTheme.PREF_ACCENT, palette.accent);
            colorOption(custom, "Keyboard background", CBoardTheme.PREF_PANEL, palette.panel);
            colorOption(custom, "Key background", CBoardTheme.PREF_KEY, palette.key);
            colorOption(custom, "Text color", CBoardTheme.PREF_TEXT, palette.text);
            c.addView(custom);
        }
        gap(c, 12);
        heading(c, "Preview", "Changes are saved immediately and update the keyboard.");
        gap(c, 10);
        int current = KeyboardTheme.getKeyboardTheme(this).mThemeId;
        for (Preset p : presets) { c.addView(themeCard(p, current)); gap(c, 12); }
        LinearLayout note = card();
        note.addView(txt("APPLYING A THEME", 10, INDIGO, true));
        gap(note, 5);
        note.addView(txt("Your choice is saved and updates the active keyboard immediately.", 12, MUTED, false));
        c.addView(note);
    }

    private View themeCard(final Preset p, int current) {
        boolean active = current == p.id;
        LinearLayout card = card();
        card.setBackground(bg(SURFACE, 20, active ? ORANGE : BORDER));
        LinearLayout preview = col();
        preview.setPadding(dp(8), dp(8), dp(8), dp(8));
        GradientDrawable pb = bg(p.panel, 14, p.panel);
        preview.setBackground(pb);
        String[] rows = {"Q  W  E  R  T  Y", "A  S  D  F  G  H", "⇧   Z  X  C   ⌫"};
        for (String keys : rows) {
            TextView key = txt(keys, 11, p.dark ? TEXT : 0xFF1B2033, true);
            key.setGravity(Gravity.CENTER);
            key.setBackground(bg(p.key, 8, p.edge));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(28));
            lp.bottomMargin = dp(4);
            preview.addView(key, lp);
        }
        TextView space = txt("space", 10, p.dark ? MUTED : 0xFF565C73, false);
        space.setGravity(Gravity.CENTER);
        space.setBackground(bg(p.key, 14, p.edge));
        preview.addView(space, new LinearLayout.LayoutParams(-1, dp(26)));
        card.addView(preview);
        gap(card, 10);
        card.addView(txt(p.name, 15, TEXT, true));
        gap(card, 3);
        card.addView(txt(p.description, 11, MUTED, false));
        gap(card, 10);
        View.OnClickListener apply = new View.OnClickListener() {
            @Override public void onClick(View v) {
                String mode = p.dark ? CBoardTheme.DARK : CBoardTheme.LIGHT;
                prefs.edit().putString(CBoardTheme.PREF_MODE, mode).apply();
                KeyboardTheme.saveKeyboardThemeId(p.id, prefs);
                render();
            }
        };
        card.addView(active ? button("✓  Active", OK_BG, OK, null) : primary("Apply theme", apply));
        return card;
    }

    private void addThemeMode(LinearLayout parent, String title, final String value, String active) {
        final boolean selected = value.equals(active);
        View.OnClickListener click = new View.OnClickListener() {
            @Override public void onClick(View v) {
                prefs.edit().putString(CBoardTheme.PREF_MODE, value).apply();
                if (CBoardTheme.LIGHT.equals(value)) KeyboardTheme.saveKeyboardThemeId(3, prefs);
                if (CBoardTheme.DARK.equals(value)) KeyboardTheme.saveKeyboardThemeId(4, prefs);
                render();
            }
        };
        parent.addView(button((selected ? "✓  " : "○  ") + title,
                selected ? INDIGO_BG : HIGH, selected ? INDIGO : TEXT, click));
        gap(parent, 6);
    }

    private void colorOption(LinearLayout parent, String title, final String prefKey, int color) {
        TextView swatch = button("●  " + title + "     " + String.format("#%06X", 0xFFFFFF & color),
                color, Color.luminance(color) > .45f ? Color.BLACK : Color.WHITE,
                new View.OnClickListener() {
                    @Override public void onClick(View v) { showColorPicker(prefKey, colorFor(prefKey)); }
                });
        parent.addView(swatch);
        gap(parent, 6);
    }

    private int colorFor(String key) {
        return prefs.getInt(key, key.equals(CBoardTheme.PREF_ACCENT) ? palette.accent
                : key.equals(CBoardTheme.PREF_PANEL) ? palette.panel
                : key.equals(CBoardTheme.PREF_KEY) ? palette.key : palette.text);
    }

    private void showColorPicker(final String prefKey, int initialColor) {
        LinearLayout box = col();
        box.setPadding(dp(20), dp(10), dp(20), dp(6));
        final TextView preview = txt("Custom color", 16, TEXT, true);
        preview.setGravity(Gravity.CENTER);
        box.addView(preview, new LinearLayout.LayoutParams(-1, dp(52)));
        final SeekBar hue = new SeekBar(this); hue.setMax(359);
        final SeekBar saturation = new SeekBar(this); saturation.setMax(100);
        final SeekBar value = new SeekBar(this); value.setMax(100);
        hue.setProgressTintList(ColorStateList.valueOf(INDIGO));
        saturation.setProgressTintList(ColorStateList.valueOf(INDIGO));
        value.setProgressTintList(ColorStateList.valueOf(INDIGO));
        float[] hsv = new float[3]; Color.colorToHSV(initialColor, hsv);
        hue.setProgress((int) hsv[0]); saturation.setProgress((int) (hsv[1] * 100));
        value.setProgress((int) (hsv[2] * 100));
        box.addView(hue); box.addView(saturation); box.addView(value);
        SeekBar.OnSeekBarChangeListener listener = new SeekBar.OnSeekBarChangeListener() {
            private void update() {
                int color = Color.HSVToColor(new float[]{hue.getProgress(),
                        saturation.getProgress() / 100f, value.getProgress() / 100f});
                preview.setBackground(bg(color, 14, color));
                preview.setTextColor(Color.luminance(color) > .45f ? Color.BLACK : Color.WHITE);
            }
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) { update(); }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        };
        hue.setOnSeekBarChangeListener(listener); saturation.setOnSeekBarChangeListener(listener);
        value.setOnSeekBarChangeListener(listener);
        preview.setBackground(bg(initialColor, 14, initialColor));
        new AlertDialog.Builder(this).setTitle("Choose color").setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Apply", (dialog, which) -> {
                    int color = Color.HSVToColor(new float[]{hue.getProgress(),
                            saturation.getProgress() / 100f, value.getProgress() / 100f});
                    prefs.edit().putInt(prefKey, color).putString(CBoardTheme.PREF_MODE,
                            CBoardTheme.CUSTOM).apply();
                    render();
                }).show();
    }

    private void buildSettings(LinearLayout c) {
        heading(c, "Control Center", "Tune the keyboard and your device integration.");
        gap(c, 14);
        settingRow(c, "◈", "Theme Studio", "Aurora Light and Aurora Dark", ORANGE, new View.OnClickListener() {
            @Override public void onClick(View v) { tab = THEMES; page = THEMES; render(); }
        });
        gap(c, 8);
        settingRow(c, "◉", "Haptics & Sounds", "Vibration, click and key preview", INDIGO, new View.OnClickListener() {
            @Override public void onClick(View v) { page = HAPTICS; render(); }
        });
        gap(c, 8);
        settingRow(c, "Aa", "Typing preferences", "Capitalization, spacing and key feedback", ORANGE, new View.OnClickListener() {
            @Override public void onClick(View v) { page = TYPING; render(); }
        });
        gap(c, 8);
        settingRow(c, "文", "Languages & layouts", "Keyboard shape and screen layout", INDIGO, new View.OnClickListener() {
            @Override public void onClick(View v) { page = LAYOUTS; render(); }
        });
        gap(c, 8);
        settingRow(c, "✎", "Text correction", "Suggestions and auto-correction", ORANGE, new View.OnClickListener() {
            @Override public void onClick(View v) { page = CORRECTION; render(); }
        });
        gap(c, 8);
        settingRow(c, "⚙", "More controls", "Gesture typing and advanced options", TEXT, new View.OnClickListener() {
            @Override public void onClick(View v) { page = ADVANCED; render(); }
        });
        gap(c, 12);
        LinearLayout privacy = card();
        privacy.addView(txt("ON-DEVICE", 10, INDIGO, true));
        gap(privacy, 4);
        privacy.addView(txt("Text typed in the playground never leaves this device.", 12, MUTED, false));
        c.addView(privacy);
    }


    private void buildTyping(LinearLayout c) {
        c.addView(button("←  Settings", HIGH, MUTED, new View.OnClickListener() {
            @Override public void onClick(View v) { page = SETTINGS; tab = SETTINGS; render(); }
        }), new LinearLayout.LayoutParams(-2, -2));
        gap(c, 12);
        heading(c, "Typing preferences", "Make everyday typing feel right.");
        gap(c, 14);
        LinearLayout typing = card();
        typing.addView(txt("TYPING", 10, INDIGO, true));
        switchRow(typing, getString(R.string.auto_cap), getString(R.string.auto_cap_summary),
                Settings.PREF_AUTO_CAP, true);
        divider(typing);
        switchRow(typing, getString(R.string.use_double_space_period),
                getString(R.string.use_double_space_period_summary),
                Settings.PREF_KEY_USE_DOUBLE_SPACE_PERIOD, true);
        c.addView(typing);
        gap(c, 12);
        LinearLayout feedback = card();
        feedback.addView(txt("KEY FEEDBACK", 10, INDIGO, true));
        switchRow(feedback, getString(R.string.vibrate_on_keypress),
                getString(R.string.vibrate_on_keypress_summary), Settings.PREF_VIBRATE_ON,
                getResources().getBoolean(R.bool.config_default_vibration_enabled));
        divider(feedback);
        switchRow(feedback, getString(R.string.sound_on_keypress),
                getString(R.string.sound_on_keypress_summary), Settings.PREF_SOUND_ON,
                getResources().getBoolean(R.bool.config_default_sound_enabled));
        divider(feedback);
        switchRow(feedback, getString(R.string.popup_on_keypress),
                getString(R.string.popup_on_keypress_summary), Settings.PREF_POPUP_ON,
                getResources().getBoolean(R.bool.config_default_key_preview_popup));
        divider(feedback);
        switchRow(feedback, getString(R.string.voice_input),
                getString(R.string.voice_input_summary), Settings.PREF_VOICE_INPUT_KEY, true);
        c.addView(feedback);
    }

    private void buildLayouts(LinearLayout c) {
        c.addView(button("←  Settings", HIGH, MUTED, new View.OnClickListener() {
            @Override public void onClick(View v) { page = SETTINGS; tab = SETTINGS; render(); }
        }), new LinearLayout.LayoutParams(-2, -2));
        gap(c, 12);
        heading(c, "Languages & layouts", "Choose a keyboard look and layout.");
        gap(c, 14);
        LinearLayout theme = card();
        theme.addView(txt("KEYBOARD THEME", 10, INDIGO, true));
        gap(theme, 6);
        theme.addView(txt("Pick a light or dark keyboard style.", 12, MUTED, false));
        gap(theme, 10);
        theme.addView(primary("Open Theme Studio", new View.OnClickListener() {
            @Override public void onClick(View v) { page = THEMES; tab = THEMES; render(); }
        }));
        c.addView(theme);
        gap(c, 12);
        LinearLayout layout = card();
        layout.addView(txt("SCREEN LAYOUT", 10, INDIGO, true));
        switchRow(layout, getString(R.string.enable_split_keyboard),
                getString(R.string.enable_split_keyboard_summary), Settings.PREF_ENABLE_SPLIT_KEYBOARD,
                false);
        gap(layout, 8);
        layout.addView(button("Custom input styles  ›", HIGH, TEXT, new View.OnClickListener() {
            @Override public void onClick(View v) {
                openFragment("com.android.inputmethod.latin.settings.CustomInputStyleSettingsFragment");
            }
        }));
        c.addView(layout);
    }

    private void buildCorrection(LinearLayout c) {
        c.addView(button("←  Settings", HIGH, MUTED, new View.OnClickListener() {
            @Override public void onClick(View v) { page = SETTINGS; tab = SETTINGS; render(); }
        }), new LinearLayout.LayoutParams(-2, -2));
        gap(c, 12);
        heading(c, "Text correction", "Choose how CrazeKeyboard helps while you type.");
        gap(c, 14);
        LinearLayout suggestions = card();
        suggestions.addView(txt("SUGGESTIONS", 10, INDIGO, true));
        switchRow(suggestions, getString(R.string.prefs_show_suggestions),
                getString(R.string.prefs_show_suggestions_summary), Settings.PREF_SHOW_SUGGESTIONS, true);
        divider(suggestions);
        switchRow(suggestions, getString(R.string.bigram_prediction),
                getString(R.string.bigram_prediction_summary), Settings.PREF_BIGRAM_PREDICTIONS, true);
        divider(suggestions);
        switchRow(suggestions, getString(R.string.auto_correction),
                getString(R.string.auto_correction_summary), Settings.PREF_AUTO_CORRECTION, true);
        c.addView(suggestions);
        gap(c, 12);
        LinearLayout privacy = card();
        privacy.addView(txt("PERSONALIZATION", 10, INDIGO, true));
        switchRow(privacy, getString(R.string.use_personalized_dicts),
                getString(R.string.use_personalized_dicts_summary),
                Settings.PREF_KEY_USE_PERSONALIZED_DICTS, true);
        divider(privacy);
        switchRow(privacy, getString(R.string.use_contacts_dict),
                getString(R.string.use_contacts_dict_summary), Settings.PREF_KEY_USE_CONTACTS_DICT, true);
        divider(privacy);
        switchRow(privacy, getString(R.string.prefs_block_potentially_offensive_title),
                getString(R.string.prefs_block_potentially_offensive_summary),
                Settings.PREF_BLOCK_POTENTIALLY_OFFENSIVE,
                getResources().getBoolean(R.bool.config_block_potentially_offensive));
        c.addView(privacy);
        gap(c, 12);
        c.addView(button("Personal dictionary  ›", HIGH, TEXT, new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(android.provider.Settings.ACTION_USER_DICTIONARY_SETTINGS));
            }
        }));
    }

    private void buildAdvanced(LinearLayout c) {
        c.addView(button("←  Settings", HIGH, MUTED, new View.OnClickListener() {
            @Override public void onClick(View v) { page = SETTINGS; tab = SETTINGS; render(); }
        }), new LinearLayout.LayoutParams(-2, -2));
        gap(c, 12);
        heading(c, "More controls", "Tune gestures and keyboard behavior.");
        gap(c, 14);
        LinearLayout gestures = card();
        gestures.addView(txt("GESTURE TYPING", 10, INDIGO, true));
        switchRow(gestures, getString(R.string.gesture_input),
                getString(R.string.gesture_input_summary), Settings.PREF_GESTURE_INPUT, true);
        divider(gestures);
        switchRow(gestures, getString(R.string.gesture_floating_preview_text),
                getString(R.string.gesture_floating_preview_text_summary),
                Settings.PREF_GESTURE_FLOATING_PREVIEW_TEXT, true);
        divider(gestures);
        switchRow(gestures, getString(R.string.gesture_preview_trail),
                getString(R.string.gesture_preview_trail_summary), Settings.PREF_GESTURE_PREVIEW_TRAIL, true);
        c.addView(gestures);
        gap(c, 12);
        LinearLayout controls = card();
        controls.addView(txt("OTHER CONTROLS", 10, INDIGO, true));
        switchRow(controls, getString(R.string.prefs_enable_emoji_alt_physical_key),
                getString(R.string.prefs_enable_emoji_alt_physical_key_summary),
                Settings.PREF_ENABLE_EMOJI_ALT_PHYSICAL_KEY, true);
        divider(controls);
        switchRow(controls, getString(R.string.show_setup_wizard_icon),
                getString(R.string.show_setup_wizard_icon_summary),
                Settings.PREF_SHOW_SETUP_WIZARD_ICON, false);
        c.addView(controls);
        gap(c, 12);
        c.addView(button("Developer tools  ›", HIGH, TEXT, new View.OnClickListener() {
            @Override public void onClick(View v) { page = DEBUG; tab = SETTINGS; render(); }
        }));
    }

    private void buildDeveloper(LinearLayout c) {
        c.addView(button("←  More controls", HIGH, MUTED, new View.OnClickListener() {
            @Override public void onClick(View v) { page = ADVANCED; tab = SETTINGS; render(); }
        }), new LinearLayout.LayoutParams(-2, -2));
        gap(c, 12);
        heading(c, "Developer tools", "Existing diagnostics and keyboard tuning controls.");
        gap(c, 14);
        LinearLayout tuning = card();
        tuning.addView(txt("INPUT DIAGNOSTICS", 10, INDIGO, true));
        switchRow(tuning, "Force compatibility multitouch", "Use the fallback touch tracker",
                com.android.inputmethod.latin.settings.DebugSettings.PREF_FORCE_NON_DISTINCT_MULTITOUCH,
                false);
        divider(tuning);
        switchRow(tuning, "Sliding key preview", "Show input path feedback",
                com.android.inputmethod.latin.settings.DebugSettings.PREF_SLIDING_KEY_INPUT_PREVIEW,
                true);
        divider(tuning);
        switchRow(tuning, "Allow keyboard resizing", "Enable test-only keyboard height controls",
                com.android.inputmethod.latin.settings.DebugSettings.PREF_RESIZE_KEYBOARD, false);
        c.addView(tuning);
        gap(c, 12);
        LinearLayout dumps = card();
        dumps.addView(txt("DICTIONARY DUMPS", 10, INDIGO, true));
        gap(dumps, 8);
        addDictionaryDump(dumps, "User dictionary", com.android.inputmethod.latin.Dictionary.TYPE_USER);
        addDictionaryDump(dumps, "Contact dictionary", com.android.inputmethod.latin.Dictionary.TYPE_CONTACTS);
        addDictionaryDump(dumps, "Typing history", com.android.inputmethod.latin.Dictionary.TYPE_USER_HISTORY);
        c.addView(dumps);
    }

    private void addDictionaryDump(LinearLayout parent, final String title, final String type) {
        parent.addView(button("Export " + title, HIGH, TEXT, new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent dump = new Intent(com.android.inputmethod.latin.DictionaryDumpBroadcastReceiver
                        .DICTIONARY_DUMP_INTENT_ACTION);
                dump.setPackage(getPackageName());
                dump.putExtra(com.android.inputmethod.latin.DictionaryDumpBroadcastReceiver
                        .DICTIONARY_NAME_KEY, type);
                sendBroadcast(dump);
                Toast.makeText(CrazeDashboardActivity.this, "Dictionary export requested",
                        Toast.LENGTH_SHORT).show();
            }
        }));
        gap(parent, 6);
    }

    private void buildHaptics(LinearLayout c) {
        c.addView(button("←  Back", HIGH, MUTED, new View.OnClickListener() {
            @Override public void onClick(View v) { tab = SETTINGS; page = SETTINGS; render(); }
        }), new LinearLayout.LayoutParams(-2, -2));
        gap(c, 12);
        heading(c, "Haptics & Sounds", "Shape the feel of every keystroke.");
        gap(c, 14);

        LinearLayout toggles = card();
        toggles.addView(txt("KEY FEEDBACK", 10, INDIGO, true));
        gap(toggles, 3);
        switchRow(toggles, "Tactile vibration", "Short vibration on keypress", Settings.PREF_VIBRATE_ON,
                getResources().getBoolean(R.bool.config_default_vibration_enabled));
        divider(toggles);
        switchRow(toggles, "Keypress sound", "Android keyboard click sounds", Settings.PREF_SOUND_ON,
                getResources().getBoolean(R.bool.config_default_sound_enabled));
        divider(toggles);
        switchRow(toggles, "Key preview popups", "Preview above each pressed key", Settings.PREF_POPUP_ON,
                getResources().getBoolean(R.bool.config_default_key_preview_popup));
        c.addView(toggles);
        gap(c, 12);

        LinearLayout vib = card();
        LinearLayout vh = row();
        vh.addView(txt("TAP DURATION", 10, ORANGE, true), new LinearLayout.LayoutParams(0, -2, 1f));
        final int max = Math.max(1, Math.min(100, getResources().getInteger(R.integer.config_max_vibration_duration)));
        int initial = Settings.readKeypressVibrationDuration(prefs, getResources());
        final TextView vValue = txt(initial < 0 ? "System default" : initial + " ms", 11, TEXT, true);
        vh.addView(vValue);
        vib.addView(vh);
        gap(vib, 6);
        SeekBar vBar = seek(max, Math.max(0, Math.min(max, initial < 0 ? Math.min(18, max) : initial)));
        vib.addView(vBar);
        vBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar b, int val, boolean user) {
                vValue.setText(val + " ms");
                if (user) prefs.edit().putInt(Settings.PREF_VIBRATION_DURATION_SETTINGS, val).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar b) {}
            @Override public void onStopTrackingTouch(SeekBar b) {}
        });
        c.addView(vib);
        gap(c, 12);

        LinearLayout sound = card();
        LinearLayout sh = row();
        sh.addView(txt("SOUND LEVEL", 10, ORANGE, true), new LinearLayout.LayoutParams(0, -2, 1f));
        int volume = Math.max(0, Math.min(100, Math.round(Settings.readKeypressSoundVolume(prefs, getResources()) * 100f)));
        final TextView soundValue = txt(volume + "%", 11, TEXT, true);
        sh.addView(soundValue);
        sound.addView(sh);
        gap(sound, 6);
        SeekBar sBar = seek(100, volume);
        sound.addView(sBar);
        sBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar b, int val, boolean user) {
                soundValue.setText(val + "%");
                if (user) prefs.edit().putFloat(Settings.PREF_KEYPRESS_SOUND_VOLUME, val / 100f).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar b) {}
            @Override public void onStopTrackingTouch(SeekBar b) {}
        });
        gap(sound, 10);
        LinearLayout tests = row();
        addWeight(tests, button("Test vibration", HIGH, INDIGO, new View.OnClickListener() {
            @Override public void onClick(View v) { testVibration(); }
        }));
        gapX(tests, 8);
        addWeight(tests, button("Test click", HIGH, ORANGE, new View.OnClickListener() {
            @Override public void onClick(View v) { testSound(); }
        }));
        sound.addView(tests);
        c.addView(sound);
        gap(c, 12);

        LinearLayout pad = card();
        pad.addView(txt("Test tap pad", 14, TEXT, true));
        gap(pad, 4);
        pad.addView(txt("Feel the selected duration. Sound follows Android's ringer settings.", 11, MUTED, false));
        gap(pad, 10);
        pad.addView(primary("●  Tap to test feedback", new View.OnClickListener() {
            @Override public void onClick(View v) { testVibration(); testSound(); }
        }));
        c.addView(pad);
    }

    // ------------------------------------------------------------ components

    private void stepRow(LinearLayout parent, String title, String summary, boolean done, String action,
                         View.OnClickListener click) {
        LinearLayout r = row();
        r.setPadding(0, dp(8), 0, dp(8));
        TextView badge = txt(done ? "✓" : "•", 15, done ? OK : MUTED, true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(bg(done ? OK_BG : HIGH, 20, done ? OK_BG : HIGH));
        r.addView(badge, new LinearLayout.LayoutParams(dp(32), dp(32)));
        LinearLayout words = col();
        words.setPadding(dp(12), 0, dp(8), 0);
        words.addView(txt(title, 13, TEXT, true));
        gap(words, 2);
        words.addView(txt(summary, 10, MUTED, false));
        r.addView(words, new LinearLayout.LayoutParams(0, -2, 1f));
        if (!done) r.addView(chip(action, click));
        parent.addView(r);
    }

    private void settingRow(LinearLayout parent, String icon, String title, String summary, int accent,
                            View.OnClickListener click) {
        LinearLayout r = row();
        r.setPadding(dp(12), dp(12), dp(12), dp(12));
        r.setBackground(bg(SURFACE, 18, BORDER));
        TextView badge = txt(icon, 17, accent, true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(bg(HIGH, 12, HIGH));
        r.addView(badge, new LinearLayout.LayoutParams(dp(42), dp(42)));
        LinearLayout labels = col();
        labels.setPadding(dp(12), 0, dp(4), 0);
        labels.addView(txt(title, 14, TEXT, true));
        gap(labels, 3);
        labels.addView(txt(summary, 11, MUTED, false));
        r.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));
        r.addView(txt("›", 24, MUTED, false));
        r.setOnClickListener(click);
        parent.addView(r);
    }

    private void switchRow(LinearLayout parent, String title, String summary, final String key, boolean def) {
        LinearLayout r = row();
        LinearLayout words = col();
        words.addView(txt(title, 14, TEXT, true));
        gap(words, 3);
        words.addView(txt(summary, 11, MUTED, false));
        r.addView(words, new LinearLayout.LayoutParams(0, -2, 1f));
        Switch sw = new Switch(this);
        sw.setChecked(prefs.getBoolean(key, def));
        sw.setOnCheckedChangeListener((b, checked) -> prefs.edit().putBoolean(key, checked).apply());
        r.addView(sw);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(10); lp.bottomMargin = dp(10);
        parent.addView(r, lp);
    }

    private SeekBar seek(int max, int progress) {
        SeekBar s = new SeekBar(this);
        s.setMax(max);
        s.setProgress(progress);
        s.setProgressTintList(ColorStateList.valueOf(ORANGE));
        s.setThumbTintList(ColorStateList.valueOf(ORANGE));
        return s;
    }

    // --------------------------------------------------------------- actions

    private void testVibration() {
        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator == null || !vibrator.hasVibrator()) {
            Toast.makeText(this, "No vibrator is available on this device.", Toast.LENGTH_SHORT).show();
            return;
        }
        int duration = Settings.readKeypressVibrationDuration(prefs, getResources());
        if (duration < 0) duration = 18;
        duration = Math.max(1, Math.min(100, duration));
        if (Build.VERSION.SDK_INT >= 26) {
            vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            vibrator.vibrate(duration);
        }
    }

    private void testSound() {
        if (!prefs.getBoolean(Settings.PREF_SOUND_ON, getResources().getBoolean(R.bool.config_default_sound_enabled))) {
            Toast.makeText(this, "Turn on keypress sound to test it.", Toast.LENGTH_SHORT).show();
            return;
        }
        final ToneGenerator tone = new ToneGenerator(AudioManager.STREAM_SYSTEM, 35);
        tone.startTone(ToneGenerator.TONE_PROP_BEEP, 85);
        new Handler().postDelayed(new Runnable() {
            @Override public void run() { tone.release(); }
        }, 120);
    }

    private void openFragment(String name) {
        Intent i = new Intent(this, SettingsActivity.class);
        i.putExtra(PreferenceActivity.EXTRA_SHOW_FRAGMENT, name);
        i.putExtra(PreferenceActivity.EXTRA_NO_HEADERS, true);
        startActivity(i);
    }

    private void openImeSettings() {
        try { startActivity(new Intent(android.provider.Settings.ACTION_INPUT_METHOD_SETTINGS)); }
        catch (Exception e) { startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS)); }
    }

    private boolean imeEnabled() {
        InputMethodManager m = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (m == null) return false;
        List<InputMethodInfo> list = m.getEnabledInputMethodList();
        for (InputMethodInfo info : list) if (getPackageName().equals(info.getPackageName())) return true;
        return false;
    }

    private boolean imeSelected() {
        String id = android.provider.Settings.Secure.getString(getContentResolver(),
                android.provider.Settings.Secure.DEFAULT_INPUT_METHOD);
        return id != null && id.startsWith(getPackageName() + "/");
    }

    private void pickKeyboard() {
        InputMethodManager m = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (m != null) m.showInputMethodPicker();
    }

    private void copy(String value) {
        if (value == null || value.trim().length() == 0) {
            Toast.makeText(this, "There is no text to copy yet.", Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager cb = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cb != null) {
            cb.setPrimaryClip(ClipData.newPlainText("CrazeKeyboard", value));
            Toast.makeText(this, "Copied to clipboard.", Toast.LENGTH_SHORT).show();
        }
    }

    private String counts(String s) {
        String t = s.trim();
        int words = t.length() == 0 ? 0 : t.split("\\s+").length;
        return s.length() + " characters · " + words + " words";
    }

    // --------------------------------------------------------------- builders

    private EditText editor(String value, String hint, int lines) {
        EditText e = new EditText(this);
        e.setTextColor(TEXT);
        e.setHintTextColor(0xFF6C7390);
        e.setTextSize(14);
        e.setGravity(Gravity.TOP | Gravity.START);
        e.setPadding(dp(14), dp(14), dp(14), dp(14));
        e.setMinLines(lines);
        e.setMaxLines(lines + 3);
        e.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        e.setHint(hint);
        e.setBackground(bg(INPUT, 14, BORDER));
        e.setText(value);
        return e;
    }

    private void heading(LinearLayout c, String title, String sub) {
        c.addView(txt(title, 26, TEXT, true));
        gap(c, 4);
        c.addView(txt(sub, 12, MUTED, false));
    }

    private LinearLayout card() {
        LinearLayout l = col();
        l.setPadding(dp(14), dp(14), dp(14), dp(14));
        l.setBackground(bg(SURFACE, 20, BORDER));
        return l;
    }

    private LinearLayout row() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    private LinearLayout col() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private TextView txt(String s, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private TextView button(String s, int fill, int fg, View.OnClickListener click) {
        TextView t = txt(s, 12, fg, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(12), dp(10), dp(12), dp(10));
        t.setMinHeight(dp(44));
        t.setBackground(bg(fill, 14, fill == HIGH ? BORDER : fill));
        if (click != null) { t.setClickable(true); t.setFocusable(true); t.setOnClickListener(click); }
        return t;
    }

    private TextView primary(String s, View.OnClickListener click) {
        TextView t = txt(s, 12, ON_ORANGE, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(12), dp(10), dp(12), dp(10));
        t.setMinHeight(dp(44));
        t.setBackground(gradient(14));
        t.setClickable(true);
        t.setFocusable(true);
        t.setOnClickListener(click);
        return t;
    }

    private TextView chip(String s, View.OnClickListener click) {
        TextView t = txt(s, 11, TEXT, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(14), dp(8), dp(14), dp(8));
        t.setBackground(bg(HIGH, 20, BORDER));
        t.setClickable(true);
        t.setFocusable(true);
        t.setOnClickListener(click);
        return t;
    }

    private TextView pill(String s, int fg, int fill) {
        TextView t = txt(s, 9, fg, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(10), dp(5), dp(10), dp(5));
        t.setBackground(bg(fill, 30, fill));
        return t;
    }

    private GradientDrawable bg(int color, int radius, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        d.setStroke(dp(1), stroke);
        return d;
    }

    private GradientDrawable gradient(int radius) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[] {ORANGE, CORAL});
        d.setCornerRadius(dp(radius));
        return d;
    }

    private GradientDrawable heroBg() {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[] {0xFFFFA64D, ORANGE, CORAL});
        d.setCornerRadius(dp(24));
        return d;
    }

    private void gap(LinearLayout l, int h) { l.addView(new View(this), new LinearLayout.LayoutParams(1, dp(h))); }
    private void gapX(LinearLayout l, int w) { l.addView(new View(this), new LinearLayout.LayoutParams(dp(w), 1)); }
    private void addWeight(LinearLayout l, View v) { l.addView(v, new LinearLayout.LayoutParams(0, -2, 1f)); }

    private void divider(LinearLayout l) {
        View v = new View(this);
        v.setBackgroundColor(BORDER);
        l.addView(v, new LinearLayout.LayoutParams(-1, dp(1)));
    }

    private int dp(float x) { return (int) (x * getResources().getDisplayMetrics().density + 0.5f); }
}
