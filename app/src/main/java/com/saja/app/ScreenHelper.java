package com.saja.app;

import android.accessibilityservice.AccessibilityService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.LinkedHashSet;
import java.util.List;

public class ScreenHelper extends AccessibilityService {
    private static ScreenHelper instance;

    public static boolean isOn() {
        return instance != null;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.createNotificationChannel(new NotificationChannel("saja_screen", "SAJA screen", NotificationManager.IMPORTANCE_LOW));
            Notification n = new Notification.Builder(this, "saja_screen")
                    .setContentTitle("SAJA screen helper")
                    .setContentText("Connected. You can ask what is on your screen.")
                    .setSmallIcon(android.R.drawable.ic_menu_view)
                    .setAutoCancel(true)
                    .build();
            nm.notify(2, n);
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {}

    @Override
    public boolean onUnbind(Intent intent) {
        instance = null;
        return super.onUnbind(intent);
    }

    public static String readScreen() {
        if (instance == null) return "Turn on SAJA screen helper in Accessibility settings first.";
        AccessibilityNodeInfo root = instance.getRootInActiveWindow();
        if (root == null) return "I cannot see the screen right now.";
        LinkedHashSet<String> texts = new LinkedHashSet<>();
        collect(root, texts);
        root.recycle();
        if (texts.isEmpty()) return "I cannot read any text on this screen.";
        StringBuilder sb = new StringBuilder("On your screen I see. ");
        int count = 0;
        for (String t : texts) {
            if (count++ >= 40) break;
            sb.append(t).append(". ");
        }
        return sb.toString();
    }

    private static void collect(AccessibilityNodeInfo node, LinkedHashSet<String> out) {
        if (node == null) return;
        CharSequence text = node.getText();
        if (text != null && text.length() > 0) out.add(text.toString().trim());
        CharSequence desc = node.getContentDescription();
        if (desc != null && desc.length() > 0) out.add(desc.toString().trim());
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                collect(child, out);
                child.recycle();
            }
        }
    }

    public static String clickText(String label) {
        if (instance == null) return "Turn on SAJA screen helper in Accessibility settings first.";
        AccessibilityNodeInfo root = instance.getRootInActiveWindow();
        if (root == null) return "I cannot see the screen right now.";
        List<AccessibilityNodeInfo> found = root.findAccessibilityNodeInfosByText(label);
        if (found == null || found.isEmpty()) {
            root.recycle();
            return "I could not find " + label + " on the screen.";
        }
        AccessibilityNodeInfo target = found.get(0);
        AccessibilityNodeInfo node = target;
        while (node != null && !node.isClickable()) {
            AccessibilityNodeInfo parent = node.getParent();
            if (node != target) node.recycle();
            node = parent;
        }
        String result;
        if (node != null && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            result = "Tapped " + label + ".";
        } else {
            result = "I found " + label + " but could not tap it.";
        }
        if (node != null && node != target) node.recycle();
        for (int i = 1; i < found.size(); i++) found.get(i).recycle();
        target.recycle();
        root.recycle();
        return result;
    }

    public static String typeText(String text) {
        if (instance == null) return "Turn on SAJA screen helper in Accessibility settings first.";
        AccessibilityNodeInfo root = instance.getRootInActiveWindow();
        if (root == null) return "I cannot see the screen right now.";
        AccessibilityNodeInfo target = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (target == null) target = findEditable(root);
        if (target == null) {
            root.recycle();
            return "I cannot find a text box. Tap the text box first.";
        }
        Bundle args = new Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
        boolean ok = target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
        target.recycle();
        root.recycle();
        return ok ? "Typed the text." : "I could not type into that box.";
    }

    private static AccessibilityNodeInfo findEditable(AccessibilityNodeInfo node) {
        if (node == null) return null;
        if (node.isEditable()) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                AccessibilityNodeInfo found = findEditable(child);
                if (found != null) return found;
                child.recycle();
            }
        }
        return null;
    }
}
