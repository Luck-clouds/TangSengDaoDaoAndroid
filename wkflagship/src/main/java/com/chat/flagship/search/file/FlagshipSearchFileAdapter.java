package com.chat.flagship.search.file;

import android.text.TextUtils;
import android.text.format.Formatter;

import androidx.annotation.NonNull;

import com.chad.library.adapter.base.BaseMultiItemQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.chat.base.ui.components.AvatarView;
import com.chat.flagship.R;
import com.xinbida.wukongim.entity.WKChannelType;

import java.util.Locale;

/**
 * 文件记录搜索适配器
 * Created by Luckclouds and chatGPT.
 */
class FlagshipSearchFileAdapter extends BaseMultiItemQuickAdapter<FlagshipSearchFileEntity, BaseViewHolder> {

    FlagshipSearchFileAdapter() {
        addItemType(FlagshipSearchFileEntity.TYPE_ITEM, R.layout.item_flagship_search_file);
        addItemType(FlagshipSearchFileEntity.TYPE_HEADER, R.layout.item_flagship_search_header);
    }

    @Override
    protected void convert(@NonNull BaseViewHolder holder, FlagshipSearchFileEntity item) {
        if (item.getItemType() == FlagshipSearchFileEntity.TYPE_HEADER) {
            holder.setText(R.id.titleTv, item.date);
            return;
        }
        holder.setText(R.id.nameTv, item.senderName);
        holder.setText(R.id.dateTv, item.displayTime);
        holder.setText(R.id.fileNameTv, item.fileName);
        holder.setText(R.id.extTv, formatExt(item.fileExt));
        holder.setText(R.id.fileSizeTv, Formatter.formatShortFileSize(getContext(), Math.max(item.fileSize, 0L)));

        AvatarView avatarView = holder.getView(R.id.avatarView);
        if (!TextUtils.isEmpty(item.senderUID)) {
            avatarView.showAvatar(item.senderUID, WKChannelType.PERSONAL);
        }
    }

    private String formatExt(String ext) {
        if (TextUtils.isEmpty(ext)) {
            return getContext().getString(R.string.flagship_search_file_unknown_ext);
        }
        String display = ext.startsWith(".") ? ext.substring(1) : ext;
        display = display.toUpperCase(Locale.ROOT);
        if (display.length() > 5) {
            display = display.substring(0, 5);
        }
        return display;
    }
}
