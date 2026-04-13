package com.chat.flagship.service;

import com.alibaba.fastjson.JSONObject;
import com.chat.base.base.WKBaseModel;
import com.chat.base.net.IRequestResultListener;
import com.chat.base.net.entity.CommonResponse;
import com.xinbida.wukongim.entity.WKChannelType;

public class FlagshipSettingModel extends WKBaseModel {

    private FlagshipSettingModel() {
    }

    private static class Binder {
        private static final FlagshipSettingModel INSTANCE = new FlagshipSettingModel();
    }

    public static FlagshipSettingModel getInstance() {
        return Binder.INSTANCE;
    }

    public void updateSetting(String channelId, byte channelType, String key, int value, IUpdateSettingListener listener) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put(key, value);
        if (channelType == WKChannelType.PERSONAL) {
            request(createService(FlagshipSettingService.class).updateUserSetting(channelId, jsonObject), new IRequestResultListener<CommonResponse>() {
                @Override
                public void onSuccess(CommonResponse result) {
                    if (listener != null) {
                        listener.onResult(result.status, result.msg);
                    }
                }

                @Override
                public void onFail(int code, String msg) {
                    if (listener != null) {
                        listener.onResult(code, msg);
                    }
                }
            });
            return;
        }
        if (channelType == WKChannelType.GROUP) {
            request(createService(FlagshipSettingService.class).updateGroupSetting(channelId, jsonObject), new IRequestResultListener<CommonResponse>() {
                @Override
                public void onSuccess(CommonResponse result) {
                    if (listener != null) {
                        listener.onResult(result.status, result.msg);
                    }
                }

                @Override
                public void onFail(int code, String msg) {
                    if (listener != null) {
                        listener.onResult(code, msg);
                    }
                }
            });
            return;
        }
        if (listener != null) {
            listener.onResult(-1, "unsupported channel type");
        }
    }

    public interface IUpdateSettingListener {
        void onResult(int code, String msg);
    }
}
