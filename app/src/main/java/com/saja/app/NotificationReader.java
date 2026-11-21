package com.saja.app;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class NotificationReader extends NotificationListenerService {
    public static final List<String> RECENT = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || sbn.getPackageName().equals(getPackageName())) return;
        Bundle extras = sbn.getNotification().extras;
        if (extras == null) return;
        CharSequence title = extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence text = extras.getCharSequence(Notification.EXTRA_TEXT);
        String t = title == null ? "" : title.toString();
        String body = text == null ? "" : text.toString();
        if (t.isEmpty() && body.isEmpty()) return;
        String app = sbn.getPackageName().replaceAll(".*\\.", "");
        String line = app + ": " + t + " " + body.replaceAll("\\d{4,8}", "****");
        RECENT.add(line.trim());
        while (RECENT.size() > 10) RECENT.remove(0);
    }
}
