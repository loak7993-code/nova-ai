package com.nova.ai.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.nova.ai.R;
import com.nova.ai.data.Conversation;

import java.util.ArrayList;
import java.util.List;

public class ConversationAdapter extends RecyclerView.Adapter<ConversationAdapter.VH> {

    public interface Listener {
        void onOpen(Conversation c);
        void onDelete(Conversation c, int position);
    }

    private List<Conversation> items = new ArrayList<>();
    private final Listener listener;
    private String activeId = null;

    public ConversationAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<Conversation> list) {
        items = list;
        notifyDataSetChanged();
    }

    public void setActiveId(String id) {
        this.activeId = id;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_conversation, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        Conversation c = items.get(position);
        holder.title.setText(c.preview());
        boolean active = c.id.equals(activeId);
        holder.itemView.setBackgroundResource(
                active ? R.drawable.bg_conv_item_active : R.drawable.bg_conv_item);
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onOpen(c);
        });
        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) listener.onDelete(c, holder.getAdapterPosition());
            return true;
        });
        holder.delete.setOnClickListener(v -> {
            if (listener != null) listener.onDelete(c, holder.getAdapterPosition());
        });
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView title;
        ImageView delete;
        VH(View v) {
            super(v);
            title = v.findViewById(R.id.convTitle);
            delete = v.findViewById(R.id.convDelete);
        }
    }
}
