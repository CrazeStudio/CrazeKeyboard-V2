package studio.craze.keyboard.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Learned local dictionary for suggestions, autocorrect, next-word prediction and glide typing. */
public final class Dictionary {
    public static final int MAX_WORDS = 30000;
    public static final int MAX_BIGRAMS = 80000;
    public static final class Entry {
        public final String key, word;
        public final int count;
        public final long lastUsed;
        Entry(String key,String word,int count,long lastUsed){this.key=key;this.word=word;this.count=count;this.lastUsed=lastUsed;}
    }
    public interface Visitor { void visit(String key,int count); }
    private static final class Rec {
        int count,capCount,mixedCount;
        String mixedForm="";
        long lastUsed;
    }
    private final TreeMap<String,Rec> words=new TreeMap<String,Rec>();
    private final HashMap<String,HashMap<String,Integer>> next=new HashMap<String,HashMap<String,Integer>>();
    private final TreeMap<String,String> shortcuts=new TreeMap<String,String>();
    private int bigramTotal;
    private boolean dirty;

    public synchronized boolean learn(String raw,String prevRaw,long now,boolean sentenceStart){return learn(raw,prevRaw,now,sentenceStart,1);}
    public synchronized boolean learn(String raw,String prevRaw,long now,boolean sentenceStart,int weight) {
        if(weight<1||!WordChars.isLearnable(raw))return false;
        String key=WordChars.normalize(raw);Rec r=words.get(key);
        if(r==null){r=new Rec();words.put(key,r);}
        r.count+=weight;r.lastUsed=now;
        boolean inner=WordChars.hasInnerUpper(raw);
        if(inner){r.mixedCount+=weight;r.mixedForm=raw.replace('\u2019','\'');}
        else if(!sentenceStart&&WordChars.startsUpper(raw))r.capCount+=weight;
        if(prevRaw!=null&&!prevRaw.isEmpty()&&WordChars.isLearnable(prevRaw)) {
            String pk=WordChars.normalize(prevRaw);HashMap<String,Integer> m=next.get(pk);
            if(m==null){m=new HashMap<String,Integer>();next.put(pk,m);}
            Integer old=m.get(key);if(old==null){m.put(key,weight);bigramTotal++;}else m.put(key,old+weight);
        }
        dirty=true;if(words.size()>MAX_WORDS)pruneWords();if(bigramTotal>MAX_BIGRAMS)pruneBigrams();return true;
    }
    public synchronized int count(String word){Rec r=words.get(WordChars.normalize(word));return r==null?0:r.count;}
    public synchronized boolean contains(String word){return words.containsKey(WordChars.normalize(word));}
    public synchronized int wordCount(){return words.size();}
    public synchronized int bigramCount(){return bigramTotal;}
    public synchronized int bigramCount(String prev,String word) {
        HashMap<String,Integer> m=next.get(WordChars.normalize(prev));if(m==null)return 0;
        Integer c=m.get(WordChars.normalize(word));return c==null?0:c;
    }
    public synchronized String displayForm(String key) {
        String k=WordChars.normalize(key);Rec r=words.get(k);if(r==null)return k;
        if(r.mixedCount>0&&r.mixedCount*2>r.count&&!r.mixedForm.isEmpty())return r.mixedForm;
        if(r.count>=2&&r.capCount*5>=r.count*4)return WordChars.capitalizeFirst(k);
        return k;
    }
    public synchronized List<Entry> withPrefix(String prefix,int limit) {
        String p=WordChars.normalize(prefix);Map<String,Rec> sub=p.isEmpty()?words:words.subMap(p,p+'\uffff');
        List<Entry> out=new ArrayList<Entry>();
        for(Map.Entry<String,Rec> e:sub.entrySet())out.add(new Entry(e.getKey(),displayForm(e.getKey()),e.getValue().count,e.getValue().lastUsed));
        sortByCount(out);return limit>0&&out.size()>limit?new ArrayList<Entry>(out.subList(0,limit)):out;
    }
    public synchronized List<Entry> nextWords(String prev,int limit) {
        List<Entry> out=new ArrayList<Entry>();if(prev==null||prev.isEmpty())return out;
        HashMap<String,Integer> m=next.get(WordChars.normalize(prev));if(m==null)return out;
        for(Map.Entry<String,Integer> e:m.entrySet()){Rec r=words.get(e.getKey());out.add(new Entry(e.getKey(),displayForm(e.getKey()),e.getValue(),r==null?0:r.lastUsed));}
        sortByCount(out);return limit>0&&out.size()>limit?new ArrayList<Entry>(out.subList(0,limit)):out;
    }
    public synchronized List<Entry> top(int limit){return withPrefix("",limit);}
    public synchronized List<Entry> search(String query,int limit) {
        String q=WordChars.normalize(query.trim());if(q.isEmpty())return top(limit);
        List<Entry> starts=new ArrayList<Entry>(),inside=new ArrayList<Entry>();
        for(Map.Entry<String,Rec> e:words.entrySet()) {
            String k=e.getKey();Entry en=new Entry(k,displayForm(k),e.getValue().count,e.getValue().lastUsed);
            if(k.startsWith(q))starts.add(en);else if(k.contains(q))inside.add(en);
        }
        sortByCount(starts);sortByCount(inside);starts.addAll(inside);
        return limit>0&&starts.size()>limit?new ArrayList<Entry>(starts.subList(0,limit)):starts;
    }
    public synchronized void forEachWord(Visitor v){for(Map.Entry<String,Rec> e:words.entrySet())v.visit(e.getKey(),e.getValue().count);}
    public synchronized boolean forget(String word) {
        String k=WordChars.normalize(word);boolean had=words.remove(k)!=null;
        HashMap<String,Integer> own=next.remove(k);if(own!=null)bigramTotal-=own.size();
        List<String> empty=new ArrayList<String>();
        for(Map.Entry<String,HashMap<String,Integer>> e:next.entrySet()){if(e.getValue().remove(k)!=null)bigramTotal--;if(e.getValue().isEmpty())empty.add(e.getKey());}
        for(String s:empty)next.remove(s);if(bigramTotal<0)bigramTotal=0;
        if(had||own!=null)dirty=true;return had;
    }
    public synchronized void clearWords(){words.clear();next.clear();bigramTotal=0;dirty=true;}
    public synchronized boolean addShortcut(String trigger,String expansion) {
        if(trigger==null||expansion==null)return false;
        String t=WordChars.normalize(trigger.trim());
        if(t.isEmpty()||t.length()>24||expansion.isEmpty()||expansion.length()>500)return false;
        for(int i=0;i<t.length();i++)if(Character.isWhitespace(t.charAt(i)))return false;
        shortcuts.put(t,expansion);dirty=true;return true;
    }
    public synchronized boolean removeShortcut(String trigger){boolean had=shortcuts.remove(WordChars.normalize(trigger.trim()))!=null;if(had)dirty=true;return had;}
    public synchronized String expansionFor(String word){if(shortcuts.isEmpty()||word==null||word.isEmpty())return null;return shortcuts.get(WordChars.normalize(word));}
    public synchronized List<String[]> shortcutList(){List<String[]> out=new ArrayList<String[]>();for(Map.Entry<String,String> e:shortcuts.entrySet())out.add(new String[]{e.getKey(),e.getValue()});return out;}
    public synchronized boolean consumeDirty(){boolean d=dirty;dirty=false;return d;}
    public synchronized void markDirty(){dirty=true;}
    private void pruneWords() {
        List<Map.Entry<String,Rec>> all=new ArrayList<Map.Entry<String,Rec>>(words.entrySet());
        Collections.sort(all,new Comparator<Map.Entry<String,Rec>>(){@Override public int compare(Map.Entry<String,Rec>a,Map.Entry<String,Rec>b){
            if(a.getValue().count!=b.getValue().count)return a.getValue().count<b.getValue().count?-1:1;
            return a.getValue().lastUsed<b.getValue().lastUsed?-1:(a.getValue().lastUsed==b.getValue().lastUsed?0:1);
        }});
        int drop=Math.max(1,all.size()/10);for(int i=0;i<drop;i++)words.remove(all.get(i).getKey());
    }
    private void pruneBigrams() {
        List<String> empty=new ArrayList<String>();
        for(Map.Entry<String,HashMap<String,Integer>> e:next.entrySet()) {
            List<String> drop=new ArrayList<String>();for(Map.Entry<String,Integer>b:e.getValue().entrySet())if(b.getValue()<=1)drop.add(b.getKey());
            for(String s:drop){e.getValue().remove(s);bigramTotal--;}if(e.getValue().isEmpty())empty.add(e.getKey());
        }
        for(String s:empty)next.remove(s);
        if(bigramTotal>MAX_BIGRAMS)for(Map.Entry<String,HashMap<String,Integer>> e:next.entrySet()) {
            List<String> drop=new ArrayList<String>();
            for(Map.Entry<String,Integer>b:e.getValue().entrySet()){int half=b.getValue()/2;if(half<=0)drop.add(b.getKey());else b.setValue(half);}
            for(String s:drop){e.getValue().remove(s);bigramTotal--;}
        }
        if(bigramTotal<0)bigramTotal=0;
    }
    private static void sortByCount(List<Entry> list) {
        Collections.sort(list,new Comparator<Entry>(){@Override public int compare(Entry a,Entry b){
            if(a.count!=b.count)return a.count>b.count?-1:1;
            if(a.lastUsed!=b.lastUsed)return a.lastUsed>b.lastUsed?-1:1;
            return a.key.compareTo(b.key);
        }});
    }
    public synchronized void write(Writer w)throws IOException {
        w.write("CRAZEDICT\t1\n");
        for(Map.Entry<String,Rec>e:words.entrySet()){Rec r=e.getValue();w.write("W\t"+e.getKey()+"\t"+r.count+"\t"+r.capCount+"\t"+r.mixedCount+"\t"+r.lastUsed+"\t"+escape(r.mixedForm)+"\n");}
        for(Map.Entry<String,HashMap<String,Integer>>e:next.entrySet())for(Map.Entry<String,Integer>b:e.getValue().entrySet())w.write("B\t"+e.getKey()+"\t"+b.getKey()+"\t"+b.getValue()+"\n");
        for(Map.Entry<String,String>e:shortcuts.entrySet())w.write("S\t"+escape(e.getKey())+"\t"+escape(e.getValue())+"\n");
        w.flush();
    }
    public static Dictionary read(Reader reader)throws IOException {
        Dictionary d=new Dictionary();BufferedReader br=reader instanceof BufferedReader?(BufferedReader)reader:new BufferedReader(reader);
        String line;boolean first=true;
        while((line=br.readLine())!=null) {
            if(first){first=false;if(!line.startsWith("CRAZEDICT"))return d;continue;}
            String[] f=line.split("\t",-1);
            try {
                if(f[0].equals("W")&&f.length>=7){Rec r=new Rec();r.count=Integer.parseInt(f[2]);r.capCount=Integer.parseInt(f[3]);r.mixedCount=Integer.parseInt(f[4]);r.lastUsed=Long.parseLong(f[5]);r.mixedForm=unescape(f[6]);if(r.count>0&&!f[1].isEmpty())d.words.put(f[1],r);}
                else if(f[0].equals("B")&&f.length>=4){int c=Integer.parseInt(f[3]);if(c>0&&!f[1].isEmpty()&&!f[2].isEmpty()){HashMap<String,Integer>m=d.next.get(f[1]);if(m==null){m=new HashMap<String,Integer>();d.next.put(f[1],m);}if(m.put(f[2],c)==null)d.bigramTotal++;}}
                else if(f[0].equals("S")&&f.length>=3){String t=unescape(f[1]),x=unescape(f[2]);if(!t.isEmpty()&&!x.isEmpty())d.shortcuts.put(t,x);}
            } catch(NumberFormatException ignored) { }
        }
        d.dirty=false;return d;
    }
    static String escape(String s) {
        StringBuilder b=new StringBuilder();
        for(int i=0;i<s.length();i++){char c=s.charAt(i);if(c=='\\')b.append("\\\\");else if(c=='\t')b.append("\\t");else if(c=='\n')b.append("\\n");else if(c=='\r')b.append("\\r");else b.append(c);}
        return b.toString();
    }
    static String unescape(String s) {
        if(s.indexOf('\\')<0)return s;StringBuilder b=new StringBuilder();
        for(int i=0;i<s.length();i++){char c=s.charAt(i);if(c=='\\'&&i+1<s.length()){char n=s.charAt(++i);if(n=='t')b.append('\t');else if(n=='n')b.append('\n');else if(n=='r')b.append('\r');else b.append(n);}else b.append(c);}
        return b.toString();
    }
}