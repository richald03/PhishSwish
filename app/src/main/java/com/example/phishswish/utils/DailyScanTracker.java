package com.example.phishswish.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class DailyScanTracker {
    private static final String PREF_NAME = "DailyScanTracker";
    private static final String KEY_LAST_RESET = "last_reset_time";
    private static final String KEY_SCAN_COUNT = "scan_count";
    private static final long TWENTY_FOUR_HOURS_MS = 24 * 60 * 60 * 1000L;

    public static void increment(Context context) {
        if (context == null) return;
        String uid = getUid();
        String resetKey = KEY_LAST_RESET + "_" + uid;
        String countKey = KEY_SCAN_COUNT + "_" + uid;
        
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        long lastReset = prefs.getLong(resetKey, 0);
        long now = System.currentTimeMillis();
        int count = prefs.getInt(countKey, 0);
        
        if (now - lastReset >= TWENTY_FOUR_HOURS_MS) {
            lastReset = now;
            count = 1;
        } else {
            count++;
        }
        
        prefs.edit()
             .putLong(resetKey, lastReset)
             .putInt(countKey, count)
             .apply();
    }

    public static int getCount(Context context) {
        if (context == null) return 0;
        String uid = getUid();
        String resetKey = KEY_LAST_RESET + "_" + uid;
        String countKey = KEY_SCAN_COUNT + "_" + uid;
        
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        long lastReset = prefs.getLong(resetKey, 0);
        long now = System.currentTimeMillis();
        
        if (now - lastReset >= TWENTY_FOUR_HOURS_MS) {
            prefs.edit()
                 .putLong(resetKey, now)
                 .putInt(countKey, 0)
                 .apply();
            return 0;
        }
        
        return prefs.getInt(countKey, 0);
    }
    
    private static String getUid() {
        com.google.firebase.auth.FirebaseUser user = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        return user != null ? user.getUid() : "unknown";
    }
}
