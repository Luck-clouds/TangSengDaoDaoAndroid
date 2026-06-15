package com.chat.base.net.ud;


import com.chat.base.net.entity.UploadResultEntity;

import io.reactivex.rxjava3.core.Observable;
import okhttp3.MultipartBody;
import okhttp3.ResponseBody;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Header;
import retrofit2.http.Part;
import retrofit2.http.Url;

public interface UploadService {
    @Multipart
    @POST
    Observable<UploadResultEntity> upload(@Url String url, @Part MultipartBody.Part file);

    @Multipart
    @POST
    Observable<UploadResultEntity> uploadWithToken(@Url String url, @Header("token") String token, @Part MultipartBody.Part file);

    @Multipart
    @PUT
    Observable<UploadResultEntity> uploadPut(@Url String url, @Part MultipartBody.Part file);

    @Multipart
    @PUT
    Observable<UploadResultEntity> uploadPutWithToken(@Url String url, @Header("token") String token, @Part MultipartBody.Part file);

    @Multipart
    @POST
    Observable<ResponseBody> uploadRawWithToken(@Url String url, @Header("token") String token, @Part MultipartBody.Part file);
}
