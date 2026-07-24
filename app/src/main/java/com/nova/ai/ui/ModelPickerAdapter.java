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

    private static final int CHIP_FREE = 0;
    private static final int CHIP_VISION = 1;
    private static final int CHIP_REASON = 2;
    private static final int CHIP_CODE = 3;
    private static final int CHIP_TOOLS = 4;

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
            holder.name.setTextColor(ctx.getResources().getColor(R.color.nova_primary));
        } else {
            holder.itemView.setBackgroundResource(R.drawable.bg_model_card);
            holder.check.setVisibility(View.INVISIBLE);
            holder.name.setTextColor(ctx.getResources().getColor(R.color.text_primary_dark));
        }

        holder.chips.removeAllViews();
        String descLower = m.description.toLowerCase();
        if (descLower.contains("free")) addChip(ctx, holder.chips, "FREE", CHIP_FREE);
        if (m.vision) addChip(ctx, holder.chips, "VISION", CHIP_VISION);
        if (descLower.contains("reasoning")) addChip(ctx, holder.chips, "REASON", CHIP_REASON);
        if (descLower.contains("coding") || descLower.contains("code")) addChip(ctx, holder.chips, "CODE", CHIP_CODE);
        if (descLower.contains("tools") || descLower.contains("agentic")) addChip(ctx, holder.chips, "TOOLS", CHIP_TOOLS);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onPick(m.id, holder.getAdapterPosition());
        });
    }

    private void addChip(Context ctx, LinearLayout container, String label, int type) {
        TextView chip = (TextView) LayoutInflater.from(ctx).inflate(R.layout.item_chip, container, false);
        chip.setText(label);
        int bgRes;
        int textColor;
        switch (type) {
            case CHIP_FREE:
                bgRes = R.drawable.bg_chip_free;
                textColor = ctx.getResources().getColor(R.color.nova_primary);
                break;
            case CHIP_VISION:
                bgRes = R.drawable.bg_chip_vision;
                textColor = 0xFF8AB4D8;
                break;
            case CHIP_REASON:
                bgRes = R.drawable.bg_chip_reason;
                textColor = 0xFFC4A8E0;
                break;
            case CHIP_CODE:
                bgRes = R.drawable.bg_chip_code;
                textColor = 0xFF8AD4A0;
                break;
            case CHIP_TOOLS:
                bgRes = R.drawable.bg_chip_tools;
                textColor = 0xFF8AC4D4;
                break;
            default:
                bgRes = R.drawable.bg_chip;
                textColor = ctx.getResources().getColor(R.color.text_secondary_dark);
                break;
        }
        chip.setBackgroundResource(bgRes);
        chip.setTextColor(textColor);
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
