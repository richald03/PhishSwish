package com.example.phishswish.ui.views;

import android.content.Context;
import com.example.phishswish.R;
import com.google.mlkit.vision.text.Text;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ArPatternHighlighter {

    private static final String[] URL_PATTERNS = {"http", "https", "www", "bit.ly", "tinyurl", "t.co"};
    private static final String[] OTP_PATTERNS = {"otp", "verification code", "passcode", "code is"};
    private static final String[] URGENCY_PATTERNS = {"urgent", "immediately", "act now", "claim now", "suspended"};
    private static final String[] BRAND_PATTERNS = {"gcash", "maya", "bdo", "bpi", "shopee", "lazada"};

    public static List<OverlayItem> detectPatterns(Context context, Text text) {
        List<OverlayItem> items = new ArrayList<>();
        if (text == null) return items;

        for (Text.TextBlock block : text.getTextBlocks()) {
            String blockText = block.getText().toLowerCase(Locale.ROOT);

            // --- Priority-based single-overlay-per-block evaluation ---
            // Evaluate in order: URL > OTP > Urgency > Brand > Safe.
            // The first matching category wins; lower-priority overlays are skipped.

            // Priority 1 — Suspicious URL
            boolean matched = false;
            for (String pattern : URL_PATTERNS) {
                if (blockText.contains(pattern)) {
                    items.add(new OverlayItem(block.getBoundingBox(),
                            "🔴 " + context.getString(R.string.overlay_url_detected),
                            OverlayItem.Type.SUSPICIOUS_URL));
                    matched = true;
                    break;
                }
            }
            if (matched) continue;

            // Priority 2 — OTP request
            for (String pattern : OTP_PATTERNS) {
                if (blockText.contains(pattern)) {
                    items.add(new OverlayItem(block.getBoundingBox(),
                            "🟠 " + context.getString(R.string.overlay_otp_detected),
                            OverlayItem.Type.OTP_REQUEST));
                    matched = true;
                    break;
                }
            }
            if (matched) continue;

            // Priority 3 — Urgency tactic
            for (String pattern : URGENCY_PATTERNS) {
                if (blockText.contains(pattern)) {
                    items.add(new OverlayItem(block.getBoundingBox(),
                            "🟡 " + context.getString(R.string.overlay_urgency_detected),
                            OverlayItem.Type.URGENCY_TACTIC));
                    matched = true;
                    break;
                }
            }
            if (matched) continue;

            // Priority 4 — Brand mention (informational caution; blocks safe overlay)
            for (String pattern : BRAND_PATTERNS) {
                if (blockText.contains(pattern)) {
                    items.add(new OverlayItem(block.getBoundingBox(),
                            "🔵 " + context.getString(R.string.overlay_brand_detected),
                            OverlayItem.Type.BRAND_MENTION));
                    matched = true;
                    break;
                }
            }
            // No safe overlay — only risky/brand overlays are shown.
        }
        return items;
    }
}
