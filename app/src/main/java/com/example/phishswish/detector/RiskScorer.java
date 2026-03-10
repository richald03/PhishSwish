package com.example.phishswish.detector;

import com.example.phishswish.model.ScanIndicators;
import com.example.phishswish.model.ScanResult;

import java.util.List;

public class RiskScorer {

    public ScanResult score(ScanIndicators indicators, String originalText) {
        ScanResult result = new ScanResult();
        
        int totalScore = 0;
        List<String> detected = indicators.getIndicators();

        // Point Mapping
        if (indicators.isHasUrl()) {
            totalScore += 3; // URL +3
        }
        
        if (indicators.isHasShortenedUrl()) {
            totalScore += 2; // Short URL +2
        }

        for (String indicator : detected) {
            switch (indicator) {
                case "OTP Request":
                    totalScore += 4;
                    break;
                case "Personal Info Request":
                    totalScore += 4;
                    break;
                case "Urgency/Threat":
                    totalScore += 2;
                    break;
                case "Money or Prize Hook":
                    totalScore += 2;
                    break;
                case "Brand Impersonation (PH)":
                    totalScore += 2;
                    break;
            }
        }

        result.setScore(totalScore);
        
        int maxScore = 19;
        String label = getLabelForScore(totalScore);
        result.setLabel(label);

        int confidencePercent = computeConfidence(totalScore, maxScore, label);
        result.setConfidence(confidencePercent); // store 55..100 as percent
        
        result.setUrls(new java.util.ArrayList<>(indicators.getUrls()));
        result.setSnippet(originalText);
        result.setTimestamp(System.currentTimeMillis());
        
        // Add reasons/recommendation based on findings
        // (Recommendations removed as per requirement)

        return result;
    }

    private String getLabelForScore(int score) {
        if (score <= 2) return "SAFE";
        if (score <= 6) return "SPAM";
        return "SCAM";
    }

    private int computeConfidence(int score, int maxScore, String label) {
        if (maxScore <= 0) maxScore = 19;

        double ratio = (double) score / (double) maxScore; // 0..1
        ratio = Math.min(1.0, Math.max(0.0, ratio));

        // Tunable curve strength (higher = more aggressive)
        double p = 0.85;

        int conf;

        if ("SAFE".equalsIgnoreCase(label)) {
            // Lower score => higher SAFE confidence
            // score=0 => ~98-99, score near max => drops
            double safeStrength = Math.pow(1.0 - ratio, p);
            conf = (int) Math.round(60 + safeStrength * 40); // 60..100
            // For truly clean messages, be very confident
            if (score == 0) conf = 99;

        } else if ("SCAM".equalsIgnoreCase(label)) {
            // Higher score => higher SCAM confidence
            double scamStrength = Math.pow(ratio, p);
            conf = (int) Math.round(60 + scamStrength * 40); // 60..100

            // If it’s clearly scam by score, allow 100
            if (ratio >= 0.85) conf = 100;

        } else { // SPAM
            // SPAM is usually less certain than SCAM
            // Higher score within SPAM range => higher confidence, but capped
            double spamStrength = Math.pow(ratio, p);
            conf = (int) Math.round(55 + spamStrength * 35); // 55..90
            if (conf > 90) conf = 90;
        }

        if (conf < 55) conf = 55;
        if (conf > 100) conf = 100;

        return conf;
    }

    private String generateRecommendation(String label) {
        switch (label) {
            case "SAFE":
                return "This message looks safe, but always be cautious with unexpected links.";
            case "SPAM":
                return "This looks like promotional spam. Avoid clicking links if you didn't request them.";
            case "SCAM":
                return "DANGER: High risk of phishing. Do not click any links or provide personal information.";
            default:
                return "Stay vigilant and verify the sender.";
        }
    }
}
