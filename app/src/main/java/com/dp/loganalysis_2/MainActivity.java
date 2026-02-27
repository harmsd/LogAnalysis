package com.dp.loganalysis_2;

import com.dp.logcat.Log;
import com.dp.loganalysis_2.LogsAdapter;
import com.dp.logcat.LogcatStreamReader;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btnDeviceLogs = findViewById(R.id.buttonDeviceLogs);
        Button btnExportLogs = findViewById(R.id.buttonExportLogs);
        Button btnImportLogs = findViewById(R.id.buttonImportLogs);

        btnDeviceLogs.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, LogActivity.class);
            startActivity(intent);
        });
        btnExportLogs.setOnClickListener(v -> {
            Toast.makeText(MainActivity.this,
                    "Кнопка нажата!",
                    Toast.LENGTH_SHORT).show();
        });
        btnImportLogs.setOnClickListener(v -> {
            Toast.makeText(MainActivity.this,
                    "Кнопка нажата!",
                    Toast.LENGTH_SHORT).show();
        });
    }

}
