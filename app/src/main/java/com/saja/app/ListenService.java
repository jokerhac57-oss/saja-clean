package com.saja.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.speech.tts.TextToSpeech;

import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;
import org.vosk.android.StorageService;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ListenService extends Service implements RecognitionListener, TextToSpeech.OnInitListener {
    private static final String[] WAKE_WORDS = {"jarvis", "jarvus", "javis"};
    private Model model;
    private SpeechService speechService;
    private TextToSpeech tts;
    private boolean ttsReady = false;

    @Override
    public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager.class)
                    .createNotificationChannel(new NotificationChannel("saja", "SAJA", NotificationManager.IMPORTANCE_LOW));
        }
        Notification note = buildNote("Loading speech model");
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, note, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } else {
            startForeground(1, note);
        }
        tts = new TextToSpeech(this, this);
        StorageService.unpack(this, "model", "model",
                (m) -> {
                    model = m;
                    startRecognition();
                },
                (e) -> showText("Speech model failed: " + e.getMessage()));
    }

    private void startRecognition() {
        try {
            Recognizer recognizer = new Recognizer(model, 16000.0f);
            speechService = new SpeechService(recognizer, 16000.0f);
            speechService.startListening(this);
            showText("Listening for Jarvis");
        } catch (IOException e) {
            showText("Could not start listening: " + e.getMessage());
        }
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

    private String answerFor(String command) {
        if (command.isEmpty()) return "Yes, I am here.";
        if (command.contains("time")) {
            return "It is " + new SimpleDateFormat("h mm a", Locale.US).format(new Date()) + ".";
        }
        if (command.contains("date") || command.contains("day")) {
            return "Today is " + new SimpleDateFormat("EEEE, MMMM d", Locale.US).format(new Date()) + ".";
        }
        return "I heard " + command + ". I cannot answer that yet.";
    }

    private void handleText(String heard) {
        String lower = heard.toLowerCase(Locale.US).trim();
        for (String w : WAKE_WORDS) {
            int at = lower.indexOf(w);
            if (at >= 0) {
                String command = lower.substring(at + w.length()).trim();
                String reply = answerFor(command);
                showText(reply);
                speak(reply);
                return;
            }
        }
    }

    private void speak(String text) {
        if (ttsReady) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "saja");
    }

    @Override
    public void onPartialResult(String hypothesis) {}

    @Override
    public void onResult(String hypothesis) {
        handleJson(hypothesis);
    }

    @Override
    public void onFinalResult(String hypothesis) {
        handleJson(hypothesis);
    }

    private void handleJson(String json) {
        try {
            String text = new JSONObject(json).optString("text", "");
            if (!text.isEmpty()) handleText(text);
        } catch (Exception e) {
            // ignore results that are not valid JSON
        }
    }

    @Override
    public void onError(Exception e) {
        showText("Listening error: " + e.getMessage());
    }

    @Override
    public void onTimeout() {}

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            tts.setLanguage(Locale.US);
            ttsReady = true;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (speechService != null) {
            speechService.stop();
            speechService.shutdown();
        }
        if (tts != null) tts.shutdown();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
    

    
