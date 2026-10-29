package studio.craze.keyboard.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

public final class GlideDecoder {
    private GlideDecoder() {}
    public static final class Layout {
        final HashMap<Integer,float[]> centers=new HashMap<Integer,float[]>();
        public final float keyWidth,keyHeight;
        public Layout(float keyWidth,float keyHeight){this.keyWidth=keyWidth;this.keyHeight=keyHeight;}
        public void put(int codePoint,float cx,float cy){centers.put(codePoint,new float[]{cx,cy});}
        public int size(){return centers.size();}
    }
    private static final int SAMPLES=24;
    private static final float MAX_COST=1.0f;
    private static final class Candidate {
        final String key;final double score;
        Candidate(String key,double score){this.key=key;this.score=score;}
    }
    public static List<String> decode(final float[][] path,final Layout layout,final Dictionary dict,int max) {
        List<String> result=new ArrayList<String>();
        if(path==null||path.length<2||layout==null||layout.centers.isEmpty()||max<=0)return result;
        final double pathLen=length(path);
        if(pathLen<layout.keyWidth*1.2)return result;
        final float[][] sample=resample(path,SAMPLES);
        final float[] start=path[0],end=path[path.length-1];
        final float reach=layout.keyWidth*1.6f;
        final List<Candidate> found=new ArrayList<Candidate>();
        dict.forEachWord(new Dictionary.Visitor(){@Override public void visit(String key,int count){
            int len=key.length();if(len<2||len>24)return;
            int first=key.codePointAt(0),last=key.codePointBefore(len);
            float[] fc=layout.centers.get(first),lc=layout.centers.get(last);
            if(fc==null||lc==null)return;
            float dStart=dist(fc,start),dEnd=dist(lc,end);if(dStart>reach||dEnd>reach)return;
            List<float[]> ideal=new ArrayList<float[]>();
            for(int i=0;i<len;){int cp=key.codePointAt(i);i+=Character.charCount(cp);float[] c=layout.centers.get(cp);if(c==null)return;if(ideal.isEmpty()||ideal.get(ideal.size()-1)!=c)ideal.add(c);}
            if(ideal.size()<2)return;
            float[][] idealPath=ideal.toArray(new float[0][]);double idealLen=length(idealPath);if(idealLen<=0)return;
            float[][] ideal24=resample(idealPath,SAMPLES);double sum=0;
            for(int i=0;i<SAMPLES;i++)sum+=dist(sample[i],ideal24[i]);
            double cost=sum/SAMPLES/layout.keyWidth+0.5*(dStart+dEnd)/layout.keyWidth;if(cost>MAX_COST)return;
            double lenPenalty=Math.abs(Math.log(pathLen/idealLen));
            found.add(new Candidate(key,cost+0.6*lenPenalty-0.08*Math.log(1+count)));
        }});
        Collections.sort(found,new Comparator<Candidate>(){@Override public int compare(Candidate a,Candidate b){
            if(a.score!=b.score)return a.score<b.score?-1:1;return a.key.compareTo(b.key);
        }});
        for(int i=0;i<found.size()&&result.size()<max;i++)result.add(dict.displayForm(found.get(i).key));
        return result;
    }
    static float dist(float[] a,float[] b){float dx=a[0]-b[0],dy=a[1]-b[1];return (float)Math.sqrt(dx*dx+dy*dy);}
    static double length(float[][] p){double s=0;for(int i=1;i<p.length;i++)s+=dist(p[i-1],p[i]);return s;}
    static float[][] resample(float[][] p,int n) {
        float[][] out=new float[n][];double total=length(p);
        if(total<=0){for(int i=0;i<n;i++)out[i]=new float[]{p[0][0],p[0][1]};return out;}
        double step=total/(n-1);out[0]=new float[]{p[0][0],p[0][1]};
        int seg=1;double segStart=0,segLen=dist(p[0],p[1]);
        for(int i=1;i<n;i++) {
            double target=step*i;
            while(seg<p.length-1&&segStart+segLen<target){segStart+=segLen;seg++;segLen=dist(p[seg-1],p[seg]);}
            double t=segLen<=0?0:Math.min(1.0,(target-segStart)/segLen);
            float x=(float)(p[seg-1][0]+(p[seg][0]-p[seg-1][0])*t);
            float y=(float)(p[seg-1][1]+(p[seg][1]-p[seg-1][1])*t);
            out[i]=new float[]{x,y};
        }
        return out;
    }
}