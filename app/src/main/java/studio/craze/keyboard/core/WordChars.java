package studio.craze.keyboard.core;

import java.text.Normalizer;
import java.util.Locale;

public final class WordChars {
    private WordChars() {}
    public static boolean isWordChar(int cp) {
        if (Character.isLetter(cp)) return true;
        int type = Character.getType(cp);
        if (type == Character.NON_SPACING_MARK || type == Character.COMBINING_SPACING_MARK) return true;
        return cp == 0x200C || cp == 0x200D;
    }
    public static boolean isApostrophe(int cp) { return cp == '\'' || cp == 0x2019; }
    public static boolean hasLetter(String s) {
        for (int i=0;i<s.length();) { int cp=s.codePointAt(i); if(Character.isLetter(cp)) return true; i+=Character.charCount(cp); }
        return false;
    }
    public static String normalize(String word) {
        String w=word.replace('\u2019','\'');
        w=Normalizer.normalize(w,Normalizer.Form.NFC);
        return w.toLowerCase(Locale.ROOT);
    }
    public static int codePointCount(String s) { return s.codePointCount(0,s.length()); }
    public static boolean isLearnable(String word) {
        if(word==null) return false;
        int n=codePointCount(word);
        if(n<1||n>32||!hasLetter(word)) return false;
        int run=1,prev=-1;
        for(int i=0;i<word.length();) {
            int cp=word.codePointAt(i); i+=Character.charCount(cp);
            if(Character.isDigit(cp)||(!isWordChar(cp)&&!isApostrophe(cp))) return false;
            run=(cp==prev)?run+1:1; if(run>=4) return false; prev=cp;
        }
        if(n==1) {
            int cp=word.codePointAt(0);
            if(cp<0x250) { int lower=Character.toLowerCase(cp); return lower=='a'||lower=='i'; }
        }
        return true;
    }
    public static boolean startsUpper(String s) { return !s.isEmpty()&&Character.isUpperCase(s.codePointAt(0)); }
    public static boolean hasInnerUpper(String s) {
        boolean first=true;
        for(int i=0;i<s.length();) { int cp=s.codePointAt(i); i+=Character.charCount(cp); if(!first&&Character.isUpperCase(cp)) return true; first=false; }
        return false;
    }
    public static boolean isAllUpper(String s) {
        if(codePointCount(s)<2) return false;
        boolean any=false;
        for(int i=0;i<s.length();) { int cp=s.codePointAt(i); i+=Character.charCount(cp); if(Character.isLetter(cp)){if(!Character.isUpperCase(cp))return false;any=true;} }
        return any;
    }
    public static String capitalizeFirst(String s) {
        if(s.isEmpty())return s;
        int cp=s.codePointAt(0),len=Character.charCount(cp);
        return new String(Character.toChars(Character.toUpperCase(cp)))+s.substring(len);
    }
    public static String matchCase(String typed,String candidate) {
        if(isAllUpper(typed))return candidate.toUpperCase(Locale.ROOT);
        if(startsUpper(typed)&&!hasInnerUpper(candidate))return capitalizeFirst(candidate);
        return candidate;
    }
}