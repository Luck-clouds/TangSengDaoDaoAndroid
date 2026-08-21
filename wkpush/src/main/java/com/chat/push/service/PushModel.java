package com.chat.push.service;

import android.text.TextUtils;
import android.util.Log;

import com.alibaba.fastjson.JSONObject;
import com.chat.base.base.WKBaseModel;
import com.chat.base.config.WKConfig;
import com.chat.base.config.WKConstants;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.net.ICommonListener;
import com.chat.base.net.IRequestResultListener;
import com.chat.base.net.entity.CommonResponse;
import com.chat.push.debug.PushDebugLogger;

/**
 * 2020-03-08 22:28
 * 推送管理w
 */
public class PushModel extends WKBaseModel {
    private static final String HUAWEI_DEVICE_TYPE = "HMS";

    private PushModel() {

    }

    private static class PushModelBinder {
        static final PushModel pushModel = new PushModel();
    }

    public static PushModel getInstance() {
        return PushModelBinder.pushModel;
    }

    /**
     * 注册设备推送token
     *
     * @param token     token
     * @param bundle_id Android为包名称
     */
    public void registerDeviceToken(String token, String bundle_id) {
        if (!WKConstants.isLogin()) {
            PushDebugLogger.warn("业务 Token 上报取消：登录状态已失效");
            return;
        }

        // HUAWEI 分支只使用华为推送，不读取设备品牌，也不接受调用方覆盖厂商类型。
        JSONObject httpParams = new JSONObject();
        httpParams.put("device_token", token);
        httpParams.put("device_type", HUAWEI_DEVICE_TYPE);
        httpParams.put("bundle_id", bundle_id);
        PushDebugLogger.info("调用 user/device_token：token="
                + PushDebugLogger.maskToken(token) + "，device_type=" + HUAWEI_DEVICE_TYPE
                + "，bundle_id=" + bundle_id);
        request(createService(PushService.class).registerAppToken(httpParams), new IRequestResultListener<CommonResponse>() {
            @Override
            public void onSuccess(CommonResponse result) {
                Log.e("注册push", result.status + "");
                PushDebugLogger.info("user/device_token 响应成功：status=" + result.status
                        + "，msg=" + result.msg);
            }

            @Override
            public void onFail(int code, String msg) {
                PushDebugLogger.warn("user/device_token 请求失败：code=" + code + "，msg=" + msg);
            }
        });

    }

    /**
     * 保留旧调用签名以兼容仓库中被 HUAWEI 分支排除编译的其他厂商回调。
     * 传入的厂商类型不会参与上报。
     */
    public void registerDeviceToken(String token, String bundle_id, String ignoredDeviceType) {
        registerDeviceToken(token, bundle_id);
    }

    /**
     * 注销推送token
     */
    public void unRegisterDeviceToken(final ICommonListener iCommonListener) {
        // 退出流程会立即清空 WKConfig。先快照认证 token，并通过显式 Header
        // 传给异步请求，避免拦截器稍后读取到空 token 导致注销失败。
        String token = WKConfig.getInstance().getToken();
        if (TextUtils.isEmpty(token)) {
            PushDebugLogger.info("解绑 Token 跳过：登录 Token 已为空");
            iCommonListener.onResult(HttpResponseCode.success, "");
            return;
        }
        request(createService(PushService.class).unRegisterAppToken(token), new IRequestResultListener<CommonResponse>() {
            @Override
            public void onSuccess(CommonResponse result) {
                PushDebugLogger.info("解绑推送 Token 响应：status=" + result.status
                        + "，msg=" + result.msg);
                iCommonListener.onResult(result.status, result.msg);
            }

            @Override
            public void onFail(int code, String msg) {
                PushDebugLogger.warn("解绑推送 Token 请求失败：code=" + code + "，msg=" + msg);
                iCommonListener.onResult(code, msg);
            }
        });
    }

    /**
     * 注册红点数量
     *
     * @param badge 数量
     */
    public void registerBadge(int badge) {
        if (TextUtils.isEmpty(WKConfig.getInstance().getToken())) return;
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("badge", badge);
        request(createService(PushService.class).registerBadge(jsonObject), new IRequestResultListener<CommonResponse>() {
            @Override
            public void onSuccess(CommonResponse result) {
            }

            @Override
            public void onFail(int code, String msg) {
            }
        });
    }
}
