package com.nova.ai.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ModelRegistry {
    public static List<ModelInfo> ALL = new ArrayList<>(Arrays.asList(
            new ModelInfo("zai-org/GLM-5.2", "GLM 5.2", "Z.AI · 40B-744B MoE · strong general reasoning", false),
            new ModelInfo("MiniMaxAI/MiniMax-M3", "MiniMax M3", "MiniMax · 23B-428B MoE · coding, agentic, vision", true),
            new ModelInfo("moonshotai/Kimi-K2.7-Code", "Kimi K2.7 Code", "Moonshot · 32B-1T MoE · vision + agentic coding", true)
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
