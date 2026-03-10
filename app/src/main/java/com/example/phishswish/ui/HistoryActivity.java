package com.example.phishswish.ui;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.phishswish.R;
import com.example.phishswish.model.FirestoreScan;
import com.example.phishswish.utils.CryptoManager;
import com.example.phishswish.utils.SenderDetector;
import com.example.phishswish.ui.adapters.HistoryAdapter;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;

public class HistoryActivity extends BaseActivity {

    private RecyclerView rvHistory;
    private TextView tvEmptyState;
    private TextView chipAll, chipSafe, chipSpam, chipScam;
    private ArrayList<FirestoreScan> items = new ArrayList<>();
    private ArrayList<FirestoreScan> fullList = new ArrayList<>();
    private HistoryAdapter adapter;
    private BottomNavigationView bottomNav;
    private ImageButton btnSourceFilter;

    private String selectedVerdictFilter = "ALL";
    private String selectedSourceFilter = "ALL";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_history);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            return insets;
        });

        rvHistory = findViewById(R.id.rvHistory);
        tvEmptyState = findViewById(R.id.tvEmptyState);
        chipAll = findViewById(R.id.chipAll);
        chipSafe = findViewById(R.id.chipSafe);
        chipSpam = findViewById(R.id.chipSpam);
        chipScam = findViewById(R.id.chipScam);
        
        View btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }



        btnSourceFilter = findViewById(R.id.btnSourceFilter);
        if (btnSourceFilter != null) {
            btnSourceFilter.setOnClickListener(v -> showSourceFilterMenu());
        }

        setupFilters();

        rvHistory.setLayoutManager(new LinearLayoutManager(this));

        adapter = new HistoryAdapter(items, new HistoryAdapter.OnHistoryClickListener() {
            @Override
            public void onClick(FirestoreScan item) {
                Intent i = new Intent(HistoryActivity.this, ResultActivity.class);
                i.putExtra("fromHistory", true);
                i.putExtra("label", item.label);
                i.putExtra("confidence", item.confidence);
                i.putExtra("snippet", item.snippet);
                i.putExtra("timestamp", item.timestamp);
                i.putExtra("extractedUrl", item.extractedUrl);
                
                // Pass existing AI data to prevent re-prompting
                i.putExtra("aiAnalysis", item.aiAnalysis);
                i.putExtra("sender", SenderDetector.normalize(HistoryActivity.this, item.sender));

                if (item.detectedLinks != null && !item.detectedLinks.isEmpty()) {
                    i.putStringArrayListExtra("detected_links", item.detectedLinks);
                }
                if (item.linkStatus != null && !item.linkStatus.isEmpty()) {
                    i.putExtra("link_status", item.linkStatus);
                }

                navigateWithLoading(i);
            }

            @Override
            public void onLongClick(FirestoreScan item) {
                showSingleDeleteConfirmation(item);
            }
        });
        rvHistory.setAdapter(adapter);

        bottomNav = findViewById(R.id.bottom_navigation);
        BottomNavHelper.setup(this, bottomNav, null, R.id.nav_history);
    }

    private void showSingleDeleteConfirmation(FirestoreScan item) {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_delete_item_title))
                .setMessage(getString(R.string.dialog_delete_item_message))
                .setPositiveButton(getString(R.string.btn_yes), (dialog, which) -> {
                    deleteItemFromFirestore(item);
                })
                .setNegativeButton(getString(R.string.btn_no), null)
                .show();
    }

    private void deleteItemFromFirestore(FirestoreScan item) {
        if (!isOnline()) return;
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || item.docId == null) return;

        // Soft Delete: Mark as hidden so Category counts reset but Scans Today remains
        FirebaseFirestore.getInstance().collection("users")
                .document(user.getUid())
                .collection("scans")
                .document(item.docId)
                .update("hiddenInHistory", true)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, getString(R.string.msg_item_removed), Toast.LENGTH_SHORT).show();
                    loadHistoryFromFirestore();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, getString(R.string.msg_item_delete_failed), Toast.LENGTH_SHORT).show();
                });
    }

    private void setupFilters() {
        chipAll.setOnClickListener(v -> {
            selectedVerdictFilter = "ALL";
            applyCombinedFilters();
        });
        chipSafe.setOnClickListener(v -> {
            selectedVerdictFilter = "SAFE";
            applyCombinedFilters();
        });
        chipSpam.setOnClickListener(v -> {
            selectedVerdictFilter = "SPAM";
            applyCombinedFilters();
        });
        chipScam.setOnClickListener(v -> {
            selectedVerdictFilter = "SCAM";
            applyCombinedFilters();
        });
    }

    private void showSourceFilterMenu() {
        String[] options = {
                getString(R.string.source_all),
                getString(R.string.source_paste),
                getString(R.string.source_inbox),
                getString(R.string.source_ar)
        };
        String[] values = {"ALL", "paste", "inbox", "ar_scan"};

        int checkedItem = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(selectedSourceFilter)) {
                checkedItem = i;
                break;
            }
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.title_filter_source)
                .setSingleChoiceItems(options, checkedItem, (dialog, which) -> {
                    selectedSourceFilter = values[which];
                    applyCombinedFilters();
                    dialog.dismiss();
                })
                .show();
    }

    private void applyCombinedFilters() {
        updateChipUI(selectedVerdictFilter);
        ArrayList<FirestoreScan> filtered = new ArrayList<>();

        for (FirestoreScan scan : fullList) {
            boolean verdictMatch = selectedVerdictFilter.equals("ALL") || 
                (scan.label != null && scan.label.equalsIgnoreCase(selectedVerdictFilter));
            
            boolean sourceMatch = selectedSourceFilter.equals("ALL") || 
                (scan.source != null && scan.source.equalsIgnoreCase(selectedSourceFilter));
            
            // Special case for old records: if source is null, only show if filter is ALL
            if (scan.source == null && !selectedSourceFilter.equals("ALL")) {
                sourceMatch = false;
            }

            if (verdictMatch && sourceMatch) {
                filtered.add(scan);
            }
        }

        items.clear();
        items.addAll(filtered);
        adapter.notifyDataSetChanged();

        if (items.isEmpty()) {
            tvEmptyState.setVisibility(View.VISIBLE);
            rvHistory.setVisibility(View.GONE);
        } else {
            tvEmptyState.setVisibility(View.GONE);
            rvHistory.setVisibility(View.VISIBLE);
        }
    }

    private void updateChipUI(String selected) {
        TextView[] chips = {chipAll, chipSafe, chipSpam, chipScam};
        String[] titles = {"ALL", "SAFE", "SPAM", "SCAM"};
        
        for (int i = 0; i < chips.length; i++) {
            if (titles[i].equals(selected)) {
                chips[i].setBackgroundResource(R.drawable.bg_chip_selected);
                chips[i].setTextColor(Color.WHITE);
            } else {
                chips[i].setBackgroundResource(R.drawable.bg_chip_unselected);
                chips[i].setTextColor(ContextCompat.getColor(this, R.color.text_muted));
            }
        }
    }



    @Override
    protected void onResume() {
        super.onResume();
        BottomNavHelper.onResume(this, bottomNav, null, R.id.nav_history);
        loadHistoryFromFirestore();
    }

    private void loadHistoryFromFirestore() {
        if (!isOnline()) return;
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("users")
                .document(user.getUid())
                .collection("scans")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(query -> {
                    fullList.clear();

                    for (QueryDocumentSnapshot doc : query) {
                        // Skip hidden items in History screen
                        Boolean hidden = doc.getBoolean("hiddenInHistory");
                        if (hidden != null && hidden) continue;

                        // Skip scans that never completed AI analysis
                        Boolean hasAi = doc.getBoolean("hasAiAnalysis");
                        if (hasAi == null || !hasAi) continue;

                        String docId = doc.getId();
                        String snippet = CryptoManager.decrypt(doc.getString("snippet"));
                        String label = doc.getString("label");

                        long timestamp = 0;
                        Object tsObj = doc.get("timestamp");
                        if (tsObj instanceof com.google.firebase.Timestamp) {
                            timestamp = ((com.google.firebase.Timestamp) tsObj).toDate().getTime();
                        } else if (tsObj instanceof Long) {
                            timestamp = (Long) tsObj;
                        }

                        Long confLong = doc.getLong("confidence");
                        int confidence = confLong == null ? 0 : confLong.intValue();

                        String extractedUrl = doc.getString("extractedUrl");
                        String sender = CryptoManager.decrypt(doc.getString("sender"));
                        String source = doc.getString("source");

                        // Retrieve saved AI data from Firestore
                        String aiAnalysis = doc.getString("aiAnalysis");

                        java.util.List<?> linksRaw = (java.util.List<?>) doc.get("detected_links");
                        ArrayList<String> detectedLinks = new ArrayList<>();
                        if (linksRaw != null) {
                            for (Object obj : linksRaw) {
                                if (obj instanceof String) {
                                    detectedLinks.add((String) obj);
                                }
                            }
                        }

                        FirestoreScan scan = new FirestoreScan(docId, snippet, label, confidence, timestamp, extractedUrl);
                        scan.aiAnalysis = aiAnalysis;
                        scan.sender = sender;
                        scan.source = source;
                        scan.detectedLinks = detectedLinks;
                        scan.linkStatus = doc.getString("link_status");
                        fullList.add(scan);
                    }

                    applyCombinedFilters(); 
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, getString(R.string.msg_fetch_failed), Toast.LENGTH_SHORT).show();
                });
    }
}
