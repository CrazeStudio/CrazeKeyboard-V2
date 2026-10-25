/* Copyright (C) 2026 The CrazeStudio Authors
 * Licensed under the Apache License, Version 2.0.
 */
package com.android.inputmethod.latin;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.preference.PreferenceManager;

/** Shared adaptive color tokens used by the app, suggestion strip and keyboard renderer. */
public final class CBoardTheme {
    public static final String PREF_MODE = "cboard_theme_mode";
    public static final String PREF_ACCENT = "cboard_custom_accent";
    public static final String PREF_PANEL = "cboard_custom_panel";
    public static final String PREF_KEY = "cboard_custom_key";
    public static final String PREF_TEXT = "cboard_custom_text";
    public static final String SYSTEM = "system", LIGHT = "light", DARK = "dark", CUSTOM = "custom";

    public final boolean dark;
    public final int background, surface, panel, key, functionalKey, text, secondaryText;
    public final int accent, accentText, border, pressed;

    private CBoardTheme(boolean dark, int background, int surface, int panel, int key,
            int functionalKey, int text, int secondaryText, int accent, int accentText,
            int border, int pressed) {
        this.dark = dark;
        this.background = background;
        this.surface = surface;
        this.panel = panel;
        this.key = key;
        this.functionalKey = functionalKey;
        this.text = text;
        this.secondaryText = secondaryText;
        this.accent = accent;
        this.accentText = accentText;
        this.border = border;
        this.pressed = pressed;
    }

    public static String mode(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getString(PREF_MODE, SYSTEM);
    }

    public static CBoardTheme resolve(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String mode = prefs.getString(PREF_MODE, SYSTEM);
        boolean systemDark = (context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        boolean dark = DARK.equals(mode) || ((SYSTEM.equals(mode) || CUSTOM.equals(mode)) && systemDark);
        boolean useDynamic = Build.VERSION.SDK_INT >= 31 && SYSTEM.equals(mode);
        int accent = dynamicColor(context, "system_accent1_500", dark ? 0xFFB5A8FF : 0xFF5D56C9, useDynamic);
        int surface = dynamicColor(context, dark ? "system_neutral1_900" : "system_neutral1_10",
                dark ? 0xFF202124 : 0xFFFFFFFF, useDynamic);
        int background = dynamicColor(context, dark ? "system_neutral1_950" : "system_neutral1_50",
                dark ? 0xFF17181B : 0xFFF4F5F8, useDynamic);
        int panel = dynamicColor(context, dark ? "system_neutral1_850" : "system_neutral1_100",
                dark ? 0xFF202124 : 0xFFD5D9E0, useDynamic);
        int key = dynamicColor(context, dark ? "system_neutral2_800" : "system_neutral1_10",
                dark ? 0xFF3C4043 : 0xFFFAFBFD, useDynamic);
        int functional = dynamicColor(context, dark ? "system_neutral2_850" : "system_neutral1_200",
                dark ? 0xFF303134 : 0xFFBBC1CB, useDynamic);
        int text = dynamicColor(context, dark ? "system_neutral1_10" : "system_neutral1_900",
                dark ? 0xFFF1F3F4 : 0xFF202124, useDynamic);
        int secondary = dynamicColor(context, dark ? "system_neutral2_200" : "system_neutral2_600",
                dark ? 0xFFBDC1C6 : 0xFF747B8D, useDynamic);
        int border = dynamicColor(context, dark ? "system_neutral2_700" : "system_neutral2_200",
                dark ? 0xFF34363A : 0xFFC4CAD3, useDynamic);
        int pressed = blend(key, accent, dark ? .30f : .16f);
        int accentText = contrastText(accent);
        if (CUSTOM.equals(mode)) {
            accent = prefs.getInt(PREF_ACCENT, accent);
            panel = prefs.getInt(PREF_PANEL, panel);
            key = prefs.getInt(PREF_KEY, key);
            text = accessibleText(prefs.getInt(PREF_TEXT, text), key);
            secondary = accessibleText(secondary, key);
            accentText = contrastText(accent);
            pressed = blend(key, accent, .22f);
        }
        return new CBoardTheme(dark, background, surface, panel, key, functional,
                text, secondary, accent, accentText, border, pressed);
    }

    private static int dynamicColor(Context context, String name, int fallback, boolean enabled) {
        if (!enabled) return fallback;
        int id = context.getResources().getIdentifier(name, "color", "android");
        if (id == 0) return fallback;
        try { return context.getColor(id); } catch (RuntimeException ignored) { return fallback; }
    }

    private static int accessibleText(int requested, int background) {
        if (contrast(requested, background) >= 4.5) return requested;
        return contrast(0xFF000000, background) > contrast(0xFFFFFFFF, background)
                ? 0xFF000000 : 0xFFFFFFFF;
    }

    private static int contrastText(int background) {
        return contrast(0xFF000000, background) > contrast(0xFFFFFFFF, background)
                ? 0xFF000000 : 0xFFFFFFFF;
    }

    private static double contrast(int foreground, int background) {
        double a = luminance(foreground), b = luminance(background);
        return (Math.max(a, b) + .05) / (Math.min(a, b) + .05);
    }

    private static double luminance(int color) {
        double r = linear((color >> 16) & 255), g = linear((color >> 8) & 255), b = linear(color & 255);
        return .2126 * r + .7152 * g + .0722 * b;
    }

    private static double linear(int value) {
        double c = value / 255.0;
        return c <= .04045 ? c / 12.92 : Math.pow((c + .055) / 1.055, 2.4);
    }

    private static int blend(int a, int b, float amount) {
        return 0xFF000000
                | (Math.round(((a >> 16) & 255) * (1 - amount) + ((b >> 16) & 255) * amount) << 16)
                | (Math.round(((a >> 8) & 255) * (1 - amount) + ((b >> 8) & 255) * amount) << 8)
                | Math.round((a & 255) * (1 - amount) + (b & 255) * amount);
    }
}
