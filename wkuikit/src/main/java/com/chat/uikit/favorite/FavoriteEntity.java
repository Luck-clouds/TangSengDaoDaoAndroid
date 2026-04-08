package com.chat.uikit.favorite;

import android.text.TextUtils;

import com.chat.base.msgitem.WKContentType;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelType;
import com.xinbida.wukongim.entity.WKMsg;
import com.xinbida.wukongim.msgmodel.WKImageContent;
import com.xinbida.wukongim.msgmodel.WKTextContent;

import java.io.Serializable;

public class FavoriteEntity implements Serializable {
    public long id;
    public String messageId;
    public long messageSeq;
    public String channelId;
    public byte channelType;
    public int type;
    public String content;
    public int width;
    public int height;
    public String authorUid;
    public String authorName;
    public String authorAvatar;
    public String authorAvatarCacheKey;
    public String createdAt;

    public boolean isImageType() {
        return type == WKContentType.WK_IMAGE;
    }

    public boolean isTextType() {
        return type == WKContentType.WK_TEXT;
    }

    public String getDisplayName() {
        if (!TextUtils.isEmpty(authorName)) {
            return authorName;
        }
        if (!TextUtils.isEmpty(authorUid)) {
            return authorUid;
        }
        return "";
    }

    public String getDisplayContent() {
        return TextUtils.isEmpty(content) ? "" : content;
    }

    public WKChannel buildAuthorChannel() {
        if (TextUtils.isEmpty(authorUid)) {
            return null;
        }
        WKChannel local = WKIM.getInstance().getChannelManager().getChannel(authorUid, WKChannelType.PERSONAL);
        WKChannel channel = new WKChannel();
        channel.channelID = authorUid;
        channel.channelType = WKChannelType.PERSONAL;
        if (local != null) {
            channel.channelName = local.channelName;
            channel.channelRemark = local.channelRemark;
            channel.avatar = local.avatar;
            channel.avatarCacheKey = local.avatarCacheKey;
        }
        if (!TextUtils.isEmpty(authorName)) {
            channel.channelName = authorName;
        }
        if (!TextUtils.isEmpty(authorAvatar)) {
            channel.avatar = authorAvatar;
        }
        if (!TextUtils.isEmpty(authorAvatarCacheKey)) {
            channel.avatarCacheKey = authorAvatarCacheKey;
        }
        if (TextUtils.isEmpty(channel.channelName)) {
            channel.channelName = authorUid;
        }
        return channel;
    }

    public WKMsg findLocalMsg() {
        if (TextUtils.isEmpty(messageId)) {
            return null;
        }
        return WKIM.getInstance().getMsgManager().getWithMessageID(messageId);
    }

    public void fillFromLocalIfNeed() {
        WKMsg local = findLocalMsg();
        if (local == null) {
            return;
        }
        WKChannel fromChannel = local.getFrom();
        if (fromChannel == null && !TextUtils.isEmpty(local.fromUID)) {
            fromChannel = WKIM.getInstance().getChannelManager().getChannel(local.fromUID, WKChannelType.PERSONAL);
        }
        if (type == 0) {
            type = local.type;
        }
        if (TextUtils.isEmpty(authorUid) && fromChannel != null) {
            authorUid = fromChannel.channelID;
        }
        if (TextUtils.isEmpty(authorName) && fromChannel != null) {
            authorName = TextUtils.isEmpty(fromChannel.channelRemark) ? fromChannel.channelName : fromChannel.channelRemark;
        }
        if (TextUtils.isEmpty(authorAvatar) && fromChannel != null) {
            authorAvatar = fromChannel.avatar;
        }
        if (TextUtils.isEmpty(authorAvatarCacheKey) && fromChannel != null) {
            authorAvatarCacheKey = fromChannel.avatarCacheKey;
        }
        if (local.baseContentMsgModel instanceof WKTextContent && TextUtils.isEmpty(content)) {
            content = ((WKTextContent) local.baseContentMsgModel).getDisplayContent();
        } else if (local.baseContentMsgModel instanceof WKImageContent) {
            WKImageContent imageContent = (WKImageContent) local.baseContentMsgModel;
            if (TextUtils.isEmpty(content)) {
                content = imageContent.url;
            }
            if (width <= 0) {
                width = imageContent.width;
            }
            if (height <= 0) {
                height = imageContent.height;
            }
        }
    }
}
