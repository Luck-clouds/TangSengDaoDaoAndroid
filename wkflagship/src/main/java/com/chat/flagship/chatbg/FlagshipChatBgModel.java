package com.chat.flagship.chatbg;

import com.chat.base.base.WKBaseModel;
import com.chat.base.net.IRequestResultListener;
import android.util.Log;

import java.util.List;

/**
 * 聊天背景数据模型
 * Created by Luckclouds .
 */
public class FlagshipChatBgModel extends WKBaseModel {
    private static final String TAG = "FlagshipChatBg";

    private FlagshipChatBgModel() {
    }

    private static class Binder {
        private static final FlagshipChatBgModel INSTANCE = new FlagshipChatBgModel();
    }

    public static FlagshipChatBgModel getInstance() {
        return Binder.INSTANCE;
    }

    public void getChatBgList(IRequestResultListener<List<FlagshipChatBgItem>> listener) {
        Log.d(TAG, "request chat background list");
        request(createService(FlagshipChatBgService.class).getChatBgList(), listener);
    }
}
