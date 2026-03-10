package com.example.phishswish.model;

import com.example.phishswish.utils.PercentageUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Represents the normalized, validated output of the Gemini analysis.
 *
 * This is the single source of truth for verdict/category, certainty,
 * explanation text, tips, and AI-detected links.
 */
public class GeminiAnalysisResult implements Serializable {

    public enum Category {
        SAFE,
        SPAM,
        SCAM
    }

    private Category category;
    private int certainty; // 0–100
    private String why;

    private GeminiAnalysisResult(Category category,
                                 int certainty,
                                 String why) {
        this.category = category;
        this.certainty = PercentageUtils.clampInt(certainty);
        this.why = (why != null ? why.trim() : "");
    }

    public Category getCategory() {
        return category;
    }

    public int getCertainty() {
        return certainty;
    }

    public String getWhy() {
        return why;
    }

    public List<String> getTips() {
        return new ArrayList<>();
    }

    public List<String> getDetectedLinks() {
        return new ArrayList<>();
    }

    /**
     * Simple factory for callers that already have validated values
     * (e.g., history loading).
     */
    public static GeminiAnalysisResult fromValues(Category category,
                                                  int certainty,
                                                  String why,
                                                  List<String> tips,
                                                  List<String> detectedLinks) {
        return new GeminiAnalysisResult(category, certainty, why);
    }

    public static GeminiAnalysisResult fromJson(String json) throws JSONException {
        if (json == null) {
            throw new JSONException("Empty Gemini JSON");
        }

        // Be forgiving to Gemini formatting: strip markdown fences and
        // extract the first JSON object if there is extra text.
        String cleaned = extractFirstJsonObject(json);

        JSONObject root = new JSONObject(cleaned);

        String rawCategory = root.optString("category", "").toUpperCase(Locale.ROOT).trim();
        Category category = parseCategory(rawCategory);

        int certainty = root.has("certainty") ? root.optInt("certainty", 50) : 50;
        certainty = PercentageUtils.clampInt(certainty);

        String why = root.optString("why", "").trim();

        // Final validation: category must be one of SAFE/SPAM/SCAM.
        if (category == null) {
            throw new JSONException("Invalid or missing category");
        }

        return new GeminiAnalysisResult(category, certainty, why);
    }

    /**
     * Extract the first top-level JSON object from an arbitrary Gemini response.
     * Handles cases where the model wraps output in markdown fences or adds prose.
     */
    private static String extractFirstJsonObject(String raw) throws JSONException {
        if (raw == null) {
            throw new JSONException("Gemini output is null");
        }

        String s = raw.trim();

        // Strip common markdown code fences anywhere in the text to avoid
        // confusing the JSON parser (```json, ```).
        if (s.contains("```")) {
            s = s.replace("```json", "")
                 .replace("```JSON", "")
                 .replace("```", "");
            s = s.trim();
        }

        int start = s.indexOf('{');
        if (start == -1) {
            throw new JSONException("No JSON object found in Gemini output");
        }

        int depth = 0;
        boolean inString = false;
        boolean escape = false;

        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);

            if (escape) {
                // Current char is escaped; skip special handling.
                escape = false;
                continue;
            }

            if (c == '\\') {
                escape = true;
                continue;
            }

            if (c == '"') {
                inString = !inString;
                continue;
            }

            if (inString) {
                // Ignore braces that appear inside string values.
                continue;
            }

            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return s.substring(start, i + 1).trim();
                }
            }
        }

        throw new JSONException("Unterminated JSON object in Gemini output");
    }

    private static Category parseCategory(String raw) {
        if (raw == null) return null;
        switch (raw) {
            case "SAFE":
                return Category.SAFE;
            case "SPAM":
                return Category.SPAM;
            case "SCAM":
                return Category.SCAM;
            default:
                return null;
        }
    }

    // No fallback-to-SPAM here: callers should handle errors and
    // switch the UI into an explicit error state instead of faking results.
}

