package com.nova.ai.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ModelRegistry {
    public static List<ModelInfo> ALL = new ArrayList<>(Arrays.asList(
            new ModelInfo("big-pickle", "Big Pickle", "OpenCode Zen · Free · vision + reasoning + tools", true),
            new ModelInfo("deepseek-v4-flash-free", "DeepSeek V4 Flash", "OpenCode Zen · Free · reasoning + tools", false),
            new ModelInfo("mimo-v2.5-free", "MiMo V2.5", "OpenCode Zen · Free · reasoning + tools", false),
            new ModelInfo("nemotron-3-ultra-free", "Nemotron 3 Ultra", "OpenCode Zen · Free · reasoning + tools", false),
            new ModelInfo("north-mini-code-free", "North Mini Code", "OpenCode Zen · Free · reasoning + tools", false),
            new ModelInfo("glm-5.2", "GLM 5.2", "OpenCode Zen · reasoning + tools", false),
            new ModelInfo("minimax-m3", "MiniMax M3", "OpenCode Zen · coding + vision + tools", true),
            new ModelInfo("kimi-k2.7-code", "Kimi K2.7 Code", "OpenCode Zen · vision + agentic coding", true)
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
