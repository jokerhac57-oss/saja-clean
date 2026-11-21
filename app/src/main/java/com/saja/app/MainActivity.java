package com.saja.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
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
        start.setOnClickListener(v -> startForegroundService(new Intent(this, ListenService.class)));
        box.addView(start);

        Button stop = new Button(this);
        stop.setText("STOP");
        stop.setOnClickListener(v -> stopService(new Intent(this, ListenService.class)));
        box.addView(stop);

        setContentView(box);
    }
    }
