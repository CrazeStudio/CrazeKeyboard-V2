package studio.craze.keyboard;

import android.inputmethodservice.InputMethodService;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.MotionEvent;

public class CrazeKeyboardService extends InputMethodService {
    private KeyboardView keyboard;

    @Override public View onCreateInputView() {
        keyboard = new KeyboardView();
        return keyboard;
    }

    @Override public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        if (keyboard != null) keyboard.setPasswordField(info != null &&
                (info.inputType & 0x0f) == 0x81 || info != null &&
                (info.inputType & 0x0f) == 0xE1 || info != null &&
                (info.inputType & 0x0f) == 0x12);
    }

    private void type(String value) {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) ic.commitText(value, 1);
    }
    private void backspace() {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            if (!ic.deleteSurroundingText(1, 0)) ic.sendKeyEvent(
                new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_DEL));
        }
    }
    private void enter() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        EditorInfo info = getCurrentInputEditorInfo();
        int action = info == null ? EditorInfo.IME_ACTION_NONE : info.imeOptions & EditorInfo.IME_MASK_ACTION;
        if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(action);
        } else {
            ic.commitText("\n", 1);
        }
    }

    private final class KeyboardView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int bg = Color.rgb(235, 233, 240);
        private final int key = Color.WHITE;
        private final int special = Color.rgb(211, 204, 226);
        private final int ink = Color.rgb(42, 39, 49);
        private boolean shifted = true;
        private boolean symbols = false;
        private boolean password = false;
        private float keyHeight;
        private String[][] rows;

        KeyboardView() { super(CrazeKeyboardService.this); setLayerType(View.LAYER_TYPE_SOFTWARE, null); }
        void setPasswordField(boolean value) { password = value; invalidate(); }
        private void updateRows() {
            if (symbols) rows = new String[][] {
                {"1","2","3","4","5","6","7","8","9","0"},
                {"@","#","$","%","&","-","+","(",")","/"},
                {"=<>","*","\"","' ",":",";","!","?","⌫"},
                {"ABC",",","?123","space",".","↵"}
            };
            else rows = new String[][] {
                shifted ? new String[]{"Q","W","E","R","T","Y","U","I","O","P"} : new String[]{"q","w","e","r","t","y","u","i","o","p"},
                shifted ? new String[]{"A","S","D","F","G","H","J","K","L"} : new String[]{"a","s","d","f","g","h","j","k","l"},
                {"⇧", shifted?"Z":"z",shifted?"X":"x",shifted?"C":"c",shifted?"V":"v",shifted?"B":"b",shifted?"N":"n",shifted?"M":"m","⌫"},
                {"?123",",","☺","space",".","↵"}
            };
        }
        @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int desiredHeight = dp(270);
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = resolveSize(desiredHeight, heightMeasureSpec);
            setMeasuredDimension(width, height);
        }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c); c.drawColor(bg); updateRows();
            float gap = dp(5), top = dp(8), side = dp(4);
            keyHeight = Math.max(dp(38), (getHeight() - top - dp(8) - gap * (rows.length-1)) / rows.length);
            for (int r=0; r<rows.length; r++) {
                String[] row = rows[r]; float totalWeight = 0;
                for (String s : row) totalWeight += weight(s);
                float available = getWidth() - side*2 - gap*(row.length-1);
                float x = side;
                for (String label : row) {
                    float w = available * weight(label) / totalWeight;
                    boolean action = isSpecial(label);
                    paint.setColor(action ? special : key);
                    c.drawRoundRect(new RectF(x, top+r*(keyHeight+gap), x+w, top+r*(keyHeight+gap)+keyHeight),
                        dp(7), dp(7), paint);
                    paint.setColor(ink); paint.setTextAlign(Paint.Align.CENTER);
                    paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
                    paint.setTextSize(label.equals("space") ? dp(12) : dp(18));
                    String shown = label.equals("space") ? "English (US)" : label.equals("?123") ? "?123" : label;
                    c.drawText(shown, x+w/2, top+r*(keyHeight+gap)+keyHeight/2 - (paint.ascent()+paint.descent())/2, paint);
                    x += w+gap;
                }
            }
        }
        private float weight(String s) {
            if (s.equals("space")) return 4.4f;
            if (s.equals("?123") || s.equals("ABC")) return 1.5f;
            if (s.equals("⇧") || s.equals("⌫") || s.equals("↵")) return 1.35f;
            if (s.equals("☺")) return 1.2f;
            return 1f;
        }
        private boolean isSpecial(String s) {
            return s.equals("space") || s.equals("?123") || s.equals("ABC") || s.equals("⇧") ||
                s.equals("⌫") || s.equals("↵") || s.equals("☺") || s.equals("=<>");
        }
        @Override public boolean onTouchEvent(MotionEvent e) {
            if (e.getAction() != MotionEvent.ACTION_UP) return true;
            float gap=dp(5), top=dp(8), side=dp(4);
            int rowIndex=(int)((e.getY()-top)/(keyHeight+gap));
            if (rowIndex<0 || rowIndex>=rows.length) return true;
            String[] row=rows[rowIndex]; float total=0;
            for(String s:row) total+=weight(s);
            float available=getWidth()-side*2-gap*(row.length-1), x=side;
            String hit=null;
            for(String s:row) {
                float w=available*weight(s)/total;
                if(e.getX()>=x && e.getX()<=x+w) { hit=s; break; }
                x+=w+gap;
            }
            if(hit==null) return true;
            switch(hit) {
                case "⇧": shifted=!shifted; invalidate(); break;
                case "⌫": backspace(); break;
                case "↵": enter(); break;
                case "space": type(" "); if(!password) shifted=false; invalidate(); break;
                case "?123": symbols=true; invalidate(); break;
                case "ABC": symbols=false; invalidate(); break;
                case "☺": type("🙂"); shifted=false; invalidate(); break;
                case "=<>": symbols=false; invalidate(); break;
                case ",": type(","); shifted=false; invalidate(); break;
                case ".": type("."); shifted=true; invalidate(); break;
                default: type(shifted ? hit.toUpperCase(java.util.Locale.ROOT) : hit.toLowerCase(java.util.Locale.ROOT)); shifted=false; invalidate();
            }
            return true;
        }
        private int dp(int n) { return (int)(n*getResources().getDisplayMetrics().density+0.5f); }
    }
}
