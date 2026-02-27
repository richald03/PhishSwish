package com.example.phishswish.detector;

import org.json.JSONObject;
import java.net.URI;

public class VtParser {

    public static class Result {
        public String status; // SAFE / HARMFUL / UNKNOWN
        public int malicious, suspicious, harmless, undetected;
    }

    public static Result parse(String url, String json) throws Exception {
        Result r = new Result();

        JSONObject root = new JSONObject(json);

        // If VT returns an error JSON
        if (root.has("error")) {
            r.status = "UNKNOWN";
            return r;
        }

        // Correct parsing from attributes.last_analysis_stats
        JSONObject stats = root.getJSONObject("data")
                .getJSONObject("attributes")
                .getJSONObject("last_analysis_stats");

        r.malicious = stats.optInt("malicious", 0);
        r.suspicious = stats.optInt("suspicious", 0);
        r.harmless = stats.optInt("harmless", 0);
        r.undetected = stats.optInt("undetected", 0);

        r.status = vtDecision(url, r.malicious, r.suspicious, r.harmless, r.undetected);

        return r;
    }

    private static String extractHost(String url) {
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            if (host == null) return "";
            host = host.toLowerCase();
            if (host.startsWith("www.")) host = host.substring(4);
            return host;
        } catch (Exception e) {
            return "";
        }
    }

    private static boolean isTrustedBigDomain(String host) {
        // Keep list small to avoid abuse
        return host.equals("facebook.com")
                || host.endsWith(".facebook.com")
                || host.equals("google.com")
                || host.endsWith(".google.com")
                || host.equals("youtube.com")
                || host.endsWith(".youtube.com")
                || host.equals("gmail.com")
                || host.endsWith(".gmail.com")
                || host.equals("microsoft.com")
                || host.endsWith(".microsoft.com");
    }

    private static String vtDecision(String url, int malicious, int suspicious, int harmless, int undetected) {
        String host = extractHost(url);

        // Strong evidence of danger
        if (malicious >= 2) return "HARMFUL";
        if (suspicious >= 2) return "HARMFUL";

        // Weak evidence: allowlist major legit domains to avoid false positives
        if (malicious == 1 || suspicious == 1) {
            if (isTrustedBigDomain(host)) return "SAFE";
            return "UNKNOWN";
        }

        // No flags and VT has data
        if (harmless > 0 || undetected > 0) return "SAFE";

        return "UNKNOWN";
    }
}
