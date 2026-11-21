package com.saja.app;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.BatteryManager;
import android.provider.CalendarContract;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class OfflineTools {
    private static final String[] NAMES = {"battery", "time", "date", "notes", "memory",
            "notifications", "history", "usage", "calendar"};

    public static String list() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < NAMES.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(NAMES[i]);
        }
        return sb.toString();
    }

    public static String run(Context c, String name) {
        switch (name) {
            case "battery": return battery(c);
            case "time": return "It is " + new SimpleDateFormat("h:mm a", Locale.US).format(new Date()) + ".";
            case "date": return "Today is " + new SimpleDateFormat("EEEE, MMMM d", Locale.US).format(new Date()) + ".";
            case "notes": return lastParts(c, "notes.txt", 5, "Your last notes are. ", "You have no notes yet.");
            case "memory": return lastParts(c, "memory.txt", 10, "I remember. ", "I do not remember anything yet.");
            case "notifications": return notifications();
            case "history": return history(c);
            case "usage": return ChatHistory.usageText(c);
            case "calendar": return calendar(c);
            default: return null;
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
}
