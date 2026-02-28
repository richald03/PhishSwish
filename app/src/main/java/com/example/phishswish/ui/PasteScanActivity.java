package com.example.phishswish.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.phishswish.R;
import com.example.phishswish.detector.Detector;
import com.example.phishswish.firebase.FirestoreService;
import com.example.phishswish.model.ScanResult;
import com.example.phishswish.utils.SafeClickListener;
import com.example.phishswish.utils.FeatureExtractor;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class PasteScanActivity extends BaseActivity {

    private TextInputEditText etMessage;
    private TextInputEditText etSender;
    private TextInputLayout tilSender;
    private TextInputLayout tilMessage;
    private Button btnScan;
    private Button btnClear;
    private TextView tvCharCount;
    private TextView tvLastScanStatus;
    private ListenerRegistration lastScanListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_paste_scan);
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            return insets;
        });

        etSender = findViewById(R.id.etSender);
        etMessage = findViewById(R.id.etMessage);
        tilSender = findViewById(R.id.tilSender);
        tilMessage = findViewById(R.id.tilMessage);
        btnScan = findViewById(R.id.btnScan);
        btnClear = findViewById(R.id.btnClear);
        tvCharCount = findViewById(R.id.tvCharCount);
        tvLastScanStatus = findViewById(R.id.tvLastScanStatus);

        etMessage.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (tvCharCount != null) {
                    tvCharCount.setText(s.length() + "/1000");
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        btnScan.setOnClickListener(new SafeClickListener() {
            @Override
            public void onSafeClick(View v, Completion completion) {
                if (tilSender != null) tilSender.setError(null);
                if (tilMessage != null) tilMessage.setError(null);

                String sender = etSender != null && etSender.getText() != null
                        ? etSender.getText().toString().trim()
                        : "";
                String text = etMessage != null && etMessage.getText() != null
                        ? etMessage.getText().toString().trim()
                        : "";

                if (sender.isEmpty()) {
                    if (tilSender != null) {
                        tilSender.setError("Sender is required.");
                    } else {
                        Toast.makeText(PasteScanActivity.this, "Sender is required.", Toast.LENGTH_SHORT).show();
                    }
                    completion.onComplete();
                    return;
                }

                if (text.isEmpty()) {
                    if (tilMessage != null) {
                        tilMessage.setError("Message is required.");
                    } else {
                        Toast.makeText(PasteScanActivity.this, "Please paste a message to scan.", Toast.LENGTH_SHORT).show();
                    }
                    completion.onComplete();
                    return;
                }

                requireInternetOrShowMessage(() -> {
                    performScan(sender, text);
                    completion.onComplete();
                });
            }
        });

        btnClear.setOnClickListener(v -> etMessage.setText(""));
        
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        startLastScanListener();
    }

    private void performScan(String sender, String text) {
        if (!isOnline()) {
            requireInternetOrShowMessage(null);
            return;
        }
        if (text.length() > 1000) text = text.substring(0, 1000);

        // Rule-based analysis only; Gemini will be the single source of truth for final verdict.
        ScanResult ruleResult = Detector.analyze(text);

        long ts = System.currentTimeMillis();
        String snippet = text.replace("\n", " ").trim();
        if (snippet.length() > 120) snippet = snippet.substring(0, 120);

        String extractedUrl = (ruleResult.getUrls() != null && !ruleResult.getUrls().isEmpty()) ? ruleResult.getUrls().get(0) : "";

        if (isOnline()) {
            FirestoreService.autoSaveScan(snippet, ruleResult.getLabel(), (int) ruleResult.getConfidence(),
                    ruleResult.getIndicators(), ts, extractedUrl, null, null, sender);
        }

        Intent intent = new Intent(this, ResultActivity.class);
        intent.putExtra("scan_result", ruleResult);
        intent.putExtra("sender", sender);
        startActivity(intent);
    }

    private void startLastScanListener() {
        if (!isOnline()) return;
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        lastScanListener = FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .collection("scans")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(1)
                .addSnapshotListener((value, error) -> {
                    if (error != null || value == null || value.isEmpty()) {
                        if (tvLastScanStatus != null) {
                            tvLastScanStatus.setText("AI-Powered Detection. No scans yet.");
                        }
                        return;
                    }

                    DocumentSnapshot doc = value.getDocuments().get(0);
                    Object tsObj = doc.get("timestamp");
                    long timestamp = 0;
                    if (tsObj instanceof com.google.firebase.Timestamp) {
                        timestamp = ((com.google.firebase.Timestamp) tsObj).toDate().getTime();
                    } else if (tsObj instanceof Long) {
                        timestamp = (Long) tsObj;
                    }

                    if (timestamp > 0) {
                        String timeAgo = getTimeAgo(timestamp);
                        if (tvLastScanStatus != null) {
                            tvLastScanStatus.setText("AI-Powered Detection. Last scan: " + timeAgo + ".");
                        }
                    }
                });
    }

    private String getTimeAgo(long time) {
        long now = System.currentTimeMillis();
        long diff = now - time;
        if (diff < 60 * 1000) return "Just now";
        if (diff < 60 * 60 * 1000) return (diff / (60 * 1000)) + " mins ago";
        if (diff < 24 * 60 * 60 * 1000) return (diff / (60 * 60 * 1000)) + " hrs ago";
        return new SimpleDateFormat("MMM dd, h:mm a", Locale.getDefault()).format(new Date(time));
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (lastScanListener != null) {
            lastScanListener.remove();
        }
    }
}
