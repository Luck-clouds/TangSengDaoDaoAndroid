package com.chat.rtc.message;

import android.os.Parcel;

import androidx.annotation.NonNull;

import com.chat.base.msgitem.WKContentType;
import com.xinbida.wukongim.msgmodel.WKMessageContent;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class RtcNoticeContent extends WKMessageContent {
    public String callId;
    public String roomName;
    public String channelId;
    public byte channelType;
    public String callType;
    public String fromUid;
    public String fromName;
    public long expireAt;
    public List<String> targetUIDs;
    public boolean inviteAll;

    public RtcNoticeContent() {
        type = WKContentType.rtcNotice;
    }

    @NonNull
    @Override
    public JSONObject encodeMsg() {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("type", "rtc_notice");
            jsonObject.put("record_type", "");
            jsonObject.put("call_id", callId);
            jsonObject.put("room_name", roomName);
            jsonObject.put("channel_id", channelId);
            jsonObject.put("channel_type", channelType);
            jsonObject.put("call_type", callType);
            jsonObject.put("from_uid", fromUid);
            jsonObject.put("from_name", fromName);
            jsonObject.put("expire_at", expireAt);
            jsonObject.put("started_at", 0);
            jsonObject.put("ended_at", 0);
            jsonObject.put("duration", 0);
            jsonObject.put("invite_all", inviteAll);
            JSONArray targets = new JSONArray();
            if (targetUIDs != null) {
                for (String uid : targetUIDs) {
                    targets.put(uid);
                }
            }
            jsonObject.put("target_uids", targets);
            jsonObject.put("invite_uids", targets);
        } catch (JSONException ignored) {
        }
        return jsonObject;
    }

    @Override
    public WKMessageContent decodeMsg(JSONObject jsonObject) {
        callId = jsonObject.optString("call_id");
        roomName = jsonObject.optString("room_name");
        channelId = jsonObject.optString("channel_id");
        channelType = (byte) jsonObject.optInt("channel_type");
        callType = jsonObject.optString("call_type");
        fromUid = jsonObject.optString("from_uid");
        fromName = jsonObject.optString("from_name");
        expireAt = jsonObject.optLong("expire_at");
        inviteAll = jsonObject.optBoolean("invite_all");
        JSONArray targets = jsonObject.optJSONArray("target_uids");
        if (targets == null) {
            targets = jsonObject.optJSONArray("invite_uids");
        }
        if (targets != null) {
            targetUIDs = new ArrayList<>();
            for (int i = 0; i < targets.length(); i++) {
                String uid = targets.optString(i);
                if (uid != null && uid.length() > 0) {
                    targetUIDs.add(uid);
                }
            }
        }
        return this;
    }

    protected RtcNoticeContent(Parcel in) {
        super(in);
        callId = in.readString();
        roomName = in.readString();
        channelId = in.readString();
        channelType = in.readByte();
        callType = in.readString();
        fromUid = in.readString();
        fromName = in.readString();
        expireAt = in.readLong();
        targetUIDs = in.createStringArrayList();
        inviteAll = in.readByte() == 1;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeString(callId);
        dest.writeString(roomName);
        dest.writeString(channelId);
        dest.writeByte(channelType);
        dest.writeString(callType);
        dest.writeString(fromUid);
        dest.writeString(fromName);
        dest.writeLong(expireAt);
        dest.writeStringList(targetUIDs);
        dest.writeByte((byte) (inviteAll ? 1 : 0));
    }

    public static final Creator<RtcNoticeContent> CREATOR = new Creator<RtcNoticeContent>() {
        @Override
        public RtcNoticeContent createFromParcel(Parcel in) {
            return new RtcNoticeContent(in);
        }

        @Override
        public RtcNoticeContent[] newArray(int size) {
            return new RtcNoticeContent[size];
        }
    };
}
