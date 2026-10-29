package studio.craze.keyboard;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {
    private final int ink = Color.rgb(34, 32, 39);
    private final int muted = Color.rgb(103, 99, 111);
    private final int accent = Color.rgb(103, 80, 164);

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(247,245,250));
        getWindow().setNavigationBarColor(Color.rgb(247,245,250));
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        ScrollView scroll = new ScrollView(this);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(24), dp(28), dp(24), dp(28));
        scroll.addView(page);

        TextView eyebrow = text("CRAZESTUDIO  /  INPUT", 12, muted, true);
        page.addView(eyebrow);
        TextView title = text("CrazeKeyboard", 30, ink, true);
        LinearLayout.LayoutParams titleLp = params(-1, -2); titleLp.topMargin = dp(12);
        page.addView(title, titleLp);
        TextView intro = text("A simple keyboard built around private, everyday typing.", 16, muted, false);
        LinearLayout.LayoutParams introLp = params(-1, -2); introLp.topMargin = dp(8);
        page.addView(intro, introLp);

        LinearLayout status = new LinearLayout(this);
        status.setOrientation(LinearLayout.VERTICAL);
        status.setPadding(dp(18), dp(18), dp(18), dp(18));
        status.setBackground(panel(Color.WHITE, dp(20)));
        LinearLayout.LayoutParams statusLp = params(-1, -2); statusLp.topMargin = dp(28);
        page.addView(status, statusLp);
        status.addView(text("GET STARTED", 12, accent, true));
        TextView step = text("1. Enable CrazeKeyboard in Android settings.\n2. Choose it when you are ready to type.", 15, ink, false);
        LinearLayout.LayoutParams stepLp = params(-1, -2); stepLp.topMargin = dp(10);
        status.addView(step, stepLp);
        Button enable = button("Enable keyboard", accent, Color.WHITE);
        LinearLayout.LayoutParams enableLp = params(-1, dp(52)); enableLp.topMargin = dp(18);
        status.addView(enable, enableLp);
        enable.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        Button choose = button("Choose keyboard", Color.rgb(238,233,247), ink);
        LinearLayout.LayoutParams chooseLp = params(-1, dp(52)); chooseLp.topMargin = dp(10);
        status.addView(choose, chooseLp);
        choose.setOnClickListener(v -> {
            InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showInputMethodPicker();
        });

        LinearLayout privacy = new LinearLayout(this);
        privacy.setOrientation(LinearLayout.VERTICAL);
        privacy.setPadding(dp(18), dp(18), dp(18), dp(18));
        privacy.setBackground(panel(Color.WHITE, dp(20)));
        LinearLayout.LayoutParams privacyLp = params(-1, -2); privacyLp.topMargin = dp(16);
        page.addView(privacy, privacyLp);
        privacy.addView(text("PRIVATE BY DESIGN", 12, accent, true));
        TextView details = text("No Internet permission. No account or cloud sync. Text is inserted into the app you are typing in. Suggestions and network features are not included in this first build.", 14, muted, false);
        LinearLayout.LayoutParams detailsLp = params(-1, -2); detailsLp.topMargin = dp(10);
        privacy.addView(details, detailsLp);

        LinearLayout appearance = new LinearLayout(this);
        appearance.setOrientation(LinearLayout.VERTICAL);
        appearance.setPadding(dp(18), dp(18), dp(18), dp(18));
        appearance.setBackground(panel(Color.WHITE, dp(20)));
        LinearLayout.LayoutParams appearanceLp = params(-1, -2); appearanceLp.topMargin = dp(16);
        page.addView(appearance, appearanceLp);
        appearance.addView(text("KEYBOARD APPEARANCE", 12, accent, true));
        boolean darkKeyboard = getSharedPreferences("crazekeyboard", MODE_PRIVATE).getBoolean("dark", false);
        Button themeButton = button(darkKeyboard ? "Switch keyboard to light" : "Switch keyboard to dark",
                Color.rgb(238,233,247), ink);
        LinearLayout.LayoutParams themeLp = params(-1, dp(50)); themeLp.topMargin = dp(12);
        appearance.addView(themeButton, themeLp);
        themeButton.setOnClickListener(v -> {
            boolean current = getSharedPreferences("crazekeyboard", MODE_PRIVATE).getBoolean("dark", false);
            getSharedPreferences("crazekeyboard", MODE_PRIVATE).edit().putBoolean("dark", !current).apply();
            themeButton.setText(!current ? "Switch keyboard to light" : "Switch keyboard to dark");
        });
        int keyboardHeight = getSharedPreferences("crazekeyboard", MODE_PRIVATE).getInt("height", 304);
        Button heightButton = button("Keyboard height: " + keyboardHeight + " dp  ·  Change",
                Color.rgb(238,233,247), ink);
        LinearLayout.LayoutParams heightLp = params(-1, dp(50)); heightLp.topMargin = dp(10);
        appearance.addView(heightButton, heightLp);
        heightButton.setOnClickListener(v -> {
            int current = getSharedPreferences("crazekeyboard", MODE_PRIVATE).getInt("height", 304);
            int next = current >= 344 ? 280 : current + 20;
            getSharedPreferences("crazekeyboard", MODE_PRIVATE).edit().putInt("height", next).apply();
            heightButton.setText("Keyboard height: " + next + " dp  ·  Change");
        });

        TextView footer = text("Version 0.1.0  •  Early build", 12, muted, false);
        LinearLayout.LayoutParams footerLp = params(-1, -2); footerLp.topMargin = dp(24);
        page.addView(footer, footerLp);
        setContentView(scroll);
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setGravity(Gravity.CENTER_VERTICAL); return t;
    }
    private Button button(String value, int bg, int fg) {
        Button b = new Button(this); b.setText(value); b.setTextColor(fg); b.setAllCaps(false);
        b.setTextSize(15); b.setBackground(panel(bg, dp(14))); return b;
    }
    private GradientDrawable panel(int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(radius); return d;
    }
    private LinearLayout.LayoutParams params(int w, int h) { return new LinearLayout.LayoutParams(w, h); }
    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density + 0.5f); }
}
