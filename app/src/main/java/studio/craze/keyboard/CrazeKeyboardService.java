package studio.craze.keyboard;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.inputmethodservice.InputMethodService;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class CrazeKeyboardService extends InputMethodService {
    private KeyboardView keyboard;

    @Override public View onCreateInputView() {
        keyboard = new KeyboardView();
        return keyboard;
    }

    @Override public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        if (keyboard != null) {
            int inputClass = info == null ? 0 : info.inputType & 0x0f;
            int variation = info == null ? 0 : info.inputType & 0xff0;
            boolean password = inputClass == 2 && variation == 0x10
                    || inputClass == 1 && (variation == 0x80 || variation == 0x90 || variation == 0xe0);
            keyboard.setPasswordField(password);
            keyboard.setNumericMode(inputClass == 2 || inputClass == 3);
            keyboard.requestLayout();
            keyboard.invalidate();
        }
    }

    private InputConnection connection() { return getCurrentInputConnection(); }

    private void type(String value) {
        InputConnection ic = connection();
        if (ic != null) ic.commitText(value, 1);
    }

    private void backspace() {
        InputConnection ic = connection();
        if (ic == null) return;
        CharSequence selected = ic.getSelectedText(0);
        if (selected != null && selected.length() > 0) {
            ic.commitText("", 1);
            return;
        }
        CharSequence before = ic.getTextBeforeCursor(2, 0);
        if (before != null && before.length() > 0) {
            int last = Character.codePointBefore(before, before.length());
            ic.deleteSurroundingText(Character.charCount(last), 0);
        } else {
            ic.sendKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN,
                    android.view.KeyEvent.KEYCODE_DEL));
            ic.sendKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_UP,
                    android.view.KeyEvent.KEYCODE_DEL));
        }
    }

    private void enter() {
        InputConnection ic = connection();
        if (ic == null) return;
        EditorInfo info = getCurrentInputEditorInfo();
        int action = info == null ? EditorInfo.IME_ACTION_NONE : info.imeOptions & EditorInfo.IME_MASK_ACTION;
        if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED
                ) {
            ic.performEditorAction(action);
        } else {
            ic.commitText("\n", 1);
        }
    }

    private final class KeyboardView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int background = Color.rgb(245, 245, 249);
        private int keyColor = Color.rgb(255, 255, 255);
        private int actionColor = Color.rgb(225, 220, 239);
        private int ink = Color.rgb(33, 33, 39);
        private int muted = Color.rgb(104, 103, 115);
        private boolean shifted = true;
        private boolean symbols;
        private boolean password;
        private boolean numeric;
        private boolean editTools;
        private float keyHeight;
        private float toolbarHeight;
        private float suggestionHeight;
        private String[][] rows;
        private List<String> suggestions = new ArrayList<>();

        KeyboardView() { super(CrazeKeyboardService.this); setFocusable(true); }

        void setPasswordField(boolean value) { password = value; invalidate(); }
        void setNumericMode(boolean value) { numeric = value; symbols = value; invalidate(); }

        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            int width = MeasureSpec.getSize(widthSpec);
            int heightSetting = getSharedPreferences("crazekeyboard", MODE_PRIVATE).getInt("height", 304);
            int desired = dp(Math.max(260, Math.min(360, heightSetting)));
            setMeasuredDimension(width, resolveSize(desired, heightSpec));
        }

        private void applyAppearance() {
            boolean dark = getSharedPreferences("crazekeyboard", MODE_PRIVATE).getBoolean("dark", false);
            if (dark) {
                background = Color.rgb(31, 29, 36);
                keyColor = Color.rgb(55, 52, 62);
                actionColor = Color.rgb(91, 78, 119);
                ink = Color.rgb(248, 246, 252);
                muted = Color.rgb(199, 193, 209);
            } else {
                background = Color.rgb(245, 245, 249);
                keyColor = Color.rgb(255, 255, 255);
                actionColor = Color.rgb(225, 220, 239);
                ink = Color.rgb(33, 33, 39);
                muted = Color.rgb(104, 103, 115);
            }
        }

        private void makeRows() {
            if (symbols) {
                rows = new String[][] {
                    {"1","2","3","4","5","6","7","8","9","0"},
                    {"@","#","$","%","&","-","+","(",")","/"},
                    {"=","*","\"","' ",":",";","!","?","⌫"},
                    {"ABC",",","?123","space",".","↵"}
                };
            } else {
                String language = getSharedPreferences("crazekeyboard", MODE_PRIVATE).getString("language", "en");
                if ("bn".equals(language)) {
                    rows = new String[][] {
                        {"অ","আ","ই","ঈ","উ","ঊ","এ","ঐ","ও","ঔ"},
                        {"ক","খ","গ","ঘ","ঙ","চ","ছ","জ","ঝ"},
                        {"⇧","ট","ঠ","ড","ঢ","ণ","ত","থ","দ","⌫"},
                        {"?123","ম","☺","space","।","↵"}
                    };
                } else {
                    rows = new String[][] {
                        shifted ? new String[]{"Q","W","E","R","T","Y","U","I","O","P"} : new String[]{"q","w","e","r","t","y","u","i","o","p"},
                        shifted ? new String[]{"A","S","D","F","G","H","J","K","L"} : new String[]{"a","s","d","f","g","h","j","k","l"},
                        {"⇧", shifted?"Z":"z",shifted?"X":"x",shifted?"C":"c",shifted?"V":"v",shifted?"B":"b",shifted?"N":"n",shifted?"M":"m","⌫"},
                        {"?123",",","☺","space",".","↵"}
                    };
                }
            }
        }

        private float weight(String s) {
            if (s.equals("space")) return 4.2f;
            if (s.equals("?123") || s.equals("ABC")) return 1.45f;
            if (s.equals("⇧") || s.equals("⌫") || s.equals("↵")) return 1.35f;
            if (s.equals("☺")) return 1.15f;
            return 1f;
        }

        private boolean special(String s) {
            return Arrays.asList("space","?123","ABC","⇧","⌫","↵","☺").contains(s);
        }

        private void refreshSuggestions() {
            suggestions.clear();
            if (password || numeric || symbols || !getSharedPreferences("crazekeyboard", MODE_PRIVATE).getBoolean("suggestions", true) || "bn".equals(getSharedPreferences("crazekeyboard", MODE_PRIVATE).getString("language", "en"))) return;
            InputConnection ic = connection();
            if (ic == null) return;
            CharSequence before = ic.getTextBeforeCursor(80, 0);
            if (before == null || before.length() == 0) return;
            String text = before.toString();
            int start = text.length();
            while (start > 0 && Character.isLetter(text.charAt(start - 1))) start--;
            if (start == text.length()) return;
            String prefix = text.substring(start).toLowerCase(Locale.ROOT);
            if (prefix.length() == 0 || prefix.length() > 24) return;
            String[] words = {"about","after","again","all","also","and","are","because","been","before",
                    "best","can","come","craze","day","did","do","down","each","even","every","find",
                    "first","for","from","get","give","good","great","have","hello","help","here","how",
                    "just","know","like","little","make","more","most","much","need","new","not","now",
                    "one","only","other","our","out","people","please","really","right","say","see",
                    "some","still","take","tell","than","that","the","their","them","then","there",
                    "these","think","this","time","to","today","too","use","very","want","was","way",
                    "we","well","what","when","where","which","who","will","with","work","would","you",
                    "your"};
            String typed = text.substring(start);
            suggestions.add(typed);
            for (String word : words) {
                if (word.startsWith(prefix) && !word.equals(prefix) && !suggestions.contains(word)) {
                    suggestions.add(word);
                    if (suggestions.size() == 3) break;
                }
            }
        }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            applyAppearance();
            c.drawColor(background);
            makeRows();
            toolbarHeight = dp(40);
            suggestionHeight = dp(34);
            drawToolbar(c);
            refreshSuggestions();
            drawSuggestions(c);
            float top = toolbarHeight + suggestionHeight + dp(5);
            float gap = dp(5), side = dp(4), bottom = dp(6);
            keyHeight = Math.max(dp(32), (getHeight() - top - bottom - gap * (rows.length - 1)) / rows.length);
            for (int r = 0; r < rows.length; r++) {
                String[] row = rows[r];
                float total = 0;
                for (String s : row) total += weight(s);
                float available = getWidth() - side * 2 - gap * (row.length - 1);
                float x = side;
                for (String label : row) {
                    float w = available * weight(label) / total;
                    float y = top + r * (keyHeight + gap);
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(special(label) ? actionColor : keyColor);
                    RectF keyRect = new RectF(x, y, x + w, y + keyHeight);
                    c.drawRoundRect(keyRect, dp(7), dp(7), p);
                    if (getSharedPreferences("crazekeyboard", MODE_PRIVATE).getBoolean("key_borders", false)) {
                        p.setStyle(Paint.Style.STROKE);
                        p.setStrokeWidth(dp(1));
                        p.setColor(background == Color.rgb(31, 29, 36) ? Color.rgb(100, 96, 110) : Color.rgb(210, 208, 218));
                        c.drawRoundRect(keyRect, dp(7), dp(7), p);
                        p.setStyle(Paint.Style.FILL);
                    }
                    p.setColor(ink);
                    p.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
                    p.setTextAlign(Paint.Align.CENTER);
                    p.setTextSize(label.equals("space") ? dp(11) : dp(18));
                    String shown = label.equals("space") ? "English (US)" : label;
                    c.drawText(shown, x + w / 2, y + keyHeight / 2 - (p.ascent() + p.descent()) / 2, p);
                    x += w + gap;
                }
            }
        }

        private void drawToolbar(Canvas c) {
            String[] items = editTools
                    ? new String[]{"Select all","Copy","Cut","Paste","Done"}
                    : new String[]{"▤","✎","☺","⚙"};
            float cell = getWidth() / (float) items.length;
            for (int i = 0; i < items.length; i++) {
                p.setColor(i == 0 ? actionColor : background);
                c.drawRoundRect(new RectF(i * cell + dp(3), dp(3), (i + 1) * cell - dp(3), toolbarHeight - dp(3)), dp(9), dp(9), p);
                p.setColor(i == 0 ? ink : muted);
                p.setTextAlign(Paint.Align.CENTER);
                p.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                p.setTextSize(items[i].length() > 2 ? dp(11) : dp(19));
                c.drawText(items[i], i * cell + cell / 2, toolbarHeight / 2 - (p.ascent() + p.descent()) / 2, p);
            }
        }

        private void drawSuggestions(Canvas c) {
            float width = getWidth() / 3f;
            for (int i = 0; i < 3; i++) {
                p.setColor(muted);
                p.setTextAlign(Paint.Align.CENTER);
                p.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
                p.setTextSize(dp(14));
                String value = i < suggestions.size() ? suggestions.get(i) : "";
                c.drawText(value, width * i + width / 2, toolbarHeight + suggestionHeight / 2 - (p.ascent() + p.descent()) / 2, p);
                if (i > 0) {
                    p.setColor(Color.rgb(218, 215, 224));
                    c.drawLine(i * width, toolbarHeight + dp(7), i * width, toolbarHeight + suggestionHeight - dp(7), p);
                }
            }
        }

        private void applySuggestion(int index) {
            if (index >= suggestions.size() || password) return;
            InputConnection ic = connection();
            if (ic == null) return;
            CharSequence before = ic.getTextBeforeCursor(80, 0);
            if (before == null) return;
            String text = before.toString();
            int start = text.length();
            while (start > 0 && Character.isLetter(text.charAt(start - 1))) start--;
            if (start == text.length()) return;
            String replacement = suggestions.get(index);
            if (start > 0 && Character.isUpperCase(text.charAt(start))) {
                replacement = replacement.substring(0,1).toUpperCase(Locale.ROOT) + replacement.substring(1);
            }
            ic.deleteSurroundingText(text.length() - start, 0);
            ic.commitText(replacement + " ", 1);
            shifted = false;
            invalidate();
        }

        private void paste() {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip().getItemCount() > 0) {
                CharSequence value = cm.getPrimaryClip().getItemAt(0).coerceToText(CrazeKeyboardService.this);
                if (value != null) type(value.toString());
            }
        }

        private void toolbarAction(int index) {
            if (editTools) {
                InputConnection ic = connection();
                if (ic == null) return;
                switch (index) {
                    case 0: ic.performContextMenuAction(android.R.id.selectAll); break;
                    case 1:
                        CharSequence selected = ic.getSelectedText(0);
                        if (selected != null) copyText(selected.toString());
                        break;
                    case 2:
                        CharSequence cut = ic.getSelectedText(0);
                        if (cut != null) { copyText(cut.toString()); ic.commitText("", 1); }
                        break;
                    case 3: paste(); break;
                    default: editTools = false; break;
                }
                invalidate();
                return;
            }
            switch (index) {
                case 0: paste(); break;
                case 1: editTools = true; break;
                case 2: type("🙂"); break;
                case 3:
                    Intent intent = new Intent(CrazeKeyboardService.this, MainActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    break;
            }
        }

        private void copyText(String value) {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Selected text", value));
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (e.getAction() != MotionEvent.ACTION_UP) return true;
            float y = e.getY();
            if (y < toolbarHeight) {
                int count = editTools ? 5 : 4;
                int index = Math.min(count - 1, (int)(e.getX() / (getWidth() / (float)count)));
                toolbarAction(index);
                return true;
            }
            if (y < toolbarHeight + suggestionHeight) {
                if (!password && !numeric && !symbols) {
                    int index = Math.min(2, (int)(e.getX() / (getWidth() / 3f)));
                    applySuggestion(index);
                }
                return true;
            }
            float top = toolbarHeight + suggestionHeight + dp(5);
            float gap = dp(5), side = dp(4);
            int rowIndex = (int)((y - top) / (keyHeight + gap));
            if (rowIndex < 0 || rowIndex >= rows.length) return true;
            String[] row = rows[rowIndex];
            float total = 0;
            for (String s : row) total += weight(s);
            float available = getWidth() - side * 2 - gap * (row.length - 1), x = side;
            String hit = null;
            for (String s : row) {
                float w = available * weight(s) / total;
                if (e.getX() >= x && e.getX() <= x + w) { hit = s; break; }
                x += w + gap;
            }
            if (hit == null) return true;
            switch (hit) {
                case "⇧": shifted = !shifted; invalidate(); break;
                case "⌫": backspace(); invalidate(); break;
                case "↵": enter(); invalidate(); break;
                case "space": type(" "); shifted = false; invalidate(); break;
                case "?123": symbols = true; invalidate(); break;
                case "ABC": symbols = false; invalidate(); break;
                case "☺": type("🙂"); shifted = false; invalidate(); break;
                case ",": type(","); shifted = false; invalidate(); break;
                case ".": type("."); shifted = true; invalidate(); break;
                default:
                    type(shifted ? hit.toUpperCase(Locale.ROOT) : hit.toLowerCase(Locale.ROOT));
                    shifted = false;
                    invalidate();
            }
            return true;
        }

        private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density + 0.5f); }
    }
}
