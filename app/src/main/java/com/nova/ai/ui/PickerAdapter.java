package com.nova.ai.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.nova.ai.R;

import java.util.ArrayList;
import java.util.List;

public class PickerAdapter extends RecyclerView.Adapter<PickerAdapter.VH> {

    public static class Item {
        public final String name;
        public final String subtitle;
        public final String tag;
        public boolean selected;

        public Item(String name, String subtitle, String tag, boolean selected) {
            this.name = name;
            this.subtitle = subtitle;
            this.tag = tag;
            this.selected = selected;
        }

        public Item(String name, String tag) {
            this(name, "", tag, false);
        }

        public Item(String name) {
            this(name, "", name, false);
        }
    }

    public interface OnPick {
        void onPick(Item item, int position);
    }

    private List<Item> items = new ArrayList<>();
    private List<Item> filtered = new ArrayList<>();
    private final OnPick listener;

    public PickerAdapter(OnPick listener) {
        this.listener = listener;
    }

    public void setItems(List<Item> list) {
        items = list;
        filtered = new ArrayList<>(list);
        notifyDataSetChanged();
    }

    public void filter(String q) {
        q = q.toLowerCase();
        filtered = new ArrayList<>();
        for (Item i : items) {
            if (i.name.toLowerCase().contains(q) || i.tag.toLowerCase().contains(q)) {
                filtered.add(i);
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_picker, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        Item item = filtered.get(position);
        holder.name.setText(item.name);
        if (item.subtitle != null && !item.subtitle.isEmpty()) {
            holder.subtitle.setText(item.subtitle);
            holder.subtitle.setVisibility(View.VISIBLE);
        } else {
            holder.subtitle.setVisibility(View.GONE);
        }
        holder.check.setVisibility(item.selected ? View.VISIBLE : View.INVISIBLE);
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onPick(item, holder.getAdapterPosition());
        });
    }

    @Override
    public int getItemCount() { return filtered.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView name, subtitle;
        ImageView check;
        VH(View v) {
            super(v);
            name = v.findViewById(R.id.pickerItemName);
            subtitle = v.findViewById(R.id.pickerItemSub);
            check = v.findViewById(R.id.pickerItemCheck);
        }
    }
}
