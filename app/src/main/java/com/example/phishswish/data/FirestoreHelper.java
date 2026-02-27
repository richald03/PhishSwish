package com.example.phishswish.data;

import android.content.Context;
import android.widget.Toast;

import com.example.phishswish.model.ScanResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class FirestoreHelper {

    private final FirebaseFirestore db;
    private final FirebaseAuth auth;
    private final Context context;

    public FirestoreHelper(Context context) {
        this.context = context;
        this.db = FirebaseFirestore.getInstance();
        this.auth = FirebaseAuth.getInstance();
    }

    /**
     * Builds the scan document and saves it to Firestore using an auto-generated ID.
     * Path: users/{uid}/scans/{scanId}
     */
    public void saveScanToCloud(ScanResult result) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        String uid = user.getUid();

        // Build the fields as specified in Step 4.8
        Map<String, Object> scanDoc = new HashMap<>();
        scanDoc.put("snippet", result.getSnippet());
        scanDoc.put("label", result.getLabel());
        scanDoc.put("confidence", (int) (result.getConfidence() * 100));
        scanDoc.put("indicators", result.getIndicators());
        scanDoc.put("timestamp", System.currentTimeMillis());
        
        if (result.getUrls() != null && !result.getUrls().isEmpty()) {
            scanDoc.put("extractedUrl", result.getUrls().get(0));
        }

        // Write to Firestore using  Auto-generate scanId
        db.collection("users")
                .document(uid)
                .collection("scans")
                .add(scanDoc)
                .addOnSuccessListener(docRef -> {
                    Toast.makeText(context, "Saved to history & cloud!", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(context, "Cloud save failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }
}
