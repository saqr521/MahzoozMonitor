package com.mahzooz.monitor;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.Bitmap;
import java.util.*;

public class Analyzer {
    private final TemplateRepository templates;
    private final HistoryDb db;
    private long lastFrameHash = 0;
    private String lastGreenSignature = "";

    public Analyzer(Context c){ templates=new TemplateRepository(c); db=new HistoryDb(c); }

    public synchronized AnalysisResult analyze(Bitmap bmp){
        List<Detector.Marker> g=Detector.findMarkers(bmp,true);
        List<Detector.Marker> y=Detector.findMarkers(bmp,false);
        sortMarkers(g); sortMarkers(y);

        // Always run tile detection. Markers are badges; the tile below each badge
        // is the actual item name used for the learned relation map.
        List<Detector.Tile> tiles=Detector.findTiles(bmp,templates);
        List<Detector.Tile> greenItems=attachMarkersToTiles(g,tiles,bmp,templates);
        List<Detector.Tile> yellowItems=attachMarkersToTiles(y,tiles,bmp,templates);

        // Fall back to direct crops if a badge could not be associated with a grid tile.
        for(int i=0;i<greenItems.size();i++) if(greenItems.get(i).label.equals("غير معروف"))
            greenItems.set(i,Detector.itemForMarker(bmp,g.get(i),templates));
        for(int i=0;i<yellowItems.size();i++) if(yellowItems.get(i).label.equals("غير معروف"))
            yellowItems.set(i,Detector.itemForMarker(bmp,y.get(i),templates));

        List<String> relations=buildRelations(greenItems,yellowItems);
        String current=signature(greenItems);
        String recommendation=updateAndRecommend(lastGreenSignature,current);
        AlgorithmTrace.recordGameToMonitor(g, y, greenItems, yellowItems, relations);
        if(!recommendation.isEmpty()) AlgorithmTrace.recordMonitorOutput(recommendation);
        lastGreenSignature=current;

        long screenHash=Detector.phash(bmp,new Rect(0,0,bmp.getWidth(),bmp.getHeight()));
        String change=lastFrameHash==0?"بدء الرصد":(Long.bitCount(screenHash^lastFrameHash)>10?"تغير ملحوظ في الشاشة":"");
        lastFrameHash=screenHash;

        float confidence=.35f;
        if(!g.isEmpty()||!y.isEmpty()) confidence+=.20f;
        if(!tiles.isEmpty()) confidence+=.15f;
        if(!relations.isEmpty()) confidence+=.15f;
        if(!recommendation.isEmpty()) confidence+=.05f;
        String coordinates = buildCoordinates(g, y, greenItems, yellowItems, bmp.getWidth(), bmp.getHeight());
        return new AnalysisResult(System.currentTimeMillis(),g,y,tiles,relations,recommendation,change,Math.min(1f,confidence),coordinates);
    }

    private static void sortMarkers(List<Detector.Marker> a){
        Collections.sort(a,(x,z)->{int d=Integer.compare(x.rect.top,z.rect.top);return d!=0?d:Integer.compare(x.rect.left,z.rect.left);});
    }

    private List<Detector.Tile> attachMarkersToTiles(List<Detector.Marker> markers,List<Detector.Tile> tiles,Bitmap bmp,TemplateRepository repo){
        ArrayList<Detector.Tile> out=new ArrayList<>();
        Set<Integer> used=new HashSet<>();
        for(Detector.Marker m:markers){
            double best=Double.MAX_VALUE; int bi=-1;
            double mx=(m.rect.left+m.rect.right)/2.0, my=(m.rect.top+m.rect.bottom)/2.0;
            for(int i=0;i<tiles.size();i++) if(!used.contains(i)){
                Rect r=tiles.get(i).rect;
                double tx=(r.left+r.right)/2.0, ty=(r.top+r.bottom)/2.0;
                // Badge normally sits near the upper-right of its tile.
                double dx=mx-tx, dy=my-ty;
                double d=Math.hypot(dx,dy);
                if(d<best && dy>-r.height()*0.75 && dy<r.height()*0.75) {best=d;bi=i;}
            }
            if(bi>=0 && best<Math.max(bmp.getWidth()/4.0,120)) {used.add(bi); out.add(tiles.get(bi));}
            else out.add(new Detector.Tile(new Rect(),0,"غير معروف",0));
        }
        return out;
    }

