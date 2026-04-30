package com.chat.flagship.chatbg;

import java.util.List;

import io.reactivex.rxjava3.core.Observable;
import retrofit2.http.GET;

/**
 * 聊天背景接口
 * Created by Luckclouds and chatGPT.
 */
public interface FlagshipChatBgService {

    @GET("common/chatbg")
    Observable<List<FlagshipChatBgItem>> getChatBgList();
}
