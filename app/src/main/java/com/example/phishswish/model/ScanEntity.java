package com.example.phishswish.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "scans")
public class ScanEntity {

    @PrimaryKey(autoGenerate = true)
    private int id;

    private String snippet;
    private String label;
    private int confidence;
    private String indicatorsJson;
    private String reasonsJson;
    private String extractedUrl;
    private long timestamp;

    public ScanEntity() {
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getSnippet() {
        return snippet;
    }

    public void setSnippet(String snippet) {
        this.snippet = snippet;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public int getConfidence() {
        return confidence;
    }

    public void setConfidence(int confidence) {
        this.confidence = confidence;
    }

    public String getIndicatorsJson() {
        return indicatorsJson;
    }

    public void setIndicatorsJson(String indicatorsJson) {
        this.indicatorsJson = indicatorsJson;
    }

    public String getReasonsJson() {
        return reasonsJson;
    }

    public void setReasonsJson(String reasonsJson) {
        this.reasonsJson = reasonsJson;
    }

    public String getExtractedUrl() {
        return extractedUrl;
    }

    public void setExtractedUrl(String extractedUrl) {
        this.extractedUrl = extractedUrl;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
