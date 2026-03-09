package com.dp.analysis;

import android.content.Context;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class CrashReportWriter {

    private static final String DIR_NAME = "crash_reports";
    public static File save(Context context, CrashReport report) throws IOException {

        File dir = new File(context.getFilesDir(), DIR_NAME);

        if (!dir.exists()) {
            dir.mkdirs();
        }

        String filename = "crash_" + System.currentTimeMillis() + ".txt";

        File file = new File(dir, filename);

        FileWriter writer = new FileWriter(file);

        writer.write(report.toString());

        writer.flush();
        writer.close();

        return file;
    }
}