    private List<String> buildRelations(List<Detector.Tile> green,List<Detector.Tile> yellow){
        ArrayList<String> out=new ArrayList<>();
        for(Detector.Tile g:green){
            if(g.label.equals("غير معروف")) continue;
            Detector.Tile best=null; double bd=Double.MAX_VALUE;
            for(Detector.Tile y:yellow){
                if(y.label.equals("غير معروف")) continue;
                double d=centerDistance(g.rect,y.rect);
                if(d<bd){bd=d;best=y;}
            }
            if(best!=null && bd<260){
                db.observeRelation(g.label,best.label);
                int hits=db.relationHits(g.label,best.label);
                int total=db.relationTotal(g.label);
                int pct=total>0?Math.round(100f*hits/total):0;
                String rel="🟩 "+g.label+" ← 🟨 "+best.label+" | نسبة الربط المرصودة: "+pct+"% ("+hits+"/"+total+")";
                out.add(rel);
            }
        }
        return out;
    }

    private String signature(List<Detector.Tile> items){
        StringBuilder s=new StringBuilder();
        for(Detector.Tile t) if(!t.label.equals("غير معروف")) s.append(t.label).append(';');
        return s.toString();
    }

    private String updateAndRecommend(String previous,String current){
        if(previous==null||previous.isEmpty()||current.isEmpty()||previous.equals(current)) return "";
        String[] now=current.split(";");
        String next=now.length>0?now[0]:"";
        if(next.isEmpty()) return "";
        int total=db.observeTransition(previous,next);
        int best=db.bestTransitionCount(previous);
        if(best<=0) return "";
        int pct=Math.round(100f*best/Math.max(1,total));
        return "الخطوة التالية المرصودة: "+next+" — "+pct+"% من السجل (ليست ضمانًا لنتيجة عشوائية)";
    }


    private String buildCoordinates(List<Detector.Marker> green,List<Detector.Marker> yellow,
                                    List<Detector.Tile> greenItems,List<Detector.Tile> yellowItems,
                                    int width,int height){
        StringBuilder s=new StringBuilder();
        if(!green.isEmpty()){
            s.append("🟩 إحداثيات الشرايط (x,y,w,h): ");
            for(int i=0;i<green.size();i++){
                if(i>0)s.append(" ؛ ");
                Rect r=green.get(i).rect;
                s.append(i+1).append("=(").append(r.left).append(',').append(r.top).append(',')
                 .append(r.width()).append(',').append(r.height()).append(')');
            }
            s.append('\n');
        }
        if(!yellow.isEmpty()){
            s.append("🟨 إحداثيات الخزين (x,y,w,h): ");
            for(int i=0;i<yellow.size();i++){
                if(i>0)s.append(" ؛ ");
                Rect r=yellow.get(i).rect;
                s.append(i+1).append("=(").append(r.left).append(',').append(r.top).append(',')
                 .append(r.width()).append(',').append(r.height()).append(')');
            }
            s.append('\n');
        }
        if(!greenItems.isEmpty()){
            s.append("📍 مراكز العناصر: ");
            for(int i=0;i<greenItems.size();i++){
                Detector.Tile t=greenItems.get(i); if(t.rect.isEmpty()) continue;
                if(s.charAt(s.length()-1)!=' ') s.append(" ؛ ");
                s.append("🟩 ").append(t.label).append("@").append((t.rect.left+t.rect.right)/2)
                 .append(',').append((t.rect.top+t.rect.bottom)/2);
            }
        }
        return s.toString();
    }

    private static double centerDistance(Rect a,Rect b){
        double ax=(a.left+a.right)/2.0,ay=(a.top+a.bottom)/2.0;
        double bx=(b.left+b.right)/2.0,by=(b.top+b.bottom)/2.0;
        return Math.hypot(ax-bx,ay-by);
    }
    public void save(AnalysisResult r){db.add(r);} public TemplateRepository templates(){return templates;} public HistoryDb db(){return db;}
}
