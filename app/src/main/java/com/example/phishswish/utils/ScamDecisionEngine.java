package com.example.phishswish.utils;

import java.util.ArrayList;
import java.util.List;

public final class ScamDecisionEngine {

    private ScamDecisionEngine() {}

    public static class Decision {
        public String label;          // SAFE / SUSPICIOUS / SCAM (app-facing)
        public int finalScore;        // 0–100
        public List<String> reasons;  // human-readable bullet points
    }

    public static Decision decide(
            float mlScoreRaw,
            float geminiScamScoreRaw,
            FeatureExtractor.RuleSignals signals,
            boolean trustedSender,
            float senderTrustConfidence,
            String senderType,
            String senderTrustReason
    ) {
        Decision d = new Decision();
        d.reasons = new ArrayList<>();

        float mlScore = RiskUtils.toPercent(mlScoreRaw);
        float geminiScore = RiskUtils.clampScore(geminiScamScoreRaw);
        float ruleScore = signals != null ? RiskUtils.clampScore(signals.ruleRiskScore) : 0f;

        // Core risk: balance between ML confidence and rule-based risk.
        // Gemini score (if non-zero) is blended in but never fully overrides the other sources.
        float mlRuleBlend = 0.6f * mlScore + 0.4f * ruleScore;
        float base = 0.7f * mlRuleBlend + 0.3f * geminiScore;

        float trustAdj = 0f;
        float conf = senderTrustConfidence;
        if (conf < 0f) conf = 0f;
        if (conf > 1f) conf = 1f;

        if (trustedSender && conf >= 0.75f) {
            trustAdj = 30f * conf;
        } else {
            trustAdj = 10f * conf;
        }

        boolean strongRuleScam = false;
        if (signals != null) {
            boolean highRedFlags = signals.hasUrl &&
                    (signals.asksForOtp || signals.asksForPassword || signals.hasUrgencyThreat);
            if (highRedFlags) {
                trustAdj = Math.min(trustAdj, 10f);
            }
            if (signals.hasShortLink) {
                trustAdj = Math.min(trustAdj, 5f);
            }
            if (ruleScore >= 70f) {
                trustAdj = Math.min(trustAdj, 10f);
            }

            // Strong rule-based scam conditions: heavy credential ask + threat or money bait.
            strongRuleScam =
                    ruleScore >= 80f ||
                    (signals.hasUrl && (signals.asksForPassword || signals.asksForBankDetails)) ||
                    (signals.hasUrgencyThreat && (signals.asksForOtp || signals.asksForPassword || signals.asksForBankDetails)) ||
                    (signals.containsMoneyBait && signals.hasUrl);

            // Likely benign OTP notifications from official shortcodes with no risky links.
            boolean likelyBenignOtp =
                    signals.otpFormatLikely &&
                    signals.senderLooksLikeOfficialShortcode &&
                    !signals.hasUrl &&
                    !signals.asksForBankDetails &&
                    !signals.asksForPassword;

            if (likelyBenignOtp) {
                // Clamp base risk for benign OTP flows; we will apply this after trust adjustment.
                base = Math.min(base, 25f);
            }
        }

        float finalScore = RiskUtils.clampScore(base - trustAdj);

        // If rules are strongly suspicious, ensure the risk score reflects that.
        if (strongRuleScam && finalScore < 80f) {
            finalScore = 80f;
        }

        int finalInt = Math.round(finalScore);
        d.finalScore = finalInt;

        // Map final risk into three buckets only: SAFE / SPAM / SCAM.
        // Keep SAFE fairly strict so obviously risky content is not misclassified.
        if (finalInt <= 25) {
            d.label = "SAFE";
        } else if (finalInt <= 69) {
            d.label = "SPAM";
        } else {
            d.label = "SCAM";
        }

        if (signals != null) {
            if (signals.hasUrl) {
                d.reasons.add("Contains a link.");
            }
            if (signals.hasShortLink) {
                d.reasons.add("Uses a shortened link.");
            }
            if (signals.asksForOtp) {
                d.reasons.add("Requests an OTP or verification code.");
            }
            if (signals.asksForPassword || signals.asksForPin || signals.asksForBankDetails) {
                d.reasons.add("Asks for sensitive credentials or bank details.");
            }
            if (signals.hasUrgencyThreat) {
                d.reasons.add("Uses urgent or threatening language.");
            }
            if (signals.containsMoneyBait) {
                d.reasons.add("Mentions prizes, free gifts, or similar bait.");
            }
            if (signals.otpFormatLikely && d.finalScore < 60) {
                d.reasons.add("Message looks like a one-time code notification.");
            }
        }

        if (trustedSender) {
            d.reasons.add("Sender appears trusted (confidence " + String.format("%.2f", conf) + ").");
            if (d.finalScore >= 30 && d.finalScore <= 59) {
                d.reasons.add("Sender appears trusted, but message contains risky patterns—verify before acting.");
            }
        }

        // Always include a concise summary reason as a complete sentence.
        String summary = "Overall, this message is classified as " +
                d.label +
                " with a risk score of " +
                finalInt +
                " out of 100.";

        java.util.List<String> original = new java.util.ArrayList<>(d.reasons);
        d.reasons.clear();
        d.reasons.add(summary);
        for (String r : original) {
            if (d.reasons.size() >= 3) break;
            d.reasons.add(r);
        }

        return d;
    }
}

