package com.example.phishswish.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;

import java.util.Locale;

public class LanguageManager {

    private static final String PREF_NAME = "phishswish_prefs";
    private static final String PREF_KEY = "app_language";
    private static final String DEFAULT_LANGUAGE = "en";

    /**
     * Returns the currently saved language code ("en" or "tl").
     */
    public static String getLanguage(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getString(PREF_KEY, DEFAULT_LANGUAGE);
    }

    /**
     * Persists the selected language code.
     */
    public static void setLanguage(Context context, String languageCode) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(PREF_KEY, languageCode).apply();
    }

    /**
     * Applies the saved locale to the given context and returns the new wrapped context.
     * Call this from Application.attachBaseContext() and every Activity.attachBaseContext().
     */
    public static Context applyLocale(Context base) {
        String lang = getLanguage(base);
        return wrapContext(base, lang);
    }

    /**
     * Wraps the context with the given language locale.
     */
    public static Context wrapContext(Context base, String lang) {
        Locale locale = new Locale(lang);
        Locale.setDefault(locale);

        Configuration config = new Configuration(base.getResources().getConfiguration());
        config.setLocale(locale);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return base.createConfigurationContext(config);
        } else {
            // For older APIs, update configuration directly
            base.getResources().updateConfiguration(config, base.getResources().getDisplayMetrics());
            return base;
        }
    }

    /**
     * Convenience: persist + wrap in one call. Use from setLocale flows.
     */
    public static void setLocale(Context context, String languageCode) {
        setLanguage(context, languageCode);
    }
}
