package com.example.phishswish.model;

import java.io.Serializable;
import java.util.ArrayList;

public class FirestoreScan implements Serializable {
    public String docId;
    public String snippet;
    public String label;
    public int confidence;
    public long timestamp;
    public ArrayList<String> indicators;
    public String extractedUrl;
    public String aiAnalysis;
    public String aiRecommendation;

    public FirestoreScan() {} // required

    public FirestoreScan(String docId, String snippet, String label, int confidence,
                         long timestamp, ArrayList<String> indicators, String extractedUrl) {
        this.docId = docId;
        this.snippet = snippet;
        this.label = label;
        this.confidence = confidence;
        this.timestamp = timestamp;
        this.indicators = indicators;
        this.extractedUrl = extractedUrl;
    }
}
