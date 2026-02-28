package com.example.phishswish.firebase;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FirestoreService {

    public static void autoSaveScan(
            String snippet,
            String label,
            int confidence,
            List<String> indicators,
            long timestamp,
            String extractedUrl
    ) {
        autoSaveScan(snippet, label, confidence, indicators, timestamp, extractedUrl, null, null, null);
    }

    public static void autoSaveScan(
            String snippet,
            String label,
            int confidence,
            List<String> indicators,
            long timestamp,
            String extractedUrl,
            String aiAnalysis,
            String aiRecommendation,
            String sender
    ) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        String uid = user.getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        Map<String, Object> doc = new HashMap<>();
        doc.put("snippet", snippet);
        doc.put("label", label);
        doc.put("confidence", confidence);
        doc.put("indicators", indicators);
        doc.put("timestamp", FieldValue.serverTimestamp());

        boolean hasAiAnalysis = (aiAnalysis != null);
        doc.put("hasAiAnalysis", hasAiAnalysis);

        if (aiAnalysis != null) doc.put("aiAnalysis", aiAnalysis);
        if (aiRecommendation != null) doc.put("aiRecommendation", aiRecommendation);

        if (extractedUrl != null && !extractedUrl.trim().isEmpty()) {
            doc.put("extractedUrl", extractedUrl);
        }

        if (sender != null && !sender.trim().isEmpty()) {
            doc.put("sender", sender.trim());
        }

        String scanId = String.valueOf(timestamp);

        db.collection("users")
                .document(uid)
                .collection("scans")
                .document(scanId)
                .set(doc);
    }
}
