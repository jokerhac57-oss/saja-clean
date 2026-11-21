package com.saja.app;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView text = new TextView(this);
        text.setText("SAJA is running");
        text.setTextSize(28);
        text.setPadding(60, 200, 60, 60);
        setContentView(text);
    }
}
