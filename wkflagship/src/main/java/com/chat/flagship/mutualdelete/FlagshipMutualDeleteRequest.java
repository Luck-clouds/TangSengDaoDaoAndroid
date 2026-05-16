package com.chat.flagship.mutualdelete;

/**
 * 批量双向删除请求体。
 */
public class FlagshipMutualDeleteRequest {
    public String message_id;
    public String channel_id;
    public byte channel_type;
    public int message_seq;
}
