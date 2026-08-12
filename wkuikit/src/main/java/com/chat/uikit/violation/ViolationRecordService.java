package com.chat.uikit.violation;

import com.alibaba.fastjson.JSONObject;

import io.reactivex.rxjava3.core.Observable;
import retrofit2.http.GET;
import retrofit2.http.Query;

interface ViolationRecordService {
    @GET("user/records")
    Observable<JSONObject> getRecords(@Query("type") String type,
                                      @Query("page") int page,
                                      @Query("limit") int limit);
}
