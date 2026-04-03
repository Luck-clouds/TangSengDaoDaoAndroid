package com.chat.uikit.contacts.label;

import android.text.TextUtils;
import android.view.View;

import androidx.annotation.NonNull;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.chat.base.ui.components.AvatarView;
import com.chat.uikit.R;

public class LabelMemberAdapter extends BaseQuickAdapter<LabelMemberItem, BaseViewHolder> {
    private boolean deleteMode;

    public LabelMemberAdapter() {
        super(R.layout.item_label_member_layout);
    }

    public void setDeleteMode(boolean deleteMode) {
        this.deleteMode = deleteMode;
        notifyDataSetChanged();
    }

    @Override
    protected void convert(@NonNull BaseViewHolder holder, LabelMemberItem item) {
        View userLayout = holder.getView(R.id.userLayout);
        AvatarView avatarView = holder.getView(R.id.avatarView);
        View badgeIv = holder.getView(R.id.badgeIv);
        View handlerIv = holder.getView(R.id.handlerIv);
        if (item.itemType == LabelMemberItem.TYPE_MEMBER) {
            userLayout.setVisibility(View.VISIBLE);
            handlerIv.setVisibility(View.GONE);
            badgeIv.setVisibility(deleteMode ? View.VISIBLE : View.GONE);
            avatarView.setSize(50f);
            avatarView.setStrokeWidth(0);
            avatarView.showAvatar(item.channel);
            String showName = item.channel.channelRemark;
            if (TextUtils.isEmpty(showName)) {
                showName = item.channel.channelName;
            }
            holder.setText(R.id.nameTv, showName);
            return;
        }
        userLayout.setVisibility(View.GONE);
        handlerIv.setVisibility(View.VISIBLE);
        badgeIv.setVisibility(View.GONE);
        holder.setText(R.id.nameTv, item.itemType == LabelMemberItem.TYPE_ADD ? R.string.label_member_add : R.string.label_member_delete);
        holder.setImageResource(R.id.handlerIv, item.itemType == LabelMemberItem.TYPE_ADD ? R.mipmap.icon_chat_add : R.mipmap.icon_chat_delete);
        holder.setVisible(R.id.nameTv, true);
    }
}
