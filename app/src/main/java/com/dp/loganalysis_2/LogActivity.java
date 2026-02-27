package com.dp.loganalysis_2;

import android.os.Bundle;
import android.widget.Button;

import com.dp.logcat.Log;
import com.dp.logcat.LogcatStreamReader;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class LogActivity extends AppCompatActivity {
    private RecyclerView recycler;
    private LogsAdapter adapter;
    private final ArrayList<Log> logs = new ArrayList<>();
    private Thread readerThread;
    private Process logcatProcess;
    private boolean running = false;
    private Button btnBack;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_log);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        recycler = findViewById(R.id.recycler);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new LogsAdapter(logs);
        recycler.setAdapter(adapter);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    protected void onStart() {
        super.onStart();
        startLogcat();
    }

    @Override
    protected void onStop() {
        stopLogcat();
        super.onStop();
    }

    private void startLogcat() {
        if (running) return;
        running = true;

        readerThread = new Thread(() -> {
            List<String> cmd = Arrays.asList(
                    "logcat",
                    "-v", "long",
                    "-T", "1"
            );

            try {
                logcatProcess = new ProcessBuilder(cmd)
                        .redirectErrorStream(true)
                        .start();

                try (LogcatStreamReader reader = new LogcatStreamReader(logcatProcess.getInputStream())) {
                    while (running && reader.hasNext()) {
                        final Log log = reader.next();

                        runOnUiThread(() -> {
                            logs.add(log);

                            final int MAX = 2000;
                            if (logs.size() > MAX) {
                                logs.subList(0, logs.size() - MAX).clear();
                                adapter.notifyDataSetChanged();
                            } else {
                                adapter.notifyItemInserted(logs.size() - 1);
                            }

                            recycler.scrollToPosition(logs.size() - 1);
                        });
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                destroyProcess();
            }
        }, "logcat-reader");

        readerThread.start();
    }

    private void stopLogcat() {
        running = false;
        destroyProcess();

        if (readerThread != null) {
            readerThread.interrupt();
            readerThread = null;
        }
    }

    private void destroyProcess() {
        if (logcatProcess != null) {
            try { logcatProcess.destroy(); } catch (Throwable ignored) {}
            logcatProcess = null;
        }
    }
}