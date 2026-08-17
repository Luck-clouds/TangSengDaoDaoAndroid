package com.chat.rtc.net;

import com.alibaba.fastjson.JSONObject;
import com.chat.base.net.entity.CommonResponse;
import com.chat.rtc.entity.RtcCallResp;
import com.chat.rtc.entity.RtcChannelStateResp;

import io.reactivex.rxjava3.core.Observable;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface RtcApiService {
    @POST("rtc/calls")
    Observable<RtcCallResp> startCall(@Body JSONObject body);

    @POST("rtc/calls/{call_id}/join")
    Observable<RtcCallResp> joinCall(@Path("call_id") String callId, @Body JSONObject body);

    @POST("rtc/calls/{call_id}/reject")
    Observable<CommonResponse> rejectCall(@Path("call_id") String callId,
                                          @Header("device_id") String deviceId);

    @POST("rtc/calls/{call_id}/cancel")
    Observable<CommonResponse> cancelCall(@Path("call_id") String callId,
                                          @Header("device_id") String deviceId);

    @POST("rtc/calls/{call_id}/close")
    Observable<CommonResponse> closeCall(@Path("call_id") String callId,
                                         @Header("device_id") String deviceId,
                                         @Body JSONObject body);

    @POST("rtc/calls/{call_id}/leave")
    Observable<CommonResponse> leaveCall(@Path("call_id") String callId,
                                         @Header("device_id") String deviceId);

    @POST("rtc/calls/{call_id}/invite")
    Observable<CommonResponse> inviteMembers(@Path("call_id") String callId,
                                             @Header("device_id") String deviceId,
                                             @Body JSONObject body);

    @GET("rtc/channels/{channel_type}/{channel_id}/state")
    Observable<RtcChannelStateResp> channelState(
            @Path("channel_type") byte channelType,
            @Path("channel_id") String channelId
    );
}
