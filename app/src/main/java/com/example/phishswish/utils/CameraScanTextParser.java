package com.example.phishswish.utils;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CameraScanTextParser {

    private static final String[] BRAND_KEYWORDS = {
            "gcash", "maya", "bdo", "bpi", "shopee", "lazada", "paypal", "security", "support"
    };

    // Regex to extract URLs (simple and broad)
    private static final Pattern URL_PATTERN = Pattern.compile(
            "((?:https?://|www\\.)\\S+|\\b(?:bit\\.ly|tinyurl\\.com|t\\.co)\\S+|\\b[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}(?:/\\S*)?)"
    );

    public static class ParsedScanResult {
        public String sender;
        public String message;
        public ArrayList<String> detectedLinks;
        public String fullText;

        public ParsedScanResult(String sender, String message, ArrayList<String> detectedLinks, String fullText) {
            this.sender = sender;
            this.message = message;
            this.detectedLinks = detectedLinks;
            this.fullText = fullText;
        }
    }

    public static ParsedScanResult parse(Context context, String ocrText) {
        if (ocrText == null || ocrText.trim().isEmpty()) {
            return new ParsedScanResult(SenderDetector.UNKNOWN, "", new ArrayList<>(), "");
        }

        String fullText = ocrText.trim();
        String lowercase = fullText.toLowerCase();
        
        String sender = null;
        String messageBody = fullText;
        ArrayList<String> detectedLinks = new ArrayList<>();

        // Extract links first so they don't mess up sender parsing
        Matcher matcher = URL_PATTERN.matcher(fullText);
        while (matcher.find()) {
            String link = matcher.group(1);
            if (link != null && !link.isEmpty()) {
                // Ensure no trailing symbols
                while(link.endsWith(".") || link.endsWith(",") || link.endsWith(":") || link.endsWith(";")) {
                    link = link.substring(0, link.length() - 1);
                }
                detectedLinks.add(link);
            }
        }

        // --- Sender Detection ---
        sender = SenderDetector.detectForCameraScan(context, fullText);

        // Remove detected links from the message body to keep it clean
        for (String link : detectedLinks) {
            messageBody = messageBody.replace(link, "").trim();
        }

        if (sender == null || sender.equalsIgnoreCase("Unknown") || sender.equalsIgnoreCase("Hindi Kilala")) {
            sender = context != null ? context.getString(com.example.phishswish.R.string.sender_unknown) : SenderDetector.UNKNOWN;
        }

        return new ParsedScanResult(sender, messageBody, detectedLinks, fullText);
    }
}
