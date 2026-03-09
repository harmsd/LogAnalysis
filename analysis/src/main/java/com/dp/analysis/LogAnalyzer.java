package com.dp.analysis;

import android.annotation.SuppressLint;

import com.dp.logcat.Log;
import com.dp.collections.FixedCircularArray;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LogAnalyzer {
    private Log log;
    private final FixedCircularArray<Log> window = new FixedCircularArray<>(300);
    private final Map<String, Integer> componentScores = new HashMap<>();
    private final Map<String, Integer> exceptionScores = new HashMap<>();

    public void accept(Log log) {
        window.add(log);

        int score = calculateScore(log);
        if (score <= 0) return;
        componentScores.merge(log.getTag(), score, Integer::sum);

        String exception = extractException(log.getMsg());
        if (exception != null) {
            exceptionScores.merge(exception, score, Integer::sum);
        }

    }

    private int calculateScore(Log log) {
        int score = 0;

        switch (log.getPriority()) {
            case "F":
                score += 100;
                break;

            case "E":
                score += 50;
                break;

            case "W":
                score += 20;
                break;

            case "I":
                score += 5;
                break;
        }

        String msg = log.getMsg();

        if (msg.contains("FATAL EXCEPTION"))
            score += 120;

        if (msg.contains("ANR"))
            score += 100;

        if (msg.contains("Caused by"))
            score += 70;

        if (msg.contains("NullPointerException"))
            score += 80;

        if (msg.contains("IllegalStateException"))
            score += 60;

        if (msg.contains("IndexOutOfBoundsException"))
            score += 60;

        return score;
    }

    private String extractException(String msg) {
        if (msg.contains("NullPointerExceptions")) {
            return "NullPointerException";
        }
        if (msg.contains("IllegalStateException")) {
            return "IllegalStateException";
        }
        if (msg.contains("IndexOutOfBoundsException"))
            return "IndexOutOfBoundsException";

        if (msg.contains("SecurityException"))
            return "SecurityException";

        return null;
    }

    public boolean isCrash(Log log) {

        String msg = log.getMsg();

        return msg.contains("FATAL EXCEPTION")
                || msg.contains("Shutting down VM")
                || msg.contains("Process: ")
                || msg.contains("ANR in")
                || msg.contains("Application Not Responding")
                || msg.contains("Input dispatching timed out")
                || msg.contains("Unable to start activity")
                || msg.contains("Unable to resume activity")
                || msg.contains("Unable to pause activity")
                || msg.contains("java.lang.NullPointerException")
                || msg.contains("java.lang.IllegalStateException")
                || msg.contains("java.lang.IndexOutOfBoundsException")
                || msg.contains("Fatal signal");
    }

    public String getTopComponent() {

        return componentScores.entrySet()
                .stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    public String getTopException() {

        return exceptionScores.entrySet()
                .stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }


    public int getComponentScore(String component) {
        return componentScores.getOrDefault(component, 0);
    }

    @SuppressLint("NewApi")
    public CrashReport createCrashReport() {

        String component = getTopComponent();
        String exception = getTopException();

        int score = getComponentScore(component);

        List<Log> logs = new ArrayList<>();

        for (Log log : window) {
            logs.add(log);
        }

        return new CrashReport(
                LocalDateTime.now(),
                component,
                exception,
                score,
                logs
        );
    }


    public void reset() {
        componentScores.clear();
        exceptionScores.clear();
    }


}
