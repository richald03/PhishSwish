package com.example.phishswish;

import android.app.Application;
import android.content.Context;

import com.example.phishswish.utils.LanguageManager;

public class PhishSwishApp extends Application {

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LanguageManager.applyLocale(base));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        com.example.phishswish.utils.NetworkMonitor.init(this);
    }
}
