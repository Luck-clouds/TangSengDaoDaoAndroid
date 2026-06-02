package com.chat.rtc.entity;

import java.util.List;

public class RtcCallResp {
    public String call_id;
    public boolean existing;
    public String room_name;
    public long expire_at;
    public String status;
    public List<String> permissions;
    public RtcLiveKit livekit;
}
