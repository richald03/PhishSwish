package com.example.phishswish.firebase;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import com.example.phishswish.utils.CryptoManager;
import com.example.phishswish.utils.SenderDetector;
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
            String extractedUrl,
            String source
    ) {
        autoSaveScan(snippet, label, confidence, indicators, timestamp, extractedUrl, null, null, null, source, null);
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
            String sender,
            String source
    ) {
        autoSaveScan(snippet, label, confidence, indicators, timestamp, extractedUrl, aiAnalysis, aiRecommendation, sender, source, null);
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
            String sender,
            String source,
            List<String> detectedLinks
    ) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        String uid = user.getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        Map<String, Object> doc = new HashMap<>();
        doc.put("snippet", CryptoManager.encrypt(snippet));
        doc.put("label", label);
        doc.put("confidence", confidence);
        doc.put("timestamp", FieldValue.serverTimestamp());

        boolean hasAiAnalysis = (aiAnalysis != null);
        doc.put("hasAiAnalysis", hasAiAnalysis);

        if (aiAnalysis != null) doc.put("aiAnalysis", aiAnalysis);

        if (extractedUrl != null && !extractedUrl.trim().isEmpty()) {
            doc.put("extractedUrl", extractedUrl);
        }

        doc.put("sender", CryptoManager.encrypt(sender == null || sender.trim().isEmpty() ? "Unknown" : sender.trim()));

        if (source != null) {
            doc.put("source", source);
        }

        doc.put("detected_links", detectedLinks != null ? detectedLinks : new java.util.ArrayList<String>());

        String scanId = String.valueOf(timestamp);

        db.collection("users")
                .document(uid)
                .collection("scans")
                .document(scanId)
                .set(doc, SetOptions.merge());
    }
    
    public static void updateLinkStatus(String scanId, String linkStatus) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || scanId == null) return;
        
        Map<String, Object> update = new HashMap<>();
        update.put("link_status", linkStatus);
        
        FirebaseFirestore.getInstance()
                .collection("users")
                .document(user.getUid())
                .collection("scans")
                .document(scanId)
                .set(update, SetOptions.merge());
    }
}
