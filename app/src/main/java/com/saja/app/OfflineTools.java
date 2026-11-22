package com.saja.app;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import android.provider.CalendarContract;
import android.speech.tts.TextToSpeech;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OfflineTools {
    private static final String[] NAMES = {"battery", "time", "date", "notes", "memory",
            "notifications", "history", "usage", "calendar", "calc", "convert", "currency",
            "headlines", "define", "shopping", "days until", "spent", "expenses", "habit", "habits",
            "stopwatch", "tip", "qr", "time in", "quiz", "quiz answer", "fact",
            "flashcard", "flashcard answer", "bill", "bills", "reading list", "read later add",
            "reading done", "journal", "journal week", "random number", "count words",
            "daily update", "dictionary", "remind habit", "habit reminders", "expenses by item"};

    private static final Map<String, Integer> NUMBERS = new HashMap<>();
    private static final Map<String, Double> LENGTH = new HashMap<>();
    private static final Map<String, Double> WEIGHT = new HashMap<>();
    private static final Map<String, String> ZONES = new HashMap<>();
    private static final String[] FACTS = {
            "Honey found in ancient Egyptian tombs can still be eaten, because honey almost never spoils.",
            "Octopuses have three hearts and blue blood.",
            "Bananas are botanically berries, but strawberries are not.",
            "Nepal's flag is the only national flag that is not a four-sided shape.",
            "Lightning can be about five times hotter than the surface of the sun.",
            "Wombats produce cube-shaped droppings."
    };
    private static final Random RANDOM = new Random();
    private static final Set<String> FIRED = new HashSet<>();
    private static String src = "";
    private static int pos = 0;

    private static Context appCtx;
    private static Handler tickHandler;
    private static boolean ticking = false;
    private static String lastDailyDay = "";
    private static TextToSpeech tts;
    private static boolean ttsReady = false;

    static {
        String[] small = {"zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
                "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen",
                "eighteen", "nineteen", "twenty"};
        for (int i = 0; i < small.length; i++) NUMBERS.put(small[i], i);
        NUMBERS.put("thirty", 30);
        NUMBERS.put("forty", 40);
        NUMBERS.put("fifty", 50);
        NUMBERS.put("sixty", 60);
        NUMBERS.put("seventy", 70);
        NUMBERS.put("eighty", 80);
        NUMBERS.put("ninety", 90);

        LENGTH.put("m", 1.0); LENGTH.put("meter", 1.0); LENGTH.put("meters", 1.0);
        LENGTH.put("km", 1000.0); LENGTH.put("kilometer", 1000.0); LENGTH.put("kilometers", 1000.0);
        LENGTH.put("cm", 0.01); LENGTH.put("mm", 0.001);
        LENGTH.put("mi", 1609.344); LENGTH.put("mile", 1609.344); LENGTH.put("miles", 1609.344);
        LENGTH.put("ft", 0.3048); LENGTH.put("foot", 0.3048); LENGTH.put("feet", 0.3048);
        LENGTH.put("in", 0.0254); LENGTH.put("inch", 0.0254); LENGTH.put("inches", 0.0254);
        LENGTH.put("yd", 0.9144); LENGTH.put("yard", 0.9144); LENGTH.put("yards", 0.9144);

        WEIGHT.put("g", 1.0); WEIGHT.put("gram", 1.0); WEIGHT.put("grams", 1.0);
        WEIGHT.put("kg", 1000.0); WEIGHT.put("kilo", 1000.0); WEIGHT.put("kilos", 1000.0);
        WEIGHT.put("kilogram", 1000.0); WEIGHT.put("kilograms", 1000.0);
        WEIGHT.put("lb", 453.592); WEIGHT.put("lbs", 453.592); WEIGHT.put("pound", 453.592); WEIGHT.put("pounds", 453.592);
        WEIGHT.put("oz", 28.3495); WEIGHT.put("ounce", 28.3495); WEIGHT.put("ounces", 28.3495);

        ZONES.put("tokyo", "Asia/Tokyo");
        ZONES.put("london", "Europe/London");
        ZONES.put("new york", "America/New_York");
        ZONES.put("kathmandu", "Asia/Kathmandu");
        ZONES.put("dubai", "Asia/Dubai");
        ZONES.put("sydney", "Australia/Sydney");
        ZONES.put("paris", "Europe/Paris");
        ZONES.put("los angeles", "America/Los_Angeles");
        ZONES.put("delhi", "Asia/Kolkata");
        ZONES.put("beijing", "Asia/Shanghai");
        ZONES.put("berlin", "Europe/Berlin");
        ZONES.put("chicago", "America/Chicago");
        ZONES.put("singapore", "Asia/Singapore");
        ZONES.put("toronto", "America/Toronto");
    }

    public static String list() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < NAMES.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(NAMES[i]);
        }
        return sb.toString();
    }

    public static String run(Context c, String input) {
        ensureTicking(c);
        String in = input.trim();
        if (in.startsWith("daily update ")) return dailyUpdateSet(c, in.substring(13).trim().replaceFirst("^at ", ""));
        if (in.equals("daily update")) return dailyUpdateSet(c, "off");
        if (in.startsWith("dictionary ")) return define(joinWord(in.substring(11)));
        if (in.startsWith("remind habit ")) return setHabitRemind(c, in.substring(13).trim());
        if (in.equals("habit reminders")) return habitReminders(c);
        if (in.startsWith("stop habit remind ")) return stopHabitRemind(c, in.substring(18).trim());
        if (in.equals("expenses by item")) return expensesByItem(c);

        if (in.equals("battery")) return battery(c);
        if (in.equals("time")) return timeText();
        if (in.equals("date")) return dateText();
        if (in.equals("notes")) return lastParts(c, "notes.txt", 5, "Your last notes are. ", "You have no notes yet.");
        if (in.equals("memory")) return lastParts(c, "memory.txt", 10, "I remember. ", "I do not remember anything yet.");
        if (in.equals("notifications")) return notifications();
        if (in.equals("history")) return history(c);
        if (in.equals("usage")) return ChatHistory.usageText(c);
        if (in.equals("calendar")) return calendar(c);
        if (in.equals("headlines")) return headlines();
        if (in.equals("habits")) return habits(c);
        if (in.equals("stopwatch")) return stopwatch(c, "");
        if (in.equals("quiz answer")) return quizAnswer(c);
        if (in.equals("quiz")) return quiz(c);
        if (in.equals("flashcard answer")) return flashcardAnswer(c);
        if (in.equals("flashcard")) return flashcard(c);
        if (in.equals("fact")) return FACTS[RANDOM.nextInt(FACTS.length)];
        if (in.equals("expenses")) return expenses(c, false);
        if (in.equals("expenses today")) return expenses(c, true);
        if (in.equals("bills")) return bills(c);
        if (in.equals("reading list")) return readingList(c);
        if (in.equals("journal week")) return journalWeek(c);
        if (in.startsWith("calc ")) return calc(in.substring(5));
        if (in.startsWith("convert ")) return convert(in.substring(8));
        if (in.startsWith("currency ")) return currency(in.substring(9));
        if (in.startsWith("define ")) return define(in.substring(7).trim());
        if (in.startsWith("shopping")) return shopping(c, in.substring(8).trim());
        if (in.startsWith("days until ")) return daysUntil(in.substring(11).trim());
        if (in.startsWith("spent ")) return spent(c, in.substring(6).trim());
        if (in.startsWith("habit ")) return logHabit(c, in.substring(6).trim());
        if (in.startsWith("stopwatch ")) return stopwatch(c, in.substring(10).trim());
        if (in.startsWith("tip ")) return tip(in.substring(4).trim());
        if (in.startsWith("qr ")) return qr(c, in.substring(3).trim());
        if (in.startsWith("time in ")) return timeIn(in.substring(8).trim());
        if (in.startsWith("bill ")) return addBill(c, in.substring(5).trim());
        if (in.startsWith("read later add ")) return addReading(c, in.substring(15).trim());
        if (in.startsWith("reading done ")) return doneReading(c, in.substring(13).trim());
        if (in.startsWith("journal ")) return addJournal(c, in.substring(8).trim());
        if (in.startsWith("random number ")) return randomNumber(in.substring(14).trim());
        if (in.startsWith("count words ")) return countWords(in.substring(12).trim());
        return null;
    }

    private static void ensureTicking(Context c) {
        appCtx = c.getApplicationContext();
        if (ticking) return;
        ticking = true;
        tickHandler = new Handler(Looper.getMainLooper());
        tickHandler.postDelayed(TICK, 60000);
    }

    private static final Runnable TICK = new Runnable() {
        @Override
        public void run() {
            try {
                checkDailyUpdate();
                checkHabitReminders();
            } catch (Exception e) {
                // keep ticking even if one check fails
            }
            tickHandler.postDelayed(this, 60000);
        }
    };

    private static void speakOut(Context c, String text) {
        Context app = c.getApplicationContext();
        if (tts == null) {
            tts = new TextToSpeech(app, status -> {
                if (status == TextToSpeech.SUCCESS) {
                    tts.setLanguage(Locale.US);
                    ttsReady = true;
                }
            });
        }
        if (ttsReady) tts.speak(text, TextToSpeech.QUEUE_ADD, null, "tool" + System.currentTimeMillis());
        NotificationManager nm = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(5, new Notification.Builder(app, "saja")
                    .setContentTitle("SAJA")
                    .setContentText(text)
                    .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                    .setAutoCancel(true)
                    .build());
        }
    }

    private static String today() {
        return dayOffset(0);
    }

    private static String dayOffset(int days) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -days);
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.getTime());
    }

    private static String nowHm() {
        return new SimpleDateFormat("HH:mm", Locale.US).format(new Date());
    }

    private static String timeText() {
        return "It is " + new SimpleDateFormat("h:mm a", Locale.US).format(new Date()) + ".";
    }

    private static String dateText() {
        return "Today is " + new SimpleDateFormat("EEEE, MMMM d", Locale.US).format(new Date()) + ".";
    }

    private static List<String> fileLines(Context c, String name) {
        List<String> out = new ArrayList<>();
        File f = new File(c.getFilesDir(), name);
        if (!f.exists()) return out;
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            String l;
            while ((l = r.readLine()) != null) {
                if (!l.trim().isEmpty()) out.add(l.trim());
            }
        } catch (IOException e) {
            // return what was read
        }
        return out;
    }

    private static void appendFile(Context c, String name, String line) throws IOException {
        try (FileWriter w = new FileWriter(new File(c.getFilesDir(), name), true)) {
            w.write(line + "\n");
        }
    }

    private static void writeFile(Context c, String name, List<String> lines) throws IOException {
        try (FileWriter w = new FileWriter(new File(c.getFilesDir(), name))) {
            for (String l : lines) w.write(l + "\n");
        }
    }

    private static String battery(Context c) {
        Intent b = c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (b == null) return "Battery level is unknown.";
        int level = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = b.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        int pct = level * 100 / scale;
        int status = b.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL;
        return "Battery is " + pct + " percent" + (charging ? ", charging." : ".");
    }

    private static String lastParts(Context c, String file, int count, String prefix, String empty) {
        List<String> all = fileLines(c, file);
        if (all.isEmpty()) return empty;
        StringBuilder sb = new StringBuilder(prefix);
        for (int i = Math.max(0, all.size() - count); i < all.size(); i++) {
            String[] p = all.get(i).split("\\|", 2);
            sb.append(p.length == 2 ? p[1] : all.get(i)).append(". ");
        }
        return sb.toString();
    }

    private static String notifications() {
        List<String> items = new ArrayList<>(NotificationReader.RECENT);
        if (items.isEmpty()) return "You have no new notifications.";
        StringBuilder sb = new StringBuilder("Your recent notifications. ");
        for (int i = Math.max(0, items.size() - 5); i < items.size(); i++) {
            sb.append(items.get(i)).append(". ");
        }
        return sb.toString();
    }

    private static String history(Context c) {
        int n = ChatHistory.lines(c).size();
        if (n == 0) return "You have no saved answers yet.";
        return "You have " + n + " saved answers. Say search history with a word to find one.";
    }

    private static String calendar(Context c) {
        if (c.checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
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
        try (Cursor cur = CalendarContract.Instances.query(c.getContentResolver(),
                new String[]{CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN}, begin, end)) {
            if (cur == null) return "I could not read the calendar.";
            while (cur.moveToNext()) {
                String title = cur.getString(0);
                long when = cur.getLong(1);
                items.add(new SimpleDateFormat("h:mm a", Locale.US).format(new Date(when))
                        + " " + (title == null ? "Untitled" : title));
            }
        } catch (Exception e) {
            return "I could not read the calendar.";
        }
        if (items.isEmpty()) return "You have nothing on your calendar today.";
        return "Today you have. " + String.join(". ", items) + ".";
    }

    private static String fmt(double v) {
        if (v == Math.rint(v) && Math.abs(v) < 1e15) return String.valueOf((long) v);
        return String.format(Locale.US, "%.2f", v);
    }

    private static boolean isNumToken(String t) {
        return t.matches("[0-9]+(\\.[0-9]+)?") || NUMBERS.containsKey(t)
                || t.equals("hundred") || t.equals("thousand");
    }

    private static Double parseNumber(List<String> tokens) {
        double total = 0;
        double current = 0;
        boolean any = false;
        for (String t : tokens) {
            if (t.matches("[0-9]+(\\.[0-9]+)?")) {
                current += Double.parseDouble(t);
                any = true;
            } else if (NUMBERS.containsKey(t)) {
                current += NUMBERS.get(t);
                any = true;
            } else if (t.equals("hundred")) {
                current = (current == 0 ? 1 : current) * 100;
                any = true;
            } else if (t.equals("thousand")) {
                total += (current == 0 ? 1 : current) * 1000;
                current = 0;
                any = true;
            } else {
                break;
            }
        }
        return any ? total + current : null;
    }

    private static int[] parseClock(String s) {
        String lower = s.toLowerCase(Locale.US);
        boolean pm = lower.contains("pm");
        boolean am = lower.contains("am");
        List<Integer> nums = new ArrayList<>();
        for (String tok : lower.replace(":", " ").split("\\s+")) {
            if (tok.matches("\\d+")) {
                nums.add(Integer.parseInt(tok));
            } else if (NUMBERS.containsKey(tok)) {
                nums.add(NUMBERS.get(tok));
            }
        }
        if (nums.isEmpty()) return null;
        int hour = nums.get(0);
        int minute = nums.size() > 1 ? nums.get(1) : 0;
        if (hour > 23 || minute > 59) return null;
        if (pm && hour < 12) hour += 12;
        if (am && hour == 12) hour = 0;
        return new int[]{hour, minute};
    }

    private static String calc(String expr) {
        String[] tokens = expr.toLowerCase(Locale.US).trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tokens.length; i++) {
            String t = tokens[i];
            String next = i + 1 < tokens.length ? tokens[i + 1] : "";
            if (t.equals("divided") && next.equals("by")) {
                sb.append('/');
                i++;
            } else if (t.equals("multiplied") && next.equals("by")) {
                sb.append('*');
                i++;
            } else if (t.equals("times") || t.equals("x")) {
                sb.append('*');
            } else if (t.equals("plus")) {
                sb.append('+');
            } else if (t.equals("minus")) {
                sb.append('-');
            } else if (t.equals("over")) {
                sb.append('/');
            } else if (t.equals("point")) {
                sb.append('.');
            } else if (NUMBERS.containsKey(t)) {
                int v = NUMBERS.get(t);
                if (v >= 20 && v % 10 == 0 && NUMBERS.containsKey(next) && NUMBERS.get(next) < 10) {
                    v += NUMBERS.get(next);
                    i++;
                }
                sb.append(v);
            } else {
                sb.append(t);
            }
        }
        try {
            src = sb.toString();
            pos = 0;
            double v = parseExpr();
            if (pos != src.length()) return "I could not work that out.";
            if (Double.isInfinite(v) || Double.isNaN(v)) return "You cannot divide by zero.";
            return "That equals " + fmt(v) + ".";
        } catch (Exception e) {
            return "I could not work that out.";
        }
    }

    private static double parseExpr() {
        double v = parseTerm();
        while (pos < src.length()) {
            char ch = src.charAt(pos);
            if (ch == '+') {
                pos++;
                v += parseTerm();
            } else if (ch == '-') {
                pos++;
                v -= parseTerm();
            } else {
                break;
            }
        }
        return v;
    }

    private static double parseTerm() {
        double v = parseFactor();
        while (pos < src.length()) {
            char ch = src.charAt(pos);
            if (ch == '*') {
                pos++;
                v *= parseFactor();
            } else if (ch == '/') {
                pos++;
                v /= parseFactor();
            } else {
                break;
            }
        }
        return v;
    }

    private static double parseFactor() {
        if (pos >= src.length()) throw new IllegalArgumentException("unexpected end");
        char ch = src.charAt(pos);
        if (ch == '-') {
            pos++;
            return -parseFactor();
        }
        if (ch == '(') {
            pos++;
            double v = parseExpr();
            if (pos < src.length() && src.charAt(pos) == ')') {
                pos++;
                return v;
            }
            throw new IllegalArgumentException("missing bracket");
        }
        int start = pos;
        while (pos < src.length() && (Character.isDigit(src.charAt(pos)) || src.charAt(pos) == '.')) pos++;
        if (start == pos) throw new IllegalArgumentException("bad number");
        return Double.parseDouble(src.substring(start, pos));
    }

    private static String tempName(String u) {
        if (u.equals("celsius")) return "c";
        if (u.equals("fahrenheit")) return "f";
        if (u.equals("kelvin")) return "k";
        return u;
    }

    private static boolean isTemp(String u) {
        return u.equals("c") || u.equals("f") || u.equals("k");
    }

    private static double toC(double n, String u) {
        if (u.equals("f")) return (n - 32) * 5 / 9;
        if (u.equals("k")) return n - 273.15;
        return n;
    }

    private static double fromC(double c, String u) {
        if (u.equals("f")) return c * 9 / 5 + 32;
        if (u.equals("k")) return c + 273.15;
        return c;
    }

    private static String convert(String s) {
        Matcher m = Pattern.compile("([0-9.]+)\\s*([a-z]+)\\s+to\\s+([a-z]+)").matcher(s.toLowerCase(Locale.US).trim());
        if (!m.find()) return "Say convert, then a number and two units, like convert 10 miles to km.";
        double n;
        try {
            n = Double.parseDouble(m.group(1));
        } catch (NumberFormatException e) {
            return "I did not catch the number.";
        }
        String a = tempName(m.group(2));
        String b = tempName(m.group(3));
        if (isTemp(a) && isTemp(b)) {
            double out = fromC(toC(n, a), b);
            return fmt(n) + " " + a + " is " + fmt(out) + " " + b + ".";
        }
        Double fa = LENGTH.get(a);
        Double fb = LENGTH.get(b);
        if (fa != null && fb != null) {
            return fmt(n) + " " + a + " is " + fmt(n * fa / fb) + " " + b + ".";
        }
        fa = WEIGHT.get(a);
        fb = WEIGHT.get(b);
        if (fa != null && fb != null) {
            return fmt(n) + " " + a + " is " + fmt(n * fa / fb) + " " + b + ".";
        }
        return "I cannot convert " + a + " to " + b + ".";
    }

    private static String httpGet(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestProperty("User-Agent", "SAJA-Android/1.0 (personal assistant)");
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        int code = c.getResponseCode();
        if (code >= 400) throw new IOException("the site returned " + code);
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
        }
        return sb.toString();
    }

    private static String currency(String s) {
        Matcher m = Pattern.compile("([0-9.]+)\\s*([a-z]{3})\\s+to\\s+([a-z]{3})").matcher(s.toLowerCase(Locale.US).trim());
        if (!m.find()) return "Say currency, then an amount and two codes, like currency 100 usd to npr.";
        double n;
        try {
            n = Double.parseDouble(m.group(1));
        } catch (NumberFormatException e) {
            return "I did not catch the amount.";
        }
        String from = m.group(2).toUpperCase(Locale.US);
        String to = m.group(3).toUpperCase(Locale.US);
        try {
            JSONObject rates = new JSONObject(httpGet("https://open.er-api.com/v6/latest/USD")).getJSONObject("rates");
            double rf = rates.getDouble(from);
            double rt = rates.getDouble(to);
            double out = n / rf * rt;
            return fmt(n) + " " + from + " is " + String.format(Locale.US, "%.2f", out) + " " + to
                    + ", using today's rates.";
        } catch (Exception e) {
            return "I could not get the rate for " + from + " to " + to + ".";
        }
    }

    private static String unescape(String s) {
        return s.replace("<![CDATA[", "").replace("]]>", "")
                .replace("&amp;", "&").replace("&quot;", "\"")
                .replace("&#39;", "'").replace("&apos;", "'")
                .replace("&lt;", "<").replace("&gt;", ">").trim();
    }

    private static String headlines() {
        try {
            String xml = httpGet("https://feeds.bbci.co.uk/news/rss.xml");
            Matcher m = Pattern.compile("<item>.*?<title>(.*?)</title>", Pattern.DOTALL).matcher(xml);
            StringBuilder sb = new StringBuilder("Top BBC headlines. ");
            int count = 0;
            while (m.find() && count < 5) {
                sb.append(unescape(m.group(1))).append(". ");
                count++;
            }
            return count == 0 ? "I could not find any headlines." : sb.toString();
        } catch (Exception e) {
            return "I could not reach the news right now.";
        }
    }

    private static String define(String word) {
        if (word.isEmpty()) return "Say define, then the word.";
        try {
            String raw = httpGet("https://api.dictionaryapi.dev/api/v2/entries/en/"
                    + URLEncoder.encode(word, "UTF-8").replace("+", "%20"));
            JSONObject first = new JSONArray(raw).getJSONObject(0);
            JSONObject meaning = first.getJSONArray("meanings").getJSONObject(0);
            String def = meaning.getJSONArray("definitions").getJSONObject(0).getString("definition");
            String pos = meaning.optString("partOfSpeech", "");
            return word + (pos.isEmpty() ? "" : ", " + pos) + ". " + def;
        } catch (Exception e) {
            return "I could not find a definition for " + word + ".";
        }
    }

    private static String joinWord(String rest) {
        String r = rest.trim();
        String[] t = r.split("\\s+");
        boolean letters = t.length > 1;
        for (String x : t) {
            if (x.length() != 1) letters = false;
        }
        return letters ? String.join("", t) : r;
    }

    private static String shopping(Context c, String rest) {
        try {
            if (rest.startsWith("add ")) {
                String item = rest.substring(4).trim();
                if (item.isEmpty()) return "Say shopping add, then the item.";
                appendFile(c, "shopping.txt", item);
                return "Added " + item + " to your shopping list.";
            }
            if (rest.isEmpty() || rest.equals("list")) {
                List<String> items = fileLines(c, "shopping.txt");
                if (items.isEmpty()) return "Your shopping list is empty.";
                StringBuilder sb = new StringBuilder("Your shopping list has " + items.size() + " items. ");
                for (String i : items) sb.append(i).append(". ");
                return sb.toString();
            }
        } catch (IOException e) {
            return "I could not update the shopping list.";
        }
        return "Say shopping add, then an item, or shopping list.";
    }

    private static String daysUntil(String s) {
        String[] formats = {"yyyy-MM-dd", "MMMM d yyyy", "MMMM d, yyyy", "d MMMM yyyy"};
        Date target = null;
        for (String f : formats) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(f, Locale.US);
                sdf.setLenient(false);
                target = sdf.parse(s.trim());
                break;
            } catch (Exception e) {
                // try the next format
            }
        }
        if (target == null) return "Say days until, then a date, like days until January 1 2027.";
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long days = Math.round((target.getTime() - cal.getTimeInMillis()) / 86400000.0);
        if (days < 0) return "That date already passed.";
        if (days == 0) return "That is today.";
        return days + " days until " + s.trim() + ".";
    }

    private static String spent(Context c, String rest) {
        List<String> tokens = Arrays.asList(rest.toLowerCase(Locale.US).split("\\s+"));
        List<String> nums = new ArrayList<>();
        int i = 0;
        while (i < tokens.size() && isNumToken(tokens.get(i))) {
            nums.add(tokens.get(i));
            i++;
        }
        Double amount = parseNumber(nums);
        if (amount == null) return "Say tool spent, then an amount, like spent twelve on lunch.";
        if (i < tokens.size() && tokens.get(i).equals("on")) i++;
        String item = i < tokens.size() ? String.join(" ", tokens.subList(i, tokens.size())).trim() : "";
        if (item.isEmpty()) item = "misc";
        try {
            appendFile(c, "expenses.txt", today() + "|" + fmt(amount) + "|" + item);
        } catch (IOException e) {
            return "I could not save that expense.";
        }
        return "Saved " + fmt(amount) + " for " + item + ".";
    }

    private static String expenses(Context c, boolean todayOnly) {
        double sum = 0;
        int count = 0;
        String day = today();
        for (String l : fileLines(c, "expenses.txt")) {
            String[] p = l.split("\\|", 3);
            if (p.length < 3) continue;
            if (todayOnly && !p[0].equals(day)) continue;
            try {
                sum += Double.parseDouble(p[1]);
                count++;
            } catch (NumberFormatException e) {
                // skip bad lines
            }
        }
        String scope = todayOnly ? "today" : "in total";
        if (count == 0) return "You have no expenses " + scope + ".";
        return "You spent " + fmt(sum) + " " + scope + " across " + count + " items.";
    }

    private static String expensesByItem(Context c) {
        Map<String, Double> sums = new HashMap<>();
        for (String l : fileLines(c, "expenses.txt")) {
            String[] p = l.split("\\|", 3);
            if (p.length < 3) continue;
            try {
                sums.merge(p[2], Double.parseDouble(p[1]), Double::sum);
            } catch (NumberFormatException e) {
                // skip bad lines
            }
        }
        if (sums.isEmpty()) return "You have no expenses saved yet.";
        List<Map.Entry<String, Double>> entries = new ArrayList<>(sums.entrySet());
        entries.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        StringBuilder sb = new StringBuilder("Your top spending. ");
        for (int i = 0; i < Math.min(5, entries.size()); i++) {
            Map.Entry<String, Double> e = entries.get(i);
            sb.append(e.getKey()).append(": ").append(fmt(e.getValue())).append(". ");
        }
        return sb.toString();
    }

    private static int streak(List<String> lines, String name) {
        Set<String> days = new HashSet<>();
        for (String l : lines) {
            String[] p = l.split("\\|", 2);
            if (p.length == 2 && p[1].equals(name)) days.add(p[0]);
        }
        int count = 0;
        int d = days.contains(dayOffset(0)) ? 0 : 1;
        while (days.contains(dayOffset(d))) {
            count++;
            d++;
        }
        return count;
    }

    private static String logHabit(Context c, String name) {
        String n = name.toLowerCase(Locale.US).trim();
        if (n.isEmpty()) return "Say tool habit, then the habit, like habit water.";
        List<String> lines = fileLines(c, "habits.txt");
        String todayLine = today() + "|" + n;
        if (lines.contains(todayLine)) {
            return n + " is already logged today. Streak " + streak(lines, n) + " days.";
        }
        try {
            appendFile(c, "habits.txt", todayLine);
        } catch (IOException e) {
            return "I could not save that habit.";
        }
        lines.add(todayLine);
        return "Logged " + n + " for today. Streak " + streak(lines, n) + " days.";
    }

    private static String habits(Context c) {
        List<String> lines = fileLines(c, "habits.txt");
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (String l : lines) {
            String[] p = l.split("\\|", 2);
            if (p.length == 2) names.add(p[1]);
        }
        if (names.isEmpty()) return "You have no habits yet. Say tool habit water to start one.";
        StringBuilder sb = new StringBuilder("Your habits. ");
        for (String n : names) {
            sb.append(n).append(": streak ").append(streak(lines, n)).append(" days. ");
        }
        return sb.toString();
    }

    private static String setHabitRemind(Context c, String rest) {
        int idx = rest.indexOf(" at ");
        String usage = "Say tool remind habit, then the habit, then at, then a time, like remind habit water at 9 pm.";
        if (idx < 0) return usage;
        String name = rest.substring(0, idx).trim().toLowerCase(Locale.US);
        int[] t = parseClock(rest.substring(idx + 4));
        if (name.isEmpty() || t == null) return usage;
        String hm = String.format(Locale.US, "%02d:%02d", t[0], t[1]);
        try {
            appendFile(c, "habit_reminders.txt", hm + "|" + name);
        } catch (IOException e) {
            return "I could not save that reminder.";
        }
        return "I will remind you about " + name + " at " + hm + " each day, until it is logged.";
    }

    private static String habitReminders(Context c) {
        List<String> lines = fileLines(c, "habit_reminders.txt");
        if (lines.isEmpty()) return "You have no habit reminders. Say tool remind habit water at 9 am.";
        StringBuilder sb = new StringBuilder("Your habit reminders. ");
        for (String l : lines) {
            String[] p = l.split("\\|", 2);
            if (p.length == 2) sb.append(p[1]).append(" at ").append(p[0]).append(". ");
        }
        return sb.toString();
    }

    private static String stopHabitRemind(Context c, String name) {
        String n = name.toLowerCase(Locale.US).trim();
        List<String> lines = fileLines(c, "habit_reminders.txt");
        List<String> kept = new ArrayList<>();
        boolean removed = false;
        for (String l : lines) {
            String[] p = l.split("\\|", 2);
            if (p.length == 2 && p[1].equals(n)) {
                removed = true;
            } else {
                kept.add(l);
            }
        }
        if (!removed) return "I did not find a reminder for " + n + ".";
        try {
            writeFile(c, "habit_reminders.txt", kept);
        } catch (IOException e) {
            return "I could not update the reminders.";
        }
        return "Stopped the reminder for " + n + ".";
    }

    private static void checkDailyUpdate() {
        if (appCtx == null) return;
        List<String> lines = fileLines(appCtx, "daily_update.txt");
        if (lines.isEmpty()) return;
        String hm = lines.get(0);
        String day = today();
        if (nowHm().equals(hm) && !day.equals(lastDailyDay)) {
            lastDailyDay = day;
            speakOut(appCtx, "Daily update. " + timeText() + " " + dateText() + " " + battery(appCtx));
        }
    }

    private static void checkHabitReminders() {
        if (appCtx == null) return;
        String now = nowHm();
        String day = today();
        List<String> logged = fileLines(appCtx, "habits.txt");
        for (String l : fileLines(appCtx, "habit_reminders.txt")) {
            String[] p = l.split("\\|", 2);
            if (p.length != 2 || !p[0].equals(now)) continue;
            String name = p[1];
            String key = day + "|" + name;
            if (FIRED.contains(key) || logged.contains(key)) continue;
            FIRED.add(key);
            speakOut(appCtx, "Time for your habit: " + name + ". Say tool habit " + name + " to log it.");
        }
    }

    private static String elapsedText(long secs) {
        long m = secs / 60;
        long s = secs % 60;
        return m + (m == 1 ? " minute" : " minutes") + " and " + s + (s == 1 ? " second" : " seconds");
    }

    private static String stopwatch(Context c, String arg) {
        try {
            if (arg.equals("start")) {
                if (new File(c.getFilesDir(), "stopwatch.txt").exists()) return "The stopwatch is already running.";
                writeFile(c, "stopwatch.txt", Arrays.asList(String.valueOf(System.currentTimeMillis())));
                return "Stopwatch started.";
            }
            List<String> saved = fileLines(c, "stopwatch.txt");
            if (saved.isEmpty()) return "The stopwatch is not running. Say tool stopwatch start.";
            long start = Long.parseLong(saved.get(0));
            long secs = (System.currentTimeMillis() - start) / 1000;
            if (arg.equals("stop")) {
                new File(c.getFilesDir(), "stopwatch.txt").delete();
                return "Stopped at " + elapsedText(secs) + ".";
            }
            return "Elapsed time is " + elapsedText(secs) + ".";
        } catch (Exception e) {
            return "I could not use the stopwatch.";
        }
    }

    private static String tip(String s) {
        String lower = s.toLowerCase(Locale.US).replace("%", " ").replace("percent", " ")
                .replace("dollars", " ").replace("dollar", " ")
                .replace("rupees", " ").replace("rupee", " ");
        String[] parts = lower.trim().split("\\s+at\\s+", 2);
        List<String> amountTokens = new ArrayList<>();
        for (String t : parts[0].trim().split("\\s+")) {
            if (isNumToken(t)) amountTokens.add(t);
        }
        Double amount = parseNumber(amountTokens);
        if (amount == null) return "Say tool tip, then the amount, like tip 45 at 15 percent.";
        double pct = 15;
        if (parts.length > 1) {
            List<String> pctTokens = new ArrayList<>();
            for (String t : parts[1].trim().split("\\s+")) {
                if (isNumToken(t)) pctTokens.add(t);
            }
            Double p = parseNumber(pctTokens);
            if (p != null) pct = p;
        }
        double tipAmount = amount * pct / 100.0;
        return "Tip is " + fmt(tipAmount) + ". Total is " + fmt(amount + tipAmount)
                + " with a " + fmt(pct) + " percent tip.";
    }

    private static String qr(Context c, String text) {
        if (text.isEmpty()) return "Say tool qr, then the text, like qr my website dot com.";
        try {
            String clean = text.replace(" dot ", ".").replace(" ", "");
            String url = "https://api.qrserver.com/v1/create-qr-code/?size=400x400&data="
                    + URLEncoder.encode(clean, "UTF-8");
            c.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return "Showing a QR code for " + clean + " in your browser.";
        } catch (Exception e) {
            return "I could not open the QR code.";
        }
    }

    private static String timeIn(String place) {
        String zone = ZONES.get(place.toLowerCase(Locale.US).trim());
        if (zone == null) return "I do not know the time zone for " + place + " yet.";
        String t = ZonedDateTime.now(ZoneId.of(zone))
                .format(DateTimeFormatter.ofPattern("h:mm a, EEEE, MMMM d", Locale.US));
        return "In " + place + " it is " + t + ".";
    }

    private static String quiz(Context c) {
        List<String> candidates = new ArrayList<>();
        for (String l : fileLines(c, "notes.txt")) {
            String[] p = l.split("\\|", 2);
            String text = p.length == 2 ? p[1] : l;
            if (text.trim().split("\\s+").length >= 4) candidates.add(text.trim());
        }
        if (candidates.isEmpty()) return "Save a few notes first with say note, then quiz me.";
        String text = candidates.get(RANDOM.nextInt(candidates.size()));
        String[] words = text.split("\\s+");
        List<Integer> options = new ArrayList<>();
        for (int i = 0; i < words.length; i++) {
            if (words[i].replaceAll("[^A-Za-z]", "").length() > 3) options.add(i);
        }
        if (options.isEmpty()) return "Save a longer note for a quiz.";
        int pick = options.get(RANDOM.nextInt(options.size()));
        String answer = words[pick].replaceAll("[^A-Za-z0-9]", "");
        words[pick] = "blank";
        try {
            writeFile(c, "quiz_answer.txt", Arrays.asList(answer));
        } catch (IOException e) {
            return "I could not start the quiz.";
        }
        return "Fill in the blank. " + String.join(" ", words) + ". Say tool quiz answer to check.";
    }

    private static String quizAnswer(Context c) {
        List<String> saved = fileLines(c, "quiz_answer.txt");
        if (saved.isEmpty()) return "Ask me a quiz first. Say tool quiz.";
        return "The answer is " + saved.get(0) + ".";
    }

    private static String flashcard(Context c) {
        List<String> candidates = new ArrayList<>();
        for (String l : fileLines(c, "notes.txt")) {
            String[] p = l.split("\\|", 2);
            String text = p.length == 2 ? p[1] : l;
            if (text.trim().split("\\s+").length >= 6) candidates.add(text.trim());
        }
        if (candidates.isEmpty()) return "Save a longer note first, with say note and at least six words.";
        String text = candidates.get(RANDOM.nextInt(candidates.size()));
        String[] words = text.split("\\s+");
        int cut = words.length / 2;
        String front = String.join(" ", Arrays.copyOfRange(words, 0, cut));
        String back = String.join(" ", Arrays.copyOfRange(words, cut, words.length));
        try {
            writeFile(c, "flashcard.txt", Arrays.asList(back));
        } catch (IOException e) {
            return "I could not start the flashcard.";
        }
        return "Flashcard. " + front + ". Say tool flashcard answer to see the rest.";
    }

    private static String flashcardAnswer(Context c) {
        List<String> saved = fileLines(c, "flashcard.txt");
        if (saved.isEmpty()) return "Get a card first. Say tool flashcard.";
        return "The rest is. " + saved.get(0);
    }

    private static String addBill(Context c, String rest) {
        Matcher d = Pattern.compile("on the (\\d{1,2})").matcher(rest);
        if (!d.find()) return "Say bill, then the name, the amount, and on the day, like bill electricity 25 on the 5th.";
        int day = Integer.parseInt(d.group(1));
        if (day < 1 || day > 31) return "Choose a day from 1 to 31.";
        String before = rest.substring(0, d.start()).trim();
        String[] tokens = before.split("\\s+");
        int amountIndex = -1;
        for (int i = tokens.length - 1; i >= 0; i--) {
            if (tokens[i].matches("[0-9]+(\\.[0-9]+)?")) {
                amountIndex = i;
                break;
            }
        }
        if (amountIndex < 1) return "I need a name and an amount before on the day.";
        String amount = tokens[amountIndex];
        String name = String.join(" ", Arrays.copyOfRange(tokens, 0, amountIndex));
        try {
            appendFile(c, "bills.txt", day + "|" + amount + "|" + name);
        } catch (IOException e) {
            return "I could not save that bill.";
        }
        return "Saved " + name + " for " + amount + " on day " + day + " of each month.";
    }

    private static String bills(Context c) {
        List<String> items = fileLines(c, "bills.txt");
        if (items.isEmpty()) return "You have no bills saved.";
        Calendar cal = Calendar.getInstance();
        int todayDay = cal.get(Calendar.DAY_OF_MONTH);
        int daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
        StringBuilder sb = new StringBuilder("Your bills. ");
        for (String l : items) {
            String[] p = l.split("\\|", 3);
            if (p.length < 3) continue;
            int day;
            try {
                day = Integer.parseInt(p[0]);
            } catch (NumberFormatException e) {
                continue;
            }
            String when;
            if (day >= todayDay) {
                int left = day - todayDay;
                when = left == 0 ? "due today" : "due in " + left + " days";
            } else {
                int left = daysInMonth - todayDay + day;
                when = "already passed this month, next due in " + left + " days";
            }
            sb.append(p[2]).append(" for ").append(p[1]).append(", ").append(when).append(". ");
        }
        return sb.toString();
    }

    private static String addReading(Context c, String title) {
        if (title.isEmpty()) return "Say read later add, then the title.";
        try {
            appendFile(c, "reading.txt", title);
        } catch (IOException e) {
            return "I could not save that.";
        }
        return "Added " + title + " to your reading list.";
    }

    private static String readingList(Context c) {
        List<String> items = fileLines(c, "reading.txt");
        if (items.isEmpty()) return "Your reading list is empty.";
        StringBuilder sb = new StringBuilder("Your reading list has " + items.size() + " items. ");
        for (String i : items) sb.append(i).append(". ");
        return sb.toString();
    }

    private static String doneReading(Context c, String title) {
        List<String> items = fileLines(c, "reading.txt");
        List<String> kept = new ArrayList<>();
        boolean removed = false;
        for (String i : items) {
            if (!removed && i.equalsIgnoreCase(title)) {
                removed = true;
            } else {
                kept.add(i);
            }
        }
        if (!removed) return "I could not find " + title + " on your reading list.";
        try {
            writeFile(c, "reading.txt", kept);
        } catch (IOException e) {
            return "I could not update the reading list.";
        }
        return "Marked " + title + " as read.";
    }

    private static String addJournal(Context c, String entry) {
        if (entry.isEmpty()) return "Say journal, then what you want to write.";
        try {
            appendFile(c, "journal.txt", today() + "|" + entry);
        } catch (IOException e) {
            return "I could not save the journal entry.";
        }
        return "Journal entry saved for today.";
    }

    private static String journalWeek(Context c) {
        String weekAgo = dayOffset(6);
        List<String> entries = new ArrayList<>();
        for (String l : fileLines(c, "journal.txt")) {
            String[] p = l.split("\\|", 2);
            if (p.length == 2 && p[0].compareTo(weekAgo) >= 0) entries.add(p[1]);
        }
        if (entries.isEmpty()) return "You have no journal entries this week.";
        StringBuilder sb = new StringBuilder("This week you wrote " + entries.size() + " entries. Latest. ");
        for (int i = Math.max(0, entries.size() - 3); i < entries.size(); i++) {
            sb.append(entries.get(i)).append(". ");
        }
        return sb.toString();
    }

    private static String randomNumber(String range) {
        Matcher m = Pattern.compile("(\\d+) to (\\d+)").matcher(range);
        if (!m.find()) return "Say random number, then a range, like random number 1 to 100.";
        int lo = Integer.parseInt(m.group(1));
        int hi = Integer.parseInt(m.group(2));
        if (lo > hi) {
            int t = lo;
            lo = hi;
            hi = t;
        }
        return "Your number is " + (lo + RANDOM.nextInt(hi - lo + 1)) + ".";
    }

    private static String countWords(String text) {
        if (text.isEmpty()) return "Say count words, then the text.";
        int count = text.trim().split("\\s+").length;
        return "That has " + count + (count == 1 ? " word." : " words.");
    }

    private static String dailyUpdateSet(Context c, String rest) {
        if (rest.equals("off")) {
            new File(c.getFilesDir(), "daily_update.txt").delete();
            return "Daily update is off.";
        }
        int[] t = parseClock(rest);
        if (t == null) return "Say tool daily update at, then a time, like daily update at 7 30 am.";
        String hm = String.format(Locale.US, "%02d:%02d", t[0], t[1]);
        try {
            writeFile(c, "daily_update.txt", Arrays.asList(hm));
        } catch (IOException e) {
            return "I could not save the daily update.";
        }
        return "Daily update set for " + hm + ". I will speak the time, date, and battery each day.";
    }
}
