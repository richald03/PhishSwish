package com.example.phishswish.utils;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FeatureExtractor {

    private static final Pattern OTP_CODE_PATTERN = Pattern.compile("\\b\\d{4,8}\\b");
    private static final Pattern SHORT_LINK_PATTERN = Pattern.compile("\\b(bit\\.ly|tinyurl\\.com|goo\\.gl|ow\\.ly|t\\.co|is\\.gd)/\\S+", Pattern.CASE_INSENSITIVE);
    private static final Pattern URL_PATTERN = Pattern.compile("(https?://\\S+|www\\.\\S+)", Pattern.CASE_INSENSITIVE);

    private FeatureExtractor() {}

    public static class RuleSignals {
        public int urlCount;
        public boolean hasUrl;
        public boolean hasShortLink;
        public boolean asksForOtp;
        public boolean asksForPassword;
        public boolean asksForPin;
        public boolean asksForBankDetails;
        public boolean hasUrgencyThreat;
        public boolean containsMoneyBait;
        public boolean otpFormatLikely;
        public boolean senderLooksLikeOfficialShortcode;
        public float ruleRiskScore;
    }

    public static RuleSignals extract(String senderRaw, String messageRaw) {
        RuleSignals s = new RuleSignals();
        String sender = senderRaw != null ? senderRaw.trim() : "";
        String msg = messageRaw != null ? messageRaw.trim() : "";
        String lower = msg.toLowerCase(Locale.ROOT);

        // URLs
        Matcher urlMatcher = URL_PATTERN.matcher(msg);
        while (urlMatcher.find()) {
            s.urlCount++;
        }
        s.hasUrl = s.urlCount > 0;

        Matcher shortMatcher = SHORT_LINK_PATTERN.matcher(msg);
        s.hasShortLink = shortMatcher.find();

        // Credential and OTP asks
        s.asksForOtp = lower.contains("otp") || lower.contains("one-time password") || lower.contains("one time password") || lower.contains("verification code");
        s.asksForPassword = lower.contains("password") || lower.contains("passcode");
        s.asksForPin = lower.contains("pin") || lower.contains("p.i.n");
        s.asksForBankDetails = lower.contains("bank account") || lower.contains("account number") || lower.contains("card number") || lower.contains("cvv") || lower.contains("security code");

        // Urgency / threat
        s.hasUrgencyThreat =
                lower.contains("account locked") ||
                lower.contains("account suspended") ||
                lower.contains("verify now") ||
                lower.contains("within 24 hours") ||
                lower.contains("immediately") ||
                lower.contains("urgent") ||
                lower.contains("last warning");

        // Money bait
        s.containsMoneyBait =
                lower.contains("won") && lower.contains("prize") ||
                lower.contains("congratulations") ||
                lower.contains("jackpot") ||
                lower.contains("free") && lower.contains("gift") ||
                lower.contains("claim now");

        // OTP-like format: short message, code + keywords, few or no links
        boolean hasCode = OTP_CODE_PATTERN.matcher(msg).find();
        int length = msg.length();
        s.otpFormatLikely = hasCode && s.asksForOtp && length < 220 && s.urlCount <= 1;

        // Sender shortcode (numeric up to 6–8 chars)
        String senderDigits = sender.replaceAll("\\D", "");
        s.senderLooksLikeOfficialShortcode = !senderDigits.isEmpty()
                && senderDigits.length() >= 3
                && senderDigits.length() <= 8
                && senderDigits.length() == sender.length();

        // Simple weighted risk score
        float score = 0f;
        if (s.hasUrl) score += 20f;
        if (s.hasShortLink) score += 15f;
        if (s.asksForPassword || s.asksForPin || s.asksForBankDetails) score += 30f;
        if (s.asksForOtp) score += 15f;
        if (s.hasUrgencyThreat) score += 15f;
        if (s.containsMoneyBait) score += 15f;
        if (s.otpFormatLikely && !s.hasUrl) score -= 15f; // likely benign OTP without links
        if (s.senderLooksLikeOfficialShortcode && s.otpFormatLikely) score -= 10f;

        if (score < 0f) score = 0f;
        if (score > 100f) score = 100f;
        s.ruleRiskScore = score;

        return s;
    }
}

