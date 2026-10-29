package studio.craze.keyboard.core;

public final class TextContext {
    public final String word;
    public final String prev;
    public final boolean sentenceStart;
    public final int wordStart;
    private TextContext(String word,String prev,boolean sentenceStart,int wordStart) {
        this.word=word;this.prev=prev;this.sentenceStart=sentenceStart;this.wordStart=wordStart;
    }
    public static TextContext parse(CharSequence before) {
        if(before==null)before="";
        int end=before.length(),ws=scanWordBack(before,end);
        String word=before.subSequence(ws,end).toString();
        int i=ws,spaces=0;boolean newline=false;
        while(i>0) {
            char c=before.charAt(i-1);
            if(c=='\n'||c=='\r'){newline=true;i--;spaces++;}
            else if(Character.isWhitespace(c)){i--;spaces++;}
            else break;
        }
        String prev="";
        if(spaces>0&&!newline&&i>0) {
            int cp=Character.codePointBefore(before,i);
            if(WordChars.isWordChar(cp)){int ps=scanWordBack(before,i);prev=before.subSequence(ps,i).toString();}
        }
        boolean sentenceStart;
        if(i==0||newline)sentenceStart=true;
        else {
            char last=before.charAt(i-1);
            sentenceStart=last=='.'||last=='!'||last=='?'||last=='\u0964'||last=='\u2026';
            if(spaces==0&&!word.isEmpty())sentenceStart=sentenceStart&&false;
        }
        return new TextContext(word,prev,sentenceStart,ws);
    }
    private static int scanWordBack(CharSequence s,int end) {
        int i=end;
        while(i>0) {
            int cp=Character.codePointBefore(s,i);
            if(WordChars.isWordChar(cp)){i-=Character.charCount(cp);continue;}
            if(WordChars.isApostrophe(cp)&&i<end&&i-1>0) {
                int before=Character.codePointBefore(s,i-1);
                if(WordChars.isWordChar(before)){i--;continue;}
            }
            break;
        }
        return i;
    }
}