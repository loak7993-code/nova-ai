package com.nova.ai.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ProviderProfile {
    public String id;
    public String name;
    public String apiBase;
    public String apiKey;
    public String activeModel;
    public List<String> models;

    public ProviderProfile() {
        this.models = new ArrayList<>();
    }

    public ProviderProfile(String id, String name, String apiBase, String apiKey) {
        this.id = id;
        this.name = name;
        this.apiBase = apiBase;
        this.apiKey = apiKey;
        this.activeModel = "";
        this.models = new ArrayList<>();
    }

    public String modelsCsv() {
        if (models == null || models.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < models.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(models.get(i));
        }
        return sb.toString();
    }

    public void setModelsCsv(String csv) {
        models = new ArrayList<>();
        if (csv == null || csv.trim().isEmpty()) return;
        models = Arrays.asList(csv.split(","));
    }
}
