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


        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnContactSupport).setOnClickListener(v -> 
            Toast.makeText(this, getString(R.string.msg_opening_support), Toast.LENGTH_SHORT).show());
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


}
