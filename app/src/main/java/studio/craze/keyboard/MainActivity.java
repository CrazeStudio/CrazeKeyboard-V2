package studio.craze.keyboard;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class MainActivity extends Activity {
    private TextView status;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setTitle("CrazeKeyboard");

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(28), dp(24), dp(32));
        root.setBackgroundColor(0xFFF8F8FA);
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText("CrazeKeyboard");
        title.setTextSize(28);
        title.setTextColor(0xFF202124);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Private, offline typing. Set up the keyboard in two quick steps.");
        subtitle.setTextSize(16);
        subtitle.setTextColor(0xFF55545E);
        subtitle.setPadding(0, dp(8), 0, dp(16));
        root.addView(subtitle);

        status = new TextView(this);
        status.setTextSize(15);
        status.setTextColor(0xFF34323B);
        status.setPadding(dp(16), dp(14), dp(16), dp(14));
        status.setBackgroundColor(0xFFECEAF2);
        root.addView(status, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        Button enable = button("1. Enable keyboard");
        enable.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        root.addView(enable);

        Button choose = button("2. Choose CrazeKeyboard");
        choose.setOnClickListener(v -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showInputMethodPicker();
        });
        root.addView(choose);

        Button dictionary = button("Typing and appearance settings");
        dictionary.setOnClickListener(v -> startActivity(new Intent(this, DictionaryActivity.class)));
        root.addView(dictionary);

        TextView privacy = new TextView(this);
        privacy.setText("Your learned words stay on this device. CrazeKeyboard does not send your typing anywhere.");
        privacy.setTextSize(14);
        privacy.setTextColor(0xFF666570);
        privacy.setPadding(0, dp(18), 0, 0);
        root.addView(privacy);
        setContentView(scroll);
    }

    @Override protected void onResume() {
        super.onResume();
        updateStatus();
    }

    private void updateStatus() {
        if (status == null) return;
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        boolean enabled = false;
        if (imm != null) {
            for (InputMethodInfo info : imm.getEnabledInputMethodList()) {
                if (getPackageName().equals(info.getPackageName())) {
                    enabled = true;
                    break;
                }
            }
        }
        String selected = Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        boolean active = selected != null && selected.startsWith(getPackageName() + "/");
        if (active) status.setText("Ready — CrazeKeyboard is enabled and selected.");
        else if (enabled) status.setText("Almost ready — CrazeKeyboard is enabled. Choose it as your current keyboard.");
        else status.setText("Setup needed — enable CrazeKeyboard, then choose it as your current keyboard.");
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setMinHeight(dp(52));
        return b;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
