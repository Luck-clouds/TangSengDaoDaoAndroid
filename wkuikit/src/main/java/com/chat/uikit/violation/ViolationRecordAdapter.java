package com.chat.uikit.violation;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.chat.uikit.R;

import java.util.ArrayList;

final class ViolationRecordAdapter extends BaseQuickAdapter<ViolationRecord, BaseViewHolder> {
    ViolationRecordAdapter() {
        super(R.layout.item_violation_record, new ArrayList<>());
    }

    @Override
    protected void convert(@NonNull BaseViewHolder holder, ViolationRecord item) {
        String category = item.getCategoryText(getContext());
        String reason = item.getReasonText(getContext());
        holder.setText(R.id.recordTypeTv, item.getTypeText(getContext()));
        holder.setText(R.id.statusTv, item.getStatusText(getContext()));
        holder.setText(R.id.categoryTv, category);
        holder.setText(R.id.reasonTv, reason);
        holder.setText(R.id.timeTv, item.getCreatedTimeText());
        holder.setGone(R.id.categoryTv, TextUtils.isEmpty(category));
        holder.setGone(R.id.reasonTv, TextUtils.isEmpty(reason));

        boolean complaint = item.isComplaint();
        holder.setBackgroundResource(R.id.recordTypeTv,
                complaint ? R.drawable.bg_record_type_complaint : R.drawable.bg_record_type_punishment);
        holder.setTextColor(R.id.statusTv, ContextCompat.getColor(getContext(),
                complaint ? R.color.blue : R.color.colorAccent));
    }
}
