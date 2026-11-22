package com.saja.app;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.BatteryManager;
import android.provider.CalendarContract;

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
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OfflineTools {
    private static final String[] NAMES = {"battery", "time", "date", "notes", "memory",
            "notifications", "history", "usage", "calendar", "calc", "convert", "currency",
            "headlines", "define", "shopping", "days until"};

    private static final Map<String, Integer> NUMBERS = new HashMap<>();
    private static final Map<String, Double> LENGTH = new HashMap<>();
    private static final Map<String, Double> WEIGHT = new HashMap<>();
    private static String src = "";
    private static int pos = 0;

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
        String in = input.trim();
        if (in.equals("battery")) return battery(c);
        if (in.equals("time")) return "It is " + new SimpleDateFormat("h:mm a", Locale.US).format(new Date()) + ".";
        if (in.equals("date")) return "Today is " + new SimpleDateFormat("EEEE, MMMM d", Locale.US).format(new Date()) + ".";
        if (in.equals("notes")) return lastParts(c, "notes.txt", 5, "Your last notes are. ", "You have no notes yet.");
        if (in.equals("memory")) return lastParts(c, "memory.txt", 10, "I remember. ", "I do not remember anything yet.");
        if (in.equals("notifications")) return notifications();
        if (in.equals("history")) return history(c);
        if (in.equals("usage")) return ChatHistory.usageText(c);
        if (in.equals("calendar")) return calendar(c);
        if (in.startsWith("calc ")) return calc(in.substring(5));
        if (in.startsWith("convert ")) return convert(in.substring(8));
        if (in.startsWith("currency ")) return currency(in.substring(9));
        if (in.equals("headlines")) return headlines();
        if (in.startsWith("define ")) return define(in.substring(7).trim());
        if (in.startsWith("shopping")) return shopping(c, in.substring(8).trim());
        if (in.startsWith("days until ")) return daysUntil(in.substring(11).trim());
        return null;
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

    private static List<String> lines(Context c, String file) {
        List<String> out = new ArrayList<>();
        File f = new File(c.getFilesDir(), file);
        if (!f.exists()) return out;
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            String l;
            while ((l = r.readLine()) != null) {
                if (!l.trim().isEmpty()) out.add(l);
            }
        } catch (IOException e) {
            // return what was read
        }
        return out;
    }

    private static String lastParts(Context c, String file, int count, String prefix, String empty) {
        List<String> all = lines(c, file);
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

    private static String shopping(Context c, String rest) {
        File f = new File(c.getFilesDir(), "shopping.txt");
        try {
            if (rest.startsWith("add ")) {
                String item = rest.substring(4).trim();
                if (item.isEmpty()) return "Say shopping add, then the item.";
                try (FileWriter w = new FileWriter(f, true)) {
                    w.write(item + "\n");
                }
                return "Added " + item + " to your shopping list.";
            }
            if (rest.isEmpty() || rest.equals("list")) {
                List<String> items = new ArrayList<>();
                if (f.exists()) {
                    try (BufferedReader r = new BufferedReader(new FileReader(f))) {
                        String l;
                        while ((l = r.readLine()) != null) {
                            if (!l.trim().isEmpty()) items.add(l.trim());
                        }
                    }
                }
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
}
