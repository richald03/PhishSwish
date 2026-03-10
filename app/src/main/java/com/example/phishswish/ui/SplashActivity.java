package com.example.phishswish.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

import com.example.phishswish.R;

public class SplashActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            // Use literal strings to ensure no dependency-related crashes
            SharedPreferences sp = getSharedPreferences("phishswish_prefs", MODE_PRIVATE);
            boolean done = sp.getBoolean("onboarding_done", false);

            Intent intent;
            if (!done) {
                intent = new Intent(SplashActivity.this, OnboardingActivity.class);
            } else {
                intent = new Intent(SplashActivity.this, LoginActivity.class);
            }
            
            startActivity(intent);
            // Verify if fade_in/fade_out exist before calling, or use 0
            try {
                overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
            } catch (Exception ignored) {}
            finish();
        }, 2000);
    }
}
