package com.chat.push.push;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import com.chat.push.WKPushApplication;
import com.huawei.hms.push.HmsMessageService;
import com.huawei.hms.push.RemoteMessage;

/**
 * 2020-03-08 22:10
 * 华为推送服务
 */
public class HuaweiHmsMessageService extends HmsMessageService {
    @Override
    public void onNewToken(String s, Bundle bundle) {
        super.onNewToken(s, bundle);
        if (!TextUtils.isEmpty(s)) {
            WKPushApplication.getInstance().onHuaweiToken(s);
        }
    }

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        Log.d("HuaweiPush", "Received Huawei data message");
    }
}
