package com.dp.logcat;

import android.net.Uri;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.dp.logger.Logger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

public final class LogcatSession {

    private static final long THREAD_JOIN_TIMEOUT_MS = 5_000L;
    private final int capacity;
    private final Set<String> buffers;
    private volatile long pollIntervalMs = 250L;
    // state
    private final AtomicBoolean active = new AtomicBoolean(false);
    private final AtomicBoolean stopped = new AtomicBoolean(false);

    private volatile boolean paused = false;
    private final Object pauseWaiter = new Object();

    // process / threads
    @Nullable private volatile Process logcatProcess;
    @Nullable private volatile Thread logcatThread;
    @Nullable private volatile Thread pollerThread;

    // data
    private final ReentrantLock lock = new ReentrantLock();
    private final RingBuffer<Log> allLogs;
    private final RingBuffer<Log> pendingLogs;

    @Nullable private volatile LogBatchListener onNewLog;

    private final List<Filter> filters = new ArrayList<>();
    private final List<Filter> exclusions = new ArrayList<>();

    // recording
    private volatile boolean recording = false;
    @Nullable private volatile Thread recordThread;
    private final BlockingQueue<List<Log>> recordBuffer = new LinkedBlockingQueue<>();
    @Nullable private volatile RecordingFileInfo recordingFileInfo;

    // option support caches (computed once)
    private static final CompletableFuture<Boolean> UID_OPTION_SUPPORTED =
            CompletableFuture.supplyAsync(() -> dumpLogcatLogWithOptions("-v", "uid"));

    private static final CompletableFuture<Boolean> YEAR_OPTION_SUPPORTED =
            CompletableFuture.supplyAsync(() -> dumpLogcatLogWithOptions("-v", "year"));

    public LogcatSession(int capacity, @NonNull Set<String> buffers) {
        this.capacity = Math.max(1, capacity);
        this.buffers = new HashSet<>(buffers);
        this.allLogs = new RingBuffer<>(this.capacity);
        this.pendingLogs = new RingBuffer<>(Math.min(1000, this.capacity));
    }

    public long getPollIntervalMs() {
        return pollIntervalMs;
    }

    public void setPollIntervalMs(long pollIntervalMs) {
        this.pollIntervalMs = pollIntervalMs;
    }

    public boolean isPaused() {
        return paused;
    }

    public void setPaused(boolean value) {
        synchronized (pauseWaiter) {
            paused = value;
            if (!paused) {
                pauseWaiter.notifyAll();
            }
        }
    }

    public boolean isRecording() {
        lock.lock();
        try {
            return recording;
        } finally {
            lock.unlock();
        }
    }

    // ---------------- subscription (replacement for Flow) ----------------

    public interface Subscription {
        void unsubscribe();
    }

    public interface LogBatchListener {
        void onLogs(@NonNull List<Log> logs);
    }
    @NonNull
    public Subscription subscribe(@NonNull LogBatchListener listener) {
        // send snapshot immediately
        final List<Log> snapshot;
        lock.lock();
        try {
            snapshot = filterList(allLogs.toListCopyUnsafe());
            onNewLog = listener;
        } finally {
            lock.unlock();
        }
        listener.onLogs(snapshot);

        return () -> {
            lock.lock();
            try {
                if (onNewLog == listener) onNewLog = null;
            } finally {
                lock.unlock();
            }
        };
    }

    // ---------------- status ----------------

    public static final class Status {
        public final boolean success;
        public Status(boolean success) { this.success = success; }
    }

    @NonNull
    public CompletableFuture<Status> start() {
        Logger.debug(LogcatSession.class, "starting");

        if (stopped.get()) {
            throw new IllegalStateException("LogcatSession was stopped, it cannot be re-started");
        }
        if (!active.compareAndSet(false, true)) {
            throw new IllegalStateException("LogcatSession is already active!");
        }

        final CompletableFuture<Status> statusFuture = new CompletableFuture<>();

        logcatThread = new Thread(() -> {
            try {
                boolean uidSupported = UID_OPTION_SUPPORTED.get(10, TimeUnit.SECONDS);
                boolean yearSupported = YEAR_OPTION_SUPPORTED.get(10, TimeUnit.SECONDS);

                Process process = startLogcatProcess(uidSupported, yearSupported);
                statusFuture.complete(new Status(process != null));

                if (process != null) {
                    readLogs(process);
                }
            } catch (Throwable t) {
                Logger.debug(LogcatSession.class, "logcat thread error: " + t);
                if (!statusFuture.isDone()) statusFuture.complete(new Status(false));
            } finally {
                Logger.debug(LogcatSession.class, "stopped logcat thread");
            }
        }, "LogcatSession-logcat");

        pollerThread = new Thread(() -> {
            try {
                poll();
            } catch (Throwable t) {
                Logger.debug(LogcatSession.class, "poller thread error: " + t);
            } finally {
                Logger.debug(LogcatSession.class, "stopped polling thread");
            }
        }, "LogcatSession-poller");

        logcatThread.start();
        pollerThread.start();

        return statusFuture;
    }

