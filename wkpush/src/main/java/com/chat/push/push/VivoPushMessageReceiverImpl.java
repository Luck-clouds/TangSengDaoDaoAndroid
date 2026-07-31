package com.chat.push.push;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.chat.push.WKPushApplication;
import com.chat.push.service.PushModel;
import com.vivo.push.model.UPSNotificationMessage;
import com.vivo.push.sdk.OpenClientPushMessageReceiver;

public class VivoPushMessageReceiverImpl extends OpenClientPushMessageReceiver {

    @Override
    public void onReceiveRegId(Context context, String regId) {
        super.onReceiveRegId(context, regId);
        if (!TextUtils.isEmpty(regId)) {
            Log.i("vivo推送", "收到新的RegId");
            PushModel.getInstance().registerDeviceToken(
                    regId,
                    WKPushApplication.getInstance().pushBundleID,
                    PushModel.DEVICE_TYPE_VIVO
            );
        }
    }

    @Override
    public void onNotificationMessageClicked(Context context, UPSNotificationMessage msg) {
        super.onNotificationMessageClicked(context, msg);
    }
}
