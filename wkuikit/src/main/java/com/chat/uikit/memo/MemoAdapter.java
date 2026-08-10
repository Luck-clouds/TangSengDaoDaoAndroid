package com.chat.uikit.memo;

import android.text.TextUtils;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.chat.uikit.R;

import org.jetbrains.annotations.NotNull;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

final class MemoAdapter extends BaseQuickAdapter<MemoRecord, BaseViewHolder> {
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());

    MemoAdapter(List<MemoRecord> data) {
        super(R.layout.item_memo, data);
    }

    @Override
    protected void convert(@NotNull BaseViewHolder holder, MemoRecord item) {
        String title = item.title == null ? "" : item.title.trim();
        if (TextUtils.isEmpty(title)) {
            title = firstLine(item.content);
        }
        if (TextUtils.isEmpty(title)) {
            title = getContext().getString(R.string.memo_no_title);
        }
        String summary = item.content == null ? "" : item.content.trim().replace('\n', ' ');
        holder.setText(R.id.memoTitleTv, title);
        holder.setText(R.id.memoContentTv, summary);
        holder.setText(R.id.memoTimeTv, timeFormat.format(item.updatedAt));
        holder.setGone(R.id.memoContentTv, TextUtils.isEmpty(summary));
    }

    private String firstLine(String content) {
        if (TextUtils.isEmpty(content)) {
            return "";
        }
        String value = content.trim();
        int index = value.indexOf('\n');
        return index >= 0 ? value.substring(0, index).trim() : value;
    }
}
