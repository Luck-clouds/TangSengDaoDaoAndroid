package com.chat.rtc.entity;

import java.util.List;

public class RtcChannelStateResp {
    public boolean existing;
    public String call_id;
    public String room_name;
    public String status;
    public String call_type;
    public String channel_id;
    public byte channel_type;
    public String from_uid;
    public String from_name;
    public List<String> invite_uids;
    public List<String> target_uids;
    public long expire_at;
}
