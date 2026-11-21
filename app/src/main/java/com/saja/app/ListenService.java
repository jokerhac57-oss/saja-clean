package com.saja.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.hardware.camera2.CameraManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.IBinder;
import android.speech.tts.TextToSpeech;

import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;
import org.vosk.android.StorageService;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class ListenService extends Service implements RecognitionListener, TextToSpeech.OnInitListener {
    private static final String[] WAKE_WORDS = {"jarvis", "jarvus", "javis"};
    private static final String[] WORDS = {"zero", "one", "two", "three", "four", "five", "six", "seven",
            "eight", "nine", "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen",
            "seventeen", "eighteen", "nineteen", "twenty"};
    private final Random random = new Random();
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

    private void speak(String text) {
        if (ttsReady) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "saja");
    }

    private File notesFile() {
        return new File(getFilesDir(), "notes.txt");
    }

    private List<String> readAllLines() {
        List<String> lines = new ArrayList<>();
        File f = notesFile();
        if (!f.exists()) return lines;
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (!line.trim().isEmpty()) lines.add(line);
            }
        } catch (IOException e) {
            // return whatever was read
        }
        return lines;
    }

    private String saveNote(String text) {
        if (text.isEmpty()) return "Say note, then what to save.";
        try (FileWriter w = new FileWriter(notesFile(), true)) {
            w.write(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()) + "|" + text + "\n");
            return "Note saved.";
        } catch (IOException e) {
            return "I could not save the note.";
        }
    }

    private String readNotes() {
        List<String> lines = readAllLines();
        if (lines.isEmpty()) return "You have no notes yet.";
        StringBuilder sb = new StringBuilder("Your last notes are. ");
        for (int i = Math.max(0, lines.size() - 5); i < lines.size(); i++) {
            String[] parts = lines.get(i).split("\\|", 2);
            sb.append(parts.length == 2 ? parts[1] : lines.get(i)).append(". ");
        }
        return sb.toString();
    }

    private String deleteLastNote() {
        List<String> lines = readAllLines();
        if (lines.isEmpty()) return "There are no notes to delete.";
        lines.remove(lines.size() - 1);
        try (PrintWriter w = new PrintWriter(new FileWriter(notesFile()))) {
            for (String l : lines) w.println(l);
        } catch (IOException e) {
            return "I could not delete the note.";
        }
        return "Last note deleted.";
    }

    private String batteryText() {
        Intent b = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (b == null) return "Battery level is unknown.";
        int level = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = b.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        int pct = level * 100 / scale;
        int status = b.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL;
        return "Battery is " + pct + " percent" + (charging ? ", charging." : ".");
    }

    private String timeText() {
        return "It is " + new SimpleDateFormat("h:mm a", Locale.US).format(new Date()) + ".";
    }

    private String dateText() {
        return "Today is " + new SimpleDateFormat("EEEE, MMMM d", Locale.US).format(new Date()) + ".";
    }

    private String setTorch(boolean on) {
        try {
            CameraManager cm = (CameraManager) getSystemService(CAMERA_SERVICE);
            cm.setTorchMode(cm.getCameraIdList()[0], on);
            return on ? "Flashlight on." : "Flashlight off.";
        } catch (Exception e) {
            return "I could not control the flashlight.";
        }
    }

    private List<Integer> numbersIn(String text) {
        List<Integer> out = new ArrayList<>();
        for (String tok : text.split("\\s+")) {
            if (tok.matches("\\d+")) {
                out.add(Integer.parseInt(tok));
            } else {
                for (int i = 0; i < WORDS.length; i++) {
                    if (WORDS[i].equals(tok)) {
                        out.add(i);
                        break;
                    }
                }
            }
        }
        return out;
    }

    private String answerFor(String command) {
        if (command.isEmpty()) return "Yes, I am here.";
        if (command.startsWith("delete") && command.contains("note")) return deleteLastNote();
        if (command.equals("notes") || command.equals("note")) return readNotes();
        if (command.startsWith("note ")) return saveNote(command.substring(5).trim());
        if (command.contains("status")) return timeText() + " " + batteryText();
        if (command.contains("battery")) return batteryText();
        if (command.contains("time")) return timeText();
        if (command.contains("date") || command.contains("day")) return dateText();
        if (command.contains("flashlight off")) return setTorch(false);
        if (command.contains("flashlight on") || command.contains("flush it on")) return setTorch(true);
        if (command.startsWith("flip") || command.contains("coin")) {
            return random.nextBoolean() ? "Heads." : "Tails.";
        }
        if (command.startsWith("roll") || command.contains("dice")) {
            return "You rolled " + (random.nextInt(6) + 1) + ".";
        }
        if (command.startsWith("pick")) {
            List<Integer> n = numbersIn(command);
            if (n.size() < 2) return "Say pick, then from a number to a number.";
            int lo = Math.min(n.get(0), n.get(1));
            int hi = Math.max(n.get(0), n.get(1));
            return "I pick " + (lo + random.nextInt(hi - lo + 1)) + ".";
        }
        if (command.startsWith("spell ")) {
            StringBuilder sb = new StringBuilder();
            for (char c : command.substring(6).toUpperCase(Locale.US).toCharArray()) {
                if (Character.isLetter(c)) sb.append(c).append(' ');
            }
            return sb.length() == 0 ? "Say spell, then a word." : sb.toString().trim() + ".";
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

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            tts.setLanguage(Locale.US);
            ttsReady = true;
        }
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
