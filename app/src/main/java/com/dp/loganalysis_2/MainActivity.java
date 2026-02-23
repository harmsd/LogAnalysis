package com.dp.loganalysis_2;

import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.dp.collections.FixedCircularArray;
import com.dp.logcat.LogcatStreamReader;

import java.io.InputStream;

public class MainActivity extends AppCompatActivity {

    private FixedCircularArray<com.dp.logcat.Log> buffer =
            new FixedCircularArray<>(50);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.d("TEST", "MainActivity started");

        startLogcatReading();
    }

    private void startLogcatReading() {

        new Thread(() -> {

            try {
                // запускаем logcat процесс
                Process process = new ProcessBuilder()
                        .command("logcat", "-v", "long")
                        .redirectErrorStream(true)
                        .start();

                InputStream inputStream = process.getInputStream();

                // твой reader
                try (LogcatStreamReader reader =
                             new LogcatStreamReader(inputStream)) {

                    while (reader.hasNext()) {

                        com.dp.logcat.Log log = reader.next();

                        // кладём в ring buffer
                        buffer.add(log);

                        // выводим в обычный logcat (чтобы увидеть результат)
                        Log.d("READER", log.toString());

                        // просто проверка size()
                        Log.d("BUFFER_SIZE",
                                "size = " + buffer.size());
                    }
                }

            } catch (Exception e) {
                Log.e("READER", "Error", e);
            }

        }).start();
    }
}