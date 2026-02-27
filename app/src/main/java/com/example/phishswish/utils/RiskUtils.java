package com.example.phishswish.utils;

import java.util.Locale;

public final class RiskUtils {

    private RiskUtils() {}

    public static float clampScore(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            return 0f;
        }
        if (value < 0f) return 0f;
        if (value > 100f) return 100f;
        return value;
    }

    public static int clampScoreInt(int value) {
        if (value < 0) return 0;
        if (value > 100) return 100;
        return value;
    }

    /** Accepts 0–1 or 0–100 and normalizes to 0–100. */
    public static float toPercent(float raw) {
        if (Float.isNaN(raw) || Float.isInfinite(raw)) {
            return 0f;
        }
        float v = raw;
        if (v >= 0f && v <= 1f) {
            v *= 100f;
        }
        return clampScore(v);
    }

    public static int toPercentInt(float raw) {
        return Math.round(toPercent(raw));
    }

    public static String formatPercent(float raw) {
        int pct = toPercentInt(raw);
        return String.format(Locale.getDefault(), "%d%%", pct);
    }

    public static String formatPercentFromInt(int value) {
        int pct = clampScoreInt(value);
        return String.format(Locale.getDefault(), "%d%%", pct);
    }
}

