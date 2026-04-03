package com.chat.uikit.contacts.label;

import androidx.annotation.NonNull;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.chat.uikit.R;

public class LabelListAdapter extends BaseQuickAdapter<LabelEntity, BaseViewHolder> {
    public LabelListAdapter() {
        super(R.layout.item_label_list_layout);
    }

    @Override
    protected void convert(@NonNull BaseViewHolder holder, LabelEntity item) {
        holder.setText(R.id.nameTv, String.format("%s(%d)", item.name, item.getMemberCount()));
    }
}
