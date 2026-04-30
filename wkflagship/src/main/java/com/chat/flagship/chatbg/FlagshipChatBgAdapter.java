package com.chat.flagship.chatbg;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.chat.base.glide.GlideUtils;
import com.chat.flagship.databinding.ItemFlagshipChatBgBinding;

import java.util.ArrayList;
import java.util.List;

/**
 * 聊天背景列表适配器
 * Created by Luckclouds and chatGPT.
 */
public class FlagshipChatBgAdapter extends RecyclerView.Adapter<FlagshipChatBgAdapter.ChatBgViewHolder> {
    private final List<FlagshipChatBgItem> data = new ArrayList<>();
    private OnItemClickListener onItemClickListener;

    public void setData(List<FlagshipChatBgItem> list) {
        data.clear();
        if (list != null) {
            data.addAll(list);
        }
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
        if (item.isDefault) {
            holder.binding.previewIv.setVisibility(View.GONE);
            holder.binding.defaultLayout.setVisibility(View.VISIBLE);
            holder.binding.nameTv.setText(com.chat.flagship.R.string.flagship_chat_bg_default);
        } else {
            holder.binding.previewIv.setVisibility(View.VISIBLE);
            holder.binding.defaultLayout.setVisibility(View.GONE);
            String cover = TextUtils.isEmpty(item.cover) ? item.url : item.cover;
            GlideUtils.getInstance().showImg(holder.binding.getRoot().getContext(), cover, holder.binding.previewIv);
            holder.binding.nameTv.setText(com.chat.flagship.R.string.flagship_chat_bg_preset);
        }
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
