package com.dp.logger;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class Logger {

    private Logger() {}

    private static final int DEBUG = 1;
    private static final int ERROR = 2;
    private static final int INFO = 3;
    private static final int VERBOSE = 4;
    private static final int WARNING = 5;
    private static final int WTF = 6;

    private static String sTag = "Logger";

    public static void init(@NonNull String tag) {
        sTag = tag;
    }

    // ----------- overloads with Class -----------

    public static void debug(@NonNull Class<?> type, @NonNull String msg) {
        debug(nameOf(type), msg);
    }

    public static void error(@NonNull Class<?> type, @NonNull String msg) {
        error(nameOf(type), msg, null);
    }

    public static void error(@NonNull Class<?> type, @NonNull String msg, @Nullable Throwable e) {
        error(nameOf(type), msg, e);
    }

    public static void info(@NonNull Class<?> type, @NonNull String msg) {
        info(nameOf(type), msg);
    }

    public static void verbose(@NonNull Class<?> type, @NonNull String msg) {
        verbose(nameOf(type), msg);
    }

    public static void warning(@NonNull Class<?> type, @NonNull String msg) {
        warning(nameOf(type), msg);
    }

    public static void wtf(@NonNull Class<?> type, @NonNull String msg) {
        wtf(nameOf(type), msg);
    }

    // ----------- overloads with String tag -----------

    public static void debug(@NonNull String tag, @NonNull String msg) {
        log(DEBUG, "[" + tag + "] " + msg, null);
    }

    public static void error(@NonNull String tag, @NonNull String msg) {
        log(ERROR, "[" + tag + "] " + msg, null);
    }

    public static void error(@NonNull String tag, @NonNull String msg, @Nullable Throwable e) {
        log(ERROR, "[" + tag + "] " + msg, e);
    }

    public static void info(@NonNull String tag, @NonNull String msg) {
        log(INFO, "[" + tag + "] " + msg, null);
    }

    public static void verbose(@NonNull String tag, @NonNull String msg) {
        log(VERBOSE, "[" + tag + "] " + msg, null);
    }

    public static void warning(@NonNull String tag, @NonNull String msg) {
        log(WARNING, "[" + tag + "] " + msg, null);
    }

    public static void wtf(@NonNull String tag, @NonNull String msg) {
        log(WTF, "[" + tag + "] " + msg, null);
    }

    // ----------- internals -----------

    @NonNull
    private static String nameOf(@NonNull Class<?> cls) {
        String n = cls.getSimpleName();
        return (n == null || n.isEmpty()) ? "N/A" : n;
    }

    private static void log(int type, @NonNull String msg, @Nullable Throwable e) {
        switch (type) {
            case DEBUG:
                Log.d(sTag, msg);
                break;
            case ERROR:
                Log.e(sTag, msg, e);
                break;
            case INFO:
                Log.i(sTag, msg);
                break;
            case VERBOSE:
                Log.v(sTag, msg);
                break;
            case WARNING:
                Log.w(sTag, msg);
                break;
            case WTF:
                Log.wtf(sTag, msg);
                break;
            default:
                Log.d(sTag, msg);
                break;
        }
    }
}