package com.nova.ai.net;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class ProviderFetcher {

    public static class ProviderInfo {
        public final String name;
        public final String apiUrl;

        public ProviderInfo(String name, String apiUrl) {
            this.name = name;
            this.apiUrl = apiUrl;
        }
    }

    public interface Callback<T> {
        void onSuccess(T result);
        void onError(String message);
    }

    private static OkHttpClient client;

    private static OkHttpClient getClient() {
        if (client == null) {
            client = new OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(20, TimeUnit.SECONDS)
                    .build();
        }
        return client;
    }

    public static void fetchProviders(Callback<List<ProviderInfo>> callback) {
        new Thread(() -> {
            try {
                Request request = new Request.Builder()
                        .url("https://models.dev/providers/")
                        .header("User-Agent", "NovaAI/1.0")
                        .build();
                try (Response response = getClient().newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        callback.onError("HTTP " + response.code());
                        return;
                    }
                    ResponseBody body = response.body();
                    if (body == null) {
                        callback.onError("Empty response");
                        return;
                    }
                    String html = body.string();
                    List<ProviderInfo> providers = parseProvidersHtml(html);
                    callback.onSuccess(providers);
                }
            } catch (IOException e) {
                callback.onError(e.getMessage());
            }
        }).start();
    }

    private static List<ProviderInfo> parseProvidersHtml(String html) {
        List<ProviderInfo> providers = new ArrayList<>();
        int idx = 0;
        while (true) {
            int start = html.indexOf("data-search=\"", idx);
            if (start < 0) break;
            start += 13;
            int end = html.indexOf("\"", start);
            if (end < 0) break;
            String raw = html.substring(start, end);
            idx = end + 1;

            String[] parts = raw.split("\\s+");
            if (parts.length < 4) continue;

            String displayName = parts[0];
            String slug = parts[1];
            String sdk = parts[2];
            String apiUrl = parts[3];

            if (!"@ai-sdk/openai-compatible".equals(sdk)) continue;
            if (!apiUrl.startsWith("http")) continue;
            if (apiUrl.contains("${")) continue;
            if (apiUrl.equals("-")) continue;

            String niceName = displayName;
            for (int i = 4; i < parts.length; i++) {
                if (parts[i].startsWith("http") || parts[i].equals("-")) break;
                niceName += " " + parts[i];
            }

            providers.add(new ProviderInfo(niceName, apiUrl));
        }
        Collections.sort(providers, (a, b) -> a.name.compareToIgnoreCase(b.name));
        return providers;
    }

    public static void fetchModels(String apiBase, String apiKey, Callback<List<String>> callback) {
        new Thread(() -> {
            try {
                String url = apiBase.replaceAll("/+$", "") + "/models";
                Request.Builder rb = new Request.Builder()
                        .url(url)
                        .header("User-Agent", "NovaAI/1.0");
                if (apiKey != null && !apiKey.trim().isEmpty()) {
                    rb.header("Authorization", "Bearer " + apiKey.trim());
                }
                try (Response response = getClient().newCall(rb.build()).execute()) {
                    if (!response.isSuccessful()) {
                        callback.onError("HTTP " + response.code());
                        return;
                    }
                    ResponseBody body = response.body();
                    if (body == null) {
                        callback.onError("Empty response");
                        return;
                    }
                    String json = body.string();
                    List<String> models = parseModelsJson(json);
                    if (models.isEmpty()) {
                        callback.onError("No models found");
                    } else {
                        Collections.sort(models, String::compareToIgnoreCase);
                        callback.onSuccess(models);
                    }
                }
            } catch (IOException e) {
                callback.onError(e.getMessage());
            }
        }).start();
    }

    private static List<String> parseModelsJson(String json) {
        List<String> models = new ArrayList<>();
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonArray data = root.getAsJsonArray("data");
            if (data != null) {
                for (JsonElement el : data) {
                    JsonObject m = el.getAsJsonObject();
                    if (m.has("id")) {
                        models.add(m.get("id").getAsString());
                    }
                }
            }
        } catch (Exception e) {
            try {
                JsonArray arr = JsonParser.parseString(json).getAsJsonArray();
                for (JsonElement el : arr) {
                    JsonObject m = el.getAsJsonObject();
                    if (m.has("id")) {
                        models.add(m.get("id").getAsString());
                    }
                }
            } catch (Exception ignored) {}
        }
        return models;
    }
}
