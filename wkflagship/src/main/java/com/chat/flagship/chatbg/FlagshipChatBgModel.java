package com.chat.flagship.chatbg;

import com.chat.base.base.WKBaseModel;
import com.chat.base.net.IRequestResultListener;

import java.util.List;

/**
 * 聊天背景数据模型
 * Created by Luckclouds and chatGPT.
 */
public class FlagshipChatBgModel extends WKBaseModel {

    private FlagshipChatBgModel() {
    }

    private static class Binder {
        private static final FlagshipChatBgModel INSTANCE = new FlagshipChatBgModel();
    }

    public static FlagshipChatBgModel getInstance() {
        return Binder.INSTANCE;
    }

    public void getChatBgList(IRequestResultListener<List<FlagshipChatBgItem>> listener) {
        request(createService(FlagshipChatBgService.class).getChatBgList(), listener);
    }
}
