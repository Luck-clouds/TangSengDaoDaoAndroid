package com.chat.moments.service

/**
 * 朋友圈接口定义
 * Created by Luckclouds.
 */

import com.alibaba.fastjson.JSONArray
import com.alibaba.fastjson.JSONObject
import io.reactivex.rxjava3.core.Observable
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface MomentService {
    @GET("moment/feed")
    fun getFeed(
        @Query("page_index") pageIndex: Int,
        @Query("page_size") pageSize: Int
    ): Observable<JSONObject>

    @GET("moment/feed/{uid}")
    fun getUserFeed(
        @Path("uid") uid: String,
        @Query("page_index") pageIndex: Int,
        @Query("page_size") pageSize: Int
    ): Observable<JSONObject>

    @GET("moment/posts/{post_id}")
    fun getPostDetail(@Path("post_id") postId: String): Observable<JSONObject>

    @POST("moment/publish")
    fun publish(@Body body: JSONObject): Observable<JSONObject>

    @POST("moment/posts/{post_id}/comments")
    fun addComment(@Path("post_id") postId: String, @Body body: JSONObject): Observable<JSONObject>

    @DELETE("moment/posts/{post_id}/comments/{comment_id}")
    fun deleteComment(
        @Path("post_id") postId: String,
        @Path("comment_id") commentId: String
    ): Observable<JSONObject>

    @POST("moment/posts/{post_id}/like")
    fun toggleLike(@Path("post_id") postId: String): Observable<JSONObject>

    @DELETE("moment/posts/{post_id}")
    fun deletePost(@Path("post_id") postId: String): Observable<JSONObject>

    @POST("moment/posts/{post_id}/favorite")
    fun toggleFavorite(
        @Path("post_id") postId: String,
        @Body body: JSONObject
    ): Observable<JSONObject>

    @GET("moment/notices/sync")
    fun syncNotices(
        @Query("version") version: Long,
        @Query("limit") limit: Int
    ): Observable<JSONArray>

    @POST("moment/notices/read")
    fun markNoticesRead(@Body body: JSONObject): Observable<JSONObject>

    @GET("moment/profile/{uid}")
    fun getProfile(@Path("uid") uid: String): Observable<JSONObject>

    @PUT("moment/profile/cover")
    fun updateProfileCover(@Body body: JSONObject): Observable<JSONObject>

    @GET("moment/settings/stranger-visibility")
    fun getStrangerVisibility(): Observable<JSONObject>

    @PUT("moment/settings/stranger-visibility")
    fun updateStrangerVisibility(@Body body: JSONObject): Observable<JSONObject>

    @GET("moment/setting/{uid}")
    fun getMomentUserState(@Path("uid") uid: String): Observable<JSONObject>

    @PUT("moment/setting/hidemy/{uid}/{value}")
    fun setHideMyMoment(
        @Path("uid") uid: String,
        @Path("value") value: Int
    ): Observable<JSONObject>

    @PUT("moment/setting/hidehis/{uid}/{value}")
    fun setHideHisMoment(
        @Path("uid") uid: String,
        @Path("value") value: Int
    ): Observable<JSONObject>
}
