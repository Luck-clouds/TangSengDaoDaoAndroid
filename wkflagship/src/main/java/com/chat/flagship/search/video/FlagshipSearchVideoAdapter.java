package com.chat.flagship.search.video;

import android.text.TextUtils;
import android.widget.ImageView;

import androidx.annotation.NonNull;

import com.chad.library.adapter.base.BaseMultiItemQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.chat.base.glide.GlideUtils;
import com.chat.flagship.R;

/**
 * 视频记录搜索适配器
 * Created by Luckclouds and chatGPT.
 */
class FlagshipSearchVideoAdapter extends BaseMultiItemQuickAdapter<FlagshipSearchVideoEntity, BaseViewHolder> {

    FlagshipSearchVideoAdapter() {
        addItemType(FlagshipSearchVideoEntity.TYPE_ITEM, R.layout.item_flagship_search_video);
        addItemType(FlagshipSearchVideoEntity.TYPE_HEADER, R.layout.item_flagship_search_header);
    }

    @Override
    protected void convert(@NonNull BaseViewHolder holder, FlagshipSearchVideoEntity item) {
        if (item.getItemType() == FlagshipSearchVideoEntity.TYPE_HEADER) {
            holder.setText(R.id.titleTv, item.date);
            return;
        }
        ImageView coverIv = holder.getView(R.id.coverIv);
        if (!TextUtils.isEmpty(item.coverPath)) {
            GlideUtils.getInstance().showImg(getContext(), item.coverPath, coverIv);
        } else {
            coverIv.setImageDrawable(null);
            coverIv.setBackgroundColor(0xFF111111);
        }
    }
}
