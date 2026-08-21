package com.chat.push.push;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import com.chat.push.WKPushApplication;
import com.chat.push.debug.PushDebugLogger;
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
        PushDebugLogger.init(getApplicationContext());
        PushDebugLogger.info("HmsMessageService.onNewToken：token="
                + PushDebugLogger.maskToken(s) + "，bundle=" + (bundle != null));
        if (!TextUtils.isEmpty(s)) {
            WKPushApplication.getInstance().onHuaweiToken(s);
        }
    }

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        Log.d("HuaweiPush", "Received Huawei data message");
        PushDebugLogger.init(getApplicationContext());
        if (remoteMessage == null) {
            PushDebugLogger.warn("onMessageReceived 收到空消息");
            return;
        }
        String data = remoteMessage.getData();
        PushDebugLogger.info("收到华为透传消息：messageId=" + remoteMessage.getMessageId()
                + "，from=" + remoteMessage.getFrom()
                + "，dataLength=" + (data == null ? 0 : data.length())
                + "，ttl=" + remoteMessage.getTtl());
    }

    @Override
    public void onDeletedMessages() {
        super.onDeletedMessages();
        PushDebugLogger.init(getApplicationContext());
        PushDebugLogger.warn("华为服务通知消息被删除：onDeletedMessages");
    }
}
