package com.chat.flagship.service;

import com.chat.base.base.WKBaseModel;
import com.chat.base.net.IRequestResultListener;
import com.chat.flagship.entity.FlagshipReceiptUser;

import java.util.List;

/**
 * 消息回执数据模型
 * Created by Luckclouds and chatGPT.
 */
public class FlagshipReceiptModel extends WKBaseModel {

    private FlagshipReceiptModel() {
    }

    private static class Binder {
        private static final FlagshipReceiptModel INSTANCE = new FlagshipReceiptModel();
    }

    public static FlagshipReceiptModel getInstance() {
        return Binder.INSTANCE;
    }

    public void getReceiptUsers(String messageId, int readed, IReceiptUsersListener listener) {
        request(createService(FlagshipReceiptService.class).getReceiptUsers(messageId, readed), new IRequestResultListener<List<FlagshipReceiptUser>>() {
            @Override
            public void onSuccess(List<FlagshipReceiptUser> result) {
                if (listener != null) {
                    listener.onResult(200, "", result);
                }
            }

            @Override
            public void onFail(int code, String msg) {
                if (listener != null) {
                    listener.onResult(code, msg, null);
                }
            }
        });
    }

    public interface IReceiptUsersListener {
        void onResult(int code, String msg, List<FlagshipReceiptUser> list);
    }
}
