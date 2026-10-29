package studio.craze.keyboard;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
public final class MainActivity extends Activity {
 @Override protected void onCreate(Bundle state) {
  super.onCreate(state);
  LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
  int pad = (int)(20 * getResources().getDisplayMetrics().density + 0.5f); root.setPadding(pad,pad,pad,pad);
  TextView title = new TextView(this); title.setText("CrazeKeyboard"); title.setTextSize(28); title.setTextColor(0xFF292532); root.addView(title);
  TextView description = new TextView(this); description.setText("Private, offline typing. Enable the keyboard in Android settings, then choose it as your current input method."); description.setTextSize(16); description.setPadding(0,pad/2,0,pad); root.addView(description);
  Button enable = new Button(this); enable.setText("Enable keyboard"); enable.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))); root.addView(enable);
  Button select = new Button(this); select.setText("Choose keyboard"); select.setOnClickListener(v -> { InputMethodManager imm=(InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE); if(imm!=null)imm.showInputMethodPicker(); }); root.addView(select);
  Button dictionary = new Button(this); dictionary.setText("Dictionary and typing settings"); dictionary.setOnClickListener(v -> startActivity(new Intent(this,DictionaryActivity.class))); root.addView(dictionary);
  setContentView(root);
 }
}
