package com.example.phishswish.model;

import java.io.Serializable;
import java.util.ArrayList;

public class ScanResult implements Serializable {

    private String label;
    private float score;
    private float confidence;
    private ArrayList<String> reasons;
    private ArrayList<String> urls;
    private String snippet;
    private long timestamp;

    public ScanResult() {
        // Default constructor
    }

    // Getters for all fields

    public String getLabel() {
        return label;
    }

    public float getScore() {
        return score;
    }

    public float getConfidence() {
        return confidence;
    }

    public ArrayList<String> getReasons() {
        return reasons;
    }

    public ArrayList<String> getUrls() {
        return urls;
    }

    public String getSnippet() {
        return snippet;
    }

    public long getTimestamp() {
        return timestamp;
    }

    // Setters for all fields

    public void setLabel(String label) {
        this.label = label;
    }

    public void setScore(float score) {
        this.score = score;
    }

    public void setConfidence(float confidence) {
        this.confidence = confidence;
    }

    public void setReasons(ArrayList<String> reasons) {
        this.reasons = reasons;
    }

    public void setUrls(ArrayList<String> urls) {
        this.urls = urls;
    }

    public void setSnippet(String snippet) {
        this.snippet = snippet;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
