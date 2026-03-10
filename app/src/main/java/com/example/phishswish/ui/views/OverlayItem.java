package com.example.phishswish.ui.views;

import android.graphics.Rect;

/**
 * Metadata for highlighting elements over the camera feed.
 */
public class OverlayItem {
    public enum Type {
        SUSPICIOUS_URL,
        OTP_REQUEST,
        URGENCY_TACTIC,
        BRAND_MENTION,
        LIKELY_SAFE
    }

    public final Rect rect;
    public final String label;
    public final Type type;

    public OverlayItem(Rect rect, String label, Type type) {
        this.rect = rect;
        this.label = label;
        this.type = type;
    }
}
