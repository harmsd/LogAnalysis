package com.dp.loganalysis_2;

import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.dp.collections.FixedCircularArray;
import com.dp.logcat.LogcatStreamReader;
import com.dp.loganalysis_2.LogsAdapter;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;



public class MainActivity extends AppCompatActivity {

    // Храним последние 50 логов
    private final FixedCircularArray<com.dp.logcat.Log> buffer = new FixedCircularArray<>(50);

    // Recycler
    private RecyclerView recyclerView;
    private LogsAdapter adapter;
    private final ArrayList<com.dp.logcat.Log> data = new ArrayList<>();

    // Thread control
    private volatile boolean running = false;
    private Thread logThread;
    private Process logcatProcess;

    // Чтобы не перегружать UI: обновляем максимум раз в ~100мс
    private long lastUiUpdateMs = 0L;
    private static final long UI_UPDATE_THROTTLE_MS = 100L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.d("TEST", "MainActivity started");

        // В activity_main.xml должен быть RecyclerView с id recyclerLogs
        recyclerView = findViewById(R.id.recyclerLogs);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new LogsAdapter(data);
        recyclerView.setAdapter(adapter);

        // Запуск чтения logcat: Thread -> callback -> runOnUiThread
        startLogcatReading(log -> {
            // Этот код уже выполняется в UI thread (мы зовём listener через runOnUiThread)
            // Обновим список значениями из buffer
            data.clear();
            int sz = buffer.size();
            for (int i = 0; i < sz; i++) {
                data.add(buffer.get(i));
            }

            adapter.notifyDataSetChanged();

            if (!data.isEmpty()) {
                recyclerView.scrollToPosition(data.size() - 1);
            }
        });
    }

    private interface OnLogListener {
        void onLogReceived(com.dp.logcat.Log log);
    }

    private void startLogcatReading(OnLogListener listener) {
        if (running) return;

        running = true;
        logThread = new Thread(() -> {
            try {
                logcatProcess = new ProcessBuilder()
                        .command("logcat", "-v", "long")
                        .redirectErrorStream(true)
                        .start();

                try (InputStream inputStream = logcatProcess.getInputStream();
                     LogcatStreamReader reader = new LogcatStreamReader(inputStream)) {

                    while (running && reader.hasNext()) {
                        com.dp.logcat.Log log = reader.next();
                        buffer.add(log);

                        // throttling UI updates (иначе может лагать)
                        long now = SystemClock.uptimeMillis();
                        if (now - lastUiUpdateMs >= UI_UPDATE_THROTTLE_MS) {
                            lastUiUpdateMs = now;
                            runOnUiThread(() -> listener.onLogReceived(log));
                        }
                    }
                }
            } catch (Exception e) {
                Log.e("READER", "Error", e);
            } finally {
                // на всякий случай
                stopLogcatProcess();
            }
        }, "logcat-reader");

        logThread.start();
    }

    private void stopLogcatProcess() {
        try {
            if (logcatProcess != null) {
                logcatProcess.destroy();
            }
        } catch (Exception ignored) {
        } finally {
            logcatProcess = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        running = false;

        if (logThread != null) {
            logThread.interrupt();
            logThread = null;
        }

        stopLogcatProcess();
    }

}