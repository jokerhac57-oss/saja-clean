package com.saja.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private static final int REQ = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(60, 200, 60, 60);

        TextView title = new TextView(this);
        title.setText("SAJA is running");
        title.setTextSize(28);
        box.addView(title);

        Button start = new Button(this);
        start.setText("START LISTENING");
        start.setOnClickListener(v -> askThenStart());
        box.addView(start);

        Button stop = new Button(this);
        stop.setText("STOP");
        stop.setOnClickListener(v -> stopService(new Intent(this, ListenService.class)));
        box.addView(stop);

        EditText keyBox = new EditText(this);
        keyBox.setHint("Paste your OpenRouter key");
        keyBox.setSingleLine(true);
        box.addView(keyBox);

        Button save = new Button(this);
        save.setText("SAVE API KEY");
        save.setOnClickListener(v -> saveKey(keyBox.getText().toString().trim()));
        box.addView(save);

        setContentView(box);
    }

    private void saveKey(String key) {
        if (key.isEmpty()) {
            Toast.makeText(this, "Paste the key first", Toast.LENGTH_SHORT).show();
            return;
        }
        try (FileOutputStream out = new FileOutputStream(new File(getFilesDir(), "openrouter.key"))) {
            out.write(key.getBytes(StandardCharsets.UTF_8));
            Toast.makeText(this, "Key saved on this phone", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(this, "Could not save the key", Toast.LENGTH_SHORT).show();
        }
    }

    private void askThenStart() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startListening();
        } else {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS}, REQ);
        }
    }

    private void startListening() {
        Intent i = new Intent(this, ListenService.class);
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(i);
        } else {
            startService(i);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQ && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            startListening();
        }
    }
}
