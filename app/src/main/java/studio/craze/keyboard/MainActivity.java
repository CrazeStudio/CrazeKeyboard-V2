package studio.craze.keyboard;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class MainActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 48, 32, 32);
        root.setBackgroundColor(0xFFF8F8FA);
        TextView title = new TextView(this);
        title.setText("CrazeKeyboard");
        title.setTextSize(28);
        title.setTextColor(0xFF202124);
        root.addView(title);
        TextView subtitle = new TextView(this);
        subtitle.setText("Private, offline typing. Enable the keyboard, then select it as your default.");
        subtitle.setTextSize(16);
        subtitle.setPadding(0, 12, 0, 24);
        root.addView(subtitle);
        Button enable = new Button(this);
        enable.setText("Enable keyboard");
        enable.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        root.addView(enable);
        Button choose = new Button(this);
        choose.setText("Choose keyboard");
        choose.setOnClickListener(v -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showInputMethodPicker();
        });
        root.addView(choose);
        Button dictionary = new Button(this);
        dictionary.setText("Dictionary settings");
        dictionary.setOnClickListener(v -> startActivity(new Intent(this, DictionaryActivity.class)));
        root.addView(dictionary);
        setContentView(root);
    }
}
