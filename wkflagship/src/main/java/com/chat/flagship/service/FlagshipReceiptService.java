package com.chat.flagship.service;

import com.chat.flagship.entity.FlagshipReceiptUser;

import java.util.List;

import io.reactivex.rxjava3.core.Observable;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * 消息回执接口
 * Created by Luckclouds .
 */
public interface FlagshipReceiptService {
    @GET("messages/{messageId}/receipt")
    Observable<List<FlagshipReceiptUser>> getReceiptUsers(@Path("messageId") String messageId, @Query("readed") int readed);
}
