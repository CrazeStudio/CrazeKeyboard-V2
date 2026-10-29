package studio.craze.keyboard;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.inputmethodservice.InputMethodService;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import studio.craze.keyboard.core.Dictionary;
import studio.craze.keyboard.core.SuggestionEngine;
import studio.craze.keyboard.core.TextContext;
import studio.craze.keyboard.core.WordChars;

public class CrazeKeyboardService extends InputMethodService {
    private KeyboardView keyboard;
    private InputConnection ic(){return getCurrentInputConnection();}
    @Override public View onCreateInputView(){keyboard=new KeyboardView();return keyboard;}
    @Override public void onStartInputView(EditorInfo info,boolean restarting){
        super.onStartInputView(info,restarting);
        if(keyboard!=null){keyboard.password=info!=null&&(info.inputType&0x80)==0x80;keyboard.refresh();}
    }
    @Override public void onFinishInput(){super.onFinishInput();DictionaryHolder.saveAsync(this);}
    private void type(String s){InputConnection c=ic();if(c!=null)c.commitText(s,1);}
    private void delete(){InputConnection c=ic();if(c!=null)c.deleteSurroundingText(1,0);}
    private void finishWord(String boundary){
        InputConnection c=ic();if(c==null)return;
        CharSequence before=c.getTextBeforeCursor(80,0);TextContext ctx=TextContext.parse(before);
        String word=ctx.word;
        if(!word.isEmpty()&&!keyboard.password&&getSharedPreferences("crazekeyboard",0).getBoolean("learn",true)){
            DictionaryHolder.get(this).learn(word,ctx.prev,System.currentTimeMillis(),ctx.sentenceStart);
            DictionaryHolder.saveAsync(this);
        }
        type(boundary);
    }
    private final class KeyboardView extends View {
        final Paint p=new Paint(3);
        final String[][] alpha={{"q","w","e","r","t","y","u","i","o","p"},{"a","s","d","f","g","h","j","k","l"},{"⇧","z","x","c","v","b","n","m","⌫"},{"?123",",","space",".","↵"}};
        final String[][] nums={{"1","2","3","4","5","6","7","8","9","0"},{"@","#","$","%","&","-","+","(",")","/"},{"ABC","*","\"","'",";",":","!","?","⌫"},{"ABC",",","space",".","↵"}};
        boolean password=false,shift=true,symbols=false,edit=false;
        float toolbar=42,sugg=38,keyH=48;
        List<String> suggestions=new ArrayList<String>();
        KeyboardView(){super(CrazeKeyboardService.this);setFocusable(true);refresh();}
        int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
        void refresh(){
            suggestions.clear();
            if(password){suggestions.add("");suggestions.add("");suggestions.add("");invalidate();return;}
            InputConnection c=ic();CharSequence before=c==null?"":c.getTextBeforeCursor(80,0);
            TextContext ctx=TextContext.parse(before);
            List<SuggestionEngine.Suggestion> list=SuggestionEngine.suggest(DictionaryHolder.get(CrazeKeyboardService.this),ctx.word,ctx.prev,ctx.sentenceStart,3);
            for(SuggestionEngine.Suggestion s:list)suggestions.add(s.text);
            invalidate();
        }
        @Override protected void onMeasure(int w,int h){setMeasuredDimension(MeasureSpec.getSize(w),dp(280));}
        @Override protected void onDraw(Canvas c){
            super.onDraw(c);c.drawColor(Color.rgb(245,245,249));
            p.setTextAlign(Paint.Align.CENTER);p.setTextSize(dp(14));p.setColor(Color.rgb(65,60,78));
            String[] tools=edit?new String[]{"Select all","Copy","Cut","Paste","Done"}:new String[]{"▣","✎","☺","Dict","⚙"};
            for(int i=0;i<5;i++){float cw=getWidth()/5f;p.setColor(i==0?Color.rgb(225,220,239):Color.WHITE);c.drawRoundRect(new RectF(i*cw+dp(3),dp(3),(i+1)*cw-dp(3),toolbar-dp(3)),dp(8),dp(8),p);p.setColor(Color.rgb(65,60,78));c.drawText(tools[i],i*cw+cw/2,toolbar/2+dp(5),p);}
            float sw=getWidth()/3f;p.setColor(Color.rgb(104,103,115));
            for(int i=0;i<3;i++){String s=i<suggestions.size()?suggestions.get(i):"";c.drawText(s,sw*i+sw/2,toolbar+sugg/2+dp(5),p);if(i>0)c.drawLine(sw*i,toolbar+dp(5),sw*i,toolbar+sugg-dp(5),p);}
            String[][] rows=symbols?nums:alpha;
            float top=toolbar+sugg, rowH=(getHeight()-top)/rows.length;
            for(int r=0;r<rows.length;r++){
                String[] row=rows[r];float unit=getWidth()/(float)row.length;
                if(r==1&&row.length==9)unit=getWidth()/9f;
                for(int k=0;k<row.length;k++){
                    float left=k*unit+dp(2),right=(k+1)*unit-dp(2);
                    p.setColor(isAction(row[k])?Color.rgb(225,220,239):Color.WHITE);
                    c.drawRoundRect(new RectF(left,top+r*rowH+dp(2),right,top+(r+1)*rowH-dp(2)),dp(6),dp(6),p);
                    p.setColor(Color.rgb(33,33,39));p.setTextSize(dp(row[k].equals("space")?11:17));
                    String label=row[k].equals("space")?"English (US)":row[k];
                    if(shift&&!symbols&&label.length()==1)label=label.toUpperCase(Locale.ROOT);
                    c.drawText(label,(left+right)/2,top+(r+.5f)*rowH-dp(2),p);
                }
            }
        }
        boolean isAction(String s){return s.equals("⇧")||s.equals("⌫")||s.equals("↵")||s.equals("space")||s.equals("?123")||s.equals("ABC");}
        @Override public boolean onTouchEvent(MotionEvent e){
            if(e.getAction()!=MotionEvent.ACTION_UP)return true;
            float x=e.getX(),y=e.getY();
            if(y<toolbar){int i=Math.min(4,(int)(x/(getWidth()/5f)));tool(i);return true;}
            if(y<toolbar+sugg){if(!password){int i=Math.min(2,(int)(x/(getWidth()/3f)));if(i<suggestions.size()&&!suggestions.get(i).isEmpty()){type(suggestions.get(i));refresh();}}return true;}
            String[][] rows=symbols?nums:alpha;float top=toolbar+sugg;float rowH=(getHeight()-top)/rows.length;
            int r=Math.min(rows.length-1,(int)((y-top)/rowH));if(r<0)return true;
            String[] row=rows[r];float unit=getWidth()/(float)row.length;int k=Math.min(row.length-1,(int)(x/unit));String hit=row[k];
            switch(hit){
                case "⇧":shift=!shift;invalidate();break;
                case "⌫":delete();refresh();break;
                case "↵":InputConnection c=ic();if(c!=null){EditorInfo info=getCurrentInputEditorInfo();int a=info==null?0:info.imeOptions&EditorInfo.IME_MASK_ACTION;if(a!=0&&a!=EditorInfo.IME_ACTION_UNSPECIFIED)c.performEditorAction(a);else type("\n");}refresh();break;
                case "space":finishWord(" ");refresh();break;
                case "?123":symbols=true;invalidate();break;
                case "ABC":symbols=false;invalidate();break;
                case ",":finishWord(",");refresh();break;
                case ".":finishWord(".");shift=true;refresh();break;
                default: type(shift&&!symbols?hit.toUpperCase(Locale.ROOT):hit);shift=false;refresh();
            }
            return true;
        }
        void tool(int i){
            InputConnection c=ic();if(c==null)return;
            if(edit){
                if(i==0)c.performContextMenuAction(android.R.id.selectAll);
                else if(i==1||i==2){CharSequence s=c.getSelectedText(0);if(s!=null){ClipboardManager cm=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);if(cm!=null)cm.setPrimaryClip(ClipData.newPlainText("text",s));if(i==2)c.commitText("",1);}}
                else if(i==3)paste();else edit=false;
                invalidate();return;
            }
            if(i==0)paste();else if(i==1)edit=true;else if(i==2)type("🙂");else if(i==3)open(DictionaryActivity.class);else open(MainActivity.class);
            invalidate();
        }
        void paste(){ClipboardManager cm=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);if(cm!=null&&cm.hasPrimaryClip()&&cm.getPrimaryClip().getItemCount()>0){CharSequence s=cm.getPrimaryClip().getItemAt(0).coerceToText(CrazeKeyboardService.this);if(s!=null)type(s.toString());}}
        void open(Class<?> cls){Intent in=new Intent(CrazeKeyboardService.this,cls);in.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(in);}
    }
}