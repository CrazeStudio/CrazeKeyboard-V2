/*
 * Copyright (C) 2026 The CrazeStudio Authors
 * Licensed under the Apache License, Version 2.0.
 */
package com.android.inputmethod.latin.setup;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
import android.view.ViewGroup;
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
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.settings.Settings;
import com.android.inputmethod.latin.settings.SettingsActivity;
import java.util.List;

/** Native, interactive companion screens for CrazeKeyboard. */
public final class CrazeDashboardActivity extends Activity {
    private static final int KEYBOARD=0, THEMES=1, SETTINGS=2, HAPTICS=3;
    private static final int BG=0xFF111318, SURFACE=0xFF191C22, HIGH=0xFF252A32;
    private static final int INPUT=0xFF0D0F13, TEXT=0xFFF0F2F5, MUTED=0xFFA5ACB7;
    private static final int BORDER=0xFF343B45, CYAN=0xFFB8C1CC;
    private static final int PINK=0xFF9AA4B2, ORANGE=0xFFFF8A24;

    private int tab=KEYBOARD, page=KEYBOARD;
    private String playgroundText="";
    private SharedPreferences prefs;
    private FrameLayout pageHost;
    private TextView subtitle;
    private LinearLayout[] navItems;
    private TextView[] navIcons, navLabels;

    private static final class Preset {
        final int id, panel, key, accent;
        final String name, description;
        final boolean dark;
        Preset(int i,String n,String d,int p,int k,int a,boolean isDark) {
            id=i; name=n; description=d; panel=p; key=k; accent=a; dark=isDark;
        }
    }
    private final Preset[] presets = {
        new Preset(3,"Craze Silver","Soft light keys with restrained orange accents",0xFFF0F2F5,0xFFFFFFFF,ORANGE,false),
        new Preset(4,"Craze Midnight","Deep slate keys with warm orange accents",0xFF171A20,0xFF292E36,ORANGE,true)
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs=PreferenceManager.getDefaultSharedPreferences(this);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(INPUT);
        getWindow().getDecorView().setSystemUiVisibility(0);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        setContentView(root);
        buildHeader(root);
        pageHost=new FrameLayout(this);
        root.addView(pageHost,new LinearLayout.LayoutParams(-1,0,1f));
        buildNav(root);
        render();
    }

