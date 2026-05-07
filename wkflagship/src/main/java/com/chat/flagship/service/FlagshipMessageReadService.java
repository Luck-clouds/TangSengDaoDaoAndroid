package com.chat.flagship.service;

import com.alibaba.fastjson.JSONObject;
import com.chat.base.net.entity.CommonResponse;

import io.reactivex.rxjava3.core.Observable;
import retrofit2.http.Body;
import retrofit2.http.POST;

/**
 * 消息已读上报接口
 * Created by Luckclouds .
 */
public interface FlagshipMessageReadService {
    @POST("message/readed")
    Observable<CommonResponse> messageReaded(@Body JSONObject jsonObject);
}
