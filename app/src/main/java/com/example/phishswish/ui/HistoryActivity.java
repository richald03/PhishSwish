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

        ImageButton btnDeleteAll = findViewById(R.id.btnDeleteAll);
        if (btnDeleteAll != null) {
            btnDeleteAll.setOnClickListener(v ->
                    requireInternetOrShowMessage(this::showDeleteAllConfirmation));
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
                i.putStringArrayListExtra("indicators", item.indicators);
                i.putExtra("extractedUrl", item.extractedUrl);
                
                // Pass existing AI data to prevent re-prompting
                i.putExtra("aiAnalysis", item.aiAnalysis);
                i.putExtra("aiRecommendation", item.aiRecommendation);
                
                startActivity(i);
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
                .setTitle("Delete Item")
                .setMessage("Are you sure you want to delete this item?")
                .setPositiveButton("Yes", (dialog, which) ->
                        requireInternetOrShowMessage(() -> deleteSingleHistoryItem(item)))
                .setNegativeButton("No", null)
                .show();
    }

    private void deleteSingleHistoryItem(FirestoreScan item) {
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
                    Toast.makeText(this, "Item removed from history.", Toast.LENGTH_SHORT).show();
                    loadHistoryFromFirestore();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to delete item.", Toast.LENGTH_SHORT).show();
                });
    }

    private void setupFilters() {
        chipAll.setOnClickListener(v -> filterHistory("ALL"));
        chipSafe.setOnClickListener(v -> filterHistory("SAFE"));
        chipSpam.setOnClickListener(v -> filterHistory("SPAM"));
        chipScam.setOnClickListener(v -> filterHistory("SCAM"));
    }

    private void filterHistory(String category) {
        updateChipUI(category);
        ArrayList<FirestoreScan> filtered = new ArrayList<>();
        
        if (category.equals("ALL")) {
            filtered.addAll(fullList);
        } else {
            for (FirestoreScan scan : fullList) {
                if (scan.label != null && scan.label.equalsIgnoreCase(category)) {
                    filtered.add(scan);
                }
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
                chips[i].setTextColor(Color.parseColor("#4B5563"));
            }
        }
    }

    private void showDeleteAllConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle("Delete History")
                .setMessage("Are you sure you want to delete all your scan history?")
                .setPositiveButton("Yes", (dialog, which) -> deleteAllHistory())
                .setNegativeButton("No", null)
                .show();
    }

    private void deleteAllHistory() {
        if (!isOnline()) return;
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        CollectionReference scansRef = db.collection("users")
                .document(user.getUid())
                .collection("scans");

        scansRef.get().addOnSuccessListener(query -> {
            if (query.isEmpty()) {
                Toast.makeText(this, "No history to delete.", Toast.LENGTH_SHORT).show();
                return;
            }
            WriteBatch batch = db.batch();
            for (QueryDocumentSnapshot doc : query) {
                // Soft Delete: Mark as hidden so Category counts reset but Scans Today remains
                batch.update(doc.getReference(), "hiddenInHistory", true);
            }
            batch.commit().addOnSuccessListener(aVoid -> {
                Toast.makeText(this, "History cleared.", Toast.LENGTH_SHORT).show();
                loadHistoryFromFirestore(); 
            }).addOnFailureListener(e -> {
                Toast.makeText(this, "Failed to delete history.", Toast.LENGTH_SHORT).show();
            });
        });
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
                        String snippet = doc.getString("snippet");
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
                        
                        // Retrieve saved AI data from Firestore
                        String aiAnalysis = doc.getString("aiAnalysis");
                        String aiRecommendation = doc.getString("aiRecommendation");

                        ArrayList<String> indicators = new ArrayList<>();
                        Object rawIndicators = doc.get("indicators");
                        if (rawIndicators instanceof java.util.List) {
                            for (Object o : (java.util.List<?>) rawIndicators) {
                                if (o != null) indicators.add(String.valueOf(o));
                            }
                        }

                        FirestoreScan scan = new FirestoreScan(docId, snippet, label, confidence, timestamp, indicators, extractedUrl);
                        scan.aiAnalysis = aiAnalysis;
                        scan.aiRecommendation = aiRecommendation;
                        fullList.add(scan);
                    }

                    filterHistory("ALL"); 
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to fetch history.", Toast.LENGTH_SHORT).show();
                });
    }
}
