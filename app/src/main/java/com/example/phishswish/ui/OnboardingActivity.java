package com.example.phishswish.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.example.phishswish.R;

public class OnboardingActivity extends BaseActivity {

    public static final String PREFS = "phishswish_prefs";
    public static final String KEY_ONBOARDING_DONE = "onboarding_done";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_onboarding);

        Button btnGetStarted = findViewById(R.id.btnGetStarted);

        btnGetStarted.setOnClickListener(v -> {
            SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
            sp.edit().putBoolean(KEY_ONBOARDING_DONE, true).apply();

            navigateWithLoading(LoginActivity.class);
            finish();
        });
    }
}