    public void stop() {
        Logger.debug(LogcatSession.class, "stopping");

        stopped.set(true);
        active.set(false);
        recording = false;
        setPaused(false);

        Process p = logcatProcess;
        if (p != null) {
            try {
                if (Build.VERSION.SDK_INT >= 26) {
                    p.destroyForcibly();
                } else {
                    p.destroy();
                }
            } catch (Throwable ignored) {}
        }
        logcatProcess = null;

        joinQuietly(logcatThread, THREAD_JOIN_TIMEOUT_MS);
        logcatThread = null;

        joinQuietly(pollerThread, THREAD_JOIN_TIMEOUT_MS);
        pollerThread = null;

        Thread rt = recordThread;
        if (rt != null) {
            rt.interrupt();
            joinQuietly(rt, THREAD_JOIN_TIMEOUT_MS);
        }
        recordThread = null;

        lock.lock();
        try {
            allLogs.clear();
            pendingLogs.clear();
            recordBuffer.clear();
            recordingFileInfo = null;
            onNewLog = null;
        } finally {
            lock.unlock();
        }

        Logger.debug(LogcatSession.class, "stopped");
    }

    // ---------------- recording ----------------

    public static final class RecordingFileInfo {
        @NonNull public final String fileName;
        @NonNull public final Uri uri;
        public final boolean isCustomLocation;

        public RecordingFileInfo(@NonNull String fileName, @NonNull Uri uri, boolean isCustomLocation) {
            this.fileName = fileName;
            this.uri = uri;
            this.isCustomLocation = isCustomLocation;
        }
    }

    public void startRecording(
            @NonNull RecordingFileInfo info,
            @NonNull BufferedWriter writer
    ) {
        lock.lock();
        try {
            if (recording) return;

            this.recordingFileInfo = info;
            recording = true;

            recordThread = new Thread(() -> {
                try {
                    while (recording) {
                        List<Log> batch;
                        try {
                            batch = recordBuffer.take();
                        } catch (InterruptedException ie) {
                            break;
                        }

                        try {
                            for (Log log : batch) {
                                writer.write(log.toString());
                            }
                            writer.flush();
                        } catch (Throwable t) {
                            break;
                        }
                    }
                } finally {
                    try { writer.flush(); } catch (Throwable ignored) {}
                    try { writer.close(); } catch (Throwable ignored) {}
                }
            }, "LogcatSession-recorder");

            recordThread.start();
        } finally {
            lock.unlock();
        }
    }

    @Nullable
    public RecordingFileInfo stopRecording() {
        lock.lock();
        try {
            recording = false;

            Thread rt = recordThread;
            if (rt != null) {
                rt.interrupt();
                joinQuietly(rt, THREAD_JOIN_TIMEOUT_MS);
            }
            recordThread = null;

            recordBuffer.clear();
            RecordingFileInfo result = recordingFileInfo;
            recordingFileInfo = null;
            return result;
        } finally {
            lock.unlock();
        }
    }

    // ---------------- filters ----------------

    public interface Filter {
        boolean apply(@NonNull Log log);
    }

    public void setFilters(@NonNull List<Filter> newFilters, boolean exclusion) {
        lock.lock();
        try {
            if (exclusion) {
                exclusions.clear();
                exclusions.addAll(newFilters);
            } else {
                filters.clear();
                filters.addAll(newFilters);
            }
        } finally {
            lock.unlock();
        }
    }

    public void clearLogs() {
        lock.lock();
        try {
            allLogs.clear();
            pendingLogs.clear();
        } finally {
            lock.unlock();
        }
    }

    // ---------------- internal: logcat process ----------------

    @Nullable
    private Process startLogcatProcess(boolean uidSupported, boolean yearSupported) {
        List<String> cmd = new ArrayList<>();
        cmd.add("logcat");
        cmd.add("-v");
//      cmd.add("long");
        cmd.add("threadtime");

        // parity with Kotlin: (questionable, but kept)
        if (uidSupported) {
            cmd.add("-v");
            cmd.add("uid");
        }
        if (yearSupported) {
            cmd.add("-v");
            cmd.add("year");
        }

        for (String buffer : buffers) {
            cmd.add("-b");
            cmd.add(buffer);
        }

        try {
            Process process = new ProcessBuilder(cmd).start();
            logcatProcess = process;
            return process;
        } catch (IOException e) {
            Logger.debug(LogcatSession.class, "error starting logcat process: " + e);
            return null;
        }
    }

