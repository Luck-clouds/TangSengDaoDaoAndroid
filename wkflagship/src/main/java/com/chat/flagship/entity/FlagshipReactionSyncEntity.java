package com.chat.flagship.entity;

/**
 * 消息回应同步实体
 * Created by Luckclouds and chatGPT.
 */
public class FlagshipReactionSyncEntity {
    public String messageId;
    public String uid;
    public String name;
    public String channelId;
    public byte channelType;
    public long seq;
    public String emoji;
    public int isDeleted;
    public String createdAt;
}
