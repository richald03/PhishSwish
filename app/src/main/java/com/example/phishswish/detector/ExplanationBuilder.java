package com.example.phishswish.detector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExplanationBuilder {

    private static final Map<String, String> INDICATOR_REASONS = new HashMap<>();

    static {
        INDICATOR_REASONS.put("OTP Request", "The message asks for a one-time password (OTP), which is a common tactic to hijack accounts.");
        INDICATOR_REASONS.put("Urgency/Threat", "It uses high-pressure language or threats (e.g., 'account suspended') to force a quick response.");
        INDICATOR_REASONS.put("Money or Prize Hook", "It promises unexpected financial gain or prizes to lure you into clicking a malicious link.");
        INDICATOR_REASONS.put("Brand Impersonation (PH)", "The message mentions a well-known brand but often comes from an unofficial source.");
        INDICATOR_REASONS.put("Personal Info Request", "It asks for sensitive personal or banking information that legitimate companies rarely request via SMS.");
        INDICATOR_REASONS.put("Shortened URL Detected", "It uses a shortened link (like bit.ly) to hide the true destination of the URL.");
    }

    public List<String> buildReasons(List<String> indicators) {
        List<String> reasons = new ArrayList<>();
        if (indicators == null) return reasons;

        for (String indicator : indicators) {
            String reason = INDICATOR_REASONS.get(indicator);
            if (reason != null) {
                reasons.add(reason);
            }
        }
        
        if (reasons.isEmpty() && indicators.size() > 0) {
            reasons.add("Multiple suspicious patterns were detected in the message content.");
        }

        return reasons;
    }

    public String getRecommendation(String label) {
        if (label == null) return "Always be cautious with messages from unknown senders.";

        switch (label.toUpperCase()) {
            case "SAFE":
                return "This message appears safe. However, never share OTPs or click suspicious links even if they look legitimate.";
            case "SPAM":
                return "This looks like unsolicited spam. Do not reply or click any links to avoid being added to more mailing lists.";
            case "SCAM":
                return "DANGER: This message shows strong signs of phishing. Block the sender and delete the message immediately. Do NOT click any links.";
            default:
                return "Exercise caution. If this is an unexpected message about an account, verify it by logging in directly through the official app or website.";
        }
    }
}
