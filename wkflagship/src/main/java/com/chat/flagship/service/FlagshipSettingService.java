package com.chat.flagship.service;

import com.alibaba.fastjson.JSONObject;
import com.chat.base.net.entity.CommonResponse;

import io.reactivex.rxjava3.core.Observable;
import retrofit2.http.Body;
import retrofit2.http.PUT;
import retrofit2.http.Path;

public interface FlagshipSettingService {

    @PUT("users/{uid}/setting")
    Observable<CommonResponse> updateUserSetting(@Path("uid") String uid, @Body JSONObject jsonObject);

    @PUT("groups/{groupNo}/setting")
    Observable<CommonResponse> updateGroupSetting(@Path("groupNo") String groupNo, @Body JSONObject jsonObject);
}
