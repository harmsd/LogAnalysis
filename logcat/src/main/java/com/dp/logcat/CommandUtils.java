package com.dp.logcat;

import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

public final class CommandUtils {

    private CommandUtils() {}
    public static int runCmd(
            List<String> cmd,
            @Nullable List<String> stdoutList,
            @Nullable List<String> stderrList,
            boolean redirectStderr
    ) {
        ProcessBuilder processBuilder = new ProcessBuilder(cmd).redirectErrorStream(redirectStderr);
        Process process = null;

        Thread stdoutThread = null;
        Thread stderrThread = null;

        try {
            process = processBuilder.start();

            final InputStream stdoutStream = process.getInputStream();
            final InputStream stderrStream = process.getErrorStream();

            stdoutThread = new Thread(() -> {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(stdoutStream))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        if (stdoutList != null) stdoutList.add(line);
                    }
                } catch (Exception ignored) {
                }
            }, "CommandUtils-stdout");

            stdoutThread.start();

            // If redirectStderr==true, stderr is already merged into stdout; no need to read it separately.
            if (!redirectStderr) {
                stderrThread = new Thread(() -> {
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(stderrStream))) {
                        String line;
                        while ((line = br.readLine()) != null) {
                            if (stderrList != null) stderrList.add(line);
                        }
                    } catch (Exception ignored) {
                    }
                }, "CommandUtils-stderr");
                stderrThread.start();
            }

            int exitCode = process.waitFor();

            try {
                if (stderrThread != null) stderrThread.join(1000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }

            try {
                if (stdoutThread != null) stdoutThread.join(1000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }

            return exitCode;

        } catch (Exception e) {
            return -1;

        } finally {
            if (process != null) {
                try {
                    process.destroy();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    // Convenience overload matching Kotlin defaults
    public static int runCmd(List<String> cmd) {
        return runCmd(cmd, null, null, false);
    }

    public static int runCmd(List<String> cmd, @Nullable List<String> stdoutList) {
        return runCmd(cmd, stdoutList, null, false);
    }

    public static int runCmd(List<String> cmd,
                             @Nullable List<String> stdoutList,
                             @Nullable List<String> stderrList) {
        return runCmd(cmd, stdoutList, stderrList, false);
    }
}