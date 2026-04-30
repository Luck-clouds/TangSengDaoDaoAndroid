package com.chat.flagship.receipt;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.chat.flagship.databinding.ItemFlagshipMsgReceiptUserBinding;
import com.chat.flagship.entity.FlagshipReceiptUser;
import com.xinbida.wukongim.entity.WKChannelType;

import java.util.ArrayList;
import java.util.List;

/**
 * 消息回执成员列表
 * Created by Luckclouds and chatGPT.
 */
public class FlagshipMsgReceiptUserAdapter extends RecyclerView.Adapter<FlagshipMsgReceiptUserAdapter.ViewHolder> {
    private final List<FlagshipReceiptUser> data = new ArrayList<>();

    public void setData(List<FlagshipReceiptUser> list) {
        data.clear();
        if (list != null) {
            data.addAll(list);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(ItemFlagshipMsgReceiptUserBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        FlagshipReceiptUser item = data.get(position);
        holder.binding.avatarView.showAvatar(item.uid, WKChannelType.PERSONAL);
        holder.binding.nameTv.setText(item.name);
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemFlagshipMsgReceiptUserBinding binding;

        ViewHolder(ItemFlagshipMsgReceiptUserBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
