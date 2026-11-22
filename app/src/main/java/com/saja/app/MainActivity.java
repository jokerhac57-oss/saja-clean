package com.saja.app;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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
    private static final int REQ_LOCATION = 3;
    private static final int REQ_CALENDAR = 4;
    private static final int REQ_CAMERA = 5;
    private static final int REQ_DOC = 6;
    private String pendingLook = null;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("saja", MODE_PRIVATE);
        setContentView(buildUi());
        String look = getIntent().getStringExtra("look");
        if (look != null) {
            pendingLook = look;
            launchCamera();
        }
    }

    private boolean light() {
        return prefs.getBoolean("light", false);
    }

    private boolean ravaan() {
        return prefs.getBoolean("ravaan", false);
    }

    private int bgColor() {
        return light() ? 0xFFF2F7FA : 0xFF05070A;
    }

    private int panelColor() {
        return light() ? 0xFFFFFFFF : 0xFF0B1A24;
    }

    private int textColor() {
        return light() ? 0xFF0B1A24 : 0xFFE6F7FF;
    }

    private int mutedColor() {
        return light() ? 0xFF5C7A88 : 0xFF4A7A8C;
    }

    private int accent() {
        if (ravaan()) return light() ? 0xFFD96B00 : 0xFFFFB300;
        return light() ? 0xFF0077A8 : 0xFF00E5FF;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable box(int fill, int strokeColor, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        g.setStroke(dp(1), strokeColor);
        return g;
    }

    private LinearLayout row() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        return l;
    }

    private LinearLayout.LayoutParams weight() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        p.setMargins(dp(4), 0, dp(4), 0);
        return p;
    }

    private LinearLayout.LayoutParams matchWrap() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, dp(6), 0, dp(6));
        return p;
    }

    private Button card(String text, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTextColor(accent());
        b.setBackground(box(panelColor(), accent(), 16));
        b.setOnClickListener(l);
        return b;
    }

    private Button chip(String text, View.OnClickListener l) {
        Button b = card(text, l);
        b.setTextSize(13);
        return b;
    }

    private TextView section(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(12);
        t.setTextColor(mutedColor());
        t.setPadding(dp(4), dp(18), 0, dp(2));
        return t;
    }

    private EditText input(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(mutedColor());
        e.setTextColor(textColor());
        e.setSingleLine(true);
        e.setBackground(box(panelColor(), accent(), 16));
        e.setPadding(dp(14), dp(12), dp(14), dp(12));
        return e;
    }

    private ScrollView buildUi() {
        int acc = accent();
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(bgColor());

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(20), dp(36), dp(20), dp(28));

        TextView title = new TextView(this);
        title.setText("S A J A");
        title.setTextSize(30);
        title.setTextColor(acc);
        title.setGravity(Gravity.CENTER);
        if (!light()) title.setShadowLayer(18f, 0f, 0f, acc);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText(ravaan() ? "RAVAAN MODE  ·  faster replies" : "Your personal assistant");
        sub.setTextSize(13);
        sub.setTextColor(mutedColor());
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, dp(4), 0, dp(8));
        root.addView(sub);

        HumanoidView figure = new HumanoidView(this);
        figure.setColors(acc, panelColor());
        figure.setFast(ravaan());
        root.addView(figure, new LinearLayout.LayoutParams(dp(220), dp(260)));

        LinearLayout modes = row();
        modes.addView(chip(light() ? "DARK MODE" : "LIGHT MODE",
                v -> {
                    prefs.edit().putBoolean("light", !light()).apply();
                    recreate();
                }), weight());
        modes.addView(chip(ravaan() ? "RAVAAN: ON" : "RAVAAN: OFF",
                v -> {
                    prefs.edit().putBoolean("ravaan", !ravaan()).apply();
                    recreate();
                }), weight());
        root.addView(modes, matchWrap());

        root.addView(section("VOICE"));
        root.addView(card("START LISTENING", v -> askThenStart()), matchWrap());
        root.addView(card("STOP", v -> stopService(new Intent(this, ListenService.class))), matchWrap());

        root.addView(section("KEYS"));
        final EditText keyBox = input("Paste your OpenRouter key");
        root.addView(keyBox, matchWrap());
        root.addView(card("SAVE API KEY",
                v -> saveSecret("openrouter.key", keyBox.getText().toString().trim(), "API key")), matchWrap());
        final EditText githubBox = input("Paste your GitHub token");
        root.addView(githubBox, matchWrap());
        root.addView(card("SAVE GITHUB TOKEN",
                v -> saveSecret("github.token", githubBox.getText().toString().trim(), "GitHub token")), matchWrap());

        root.addView(section("CAMERA AND FILES"));
        root.addView(card("TAKE PHOTO", v -> {
            pendingLook = null;
            launchCamera();
        }), matchWrap());
        root.addView(card("PICK DOCUMENT", v -> pickDocument()), matchWrap());

        root.addView(section("PERMISSIONS"));
        root.addView(card("ALLOW CONTACTS",
                v -> requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, REQ_CONTACTS)), matchWrap());
        root.addView(card("ALLOW LOCATION (HOME WI-FI)",
                v -> requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQ_LOCATION)), matchWrap());
        root.addView(card("ALLOW CALENDAR",
                v -> requestPermissions(new String[]{Manifest.permission.READ_CALENDAR}, REQ_CALENDAR)), matchWrap());
        root.addView(card("NOTIFICATION ACCESS",
                v -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))), matchWrap());
        root.addView(card("SCREEN HELPER SETTINGS",
                v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))), matchWrap());

        scroll.addView(root);
        return scroll;
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

    private void pickDocument() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("text/*");
        try {
            startActivityForResult(i, REQ_DOC);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No file picker found", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveDocument(int resultCode, Intent data) {
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            Toast.makeText(this, "No document picked", Toast.LENGTH_SHORT).show();
            return;
        }
        Uri uri = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception e) {
            // some pickers do not allow saved access; reading may still work
        }
        try (FileOutputStream out = new FileOutputStream(new File(getFilesDir(), "doc_uri.txt"))) {
            out.write(uri.toString().getBytes(StandardCharsets.UTF_8));
            Toast.makeText(this, "Document saved. Say ask document, then your question.", Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            Toast.makeText(this, "Could not remember the document", Toast.LENGTH_SHORT).show();
        }
    }

    private void launchCamera() {
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
            startActivityForResult(camera, REQ_CAMERA);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No camera app found", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_DOC) {
            saveDocument(resultCode, data);
            return;
        }
        if (requestCode != REQ_CAMERA) return;
        if (resultCode == RESULT_OK && pendingLook != null) {
            Intent svc = new Intent(this, ListenService.class)
                    .setAction("com.saja.app.ASK_LOOK")
                    .putExtra("q", pendingLook);
            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(svc);
            } else {
                startService(svc);
            }
            Toast.makeText(this, "Sending the photo to SAJA", Toast.LENGTH_SHORT).show();
        } else if (resultCode != RESULT_OK) {
            Toast.makeText(this, "No photo taken", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Photo saved", Toast.LENGTH_SHORT).show();
        }
        pendingLook = null;
        if (getIntent().getStringExtra("look") != null) finish();
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
        if (requestCode == REQ_LOCATION) {
            Toast.makeText(this, granted ? "Location allowed" : "Location not allowed", Toast.LENGTH_SHORT).show();
        }
        if (requestCode == REQ_CALENDAR) {
            Toast.makeText(this, granted ? "Calendar allowed" : "Calendar not allowed", Toast.LENGTH_SHORT).show();
        }
    }
}
