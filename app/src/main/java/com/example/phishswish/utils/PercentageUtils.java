package com.example.phishswish.utils;

import java.util.Locale;

public final class PercentageUtils {

    private PercentageUtils() {
    }

    /**
     * Clamp a raw percentage value to the [0, 100] range.
     */
    public static float clamp(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            return 0f;
        }
        if (value < 0f) return 0f;
        if (value > 100f) return 100f;
        return value;
    }

    /**
     * Normalize a value that may be expressed as:
     * - 0–1 fraction (e.g., 0.92)
     * - 0–100 percentage (e.g., 92)
     */
    public static float normalize(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            return 0f;
        }
        float normalized = value;
        if (normalized >= 0f && normalized <= 1f) {
            normalized *= 100f;
        }
        return clamp(normalized);
    }

    /**
     * Convert a raw value to a clamped integer percentage (0–100).
     */
    public static int toInt(float value) {
        return Math.round(normalize(value));
    }

    /**
     * Clamp an integer percentage to [0, 100].
     */
    public static int clampInt(int value) {
        if (value < 0) return 0;
        if (value > 100) return 100;
        return value;
    }

    /**
     * Format a raw value as a percentage string like "85%".
     */
    public static String format(float value) {
        int pct = toInt(value);
        return String.format(Locale.getDefault(), "%d%%", pct);
    }

    /**
     * Format an integer percentage as a string like "85%".
     */
    public static String formatFromInt(int value) {
        int pct = clampInt(value);
        return String.format(Locale.getDefault(), "%d%%", pct);
    }
}

