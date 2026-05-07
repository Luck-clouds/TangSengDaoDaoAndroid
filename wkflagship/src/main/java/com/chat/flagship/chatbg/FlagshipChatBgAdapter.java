package com.chat.flagship.chatbg;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.chat.flagship.databinding.ItemFlagshipChatBgBinding;

import java.util.ArrayList;
import java.util.List;

/**
 * 聊天背景列表适配器
 * Created by Luckclouds .
 */
public class FlagshipChatBgAdapter extends RecyclerView.Adapter<FlagshipChatBgAdapter.ChatBgViewHolder> {
    private final List<FlagshipChatBgItem> data = new ArrayList<>();
    private OnItemClickListener onItemClickListener;
    private FlagshipChatBgConfig currentConfig;

    public void setData(List<FlagshipChatBgItem> list) {
        data.clear();
        if (list != null) {
            data.addAll(list);
        }
        notifyDataSetChanged();
    }

    public void setCurrentConfig(FlagshipChatBgConfig config) {
        this.currentConfig = config;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ChatBgViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ChatBgViewHolder(ItemFlagshipChatBgBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ChatBgViewHolder holder, int position) {
        FlagshipChatBgItem item = data.get(position);
        boolean isSelected = isSelected(item);
        if (item.isDefault) {
            holder.binding.previewIv.setVisibility(View.GONE);
            holder.binding.defaultLayout.setVisibility(View.VISIBLE);
        } else {
            holder.binding.previewIv.setVisibility(View.VISIBLE);
            holder.binding.defaultLayout.setVisibility(View.GONE);
            FlagshipChatBgManager.getInstance().loadPreviewImage(holder.binding.previewIv, item);
        }
        holder.binding.checkedTv.setVisibility(isSelected ? View.VISIBLE : View.GONE);
        holder.binding.getRoot().setOnClickListener(v -> {
            if (onItemClickListener != null) {
                onItemClickListener.onClick(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.onItemClickListener = listener;
    }

    private boolean isSelected(FlagshipChatBgItem item) {
        if (item == null) {
            return false;
        }
        if (item.isDefault) {
            return currentConfig == null || currentConfig.type == FlagshipChatBgConfig.TYPE_DEFAULT;
        }
        return currentConfig != null
                && currentConfig.type == FlagshipChatBgConfig.TYPE_PRESET
                && TextUtils.equals(item.url, currentConfig.sourceUrl);
    }

    public interface OnItemClickListener {
        void onClick(FlagshipChatBgItem item);
    }

    static class ChatBgViewHolder extends RecyclerView.ViewHolder {
        final ItemFlagshipChatBgBinding binding;

        ChatBgViewHolder(ItemFlagshipChatBgBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
