package com.dp.logcat;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;

import com.dp.logger.Logger;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class LogcatUtil {

    private static final Set<String> POSSIBLE_BUFFERS = new LinkedHashSet<>(Arrays.asList(
            "main", "system", "radio", "events", "crash", "security", "kernel"
    ));

    private static volatile Set<String> DEFAULT_BUFFERS;
    private static volatile String[] AVAILABLE_BUFFERS;

    private LogcatUtil() {
        // no instances
    }

    @NonNull
    public static Set<String> getDEFAULT_BUFFERS() {
        Set<String> local = DEFAULT_BUFFERS;
        if (local == null) {
            synchronized (LogcatUtil.class) {
                local = DEFAULT_BUFFERS;
                if (local == null) {
                    local = getDefaultBuffers();
                    DEFAULT_BUFFERS = local;
                    Logger.debug(LogcatUtil.class, "Default buffers: " + local);
                }
            }
        }
        return local;
    }

    @NonNull
    public static String[] getAVAILABLE_BUFFERS() {
        String[] local = AVAILABLE_BUFFERS;
        if (local == null) {
            synchronized (LogcatUtil.class) {
                local = AVAILABLE_BUFFERS;
                if (local == null) {
                    local = getAvailableBuffers();
                    AVAILABLE_BUFFERS = local;
                    Logger.debug(LogcatUtil.class, "Available buffers: " + Arrays.toString(local));
                }
            }
        }
        return local;
    }

    public static boolean writeToFile(@NonNull List<Log> logs, @NonNull File file) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, false))) {
            writeToFileHelper(logs, writer);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean writeToFile(@NonNull Context context,
                                      @NonNull List<Log> logs,
                                      @NonNull Uri uri) {
        OutputStream os = null;
        try {
            os = context.getContentResolver().openOutputStream(uri);
            if (os == null) return false;

            // writer закроет OutputStreamWriter, который закроет OutputStream
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os))) {
                writeToFileHelper(logs, writer);
                return true;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        } finally {
            // На случай, если writer не успел создаться, но os уже открылся
            if (os != null) {
                try {
                    os.close();
                } catch (IOException ignored) { }
            }
        }
    }

    /**
     * Blocking version of Kotlin suspend fun countLogs(file: File).
     * Call off the main thread.
     */
    public static long countLogs(@NonNull File file) {
        try (FileInputStream fis = new FileInputStream(file);
             LogcatStreamReader reader = new LogcatStreamReader(fis)) {

            long count = 0L;
            while (reader.hasNext()) {
                reader.next();
                count++;
            }
            return count;

        } catch (IOException | SecurityException | UncheckedIOException ignored) {
            return 0L;
        }
    }

    /**
     * Blocking version of Kotlin suspend fun countLogs(context: Context, file: DocumentFile).
     * Call off the main thread.
     */
    public static long countLogs(@NonNull Context context, @NonNull DocumentFile file) {
        InputStream is = null;
        try {
            is = context.getContentResolver().openInputStream(file.getUri());
            if (is == null) return 0L;

            // Закрываем reader (он закроет свой BufferedReader, а тот закроет InputStreamReader),
            // но сам InputStream лучше закрывать только если reader не создался.
            try (LogcatStreamReader reader = new LogcatStreamReader(is)) {
                long count = 0L;
                while (reader.hasNext()) {
                    reader.next();
                    count++;
                }
                return count;
            }

        } catch (IOException | SecurityException | UncheckedIOException ignored) {
            return 0L;

        } finally {
            // Если LogcatStreamReader не создался, is нужно закрыть вручную
            if (is != null) {
                try {
                    is.close();
                } catch (IOException ignored) { }
            }
        }
    }

    private static void writeToFileHelper(@NonNull List<Log> logs,
                                          @NonNull BufferedWriter writer) throws IOException {
        for (Log log : logs) {
            writer.write(String.valueOf(log)); // log.toString()
        }
        writer.flush();
    }

    @NonNull
    private static Set<String> getDefaultBuffers() {
        Set<String> result = new LinkedHashSet<>();

        List<String> stdoutList = new ArrayList<>();
        CommandUtils.runCmd(Arrays.asList("logcat", "-g"), stdoutList, null, false);

        for (String s : stdoutList) {
            int colonIndex = s.indexOf(':');
            if (colonIndex != -1) {
                if (s.startsWith("/")) {
                    String sub = s.substring(0, colonIndex);
                    int lastSlashIndex = sub.lastIndexOf('/');
                    if (lastSlashIndex != -1) {
                        result.add(sub.substring(lastSlashIndex + 1));
                    }
                } else {
                    result.add(s.substring(0, colonIndex));
                }
            }
        }

        return result;
    }

    @NonNull
    private static String[] getAvailableBuffers() {
        List<String> stdoutList = new ArrayList<>();

        // Kotlin: redirectStderr = true
        CommandUtils.runCmd(Arrays.asList("logcat", "-h"), stdoutList, null, true);

        int bufferHelpIndex = -1;
        for (int i = 0; i < stdoutList.size(); i++) {
            if (stdoutList.get(i).trim().startsWith("-b")) {
                bufferHelpIndex = i;
                break;
            }
        }

        int bufferHelpEndIndex = bufferHelpIndex;
        if (bufferHelpIndex != -1) {
            bufferHelpEndIndex += 1;
            while (bufferHelpEndIndex < stdoutList.size()
                    && !stdoutList.get(bufferHelpEndIndex).trim().startsWith("-")) {
                bufferHelpEndIndex += 1;
            }

            StringBuilder sb = new StringBuilder();
            for (int i = bufferHelpIndex; i < bufferHelpEndIndex; i++) {
                if (i > bufferHelpIndex) sb.append('\n');
                sb.append(stdoutList.get(i));
            }
            String helpChunk = sb.toString();

            List<String> buffers = new ArrayList<>();
            for (String buffer : POSSIBLE_BUFFERS) {
                if (helpChunk.contains(buffer)) {
                    buffers.add(buffer);
                }
            }

            Collections.sort(buffers);
            return buffers.toArray(new String[0]);
        }

        return new String[0];
    }
}