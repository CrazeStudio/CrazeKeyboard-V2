package studio.craze.keyboard;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import studio.craze.keyboard.core.AutoCorrector;
import studio.craze.keyboard.core.Dictionary;
import studio.craze.keyboard.core.GlideDecoder;
import studio.craze.keyboard.core.SuggestionEngine;
import studio.craze.keyboard.core.TextContext;
import studio.craze.keyboard.core.WordChars;

public class CrazeKeyboardService extends InputMethodService {
    private static final String PREFS = "crazekeyboard";
    private static final int NO_PERSONALIZED_LEARNING = 0x1000000; // EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING

    private KeyboardView keyboard;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable saveTask = new Runnable() {
        @Override public void run() { DictionaryHolder.saveAsync(CrazeKeyboardService.this); }
    };
    /** True in password fields and in fields that ask the keyboard not to learn. */
    private boolean noLearning;
    private boolean glideHintShown;
    // The last automatic correction, so Backspace can undo it.
    private String undoOriginal, undoFixed, undoBoundary;

    private boolean pref(String key, boolean fallback) {
        return getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(key, fallback);
    }

    private Dictionary dictionary() { return DictionaryHolder.get(this); }

    private void clearUndo() { undoOriginal = null; undoFixed = null; undoBoundary = null; }

    private void learn(String word, String prev, boolean sentenceStart) {
        if (noLearning || !pref("learn", true)) return;
        dictionary().learn(word, prev, System.currentTimeMillis(), sentenceStart);
        handler.removeCallbacks(saveTask);
        handler.postDelayed(saveTask, 5000);
    }

    @Override public View onCreateInputView() {
        keyboard = new KeyboardView();
        return keyboard;
    }

