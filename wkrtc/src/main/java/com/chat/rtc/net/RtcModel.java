package com.chat.rtc.net;

import android.util.Log;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.chat.base.base.WKBaseModel;
import com.chat.base.net.IRequestResultListener;
import com.chat.base.net.entity.CommonResponse;
import com.chat.rtc.entity.RtcCallResp;
import com.chat.rtc.entity.RtcChannelStateResp;

import java.util.List;

public class RtcModel extends WKBaseModel {
    private static final String TAG = "WKRTC";

    private static class Binder {
        private static final RtcModel MODEL = new RtcModel();
    }

    public static RtcModel getInstance() {
        return Binder.MODEL;
    }

    public void startCall(String requestId, String channelId, byte channelType, String callType,
                          String deviceId, List<String> inviteUIDs,
                          IRequestResultListener<RtcCallResp> listener) {
        JSONObject body = new JSONObject();
        body.put("request_id", requestId);
        body.put("channel_id", channelId);
        body.put("channel_type", channelType);
        body.put("call_type", callType);
        body.put("device_id", deviceId);
        JSONArray inviteArray = new JSONArray();
        if (inviteUIDs != null) {
            inviteArray.addAll(inviteUIDs);
        }
        body.put("invite_uids", inviteArray);
        Log.i(TAG, "POST rtc/calls body=" + body.toJSONString());
        request(createService(RtcApiService.class).startCall(body), listener);
    }

    public void joinCall(String callId, String deviceId, String joinCode,
                         IRequestResultListener<RtcCallResp> listener) {
        JSONObject body = new JSONObject();
        body.put("device_id", deviceId);
        body.put("join_code", joinCode == null ? "" : joinCode);
        request(createService(RtcApiService.class).joinCall(callId, body), listener);
    }

    public void rejectCall(String callId, String deviceId, IRequestResultListener<CommonResponse> listener) {
        request(createService(RtcApiService.class).rejectCall(callId, deviceId), listener);
    }

    public void cancelCall(String callId, String deviceId, IRequestResultListener<CommonResponse> listener) {
        request(createService(RtcApiService.class).cancelCall(callId, deviceId), listener);
    }

    public void closeCall(String callId, String deviceId, IRequestResultListener<CommonResponse> listener) {
        closeCall(callId, deviceId, "hangup", listener);
    }

    public void closeCall(String callId, String deviceId, String reason, IRequestResultListener<CommonResponse> listener) {
        JSONObject body = new JSONObject();
        body.put("reason", reason);
        request(createService(RtcApiService.class).closeCall(callId, deviceId, body), listener);
    }

    public void channelState(byte channelType, String channelId,
                             IRequestResultListener<RtcChannelStateResp> listener) {
        request(createService(RtcApiService.class).channelState(channelType, channelId), listener);
    }
}