    private void readLogs(@NonNull Process process) {
        try {
            final InputStream inputStream = process.getInputStream();

            Thread stdoutReaderThread = new Thread(() -> {
                try (LogcatStreamReader reader = new LogcatStreamReader(inputStream)) {
                    // IMPORTANT: your LogcatStreamReader is Iterator<Log>
                    while (reader.hasNext()) {
                        Log log = reader.next();
                        lock.lock();
                        try {
                            pendingLogs.add(log);
                        } finally {
                            lock.unlock();
                        }
                    }
                } catch (Throwable ignored) {
                }
                Logger.debug(LogcatSession.class, "stopped logcat reader thread");
            }, "LogcatSession-stdout");

            stdoutReaderThread.start();

            process.waitFor();
            try { inputStream.close(); } catch (Throwable ignored) {}
            joinQuietly(stdoutReaderThread, THREAD_JOIN_TIMEOUT_MS);

        } catch (Throwable t) {
            Logger.debug(LogcatSession.class, "error reading logs: " + t);
        }
    }

    private void poll() {
        while (active.get()) {
            // pause support
            synchronized (pauseWaiter) {
                while (paused && active.get()) {
                    try {
                        pauseWaiter.wait();
                    } catch (InterruptedException ignored) {}
                }
            }
            if (!active.get()) break;

            List<Log> pending;
            List<Log> filtered;
            LogBatchListener listener;

            lock.lock();
            try {
                pending = pendingLogs.drainToList();
                pendingLogs.clear(); // not strictly needed after drain, but safe
                allLogs.addAll(pending);

                filtered = filterList(pending);

                if (recording && !filtered.isEmpty()) {
                    recordBuffer.offer(filtered);
                }

                listener = onNewLog;
            } finally {
                lock.unlock();
            }

            if (listener != null && !filtered.isEmpty()) {
                try {
                    listener.onLogs(filtered);
                } catch (Throwable ignored) {}
            }

            try {
                Thread.sleep(pollIntervalMs);
            } catch (InterruptedException ignored) {}
        }
    }

    // ---------------- internal: filtering ----------------

    @NonNull
    private List<Log> filterList(@NonNull List<Log> input) {
        if (input.isEmpty()) return input;

        // Copy rules under lock, to avoid race while filtering
        final List<Filter> f;
        final List<Filter> ex;

        lock.lock();
        try {
            f = new ArrayList<>(filters);
            ex = new ArrayList<>(exclusions);
        } finally {
            lock.unlock();
        }

        List<Log> out = new ArrayList<>(input.size());

        for (Log e : input) {
            boolean excluded = false;
            for (Filter flt : ex) {
                if (flt.apply(e)) {
                    excluded = true;
                    break;
                }
            }
            if (excluded) continue;

            if (f.isEmpty()) {
                out.add(e);
            } else {
                for (Filter flt : f) {
                    if (flt.apply(e)) {
                        out.add(e);
                        break;
                    }
                }
            }
        }
        return out;
    }

    // ---------------- helpers ----------------

    private static void joinQuietly(@Nullable Thread t, long timeoutMs) {
        if (t == null) return;
        try {
            t.join(timeoutMs);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private static boolean dumpLogcatLogWithOptions(String... options) {
        if (options == null || options.length == 0) {
            throw new IllegalArgumentException("options empty");
        }
        try {
            List<String> cmd = new ArrayList<>();
            cmd.add("logcat");
            cmd.add("-v");
            cmd.add("brief");
            Collections.addAll(cmd, options);
            cmd.add("-d");

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);

            Process p = pb.start();

            // must consume stdout on some devices otherwise waitFor may hang
            Thread consumer = new Thread(() -> {
                try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                    while (r.readLine() != null) { /* consume */ }
                } catch (Throwable ignored) {}
            }, "LogcatSession-dump-consumer");
            consumer.start();

            int exit = p.waitFor();
            joinQuietly(consumer, THREAD_JOIN_TIMEOUT_MS);
            return exit == 0;

        } catch (Throwable ignored) {
            return false;
        }
    }

    // ---------------- ring buffer ----------------

    private static final class RingBuffer<T> {
        private final int capacity;
        private final Deque<T> dq;

        RingBuffer(int capacity) {
            this.capacity = Math.max(1, capacity);
            this.dq = new ArrayDeque<>(Math.min(this.capacity, 1024));
        }

        void add(@NonNull T value) {
            if (dq.size() == capacity) dq.removeFirst();
            dq.addLast(value);
        }

        void addAll(@NonNull List<T> values) {
            for (T v : values) add(v);
        }

        void clear() { dq.clear(); }

        @NonNull
        List<T> toListCopyUnsafe() {
            return new ArrayList<>(dq);
        }



        @NonNull
        List<T> drainToList() {
            List<T> out = new ArrayList<>(dq.size());
            while (!dq.isEmpty()) out.add(dq.removeFirst());
            return out;
        }
    }
}