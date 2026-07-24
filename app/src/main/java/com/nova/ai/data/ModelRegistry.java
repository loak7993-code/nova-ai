package com.nova.ai.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ModelRegistry {
    public static List<ModelInfo> ALL = new ArrayList<>(Arrays.asList(
            new ModelInfo("zhipuai/glm-5.2", "GLM 5.2", "Zhipu AI · 1M context · reasoning + tool calling", false),
            new ModelInfo("minimax/MiniMax-M3", "MiniMax M3", "MiniMax · 512K context · coding, agentic, vision", true),
            new ModelInfo("moonshotai/kimi-k2.7-code", "Kimi K2.7 Code", "Moonshot · 262K context · vision + agentic coding", true)
    ));

    public static ModelInfo find(String id) {
        if (id == null) return ALL.get(0);
        for (ModelInfo m : ALL) if (m.id.equals(id)) return m;
        return new ModelInfo(id, id, "");
    }

    public static String[] ids() {
        String[] a = new String[ALL.size()];
        for (int i = 0; i < ALL.size(); i++) a[i] = ALL.get(i).id;
        return a;
    }
}
