package com.chat.flagship.search.video;

import com.chad.library.adapter.base.entity.MultiItemEntity;
import com.chat.base.entity.GlobalMessage;

/**
 * 视频搜索页数据项
 * Created by Luckclouds .
 */
public class FlagshipSearchVideoEntity implements MultiItemEntity {
    public static final int TYPE_ITEM = 0;
    public static final int TYPE_HEADER = 1;

    public int itemType = TYPE_ITEM;
    public String date;
    public String coverPath;
    public String playPath;
    public GlobalMessage message;

    @Override
    public int getItemType() {
        return itemType;
    }
}
