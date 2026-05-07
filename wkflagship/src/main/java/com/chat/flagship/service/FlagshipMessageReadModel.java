package com.chat.flagship.service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.chat.base.base.WKBaseModel;
import com.chat.base.net.IRequestResultListener;
import com.chat.base.net.entity.CommonResponse;

import java.util.List;

/**
 * 消息已读上报
 * Created by Luckclouds .
 */
public class FlagshipMessageReadModel extends WKBaseModel {

    private FlagshipMessageReadModel() {
    }

    private static class Binder {
        private static final FlagshipMessageReadModel INSTANCE = new FlagshipMessageReadModel();
    }

    public static FlagshipMessageReadModel getInstance() {
        return Binder.INSTANCE;
    }

    public void markRead(String channelId, byte channelType, List<String> msgIds) {
        if (msgIds == null || msgIds.isEmpty()) {
            return;
        }
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("channel_id", channelId);
        jsonObject.put("channel_type", channelType);
        JSONArray array = new JSONArray();
        array.addAll(msgIds);
        jsonObject.put("message_ids", array);
        request(createService(FlagshipMessageReadService.class).messageReaded(jsonObject), new IRequestResultListener<CommonResponse>() {
            @Override
            public void onSuccess(CommonResponse result) {
            }

            @Override
            public void onFail(int code, String msg) {
            }
        });
    }
}
