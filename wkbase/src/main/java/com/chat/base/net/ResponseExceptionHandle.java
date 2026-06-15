package com.chat.base.net;

import android.text.TextUtils;
import android.util.Log;

import com.chat.base.utils.WKLogUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

import retrofit2.HttpException;

/**
 * 2020-07-20 14:23
 * 请求异常处理
 */
public class ResponseExceptionHandle {
    private ResponseExceptionHandle() {
    }

    private static class ResponseExceptionHandleBinder {
        final static ResponseExceptionHandle response = new ResponseExceptionHandle();
    }

    public static ResponseExceptionHandle getInstance() {
        return ResponseExceptionHandleBinder.response;
    }

    public ResponseThrowable handleException(Throwable e) {
        ResponseThrowable responseThrowable;
        if (e instanceof HttpException) {
            HttpException httpException = (HttpException) e;
            responseThrowable = new ResponseThrowable(e, httpException.code());
            switch (httpException.code()) {
                case 400:
                    try {
                        String errorStr = Objects.requireNonNull(Objects.requireNonNull(httpException.response()).errorBody()).string();
                        if (!TextUtils.isEmpty(errorStr)) {
                            try {
                                Log.e("请求错误信息", errorStr);
                                JSONObject jsonObject = new JSONObject(errorStr);
                                int status = jsonObject.optInt("status");
                                String msg = jsonObject.optString("msg");
                                responseThrowable.setMessage(msg);
                                responseThrowable.setErrJson(errorStr);
                                responseThrowable.setStatus(status);
                                Log.e("请求错误", status + "|" + msg);
                            } catch (JSONException ex) {
                                WKLogUtils.e("解析请求【400】不是json结构");
                            }
                        } else {
                            responseThrowable.setMessage("");
                            responseThrowable.setStatus(400);
                        }
                    } catch (IOException ex) {
                        WKLogUtils.e("解析请求【400】结果错误");
                    }
                    break;
                case 401:
                    responseThrowable.setMessage("认证失败");
                    break;
                case 404:
                    responseThrowable.setMessage("请求地址不存在");
                    break;
                case 504:
                    responseThrowable.setMessage("网络连接失败");
                    break;
                default:
                    responseThrowable.setMessage("请求失败");
                    break;
            }
        } else if (e instanceof TimeoutException || e instanceof SocketTimeoutException) {
            responseThrowable = new ResponseThrowable(e, 504);
            responseThrowable.setMessage("请求超时，请检查服务器地址或网络连接");
        } else if (e instanceof UnknownHostException) {
            responseThrowable = new ResponseThrowable(e, 504);
            responseThrowable.setMessage("无法解析服务器地址，请检查服务器地址");
        } else if (e instanceof ConnectException) {
            responseThrowable = new ResponseThrowable(e, 504);
            responseThrowable.setMessage("无法连接服务器，请确认手机和服务器在同一网络");
        } else if (e instanceof IOException) {
            responseThrowable = new ResponseThrowable(e, 504);
            responseThrowable.setMessage("网络连接失败，请检查网络");
        } else if (e instanceof RuntimeException) {
            Log.e("服务器运行时错误", e.getClass().getName() + ": " + e.getMessage(), e);
            responseThrowable = new ResponseThrowable(e, 500);
            responseThrowable.setMessage("请求处理异常");
        } else {
            responseThrowable = new ResponseThrowable(e, 500);
            responseThrowable.setMessage("未知错误");
        }
        return responseThrowable;
    }
}
