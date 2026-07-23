package com.nova.ai.data;

public class ModelInfo {
    public final String id;
    public final String name;
    public final String description;
    public final boolean vision;

    public ModelInfo(String id, String name, String description) {
        this(id, name, description, false);
    }

    public ModelInfo(String id, String name, String description, boolean vision) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.vision = vision;
    }

    public String shortName() {
        int slash = id.indexOf('/');
        return slash >= 0 ? id.substring(slash + 1) : id;
    }
}
