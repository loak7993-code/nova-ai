package com.nova.ai.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ProviderManager {
    private static final String PREF = "nova_providers";
    private static final String KEY_PROFILES = "profiles";
    private static final String KEY_ACTIVE = "active_id";
    private static final String KEY_MODELS = "models_csv";

    private static ProviderManager instance;
    private final SharedPreferences sp;
    private List<ProviderProfile> profiles = new ArrayList<>();
    private String activeId = "";

    private ProviderManager(Context ctx) {
        sp = ctx.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
        load();
    }

    public static synchronized ProviderManager get(Context ctx) {
        if (instance == null) instance = new ProviderManager(ctx);
        return instance;
    }

    public static ProviderManager get() {
        if (instance == null) throw new IllegalStateException("ProviderManager not initialized");
        return instance;
    }

    private void load() {
        activeId = sp.getString(KEY_ACTIVE, "");
        String csv = sp.getString(KEY_MODELS, "");
        int count = sp.getInt("count", 0);
        profiles = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String id = sp.getString("p" + i + "_id", "");
            if (id.isEmpty()) continue;
            ProviderProfile p = new ProviderProfile();
            p.id = id;
            p.name = sp.getString("p" + i + "_name", "");
            p.apiBase = sp.getString("p" + i + "_base", "");
            p.apiKey = sp.getString("p" + i + "_key", "");
            p.activeModel = sp.getString("p" + i + "_model", "");
            p.setModelsCsv(sp.getString("p" + i + "_models", ""));
            profiles.add(p);
        }
        if (activeId.isEmpty() && !profiles.isEmpty()) {
            activeId = profiles.get(0).id;
        }
    }

    private void persist() {
        SharedPreferences.Editor ed = sp.edit().clear();
        ed.putInt("count", profiles.size());
        for (int i = 0; i < profiles.size(); i++) {
            ProviderProfile p = profiles.get(i);
            ed.putString("p" + i + "_id", p.id);
            ed.putString("p" + i + "_name", p.name);
            ed.putString("p" + i + "_base", p.apiBase);
            ed.putString("p" + i + "_key", p.apiKey);
            ed.putString("p" + i + "_model", p.activeModel);
            ed.putString("p" + i + "_models", p.modelsCsv());
        }
        ed.putString(KEY_ACTIVE, activeId);
        ed.apply();
    }

    public List<ProviderProfile> all() {
        return profiles;
    }

    public ProviderProfile active() {
        for (ProviderProfile p : profiles) {
            if (p.id.equals(activeId)) return p;
        }
        if (!profiles.isEmpty()) return profiles.get(0);
        return null;
    }

    public String activeId() {
        return activeId;
    }

    public void setActive(String id) {
        activeId = id;
        persist();
    }

    public ProviderProfile find(String id) {
        for (ProviderProfile p : profiles) {
            if (p.id.equals(id)) return p;
        }
        return null;
    }

    public ProviderProfile findOrCreate(String name, String apiBase, String apiKey) {
        for (ProviderProfile p : profiles) {
            if (p.apiBase.equals(apiBase)) {
                p.name = name;
                p.apiKey = apiKey;
                persist();
                return p;
            }
        }
        ProviderProfile p = new ProviderProfile(UUID.randomUUID().toString(), name, apiBase, apiKey);
        profiles.add(p);
        if (activeId.isEmpty()) activeId = p.id;
        persist();
        return p;
    }

    public void updateModels(String id, List<String> models, String activeModel) {
        ProviderProfile p = find(id);
        if (p == null) return;
        p.models = new ArrayList<>(models);
        if (activeModel != null && !activeModel.isEmpty()) {
            p.activeModel = activeModel;
        } else if (!models.isEmpty() && (p.activeModel == null || p.activeModel.isEmpty())) {
            p.activeModel = models.get(0);
        }
        persist();
    }

    public void remove(String id) {
        profiles.removeIf(p -> p.id.equals(id));
        if (activeId.equals(id)) {
            activeId = profiles.isEmpty() ? "" : profiles.get(0).id;
        }
        persist();
    }

    public void ensureDefault() {
        if (profiles.isEmpty()) {
            ProviderProfile zen = new ProviderProfile(
                    UUID.randomUUID().toString(),
                    "OpenCode Zen",
                    "https://opencode.ai/zen/v1",
                    "");
            zen.activeModel = "big-pickle";
            zen.setModelsCsv("big-pickle,deepseek-v4-flash-free,mimo-v2.5-free,nemotron-3-ultra-free,north-mini-code-free,glm-5.2,minimax-m3,kimi-k2.7-code");
            profiles.add(zen);
            activeId = zen.id;
            persist();
        }
    }
}
