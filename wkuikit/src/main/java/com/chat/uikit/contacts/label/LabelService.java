package com.chat.uikit.contacts.label;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.chat.base.net.entity.CommonResponse;

import io.reactivex.rxjava3.core.Observable;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface LabelService {
    @GET("friend/tags")
    Observable<JSONArray> getTags();

    @GET("friend/tags/full")
    Observable<JSONObject> getTagsFull();

    @POST("friend/tags")
    Observable<JSONObject> createTag(@Body JSONObject jsonObject);

    @POST("friend/tags/create_with_contacts")
    Observable<JSONObject> createTagWithContacts(@Body JSONObject jsonObject);

    @PUT("friend/tags/{id}")
    Observable<JSONObject> updateTag(@Path("id") String id, @Body JSONObject jsonObject);

    @POST("friend/tags/{id}/contacts")
    Observable<CommonResponse> addContacts(@Path("id") String id, @Body JSONObject jsonObject);

    @DELETE("friend/tags/{id}/contacts/{to_uid}")
    Observable<CommonResponse> removeContact(@Path("id") String id, @Path("to_uid") String toUid);

    @DELETE("friend/tags/{id}")
    Observable<CommonResponse> deleteTag(@Path("id") String id);

    @GET("friend/tags/sync")
    Observable<JSONArray> syncTags(@Query("version") long version, @Query("limit") int limit);

    @GET("friend/tag-relations/sync")
    Observable<JSONArray> syncTagRelations(@Query("version") long version, @Query("limit") int limit);
}
