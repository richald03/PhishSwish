package com.example.phishswish.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class InboxConsentManager {

    private static final String PREF = "phishswish_inbox_consent";
    private static final String KEY_UID = "uid";
    private static final String KEY_ALLOWED = "allowed";

    public static boolean hasConsent(Context ctx, String uid) {
        if (uid == null) return false;
        SharedPreferences sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        String savedUid = sp.getString(KEY_UID, null);
        boolean allowed = sp.getBoolean(KEY_ALLOWED, false);
        return uid.equals(savedUid) && allowed;
    }

    public static void grantConsent(Context ctx, String uid) {
        if (uid == null) return;
        SharedPreferences sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        sp.edit()
                .putString(KEY_UID, uid)
                .putBoolean(KEY_ALLOWED, true)
                .apply();
    }

    public static void revokeConsent(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        sp.edit().clear().apply();
    }
}
