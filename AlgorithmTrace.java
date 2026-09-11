package com.mahzooz.monitor;

import android.content.Context;
import android.graphics.Rect;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Read-only audit trail for what the monitor observed and what it produced.
 * It deliberately does not inspect another app's private memory/network traffic
 * and does not inject input into the game.
 */
public final class AlgorithmTrace {
    private static final Object LOCK = new Object();
    private static String lastGameToMonitor = "";
    private static String lastMonitorToOverlay = "";
    private static long lastAt;
    private static Context appContext;

    private AlgorithmTrace() {}

    public static void init(Context c) { appContext = c.getApplicationContext(); }

    public static void recordGameToMonitor(
            List<Detector.Marker> green,
            List<Detector.Marker> yellow,
            List<Detector.Tile> greenItems,
            List<Detector.Tile> yellowItems,
            List<String> relations) {
        StringBuilder s = new StringBuilder();
        s.append("GAME_SCREEN -> MONITOR | green=").append(green.size())
         .append(" yellow=").append(yellow.size());
        appendItems(s, "greenItems", greenItems);
        appendItems(s, "yellowItems", yellowItems);
        s.append(" relations=").append(relations.size());
        synchronized (LOCK) {
            lastGameToMonitor = s.toString();
            lastAt = System.currentTimeMillis();
        }
        persist("IN", s.toString());
    }

    public static void recordMonitorOutput(String output) {
        String value = "MONITOR -> OVERLAY | " + output;
        synchronized (LOCK) { lastMonitorToOverlay = value; }
        persist("OUT", value);
    }

    /** Records an intended UI action without actually sending it to the game. */
    public static void recordIntendedAction(String action) {
        persist("INTENDED_ACTION", "MONITOR -> GAME (NOT SENT) | " + action);
    }

    public static String snapshot() {
        synchronized (LOCK) {
            return "آخر مسار مقروء:\n" + lastGameToMonitor +
                   "\nآخر مخرَج للمراقب:\n" + lastMonitorToOverlay +
                   "\nوقت آخر قراءة: " + format(lastAt);
        }
    }

    private static void appendItems(StringBuilder s, String name, List<Detector.Tile> items) {
        s.append(' ').append(name).append('=');
        for (int i=0; i<items.size(); i++) {
            Detector.Tile t = items.get(i);
            if (i > 0) s.append(',');
            s.append(t.label).append('@');
            Rect r = t.rect;
            if (r == null || r.isEmpty()) s.append("?,?");
            else s.append((r.left+r.right)/2).append(',').append((r.top+r.bottom)/2);
        }
    }

    private static void persist(String direction, String message) {
        if (appContext == null) return;
        String line = format(System.currentTimeMillis()) + " | " + direction + " | " + message;
        String old = appContext.getSharedPreferences("algorithm_trace", Context.MODE_PRIVATE)
                .getString("log", "");
        if (old.length() > 12000) old = old.substring(old.length() - 9000);
        appContext.getSharedPreferences("algorithm_trace", Context.MODE_PRIVATE)
                .edit().putString("log", old + line + "\n").apply();
    }

    private static String format(long t) {
        if (t == 0) return "-";
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date(t));
    }

    public static String log(Context c) {
        return c.getSharedPreferences("algorithm_trace", Context.MODE_PRIVATE)
                .getString("log", "لا يوجد سجل خوارزمي بعد.");
    }
}