    @Override public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        clearUndo();
        if (keyboard != null) {
            int inputClass = info == null ? 0 : info.inputType & 0x0f;
            int variation = info == null ? 0 : info.inputType & 0xff0;
            boolean password = inputClass == 2 && variation == 0x10
                    || inputClass == 1 && (variation == 0x80 || variation == 0x90 || variation == 0xe0);
            boolean incognito = info != null && (info.imeOptions & NO_PERSONALIZED_LEARNING) != 0;
            noLearning = password || incognito;
            keyboard.setPasswordField(password);
            keyboard.setNumericMode(inputClass == 2 || inputClass == 3);
            keyboard.requestLayout();
            keyboard.updateSuggestions();
        }
    }

    @Override public void onUpdateSelection(int oldSelStart, int oldSelEnd, int newSelStart, int newSelEnd,
                                            int candidatesStart, int candidatesEnd) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd);
        if (keyboard != null) keyboard.updateSuggestions();
    }

    @Override public void onFinishInput() {
        super.onFinishInput();
        clearUndo();
        handler.removeCallbacks(saveTask);
        DictionaryHolder.saveAsync(this);
    }

    @Override public void onDestroy() {
        handler.removeCallbacks(saveTask);
        DictionaryHolder.saveNow(this);
        super.onDestroy();
    }

    private InputConnection connection() { return getCurrentInputConnection(); }

    private void type(String value) {
        InputConnection ic = connection();
        if (ic != null) ic.commitText(value, 1);
    }

    private void backspace() {
        InputConnection ic = connection();
        if (ic == null) return;
        if (undoOriginal != null) {
            String tail = undoFixed + undoBoundary;
            CharSequence tailText = ic.getTextBeforeCursor(tail.length(), 0);
            if (tailText != null && tailText.toString().equals(tail)) {
                ic.deleteSurroundingText(tail.length(), 0);
                ic.commitText(undoOriginal, 1);
                if (!noLearning && pref("learn", true)) {
                    // typing it again after an undo teaches the keyboard that this word is meant
                    dictionary().learn(undoOriginal, "", System.currentTimeMillis(), false, 2);
                    handler.removeCallbacks(saveTask);
                    handler.postDelayed(saveTask, 5000);
                }
                clearUndo();
                return;
            }
            clearUndo();
        }
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
        if (keyboard != null) keyboard.finishWord("\n");
        EditorInfo info = getCurrentInputEditorInfo();
        int action = info == null ? EditorInfo.IME_ACTION_NONE : info.imeOptions & EditorInfo.IME_MASK_ACTION;
        if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED
                ) {
            clearUndo();
            ic.performEditorAction(action);
        } else {
            ic.commitText("\n", 1);
        }
    }

    private final class Key {
        final String label;
        final RectF rect;
        final boolean letter;

        Key(String label, RectF rect, boolean letter) {
            this.label = label;
            this.rect = rect;
            this.letter = letter;
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
        private final List<Key> keys = new ArrayList<>();
        private List<String> suggestions = new ArrayList<>();
        // glide typing
        private final ArrayList<float[]> trail = new ArrayList<>();
        private final Path trailPath = new Path();
        private boolean trackingGlide;
        private boolean gliding;
        private float downX, downY;

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
                    {"=","*","\"","'",":",";","!","?","⌫"},
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

        /** Works out where every key sits. Drawing and touch both use this, so they always agree. */
        private void buildKeys() {
            makeRows();
            toolbarHeight = dp(40);
            suggestionHeight = dp(34);
            float top = toolbarHeight + suggestionHeight + dp(5);
            float gap = dp(5), side = dp(4), bottom = dp(6);
            keyHeight = Math.max(dp(32), (getHeight() - top - bottom - gap * (rows.length - 1)) / rows.length);
            keys.clear();
            for (int r = 0; r < rows.length; r++) {
                String[] row = rows[r];
                float total = 0;
                for (String s : row) total += weight(s);
                float available = getWidth() - side * 2 - gap * (row.length - 1);
                float x = side;
                for (String label : row) {
                    float w = available * weight(label) / total;
                    float y = top + r * (keyHeight + gap);
                    boolean letter = !special(label) && label.codePointCount(0, label.length()) == 1
                            && WordChars.isWordChar(label.codePointAt(0));
                    keys.add(new Key(label, new RectF(x, y, x + w, y + keyHeight), letter));
                    x += w + gap;
                }
            }
        }

        private Key keyAt(float x, float y) {
            Key best = null;
            float bestDistance = Float.MAX_VALUE;
            for (Key k : keys) {
                float dx = x < k.rect.left ? k.rect.left - x : (x > k.rect.right ? x - k.rect.right : 0);
                float dy = y < k.rect.top ? k.rect.top - y : (y > k.rect.bottom ? y - k.rect.bottom : 0);
                float d = dx * dx + dy * dy;
                if (d < bestDistance) { bestDistance = d; best = k; }
            }
            float limit = dp(10);
            return bestDistance <= limit * limit ? best : null;
        }

        // ---- suggestions, autocorrect, learning ----

        void updateSuggestions() {
            suggestions.clear();
            if (!password && !numeric && !symbols && pref("suggestions", true)) {
                InputConnection ic = connection();
                CharSequence before = ic == null ? null : ic.getTextBeforeCursor(80, 0);
                if (before != null) {
                    TextContext ctx = TextContext.parse(before);
                    List<SuggestionEngine.Suggestion> list =
                            SuggestionEngine.suggest(dictionary(), ctx.word, ctx.prev, ctx.sentenceStart, 3);
                    for (SuggestionEngine.Suggestion s : list) suggestions.add(s.text);
                }
            }
            invalidate();
        }

        /** Called just before a space, punctuation mark or Enter: expands shortcuts, autocorrects, then learns the word. */
        void finishWord(String boundary) {
            clearUndo();
            if (password || numeric) return;
            InputConnection ic = connection();
            if (ic == null) return;
            CharSequence selected = ic.getSelectedText(0);
            if (selected != null && selected.length() > 0) return;
            CharSequence before = ic.getTextBeforeCursor(80, 0);
            if (before == null || before.length() == 0) return;
            TextContext ctx = TextContext.parse(before);
            if (ctx.word.isEmpty()) return;
            Dictionary d = dictionary();
            String expansion = d.expansionFor(ctx.word);
            if (expansion != null) {
                ic.deleteSurroundingText(ctx.word.length(), 0);
                ic.commitText(expansion, 1);
                return;
            }
            String finalWord = ctx.word;
            if (pref("autocorrect", true)) {
                String fix = AutoCorrector.correct(d, ctx.word, ctx.prev, ctx.sentenceStart);
                if (fix != null) {
                    ic.deleteSurroundingText(ctx.word.length(), 0);
                    ic.commitText(fix, 1);
                    undoOriginal = ctx.word;
                    undoFixed = fix;
                    undoBoundary = boundary;
                    finalWord = fix;
                }
            }
            learn(finalWord, ctx.prev, ctx.sentenceStart);
        }

        private void applySuggestion(int index) {
            if (index >= suggestions.size() || password || numeric) return;
            InputConnection ic = connection();
            if (ic == null) return;
            CharSequence before = ic.getTextBeforeCursor(80, 0);
            if (before == null) return;
            TextContext ctx = TextContext.parse(before);
            String replacement = suggestions.get(index);
            clearUndo();
            if (!ctx.word.isEmpty()) ic.deleteSurroundingText(ctx.word.length(), 0);
            ic.commitText(replacement + " ", 1);
            learn(replacement, ctx.prev, ctx.sentenceStart);
            shifted = false;
            updateSuggestions();
        }

        // ---- glide typing ----

        private boolean glideAllowed() {
            return !password && !numeric && !symbols && !editTools && pref("glide", true);
        }

        private GlideDecoder.Layout glideLayout() {
            float width = 0;
            int n = 0;
            for (Key k : keys) {
                if (k.letter) { width += k.rect.width(); n++; }
            }
            GlideDecoder.Layout layout = new GlideDecoder.Layout(n == 0 ? dp(36) : width / n, keyHeight);
            for (Key k : keys) {
                if (k.letter) layout.put(Character.toLowerCase(k.label.codePointAt(0)), k.rect.centerX(), k.rect.centerY());
            }
            return layout;
        }

        private void finishGlide() {
            float[][] path = trail.toArray(new float[0][]);
            trail.clear();
            gliding = false;
            trackingGlide = false;
            List<String> words = GlideDecoder.decode(path, glideLayout(), dictionary(), 3);
            if (words.isEmpty()) {
                if (!glideHintShown && dictionary().wordCount() < 20) {
                    glideHintShown = true;
                    Toast.makeText(CrazeKeyboardService.this,
                            "Glide uses words you have typed. Type a few words first.", Toast.LENGTH_LONG).show();
                }
                invalidate();
                return;
            }
            InputConnection ic = connection();
            if (ic == null) return;
            CharSequence before = ic.getTextBeforeCursor(80, 0);
            TextContext ctx = TextContext.parse(before == null ? "" : before);
            if (!ctx.word.isEmpty()) {
                finishWord(" ");
                type(" ");
            }
            CharSequence after = ic.getTextBeforeCursor(80, 0);
            TextContext next = TextContext.parse(after == null ? "" : after);
            String word = words.get(0);
            if (shifted) word = WordChars.capitalizeFirst(word);
            clearUndo();
            type(word + " ");
            learn(word, next.prev, next.sentenceStart);
            shifted = false;
            updateSuggestions();
        }

        // ---- drawing ----

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            applyAppearance();
            c.drawColor(background);
            buildKeys();
            drawToolbar(c);
            drawSuggestions(c);
            for (Key key : keys) {
                String label = key.label;
                RectF keyRect = key.rect;
                p.setStyle(Paint.Style.FILL);
                p.setColor(special(label) ? actionColor : keyColor);
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
                c.drawText(shown, keyRect.centerX(), keyRect.centerY() - (p.ascent() + p.descent()) / 2, p);
            }
            if (gliding && trail.size() > 1) {
                trailPath.reset();
                trailPath.moveTo(trail.get(0)[0], trail.get(0)[1]);
                for (int i = 1; i < trail.size(); i++) trailPath.lineTo(trail.get(i)[0], trail.get(i)[1]);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(dp(4));
                p.setStrokeCap(Paint.Cap.ROUND);
                p.setStrokeJoin(Paint.Join.ROUND);
                p.setColor(Color.argb(200, 91, 78, 160));
                c.drawPath(trailPath, p);
                p.setStyle(Paint.Style.FILL);
            }
        }

        private void drawToolbar(Canvas c) {
            String[] items = editTools
                    ? new String[]{"Select all","Copy","Cut","Paste","Done"}
                    : new String[]{"clipboard","edit","emoji","dictionary","settings"};
            int[] iconIds = new int[]{R.drawable.ic_clipboard, R.drawable.ic_edit, R.drawable.ic_emoji,
                    R.drawable.ic_dictionary, R.drawable.ic_settings};
            float cell = getWidth() / (float) items.length;
            for (int i = 0; i < items.length; i++) {
                p.setColor(i == 0 ? actionColor : background);
                c.drawRoundRect(new RectF(i * cell + dp(3), dp(3), (i + 1) * cell - dp(3), toolbarHeight - dp(3)), dp(9), dp(9), p);
                if (!editTools) {
                    Drawable icon = getResources().getDrawable(iconIds[i], getContext().getTheme());
                    int size = dp(22);
                    int left = (int)(i * cell + (cell - size) / 2);
                    int top = (int)((toolbarHeight - size) / 2);
                    icon.setBounds(left, top, left + size, top + size);
                    icon.draw(c);
                } else {
                    p.setColor(i == 0 ? ink : muted);
                    p.setTextAlign(Paint.Align.CENTER);
                    p.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                    p.setTextSize(dp(11));
                    c.drawText(items[i], i * cell + cell / 2, toolbarHeight / 2 - (p.ascent() + p.descent()) / 2, p);
                }
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

        // ---- toolbar actions ----

        private void paste() {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip().getItemCount() > 0) {
                CharSequence value = cm.getPrimaryClip().getItemAt(0).coerceToText(CrazeKeyboardService.this);
                if (value != null) type(value.toString());
            }
        }

        private void toolbarAction(int index) {
            clearUndo();
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
                case 3: openScreen(DictionaryActivity.class); break;
                case 4: openScreen(MainActivity.class); break;
            }
            updateSuggestions();
        }

        private void openScreen(Class<?> screen) {
            Intent intent = new Intent(CrazeKeyboardService.this, screen);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }

        private void copyText(String value) {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Selected text", value));
        }

        // ---- touch ----

        @Override public boolean onTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: {
                    buildKeys();
                    downX = e.getX();
                    downY = e.getY();
                    gliding = false;
                    trail.clear();
                    trail.add(new float[]{downX, downY});
                    trackingGlide = false;
                    if (downY >= toolbarHeight + suggestionHeight && glideAllowed()) {
                        Key start = keyAt(downX, downY);
                        trackingGlide = start != null && start.letter;
                    }
                    return true;
                }
                case MotionEvent.ACTION_MOVE: {
                    if (!trackingGlide) return true;
                    if (trail.size() < 600) trail.add(new float[]{e.getX(), e.getY()});
                    if (!gliding) {
                        float dx = e.getX() - downX, dy = e.getY() - downY;
                        float limit = dp(28);
                        if (dx * dx + dy * dy > limit * limit) gliding = true;
                    }
                    if (gliding) invalidate();
                    return true;
                }
                case MotionEvent.ACTION_CANCEL:
                    trail.clear();
                    gliding = false;
                    trackingGlide = false;
                    invalidate();
                    return true;
                case MotionEvent.ACTION_UP: {
                    if (gliding) {
                        trail.add(new float[]{e.getX(), e.getY()});
                        finishGlide();
                        return true;
                    }
                    trail.clear();
                    trackingGlide = false;
                    handleTap(e.getX(), e.getY());
                    return true;
                }
                default:
                    return true;
            }
        }

        private void handleTap(float x, float y) {
            if (y < toolbarHeight) {
                int count = 5;
                int index = Math.min(count - 1, (int)(x / (getWidth() / (float)count)));
                toolbarAction(index);
                return;
            }
            if (y < toolbarHeight + suggestionHeight) {
                if (!password && !numeric && !symbols) {
                    int index = Math.min(2, (int)(x / (getWidth() / 3f)));
                    applySuggestion(index);
                }
                return;
            }
            Key key = keyAt(x, y);
            if (key == null) return;
            String hit = key.label;
            switch (hit) {
                case "⇧": clearUndo(); shifted = !shifted; invalidate(); break;
                case "⌫": backspace(); updateSuggestions(); break;
                case "↵": enter(); updateSuggestions(); break;
                case "space": finishWord(" "); type(" "); shifted = false; updateSuggestions(); break;
                case "?123": clearUndo(); symbols = true; updateSuggestions(); break;
                case "ABC": clearUndo(); symbols = false; updateSuggestions(); break;
                case "☺": clearUndo(); type("🙂"); shifted = false; updateSuggestions(); break;
                case ",": finishWord(","); type(","); shifted = false; updateSuggestions(); break;
                case ".": finishWord("."); type("."); shifted = true; updateSuggestions(); break;
                default: {
                    String out = shifted ? hit.toUpperCase(Locale.ROOT) : hit.toLowerCase(Locale.ROOT);
                    int cp = out.codePointAt(0);
                    if (WordChars.isWordChar(cp) || WordChars.isApostrophe(cp) || Character.isDigit(cp)) {
                        clearUndo();
                    } else {
                        finishWord(out);
                    }
                    type(out);
                    shifted = false;
                    updateSuggestions();
                }
            }
        }

        private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density + 0.5f); }
    }
}
