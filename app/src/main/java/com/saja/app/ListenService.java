package com.saja.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.hardware.camera2.CameraManager;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.AlarmClock;
import android.speech.tts.TextToSpeech;

import org.json.JSONArray;
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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ListenService extends Service implements RecognitionListener, TextToSpeech.OnInitListener {
    private static final String[] WAKE_WORDS = {"jarvis", "jarvus", "javis"};
    private static final String MODEL = "nvidia/nemotron-3-ultra-550b-a55b:free";
    private static final String[] WORDS = {"zero", "one", "two", "three", "four", "five", "six", "seven",
            "eight", "nine", "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen",
            "seventeen", "eighteen", "nineteen", "twenty"};
    private static final Map<String, String> APPS = new HashMap<>();

    static {
        APPS.put("whatsapp", "com.whatsapp");
        APPS.put("youtube", "com.google.android.youtube");
        APPS.put("chrome", "com.android.chrome");
        APPS.put("gmail", "com.google.android.gm");
        APPS.put("maps", "com.google.android.apps.maps");
        APPS.put("photos", "com.google.android.apps.photos");
        APPS.put("settings", "com.android.settings");
        APPS.put("calculator", "com.android.calculator2");
        APPS.put("clock", "com.android.deskclock");
        APPS.put("camera", "com.android.camera");
    }

    private final Random random = new Random();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<String> reminderLines = new ArrayList<>();
    private Model model;
    private SpeechService speechService;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private String pendingPrompt = null;
    private String pendingLabel = null;
    private long pendingSince = 0;

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
        if (!ttsReady) return;
        int i = 0;
        boolean first = true;
        while (i < text.length()) {
            int end = Math.min(text.length(), i + 3000);
            tts.speak(text.substring(i, end), first ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD, null, "saja" + i);
            first = false;
            i = end;
        }
    }

    private void reply(String text) {
        showText(text);
        speak(text);
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

    private String clockAt(long ms) {
        return new SimpleDateFormat("h:mm a", Locale.US).format(new Date(System.currentTimeMillis() + ms));
    }

    private void later(long ms, String text, String at) {
        final String entry = at + " " + text;
        reminderLines.add(entry);
        handler.postDelayed(() -> {
            reminderLines.remove(entry);
            reply(text);
        }, ms);
    }

    private String remindCmd(String command) {
        Matcher m = Pattern.compile("remind (?:me )?in (\\S+) minutes?(?: to)? (.+)").matcher(command);
        if (!m.find()) return "Say remind in, then a number of minutes, then to, then the reminder.";
        List<Integer> n = numbersIn(m.group(1));
        if (n.isEmpty()) return "I did not catch the minutes.";
        long ms = n.get(0) * 60000L;
        String at = clockAt(ms);
        later(ms, "Reminder: " + m.group(2).trim(), at);
        return "Reminder set for " + at + ".";
    }

    private String timerCmd(String command) {
        Matcher m = Pattern.compile("timer (\\S+) minutes?").matcher(command);
        if (!m.find()) return "Say timer, then a number of minutes.";
        List<Integer> n = numbersIn(m.group(1));
        if (n.isEmpty()) return "I did not catch the minutes.";
        long ms = n.get(0) * 60000L;
        later(ms, "Timer finished.", clockAt(ms));
        return "Timer set for " + n.get(0) + " minutes.";
    }

    private String readReminders() {
        if (reminderLines.isEmpty()) return "You have no reminders.";
        return "Reminders. " + String.join(". ", reminderLines) + ".";
    }

    private String alarmCmd(String command) {
        List<Integer> n = numbersIn(command);
        if (n.isEmpty()) return "Say the hour, like wake me at seven.";
        int hour = n.get(0);
        if (hour > 23) return "That hour is not valid.";
        if (command.contains("pm") && hour < 12) hour += 12;
        Intent i = new Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_HOUR, hour)
                .putExtra(AlarmClock.EXTRA_MINUTES, 0)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
            return "Alarm set for " + hour + " o'clock.";
        } catch (ActivityNotFoundException e) {
            return "No clock app found to set the alarm.";
        }
    }

    private String vibrateCmd() {
        Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (v != null) v.vibrate(VibrationEffect.createOneShot(1500, VibrationEffect.DEFAULT_AMPLITUDE));
        return "Vibrating.";
    }

    private String openCmd(String target) {
        if (target.isEmpty()) return "Say open, then an app or a website.";
        String pkg = APPS.get(target);
        if (pkg != null) {
            Intent launch = getPackageManager().getLaunchIntentForPackage(pkg);
            if (launch == null) return target + " is not installed.";
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(launch);
            return "Opening " + target + ".";
        }
        String url = "https://" + target.replace(" ", "") + (target.contains(".") ? "" : ".com");
        Intent web = new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(web);
            return "Opening " + target + ".";
        } catch (ActivityNotFoundException e) {
            return "No browser found.";
        }
    }

    private void askApproval(String prompt, String label) {
        pendingPrompt = prompt;
        pendingLabel = label;
        pendingSince = System.currentTimeMillis();
        reply("Send this " + label + " to the model? Say yes or no.");
    }

    private String readKey() {
        File f = new File(getFilesDir(), "openrouter.key");
        if (!f.exists()) return "";
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            String line = r.readLine();
            return line == null ? "" : line.trim();
        } catch (IOException e) {
            return "";
        }
    }

    private String readStream(InputStream is) throws IOException {
        if (is == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append("\n");
        }
        return sb.toString();
    }

    private void runModel(String prompt) {
        String key = readKey();
        if (key.isEmpty()) {
            reply("Save your OpenRouter key in the SAJA app first.");
            return;
        }
        showText("Asking the model...");
        new Thread(() -> {
            String answer;
            try {
                JSONObject body = new JSONObject();
                body.put("model", MODEL);
                body.put("max_tokens", 600);
                JSONObject message = new JSONObject();
                message.put("role", "user");
                message.put("content", prompt);
                body.put("messages", new JSONArray().put(message));

                HttpURLConnection c = (HttpURLConnection) new URL("https://openrouter.ai/api/v1/chat/completions").openConnection();
                c.setRequestMethod("POST");
                c.setRequestProperty("Content-Type", "application/json");
                c.setRequestProperty("Authorization", "Bearer " + key);
                c.setConnectTimeout(20000);
                c.setReadTimeout(150000);
                c.setDoOutput(true);
                try (OutputStream os = c.getOutputStream()) {
                    os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }
                int code = c.getResponseCode();
                String raw = readStream(code >= 400 ? c.getErrorStream() : c.getInputStream());
                if (code >= 400) {
                    answer = "Model error " + code + ". " + raw.substring(0, Math.min(200, raw.length()));
                } else {
                    answer = new JSONObject(raw).getJSONArray("choices").getJSONObject(0)
                            .getJSONObject("message").getString("content");
                }
            } catch (Exception e) {
                answer = "The model request failed: " + e.getMessage();
            }
            answer = answer.replace("*", "").replace("#", "").trim();
            reply(answer.isEmpty() ? "No reply." : answer);
        }).start();
    }

    private String answerFor(String command) {
        if (command.isEmpty()) return "Yes, I am here.";
        if (command.startsWith("ask ") || command.startsWith("question ")) {
            String q = command.replaceFirst("^(ask|question) ", "").trim();
            if (q.isEmpty()) return "Say ask, then your question.";
            askApproval(q, "question");
            return null;
        }
        if (command.startsWith("translate")) {
            Matcher tm = Pattern.compile("translate (.+?) to (\\w+)").matcher(command);
            if (!tm.find()) return "Say translate, then the words, then to, then the language.";
            askApproval("Translate this into " + tm.group(2) + ". Reply with only the translation: " + tm.group(1), "translation");
            return null;
        }
        if (command.startsWith("define ")) {
            askApproval("Define this word in two short plain sentences: " + command.substring(7).trim(), "definition");
            return null;
        }
        if (command.contains("joke")) {
            askApproval("Tell one short clean joke in two lines, plain text.", "joke");
            return null;
        }
        if (command.contains("headlines")) {
            askApproval("List three major news headlines from today in short plain sentences. If you are not sure they are current, say so first.", "headlines request");
            return null;
        }
        if (command.startsWith("remind ")) return remindCmd(command);
        if (command.equals("reminders") || command.equals("reminder")) return readReminders();
        if (command.startsWith("timer")) return timerCmd(command);
        if (command.contains("wake me") || command.startsWith("alarm")) return alarmCmd(command);
        if (command.equals("vibrate") || command.contains("find my phone") || command.contains("find phone")) {
            return vibrateCmd();
        }
        if (command.startsWith("open ")) return openCmd(command.substring(5).trim());
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

        if (pendingPrompt != null) {
            long age = System.currentTimeMillis() - pendingSince;
            if (age > 30000) {
                pendingPrompt = null;
                pendingLabel = null;
            } else if (age > 3000) {
                List<String> tokens = Arrays.asList(lower.split("\\s+"));
                if (tokens.contains("yes") || tokens.contains("yeah") || tokens.contains("sure")) {
                    String prompt = pendingPrompt;
                    pendingPrompt = null;
                    pendingLabel = null;
                    runModel(prompt);
                    return;
                }
                if (tokens.contains("no") || tokens.contains("nope") || tokens.contains("cancel")) {
                    pendingPrompt = null;
                    pendingLabel = null;
                    reply("Cancelled.");
                    return;
                }
            }
        }

        for (String w : WAKE_WORDS) {
            int at = lower.indexOf(w);
            if (at >= 0) {
                pendingPrompt = null;
                pendingLabel = null;
                String command = lower.substring(at + w.length()).trim();
                String result = answerFor(command);
                if (result != null) reply(result);
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
        handler.removeCallbacksAndMessages(null);
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
