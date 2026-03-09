package com.dp.loganalysis_2;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import com.dp.analysis.CrashReport;
import com.dp.analysis.CrashReportWriter;
import com.dp.analysis.LogAnalyzer;
import com.dp.collections.FixedCircularArray;
import com.dp.logcat.Log;
import com.dp.logcat.LogcatStreamReader;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class LogActivity extends AppCompatActivity {

    private static final int MAX_LOGS = 2000;

    private final FixedCircularArray<Log> logs = new FixedCircularArray<>(MAX_LOGS);
    private final LogAnalyzer analyzer = new LogAnalyzer();

    private RecyclerView recycler;
    private LogsAdapter adapter;

    private Thread readerThread;
    private Process logcatProcess;

    private boolean running = false;
    private boolean paused = false;

    private MenuItem playPauseItem;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_log);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        setTitle("Логи");
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        recycler = findViewById(R.id.recycler);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setHasFixedSize(true);

        adapter = new LogsAdapter(logs);
        recycler.setAdapter(adapter);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.log_menu, menu);

        playPauseItem = menu.findItem(R.id.action_next);

        updatePlayPauseIcon();

        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        updatePlayPauseIcon();
        return super.onPrepareOptionsMenu(menu);
    }

    private void updatePlayPauseIcon() {
        if (playPauseItem != null) {
            playPauseItem.setIcon(paused
                    ? android.R.drawable.ic_media_play
                    : android.R.drawable.ic_media_pause);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        int id = item.getItemId();

        if (id == android.R.id.home) {
            finish();
            return true;
        }

        if (id == R.id.action_next) {

            paused = !paused;

            updatePlayPauseIcon();

            return true;
        }

        return super.onOptionsItemSelected(item);
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

    @Override
    protected void onDestroy() {
        stopLogcat();
        super.onDestroy();
    }

    @SuppressLint("NotifyDataSetChanged")
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

                try (LogcatStreamReader reader =
                             new LogcatStreamReader(logcatProcess.getInputStream())) {

                    while (running && reader.hasNext()) {

                        final Log log = reader.next();

                        logs.add(log);
                        analyzer.accept(log);
                        if (analyzer.isCrash(log)) {
                            android.util.Log.d("ANALYZER_DEBUG", "CRASH DETECTED");
                            CrashReport report = analyzer.createCrashReport();

                            try {

                                File file = CrashReportWriter.save(
                                        LogActivity.this,
                                        report
                                );

                                android.util.Log.d(
                                        "CrashAnalyzer",
                                        "Report saved: " + file.getAbsolutePath()
                                );

                            } catch (IOException e) {
                                e.printStackTrace();
                            }

                            analyzer.reset();
                        }

                        runOnUiThread(() -> {

                            if (paused) return;

                            adapter.notifyDataSetChanged();

                            if (logs.size() > 0) {
                                recycler.scrollToPosition(logs.size() - 1);
                            }

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

            try {
                logcatProcess.destroy();
            } catch (Throwable ignored) {}

            logcatProcess = null;
        }
    }
}