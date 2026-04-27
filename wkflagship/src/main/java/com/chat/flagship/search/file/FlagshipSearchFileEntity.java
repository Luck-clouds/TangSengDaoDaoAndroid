package com.chat.flagship.search.file;

import com.chad.library.adapter.base.entity.MultiItemEntity;
import com.chat.base.entity.GlobalMessage;

/**
 * 文件搜索页数据项
 * Created by Luckclouds and chatGPT.
 */
public class FlagshipSearchFileEntity implements MultiItemEntity {
    public static final int TYPE_ITEM = 0;
    public static final int TYPE_HEADER = 1;

    public int itemType = TYPE_ITEM;
    public String date;
    public String displayTime;
    public String senderName;
    public String senderUID;
    public String fileName;
    public String fileExt;
    public String fileUrl;
    public String localPath;
    public String clientMsgNo;
    public long fileSize;
    public GlobalMessage message;

    @Override
    public int getItemType() {
        return itemType;
    }
}
