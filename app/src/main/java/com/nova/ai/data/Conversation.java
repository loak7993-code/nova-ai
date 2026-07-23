package com.nova.ai.data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Conversation {
    public String id;
    public String title;
    public long createdAt;
    public long updatedAt;
    public List<Message> messages;

    public Conversation() {
        this.id = UUID.randomUUID().toString();
        this.title = "New chat";
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = this.createdAt;
        this.messages = new ArrayList<>();
    }

    public void touch() {
        this.updatedAt = System.currentTimeMillis();
    }

    public void add(Message m) {
        messages.add(m);
        touch();
    }

    public String preview() {
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message m = messages.get(i);
            if (m.content != null && !m.content.isEmpty() && !m.isError()) {
                String s = m.content.trim().replace("\n", " ");
                if (s.length() > 48) s = s.substring(0, 48) + "…";
                return s;
            }
        }
        return title;
    }
}
