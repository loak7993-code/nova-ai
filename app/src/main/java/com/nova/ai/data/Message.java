package com.nova.ai.data;

import java.util.UUID;

public class Message {
    public enum Role { USER, ASSISTANT, ERROR, TOOL }

    public String id;
    public Role role;
    public String content;
    public String reasoning;
    public String toolName;
    public String toolResult;
    public String imagePath;
    public java.util.List<String> sources;
    public transient String toolCallsJson;
    public transient String toolCallId;
    public transient boolean streaming;
    public transient boolean thinking;
    public transient boolean searching;

    public Message() {}

    public Message(Role role, String content) {
        this.id = UUID.randomUUID().toString();
        this.role = role;
        this.content = content;
    }

    public boolean hasImage() { return imagePath != null && !imagePath.isEmpty(); }
    public boolean hasSources() { return sources != null && !sources.isEmpty(); }

    public boolean isUser() { return role == Role.USER; }
    public boolean isAssistant() { return role == Role.ASSISTANT; }
    public boolean isError() { return role == Role.ERROR; }
    public boolean isTool() { return role == Role.TOOL; }

    public String apiRole() {
        switch (role) {
            case USER: return "user";
            case ASSISTANT: return "assistant";
            case TOOL: return "tool";
            default: return "system";
        }
    }
}
