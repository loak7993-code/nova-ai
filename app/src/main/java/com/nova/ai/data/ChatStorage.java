package com.nova.ai.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ChatStorage {
    private static final String PREF = "nova_chats";
    private static final String KEY = "conversations";
    private static ChatStorage instance;

    private final SharedPreferences sp;
    private final Gson gson = new Gson();
    private final List<Conversation> conversations = new ArrayList<>();

    private ChatStorage(Context ctx) {
        sp = ctx.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
        load();
    }

    public static synchronized ChatStorage get(Context ctx) {
        if (instance == null) instance = new ChatStorage(ctx);
        return instance;
    }

    public static ChatStorage get() {
        if (instance == null) throw new IllegalStateException("ChatStorage not initialized");
        return instance;
    }

    private void load() {
        String json = sp.getString(KEY, null);
        if (json != null) {
            try {
                Type type = new TypeToken<List<Conversation>>() {}.getType();
                List<Conversation> list = gson.fromJson(json, type);
                if (list != null) conversations.addAll(list);
            } catch (Exception ignored) {}
        }
    }

    public void persist() {
        sp.edit().putString(KEY, gson.toJson(conversations)).apply();
    }

    public List<Conversation> all() {
        List<Conversation> sorted = new ArrayList<>(conversations);
        Collections.sort(sorted, (a, b) -> Long.compare(b.updatedAt, a.updatedAt));
        return sorted;
    }

    public Conversation create() {
        Conversation c = new Conversation();
        conversations.add(c);
        persist();
        return c;
    }

    public Conversation find(String id) {
        for (Conversation c : conversations) if (c.id.equals(id)) return c;
        return null;
    }

    public void update(Conversation c) {
        c.touch();
        persist();
    }

    public void delete(String id) {
        for (int i = 0; i < conversations.size(); i++) {
            if (conversations.get(i).id.equals(id)) {
                conversations.remove(i);
                break;
            }
        }
        persist();
    }

    public void clearAll() {
        conversations.clear();
        persist();
    }
}
