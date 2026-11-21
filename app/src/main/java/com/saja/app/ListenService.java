package com.saja.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;

import java.util.ArrayList;
import java.util.Locale;

public class ListenService extends Service implements RecognitionListener, TextToSpeech.OnInitListener {
    private static final String[] WAKE_WORDS = {"jarvis", "jarvus", "javis"};
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SpeechRecognizer recognizer;
    private TextToSpeech tts;
    private boolean running = false;
    private boolean ttsReady = false;

    @Override
    public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager.class)
                    .createNotificationChannel(new NotificationChannel("saja", "SAJA", NotificationManager.IMPORTANCE_LOW));
        }
        Notification note = buildNote("Listening for Jarvis");
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, note, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } else {
            startForeground(1, note);
        }
        tts = new TextToSpeech(this, this);
        recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(this);
        running = true;
        handler.postDelayed(this::listen, 1000);
    }

    private Notification buildNote(String text) {
        PendingIntent tap = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, "saja")
                .setContentTitle("SAJA")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentIntent(tap)
                .setOngoing(true)
                .build();
    }

    private void showText(String text) {
        getSystemService(NotificationManager.class).notify(1, buildNote(text));
    }

    private void listen() {
        if (!running) return;
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        recognizer.startListening(i);
    }

    private void restartSoon(long ms) {
        if (running) handler.postDelayed(this::listen, ms);
    }

    private void speak(String text) {
        if (ttsReady) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "saja");
    }

    private void handleText(String heard) {
        String lower = heard.toLowerCase(Locale.US);
        for (String w : WAKE_WORDS) {
            int at = lower.indexOf(w);
            if (at >= 0) {
                String command = lower.substring(at + w.length()).trim();
                String reply = command.isEmpty() ? "Yes, I am here." : "You said " + command + ".";
                showText(reply);
                speak(reply);
                restartSoon(3000);
                return;
            }
        }
        restartSoon(300);
    }

    @Override
    public void onResults(Bundle results) {
        ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        handleText(matches == null ? "" : String.join(" ", matches));
    }

    @Override
    public void onError(int error) {
        if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
            running = false;
            showText("Microphone permission needed. Open SAJA and allow it.");
            return;
        }
        restartSoon(error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ? 2000 : 500);
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            tts.setLanguage(Locale.US);
            ttsReady = true;
        }
    }

    @Override public void onReadyForSpeech(Bundle params) {}
    @Override public void onBeginningOfSpeech() {}
    @Override public void onRmsChanged(float rmsdB) {}
    @Override public void onBufferReceived(byte[] buffer) {}
    @Override public void onEndOfSpeech() {}
    @Override public void onPartialResults(Bundle partialResults) {}
    @Override public void onEvent(int eventType, Bundle params) {}

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        handler.removeCallbacksAndMessages(null);
        if (recognizer != null) recognizer.destroy();
        if (tts != null) tts.shutdown();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
