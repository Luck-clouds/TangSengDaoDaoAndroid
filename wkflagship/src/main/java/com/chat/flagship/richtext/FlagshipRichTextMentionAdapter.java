package com.chat.flagship.richtext;

/**
 * 富文本@成员选择适配器
 * Created by Luckclouds and chatGPT.
 */

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.chat.flagship.databinding.ItemFlagshipRichTextMemberBinding;
import com.xinbida.wukongim.entity.WKChannelType;
import com.xinbida.wukongim.entity.WKChannelMember;

import java.util.ArrayList;
import java.util.List;

public class FlagshipRichTextMentionAdapter extends RecyclerView.Adapter<FlagshipRichTextMentionAdapter.VH> {
    public interface IChooseMember {
        void onChoose(WKChannelMember member);
    }

    private final List<WKChannelMember> data = new ArrayList<>();
    private final IChooseMember chooseMember;

    public FlagshipRichTextMentionAdapter(IChooseMember chooseMember) {
        this.chooseMember = chooseMember;
    }

    public void setData(List<WKChannelMember> list) {
        data.clear();
        if (list != null) {
            data.addAll(list);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(ItemFlagshipRichTextMemberBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        WKChannelMember member = data.get(position);
        holder.binding.nameTv.setText(getDisplayName(member));
        holder.binding.avatarView.showAvatar(member.memberUID, WKChannelType.PERSONAL, member.memberAvatarCacheKey);
        holder.binding.getRoot().setOnClickListener(v -> chooseMember.onChoose(member));
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    private String getDisplayName(WKChannelMember member) {
        if (member == null) {
            return "";
        }
        if (!TextUtils.isEmpty(member.memberRemark)) {
            return member.memberRemark;
        }
        if (!TextUtils.isEmpty(member.memberName)) {
            return member.memberName;
        }
        return member.memberUID;
    }

    static class VH extends RecyclerView.ViewHolder {
        private final ItemFlagshipRichTextMemberBinding binding;

        public VH(ItemFlagshipRichTextMemberBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
