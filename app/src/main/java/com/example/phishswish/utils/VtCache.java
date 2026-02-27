package com.example.phishswish.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class VtCache {

    private static final String PREF = "vt_cache";
    private static final long CACHE_TIME = 24L * 60L * 60L * 1000L;

    public static String get(Context context, String url) {
        SharedPreferences sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);

        long time = sp.getLong(url + "_time", 0);
        if (System.currentTimeMillis() - time > CACHE_TIME) return null;

        return sp.getString(url + "_data", null);
    }

    public static void save(Context context, String url, String data) {
        SharedPreferences sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);

        sp.edit()
                .putString(url + "_data", data)
                .putLong(url + "_time", System.currentTimeMillis())
                .apply();
    }
}
