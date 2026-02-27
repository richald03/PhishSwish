package com.example.phishswish.detector;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class GeminiModelsDebug {

    private static final String TAG = "GeminiModels";
    private final OkHttpClient http = new OkHttpClient();

    public List<String> listModelsThatSupportGenerateContent(String apiKey) throws Exception {
        // Official endpoint for listing models
        String url = "https://generativelanguage.googleapis.com/v1beta/models?key=" + apiKey;

        Request req = new Request.Builder().url(url).get().build();
        Response res = http.newCall(req).execute();

        String body = res.body() != null ? res.body().string() : "";
        if (!res.isSuccessful()) {
            throw new Exception("ListModels failed HTTP " + res.code() + ": " + body);
        }

        JSONObject root = new JSONObject(body);
        JSONArray models = root.optJSONArray("models");

        List<String> okModels = new ArrayList<>();

        if (models != null) {
            for (int i = 0; i < models.length(); i++) {
                JSONObject m = models.getJSONObject(i);
                String name = m.optString("name"); // e.g. "models/gemini-2.0-flash"
                JSONArray methods = m.optJSONArray("supportedGenerationMethods");

                boolean supports = false;
                if (methods != null) {
                    for (int j = 0; j < methods.length(); j++) {
                        if ("generateContent".equals(methods.getString(j))) {
                            supports = true;
                            break;
                        }
                    }
                }

                Log.d(TAG, "MODEL: " + name + " methods=" + (methods != null ? methods.toString() : "[]"));

                if (supports && name != null && name.startsWith("models/")) {
                    okModels.add(name);
                }
            }
        }

        Log.d(TAG, "OK MODELS (generateContent): " + okModels);
        return okModels;
    }
}
