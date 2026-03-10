package com.example.phishswish.detector;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import com.example.phishswish.utils.GeminiKeyManager;
import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class GeminiClient {

    public interface Callback {
        /**
         * Called when Gemini returns a response.
         *
         * For strict JSON mode, the {@code analysis} parameter will contain the raw JSON
         * string returned by the model. {@code recommendation} is kept for backward
         * compatibility but is not used by the current caller.
         */
        void onSuccess(String analysis, String recommendation);

        void onError(String error);
    }

    private static final String TAG = "GeminiClient";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final String MODEL = "models/gemini-2.5-flash";

    private final OkHttpClient http = new OkHttpClient();

    private String endpoint(String apiKey) {
        return "https://generativelanguage.googleapis.com/v1beta/" + MODEL + ":generateContent?key=" + apiKey;
    }

    public void analyze(String apiKey, String prompt, Callback cb) {
        analyzeWithFallback(apiKey, null, prompt, cb);
    }

    public void analyzeWithFallback(String apiKey, GeminiKeyManager keyManager, String prompt, Callback cb) {
        new Thread(() -> {
            try {
                if (keyManager != null) {
                    Log.d(TAG, "Gemini request using key #" + (keyManager.getCurrentIndex() + 1));
                } else {
                    Log.d(TAG, "Gemini request using provided key");
                }
                Log.d(TAG, "Using model: " + MODEL);

                JSONObject body = new JSONObject();
                JSONArray contents = new JSONArray();
                JSONObject contentObj = new JSONObject();
                JSONArray parts = new JSONArray();
                JSONObject partObj = new JSONObject();
                partObj.put("text", prompt);
                parts.put(partObj);
                contentObj.put("parts", parts);
                contents.put(contentObj);
                body.put("contents", contents);

                JSONObject genConfig = new JSONObject();
                genConfig.put("temperature", 0.1);
                // Slightly increased to reduce chance of truncated JSON payloads.
                genConfig.put("maxOutputTokens", 1536);
                body.put("generationConfig", genConfig);

                Request request = new Request.Builder()
                        .url(endpoint(apiKey))
                        .post(RequestBody.create(body.toString(), JSON))
                        .build();

                Response response = http.newCall(request).execute();
                Log.d("GeminiClient", "Gemini HTTP=" + response.code());

                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "";
                    Log.e(TAG, "Gemini error body: " + errorBody);

                    boolean isQuotaExceeded = response.code() == 429 || 
                                             errorBody.toLowerCase().contains("quota exceeded") || 
                                             errorBody.toLowerCase().contains("rate limit exceeded");

                    if (isQuotaExceeded && keyManager != null && keyManager.hasMoreKeys()) {
                        Log.w(TAG, "Quota exceeded on key #" + (keyManager.getCurrentIndex() + 1) + ", switching to next key...");
                        keyManager.moveToNextKey();
                        analyzeWithFallback(keyManager.getCurrentKey(), keyManager, prompt, cb);
                        return;
                    }

                    if (response.code() == 429 || isQuotaExceeded) {
                        cb.onError("AI analysis limit reached for now. Please rely on the detected patterns and try again later.");
                    } else if (response.code() == 401 || response.code() == 403) {
                        cb.onError("AI Analysis is not configured properly. Please contact support.");
                    } else {
                        cb.onError("AI analysis is temporarily unavailable. Please try again later.");
                    }
                    return;
                }

                String res = response.body() != null ? response.body().string() : "";
                JSONObject root = new JSONObject(res);

                JSONArray candidates = root.optJSONArray("candidates");
                if (candidates == null || candidates.length() == 0) {
                    cb.onError("No candidates returned by Gemini.");
                    return;
                }

                JSONObject cand0 = candidates.getJSONObject(0);
                JSONObject content = cand0.getJSONObject("content");
                JSONArray partsOut = content.getJSONArray("parts");
                String text = partsOut.getJSONObject(0).optString("text", "").trim();

                // In strict JSON mode, the entire Gemini response is a single JSON object.
                // Pass it straight through to the callback without additional parsing.
                String analysis = text;
                String recommendation = "";

                cb.onSuccess(analysis, recommendation);

            } catch (IOException e) {
                Log.e(TAG, "Network error", e);
                cb.onError("AI analysis is currently offline. Please check your internet connection.");
            } catch (Exception e) {
                Log.e(TAG, "Gemini error", e);
                cb.onError("AI analysis is temporarily unavailable.");
            }
        }).start();
    }
}
