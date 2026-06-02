package com.chat.rtc.entity;

import java.util.List;

public class RtcCallPayload {
    public String call_id;
    public String room_name;
    public String channel_id;
    public byte channel_type;
    public String call_type;
    public String from_uid;
    public String from_name;
    public List<String> invite_uids;
    public long expire_at;
    public String answer_uid;
    public String answer_device_id;
    public String reason;
    public String uid;
    public List<String> target_uids;
    public boolean invite_all;
}
