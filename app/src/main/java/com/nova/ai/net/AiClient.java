package com.nova.ai.net;

import android.text.TextUtils;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nova.ai.data.Conversation;
import com.nova.ai.data.Message;
import com.nova.ai.data.Settings;
import com.nova.ai.tools.Tools;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class AiClient {
    public interface Callback {
        void onReasoning(String delta);
        void onToken(String delta);
        void onToolStart(Message toolMsg, java.util.concurrent.CountDownLatch latch);
        void onToolCall(String toolName, String result, List<String> sources);
        void onComplete(String fullText, String fullReasoning);
        void onError(int httpCode, String message);
    }

    private static AiClient instance;
    private final Gson gson = new Gson();
    private final OkHttpClient http;
    private final OkHttpClient streamHttp;

    private AiClient() {
        http = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        streamHttp = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(300, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    public static synchronized AiClient get() {
        if (instance == null) instance = new AiClient();
        return instance;
    }

    public Call send(Conversation conv, Message aiMsg, AtomicBoolean cancelled, Callback cb) {
        Settings s = Settings.get();
        JsonObject body = buildRequestBody(conv, s, true);
        String url = s.apiBase.replaceAll("/+$", "") + "/chat/completions";
        return doRequest(url, body, s, cancelled, cb, conv, aiMsg, 0);
    }

    private JsonObject buildRequestBody(Conversation conv, Settings s, boolean withTools) {
        JsonArray msgs = new JsonArray();
        if (s.systemPrompt != null && !s.systemPrompt.trim().isEmpty()) {
            JsonObject sys = new JsonObject();
            sys.addProperty("role", "system");
            sys.addProperty("content", s.systemPrompt);
            msgs.add(sys);
        }
        for (Message m : conv.messages) {
            if (m.isError()) continue;
            if (m.streaming || m.thinking) {
                if (m.toolCallsJson == null || m.toolCallsJson.isEmpty()) continue;
            }
            JsonObject mo = new JsonObject();
            if (m.isTool()) {
                mo.addProperty("role", "tool");
                mo.addProperty("content", m.toolResult != null ? m.toolResult : "");
                if (m.toolCallId != null && !m.toolCallId.isEmpty()) {
                    mo.addProperty("tool_call_id", m.toolCallId);
                }
            } else if (m.isAssistant() && m.toolCallsJson != null && !m.toolCallsJson.isEmpty()) {
                mo.addProperty("role", "assistant");
                if (m.content != null && !m.content.isEmpty()) {
                    mo.addProperty("content", m.content);
                } else {
                    mo.add("content", com.google.gson.JsonNull.INSTANCE);
                }
                mo.add("tool_calls", JsonParser.parseString(m.toolCallsJson).getAsJsonArray());
            } else if (m.isUser() && m.hasImage()) {
                mo.addProperty("role", "user");
                JsonArray content = new JsonArray();
                JsonObject textPart = new JsonObject();
                textPart.addProperty("type", "text");
                textPart.addProperty("text", m.content != null ? m.content : "");
                content.add(textPart);
                String dataUrl = com.nova.ai.util.ImageLoader.toDataUrl(m.imagePath, 1024);
                if (dataUrl != null) {
                    JsonObject imgPart = new JsonObject();
                    imgPart.addProperty("type", "image_url");
                    JsonObject imgUrl = new JsonObject();
                    imgUrl.addProperty("url", dataUrl);
                    imgPart.add("image_url", imgUrl);
                    content.add(imgPart);
                }
                mo.add("content", content);
            } else {
                mo.addProperty("role", m.apiRole());
                mo.addProperty("content", m.content != null ? m.content : "");
            }
            msgs.add(mo);
        }

        JsonObject body = new JsonObject();
        body.add("messages", msgs);
        body.addProperty("model", s.model);
        body.addProperty("temperature", s.temperature);
        body.addProperty("stream", s.stream);
        if (withTools) body.add("tools", Tools.toolDefinitions());

        return body;
    }

    private Call doRequest(String url, JsonObject body, Settings s,
                           AtomicBoolean cancelled, Callback cb,
                           Conversation conv, Message aiMsg, int depth) {
        if (depth > 15) {
            cb.onError(0, "Too many tool calls");
            return null;
        }

        boolean useStream = s.stream && body.has("stream") && body.get("stream").getAsBoolean();

        RequestBody reqBody = RequestBody.create(
                body.toString().getBytes(StandardCharsets.UTF_8),
                MediaType.parse("application/json"));

        Request.Builder rb = new Request.Builder()
                .url(url)
                .post(reqBody)
                .header("Accept", useStream ? "text/event-stream" : "application/json")
                .header("User-Agent", "NovaAI/2.4");

        if (s.apiKey != null && !s.apiKey.trim().isEmpty()) {
            rb.header("Authorization", "Bearer " + s.apiKey.trim());
        }

        Request request = rb.build();
        OkHttpClient client = useStream ? streamHttp : http;
        Call call = client.newCall(request);
        call.enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(Call c, IOException e) {
                if (cancelled.get()) {
                    cb.onComplete("", "");
                    return;
                }
                cb.onError(0, e.getMessage() == null ? "Connection failed" : e.getMessage());
            }

            @Override
            public void onResponse(Call c, Response response) throws IOException {
                try (ResponseBody respBody = response.body()) {
                    if (!response.isSuccessful()) {
                        String errBody = respBody == null ? "" : respBody.string();
                        cb.onError(response.code(), extractError(errBody, response.code()));
                        return;
                    }
                    if (respBody == null) {
                        cb.onError(response.code(), "Empty response");
                        return;
                    }

                    if (useStream) {
                        handleStream(respBody, cancelled, cb, conv, aiMsg, url, s, depth);
                    } else {
                        handleNonStream(respBody.string(), cancelled, cb, conv, aiMsg, url, s, depth);
                    }
                }
            }
        });
        return call;
    }

    private void handleStream(ResponseBody respBody, AtomicBoolean cancelled, Callback cb,
                             Conversation conv, Message aiMsg, String url, Settings s, int depth) {
        StringBuilder contentBuilder = new StringBuilder();
        StringBuilder reasoningBuilder = new StringBuilder();
        String accumulatedToolCalls = "";
        String finishReason = "";
        String toolCallId = "";

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(respBody.byteStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (cancelled.get()) {
                    cb.onComplete(contentBuilder.toString(), reasoningBuilder.toString());
                    return;
                }
                if (line.isEmpty() || !line.startsWith("data:")) continue;
                String data = line.substring(5).trim();
                if ("[DONE]".equals(data)) break;

                try {
                    JsonObject chunk = JsonParser.parseString(data).getAsJsonObject();
                    if (!chunk.has("choices") || chunk.getAsJsonArray("choices").size() == 0) continue;
                    JsonObject choice = chunk.getAsJsonArray("choices").get(0).getAsJsonObject();

                    if (choice.has("delta")) {
                        JsonObject delta = choice.getAsJsonObject("delta");

                        if (delta.has("reasoning") && !delta.get("reasoning").isJsonNull()) {
                            String r = delta.get("reasoning").getAsString();
                            if (!r.isEmpty()) {
                                reasoningBuilder.append(r);
                                cb.onReasoning(r);
                            }
                        }

                        if (delta.has("reasoning_content") && !delta.get("reasoning_content").isJsonNull()) {
                            String r = delta.get("reasoning_content").getAsString();
                            if (!r.isEmpty()) {
                                reasoningBuilder.append(r);
                                cb.onReasoning(r);
                            }
                        }

                        if (delta.has("content") && !delta.get("content").isJsonNull()) {
                            String t = delta.get("content").getAsString();
                            if (!t.isEmpty()) {
                                contentBuilder.append(t);
                                cb.onToken(t);
                            }
                        }

                        if (delta.has("tool_calls") && !delta.get("tool_calls").isJsonNull()) {
                            JsonArray tcs = delta.getAsJsonArray("tool_calls");
                            if (tcs.size() > 0) {
                                JsonObject tc = tcs.get(0).getAsJsonObject();
                                if (tc.has("id") && !tc.get("id").isJsonNull() && toolCallId.isEmpty()) {
                                    toolCallId = tc.get("id").getAsString();
                                }
                                if (tc.has("function")) {
                                    JsonObject fn = tc.getAsJsonObject("function");
                                    if (fn.has("arguments") && !fn.get("arguments").isJsonNull()) {
                                        accumulatedToolCalls += fn.get("arguments").getAsString();
                                    }
                                }
                            }
                        }
                    }

                    if (choice.has("finish_reason") && !choice.get("finish_reason").isJsonNull()) {
                        finishReason = choice.get("finish_reason").getAsString();
                    }
                } catch (Exception ignored) {}
            }
        } catch (IOException e) {
            if (!cancelled.get()) {
                cb.onError(0, e.getMessage());
            }
            return;
        }

        if (cancelled.get()) {
            cb.onComplete(contentBuilder.toString(), reasoningBuilder.toString());
            return;
        }

        if ("tool_calls".equals(finishReason) && !accumulatedToolCalls.isEmpty()) {
            handleToolCall(accumulatedToolCalls, toolCallId, aiMsg, conv, s, url, cancelled, cb, depth);
            return;
        }

        cb.onComplete(contentBuilder.toString(), reasoningBuilder.toString());
    }

    private void handleNonStream(String full, AtomicBoolean cancelled, Callback cb,
                                  Conversation conv, Message aiMsg, String url, Settings s, int depth) {
        try {
            JsonObject obj = JsonParser.parseString(full).getAsJsonObject();
            JsonObject choice = (obj.has("choices") && obj.getAsJsonArray("choices").size() > 0)
                    ? obj.getAsJsonArray("choices").get(0).getAsJsonObject() : null;
            if (choice == null) {
                cb.onError(0, "No choices in response");
                return;
            }

            String reasoning = "";
            String content = "";
            String finishReason = "";

            if (choice.has("message")) {
                JsonObject msg = choice.getAsJsonObject("message");
                reasoning = msg.has("reasoning") && !msg.get("reasoning").isJsonNull()
                        ? msg.get("reasoning").getAsString() : "";
                content = msg.has("content") && !msg.get("content").isJsonNull()
                        ? msg.get("content").getAsString() : "";
            }
            finishReason = choice.has("finish_reason") && !choice.get("finish_reason").isJsonNull()
                    ? choice.get("finish_reason").getAsString() : "";

            if (!reasoning.isEmpty()) cb.onReasoning(reasoning);

            if ("tool_calls".equals(finishReason) && choice.has("message")) {
                JsonObject msg = choice.getAsJsonObject("message");
                if (msg.has("tool_calls")) {
                    JsonArray toolCalls = msg.getAsJsonArray("tool_calls");
                    String toolCallId = "";
                    String toolName = "";
                    String args = "{}";

                    if (toolCalls.size() > 0) {
                        JsonObject tc = toolCalls.get(0).getAsJsonObject();
                        aiMsg.toolCallsJson = toolCalls.toString();
                        toolCallId = tc.has("id") && !tc.get("id").isJsonNull()
                                ? tc.get("id").getAsString() : "";
                        JsonObject fn = tc.has("function") ? tc.getAsJsonObject("function") : null;
                        toolName = fn != null && fn.has("name") ? fn.get("name").getAsString() : "";
                        args = fn != null && fn.has("arguments") && !fn.get("arguments").isJsonNull()
                                ? fn.get("arguments").getAsString() : "{}";
                    }
                    handleToolCall(args, toolCallId, aiMsg, conv, s, url, cancelled, cb, depth);
                    return;
                }
            }

            if (cancelled.get()) {
                cb.onComplete(content, reasoning);
                return;
            }
            cb.onToken(content);
            cb.onComplete(content, reasoning);
        } catch (Exception e) {
            cb.onError(0, "Parse error: " + e.getMessage());
        }
    }

    private void handleToolCall(String argsJson, String toolCallId, Message aiMsg,
                                 Conversation conv, Settings s, String url,
                                 AtomicBoolean cancelled, Callback cb, int depth) {
        try {
            JsonObject argsObj = JsonParser.parseString(argsJson).getAsJsonObject();
            String toolName = argsObj.has("name") ? argsObj.get("name").getAsString() : "";
            String arguments = argsObj.has("arguments")
                    ? (argsObj.get("arguments").isJsonPrimitive()
                        ? argsObj.get("arguments").getAsString()
                        : argsObj.get("arguments").toString())
                    : "{}";

            JsonArray tcArray = new JsonArray();
            JsonObject tc = new JsonObject();
            tc.addProperty("id", toolCallId);
            tc.addProperty("type", "function");
            JsonObject fn = new JsonObject();
            fn.addProperty("name", toolName);
            fn.addProperty("arguments", arguments);
            tc.add("function", fn);
            tcArray.add(tc);
            aiMsg.toolCallsJson = tcArray.toString();

            Message toolMsg = new Message(Message.Role.TOOL, "");
            toolMsg.toolName = toolName;
            toolMsg.toolCallId = toolCallId;
            toolMsg.searching = "web_search".equals(toolName);

            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
            cb.onToolStart(toolMsg, latch);
            try { latch.await(5, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}

            Tools.ToolResult result = Tools.execute(toolName, arguments);
            toolMsg.toolResult = result.content;
            toolMsg.sources = result.sources;
            toolMsg.searching = false;
            cb.onToolCall(toolName, result.content, result.sources);

            if (cancelled.get()) {
                cb.onComplete("", "");
                return;
            }

            JsonObject nextBody = buildRequestBody(conv, s, true);
            doRequest(url, nextBody, s, cancelled, cb, conv, aiMsg, depth + 1);
        } catch (Exception e) {
            cb.onError(0, "Tool call error: " + e.getMessage());
        }
    }

    private String extractError(String body, int code) {
        try {
            JsonObject obj = JsonParser.parseString(body).getAsJsonObject();
            if (obj.has("error")) {
                JsonObject err = obj.getAsJsonObject("error");
                if (err.has("message")) return err.get("message").getAsString();
            }
            if (obj.has("message")) return obj.get("message").getAsString();
        } catch (Exception ignored) {}
        return TextUtils.isEmpty(body) ? ("HTTP " + code) : body;
    }
}
