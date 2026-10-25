/* Copyright (C) 2026 The CrazeStudio Authors
 * Licensed under the Apache License, Version 2.0.
 */
package com.android.inputmethod.latin;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.inputmethod.latin.suggestions.SuggestionStripView;

import java.util.ArrayList;

/** CBoard's compact, horizontally scrollable utility row. */
public final class CBoardToolbarView extends LinearLayout {
    public static final int MORE = SuggestionStripView.TOOL_ACTION_MORE;
    public static final int VOICE = SuggestionStripView.TOOL_ACTION_VOICE;

    public interface Listener { void onCBoardToolbarAction(int action); }

    private static final class Tool {
        final int label;
        final int icon;
        final int action;
        final boolean emphasized;
        Tool(int label, int icon, int action, boolean emphasized) {
            this.label = label;
            this.icon = icon;
            this.action = action;
            this.emphasized = emphasized;
        }
    }

    private static final class ToolView {
        final View view;
        final boolean emphasized;
        final boolean gifLabel;
        ToolView(View view, boolean emphasized, boolean gifLabel) {
            this.view = view;
            this.emphasized = emphasized;
            this.gifLabel = gifLabel;
        }
    }

    private Listener listener;
    private HorizontalScrollView scroller;
    private LinearLayout actionRow;
    private ImageButton voiceButton;
    private final ArrayList<ToolView> toolViews = new ArrayList<>();
    private boolean gifEnabled;
    private CBoardTheme colors;

    public CBoardToolbarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CBoardToolbarView(Context context) {
        super(context);
        init();
    }

    private void init() {
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(dp(5), dp(3), dp(4), dp(3));

        scroller = new HorizontalScrollView(getContext());
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroller.setFillViewport(false);
        actionRow = new LinearLayout(getContext());
        actionRow.setOrientation(HORIZONTAL);
        actionRow.setGravity(Gravity.CENTER_VERTICAL);
        scroller.addView(actionRow, new HorizontalScrollView.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));
        addView(scroller, new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1));

        voiceButton = new ImageButton(getContext());
        voiceButton.setImageResource(R.drawable.ic_cboard_voice);
        voiceButton.setScaleType(ImageButton.ScaleType.CENTER_INSIDE);
        voiceButton.setContentDescription(getResources().getString(R.string.spoken_description_mic));
        voiceButton.setFocusable(true);
        voiceButton.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View view) { dispatch(VOICE); }
        });
        addView(voiceButton, new LinearLayout.LayoutParams(dp(38), LayoutParams.MATCH_PARENT));
        rebuildActions();
        applyCBoardTheme();
    }

    public void setListener(Listener callback) { listener = callback; }

    public void setGifEnabled(boolean enabled) {
        if (gifEnabled == enabled) return;
        gifEnabled = enabled;
        rebuildActions();
        applyCBoardTheme();
    }

    public void setVoiceEnabled(boolean enabled) {
        voiceButton.setVisibility(enabled ? VISIBLE : GONE);
    }

    private void rebuildActions() {
        actionRow.removeAllViews();
        toolViews.clear();
        final ArrayList<Tool> tools = new ArrayList<>();
        tools.add(new Tool(R.string.keyboard_tool_more, R.drawable.ic_cboard_more, MORE, true));
        if (gifEnabled) tools.add(new Tool(R.string.keyboard_tool_gif, 0,
                SuggestionStripView.TOOL_ACTION_GIF, false));
        tools.add(new Tool(R.string.keyboard_tool_clipboard, R.drawable.ic_cboard_clipboard,
                SuggestionStripView.TOOL_ACTION_CLIPBOARD, false));
        tools.add(new Tool(R.string.keyboard_tool_text, R.drawable.ic_tool_text,
                SuggestionStripView.TOOL_ACTION_TEXT, false));
        tools.add(new Tool(R.string.keyboard_tool_emoji, R.drawable.ic_tool_emoji,
                SuggestionStripView.TOOL_ACTION_EMOJI, false));
        tools.add(new Tool(R.string.more_switch_keyboard, R.drawable.ic_tool_switch_keyboard,
                SuggestionStripView.TOOL_ACTION_SWITCHER, false));
        tools.add(new Tool(R.string.keyboard_theme, R.drawable.ic_tool_theme,
                SuggestionStripView.TOOL_ACTION_THEME, false));
        tools.add(new Tool(R.string.keyboard_tool_settings, R.drawable.ic_tool_settings,
                SuggestionStripView.TOOL_ACTION_SETTINGS, false));
        for (final Tool tool : tools) addTool(tool);
    }

    private void addTool(final Tool tool) {
        final View view;
        final boolean isGif = tool.icon == 0;
        if (isGif) {
            TextView label = new TextView(getContext());
            label.setText("GIF");
            label.setTypeface(null, android.graphics.Typeface.BOLD);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            label.setGravity(Gravity.CENTER);
            label.setContentDescription(getResources().getString(tool.label));
            view = label;
        } else {
            ImageButton button = new ImageButton(getContext());
            button.setImageResource(tool.icon);
            button.setScaleType(ImageButton.ScaleType.CENTER_INSIDE);
            button.setPadding(dp(8), dp(8), dp(8), dp(8));
            button.setContentDescription(getResources().getString(tool.label));
            view = button;
        }
        view.setFocusable(true);
        view.setClickable(true);
        view.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) { dispatch(tool.action); }
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(36), dp(36));
        params.setMargins(dp(2), 0, dp(2), 0);
        actionRow.addView(view, params);
        toolViews.add(new ToolView(view, tool.emphasized, isGif));
    }

    private void dispatch(int action) {
        if (listener != null) listener.onCBoardToolbarAction(action);
    }

    public void applyCBoardTheme() {
        colors = CBoardTheme.resolve(getContext());
        setBackgroundColor(colors.panel);
        actionRow.setBackgroundColor(colors.panel);
        for (ToolView tool : toolViews) {
            final int foreground = tool.emphasized ? colors.accent : colors.secondaryText;
            if (tool.gifLabel) {
                ((TextView)tool.view).setTextColor(foreground);
                tool.view.setBackground(rippleBackground(tool.emphasized));
            } else {
                ImageButton button = (ImageButton)tool.view;
                button.setImageTintList(ColorStateList.valueOf(foreground));
                button.setBackground(rippleBackground(tool.emphasized));
            }
        }
        voiceButton.setImageTintList(ColorStateList.valueOf(colors.accent));
        voiceButton.setBackground(rippleBackground(true));
    }

    private Drawable rippleBackground(boolean emphasized) {
        final GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(dp(18));
        final Drawable content = new ColorDrawable(
                emphasized ? colors.functionalKey : Color.TRANSPARENT);
        if (emphasized) mask.setStroke(dp(1), colors.border);
        final int ripple = colors.dark ? 0x33FFFFFF : 0x22000000;
        return new RippleDrawable(ColorStateList.valueOf(ripple), content, mask);
    }

    private int dp(float value) {
        return (int)(value * getResources().getDisplayMetrics().density + .5f);
    }
}
