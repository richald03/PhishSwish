package com.example.phishswish.utils;

import android.util.Log;
import com.example.phishswish.BuildConfig;
import java.util.ArrayList;
import java.util.List;

public class GeminiKeyManager {
    private static final String TAG = "GeminiKeyManager";
    private final List<String> keys;
    private int currentIndex;

    public GeminiKeyManager() {
        keys = new ArrayList<>();
        // Load keys from BuildConfig
        if (isValidKey(BuildConfig.GEMINI_API_KEY_1)) keys.add(BuildConfig.GEMINI_API_KEY_1);
        if (isValidKey(BuildConfig.GEMINI_API_KEY_2)) keys.add(BuildConfig.GEMINI_API_KEY_2);
        if (isValidKey(BuildConfig.GEMINI_API_KEY_3)) keys.add(BuildConfig.GEMINI_API_KEY_3);
        if (isValidKey(BuildConfig.GEMINI_API_KEY_4)) keys.add(BuildConfig.GEMINI_API_KEY_4);
        if (isValidKey(BuildConfig.GEMINI_API_KEY_5)) keys.add(BuildConfig.GEMINI_API_KEY_5);
        if (isValidKey(BuildConfig.GEMINI_API_KEY_6)) keys.add(BuildConfig.GEMINI_API_KEY_6);
        
        currentIndex = 0;
        Log.d(TAG, "GeminiKeyManager initialized with " + keys.size() + " keys.");
    }

    private boolean isValidKey(String key) {
        return key != null && !key.trim().isEmpty() && !key.equals("null");
    }

    public String getCurrentKey() {
        if (keys.isEmpty()) return null;
        return keys.get(currentIndex);
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    public void moveToNextKey() {
        if (currentIndex < keys.size() - 1) {
            currentIndex++;
            Log.d(TAG, "Switched to Gemini API key #" + (currentIndex + 1));
        } else {
            Log.d(TAG, "All Gemini API keys exhausted.");
        }
    }

    public boolean hasMoreKeys() {
        return currentIndex < keys.size() - 1;
    }

    public void reset() {
        currentIndex = 0;
        Log.d(TAG, "GeminiKeyManager reset to first key.");
    }
    
    public int getKeyCount() {
        return keys.size();
    }
}
