package com.mahzooz.monitor;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.*;

public class HistoryDb extends SQLiteOpenHelper {
    public HistoryDb(Context c) { super(c,"mahzooz_history.db",null,3); }
    @Override public void onCreate(SQLiteDatabase d){
        d.execSQL("CREATE TABLE events(id INTEGER PRIMARY KEY AUTOINCREMENT, ts INTEGER, green INTEGER, yellow INTEGER, tiles TEXT, observed TEXT, confidence REAL, recommendation TEXT)");
        d.execSQL("CREATE TABLE relations(green TEXT, yellow TEXT, hits INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(green,yellow))");
        d.execSQL("CREATE TABLE transitions(previous TEXT, next TEXT, hits INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(previous,next))");
    }
    @Override public void onUpgrade(SQLiteDatabase d,int oldV,int newV){
        if(oldV<2) d.execSQL("ALTER TABLE events ADD COLUMN recommendation TEXT DEFAULT ''");
        if(oldV<3){
            d.execSQL("CREATE TABLE IF NOT EXISTS relations(green TEXT, yellow TEXT, hits INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(green,yellow))");
            d.execSQL("CREATE TABLE IF NOT EXISTS transitions(previous TEXT, next TEXT, hits INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(previous,next))");
        }
    }
    public synchronized void observeRelation(String green,String yellow){
        if(green==null||yellow==null||green.isEmpty()||yellow.isEmpty())return;
        getWritableDatabase().execSQL("INSERT INTO relations(green,yellow,hits) VALUES(?,?,1) ON CONFLICT(green,yellow) DO UPDATE SET hits=hits+1",new Object[]{green,yellow});
    }
    public synchronized int observeTransition(String previous,String next){
        getWritableDatabase().execSQL("INSERT INTO transitions(previous,next,hits) VALUES(?,?,1) ON CONFLICT(previous,next) DO UPDATE SET hits=hits+1",new Object[]{previous,next});
        Cursor c=getReadableDatabase().rawQuery("SELECT SUM(hits) FROM transitions WHERE previous=?",new String[]{previous});
        int n=0;if(c.moveToFirst())n=c.isNull(0)?0:c.getInt(0);c.close();return n;
    }
    public synchronized int bestTransitionCount(String previous){
        Cursor c=getReadableDatabase().rawQuery("SELECT MAX(hits) FROM transitions WHERE previous=?",new String[]{previous});
        int n=0;if(c.moveToFirst())n=c.isNull(0)?0:c.getInt(0);c.close();return n;
    }
    public void add(AnalysisResult r){
        StringBuilder t=new StringBuilder();
        for(Detector.Tile x:r.tiles){if(t.length()>0)t.append(',');t.append(x.label).append(':').append(String.format(Locale.US,"%.2f",x.similarity));}
        getWritableDatabase().execSQL("INSERT INTO events(ts,green,yellow,tiles,observed,confidence,recommendation) VALUES(?,?,?,?,?,?,?)",new Object[]{r.timestamp,r.green.size(),r.yellow.size(),t.toString(),r.observedChange,r.confidence,r.recommendation});
    }
    public List<String> recent(int n){
        ArrayList<String> out=new ArrayList<>();
        Cursor c=getReadableDatabase().rawQuery("SELECT ts,green,yellow,tiles,confidence,recommendation FROM events ORDER BY id DESC LIMIT ?",new String[]{String.valueOf(n)});
        while(c.moveToNext()){
            String rec=c.getString(5);
            out.add(new java.text.SimpleDateFormat("HH:mm:ss",Locale.getDefault()).format(new Date(c.getLong(0)))+" | 🟩 "+c.getInt(1)+" | 🟨 "+c.getInt(2)+" | "+c.getString(3)+" | "+String.format(Locale.US,"%.0f%%",c.getDouble(4)*100)+(rec==null||rec.isEmpty()?"":"\n"+rec));
        } c.close(); return out;
    }
    public String stats(){
        Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*),AVG(confidence),SUM(green),SUM(yellow) FROM events",null);
        String s="لا توجد بيانات بعد";
        if(c.moveToFirst())s="الرصد: "+c.getInt(0)+" | متوسط الثقة: "+String.format(Locale.US,"%.0f%%",c.isNull(1)?0:c.getDouble(1)*100)+" | 🟩 "+c.getInt(2)+" | 🟨 "+c.getInt(3);
        c.close(); return s;
    }
}
