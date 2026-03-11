package com.example.phishswish.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class LoginAttemptManager {
    private static final String PREF_NAME = "LoginAttemptPrefs";
    private static final String KEY_FAILED_ATTEMPTS = "failed_attempt_count";
    private static final String KEY_LOCKOUT_END_TIME = "lockout_end_time";
    
    private static final int MAX_ATTEMPTS = 3;
    private static final long LOCKOUT_DURATION_MS = 5 * 60 * 1000; // 5 minutes

    private final SharedPreferences prefs;

    public LoginAttemptManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        checkAndResetIfExpired();
    }

    private void checkAndResetIfExpired() {
        long lockoutEndTime = prefs.getLong(KEY_LOCKOUT_END_TIME, 0);
        if (lockoutEndTime > 0 && System.currentTimeMillis() >= lockoutEndTime) {
            resetAttempts();
        }
    }

    public boolean isLockedOut() {
        checkAndResetIfExpired();
        long lockoutEndTime = prefs.getLong(KEY_LOCKOUT_END_TIME, 0);
        return System.currentTimeMillis() < lockoutEndTime;
    }

    public long getRemainingLockoutMillis() {
        long lockoutEndTime = prefs.getLong(KEY_LOCKOUT_END_TIME, 0);
        long remaining = lockoutEndTime - System.currentTimeMillis();
        return Math.max(0, remaining);
    }

    public int getRemainingAttempts() {
        return Math.max(0, MAX_ATTEMPTS - prefs.getInt(KEY_FAILED_ATTEMPTS, 0));
    }

    public void recordFailedAttempt() {
        if (isLockedOut()) return;

        int attempts = prefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1;
        SharedPreferences.Editor editor = prefs.edit();
        
        if (attempts >= MAX_ATTEMPTS) {
            long lockoutEndTime = System.currentTimeMillis() + LOCKOUT_DURATION_MS;
            editor.putLong(KEY_LOCKOUT_END_TIME, lockoutEndTime);
        } else {
            editor.putInt(KEY_FAILED_ATTEMPTS, attempts);
        }
        editor.apply();
    }
    
    public void resetAttempts() {
        prefs.edit()
            .remove(KEY_FAILED_ATTEMPTS)
            .remove(KEY_LOCKOUT_END_TIME)
            .apply();
    }
}
