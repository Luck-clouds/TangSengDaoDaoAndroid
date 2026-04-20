package com.chat.sticker.service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.chat.base.net.entity.CommonResponse;

import io.reactivex.rxjava3.core.Observable;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.HTTP;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.PUT;
import retrofit2.http.Query;

/**
 * 表情商店接口
 * Created by Luckclouds .
 */
public interface StickerService {
    @GET("sticker/panel")
    Observable<JSONObject> panel();

    @GET("sticker/emoji")
    Observable<JSONArray> emojiList(@Query("keyword") String keyword, @Query("group_no") String groupNo);

    @GET("sticker/favorites")
    Observable<JSONArray> favorites();

    @POST("sticker/favorites")
    Observable<CommonResponse> addFavorite(@Body JSONObject body);

    @HTTP(method = "DELETE", path = "sticker/favorites", hasBody = true)
    Observable<CommonResponse> removeFavorite(@Body JSONObject body);

    @GET("sticker/custom")
    Observable<JSONArray> customList();

    @POST("sticker/custom")
    Observable<JSONObject> createCustom(@Body JSONObject body);

    @HTTP(method = "DELETE", path = "sticker/custom", hasBody = true)
    Observable<CommonResponse> deleteCustom(@Body JSONObject body);

    @PUT("sticker/custom/reorder")
    Observable<CommonResponse> reorderCustom(@Body JSONObject body);

    @GET("sticker/my/packages")
    Observable<JSONArray> myPackages();

    @PUT("sticker/my/packages/reorder")
    Observable<CommonResponse> reorderMyPackages(@Body JSONObject body);

    @GET("sticker/store/packages")
    Observable<JSONObject> storePackages(@Query("keyword") String keyword, @Query("page_index") int pageIndex, @Query("page_size") int pageSize);

    @GET("sticker/store/packages/{package_id}")
    Observable<JSONObject> packageDetail(@Path("package_id") String packageId);

    @POST("sticker/store/packages/{package_id}/add")
    Observable<CommonResponse> addMyPackage(@Path("package_id") String packageId);

    @DELETE("sticker/store/packages/{package_id}/add")
    Observable<CommonResponse> removeMyPackage(@Path("package_id") String packageId);
}
