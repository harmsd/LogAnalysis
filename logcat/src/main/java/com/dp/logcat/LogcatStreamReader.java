package com.dp.logcat;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.NoSuchElementException;

public class LogcatStreamReader implements Iterator<Log>, Closeable {
    private final BufferedReader reader;
    private final StringBuilder msgBuffer = new StringBuilder();
    private Log nextLog;
    private boolean nextReady = false;
    private boolean finished = false;
    private int id = 0;

    public LogcatStreamReader(InputStream inputStream) {
        this.reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
    }

    @Override
    public boolean hasNext() {
        if(finished) return false;
        if(nextReady) return true;

        try {
            while(true) {
                String metadata = reader.readLine();
                if(metadata == null) {
                    finished = true;
                    return false;
                }
                metadata = metadata.trim();

                if (!metadata.startsWith("[")) {
                    continue;
                }

                String msg = reader.readLine();
                if(msg == null) {
                    finished = true;
                    return false;
                }
                msgBuffer.append(msg);

                while(true) {
                    msg = reader.readLine();
                    if(msg == null) {
                        finished = true;
                        return false;
                    }
                    if (msg.isEmpty()) {
                        break;
                    }
                    msgBuffer.append('\n').append(msg);
                }

                try {
                    nextLog = Log.parse(id, metadata, msgBuffer.toString());
                    id += 1;
                    nextReady = true;
                    return true;
                } catch (Exception ignored) {

                } finally {
                    msgBuffer.setLength(0);
                }
            }
        } catch (IOException io) {
            finished = true;
            throw new UncheckedIOException(io);
        }
    }

    @Override
    public Log next() {
        if (!hasNext()) {
            throw new NoSuchElementException("No more logs");
        }
        nextReady = false;
        Log out = nextLog;
        nextLog = null;
        return out;
    }

    public void close() {
        try {
            reader.close();
        } catch (IOException ignored) {
        }
    }
}
