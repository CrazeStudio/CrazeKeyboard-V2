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

/** Voice shortcut at the edge of the combined suggestions and tools row. */
public final class CBoardToolbarView extends LinearLayout {
    public static final int MORE = 6, VOICE = 7;
    public interface Listener { void onCBoardToolbarAction(int action); }
    private Listener listener;
    private ImageButton voiceButton;

    public CBoardToolbarView(Context context, AttributeSet attrs) { super(context, attrs); init(); }
    public CBoardToolbarView(Context context) { super(context); init(); }

    private void init() {
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER);
        setPadding(dp(2), dp(2), dp(5), dp(2));
        voiceButton = new ImageButton(getContext());
        voiceButton.setImageResource(R.drawable.ic_cboard_voice);
        voiceButton.setScaleType(ImageButton.ScaleType.CENTER_INSIDE);
        voiceButton.setPadding(dp(10), dp(10), dp(10), dp(10));
        voiceButton.setContentDescription("Voice input");
        voiceButton.setFocusable(true);
        voiceButton.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View view) {
                if (listener != null) listener.onCBoardToolbarAction(VOICE);
            }
        });
        addView(voiceButton, new LinearLayout.LayoutParams(dp(42), LayoutParams.MATCH_PARENT));
        applyCBoardTheme();
    }

    public void setListener(Listener callback) { listener = callback; }
    public void setVoiceEnabled(boolean enabled) {
        voiceButton.setVisibility(enabled ? VISIBLE : GONE);
    }

    public void applyCBoardTheme() {
        CBoardTheme theme = CBoardTheme.resolve(getContext());
        View parent = (View)getParent();
        if (parent != null) parent.setBackgroundColor(theme.panel);
        setBackgroundColor(theme.panel);
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(dp(14));
        voiceButton.setBackground(new RippleDrawable(ColorStateList.valueOf(
                theme.dark ? 0x33FFFFFF : 0x22000000), null, mask));
        voiceButton.setImageTintList(ColorStateList.valueOf(theme.secondaryText));
    }

    private int dp(float value) {
        return (int)(value * getResources().getDisplayMetrics().density + .5f);
    }
}
