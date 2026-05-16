package com.chat.flagship.mutualdelete;

import android.text.TextUtils;

import com.alibaba.fastjson.JSONObject;
import com.chat.base.base.WKBaseModel;
import com.chat.base.net.ICommonListener;
import com.chat.base.net.IRequestResultListener;
import com.chat.base.net.entity.CommonResponse;
import com.xinbida.wukongim.entity.WKMsg;

import java.util.ArrayList;
import java.util.List;

/**
 * 双向删除业务模型。
 */
public class FlagshipMutualDeleteModel extends WKBaseModel {

    private FlagshipMutualDeleteModel() {
    }

    private static class Binder {
        private static final FlagshipMutualDeleteModel INSTANCE = new FlagshipMutualDeleteModel();
    }

    public static FlagshipMutualDeleteModel getInstance() {
        return Binder.INSTANCE;
    }

    public void mutualDelete(WKMsg msg, ICommonListener listener) {
        if (msg == null || TextUtils.isEmpty(msg.channelID) || TextUtils.isEmpty(msg.messageID) || msg.messageSeq <= 0) {
            if (listener != null) {
                listener.onResult(-1, "");
            }
            return;
        }
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("channel_id", msg.channelID);
        jsonObject.put("channel_type", msg.channelType);
        jsonObject.put("message_id", msg.messageID);
        jsonObject.put("message_seq", msg.messageSeq);
        request(createService(FlagshipMutualDeleteService.class).mutualDelete(jsonObject), new IRequestResultListener<CommonResponse>() {
            @Override
            public void onSuccess(CommonResponse result) {
                if (listener != null) {
                    listener.onResult(result == null ? 0 : result.status, result == null ? "" : result.msg);
                }
            }

            @Override
            public void onFail(int code, String msgText) {
                if (listener != null) {
                    listener.onResult(code, msgText);
                }
            }
        });
    }

    public void batchMutualDelete(List<WKMsg> msgList, ICommonListener listener) {
        if (msgList == null || msgList.isEmpty()) {
            if (listener != null) {
                listener.onResult(-1, "");
            }
            return;
        }
        List<FlagshipMutualDeleteRequest> requestList = new ArrayList<>();
        for (WKMsg msg : msgList) {
            if (msg == null || TextUtils.isEmpty(msg.channelID) || TextUtils.isEmpty(msg.messageID) || msg.messageSeq <= 0) {
                if (listener != null) {
                    listener.onResult(-1, "");
                }
                return;
            }
            FlagshipMutualDeleteRequest request = new FlagshipMutualDeleteRequest();
            request.channel_id = msg.channelID;
            request.channel_type = msg.channelType;
            request.message_id = msg.messageID;
            request.message_seq = msg.messageSeq;
            requestList.add(request);
        }
        request(createService(FlagshipMutualDeleteService.class).batchMutualDelete(requestList), new IRequestResultListener<CommonResponse>() {
            @Override
            public void onSuccess(CommonResponse result) {
                if (listener != null) {
                    listener.onResult(result == null ? 0 : result.status, result == null ? "" : result.msg);
                }
            }

            @Override
            public void onFail(int code, String msgText) {
                if (listener != null) {
                    listener.onResult(code, msgText);
                }
            }
        });
    }
}
