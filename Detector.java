package com.mahzooz.monitor;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class Detector {
    public static class Marker { public Rect rect; public float score; public String type; public Marker(Rect r,float s,String t){rect=r;score=s;type=t;} }
    public static class Tile { public Rect rect; public long hash; public String label; public float similarity; public Tile(Rect r,long h,String l,float s){rect=r;hash=h;label=l;similarity=s;} }

    private Detector() {}

    public static List<Marker> findMarkers(Bitmap src, boolean green) {
        Bitmap b = src;
        int w=b.getWidth(), h=b.getHeight();
        boolean[][] seen=new boolean[h][w];
        ArrayList<Marker> out=new ArrayList<>();
        float[] hsv=new float[3];
        int step=Math.max(1, Math.min(w,h)/500);
        int startY=(int)(h*0.52f);
        for(int y=startY;y<h;y+=step) for(int x=0;x<w;x+=step){
            if(seen[y][x]) continue;
            Color.colorToHSV(b.getPixel(x,y),hsv);
            if(!matches(hsv,green)) continue;
            int minX=x,maxX=x,minY=y,maxY=y,count=0;
            ArrayDeque<int[]> q=new ArrayDeque<>(); q.add(new int[]{x,y}); seen[y][x]=true;
            while(!q.isEmpty()){
                int[] p=q.removeFirst(); int px=p[0],py=p[1]; count++;
                minX=Math.min(minX,px);maxX=Math.max(maxX,px);minY=Math.min(minY,py);maxY=Math.max(maxY,py);
                int[][] ns={{px+step,py},{px-step,py},{px,py+step},{px,py-step}};
                for(int[] n:ns){int nx=n[0],ny=n[1]; if(nx<0||ny<0||nx>=w||ny>=h||seen[ny][nx])continue; Color.colorToHSV(b.getPixel(nx,ny),hsv); if(matches(hsv,green)){seen[ny][nx]=true;q.add(n);}}
            }
            int bw=maxX-minX+1,bh=maxY-minY+1;
            if(count>=20 && bw>=8 && bh>=8 && bw<=70 && bh<=70 && ((double)bw/bh)>=0.55 && ((double)bw/bh)<=1.8){
                float density=(float)count/(bw*bh);
                if(density>0.08f) out.add(new Marker(new Rect(minX,minY,maxX+1,maxY+1),Math.min(1f,density*1.8f),green?"GREEN":"YELLOW"));
            }
        }
        dedupe(out); return out;
    }

    private static boolean matches(float[] hsv, boolean green){
        float hue=hsv[0], sat=hsv[1], val=hsv[2];
        if(sat<0.35f || val<0.35f) return false;
        if(green) return (hue>=65 && hue<=170); // Android HSV hue is degrees
        return (hue>=35 && hue<=70);
    }

    private static void dedupe(List<Marker> list){
        Collections.sort(list, Comparator.comparingInt(a->a.rect.top));
        ArrayList<Marker> keep=new ArrayList<>();
        for(Marker m:list){boolean dup=false; for(Marker k:keep){if(Rect.intersects(m.rect,k.rect) || centerDistance(m.rect,k.rect)<10){dup=true;break;}} if(!dup)keep.add(m);} list.clear();list.addAll(keep);
    }
    private static double centerDistance(Rect a,Rect b){double ax=(a.left+a.right)/2.0,ay=(a.top+a.bottom)/2.0,bx=(b.left+b.right)/2.0,by=(b.top+b.bottom)/2.0;return Math.hypot(ax-bx,ay-by);}

    /** Finds a likely grid of square/near-square tiles in the lower portion of the screen. */
    public static List<Tile> findTiles(Bitmap src, TemplateRepository repo){
        int w=src.getWidth(),h=src.getHeight();
        ArrayList<Tile> out=new ArrayList<>();
        int start=(int)(h*0.52f), cell=Math.max(48,w/6), gap=Math.max(3,cell/12);
        for(int y=start;y<h-cell/3;y+=cell){
            for(int x=0;x+cell<=w;x+=cell){
                Rect r=new Rect(x+gap,y+gap,x+cell-gap,y+cell-gap);
                if(r.width()<32||r.height()<32) continue;
                long hash=phash(src,r);
                TemplateRepository.Match m=repo.best(hash);
                out.add(new Tile(r,hash,m.label,m.similarity));
            }
        }
        return out;
    }


    /** Matches the icon directly below a green/yellow marker. The game places its
     * classification badge at the upper-right of each gift tile. */
    public static Tile itemForMarker(Bitmap src, Marker marker, TemplateRepository repo) {
        int cx=(marker.rect.left+marker.rect.right)/2;
        int top=marker.rect.bottom+8;
        int halfW=Math.max(58, src.getWidth()/12);
        int left=Math.max(0,cx-halfW-18);
        int right=Math.min(src.getWidth(),cx+halfW-18);
        int bottom=Math.min(src.getHeight(),top+Math.max(90,src.getHeight()/10));
        if(right-left<32 || bottom-top<32) return new Tile(new Rect(),0,"غير معروف",0);
        Rect r=new Rect(left,top,right,bottom);
        long hash=phash(src,r); TemplateRepository.Match m=repo.best(hash);
        return new Tile(r,hash,m.label,m.similarity);
    }

    public static long phash(Bitmap src, Rect r){
        final int N=8; double[] gray=new double[N*N];
        for(int yy=0;yy<N;yy++) for(int xx=0;xx<N;xx++){
            int sx=r.left+(xx*r.width())/N, sy=r.top+(yy*r.height())/N;
            int c=src.getPixel(Math.max(0,Math.min(src.getWidth()-1,sx)),Math.max(0,Math.min(src.getHeight()-1,sy)));
            gray[yy*N+xx]=0.299*Color.red(c)+0.587*Color.green(c)+0.114*Color.blue(c);
        }
        double sum=0; for(double v:gray)sum+=v; double avg=sum/gray.length; long hash=0;
        for(int i=0;i<gray.length;i++) if(gray[i]>=avg) hash|=(1L<<i);
        return hash;
    }
}
