package com.saja.app;

import android.content.Context;

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

public class ChatHistory {
    private static String day() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    public static void save(Context c, String question, String answer) throws IOException {
        String stamp = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date());
        String q = question.replace("\n", " ").replace("|", "/");
        String a = answer.replace("\n", " ");
        try (FileWriter w = new FileWriter(new File(c.getFilesDir(), "history.txt"), true)) {
            w.write(stamp + "|" + q + "|" + a + "\n");
        }
    }

    public static List<String> lines(Context c) {
        List<String> out = new ArrayList<>();
        File f = new File(c.getFilesDir(), "history.txt");
        if (!f.exists()) return out;
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            String l;
            while ((l = r.readLine()) != null) {
                if (!l.trim().isEmpty()) out.add(l);
            }
        } catch (IOException e) {
            // return whatever was read
        }
        return out;
    }

    public static String search(Context c, String term) {
        String t = term.toLowerCase(Locale.US).trim();
        if (t.isEmpty()) return "Say search history, then a word.";
        List<String> hits = new ArrayList<>();
        for (String l : lines(c)) {
            if (l.toLowerCase(Locale.US).contains(t)) hits.add(l);
        }
        if (hits.isEmpty()) return "No saved answers mention " + t + ".";
        StringBuilder sb = new StringBuilder("I found " + hits.size() + " saved answers. The latest questions are. ");
        for (int i = Math.max(0, hits.size() - 3); i < hits.size(); i++) {
            String[] p = hits.get(i).split("\\|", 3);
            if (p.length == 3) sb.append(p[1]).append(". ");
        }
        return sb.toString();
    }

    public static String export(Context c) throws IOException {
        File dir = c.getExternalFilesDir(null);
        if (dir == null) dir = c.getFilesDir();
        File out = new File(dir, "saja_history.md");
        try (PrintWriter w = new PrintWriter(new FileWriter(out))) {
            w.println("# SAJA history");
            w.println();
            for (String l : lines(c)) {
                String[] p = l.split("\\|", 3);
                if (p.length == 3) {
                    w.println("## " + p[0]);
                    w.println("**Question:** " + p[1]);
                    w.println();
                    w.println("**Answer:** " + p[2]);
                    w.println();
                }
            }
        }
        return out.getAbsolutePath();
    }

    private static int[] readUsage(Context c, String today) {
        int todayCount = 0;
        int total = 0;
        File f = new File(c.getFilesDir(), "usage.txt");
        if (f.exists()) {
            try (BufferedReader r = new BufferedReader(new FileReader(f))) {
                String[] p = r.readLine().split("\\|");
                total = Integer.parseInt(p[2]);
                if (p[0].equals(today)) todayCount = Integer.parseInt(p[1]);
            } catch (Exception e) {
                // start counting again if the file is damaged
            }
        }
        return new int[]{todayCount, total};
    }

    public static void countUse(Context c) throws IOException {
        String today = day();
        int[] u = readUsage(c, today);
        try (FileWriter w = new FileWriter(new File(c.getFilesDir(), "usage.txt"))) {
            w.write(today + "|" + (u[0] + 1) + "|" + (u[1] + 1));
        }
    }

    public static String usageText(Context c) {
        int[] u = readUsage(c, day());
        return "You asked " + u[0] + " questions today and " + u[1] + " in total.";
    }
}
