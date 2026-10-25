/* Copyright (C) 2026 The CrazeStudio Authors
 * Licensed under the Apache License, Version 2.0.
 */
package com.android.inputmethod.latin;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Compact CBoard action rail, separate from the word suggestion layer. */
public final class CBoardToolbarView extends LinearLayout {
    public static final int NAVIGATION = 1, TEXT_TOOLS = 2, GIF = 3, CLIPBOARD = 4,
            SETTINGS = 5, MORE = 6, VOICE = 7;
    public interface Listener { void onCBoardToolbarAction(int action); }
    private Listener listener;
    private final View[] buttons = new View[7];
    private final int[] actions = {NAVIGATION, TEXT_TOOLS, GIF, CLIPBOARD, SETTINGS, MORE, VOICE};
    private final int[] icons = {R.drawable.ic_cboard_back, R.drawable.ic_keyboard_tools, 0,
            R.drawable.ic_cboard_clipboard, R.drawable.ic_tool_settings,
            R.drawable.ic_cboard_more, R.drawable.ic_cboard_voice};
    private final String[] labels = {"Keyboard switcher", "Text tools", "GIF picker",
            "Clipboard history", "Settings", "More options", "Voice input"};

    public CBoardToolbarView(Context context, AttributeSet attrs) { super(context, attrs); init(); }
    public CBoardToolbarView(Context context) { super(context); init(); }

    private void init() {
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        int pad = dp(7);
        setPadding(pad, dp(2), pad, dp(2));
        for (int i = 0; i < actions.length; i++) {
            View item;
            if (icons[i] == 0) {
                TextView gif = new TextView(getContext());
                gif.setText("GIF");
                gif.setTextSize(11);
                gif.setTypeface(null, android.graphics.Typeface.BOLD);
                gif.setGravity(Gravity.CENTER);
                item = gif;
            } else {
                ImageButton button = new ImageButton(getContext());
                button.setImageResource(icons[i]);
                button.setScaleType(ImageButton.ScaleType.CENTER_INSIDE);
                button.setPadding(dp(9), dp(9), dp(9), dp(9));
                item = button;
            }
            item.setContentDescription(labels[i]);
            item.setFocusable(true);
            item.setClickable(true);
            final int action = actions[i];
            item.setOnClickListener(new OnClickListener() {
                @Override public void onClick(View view) {
                    if (listener != null) listener.onCBoardToolbarAction(action);
                }
            });
            addView(item, new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));
            buttons[i] = item;
        }
        applyCBoardTheme();
    }

    public void setListener(Listener callback) { listener = callback; }
    public void setVoiceEnabled(boolean enabled) {
        buttons[6].setVisibility(enabled ? VISIBLE : GONE);
    }
    public void setGifEnabled(boolean enabled) {
        buttons[2].setVisibility(enabled ? VISIBLE : GONE);
    }

    public void applyCBoardTheme() {
        CBoardTheme theme = CBoardTheme.resolve(getContext());
        setBackgroundColor(theme.panel);
        for (int i = 0; i < buttons.length; i++) {
            View button = buttons[i];
            GradientDrawable mask = new GradientDrawable();
            mask.setColor(Color.WHITE);
            mask.setCornerRadius(dp(12));
            button.setBackground(new RippleDrawable(ColorStateList.valueOf(
                    theme.dark ? 0x33FFFFFF : 0x22000000), null, mask));
            if (button instanceof ImageButton) {
                ((ImageButton)button).setImageTintList(ColorStateList.valueOf(theme.secondaryText));
            } else if (button instanceof TextView) {
                TextView gif = (TextView)button;
                gif.setTextColor(theme.secondaryText);
                gif.setBackground(new RippleDrawable(ColorStateList.valueOf(
                        theme.dark ? 0x33FFFFFF : 0x22000000),
                        round(theme.key, dp(10)), round(Color.WHITE, dp(10))));
            }
        }
    }

    private GradientDrawable round(int color, float radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(radius); return d;
    }
    private int dp(float value) { return (int)(value * getResources().getDisplayMetrics().density + .5f); }
}
