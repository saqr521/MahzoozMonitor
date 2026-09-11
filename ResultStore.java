package com.mahzooz.monitor;

import android.content.Context;
import java.util.*;

public final class ResultStore {
    private static final String PREF="results";
    private static final String KEY="count";
    public static synchronized void save(Context c, AnalysisResult r){
        int n=c.getSharedPreferences(PREF,0).getInt(KEY,0)+1;
        c.getSharedPreferences(PREF,0).edit().putInt(KEY,n)
            .putString("last",r.toString()).apply();
    }
    public static String summary(Context c){
        android.content.SharedPreferences p=c.getSharedPreferences(PREF,0);
        return "عدد اللقطات المحللة: "+p.getInt(KEY,0)+"\nآخر تحليل: "+p.getString("last","لا يوجد");
    }
}
