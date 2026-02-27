package com.example.phishswish.model;

import java.util.ArrayList;
import java.util.List;

public class ScanIndicators {
    private List<String> indicators;
    private List<String> urls;
    private boolean hasUrl;
    private boolean hasShortenedUrl;

    public ScanIndicators() {
        this.indicators = new ArrayList<>();
        this.urls = new ArrayList<>();
        this.hasUrl = false;
        this.hasShortenedUrl = false;
    }

    public List<String> getIndicators() {
        return indicators;
    }

    public void addIndicator(String indicator) {
        if (!this.indicators.contains(indicator)) {
            this.indicators.add(indicator);
        }
    }

    public List<String> getUrls() {
        return urls;
    }

    public void setUrls(List<String> urls) {
        this.urls = urls;
        this.hasUrl = !urls.isEmpty();
    }

    public boolean isHasUrl() {
        return hasUrl;
    }

    public boolean isHasShortenedUrl() {
        return hasShortenedUrl;
    }

    public void setHasShortenedUrl(boolean hasShortenedUrl) {
        this.hasShortenedUrl = hasShortenedUrl;
    }
}
