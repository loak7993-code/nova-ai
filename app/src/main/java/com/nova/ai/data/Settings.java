package com.nova.ai.data;

import android.content.Context;
import android.content.SharedPreferences;

public class Settings {
    private static final String PREF = "nova_settings";
    private static Settings instance;

    private final SharedPreferences sp;

    public String apiBase;
    public String apiKey;
    public String model;
    public String systemPrompt;
    public float temperature;
    public boolean stream;
    public String searchUrl;

    private Settings(Context ctx) {
        sp = ctx.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
        load();
    }

    public static synchronized Settings get(Context ctx) {
        if (instance == null) instance = new Settings(ctx);
        return instance;
    }

    public static Settings get() {
        if (instance == null) throw new IllegalStateException("Settings not initialized");
        return instance;
    }

    private static final String DEFAULT_SYSTEM_PROMPT =
            "You are Nova, an advanced AI assistant built for clarity and depth. " +
            "You help with coding, writing, analysis, research, and creative tasks.\n\n" +
            "Guidelines:\n" +
            "- Be direct and confident. No filler phrases or unnecessary hedging.\n" +
            "- Use Markdown for structure: headers for sections, lists for steps, fenced code blocks with language tags for code.\n" +
            "- For code: always specify the language tag. Keep snippets focused and runnable.\n" +
            "- For complex problems: reason step by step before giving the final answer.\n" +
            "- Match the user's energy — casual chat stays light, technical questions get precise detail.\n" +
            "- If you're uncertain, say so plainly. Accuracy beats confidence.\n" +
            "- Keep answers complete but tight. Don't pad. Don't repeat the question back.";

    private void load() {
        apiBase = sp.getString("api_base", "https://api.inference.wandb.ai/v1");
        apiKey = sp.getString("api_key", "");
        model = sp.getString("model", "zai-org/GLM-5.2");
        systemPrompt = sp.getString("system_prompt", DEFAULT_SYSTEM_PROMPT);
        temperature = sp.getFloat("temperature", 0.7f);
        stream = sp.getBoolean("stream", true);
        searchUrl = sp.getString("search_url", "");
        migrate();
    }

    private void migrate() {
        boolean changed = false;
        if (apiBase == null || (apiBase.contains("api.wandb.ai") && !apiBase.contains("inference"))) {
            apiBase = "https://api.inference.wandb.ai/v1";
            changed = true;
        }
        if (model == null || model.isEmpty()) {
            model = "zai-org/GLM-5.2";
            changed = true;
        }
        String oldPrompt = "You are Nova, a helpful, friendly AI assistant. Provide clear, concise, well-formatted answers using Markdown when useful.";
        if (oldPrompt.equals(systemPrompt) || systemPrompt == null || systemPrompt.trim().isEmpty()) {
            systemPrompt = DEFAULT_SYSTEM_PROMPT;
            changed = true;
        }
        if (changed) save();
    }

    public void save() {
        sp.edit()
                .putString("api_base", apiBase)
                .putString("api_key", apiKey)
                .putString("model", model)
                .putString("system_prompt", systemPrompt)
                .putFloat("temperature", temperature)
                .putBoolean("stream", stream)
                .putString("search_url", searchUrl)
                .apply();
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.trim().isEmpty();
    }
}
