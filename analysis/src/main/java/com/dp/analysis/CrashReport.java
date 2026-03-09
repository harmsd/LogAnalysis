package com.dp.analysis;

import com.dp.logcat.Log;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class CrashReport {

    private final LocalDateTime timestamp;

    private final String topComponent;
    private final String topException;

    private final int componentScore;

    private final List<Log> contextLogs;

    public CrashReport(
            LocalDateTime timestamp,
            String topComponent,
            String topException,
            int componentScore,
            List<Log> contextLogs
    ) {

        this.timestamp = timestamp;
        this.topComponent = topComponent;
        this.topException = topException;
        this.componentScore = componentScore;
        this.contextLogs = new ArrayList<>(contextLogs);
    }


    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getTopComponent() {
        return topComponent;
    }

    public String getTopException() {
        return topException;
    }

    public int getComponentScore() {
        return componentScore;
    }

    public List<Log> getContextLogs() {
        return contextLogs;
    }


    public double getConfidence() {

        if (componentScore == 0) return 0;

        return Math.min(1.0, componentScore / 200.0);
    }


    @Override
    public String toString() {

        StringBuilder sb = new StringBuilder();

        sb.append("Crash Report\n");
        sb.append("Time: ").append(timestamp).append("\n");

        sb.append("Top component: ")
                .append(topComponent)
                .append("\n");

        sb.append("Exception: ")
                .append(topException)
                .append("\n");

        sb.append("Score: ")
                .append(componentScore)
                .append("\n");

        sb.append("Confidence: ")
                .append(getConfidence())
                .append("\n");

        sb.append("\nContext logs:\n");

        for (Log log : contextLogs) {
            sb.append(log.metadataToString())
                    .append(" ")
                    .append(log.getMsg())
                    .append("\n");
        }

        return sb.toString();
    }
}