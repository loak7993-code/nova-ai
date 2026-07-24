package com.nova.ai.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.nova.ai.R;
import com.nova.ai.data.ModelInfo;

import java.util.ArrayList;
import java.util.List;

public class ModelPickerAdapter extends RecyclerView.Adapter<ModelPickerAdapter.VH> {

    public interface OnPick {
        void onPick(String modelId, int position);
    }

    private final List<ModelInfo> items = new ArrayList<>();
    private final OnPick listener;
    private String selectedId;

    public ModelPickerAdapter(OnPick listener) {
        this.listener = listener;
    }

    public void setItems(List<ModelInfo> list, String selectedId) {
        items.clear();
        items.addAll(list);
        this.selectedId = selectedId;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_model_card, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        ModelInfo m = items.get(position);
        Context ctx = holder.itemView.getContext();

        String initial = m.name.isEmpty() ? "?" : m.name.substring(0, 1).toUpperCase();
        holder.avatar.setText(initial);
        holder.name.setText(m.name);

        boolean isActive = m.id.equals(selectedId);
        if (isActive) {
            holder.itemView.setBackgroundResource(R.drawable.bg_model_card_selected);
            holder.check.setVisibility(View.VISIBLE);
        } else {
            holder.itemView.setBackgroundResource(R.drawable.bg_model_card);
            holder.check.setVisibility(View.INVISIBLE);
        }

        holder.chips.removeAllViews();
        boolean isFree = m.description.toLowerCase().contains("free");
        if (isFree) addChip(ctx, holder.chips, "FREE", true);
        if (m.vision) addChip(ctx, holder.chips, "VISION", false);
        String descLower = m.description.toLowerCase();
        if (descLower.contains("reasoning")) addChip(ctx, holder.chips, "REASON", false);
        if (descLower.contains("coding") || descLower.contains("code")) addChip(ctx, holder.chips, "CODE", false);
        if (descLower.contains("tools") || descLower.contains("agentic")) addChip(ctx, holder.chips, "TOOLS", false);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onPick(m.id, holder.getAdapterPosition());
        });
    }

    private void addChip(Context ctx, LinearLayout container, String label, boolean free) {
        TextView chip = (TextView) LayoutInflater.from(ctx).inflate(R.layout.item_chip, container, false);
        chip.setText(label);
        if (free) {
            chip.setBackgroundResource(R.drawable.bg_chip_free);
            chip.setTextColor(ctx.getResources().getColor(R.color.nova_primary));
        }
        container.addView(chip);
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView avatar, name;
        LinearLayout chips;
        ImageView check;
        VH(View v) {
            super(v);
            avatar = v.findViewById(R.id.modelAvatar);
            name = v.findViewById(R.id.modelName);
            chips = v.findViewById(R.id.modelChips);
            check = v.findViewById(R.id.modelCheck);
        }
    }
}
