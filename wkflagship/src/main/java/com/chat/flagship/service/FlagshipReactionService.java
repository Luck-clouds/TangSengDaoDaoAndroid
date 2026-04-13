package com.chat.flagship.service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.chat.base.net.entity.CommonResponse;

import io.reactivex.rxjava3.core.Observable;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface FlagshipReactionService {

    @POST("reactions")
    Observable<CommonResponse> toggleReaction(@Body JSONObject jsonObject);

    @POST("reaction/sync")
    Observable<JSONArray> syncReaction(@Body JSONObject jsonObject);
}
