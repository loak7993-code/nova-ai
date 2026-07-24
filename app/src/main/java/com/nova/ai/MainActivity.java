package com.nova.ai;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.widget.TextView;
import com.google.android.material.button.MaterialButton;
import com.nova.ai.data.ChatStorage;
import com.nova.ai.data.Conversation;
import com.nova.ai.data.Message;
import com.nova.ai.data.ProviderManager;
import com.nova.ai.data.ProviderProfile;
import com.nova.ai.data.Settings;
import com.nova.ai.net.AiClient;
import com.nova.ai.ui.ConversationAdapter;
import com.nova.ai.ui.MessageAdapter;
import com.nova.ai.ui.ModelPickerAdapter;

import com.nova.ai.BuildConfig;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends AppCompatActivity {

    private DrawerLayout drawer;
    private View toolbar;
    private TextView toolbarSubtitle;
    private RecyclerView messagesList, conversationList;
    private EditText input;
    private ImageButton sendButton;
    private MaterialButton newChatButton, settingsButton;
    private View emptyState;
    private TextView drawerVersion;

    private ChatStorage storage;
    private Conversation current;
    private MessageAdapter messageAdapter;
    private ConversationAdapter conversationAdapter;

    private AiClient ai;
    private AtomicBoolean cancelled = new AtomicBoolean(false);
    private boolean responding = false;
    private okhttp3.Call currentCall;
    private com.google.android.material.bottomsheet.BottomSheetDialog activeSheet;
    private String pendingImagePath;
    private View attachmentPreview;
    private ImageView attachmentThumb;

    private android.os.Handler uiThrottle = new android.os.Handler();
    private boolean uiUpdatePending = false;
    private int uiStreamingIndex = -1;
    private Message uiStreamingMsg = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        storage = ChatStorage.get();
        ai = AiClient.get();

        drawer = findViewById(R.id.drawerLayout);
        toolbar = findViewById(R.id.toolbar);
        toolbarSubtitle = findViewById(R.id.toolbarSubtitle);
        messagesList = findViewById(R.id.messagesList);
        conversationList = findViewById(R.id.conversationList);
        input = findViewById(R.id.inputField);
        sendButton = findViewById(R.id.sendButton);
        ImageButton attachButton = findViewById(R.id.attachButton);
        emptyState = findViewById(R.id.emptyState);
        newChatButton = findViewById(R.id.newChatButton);
        settingsButton = findViewById(R.id.settingsButton);
        drawerVersion = findViewById(R.id.drawerVersion);
        if (drawerVersion != null) drawerVersion.setText("v" + BuildConfig.VERSION_NAME);

        findViewById(R.id.menuButton).setOnClickListener(v -> drawer.openDrawer(GravityCompat.START));
        findViewById(R.id.newChatTopButton).setOnClickListener(v -> newChat());

        attachmentPreview = findViewById(R.id.attachmentPreview);
        attachmentThumb = findViewById(R.id.attachmentThumb);
        findViewById(R.id.attachmentRemove).setOnClickListener(v -> clearAttachment());

        attachButton.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("image/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/jpeg", "image/png", "image/webp", "image/gif"});
            startActivityForResult(intent, 1001);
        });

        messagesList.setLayoutManager(new LinearLayoutManager(this));
        messagesList.setItemAnimator(null);
        messageAdapter = new MessageAdapter(this, current == null ? new java.util.ArrayList<>() : current.messages);
        messageAdapter.setActionListener(makeListener());
        messagesList.setAdapter(messageAdapter);

        conversationAdapter = new ConversationAdapter(new ConversationAdapter.Listener() {
            @Override
            public void onOpen(Conversation c) {
                openConversation(c.id);
                drawer.closeDrawer(GravityCompat.START);
            }

            @Override
            public void onDelete(Conversation c, int position) {
                storage.delete(c.id);
                refreshConversations();
                if (current != null && current.id.equals(c.id)) {
                    newChat();
                }
                Toast.makeText(MainActivity.this, R.string.chat_deleted, Toast.LENGTH_SHORT).show();
            }
        });
        conversationList.setLayoutManager(new LinearLayoutManager(this));
        conversationList.setAdapter(conversationAdapter);

        newChatButton.setOnClickListener(v -> {
            newChat();
            drawer.closeDrawer(GravityCompat.START);
        });
        settingsButton.setOnClickListener(v -> {
            drawer.closeDrawer(GravityCompat.START);
            startActivity(new Intent(this, SettingsActivity.class));
        });
        findViewById(R.id.modelSwitchButton).setOnClickListener(v -> {
            drawer.closeDrawer(GravityCompat.START);
            showModelPicker();
        });

        sendButton.setOnClickListener(v -> onSend());
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                onSend();
                return true;
            }
            return false;
        });
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                updateSendButton();
            }
        });

        findViewById(R.id.suggestion1).setOnClickListener(v -> { input.setText(((TextView) v).getText()); onSend(); });
        findViewById(R.id.suggestion2).setOnClickListener(v -> { input.setText(((TextView) v).getText()); onSend(); });
        findViewById(R.id.suggestion3).setOnClickListener(v -> { input.setText(((TextView) v).getText()); onSend(); });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (drawer.isDrawerOpen(GravityCompat.START)) {
                    drawer.closeDrawer(GravityCompat.START);
                } else {
                    setEnabled(false);
                    onBackPressed();
                }
            }
        });

        List<Conversation> all = storage.all();
        if (all.isEmpty()) {
            current = storage.create();
        } else {
            current = all.get(0);
        }
        openConversation(current.id);
        refreshConversations();
    }

    private void openConversation(String id) {
        current = storage.find(id);
        if (current == null) {
            current = storage.create();
        }
        messageAdapter = new MessageAdapter(this, current.messages);
        messageAdapter.setActionListener(makeListener());
        messagesList.setAdapter(messageAdapter);
        messagesList.setItemAnimator(null);
        updateEmptyState();
        scrollToEnd();
    }

    private void refreshConversations() {
        conversationAdapter.submit(storage.all());
        if (current != null) conversationAdapter.setActiveId(current.id);
    }

    private void newChat() {
        current = storage.create();
        messageAdapter = new MessageAdapter(this, current.messages);
        messageAdapter.setActionListener(makeListener());
        messagesList.setAdapter(messageAdapter);
        messagesList.setItemAnimator(null);
        updateEmptyState();
        refreshConversations();
    }

    private void updateEmptyState() {
        boolean empty = current == null || current.messages.isEmpty();
        emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        messagesList.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void updateSendButton() {
        String text = input.getText().toString().trim();
        if (responding) {
            sendButton.setEnabled(true);
            sendButton.setImageResource(R.drawable.ic_stop);
            return;
        }
        boolean hasText = !text.isEmpty();
        boolean hasImage = pendingImagePath != null;
        sendButton.setEnabled(hasText || hasImage);
        sendButton.setImageResource(R.drawable.ic_send);
    }

    private void updateModelDisplay() {
        String model = Settings.get().model;
        if (model == null || model.isEmpty()) model = "big-pickle";
        com.nova.ai.data.ModelInfo info = com.nova.ai.data.ModelRegistry.find(model);
        String version = BuildConfig.VERSION_NAME;
        if (toolbarSubtitle != null) toolbarSubtitle.setText(info.name);
        com.google.android.material.button.MaterialButton msb = findViewById(R.id.modelSwitchButton);
        if (msb != null) msb.setText(info.name);
    }

    private void showModelPicker() {
        String current = Settings.get().model;
        String[] modelIds = com.nova.ai.data.Settings.modelsForActive();

        android.view.View sheetView = getLayoutInflater().inflate(R.layout.bottom_sheet_model_picker, null);
        android.widget.TextView mpTitle = sheetView.findViewById(R.id.mpTitle);
        android.widget.TextView mpProvider = sheetView.findViewById(R.id.mpProvider);
        androidx.recyclerview.widget.RecyclerView rv = sheetView.findViewById(R.id.modelList);

        ProviderProfile active = ProviderManager.get().active();
        String providerName = active != null ? active.name : "OpenCode Zen";
        mpTitle.setText("Select Model");
        mpProvider.setText(providerName + " · " + modelIds.length + " available");

        java.util.List<com.nova.ai.data.ModelInfo> models = new java.util.ArrayList<>();
        for (String mid : modelIds) {
            models.add(com.nova.ai.data.ModelRegistry.find(mid));
        }

        ModelPickerAdapter adapter = new ModelPickerAdapter((modelId, pos) -> {
            Settings.get().model = modelId;
            Settings.get().save();
            ProviderProfile ap = ProviderManager.get().active();
            if (ap != null) {
                ap.activeModel = modelId;
                ProviderManager.get().updateModels(ap.id,
                        ap.models != null ? ap.models : new java.util.ArrayList<>(), modelId);
            }
            updateModelDisplay();
            com.nova.ai.data.ModelInfo info = com.nova.ai.data.ModelRegistry.find(modelId);
            Toast.makeText(this, info.name, Toast.LENGTH_SHORT).show();
            dismissActiveSheet();
        });
        adapter.setItems(models, current);
        rv.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
        rv.setAdapter(adapter);

        activeSheet = new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        activeSheet.setContentView(sheetView);
        activeSheet.show();
    }

    private void dismissActiveSheet() {
        if (activeSheet != null && activeSheet.isShowing()) {
            activeSheet.dismiss();
        }
    }

    private void onSend() {
        if (responding) {
            stopResponding();
            return;
        }
        String text = input.getText().toString().trim();
        if (text.isEmpty() && pendingImagePath == null) return;
        if (!Settings.get().isConfigured()) {
            Toast.makeText(this, R.string.err_no_api_key, Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, SettingsActivity.class));
            return;
        }

        Message userMsg = new Message(Message.Role.USER, text);
        if (pendingImagePath != null) {
            userMsg.imagePath = pendingImagePath;
        }
        current.add(userMsg);
        messageAdapter.notifyItemInserted(current.messages.size() - 1);
        updateEmptyState();

        clearAttachment();
        input.setText("");

        if (current.title.equals("New chat") && current.messages.size() == 1) {
            current.title = text.length() > 40 ? text.substring(0, 40) + "…" : text;
        }
        storage.update(current);

        respond();
        refreshConversations();
    }

    private void respond() {
        respond(false);
    }

    private com.nova.ai.ui.MessageAdapter.ActionListener makeListener() {
        return new com.nova.ai.ui.MessageAdapter.ActionListener() {
            @Override
            public void onRegenerate() { regenerate(); }

            @Override
            public void onEditUser(String currentText) { editUserMessage(currentText); }
        };
    }

    private void editUserMessage(String oldText) {
        if (responding) return;
        for (int i = current.messages.size() - 1; i >= 0; i--) {
            Message m = current.messages.get(i);
            if (m.isUser()) {
                if (i + 1 < current.messages.size() && current.messages.get(i + 1).isAssistant()) {
                    current.messages.remove(i + 1);
                    messageAdapter.notifyItemRemoved(i + 1);
                }
                current.messages.remove(i);
                messageAdapter.notifyItemRemoved(i);
                break;
            }
        }
        storage.update(current);
        input.setText(oldText);
        input.requestFocus();
        input.setSelection(oldText.length());
        refreshConversations();
        updateEmptyState();
    }

    private void regenerate() {
        if (responding) return;
        for (int i = current.messages.size() - 1; i >= 0; i--) {
            Message m = current.messages.get(i);
            if (m.isAssistant()) {
                current.messages.remove(i);
                messageAdapter.notifyItemRemoved(i);
                break;
            }
        }
        storage.update(current);
        respond();
    }

    private void respond(boolean isRegen) {
        responding = true;
        updateSendButton();

        Message aiMsg = new Message(Message.Role.ASSISTANT, "");
        aiMsg.thinking = true;
        aiMsg.streaming = true;
        current.add(aiMsg);
        int aiIndex = current.messages.size() - 1;
        messageAdapter.notifyItemInserted(aiIndex);
        scrollToEnd();
        updateEmptyState();

        cancelled = new AtomicBoolean(false);
        final int index = aiIndex;
        uiStreamingIndex = index;
        uiStreamingMsg = aiMsg;
        uiUpdatePending = false;

        currentCall = ai.send(current, aiMsg, cancelled, new AiClient.Callback() {
            @Override
            public void onReasoning(String delta) {
                runOnUiThread(() -> {
                    if (aiMsg.reasoning == null) aiMsg.reasoning = "";
                    aiMsg.reasoning += delta;
                    throttledUpdate();
                });
            }

            @Override
            public void onToken(String delta) {
                runOnUiThread(() -> {
                    aiMsg.thinking = false;
                    if (aiMsg.content == null) aiMsg.content = "";
                    aiMsg.content += delta;
                    throttledUpdate();
                });
            }

            @Override
            public void onToolStart(Message toolMsg, java.util.concurrent.CountDownLatch latch) {
                runOnUiThread(() -> {
                    int toolIndex = current.messages.size();
                    current.add(toolMsg);
                    messageAdapter.notifyItemInserted(toolIndex);
                    scrollToEnd();
                    latch.countDown();
                });
            }

            @Override
            public void onToolCall(String toolName, String result, java.util.List<String> sources) {
                runOnUiThread(() -> {
                    for (int i = current.messages.size() - 1; i >= 0; i--) {
                        Message m = current.messages.get(i);
                        if (m.isTool() && toolName != null && toolName.equals(m.toolName)) {
                            messageAdapter.notifyItemChanged(i);
                            break;
                        }
                    }
                    scrollToEnd();
                });
            }

            @Override
            public void onComplete(String fullText, String fullReasoning) {
                runOnUiThread(() -> {
                    if (!responding) return;
                    uiThrottle.removeCallbacksAndMessages(null);
                    uiUpdatePending = false;
                    aiMsg.thinking = false;
                    aiMsg.streaming = false;
                    if (aiMsg.content == null || aiMsg.content.isEmpty()) {
                        aiMsg.content = fullText;
                    }
                    if (aiMsg.reasoning == null || aiMsg.reasoning.isEmpty()) {
                        aiMsg.reasoning = fullReasoning;
                    }
                    if (aiMsg.content.trim().isEmpty()) {
                        if (index < current.messages.size() && current.messages.get(index) == aiMsg) {
                            current.messages.remove(index);
                            messageAdapter.notifyItemRemoved(index);
                        }
                    } else {
                        messageAdapter.notifyItemChanged(index);
                    }
                    storage.update(current);
                    responding = false;
                    currentCall = null;
                    uiStreamingIndex = -1;
                    uiStreamingMsg = null;
                    updateSendButton();
                    refreshConversations();
                    updateEmptyState();
                });
            }

            @Override
            public void onError(int httpCode, String message) {
                runOnUiThread(() -> {
                    if (!responding) return;
                    uiThrottle.removeCallbacksAndMessages(null);
                    uiUpdatePending = false;
                    uiStreamingIndex = -1;
                    uiStreamingMsg = null;
                    if (index < current.messages.size() && current.messages.get(index) == aiMsg) {
                        current.messages.remove(index);
                        messageAdapter.notifyItemRemoved(index);
                    }
                    Message err = new Message(Message.Role.ERROR,
                            httpCode == 0
                                    ? getString(R.string.err_network, message)
                                    : getString(R.string.err_api, httpCode, message));
                    current.add(err);
                    messageAdapter.notifyItemInserted(current.messages.size() - 1);
                    storage.update(current);
                    responding = false;
                    currentCall = null;
                    updateSendButton();
                    updateEmptyState();
                });
            }
        });
    }

    private void stopResponding() {
        if (cancelled != null) cancelled.set(true);
        if (currentCall != null && !currentCall.isCanceled()) currentCall.cancel();
    }

    private boolean isNearBottom() {
        LinearLayoutManager lm = (LinearLayoutManager) messagesList.getLayoutManager();
        if (lm == null) return true;
        int lastVisible = lm.findLastCompletelyVisibleItemPosition();
        int total = messageAdapter.getItemCount() - 1;
        return lastVisible >= total - 2;
    }

    private void throttledUpdate() {
        if (uiUpdatePending) return;
        uiUpdatePending = true;
        uiThrottle.postDelayed(() -> {
            uiUpdatePending = false;
            if (uiStreamingIndex >= 0 && uiStreamingMsg != null) {
                messageAdapter.updateStreaming(uiStreamingIndex);
                if (isNearBottom()) {
                    messagesList.scrollToPosition(messageAdapter.getItemCount() - 1);
                }
            }
        }, 80);
    }

    private void scrollToEnd() {
        messagesList.post(() -> {
            if (messageAdapter.getItemCount() > 0) {
                if (isNearBottom()) {
                    messagesList.scrollToPosition(messageAdapter.getItemCount() - 1);
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshConversations();
        updateSendButton();
        updateModelDisplay();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1001 && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                try {
                    String path = copyImageToInternal(uri);
                    if (path != null) {
                        pendingImagePath = path;
                        showAttachmentPreview(path);
                        checkVisionModel();
                        updateSendButton();
                    }
                } catch (Exception e) {
                    Toast.makeText(this, "Failed to attach image", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private String copyImageToInternal(Uri uri) throws IOException {
        java.io.InputStream in = getContentResolver().openInputStream(uri);
        if (in == null) return null;
        File dir = new File(getFilesDir(), "images");
        if (!dir.exists()) dir.mkdirs();
        String name = "img_" + System.currentTimeMillis() + ".jpg";
        File out = new File(dir, name);
        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(out)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) fos.write(buf, 0, n);
        }
        in.close();
        return out.getAbsolutePath();
    }

    private void showAttachmentPreview(String path) {
        android.graphics.Bitmap bmp = com.nova.ai.util.ImageLoader.load(path, 128);
        if (bmp != null) {
            attachmentThumb.setImageBitmap(bmp);
            attachmentPreview.setVisibility(View.VISIBLE);
        }
    }

    private void clearAttachment() {
        pendingImagePath = null;
        attachmentThumb.setImageBitmap(null);
        attachmentPreview.setVisibility(View.GONE);
        updateSendButton();
    }

    private void checkVisionModel() {
        String modelId = Settings.get().model;
        com.nova.ai.data.ModelInfo info = com.nova.ai.data.ModelRegistry.find(modelId);
        if (!info.vision) {
            com.google.android.material.bottomsheet.BottomSheetDialog sheet =
                    new com.google.android.material.bottomsheet.BottomSheetDialog(this);
            sheet.setContentView(R.layout.bottom_sheet_confirm);

            android.widget.TextView title = sheet.findViewById(R.id.bsConfirmTitle);
            android.widget.TextView message = sheet.findViewById(R.id.bsConfirmMessage);
            android.widget.Button cancel = sheet.findViewById(R.id.bsBtnCancel);
            android.widget.Button confirm = sheet.findViewById(R.id.bsBtnConfirm);

            if (title != null) title.setText(R.string.attention);
            if (message != null) message.setText(getString(R.string.err_no_vision, info.name));
            if (cancel != null) cancel.setText(R.string.cancel);
            if (cancel != null) cancel.setOnClickListener(v -> sheet.dismiss());
            if (confirm != null) confirm.setText(R.string.switch_model);
            if (confirm != null) confirm.setOnClickListener(v -> {
                for (com.nova.ai.data.ModelInfo m : com.nova.ai.data.ModelRegistry.ALL) {
                    if (m.vision) {
                        Settings.get().model = m.id;
                        Settings.get().save();
                        updateModelDisplay();
                        Toast.makeText(this, m.name, Toast.LENGTH_SHORT).show();
                        break;
                    }
                }
                sheet.dismiss();
            });
            sheet.show();
        }
    }
}
