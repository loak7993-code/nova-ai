package com.nova.ai.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.nova.ai.R;
import com.nova.ai.data.Message;

import java.util.List;

public class MessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_USER = 1;
    private static final int TYPE_AI = 2;
    private static final int TYPE_ERROR = 3;
    private static final int TYPE_TOOL = 4;

    public interface ActionListener {
        void onRegenerate();
        void onEditUser(String currentText);
    }

    private final Context ctx;
    private final List<Message> messages;
    private ActionListener actionListener;

    public MessageAdapter(Context ctx, List<Message> messages) {
        this.ctx = ctx;
        this.messages = messages;
    }

    public void setActionListener(ActionListener l) { this.actionListener = l; }

    @Override
    public int getItemViewType(int position) {
        Message m = messages.get(position);
        if (m.isError()) return TYPE_ERROR;
        if (m.isUser()) return TYPE_USER;
        if (m.isTool()) return TYPE_TOOL;
        return TYPE_AI;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_USER) return new UserVH(inf.inflate(R.layout.item_message_user, parent, false));
        if (viewType == TYPE_ERROR) return new ErrorVH(inf.inflate(R.layout.item_message_error, parent, false));
        if (viewType == TYPE_TOOL) return new ToolVH(inf.inflate(R.layout.item_message_tool, parent, false));
        return new AiVH(inf.inflate(R.layout.item_message_ai, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Message m = messages.get(position);
        if (holder instanceof UserVH) ((UserVH) holder).bind(m);
        else if (holder instanceof AiVH) ((AiVH) holder).bind(m);
        else if (holder instanceof ErrorVH) ((ErrorVH) holder).bind(m);
        else if (holder instanceof ToolVH) ((ToolVH) holder).bind(m);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (!payloads.isEmpty() && payloads.contains("stream")) {
            bindStreaming(holder, position);
        } else {
            onBindViewHolder(holder, position);
        }
    }

    @Override
    public int getItemCount() { return messages.size(); }

    private void copyToClipboard(String label, String text) {
        ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText(label, text));
        Toast.makeText(ctx, R.string.copied, Toast.LENGTH_SHORT).show();
    }

    private void shareText(String text) {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, text);
        ctx.startActivity(Intent.createChooser(send, ctx.getString(R.string.share)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    class UserVH extends RecyclerView.ViewHolder {
        TextView text;
        ImageView image;
        ImageButton copy, edit;
        String currentRaw = "";
        UserVH(View v) {
            super(v);
            text = v.findViewById(R.id.userText);
            image = v.findViewById(R.id.userImage);
            copy = v.findViewById(R.id.userActionCopy);
            edit = v.findViewById(R.id.userActionEdit);
        }
        void bind(Message m) {
            currentRaw = m.content == null ? "" : m.content;
            text.setText(currentRaw);
            if (m.hasImage()) {
                try {
                    android.graphics.Bitmap bmp = com.nova.ai.util.ImageLoader.load(m.imagePath, 400);
                    if (bmp != null) {
                        image.setImageBitmap(bmp);
                        image.setVisibility(View.VISIBLE);
                    } else {
                        image.setVisibility(View.GONE);
                    }
                } catch (Exception e) {
                    image.setVisibility(View.GONE);
                }
            } else {
                image.setVisibility(View.GONE);
            }
            copy.setOnClickListener(v -> copyToClipboard("Nova", currentRaw));
            edit.setOnClickListener(v -> { if (actionListener != null) actionListener.onEditUser(currentRaw); });
        }
    }

    class AiVH extends RecyclerView.ViewHolder {
        TextView text;
        ProgressBar progress;
        View actions, thinkingSection;
        TextView thinkingText, thinkingLabel;
        ProgressBar thinkingSpinner;
        ImageView thinkingChevron;
        View thinkingHeader;
        boolean thinkingExpanded = false;
        ImageButton copy, regenerate, share;
        String currentRaw = "";

        AiVH(View v) {
            super(v);
            text = v.findViewById(R.id.aiText);
            progress = v.findViewById(R.id.thinkingProgress);
            actions = v.findViewById(R.id.aiActions);
            thinkingSection = v.findViewById(R.id.thinkingSection);
            thinkingText = v.findViewById(R.id.thinkingText);
            thinkingLabel = v.findViewById(R.id.thinkingLabel);
            thinkingSpinner = v.findViewById(R.id.thinkingSpinner);
            thinkingChevron = v.findViewById(R.id.thinkingChevron);
            thinkingHeader = v.findViewById(R.id.thinkingHeader);
            copy = v.findViewById(R.id.actionCopy);
            regenerate = v.findViewById(R.id.actionRegenerate);
            share = v.findViewById(R.id.actionShare);
            text.setMovementMethod(LinkMovementMethod.getInstance());

            thinkingHeader.setOnClickListener(hdr -> {
                thinkingExpanded = !thinkingExpanded;
                thinkingText.setVisibility(thinkingExpanded ? View.VISIBLE : View.GONE);
                thinkingChevron.setRotation(thinkingExpanded ? 180 : 0);
            });
        }

        void bind(Message m) {
            currentRaw = m.content == null ? "" : m.content;
            String reasoning = m.reasoning == null ? "" : m.reasoning;

            if (m.thinking && reasoning.isEmpty()) {
                text.setText(R.string.thinking);
                progress.setVisibility(View.VISIBLE);
                actions.setVisibility(View.GONE);
                thinkingSection.setVisibility(View.GONE);
                return;
            }
            progress.setVisibility(View.GONE);
            if (currentRaw.isEmpty() && m.thinking) {
                text.setText("");
            } else {
                text.setText(MarkdownFormatter.format(currentRaw));
            }

            if (!reasoning.isEmpty()) {
                thinkingSection.setVisibility(View.VISIBLE);
                thinkingText.setText(reasoning);
                if (m.streaming || m.thinking) {
                    thinkingSpinner.setVisibility(View.VISIBLE);
                    thinkingLabel.setText("Thinking");
                    if (!thinkingExpanded) {
                        thinkingText.setVisibility(View.VISIBLE);
                        thinkingChevron.setRotation(180);
                    }
                } else {
                    thinkingSpinner.setVisibility(View.GONE);
                    thinkingLabel.setText("Thought process");
                }
            } else {
                thinkingSection.setVisibility(View.GONE);
            }

            if (m.streaming) {
                actions.setVisibility(View.GONE);
            } else {
                actions.setVisibility(View.VISIBLE);
                copy.setOnClickListener(v -> copyToClipboard("Nova", currentRaw));
                regenerate.setOnClickListener(v -> { if (actionListener != null) actionListener.onRegenerate(); });
                share.setOnClickListener(v -> shareText(currentRaw));
            }
        }
    }

    class ToolVH extends RecyclerView.ViewHolder {
        View searchingRow;
        android.widget.HorizontalScrollView sourcesScroll;
        ViewGroup sourcesContainer;
        ToolVH(View v) {
            super(v);
            searchingRow = v.findViewById(R.id.toolSearchingRow);
            sourcesScroll = v.findViewById(R.id.toolSourcesScroll);
            sourcesContainer = v.findViewById(R.id.toolSourcesContainer);
        }
        void bind(Message m) {
            if (!m.searching && !m.hasSources()) {
                itemView.setVisibility(View.GONE);
                ViewGroup.LayoutParams lp = itemView.getLayoutParams();
                if (lp != null) {
                    lp.height = 0;
                    itemView.setLayoutParams(lp);
                }
                return;
            }
            itemView.setVisibility(View.VISIBLE);
            ViewGroup.LayoutParams lp = itemView.getLayoutParams();
            if (lp != null) {
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                itemView.setLayoutParams(lp);
            }

            if (m.searching) {
                searchingRow.setVisibility(View.VISIBLE);
                sourcesScroll.setVisibility(View.GONE);
                return;
            }
            searchingRow.setVisibility(View.GONE);

            if (m.hasSources()) {
                sourcesContainer.removeAllViews();
                int max = 3;
                int count = m.sources.size();
                int shown = Math.min(count, max);

                for (int i = 0; i < shown; i++) {
                    sourcesContainer.addView(makeBadge(m.sources.get(i)));
                }
                if (count > max) {
                    sourcesContainer.addView(makeBadge("+" + (count - max)));
                }
                sourcesScroll.setVisibility(View.VISIBLE);
            } else {
                sourcesScroll.setVisibility(View.GONE);
            }
        }

        private View makeBadge(String text) {
            TextView tv = new TextView(ctx);
            tv.setText(text);
            tv.setTextSize(12);
            tv.setTextColor(0xFFD4915D);
            android.widget.LinearLayout.LayoutParams lp =
                    new android.widget.LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(8);
            tv.setLayoutParams(lp);
            tv.setBackgroundResource(R.drawable.bg_source_badge);
            int pad = (int) (6 * ctx.getResources().getDisplayMetrics().density);
            tv.setPadding(pad * 2, pad, pad * 2, pad);
            return tv;
        }
    }

    class ErrorVH extends RecyclerView.ViewHolder {
        TextView text;
        ErrorVH(View v) {
            super(v);
            text = v.findViewById(R.id.errorText);
        }
        void bind(Message m) { text.setText(m.content); }
    }

    public void updateStreaming(int position) {
        notifyItemChanged(position, "stream");
    }

    public void bindStreaming(RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof AiVH && position < messages.size()) {
            AiVH vh = (AiVH) holder;
            Message m = messages.get(position);
            String reasoning = m.reasoning == null ? "" : m.reasoning;
            String content = m.content == null ? "" : m.content;

            if (content.isEmpty()) {
                vh.text.setText("");
            } else {
                vh.text.setText(MarkdownFormatter.format(content));
            }

            if (!reasoning.isEmpty()) {
                vh.thinkingSection.setVisibility(View.VISIBLE);
                vh.thinkingText.setText(reasoning);
                if (m.streaming || m.thinking) {
                    vh.thinkingSpinner.setVisibility(View.VISIBLE);
                    vh.thinkingLabel.setText("Thinking");
                } else {
                    vh.thinkingSpinner.setVisibility(View.GONE);
                    vh.thinkingLabel.setText("Thought process");
                }
            } else {
                vh.thinkingSection.setVisibility(View.GONE);
            }
        }
    }
}
