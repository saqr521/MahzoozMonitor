package com.mahzooz.monitor;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.List;

public class MainActivity extends Activity {
    private static final int REQ = 42;
    private TextView status, stats;
    private Analyzer analyzer;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        analyzer = new Analyzer(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 24, 24, 24);

        TextView title = new TextView(this);
        title.setText("مراقب المحظوظ Pro — تحليل 🟩 الشرايط و🟨 الخزين");
        title.setTextSize(21);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(title);

        status = new TextView(this);
        status.setText("جاهز — اضغط ابدأ المراقبة");
        status.setTextSize(17);
        status.setPadding(0, 20, 0, 20);
        root.addView(status);

        Button start = new Button(this);
        start.setText("ابدأ المراقبة والتحليل");
        start.setOnClickListener(v -> startCapture());
        root.addView(start);

        Button stop = new Button(this);
        stop.setText("إيقاف المراقبة");
        stop.setOnClickListener(v -> {
            stopService(new Intent(this, CaptureService.class));
            status.setText("تم الإيقاف");
        });
        root.addView(stop);

        Button calibrate = new Button(this);
        calibrate.setText("تعليم اسم عنصر (بط / جولد / بطيخ...)");
        calibrate.setOnClickListener(v -> calibrate());
        root.addView(calibrate);

        Button history = new Button(this);
        history.setText("السجل والإحصائيات");
        history.setOnClickListener(v -> showHistory());
        root.addView(history);

        stats = new TextView(this);
        stats.setText(analyzer.db().stats());
        stats.setPadding(0, 20, 0, 0);
        root.addView(stats);

        setContentView(root);
        registerReceiver(receiver, new IntentFilter(CaptureService.ACTION_RESULT),
                Build.VERSION.SDK_INT >= 33 ? Context.RECEIVER_NOT_EXPORTED : 0);
    }

    private void startCapture() {
        MediaProjectionManager m =
                (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        startActivityForResult(m.createScreenCaptureIntent(), REQ);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != REQ) return;
        if (result != RESULT_OK || data == null) {
            status.setText("تم إلغاء صلاحية التقاط الشاشة.");
            return;
        }

        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        Intent i = new Intent(this, CaptureService.class);
        i.putExtra("resultCode", result);
        i.putExtra("data", data);
        i.putExtra("width", dm.widthPixels);
        i.putExtra("height", dm.heightPixels);
        i.putExtra("densityDpi", dm.densityDpi);

        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
        else startService(i);

        status.setText("جاري تشغيل التحليل… انتظر أول لقطة.");
    }

    private void calibrate() {
        final EditText e = new EditText(this);
        e.setHint("اسم العنصر");
        new AlertDialog.Builder(this)
                .setTitle("تعليم عنصر")
                .setMessage("الاسم وحده لا ينشئ قالبًا بصريًا. التعرف الدقيق على بط/جولد/بطيخ يحتاج لقطة واضحة من شاشة اللعبة.")
                .setView(e)
                .setPositiveButton("حفظ الاسم", (d, w) ->
                        Toast.makeText(this, "تم إدخال: " + e.getText(), Toast.LENGTH_SHORT).show())
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void showHistory() {
        List<String> x = analyzer.db().recent(30);
        new AlertDialog.Builder(this)
                .setTitle("السجل")
                .setMessage(analyzer.db().stats() + "\n\n"
                        + android.text.TextUtils.join("\n", x))
                .setPositiveButton("حسنًا", null)
                .show();
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            status.setText(i.getStringExtra("text"));
            stats.setText(analyzer.db().stats());
        }
    };

    @Override protected void onDestroy() {
        try { unregisterReceiver(receiver); } catch (Exception ignored) {}
        super.onDestroy();
    }
}
