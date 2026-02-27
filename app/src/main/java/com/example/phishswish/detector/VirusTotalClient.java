package com.example.phishswish.detector;

import android.util.Base64;
import java.nio.charset.StandardCharsets;

import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class VirusTotalClient {

    private final OkHttpClient client = new OkHttpClient();
    private final String apiKey;

    public VirusTotalClient(String apiKey) {
        this.apiKey = apiKey;
    }

    private String toUrlId(String url) {
        // Use android.util.Base64 for compatibility with minSdk 24 (java.util.Base64 requires API 26)
        byte[] bytes = url.getBytes(StandardCharsets.UTF_8);
        return Base64.encodeToString(bytes, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
    }

    public String getUrlReport(String url) throws Exception {

        String urlId = toUrlId(url);

        Request request = new Request.Builder()
                .url("https://www.virustotal.com/api/v3/urls/" + urlId)
                .addHeader("x-apikey", apiKey)
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                return response.body().string();
            }

            if (response.code() == 404) {
                submitUrl(url);
                Thread.sleep(3000);
                return getUrlReport(url);
            }

            throw new Exception("VT Error: " + response.code());
        }
    }

    private void submitUrl(String url) throws Exception {

        FormBody body = new FormBody.Builder()
                .add("url", url)
                .build();

        Request request = new Request.Builder()
                .url("https://www.virustotal.com/api/v3/urls")
                .addHeader("x-apikey", apiKey)
                .post(body)
                .build();

        client.newCall(request).execute();
    }
}
