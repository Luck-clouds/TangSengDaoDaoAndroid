package com.chat.push.push;

import android.text.TextUtils;
import android.util.Log;

import com.chat.push.WKPushApplication;
import com.chat.push.service.PushModel;
import com.hihonor.push.sdk.HonorMessageService;
import com.hihonor.push.sdk.HonorPushDataMsg;

public class HonorPushMessageService extends HonorMessageService {
    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);
        if (!TextUtils.isEmpty(token)) {
            PushModel.getInstance().registerDeviceToken(
                    token,
                    WKPushApplication.getInstance().pushBundleID,
                    PushModel.DEVICE_TYPE_HONOR
            );
        }
    }

    @Override
    public void onMessageReceived(HonorPushDataMsg message) {
        super.onMessageReceived(message);
        if (message != null) {
            Log.e("收到荣耀透传消息", message.getData());
        }
    }
}
