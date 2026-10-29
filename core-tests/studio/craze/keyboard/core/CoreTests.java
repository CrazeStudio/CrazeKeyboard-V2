package studio.craze.keyboard.core;

import java.io.StringReader;
import java.io.StringWriter;

public final class CoreTests {
    private static int passed;
    private static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);passed++;}
    public static void main(String[] args)throws Exception{
        check(WordChars.isWordChar('a'),"latin letter");
        check(WordChars.isWordChar(0x09BE),"bengali vowel mark");
        check(WordChars.normalize("HELLO").equals("hello"),"normalization");
        check(WordChars.isLearnable("keyboard"),"learnable word");
        check(!WordChars.isLearnable("1234"),"reject digits");
        check(EditDistance.distance("cat","cta",1)==1,"transposition");
        check(EditDistance.distance("cat","dog",1)==2,"bounded distance");
        Dictionary d=new Dictionary();
        check(d.learn("hello","",1000,true),"learn hello");
        check(d.learn("help","hello",2000,false),"learn help");
        check(d.contains("HELLO"),"case-insensitive lookup");
        check(d.bigramCount("hello","help")==1,"bigram count");
        check(d.withPrefix("he",10).size()==2,"prefix suggestions");
        check(SuggestionEngine.suggest(d,"hel","",false,3).size()>=2,"suggestions");
        check(d.addShortcut("omw","On my way!"),"add shortcut");
        check("On my way!".equals(d.expansionFor("OMW")),"shortcut lookup");
        StringWriter out=new StringWriter();d.write(out);
        Dictionary copy=Dictionary.read(new StringReader(out.toString()));
        check(copy.contains("hello"),"dictionary persistence");
        check(copy.bigramCount("hello","help")==1,"bigram persistence");
        check("On my way!".equals(copy.expansionFor("omw")),"shortcut persistence");
        check(copy.forget("help"),"forget word");
        check(!copy.contains("help"),"forgotten word absent");
        check(copy.clearAndCountForTest()==0,"placeholder");
        System.out.println(passed+" passed, 0 failed");
    }
}