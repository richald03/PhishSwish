package com.example.phishswish.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.phishswish.R;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class FaqsActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_faqs);

        setupFaqInteractions();
        setupNavigation();

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnContactSupport).setOnClickListener(v -> 
            Toast.makeText(this, "Opening support channel...", Toast.LENGTH_SHORT).show());
    }

    private void setupFaqInteractions() {
        setupFaqItem(R.id.faqHeader1, R.id.faqAnswer1, R.id.faqChevron1);
        setupFaqItem(R.id.faqHeader2, R.id.faqAnswer2, R.id.faqChevron2);
        setupFaqItem(R.id.faqHeader3, R.id.faqAnswer3, R.id.faqChevron3);
        setupFaqItem(R.id.faqHeader4, R.id.faqAnswer4, R.id.faqChevron4);
    }

    private void setupFaqItem(int headerId, int answerId, int chevronId) {
        View header = findViewById(headerId);
        TextView answer = findViewById(answerId);
        ImageView chevron = findViewById(chevronId);

        header.setOnClickListener(v -> {
            boolean isExpanded = answer.getVisibility() == View.VISIBLE;
            answer.setVisibility(isExpanded ? View.GONE : View.VISIBLE);
            chevron.setRotation(isExpanded ? 0 : 90);
        });
    }

    private void setupNavigation() {
        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(R.id.nav_faqs);
            bottomNav.setOnItemSelectedListener(item -> {
                int id = item.getItemId();
                if (id == R.id.nav_faqs) return true;

                Intent intent = null;
                // Placeholders for other activities
                if (id == R.id.nav_privacy) {
                    Toast.makeText(this, "Privacy Policy screen coming soon", Toast.LENGTH_SHORT).show();
                } else if (id == R.id.nav_about) {
                    Toast.makeText(this, "About screen coming soon", Toast.LENGTH_SHORT).show();
                }

                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                    return true;
                }
                return false;
            });
        }
    }
}
