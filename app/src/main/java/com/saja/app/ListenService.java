package com.saja.app;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.database.Cursor;
import android.hardware.camera2.CameraManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.AlarmClock;
import android.provider.CalendarContract;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.util.Base64;
import android.view.KeyEvent;

import org.json.JSONArray;
import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;
import org.vosk.android.StorageService;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ListenService extends Service implements RecognitionListener, TextToSpeech.OnInitListener {
    public static ListenService instance;
    private static final String[] WAKE_WORDS = {"jarvis", "jarvus", "javis"};
    private static final String[] FREE_MODELS = {
            "nvidia/nemotron-3-ultra-550b-a55b:free",
            "openai/gpt-oss-20b:free",
            "meta-llama/llama-3.3-70b-instruct:free"
    };
    private static final String VISION_MODEL = "qwen/qwen3.8-27b";
    private static final String NO_CONTACTS = "#nocontacts";
    private static final String HOME_FILE = "home_wifi.txt";
    private static final String[] WORDS = {"zero", "one", "two", "three", "four", "five", "six", "seven",
            "eight", "nine", "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen",
            "seventeen", "eighteen", "nineteen", "twenty"};
    private static final String[] SAFE_STEPS = {"time", "date", "battery", "status", "flashlight on",
            "flashlight off", "flush it on", "vibrate", "open ", "remind ", "timer ", "notes", "reminders",
            "weather", "music ", "volume ", "directions to ", "calendar", "notifications", "focus mode"};
    private static final Map<String, Integer> WORD_VALUES = new HashMap<>();
    private static final Map<String, String> APPS = new HashMap<>();

    static {
        for (int i = 0; i < WORDS.length; i++) WORD_VALUES.put(WORDS[i], i);
        WORD_VALUES.put("thirty", 30);
        WORD_VALUES.put("forty", 40);
        WORD_VALUES.put("fifty", 50);
        WORD_VALUES.put("sixty", 60);

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
    private Runnable pendingYes = null;
    private long pendingSince = 0;
    private boolean wasHome = false;
    private boolean batteryAlerted = false;
    private String lastBriefingDay = "";
    private String lastBillDay = "";
    private Runnable waterTask = null;

    private final Runnable everyMinute = new Runnable() {
        @Override
        public void run() {
            checkHome();
            checkBriefing();
            checkBattery();
            checkBills();
            handler.postDelayed(this, 60000);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
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
        handler.postDelayed(everyMinute, 60000);
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

    private void replyLater(String text) {
        handler.post(() -> reply(text));
    }

    private String now() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
    }

    private File notesFile() {
        return new File(getFilesDir(), "notes.txt");
    }

    private File memoryFile() {
        return new File(getFilesDir(), "memory.txt");
    }

    private File routinesFile() {
        return new File(getFilesDir(), "routines.txt");
    }

    private String readFileText(String name) {
        File f = new File(getFilesDir(), name);
        if (!f.exists()) return "";
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            String line = r.readLine();
            return line == null ? "" : line.trim();
        } catch (IOException e) {
            return "";
        }
    }

    private void writeFileText(String name, String value) throws IOException {
        try (FileWriter w = new FileWriter(new File(getFilesDir(), name))) {
            w.write(value);
        }
    }

    private List<String> readLines(File f) {
        List<String> lines = new ArrayList<>();
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

    private void writeLines(File f, List<String> lines) throws IOException {
        try (PrintWriter w = new PrintWriter(new FileWriter(f))) {
            for (String l : lines) w.println(l);
        }
    }

    private void appendLine(File f, String line) throws IOException {
        try (FileWriter w = new FileWriter(f, true)) {
            w.write(line + "\n");
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

    private String httpGet(String url, String bearer) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestProperty("User-Agent", "curl/8.0");
        if (bearer != null) {
            c.setRequestProperty("Authorization", "Bearer " + bearer);
            c.setRequestProperty("Accept", "application/vnd.github+json");
        }
        c.setConnectTimeout(20000);
        c.setReadTimeout(30000);
        int code = c.getResponseCode();
        if (code >= 400) throw new IOException("the server returned " + code);
        return readStream(c.getInputStream());
    }

    private String httpGetAgent(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestProperty("User-Agent", "SAJA-Android/1.0 (personal assistant)");
        c.setConnectTimeout(20000);
        c.setReadTimeout(30000);
        int code = c.getResponseCode();
        if (code >= 400) throw new IOException("the site returned " + code);
        return readStream(c.getInputStream());
    }

    private String saveNote(String text) {
        if (text.isEmpty()) return "Say note, then what to save.";
        try {
            appendLine(notesFile(), now() + "|" + text);
            return "Note saved.";
        } catch (IOException e) {
            return "I could not save the note.";
        }
    }

    private String readNotes() {
        List<String> lines = readLines(notesFile());
        if (lines.isEmpty()) return "You have no notes yet.";
        StringBuilder sb = new StringBuilder("Your last notes are. ");
        for (int i = Math.max(0, lines.size() - 5); i < lines.size(); i++) {
            String[] parts = lines.get(i).split("\\|", 2);
            sb.append(parts.length == 2 ? parts[1] : lines.get(i)).append(". ");
        }
        return sb.toString();
    }

    private String deleteLastNote() {
        List<String> lines = readLines(notesFile());
        if (lines.isEmpty()) return "There are no notes to delete.";
        lines.remove(lines.size() - 1);
        try {
            writeLines(notesFile(), lines);
        } catch (IOException e) {
            return "I could not delete the note.";
        }
        return "Last note deleted.";
    }

    private String lastVoiceNote() {
        String found = null;
        for (String l : readLines(notesFile())) {
            int at = l.indexOf("voice note: ");
            if (at >= 0) found = l.substring(at + 12);
        }
        return found;
    }

    private String notesContext() {
        StringBuilder all = new StringBuilder();
        for (String l : readLines(notesFile())) {
            String[] parts = l.split("\\|", 2);
            all.append(parts.length == 2 ? parts[1] : l).append("\n");
        }
        String text = all.toString();
        return text.length() > 3000 ? text.substring(text.length() - 3000) : text;
    }

    private String readDocument() throws IOException {
        String raw = readFileText("doc_uri.txt");
        if (raw.isEmpty()) return null;
        Uri uri = Uri.parse(raw);
        StringBuilder sb = new StringBuilder();
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) return null;
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = r.readLine()) != null && sb.length() < 6000) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }

    private void askDocument(String question) {
        askApproval("Send your document to the model? This uses OpenRouter credits.", () -> {
            try {
                String doc = readDocument();
                if (doc == null || doc.trim().isEmpty()) {
                    replyLater("I could not read the document. Pick one with PICK DOCUMENT in the SAJA app.");
                    return;
                }
                runModel("Answer using only this document. If the answer is not in it, say so.\n\nDocument:\n"
                        + doc + "\n\nQuestion: " + question);
            } catch (IOException e) {
                replyLater("I could not read the document.");
            }
        });
    }

    private void runResearch(String topic) {
        handler.post(() -> showText("Searching Wikipedia..."));
        new Thread(() -> {
            try {
                String q = URLEncoder.encode(topic, "UTF-8").replace("+", "%20");
                String searchRaw = httpGetAgent("https://en.wikipedia.org/w/api.php?action=query&list=search&srlimit=3&format=json&srsearch=" + q);
                JSONArray hits = new JSONObject(searchRaw).getJSONObject("query").getJSONArray("search");
                if (hits.length() == 0) {
                    replyLater("I found no Wikipedia pages for " + topic + ".");
                    return;
                }
                StringBuilder context = new StringBuilder();
                StringBuilder links = new StringBuilder();
                StringBuilder titles = new StringBuilder();
                for (int i = 0; i < hits.length(); i++) {
                    String title = hits.getJSONObject(i).getString("title");
                    String t = URLEncoder.encode(title, "UTF-8").replace("+", "%20");
                    String extRaw = httpGetAgent("https://en.wikipedia.org/w/api.php?action=query&prop=extracts&exintro=1&explaintext=1&format=json&titles=" + t);
                    JSONObject pages = new JSONObject(extRaw).getJSONObject("query").getJSONObject("pages");
                    String extract = "";
                    Iterator<String> keys = pages.keys();
                    if (keys.hasNext()) extract = pages.getJSONObject(keys.next()).optString("extract", "");
                    if (extract.length() > 1500) extract = extract.substring(0, 1500);
                    String url = "https://en.wikipedia.org/wiki/" + title.replace(' ', '_');
                    context.append("[").append(i + 1).append("] ").append(title).append(": ").append(extract).append("\n\n");
                    links.append(title).append(": ").append(url).append("\n");
                    if (i > 0) titles.append(", ");
                    titles.append(title);
                }
                String prompt = "Answer the question about \"" + topic + "\" using only these sources. "
                        + "Be brief, three to four sentences. Mention source numbers like [1] where useful.\n\nSources:\n" + context;
                String answer = callModel(prompt).replace("*", "").replace("#", "").trim();
                if (answer.isEmpty()) answer = "No reply.";
                final String shown = answer + "\n\nSources:\n" + links;
                final String spoken = answer + " Sources: " + titles + ".";
                handler.post(() -> {
                    showText(shown);
                    speak(spoken);
                });
                try {
                    ChatHistory.save(this, "research " + topic, answer);
                } catch (IOException e) {
                    // saving history is optional
                }
            } catch (Exception e) {
                replyLater("Research failed: " + e.getMessage());
            }
        }).start();
    }

    private String rememberFact(String fact) {
        if (fact.isEmpty()) return "Say remember, then what to remember.";
        try {
            appendLine(memoryFile(), now() + "|" + fact);
            return "Got it. I will remember that.";
        } catch (IOException e) {
            return "I could not save that.";
        }
    }

    private String readMemory() {
        List<String> lines = readLines(memoryFile());
        if (lines.isEmpty()) return "I do not remember anything yet.";
        StringBuilder sb = new StringBuilder("I remember. ");
        for (int i = Math.max(0, lines.size() - 10); i < lines.size(); i++) {
            String[] parts = lines.get(i).split("\\|", 2);
            sb.append(parts.length == 2 ? parts[1] : lines.get(i)).append(". ");
        }
        return sb.toString();
    }

    private String memoryContext() {
        List<String> lines = readLines(memoryFile());
        if (lines.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("Facts the user asked you to remember: ");
        for (String line : lines) {
            String[] parts = line.split("\\|", 2);
            sb.append(parts.length == 2 ? parts[1] : line).append("; ");
        }
        return sb.toString().trim() + "\n\n";
    }

    private void clearMemory() {
        try {
            writeLines(memoryFile(), new ArrayList<>());
            reply("Memory cleared.");
        } catch (IOException e) {
            reply("I could not clear memory.");
        }
    }

    private boolean isSafeStep(String step) {
        if (step.contains("ask ") || step.startsWith("routine") || step.startsWith("make ")
                || step.startsWith("delete ") || step.startsWith("read ") || step.startsWith("tap ")
                || step.startsWith("type ") || step.startsWith("tool ") || step.startsWith("look")
                || step.startsWith("automate") || step.startsWith("draft ") || step.startsWith("improve ")
                || step.startsWith("summarize ") || step.startsWith("research ") || step.startsWith("document ")) {
            return false;
        }
        for (String s : SAFE_STEPS) {
            if (step.startsWith(s)) return true;
        }
        return false;
    }

    private String makeRoutine(String name, String stepsText) {
        if (name.isEmpty() || name.contains("|")) return "Say make routine, then a name, then do, then the steps.";
        List<String> steps = new ArrayList<>();
        for (String s : stepsText.split(" then ")) {
            String t = s.trim();
            if (t.isEmpty()) continue;
            if (!isSafeStep(t)) return "The step " + t + " cannot be used in a routine.";
            steps.add(t);
        }
        if (steps.isEmpty()) return "Say at least one step.";
        if (steps.size() > 6) return "A routine can have up to six steps.";
        final String line = name + "|" + String.join(" || ", steps);
        final String routineName = name;
        askApproval("Save the routine " + name + " with " + steps.size() + " steps?", () -> {
            try {
                List<String> all = readLines(routinesFile());
                all.removeIf(l -> l.startsWith(routineName + "|"));
                all.add(line);
                writeLines(routinesFile(), all);
                reply("Routine " + routineName + " saved.");
            } catch (IOException e) {
                reply("I could not save the routine.");
            }
        });
        return null;
    }

    private String deleteRoutine(String name) {
        askApproval("Delete the routine " + name + "?", () -> {
            try {
                List<String> all = readLines(routinesFile());
                all.removeIf(l -> l.startsWith(name + "|"));
                writeLines(routinesFile(), all);
                reply("Routine " + name + " deleted.");
            } catch (IOException e) {
                reply("I could not delete the routine.");
            }
        });
        return null;
    }

    private String listRoutines() {
        List<String> all = readLines(routinesFile());
        if (all.isEmpty()) return "You have no routines yet.";
        StringBuilder sb = new StringBuilder("Your routines are. ");
        for (String l : all) sb.append(l.split("\\|", 2)[0]).append(". ");
        return sb.toString();
    }

    private String findRoutineSteps(String name) {
        for (String l : readLines(routinesFile())) {
            String[] parts = l.split("\\|", 2);
            if (parts.length == 2 && parts[0].equals(name)) return parts[1];
        }
        return null;
    }

    private void runRoutine(String name, String[] steps) {
        runStep(name, steps, 0);
    }

    private void runStep(String name, String[] steps, int index) {
        if (index >= steps.length) {
            replyLater("Routine " + name + " finished.");
            return;
        }
        String result = answerFor(steps[index].trim());
        if (result != null) reply(result);
        handler.postDelayed(() -> runStep(name, steps, index + 1), 2500);
    }

    private void runAutomation(String plan) {
        List<String> steps = new ArrayList<>();
        for (String s : plan.split(" then ")) {
            String t = s.trim();
            if (!t.isEmpty()) steps.add(t);
        }
        if (steps.isEmpty()) {
            reply("Say automate, then the steps, separated by then.");
            return;
        }
        if (steps.size() > 8) {
            reply("An automation can have up to eight steps.");
            return;
        }
        for (String s : steps) {
            if (!(s.startsWith("open ") || s.startsWith("tap ") || s.startsWith("wait ") || s.startsWith("type "))) {
                reply("I can only open apps, tap buttons, type text, and wait. The step " + s + " is not allowed.");
                return;
            }
        }
        runAutoStep(steps, 0);
    }

    private void runAutoStep(List<String> steps, int index) {
        if (index >= steps.size()) {
            replyLater("Automation finished.");
            return;
        }
        final String step = steps.get(index);
        final int next = index + 1;
        askApproval("Step " + next + " of " + steps.size() + ": " + step + ".", () -> {
            if (step.startsWith("wait ")) {
                List<Integer> n = numbersIn(step);
                int seconds = n.isEmpty() ? 2 : Math.min(30, Math.max(1, n.get(0)));
                handler.postDelayed(() -> runAutoStep(steps, next), seconds * 1000L);
                return;
            }
            if (step.startsWith("open ")) {
                reply(openCmd(step.substring(5).trim()));
            } else if (step.startsWith("tap ")) {
                reply(ScreenHelper.clickText(step.substring(4).trim()));
            } else if (step.startsWith("type ")) {
                reply(ScreenHelper.typeText(step.substring(5).trim()));
            }
            handler.postDelayed(() -> runAutoStep(steps, next), 2500);
        });
    }

    private String currentSsid() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return "";
        }
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            WifiInfo info = wm.getConnectionInfo();
            String s = info == null || info.getSSID() == null ? "" : info.getSSID();
            s = s.replace("\"", "");
            return s.equals("<unknown ssid>") ? "" : s;
        } catch (Exception e) {
            return "";
        }
    }

    private void checkHome() {
        String home = readFileText(HOME_FILE);
        if (home.isEmpty()) {
            wasHome = false;
            return;
        }
        String current = currentSsid();
        boolean isHome = !current.isEmpty() && current.equals(home);
        if (isHome && !wasHome) {
            String steps = findRoutineSteps("home");
            if (steps != null) runRoutine("home", steps.split(" \\|\\| "));
        }
        wasHome = isHome;
    }

    private String setHomeWifi() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return "Allow location in the SAJA app first, so I can read the Wi-Fi name.";
        }
        final String ssid = currentSsid();
        if (ssid.isEmpty()) return "Connect to your home Wi-Fi first, then say set home wifi.";
        askApproval("Use the Wi-Fi " + ssid + " as your home Wi-Fi?", () -> {
            try {
                writeFileText(HOME_FILE, ssid);
                reply("Home Wi-Fi saved.");
            } catch (IOException e) {
                reply("I could not save the home Wi-Fi.");
            }
        });
        return null;
    }

    private String focusMode(boolean on) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (!nm.isNotificationPolicyAccessGranted()) {
            try {
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (ActivityNotFoundException e) {
                return "I could not open Do Not Disturb settings.";
            }
            return "Allow SAJA in Do Not Disturb access, then say focus mode again.";
        }
        nm.setInterruptionFilter(on
                ? NotificationManager.INTERRUPTION_FILTER_PRIORITY
                : NotificationManager.INTERRUPTION_FILTER_ALL);
        return on ? "Focus mode is on. Only priority alerts will ring." : "Focus mode is off.";
    }

    private String startFocus(final int minutes) {
        handler.postDelayed(() -> {
            reply("Focus session done. Take a short break.");
            try {
                Extras.focusDone(this);
            } catch (IOException e) {
                // counting is optional
            }
        }, minutes * 60000L);
        return "Focus session of " + minutes + " minutes started. I will tell you when it ends.";
    }

    private void startWater(final int hours) {
        stopWater();
        final Runnable[] holder = new Runnable[1];
        holder[0] = () -> {
            reply("Time to drink some water.");
            handler.postDelayed(holder[0], hours * 3600000L);
        };
        waterTask = holder[0];
        handler.postDelayed(waterTask, hours * 3600000L);
    }

    private void stopWater() {
        if (waterTask != null) {
            handler.removeCallbacks(waterTask);
            waterTask = null;
        }
    }

    private void checkBills() {
        String nowHm = new SimpleDateFormat("HH:mm", Locale.US).format(new Date());
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        if (!nowHm.equals("09:00") || today.equals(lastBillDay)) return;
        lastBillDay = today;
        Calendar cal = Calendar.getInstance();
        int todayDay = cal.get(Calendar.DAY_OF_MONTH);
        cal.add(Calendar.DAY_OF_MONTH, 1);
        int tomorrowDay = cal.get(Calendar.DAY_OF_MONTH);
        StringBuilder sb = new StringBuilder();
        for (String l : readLines(new File(getFilesDir(), "bills.txt"))) {
            String[] p = l.split("\\|", 3);
            if (p.length < 3) continue;
            try {
                int day = Integer.parseInt(p[0].trim());
                if (day == todayDay) {
                    sb.append(p[2]).append(" for ").append(p[1]).append(" is due today. ");
                } else if (day == tomorrowDay) {
                    sb.append(p[2]).append(" for ").append(p[1]).append(" is due tomorrow. ");
                }
            } catch (NumberFormatException e) {
                // skip bad lines
            }
        }
        if (sb.length() > 0) reply("Bill reminder. " + sb);
    }

    private void checkBattery() {
        String raw = readFileText("battery_alert.txt");
        if (raw.isEmpty()) {
            batteryAlerted = false;
            return;
        }
        int threshold;
        try {
            threshold = Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return;
        }
        Intent b = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (b == null) return;
        int level = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = b.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        int pct = level * 100 / scale;
        int status = b.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL;
        if (charging || pct > threshold + 5) {
            batteryAlerted = false;
            return;
        }
        if (pct <= threshold && !batteryAlerted) {
            batteryAlerted = true;
            reply("Battery alert. Battery is " + pct + " percent. Please plug in your charger.");
        }
    }

    private String batteryAlertCmd(String command) {
        if (command.contains("off")) {
            new File(getFilesDir(), "battery_alert.txt").delete();
            batteryAlerted = false;
            return "Battery alert is off.";
        }
        List<Integer> n = numbersIn(command);
        if (n.isEmpty()) return "Say battery alert, then a percent, like battery alert 20.";
        int threshold = n.get(0);
        if (threshold < 5 || threshold > 50) return "Choose a number from 5 to 50.";
        try {
            writeFileText("battery_alert.txt", String.valueOf(threshold));
        } catch (IOException e) {
            return "I could not save the battery alert.";
        }
        batteryAlerted = false;
        return "Battery alert is on at " + threshold + " percent.";
    }

    private void checkBriefing() {
        String time = readFileText("briefing_time.txt");
        if (time.isEmpty()) return;
        String nowHm = new SimpleDateFormat("HH:mm", Locale.US).format(new Date());
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        if (!nowHm.equals(time) || today.equals(lastBriefingDay)) return;
        lastBriefingDay = today;
        reply(morningBriefing());
    }

    private String morningBriefing() {
        return "Good morning. " + timeText() + " " + batteryText() + " " + todayEvents() + " " + readReminders();
    }

    private String setBriefingCmd(String command) {
        List<Integer> n = numbersIn(command);
        if (n.isEmpty()) return "Say briefing at, then the time, like briefing at seven thirty.";
        int hour = n.get(0);
        int minute = n.size() > 1 ? n.get(1) : 0;
        if (command.contains("pm") && hour < 12) hour += 12;
        if (hour > 23 || minute > 59) return "That time is not valid.";
        String time = String.format(Locale.US, "%02d:%02d", hour, minute);
        try {
            writeFileText("briefing_time.txt", time);
        } catch (IOException e) {
            return "I could not save the briefing time.";
        }
        return "Morning briefing is set for " + time + ".";
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
            } else if (WORD_VALUES.containsKey(tok)) {
                out.add(WORD_VALUES.get(tok));
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

    private String navigate(String target) {
        if (target.isEmpty()) return "Say directions to, then the place.";
        Intent nav = new Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + Uri.encode(target)));
        nav.setPackage("com.google.android.apps.maps");
        nav.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(nav);
            return "Starting directions to " + target + ".";
        } catch (ActivityNotFoundException e) {
            Intent geo = new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(target)));
            geo.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                startActivity(geo);
                return "Opening directions to " + target + ".";
            } catch (ActivityNotFoundException e2) {
                return "No maps app found.";
            }
        }
    }

    private String todayEvents() {
        if (checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            return "Allow calendar in the SAJA app first.";
        }
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long begin = cal.getTimeInMillis();
        long end = begin + 86400000L;
        List<String> items = new ArrayList<>();
        try (Cursor c = CalendarContract.Instances.query(getContentResolver(),
                new String[]{CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN}, begin, end)) {
            if (c == null) return "I could not read the calendar.";
            while (c.moveToNext()) {
                String title = c.getString(0);
                long when = c.getLong(1);
                items.add(new SimpleDateFormat("h:mm a", Locale.US).format(new Date(when))
                        + " " + (title == null ? "Untitled" : title));
            }
        } catch (Exception e) {
            return "I could not read the calendar.";
        }
        if (items.isEmpty()) return "You have nothing on your calendar today.";
        return "Today you have. " + String.join(". ", items) + ".";
    }

    private String addEvent(String title) {
        if (title.isEmpty()) return "Say add event, then the title.";
        final String t = title;
        askApproval("Open the calendar to add " + t + "?", () -> {
            Intent i = new Intent(Intent.ACTION_INSERT)
                    .setData(CalendarContract.Events.CONTENT_URI)
                    .putExtra(CalendarContract.Events.TITLE, t)
                    .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, System.currentTimeMillis() + 3600000L)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                startActivity(i);
                reply("The calendar is open. Tap save.");
            } catch (ActivityNotFoundException e) {
                reply("No calendar app found.");
            }
        });
        return null;
    }

    private boolean notificationAccessOn() {
        String enabled = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        return enabled != null && enabled.contains(getPackageName());
    }

    private String readNotifications() {
        if (!notificationAccessOn()) {
            return "Turn on notification access for SAJA first. Open the SAJA app and tap NOTIFICATION ACCESS.";
        }
        List<String> items = new ArrayList<>(NotificationReader.RECENT);
        if (items.isEmpty()) return "You have no new notifications.";
        StringBuilder sb = new StringBuilder("Your recent notifications. ");
        for (int i = Math.max(0, items.size() - 5); i < items.size(); i++) {
            sb.append(items.get(i)).append(". ");
        }
        return sb.toString();
    }

    private void sendMediaKey(int action, int keyCode) {
        Intent i = new Intent(Intent.ACTION_MEDIA_BUTTON);
        i.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(action, keyCode));
        sendOrderedBroadcast(i, null);
    }

    private String setVolume(String direction) {
        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (direction.contains("up")) {
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI);
            return "Volume up.";
        }
        if (direction.contains("down")) {
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);
            return "Volume down.";
        }
        return "Say volume up or volume down.";
    }

    private void weatherAsync(String city) {
        handler.post(() -> showText("Checking the weather..."));
        new Thread(() -> {
            try {
                String place = city.replace(" ", "+");
                String raw = httpGet("https://wttr.in/" + place + "?format=%C,+%t", null);
                replyLater("The weather is " + raw.trim().replace("+", " ") + ".");
            } catch (Exception e) {
                replyLater("I could not get the weather. " + e.getMessage());
            }
        }).start();
    }

    private void githubAsync() {
        String token = readFileText("github.token");
        if (token.isEmpty()) {
            reply("Save a GitHub token in the SAJA app first.");
            return;
        }
        handler.post(() -> showText("Checking GitHub..."));
        new Thread(() -> {
            try {
                String raw = httpGet("https://api.github.com/user/repos?per_page=10&sort=updated", token);
                JSONArray repos = new JSONArray(raw);
                StringBuilder sb = new StringBuilder("Your latest repositories are. ");
                for (int i = 0; i < repos.length(); i++) {
                    sb.append(repos.getJSONObject(i).getString("name")).append(". ");
                }
                replyLater(sb.toString());
            } catch (Exception e) {
                replyLater("I could not read GitHub. " + e.getMessage());
            }
        }).start();
    }

    private String resolveNumber(String target) {
        String compact = target.replaceAll("[\\s-]", "");
        if (compact.matches("\\+?\\d{5,}")) return compact;
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return NO_CONTACTS;
        }
        String want = target.toLowerCase(Locale.US);
        Cursor cursor = getContentResolver().query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.NUMBER},
                null, null, null);
        if (cursor == null) return null;
        try {
            while (cursor.moveToNext()) {
                String display = cursor.getString(0);
                String number = cursor.getString(1);
                if (display != null && number != null && display.toLowerCase(Locale.US).contains(want)) {
                    return number.replaceAll("[^+0-9]", "");
                }
            }
        } finally {
            cursor.close();
        }
        return null;
    }

    private void dial(String number) {
        try {
            startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            reply("The dialer is open. Tap call to dial.");
        } catch (ActivityNotFoundException e) {
            reply("No phone app found.");
        }
    }

    private void sendSms(String number, String body) {
        try {
            Intent i = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + number));
            i.putExtra("sms_body", body);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            reply("Your message is ready. Tap send.");
        } catch (ActivityNotFoundException e) {
            reply("No message app found.");
        }
    }

    private void askApproval(String question, Runnable onYes) {
        pendingYes = onYes;
        pendingSince = System.currentTimeMillis();
        reply(question + " Say yes or no.");
    }

    private String callOpenRouter(String modelName, JSONArray messages) {
        String key = readFileText("openrouter.key");
        if (key.isEmpty()) return "Save your OpenRouter key in the SAJA app first.";
        try {
            JSONObject body = new JSONObject();
            body.put("model", modelName);
            body.put("max_tokens", 600);
            body.put("messages", messages);

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
                return "Model error " + code + ". " + raw.substring(0, Math.min(200, raw.length()));
            }
            return new JSONObject(raw).getJSONArray("choices").getJSONObject(0)
                    .getJSONObject("message").getString("content");
        } catch (Exception e) {
            return "The model request failed: " + e.getMessage();
        }
    }

    private String callModelWithFailover(String prompt) {
        String last = "No reply.";
        for (String m : FREE_MODELS) {
            try {
                JSONArray messages = new JSONArray()
                        .put(new JSONObject().put("role", "user").put("content", prompt));
                String answer = callOpenRouter(m, messages);
                boolean failed = answer.startsWith("Model error") || answer.startsWith("The model request failed");
                if (answer.startsWith("Save your")) return answer;
                if (!failed) {
                    try {
                        ChatHistory.countUse(this);
                    } catch (IOException e) {
                        // counting is optional
                    }
                    return answer;
                }
                last = answer;
            } catch (Exception e) {
                last = "The model request failed: " + e.getMessage();
            }
        }
        return last + " I tried all the free models.";
    }

    private String callModel(String prompt) {
        return callModelWithFailover(prompt);
    }

    private void runModel(String prompt) {
        handler.post(() -> showText("Asking the model..."));
        new Thread(() -> {
            String answer = callModel(prompt);
            answer = answer.replace("*", "").replace("#", "").trim();
            if (answer.isEmpty()) answer = "No reply.";
            try {
                ChatHistory.save(this, prompt, answer);
            } catch (IOException e) {
                // saving history is optional
            }
            replyLater(answer);
        }).start();
    }

    private String fetchText(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) SAJA");
        c.setConnectTimeout(20000);
        c.setReadTimeout(30000);
        int code = c.getResponseCode();
        if (code >= 400) throw new IOException("the site returned " + code);
        StringBuilder raw = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null && raw.length() < 300000) {
                raw.append(line).append('\n');
            }
        }
        String text = raw.toString()
                .replaceAll("(?is)<script.*?</script>", " ")
                .replaceAll("(?is)<style.*?</style>", " ")
                .replaceAll("(?s)<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replaceAll("\\s+", " ")
                .trim();
        return text.length() > 6000 ? text.substring(0, 6000) : text;
    }

    private void summarizeSite(String url) {
        handler.post(() -> showText("Reading the page..."));
        new Thread(() -> {
            String text;
            try {
                text = fetchText(url);
            } catch (Exception e) {
                replyLater("I could not read that page: " + e.getMessage());
                return;
            }
            if (text.isEmpty()) {
                replyLater("That page had no readable text.");
                return;
            }
            String answer = callModel("Summarize this web page in four short sentences:\n\n" + text);
            replyLater(answer.replace("*", "").replace("#", "").trim());
        }).start();
    }

    private String photoQuestion(String question) {
        if (readFileText("photo_uri.txt").isEmpty()) return "Take a photo first with TAKE PHOTO in the SAJA app.";
        askApproval("Send your last photo to the model? This uses OpenRouter credits.", () -> runVision(question));
        return null;
    }

    private void openCameraForLook(String question) {
        Intent i = new Intent(this, MainActivity.class)
                .putExtra("look", question)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
            showText("Take your photo in the camera.");
        } catch (ActivityNotFoundException e) {
            reply("I could not open SAJA to start the camera.");
        }
    }

    private void runVision(String question) {
        handler.post(() -> showText("Looking at your photo..."));
        new Thread(() -> {
            try {
                Uri uri = Uri.parse(readFileText("photo_uri.txt"));
                byte[] bytes;
                try (InputStream in = getContentResolver().openInputStream(uri)) {
                    if (in == null) {
                        replyLater("I could not open the photo.");
                        return;
                    }
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                    bytes = out.toByteArray();
                }
                if (bytes.length == 0) {
                    replyLater("The photo is empty. Take it again with TAKE PHOTO in the SAJA app.");
                    return;
                }
                String b64 = Base64.encodeToString(bytes, Base64.NO_WRAP);
                JSONArray content = new JSONArray();
                content.put(new JSONObject().put("type", "text").put("text", question));
                content.put(new JSONObject().put("type", "image_url")
                        .put("image_url", new JSONObject().put("url", "data:image/jpeg;base64," + b64)));
                JSONArray messages = new JSONArray()
                        .put(new JSONObject().put("role", "user").put("content", content));
                String answer = callOpenRouter(VISION_MODEL, messages);
                replyLater(answer.replace("*", "").replace("#", "").trim());
            } catch (Exception e) {
                replyLater("The photo question failed: " + e.getMessage());
            }
        }).start();
    }

    private void stopForNote() {
        if (speechService != null) {
            speechService.stop();
            speechService.shutdown();
            speechService = null;
        }
    }

    private byte[] captureAudio(int seconds) {
        int rate = 16000;
        int minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        AudioRecord ar = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, rate,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuf * 2);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[minBuf];
        try {
            ar.startRecording();
            long end = System.currentTimeMillis() + seconds * 1000L;
            while (System.currentTimeMillis() < end) {
                int n = ar.read(buf, 0, buf.length);
                if (n > 0) out.write(buf, 0, n);
            }
        } finally {
            ar.stop();
            ar.release();
        }
        return out.toByteArray();
    }

    private String transcribe(byte[] audio) {
        if (model == null || audio.length == 0) return "";
        Recognizer rec = null;
        try {
            rec = new Recognizer(model, 16000.0f);
            rec.acceptWaveForm(audio, audio.length);
            return new JSONObject(rec.getFinalResult()).optString("text", "");
        } catch (Exception e) {
            return "";
        } finally {
            if (rec != null) rec.close();
        }
    }

    private String recordNote(String command) {
        List<Integer> n = numbersIn(command);
        final int seconds = n.isEmpty() ? 30 : Math.min(120, Math.max(5, n.get(0)));
        reply("Recording a voice note for " + seconds + " seconds.");
        stopForNote();
        new Thread(() -> {
            String text;
            try {
                text = transcribe(captureAudio(seconds)).trim();
            } catch (Exception e) {
                text = "";
            }
            final String heard = text;
            handler.post(() -> {
                startRecognition();
                if (heard.isEmpty()) {
                    reply("I did not hear anything in the voice note.");
                    return;
                }
                try {
                    appendLine(notesFile(), now() + "|voice note: " + heard);
                    reply("Voice note saved. " + heard);
                } catch (IOException e) {
                    reply("I could not save the voice note.");
                }
            });
        }).start();
        return null;
    }

    private String answerFor(String command) {
        if (command.isEmpty()) return "Yes, I am here.";

        if (command.startsWith("focus ") && command.contains("minute")) {
            List<Integer> fm = numbersIn(command);
            if (fm.isEmpty()) return "Say focus, then the minutes, like focus 25 minutes.";
            return startFocus(Math.min(120, Math.max(1, fm.get(0))));
        }
        if (command.equals("focus count") || command.equals("focus sessions")) {
            return Extras.focusCount(this);
        }
        if (command.startsWith("remind me to drink water") || command.startsWith("water reminder")) {
            List<Integer> wh = numbersIn(command);
            if (wh.isEmpty()) return "Say water reminder every, then the hours, like every 2 hours.";
            int hours = Math.min(12, Math.max(1, wh.get(0)));
            startWater(hours);
            return "I will remind you to drink water every " + hours
                    + " hours until you say stop water reminders. This resets if SAJA restarts.";
        }
        if (command.equals("stop water reminders") || command.equals("stop water")) {
            stopWater();
            return "Water reminders are off.";
        }
        if (command.startsWith("goal ")) return Extras.goal(this, command.substring(5).trim());
        if (command.equals("goals")) return Extras.goals(this);
        if (command.startsWith("slept ")) return Extras.sleep(this, command.substring(6).trim());
        if (command.equals("sleep week")) return Extras.sleepWeek(this);
        if (command.startsWith("emergency set ")) return Extras.emergencySet(this, command.substring(14).trim());
        if (command.equals("emergency card")) {
            askApproval("Read your emergency card aloud?", () -> reply(Extras.emergencyRead(this)));
            return null;
        }
        if (command.equals("quote")) {
            new Thread(() -> replyLater(Extras.quote())).start();
            return null;
        }
        if (command.startsWith("practice check ")) {
            final String text = command.substring(15).trim();
            askApproval("Send your Spanish to the model to check?", () -> runModel(
                    "Check this Spanish sentence. Say whether it is correct, then give the corrected version in one short line: " + text));
            return null;
        }
        if (command.equals("practice spanish") || command.equals("practice")) {
            askApproval("Get a Spanish practice sentence from the model?", () -> runModel(
                    "Give one short everyday English sentence for a beginner to translate into Spanish. Reply with only the sentence."));
            return null;
        }
        if (command.equals("dinner ideas") || command.equals("meal ideas")) {
            final String items = Extras.shoppingItems(this);
            if (items.isEmpty()) return "Your shopping list is empty. Add items with tool shopping add first.";
            askApproval("Send your shopping list to the model for dinner ideas?", () -> runModel(
                    "Suggest three simple dinner ideas using these items where possible. Keep it short: " + items));
            return null;
        }

        if (command.startsWith("automate ")) {
            runAutomation(command.substring(9).trim());
            return null;
        }

        if (command.startsWith("research ")) {
            final String topic = command.substring(9).trim();
            if (topic.isEmpty()) return "Say research, then the topic.";
            askApproval("Search Wikipedia for " + topic + " and summarize it with sources?", () -> runResearch(topic));
            return null;
        }

        if (command.startsWith("document ") || command.startsWith("ask document ")) {
            String q = command.replaceFirst("^(ask )?document ", "").trim();
            if (q.isEmpty()) return "Say ask document, then your question.";
            askDocument(q);
            return null;
        }
        if (command.equals("summarize document") || command.equals("summarize my document")) {
            askDocument("Summarize this document in five short sentences.");
            return null;
        }

        if (command.startsWith("type ")) {
            final String text = command.substring(5).trim();
            if (text.isEmpty()) return "Say type, then the words to type.";
            askApproval("Type " + text + " into the text box you tapped?", () -> reply(ScreenHelper.typeText(text)));
            return null;
        }

        if (command.startsWith("draft ")) {
            final String request = command.substring(6).trim();
            if (request.isEmpty()) return "Say draft, then what you want written.";
            String prompt = "Write this for me. Reply with only the finished text, ready to send, plain and friendly: " + request;
            askApproval("Send this writing request to the model?", () -> runModel(prompt));
            return null;
        }

        if (command.startsWith("improve prompt ") || command.startsWith("improve ")) {
            final String rough = command.replaceFirst("^improve (prompt )?", "").trim();
            if (rough.isEmpty()) return "Say improve prompt, then your rough request.";
            String prompt = "Rewrite this rough request as a clear, detailed prompt for an AI assistant. Reply with only the improved prompt: " + rough;
            askApproval("Send this prompt to the model to improve it?", () -> runModel(prompt));
            return null;
        }

        if (command.equals("summarize my last voice note") || command.equals("summarize voice note")
                || command.equals("summarize last recording") || command.equals("summarize my last recording")) {
            String note = lastVoiceNote();
            if (note == null) return "You have no voice notes yet. Say record a voice note first.";
            String prompt = "Summarize this recording in three short sentences, then list any action items as short plain lines:\n\n" + note;
            askApproval("Send your last voice note to the model for a summary?", () -> runModel(prompt));
            return null;
        }

        if (command.startsWith("ask my notes ")) {
            final String q = command.substring(13).trim();
            if (q.isEmpty()) return "Say ask my notes, then your question.";
            String prompt = "Answer this question using only these notes. If the answer is not in the notes, say so.\n\nNotes:\n"
                    + notesContext() + "\n\nQuestion: " + q;
            askApproval("Send your notes and question to the model? This uses OpenRouter credits.", () -> runModel(prompt));
            return null;
        }

        if (command.startsWith("look")) {
            String q = command.replaceFirst("^look( at this| at it| at that)?\\s*", "").trim();
            if (q.isEmpty()) q = "Describe what you see in this photo.";
            final String question = q;
            askApproval("Open the camera, take a photo, and send it to the model? This uses OpenRouter credits.",
                    () -> openCameraForLook(question));
            return null;
        }

        if (command.equals("tools") || command.equals("tool list")) {
            return "Offline tools are " + OfflineTools.list() + ".";
        }
        if (command.startsWith("tool ")) {
            String out = OfflineTools.run(this, command.substring(5).trim());
            return out == null ? "I do not have that tool. Say tools to hear the list." : out;
        }

        if (command.startsWith("tap ")) {
            final String label = command.substring(4).trim();
            if (label.isEmpty()) return "Say tap, then the button name.";
            askApproval("Tap " + label + " on the screen?", () -> reply(ScreenHelper.clickText(label)));
            return null;
        }
        if (command.contains("on my screen") || command.contains("on the screen") || command.equals("read my screen")) {
            askApproval("Read the text on your screen and send it to the model?", () -> {
                String screen = ScreenHelper.readScreen();
                if (screen.startsWith("Turn on") || screen.startsWith("I cannot")) {
                    reply(screen);
                    return;
                }
                runModel("Summarize what is on this phone screen in three short sentences:\n\n" + screen);
            });
            return null;
        }

        if (command.equals("usage") || command.equals("how many questions")) {
            return ChatHistory.usageText(this);
        }
        if (command.equals("models")) {
            StringBuilder sb = new StringBuilder("I try these free models in order. ");
            for (String m : FREE_MODELS) sb.append(m).append(". ");
            return sb.toString();
        }
        if (command.startsWith("search history ")) {
            return ChatHistory.search(this, command.substring(15).trim());
        }
        if (command.equals("export history")) {
            try {
                return "History exported to " + ChatHistory.export(this) + ".";
            } catch (IOException e) {
                return "I could not export the history.";
            }
        }

        String routineSteps = findRoutineSteps(command);
        if (routineSteps != null) {
            runRoutine(command, routineSteps.split(" \\|\\| "));
            return null;
        }
        Matcher mk = Pattern.compile("make routine (.+?) do (.+)").matcher(command);
        if (mk.find()) return makeRoutine(mk.group(1).trim(), mk.group(2).trim());
        if (command.startsWith("delete routine ")) return deleteRoutine(command.substring(15).trim());
        if (command.equals("routines") || command.equals("routine")) return listRoutines();

        if (command.startsWith("when i get home do ")) {
            return makeRoutine("home", command.substring(19).trim());
        }
        if (command.equals("set home wifi") || command.equals("set home")) return setHomeWifi();

        if (command.startsWith("focus mode") || command.equals("focus")) {
            return focusMode(!command.contains("off"));
        }
        if (command.startsWith("battery alert")) return batteryAlertCmd(command);
        if (command.startsWith("briefing at ") || command.startsWith("morning briefing at ")) {
            return setBriefingCmd(command);
        }
        if (command.equals("briefing") || command.equals("morning briefing")) return morningBriefing();
        if (command.startsWith("record") || command.startsWith("voice note")) return recordNote(command);

        if (command.startsWith("forget")) {
            askApproval("Delete everything I remember?", this::clearMemory);
            return null;
        }
        if (command.startsWith("remember ")) return rememberFact(command.substring(9).trim());
        if (command.equals("memory") || command.contains("what do you remember") || command.contains("do you remember")) {
            return readMemory();
        }

        if (command.equals("notifications") || (command.startsWith("read") && command.contains("notification"))) {
            return readNotifications();
        }

        if (command.startsWith("ask ") || command.startsWith("question ")) {
            String q = command.replaceFirst("^(ask|question) ", "").trim();
            if (q.isEmpty()) return "Say ask, then your question.";
            String prompt = memoryContext() + q;
            askApproval("Send this question to the model?", () -> runModel(prompt));
            return null;
        }
        if (command.startsWith("translate")) {
            Matcher tm = Pattern.compile("translate (.+?) to (\\w+)").matcher(command);
            if (!tm.find()) return "Say translate, then the words, then to, then the language.";
            String prompt = "Translate this into " + tm.group(2) + ". Reply with only the translation: " + tm.group(1);
            askApproval("Send this translation to the model?", () -> runModel(prompt));
            return null;
        }
        if (command.startsWith("define ")) {
            String prompt = "Define this word in two short plain sentences: " + command.substring(7).trim();
            askApproval("Send this definition request to the model?", () -> runModel(prompt));
            return null;
        }
        if (command.contains("joke")) {
            String prompt = "Tell one short clean joke in two lines, plain text.";
            askApproval("Send the joke request to the model?", () -> runModel(prompt));
            return null;
        }
        if (command.contains("headlines")) {
            String prompt = "List three major news headlines from today in short plain sentences. If you are not sure they are current, say so first.";
            askApproval("Send the headlines request to the model?", () -> runModel(prompt));
            return null;
        }
        if (command.startsWith("read ")) {
            String target = command.substring(5).trim();
            if (target.isEmpty()) return "Say read, then a website.";
            String url = target.startsWith("http") ? target
                    : "https://" + target.replace(" ", "") + (target.contains(".") ? "" : ".com");
            askApproval("Read and summarize " + target + "? This sends the page text to the model.", () -> summarizeSite(url));
            return null;
        }

        if (command.startsWith("call ")) {
            String target = command.substring(5).trim();
            String number = resolveNumber(target);
            if (number == null) return "I could not find " + target + " in your contacts.";
            if (number.equals(NO_CONTACTS)) return "Allow contacts in the SAJA app first, or say the phone number.";
            askApproval("Open the dialer to call " + target + "?", () -> dial(number));
            return null;
        }
        if (command.startsWith("text ")) {
            String[] parts = command.substring(5).trim().split("\\s+", 2);
            if (parts.length < 2) return "Say text, then a name, then the message.";
            String target = parts[0];
            String body = parts[1];
            String number = resolveNumber(target);
            if (number == null) return "I could not find " + target + " in your contacts.";
            if (number.equals(NO_CONTACTS)) return "Allow contacts in the SAJA app first, or say the phone number.";
            askApproval("Prepare a message to " + target + " saying " + body + "?", () -> sendSms(number, body));
            return null;
        }
        if (command.startsWith("photo ") || command.startsWith("picture ")) {
            String q = command.replaceFirst("^(photo|picture) ", "").trim();
            return photoQuestion(q.isEmpty() ? "Describe this photo." : q);
        }
        if (command.equals("photo") || command.equals("picture")) return "Say photo, then your question.";
        if (command.startsWith("music ")) {
            String action = command.substring(6).trim();
            int code;
            if (action.equals("play") || action.equals("pause")) {
                code = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE;
            } else if (action.equals("next")) {
                code = KeyEvent.KEYCODE_MEDIA_NEXT;
            } else if (action.equals("previous") || action.equals("back")) {
                code = KeyEvent.KEYCODE_MEDIA_PREVIOUS;
            } else {
                return "Say music, then play, pause, next, or previous.";
            }
            sendMediaKey(KeyEvent.ACTION_DOWN, code);
            sendMediaKey(KeyEvent.ACTION_UP, code);
            return "Done.";
        }
        if (command.startsWith("volume ")) return setVolume(command.substring(7).trim());
        if (command.contains("weather")) {
            String city = "";
            int idx = command.indexOf(" in ");
            if (idx >= 0) city = command.substring(idx + 4).trim();
            weatherAsync(city);
            return null;
        }
        if (command.contains("github") || command.contains("repos")) {
            githubAsync();
            return null;
        }

        if (command.startsWith("add event ")) return addEvent(command.substring(10).trim());
        if (command.startsWith("directions to ") || command.startsWith("navigate to ")) {
            return navigate(command.replaceFirst("^(directions|navigate) to ", "").trim());
        }
        if (command.equals("calendar") || command.contains("calendar") || command.contains("agenda")
                || command.contains("what's on") || command.contains("whats on")) {
            return todayEvents();
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

        // Catch-all brain: anything else goes to the free model after a yes.
        final String prompt = memoryContext()
                + "Answer this briefly and clearly in plain text, for a phone assistant. "
                + "Keep it under four sentences unless the question needs more: " + command;
        askApproval("Send this question to the model?", () -> runModel(prompt));
        return null;
    }

    private void handleText(String heard) {
        String lower = heard.toLowerCase(Locale.US).trim();

        if (pendingYes != null) {
            long age = System.currentTimeMillis() - pendingSince;
            if (age > 30000) {
                pendingYes = null;
            } else if (age > 3000) {
                List<String> tokens = Arrays.asList(lower.split("\\s+"));
                if (tokens.contains("yes") || tokens.contains("yeah") || tokens.contains("sure")) {
                    Runnable action = pendingYes;
                    pendingYes = null;
                    action.run();
                    return;
                }
                if (tokens.contains("no") || tokens.contains("nope") || tokens.contains("cancel")) {
                    pendingYes = null;
                    reply("Cancelled.");
                    return;
                }
            }
        }

        for (String w : WAKE_WORDS) {
            int at = lower.indexOf(w);
            if (at >= 0) {
                pendingYes = null;
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
        if (intent != null && "com.saja.app.ASK_LOOK".equals(intent.getAction())) {
            String q = intent.getStringExtra("q");
            runVision(q == null || q.isEmpty() ? "Describe what you see in this photo." : q);
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        instance = null;
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
