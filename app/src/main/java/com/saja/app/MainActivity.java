package com.saja.app;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQ_START = 1;
    private static final int REQ_CONTACTS = 2;

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

        box.addView(button("START LISTENING", v -> askThenStart()));
        box.addView(button("STOP", v -> stopService(new Intent(this, ListenService.class))));

        EditText keyBox = input("Paste your OpenRouter key");
        box.addView(keyBox);
        box.addView(button("SAVE API KEY", v -> saveSecret("openrouter.key", keyBox.getText().toString().trim(), "API key")));

        EditText githubBox = input("Paste your GitHub token");
        box.addView(githubBox);
        box.addView(button("SAVE GITHUB TOKEN", v -> saveSecret("github.token", githubBox.getText().toString().trim(), "GitHub token")));

        box.addView(button("TAKE PHOTO", v -> takePhoto()));
        box.addView(button("ALLOW CONTACTS", v -> requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, REQ_CONTACTS)));

        setContentView(box);
    }

    private Button button(String text, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(text);
        b.setOnClickListener(listener);
        return b;
    }

    private EditText input(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setSingleLine(true);
        return e;
    }

    private void saveSecret(String fileName, String value, String label) {
        if (value.isEmpty()) {
            Toast.makeText(this, "Paste the " + label + " first", Toast.LENGTH_SHORT).show();
            return;
        }
        try (FileOutputStream out = new FileOutputStream(new File(getFilesDir(), fileName))) {
            out.write(value.getBytes(StandardCharsets.UTF_8));
            Toast.makeText(this, label + " saved on this phone", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(this, "Could not save the " + label, Toast.LENGTH_SHORT).show();
        }
    }

    private void takePhoto() {
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, "SAJA_" + stamp + ".jpg");
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (uri == null) {
            Toast.makeText(this, "Could not prepare the photo", Toast.LENGTH_SHORT).show();
            return;
        }
        try (FileOutputStream out = new FileOutputStream(new File(getFilesDir(), "photo_uri.txt"))) {
            out.write(uri.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            Toast.makeText(this, "Could not remember the photo", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent camera = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        camera.putExtra(MediaStore.EXTRA_OUTPUT, uri);
        camera.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try {
            startActivity(camera);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No camera app found", Toast.LENGTH_SHORT).show();
        }
    }

    private void askThenStart() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startListening();
        } else {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS}, REQ_START);
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
        boolean granted = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
        if (requestCode == REQ_START && granted) {
            startListening();
        }
        if (requestCode == REQ_CONTACTS) {
            Toast.makeText(this, granted ? "Contacts allowed" : "Contacts not allowed", Toast.LENGTH_SHORT).show();
        }
    }
}
