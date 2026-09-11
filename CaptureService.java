package com.mahzooz.monitor;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.hardware.display.DisplayManager;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import androidx.core.app.NotificationCompat;

public class CaptureService extends Service {
    public static final String ACTION_RESULT = "com.mahzooz.monitor.RESULT";
    private MediaProjection projection;
    private ImageReader reader;
    private android.hardware.display.VirtualDisplay display;
    private Analyzer analyzer;
    private HandlerThread workerThread;
    private Handler worker;
    private long lastFrame = 0;
    private boolean running = false;

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        createChannel();

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(7, notification("جاري التقاط الشاشة وتحليلها"),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(7, notification("جاري التقاط الشاشة وتحليلها"));
        }

        if (running) return START_STICKY;

        int resultCode = intent.getIntExtra("resultCode", Activity.RESULT_CANCELED);
        Intent data;
        if (Build.VERSION.SDK_INT >= 33) {
            data = intent.getParcelableExtra("data", Intent.class);
        } else {
            data = intent.getParcelableExtra("data");
        }

        if (resultCode != Activity.RESULT_OK || data == null) {
            broadcast("لم يتم منح صلاحية التقاط الشاشة.");
            stopSelf();
            return START_NOT_STICKY;
        }

        MediaProjectionManager pm =
                (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        projection = pm.getMediaProjection(resultCode, data);
        if (projection == null) {
            broadcast("تعذر بدء التقاط الشاشة.");
            stopSelf();
            return START_NOT_STICKY;
        }

        DisplayMetrics dm = getResources().getDisplayMetrics();
        int w = intent.getIntExtra("width", dm.widthPixels);
        int h = intent.getIntExtra("height", dm.heightPixels);
        int dpi = intent.getIntExtra("densityDpi", dm.densityDpi);

        workerThread = new HandlerThread("MahzoozCapture");
        workerThread.start();
        worker = new Handler(workerThread.getLooper());
        analyzer = new Analyzer(this);
        AlgorithmTrace.init(this);

        reader = ImageReader.newInstance(w, h, android.graphics.PixelFormat.RGBA_8888, 3);
        display = projection.createVirtualDisplay(
                "MahzoozMonitor", w, h, dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.getSurface(), null, worker);

        projection.registerCallback(new MediaProjection.Callback() {
            @Override public void onStop() {
                running = false;
                broadcast("تم إيقاف التقاط الشاشة.");
                stopSelf();
            }
        }, worker);

        reader.setOnImageAvailableListener(r -> {
            running = true;
            long now = System.currentTimeMillis();
            if (now - lastFrame < 300) return;
            lastFrame = now;
            Image image = null;
            Bitmap bitmap = null;
            try {
                image = r.acquireLatestImage();
                if (image == null) return;
                bitmap = ImageUtils.toBitmap(image);
                AnalysisResult result = analyzer.analyze(bitmap);
                analyzer.save(result);
                broadcast(format(result));
            } catch (Throwable e) {
                broadcast("خطأ أثناء تحليل اللقطة: " + e.getClass().getSimpleName());
            } finally {
                if (image != null) image.close();
                if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
            }
        }, worker);

        broadcast("تم بدء التقاط الشاشة — في انتظار عناصر 🟩 و🟨...");
        return START_STICKY;
    }

    private Notification notification(String text) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, "capture")
                .setContentTitle("مراقب المحظوظ Pro")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    private void broadcast(String text) {
        Intent out = new Intent(ACTION_RESULT);
        out.setPackage(getPackageName());
        out.putExtra("text", text);
        sendBroadcast(out);
    }

    private String format(AnalysisResult r) {
        StringBuilder s = new StringBuilder();
        s.append("🟩 الأشرطة: ").append(r.green.size())
                .append("   🟨 الخزين: ").append(r.yellow.size()).append("\n");
        if (!r.relations.isEmpty()) {
            s.append("الربط والنسب:\n");
            for (String rel : r.relations) s.append("• ").append(rel).append("\n");
        }
        if (!r.recommendation.isEmpty()) s.append(r.recommendation).append("\n");
        if (!r.tiles.isEmpty()) {
            s.append("العناصر: ");
            int n = 0;
            for (Detector.Tile t : r.tiles) {
                if (n++ >= 10) break;
                if (n > 1) s.append("، ");
                s.append(t.label);
            }
            s.append("\n");
        }
        if (!r.observedChange.isEmpty()) s.append(r.observedChange).append("\n");
        if (!r.coordinateSummary.isEmpty()) s.append(r.coordinateSummary).append("\n");
        s.append("الثقة البصرية: ").append(Math.round(r.confidence * 100)).append('%\n');
        s.append(AlgorithmTrace.snapshot());
        return s.toString();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm =
                    (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.createNotificationChannel(new NotificationChannel(
                    "capture", "مراقبة الشاشة", NotificationManager.IMPORTANCE_LOW));
        }
    }

    @Override public void onDestroy() {
        running = false;
        if (reader != null) { reader.close(); reader = null; }
        if (display != null) { display.release(); display = null; }
        if (projection != null) { projection.stop(); projection = null; }
        if (workerThread != null) { workerThread.quitSafely(); workerThread = null; }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
