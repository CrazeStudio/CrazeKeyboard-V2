package studio.craze.keyboard.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class SuggestionEngine {
    private SuggestionEngine() {}
    public enum Kind { TYPED, COMPLETION, CORRECTION, NEXT_WORD }
    public static final class Suggestion {
        public final String text;
        public final Kind kind;
        Suggestion(String text, Kind kind) { this.text=text; this.kind=kind; }
    }
    static final class Scored {
        final String key, display;
        final long score;
        final int distance;
        Scored(String key,String display,long score,int distance) {
            this.key=key;this.display=display;this.score=score;this.distance=distance;
        }
    }
    public static List<Suggestion> suggest(Dictionary d,String typed,String prev,boolean sentenceStart,int max) {
        List<Suggestion> out=new ArrayList<Suggestion>();
        if(max<=0)return out;
        if(typed==null)typed="";
        if(typed.isEmpty())return nextWord(d,prev,sentenceStart,max);
        out.add(new Suggestion(typed,Kind.TYPED));
        String key=WordChars.normalize(typed);
        Set<String> seen=new HashSet<String>();seen.add(key);
        List<Dictionary.Entry> pre=d.withPrefix(key,40);
        List<Scored> completions=new ArrayList<Scored>();
        for(Dictionary.Entry e:pre) {
            if(e.key.equals(key))continue;
            long score=e.count+3L*d.bigramCount(prev==null?"":prev,e.key);
            completions.add(new Scored(e.key,e.word,score,0));
        }
        sortScored(completions);
        for(Scored s:completions) {
            if(out.size()>=max)break;
            if(seen.add(s.key))out.add(new Suggestion(WordChars.matchCase(typed,s.display),Kind.COMPLETION));
        }
        if(out.size()<max)for(Scored s:corrections(d,key,prev,3)) {
            if(out.size()>=max)break;
            if(seen.add(s.key))out.add(new Suggestion(WordChars.matchCase(typed,s.display),Kind.CORRECTION));
        }
        return out;
    }
    private static List<Suggestion> nextWord(Dictionary d,String prev,boolean sentenceStart,int max) {
        List<Suggestion> out=new ArrayList<Suggestion>();Set<String> seen=new HashSet<String>();
        for(Dictionary.Entry e:d.nextWords(prev,max))if(seen.add(e.key))out.add(new Suggestion(cap(e.word,sentenceStart),Kind.NEXT_WORD));
        if(out.isEmpty()&&(prev==null||prev.isEmpty()))for(Dictionary.Entry e:d.top(max)) {
            if(e.count<2)break;
            if(seen.add(e.key))out.add(new Suggestion(cap(e.word,sentenceStart),Kind.NEXT_WORD));
        }
        return out;
    }
    private static String cap(String w,boolean sentenceStart){return sentenceStart?WordChars.capitalizeFirst(w):w;}
    static List<Scored> corrections(Dictionary d,final String key,final String prev,int maxKeep) {
        final int len=key.length(),maxDist=len<=4?1:2;
        final List<Scored> found=new ArrayList<Scored>();
        final char first=key.isEmpty()?0:key.charAt(0);
        final String prevKey=prev==null?"":prev;
        final Dictionary dict=d;
        d.forEachWord(new Dictionary.Visitor(){@Override public void visit(String k,int count){
            if(k.equals(key))return;
            int l=k.length();if(Math.abs(l-len)>maxDist)return;
            if(len>3&&k.charAt(0)!=first)return;
            int dist=EditDistance.distance(key,k,maxDist);if(dist>maxDist)return;
            long score=count+3L*dict.bigramCount(prevKey,k);
            found.add(new Scored(k,dict.displayForm(k),score,dist));
        }});
        Collections.sort(found,new Comparator<Scored>(){@Override public int compare(Scored a,Scored b){
            if(a.distance!=b.distance)return a.distance<b.distance?-1:1;
            if(a.score!=b.score)return a.score>b.score?-1:1;
            return a.key.compareTo(b.key);
        }});
        return found.size()>maxKeep?new ArrayList<Scored>(found.subList(0,maxKeep)):found;
    }
    private static void sortScored(List<Scored> list) {
        Collections.sort(list,new Comparator<Scored>(){@Override public int compare(Scored a,Scored b){
            if(a.score!=b.score)return a.score>b.score?-1:1;
            return a.key.compareTo(b.key);
        }});
    }
}