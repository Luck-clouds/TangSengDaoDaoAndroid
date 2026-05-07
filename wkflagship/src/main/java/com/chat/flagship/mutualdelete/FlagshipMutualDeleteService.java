package com.chat.flagship.mutualdelete;

import com.alibaba.fastjson.JSONObject;
import com.chat.base.net.entity.CommonResponse;

import io.reactivex.rxjava3.core.Observable;
import retrofit2.http.Body;
import retrofit2.http.HTTP;

/**
 * 双向删除接口定义。
 */
public interface FlagshipMutualDeleteService {

    @HTTP(method = "DELETE", path = "message/mutual", hasBody = true)
    Observable<CommonResponse> mutualDelete(@Body JSONObject jsonObject);
}
