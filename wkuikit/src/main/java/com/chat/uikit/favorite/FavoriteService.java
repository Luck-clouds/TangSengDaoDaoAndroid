package com.chat.uikit.favorite;

import com.alibaba.fastjson.JSONObject;
import com.chat.base.net.entity.CommonResponse;

import io.reactivex.rxjava3.core.Observable;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.HTTP;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface FavoriteService {
    @POST("message/favorite")
    Observable<JSONObject> favorite(@Body JSONObject jsonObject);

    @HTTP(method = "DELETE", path = "message/favorite", hasBody = true)
    Observable<CommonResponse> deleteFavorite(@Body JSONObject jsonObject);

    @GET("message/favorite/list")
    Observable<JSONObject> favoriteList(@Query("page") int pageIndex, @Query("limit") int pageSize, @Query("type") String type);
}