    private void buildHeader(LinearLayout root) {
        LinearLayout h=row();
        h.setPadding(dp(16),dp(10),dp(14),dp(10));
        TextView logo=txt("C",21,BG,true);
        logo.setGravity(Gravity.CENTER); logo.setBackground(bg(PURPLE,11,PURPLE));
        h.addView(logo,new LinearLayout.LayoutParams(dp(36),dp(36)));
        LinearLayout brand=col(); brand.setPadding(dp(10),0,0,0);
        brand.addView(txt("CrazeKeyboard",17,TEXT,true));
        subtitle=txt("KEYBOARD PLAYGROUND",9,CYAN,true); subtitle.setLetterSpacing(.08f);
        brand.addView(subtitle);
        h.addView(brand,new LinearLayout.LayoutParams(0,-2,1f));
        TextView picker=button("⌨  QWERTY  ▾",HIGH,MUTED, new View.OnClickListener(){
            @Override public void onClick(View v){pickKeyboard();}
        });
        h.addView(picker);
        root.addView(h);
        View line=new View(this); line.setBackgroundColor(0xFF222431);
        root.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));
    }

    private void buildNav(LinearLayout root) {
        LinearLayout nav=row(); nav.setPadding(dp(5),dp(6),dp(5),dp(6)); nav.setBackgroundColor(INPUT);
        String[] icons={"⌨","◈","☷"}, names={"Keyboard","Themes","Settings"};
        navItems=new LinearLayout[3]; navIcons=new TextView[3]; navLabels=new TextView[3];
        for(int i=0;i<3;i++){
            final int dest=i; LinearLayout item=col(); item.setGravity(Gravity.CENTER);
            item.setPadding(0,dp(6),0,dp(6));
            TextView icon=txt(icons[i],20,MUTED,true); icon.setGravity(Gravity.CENTER);
            TextView label=txt(names[i],10,MUTED,true); label.setGravity(Gravity.CENTER); label.setPadding(0,dp(3),0,0);
            item.addView(icon); item.addView(label);
            item.setOnClickListener(new View.OnClickListener(){@Override public void onClick(View v){
                tab=dest; page=dest; render();
            }});
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1f); lp.leftMargin=dp(3); lp.rightMargin=dp(3);
            nav.addView(item,lp); navItems[i]=item; navIcons[i]=icon; navLabels[i]=label;
        }
        root.addView(nav);
    }

    private void render() {
        if(pageHost==null)return;
        LinearLayout content=col(); content.setPadding(dp(15),dp(15),dp(15),dp(24));
        if(page==HAPTICS) buildHaptics(content);
        else if(tab==THEMES) buildThemes(content);
        else if(tab==SETTINGS) buildSettings(content);
        else buildKeyboard(content);
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(false); scroll.setClipToPadding(false); scroll.addView(content);
        pageHost.removeAllViews(); pageHost.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
        updateNav();
    }

    private void updateNav() {
        String label=page==HAPTICS?"HAPTICS & SOUNDS":
            tab==THEMES?"THEME STUDIO":tab==SETTINGS?"SETTINGS":"KEYBOARD PLAYGROUND";
        subtitle.setText(label);
        for(int i=0;i<3;i++){
            boolean active=page!=HAPTICS&&tab==i;
            navItems[i].setBackground(bg(active?HIGH:Color.TRANSPARENT,13,active?BORDER:Color.TRANSPARENT));
            navIcons[i].setTextColor(active?(i==THEMES?ORANGE:CYAN):MUTED);
            navLabels[i].setTextColor(active?TEXT:MUTED);
        }
    }

    private void buildKeyboard(LinearLayout c) {
        heading(c,"Your keyboard, your rules.","A live stage for everyday typing.");
        gap(c,14);
        LinearLayout stage=card(); stage.addView(pill("LIVE STAGE",CYAN,0xFF153744));
        gap(stage,10); stage.addView(txt("TRY YOUR KEYBOARD",10,MUTED,true)); gap(stage,6);
        final EditText edit=editor(playgroundText,"Tap here to type with the real CrazeKeyboard IME…",3);
        stage.addView(edit,new LinearLayout.LayoutParams(-1,-2));
        final TextView count=txt(playgroundText.length()+" characters · "+words(playgroundText)+" words",11,MUTED,false);
        gap(stage,6); stage.addView(count);
        edit.addTextChangedListener(new TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int start,int n,int after){}
            @Override public void onTextChanged(CharSequence s,int start,int before,int n){
                playgroundText=s.toString(); count.setText(s.length()+" characters · "+words(s.toString())+" words");
            }
            @Override public void afterTextChanged(Editable e){}
        });
        gap(stage,10); LinearLayout actions=row();
        addWeight(actions,button("Clear",HIGH,TEXT,new View.OnClickListener(){@Override public void onClick(View v){edit.setText("");edit.requestFocus();}}));
        gapX(actions,6);
        addWeight(actions,button("Copy",HIGH,TEXT,new View.OnClickListener(){@Override public void onClick(View v){copy(edit.getText().toString());}}));
        gapX(actions,6);
        addWeight(actions,button("Open keyboard",PURPLE,0xFF30175A,new View.OnClickListener(){@Override public void onClick(View v){
            edit.requestFocus(); InputMethodManager m=(InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
            if(m!=null)m.showSoftInput(edit,InputMethodManager.SHOW_IMPLICIT);
        }}));
        stage.addView(actions); c.addView(stage);
        gap(c,12);

        LinearLayout status=card(); boolean enabled=imeEnabled();
        LinearLayout title=row(); title.addView(txt("Keyboard setup",15,TEXT,true),new LinearLayout.LayoutParams(0,-2,1f));
        title.addView(pill(enabled?"ENABLED":"SETUP NEEDED",enabled?CYAN:PINK,enabled?0xFF153744:0xFF412035));
        status.addView(title); gap(status,5);
        status.addView(txt(enabled?"CrazeKeyboard is enabled. Choose it as your current input method.":"Enable CrazeKeyboard in Android settings before using it in other apps.",12,MUTED,false));
        gap(status,10); LinearLayout setup=row();
        if(!enabled){addWeight(setup,button("Enable keyboard",CYAN,0xFF082933,new View.OnClickListener(){@Override public void onClick(View v){openImeSettings();}}));gapX(setup,7);}
        addWeight(setup,button("Choose input method",HIGH,TEXT,new View.OnClickListener(){@Override public void onClick(View v){pickKeyboard();}}));
        status.addView(setup); c.addView(status); gap(c,10);
        LinearLayout quick=row();
        addWeight(quick,button("◈  Change theme",HIGH,PURPLE,new View.OnClickListener(){@Override public void onClick(View v){tab=THEMES;page=THEMES;render();}}));
        gapX(quick,8);
        addWeight(quick,button("◉  Haptics & sound",HIGH,CYAN,new View.OnClickListener(){@Override public void onClick(View v){page=HAPTICS;render();}}));
        c.addView(quick);
    }

    private void buildThemes(LinearLayout c) {
        heading(c,"Theme Studio","These presets change the real keyboard theme.");
        gap(c,13); int current=KeyboardTheme.getKeyboardTheme(this).mThemeId;
        for(int i=0;i<presets.length;i+=2){
            LinearLayout line=row(); addWeight(line,themeCard(presets[i],current)); gapX(line,8);
            addWeight(line,themeCard(presets[i+1],current)); c.addView(line); gap(c,9);
        }
        LinearLayout note=card(); note.addView(txt("APPLYING A THEME",10,CYAN,true)); gap(note,5);
        note.addView(txt("Your choice is saved in CrazeKeyboard's theme preference. Reopen the keyboard in a text field to see it.",12,MUTED,false));
        c.addView(note);
    }

    private View themeCard(final Preset p,int current) {
        boolean active=current==p.id; LinearLayout card=card(); card.setPadding(dp(10),dp(10),dp(10),dp(10));
        LinearLayout preview=col(); preview.setPadding(dp(6),dp(7),dp(6),dp(7)); preview.setBackground(bg(p.panel,11,p.accent));
        String[] rows={"Q W E R T Y","A S D F G H","⇧  Z X C  ⌫"};
        for(String keys:rows){TextView key=txt(keys,10,p.dark?TEXT:0xFF30343B,true);key.setGravity(Gravity.CENTER);
            key.setBackground(bg(p.key,5,p.accent)); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(24));lp.bottomMargin=dp(3);preview.addView(key,lp);}
        card.addView(preview); gap(card,8); card.addView(txt(p.name,13,TEXT,true)); gap(card,3);
        TextView desc=txt(p.description,10,MUTED,false); desc.setMinLines(2);card.addView(desc);gap(card,8);
        card.addView(button(active?"✓ Active":"Apply",active?p.accent:HIGH,active?(p.dark?INPUT:0xFF40230A):TEXT,
            new View.OnClickListener(){@Override public void onClick(View v){
                KeyboardTheme.saveKeyboardThemeId(p.id,prefs);
                Toast.makeText(CrazeDashboardActivity.this,p.name+" saved. Reopen the keyboard to apply it.",Toast.LENGTH_LONG).show();
                render();
            }}));
        return card;
    }

    private void buildSettings(LinearLayout c) {
        heading(c,"Control Center","Configure the experience and device integration.");
        gap(c,12);LinearLayout state=card();boolean enabled=imeEnabled();
        state.addView(txt(enabled?"CrazeKeyboard is enabled":"CrazeKeyboard needs setup",16,TEXT,true));gap(state,5);
        state.addView(txt(enabled?"Choose it as the current input method.":"Enable it in Android settings, then choose it in the input picker.",12,MUTED,false));gap(state,10);
        state.addView(button(enabled?"Choose keyboard":"Enable keyboard",CYAN,0xFF082933,new View.OnClickListener(){@Override public void onClick(View v){if(imeEnabled())pickKeyboard();else openImeSettings();}}));
        c.addView(state);gap(c,9);
        settingRow(c,"◈","Theme Studio","Two focused keyboard themes",PURPLE,new View.OnClickListener(){@Override public void onClick(View v){tab=THEMES;page=THEMES;render();}});
        gap(c,8);settingRow(c,"◉","Haptics & Sounds","Actual vibration and click preferences",CYAN,new View.OnClickListener(){@Override public void onClick(View v){page=HAPTICS;render();}});
        gap(c,8);settingRow(c,"Aa","Typing preferences","Capitalization, spacing and key preview",PINK,new View.OnClickListener(){@Override public void onClick(View v){openFragment("com.android.inputmethod.latin.settings.PreferencesSettingsFragment");}});
        gap(c,8);settingRow(c,"文","Languages & layouts","Language and layout configuration",PURPLE,new View.OnClickListener(){@Override public void onClick(View v){openFragment("com.android.inputmethod.latin.settings.AppearanceSettingsFragment");}});
        gap(c,8);settingRow(c,"✎","Text correction","Suggestions and auto-correction",CYAN,new View.OnClickListener(){@Override public void onClick(View v){openFragment("com.android.inputmethod.latin.settings.CorrectionSettingsFragment");}});
        gap(c,8);settingRow(c,"⚙","All keyboard settings","Open the full native settings screen",TEXT,new View.OnClickListener(){@Override public void onClick(View v){startActivity(new Intent(CrazeDashboardActivity.this,SettingsActivity.class));}});
        gap(c,10);LinearLayout privacy=card();privacy.addView(txt("ON-DEVICE",10,CYAN,true));gap(privacy,4);
        privacy.addView(txt("Text entered in the keyboard preview stays in this local test screen; it is not sent to a remote prompt service.",12,MUTED,false));c.addView(privacy);
    }

    private void settingRow(LinearLayout parent,String icon,String title,String summary,int accent,View.OnClickListener click){
        LinearLayout r=row();r.setPadding(dp(11),dp(12),dp(11),dp(12));r.setBackground(bg(SURFACE,15,BORDER));
        TextView badge=txt(icon,18,accent,true);badge.setGravity(Gravity.CENTER);badge.setBackground(bg(HIGH,10,HIGH));
        r.addView(badge,new LinearLayout.LayoutParams(dp(40),dp(40)));
        LinearLayout labels=col();labels.setPadding(dp(10),0,dp(4),0);labels.addView(txt(title,13,TEXT,true));gap(labels,3);labels.addView(txt(summary,10,MUTED,false));
        r.addView(labels,new LinearLayout.LayoutParams(0,-2,1f));r.addView(txt("›",23,MUTED,false));r.setOnClickListener(click);parent.addView(r);
    }

    private void buildHaptics(LinearLayout c) {
        c.addView(button("←  Back to settings",HIGH,MUTED,new View.OnClickListener(){@Override public void onClick(View v){tab=SETTINGS;page=SETTINGS;render();}}));
        gap(c,12);heading(c,"Haptics & Sounds","Shape the feel of every keystroke.");gap(c,12);
        LinearLayout toggles=card();toggles.addView(txt("KEY FEEDBACK",10,CYAN,true));gap(toggles,3);
        switchRow(toggles,"Tactile vibration","Short vibration on keypress",Settings.PREF_VIBRATE_ON,getResources().getBoolean(R.bool.config_default_vibration_enabled));
        divider(toggles);
        switchRow(toggles,"Keypress sound","Android keyboard click sounds",Settings.PREF_SOUND_ON,getResources().getBoolean(R.bool.config_default_sound_enabled));
        divider(toggles);
        switchRow(toggles,"Key preview popups","Preview above each pressed key",Settings.PREF_POPUP_ON,getResources().getBoolean(R.bool.config_default_key_preview_popup));
        c.addView(toggles);gap(c,11);

        LinearLayout vib=card();LinearLayout vh=row();vh.addView(txt("TAP DURATION",10,PURPLE,true),new LinearLayout.LayoutParams(0,-2,1f));
        final int max=Math.max(1,Math.min(100,getResources().getInteger(R.integer.config_max_vibration_duration)));
        int initial=Settings.readKeypressVibrationDuration(prefs,getResources());
        final TextView vValue=txt(initial<0?"System default":initial+" ms",11,TEXT,true);vh.addView(vValue);vib.addView(vh);gap(vib,5);
        vib.addView(txt("Saves to the same duration preference used by the keyboard.",11,MUTED,false));
        SeekBar vBar=new SeekBar(this);vBar.setMax(max);vBar.setProgress(Math.max(0,Math.min(max,initial<0?Math.min(18,max):initial)));vib.addView(vBar);
        vBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){@Override public void onProgressChanged(SeekBar b,int val,boolean user){vValue.setText(val+" ms");if(user)prefs.edit().putInt(Settings.PREF_VIBRATION_DURATION_SETTINGS,val).apply();}@Override public void onStartTrackingTouch(SeekBar b){}@Override public void onStopTrackingTouch(SeekBar b){}});
        c.addView(vib);gap(c,11);

        LinearLayout sound=card();LinearLayout sh=row();sh.addView(txt("SOUND LEVEL",10,PINK,true),new LinearLayout.LayoutParams(0,-2,1f));
        int volume=Math.max(0,Math.min(100,Math.round(Settings.readKeypressSoundVolume(prefs,getResources())*100f)));
        final TextView soundValue=txt(volume+"%",11,TEXT,true);sh.addView(soundValue);sound.addView(sh);gap(sound,5);
        SeekBar sBar=new SeekBar(this);sBar.setMax(100);sBar.setProgress(volume);sound.addView(sBar);
        sBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){@Override public void onProgressChanged(SeekBar b,int val,boolean user){soundValue.setText(val+"%");if(user)prefs.edit().putFloat(Settings.PREF_KEYPRESS_SOUND_VOLUME,val/100f).apply();}@Override public void onStartTrackingTouch(SeekBar b){}@Override public void onStopTrackingTouch(SeekBar b){}});
        gap(sound,8);LinearLayout tests=row();
        addWeight(tests,button("Test vibration",HIGH,CYAN,new View.OnClickListener(){@Override public void onClick(View v){testVibration();}}));
        gapX(tests,7);addWeight(tests,button("Test click",HIGH,PINK,new View.OnClickListener(){@Override public void onClick(View v){testSound();}}));
        sound.addView(tests);c.addView(sound);gap(c,10);
        LinearLayout pad=card();pad.addView(txt("Test tap pad",14,TEXT,true));gap(pad,4);
        pad.addView(txt("Feel the selected duration. Sound obeys Android's sound and ringer settings.",11,MUTED,false));gap(pad,8);
        pad.addView(button("●  Tap to test feedback",0xFF302347,PURPLE,new View.OnClickListener(){@Override public void onClick(View v){testVibration();testSound();}}));c.addView(pad);
    }

    private void switchRow(LinearLayout parent,String title,String summary,final String key,boolean defaultValue){
        LinearLayout r=row();LinearLayout words=col();words.addView(txt(title,13,TEXT,true));gap(words,3);words.addView(txt(summary,10,MUTED,false));
        r.addView(words,new LinearLayout.LayoutParams(0,-2,1f));Switch sw=new Switch(this);sw.setChecked(prefs.getBoolean(key,defaultValue));
        sw.setOnCheckedChangeListener((button,checked)->prefs.edit().putBoolean(key,checked).apply());r.addView(sw);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(8);lp.bottomMargin=dp(8);parent.addView(r,lp);
    }

    private void testVibration(){
        Vibrator vibrator=(Vibrator)getSystemService(Context.VIBRATOR_SERVICE);
        if(vibrator==null||!vibrator.hasVibrator()){Toast.makeText(this,"No vibrator is available on this device.",Toast.LENGTH_SHORT).show();return;}
        int duration=Settings.readKeypressVibrationDuration(prefs,getResources());if(duration<0)duration=18;duration=Math.max(1,Math.min(100,duration));
        if(Build.VERSION.SDK_INT>=26)vibrator.vibrate(VibrationEffect.createOneShot(duration,VibrationEffect.DEFAULT_AMPLITUDE));else vibrator.vibrate(duration);
    }
    private void testSound(){
        if(!prefs.getBoolean(Settings.PREF_SOUND_ON,getResources().getBoolean(R.bool.config_default_sound_enabled))){Toast.makeText(this,"Turn on keypress sound to test it.",Toast.LENGTH_SHORT).show();return;}
        final ToneGenerator tone=new ToneGenerator(AudioManager.STREAM_SYSTEM,35);tone.startTone(ToneGenerator.TONE_PROP_BEEP,85);
        new Handler().postDelayed(new Runnable(){@Override public void run(){tone.release();}},120);
    }
    private void openFragment(String name){Intent i=new Intent(this,SettingsActivity.class);i.putExtra(PreferenceActivity.EXTRA_SHOW_FRAGMENT,name);i.putExtra(PreferenceActivity.EXTRA_NO_HEADERS,true);startActivity(i);}
    private void openImeSettings(){try{startActivity(new Intent(android.provider.Settings.ACTION_INPUT_METHOD_SETTINGS));}catch(Exception e){startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));}}
    private boolean imeEnabled(){InputMethodManager m=(InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);if(m==null)return false;List<InputMethodInfo> list=m.getEnabledInputMethodList();for(InputMethodInfo info:list)if(getPackageName().equals(info.getPackageName()))return true;return false;}
    private void pickKeyboard(){InputMethodManager m=(InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);if(m!=null)m.showInputMethodPicker();}
    private void copy(String value){if(value==null||value.trim().length()==0){Toast.makeText(this,"There is no text to copy yet.",Toast.LENGTH_SHORT).show();return;}ClipboardManager cb=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);if(cb!=null){cb.setPrimaryClip(ClipData.newPlainText("CrazeKeyboard",value));Toast.makeText(this,"Copied to clipboard.",Toast.LENGTH_SHORT).show();}}
    private int words(String s){String t=s.trim();return t.length()==0?0:t.split("\\s+").length;}

    private EditText editor(String value,String hint,int lines){EditText e=new EditText(this);e.setTextColor(TEXT);e.setHintTextColor(0xFF7B7D93);e.setTextSize(14);e.setGravity(Gravity.TOP|Gravity.START);e.setPadding(dp(12),dp(12),dp(12),dp(12));e.setMinLines(lines);e.setMaxLines(lines+3);e.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);e.setHint(hint);e.setBackground(bg(INPUT,12,0xFF2B2D3E));e.setText(value);return e;}
    private void heading(LinearLayout c,String title,String sub){c.addView(txt(title,25,TEXT,true));gap(c,4);c.addView(txt(sub,12,MUTED,false));}
    private LinearLayout card(){LinearLayout l=col();l.setPadding(dp(13),dp(13),dp(13),dp(13));l.setBackground(bg(SURFACE,17,BORDER));return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private LinearLayout col(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private TextView txt(String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setGravity(Gravity.CENTER_VERTICAL);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private TextView button(String s,int color,int foreground,View.OnClickListener click){TextView t=txt(s,11,foreground,true);t.setGravity(Gravity.CENTER);t.setPadding(dp(9),dp(9),dp(9),dp(9));t.setMinHeight(dp(42));t.setBackground(bg(color,12,(color==HIGH||color==SURFACE)?BORDER:color));t.setClickable(true);t.setFocusable(true);t.setOnClickListener(click);return t;}
    private TextView pill(String s,int fg,int color){TextView t=txt(s,9,fg,true);t.setGravity(Gravity.CENTER);t.setPadding(dp(9),dp(5),dp(9),dp(5));t.setBackground(bg(color,30,color));return t;}
    private GradientDrawable bg(int color,int radius,int stroke){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));d.setStroke(dp(1),stroke);return d;}
    private void gap(LinearLayout l,int h){l.addView(new View(this),new LinearLayout.LayoutParams(1,dp(h)));}
    private void gapX(LinearLayout l,int w){l.addView(new View(this),new LinearLayout.LayoutParams(dp(w),1));}
    private void addWeight(LinearLayout l,View v){l.addView(v,new LinearLayout.LayoutParams(0,-2,1f));}
    private void divider(LinearLayout l){View v=new View(this);v.setBackgroundColor(0xFF2E3040);l.addView(v,new LinearLayout.LayoutParams(-1,dp(1)));}
    private int dp(float x){return (int)(x*getResources().getDisplayMetrics().density+0.5f);}
}
