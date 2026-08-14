package com.chat.rtc.message;

import android.os.Parcel;

import androidx.annotation.NonNull;

import com.chat.base.msgitem.WKContentType;
import com.xinbida.wukongim.msgmodel.WKMessageContent;

import org.json.JSONException;
import org.json.JSONObject;

/** Registers the server's string-based one-to-one rtc_record message. */
public class RtcRecordContent extends WKMessageContent {
    public RtcRecordContent() {
        type = WKContentType.rtcRecord;
    }

    @NonNull
    @Override
    public JSONObject encodeMsg() {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("type", "rtc_record");
        } catch (JSONException ignored) {
        }
        return jsonObject;
    }

    @Override
    public WKMessageContent decodeMsg(JSONObject jsonObject) {
        return this;
    }

    protected RtcRecordContent(Parcel in) {
        super(in);
        type = WKContentType.rtcRecord;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
    }

    public static final Creator<RtcRecordContent> CREATOR = new Creator<RtcRecordContent>() {
        @Override
        public RtcRecordContent createFromParcel(Parcel in) {
            return new RtcRecordContent(in);
        }

        @Override
        public RtcRecordContent[] newArray(int size) {
            return new RtcRecordContent[size];
        }
    };
}
