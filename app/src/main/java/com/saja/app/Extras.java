package com.saja.app;

import android.content.Context;

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
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class Extras {
    private static final Map<String, Integer> WORDS = new HashMap<>();

    static {
        String[] w = {"zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
                "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen",
                "eighteen", "nineteen", "twenty"};
        for (int i = 0; i < w.length; i++) WORDS.put(w[i], i);
    }

    static String dayOffset(int days) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -days);
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.getTime());
    }

    static String today() {
        return dayOffset(0);
    }

    static List<String> lines(Context c, String name) {
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

    static void append(Context c, String name, String line) throws IOException {
        try (FileWriter w = new FileWriter(new File(c.getFilesDir(), name), true)) {
            w.write(line + "\n");
        }
    }

    static void write(Context c, String name, List<String> items) throws IOException {
        try (FileWriter w = new FileWriter(new File(c.getFilesDir(), name))) {
            for (String i : items) w.write(i + "\n");
        }
    }

    static double firstNumber(String s) {
        for (String t : s.toLowerCase(Locale.US).split("\\s+")) {
            if (t.matches("[0-9]+(\\.[0-9]+)?")) return Double.parseDouble(t);
            Integer v = WORDS.get(t);
            if (v != null) return v;
        }
        return -1;
    }

    static String fmtH(double h) {
        return h == Math.rint(h) ? String.valueOf((long) h) : String.valueOf(h);
    }

    static int streak(List<String> log, String name) {
        Set<String> days = new HashSet<>();
        for (String l : log) {
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

    public static String goal(Context c, String rest) {
        try {
            if (rest.startsWith("done ")) {
                String name = rest.substring(5).trim().toLowerCase(Locale.US);
                boolean known = false;
                for (String g : lines(c, "goals.txt")) {
                    if (g.equalsIgnoreCase(name)) known = true;
                }
                if (!known) return "I do not have a goal called " + name + ". Say goals to see yours.";
                List<String> log = lines(c, "goal_log.txt");
                String entry = today() + "|" + name;
                if (log.contains(entry)) return "Already logged " + name + " today.";
                append(c, "goal_log.txt", entry);
                log.add(entry);
                return "Logged " + name + " for today. Streak " + streak(log, name) + " days.";
            }
            if (rest.isEmpty()) return "Say goal, then the goal, like goal read 10 pages.";
            String name = rest.toLowerCase(Locale.US);
            for (String g : lines(c, "goals.txt")) {
                if (g.equalsIgnoreCase(name)) return "You already have that goal.";
            }
            append(c, "goals.txt", name);
            return "Goal saved: " + name + ". Say goal done " + name + " each day to log it.";
        } catch (IOException e) {
            return "I could not save that goal.";
        }
    }

    public static String goals(Context c) {
        List<String> names = lines(c, "goals.txt");
        if (names.isEmpty()) return "You have no goals yet. Say goal, then your goal.";
        List<String> log = lines(c, "goal_log.txt");
        StringBuilder sb = new StringBuilder("Your goals. ");
        for (String g : names) {
            boolean done = log.contains(today() + "|" + g);
            sb.append(g).append(done ? ": done today" : ": not done yet today")
                    .append(", streak ").append(streak(log, g)).append(" days. ");
        }
        return sb.toString();
    }

    public static String sleep(Context c, String rest) {
        double h = firstNumber(rest);
        if (h < 0 || h > 24) return "Say slept, then the hours, like slept 7 hours.";
        try {
            append(c, "sleep.txt", today() + "|" + h);
        } catch (IOException e) {
            return "I could not save that.";
        }
        return "Saved " + fmtH(h) + " hours of sleep for today.";
    }

    public static String sleepWeek(Context c) {
        String weekAgo = dayOffset(6);
        double sum = 0;
        int n = 0;
        for (String l : lines(c, "sleep.txt")) {
            String[] p = l.split("\\|", 2);
            if (p.length == 2 && p[0].compareTo(weekAgo) >= 0) {
                try {
                    sum += Double.parseDouble(p[1]);
                    n++;
                } catch (NumberFormatException e) {
                    // skip bad lines
                }
            }
        }
        if (n == 0) return "You have no sleep logged this week.";
        return "Average sleep this week is " + String.format(Locale.US, "%.1f", sum / n)
                + " hours over " + n + " nights.";
    }

    public static String emergencySet(Context c, String text) {
        try {
            write(c, "emergency.txt", Arrays.asList(text));
            return "Emergency card saved on this phone.";
        } catch (IOException e) {
            return "I could not save the card.";
        }
    }

    public static String emergencyRead(Context c) {
        List<String> l = lines(c, "emergency.txt");
        if (l.isEmpty()) return "You have no emergency card yet. Say emergency set, then your details.";
        return "Emergency card. " + l.get(0);
    }

    public static void focusDone(Context c) throws IOException {
        append(c, "focus.txt", today() + "|1");
    }

    public static String focusCount(Context c) {
        int n = 0;
        String d = today();
        for (String l : lines(c, "focus.txt")) {
            if (l.startsWith(d + "|")) n++;
        }
        if (n == 0) return "You have no finished focus sessions today.";
        return "You finished " + n + (n == 1 ? " focus session" : " focus sessions") + " today.";
    }

    public static String shoppingItems(Context c) {
        StringBuilder sb = new StringBuilder();
        for (String l : lines(c, "shopping.txt")) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(l);
        }
        return sb.toString();
    }

    public static String quote() {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL("https://zenquotes.io/api/random").openConnection();
            c.setRequestProperty("User-Agent", "SAJA-Android/1.0 (personal assistant)");
            c.setConnectTimeout(15000);
            c.setReadTimeout(20000);
            if (c.getResponseCode() >= 400) return "I could not get a quote right now.";
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
            }
            JSONObject q = new JSONArray(sb.toString()).getJSONObject(0);
            return q.getString("q") + ". By " + q.getString("a") + ".";
        } catch (Exception e) {
            return "I could not get a quote right now.";
        }
    }
}
