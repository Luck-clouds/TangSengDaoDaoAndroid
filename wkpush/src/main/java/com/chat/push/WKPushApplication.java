package com.chat.push;

import android.app.Activity;
import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import com.chat.base.WKBaseApplication;
import com.chat.base.config.WKConfig;
import com.chat.base.config.WKConstants;
import com.chat.base.endpoint.EndpointCategory;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.endpoint.entity.LoginMenu;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.ui.Theme;
import com.chat.base.utils.WKDialogUtils;
import com.chat.base.utils.systembar.WKOSUtils;
import com.chat.push.service.PushModel;
import com.heytap.msp.push.HeytapPushManager;
import com.heytap.msp.push.callback.ICallBackResultService;
import com.hihonor.push.sdk.HonorPushCallback;
import com.hihonor.push.sdk.HonorPushClient;
import com.vivo.push.PushClient;
import com.vivo.push.util.VivoPushException;
import com.xiaomi.mipush.sdk.MiPushClient;

import java.lang.ref.WeakReference;

/**
 * 2020-03-08 22:29
 * 推送管理
 */
public class WKPushApplication {
    private WKPushApplication() {
    }

    private static class PushApplicationBinder {
        static final WKPushApplication push = new WKPushApplication();
    }

    private WeakReference<Context> mContext;
    public String pushBundleID;
    private boolean listenerAdded = false;

    public static WKPushApplication getInstance() {
        return PushApplicationBinder.push;
    }


    //初始化推送服务
    public void init(String pushBundleID, final Context context) {
        this.pushBundleID = pushBundleID;
        this.mContext = new WeakReference<>(context);
        addListener();
        initPush();
        EndpointManager.getInstance().setMethod("", EndpointCategory.loginMenus, object -> new LoginMenu(this::initPush));
    }

    public void registerNotificationDialog(String pushBundleID, final Context context) {
        this.pushBundleID = pushBundleID;
        this.mContext = new WeakReference<>(context);
        addListener();
    }

    private void initPush() {
        if (mContext == null || mContext.get() == null) return;
        notifyChannel(WKBaseApplication.getInstance().application);
        getPushToken();
    }

    private void initHonorPush(Context context) {
        HonorPushClient pushClient = HonorPushClient.getInstance();
        if (!pushClient.checkSupportHonorPush(context)) {
            return;
        }
        pushClient.init(context, false);
        pushClient.getPushToken(new HonorPushCallback<String>() {
            @Override
            public void onSuccess(String token) {
                if (!TextUtils.isEmpty(token)) {
                    Log.e("获取荣耀push", token);
                    PushModel.getInstance().registerDeviceToken(token, pushBundleID, "");
                }
            }

            @Override
            public void onFailure(int code, String errorMessage) {
                Log.e("获取荣耀push失败", code + ":" + errorMessage);
            }
        });
    }
    private void initXiaoMiPush(Context context) {
        String regId = MiPushClient.getRegId(context);
        if (!TextUtils.isEmpty(regId)) {
            // SDK 已注册时复用现有 RegID，只补传当前账号，避免重复 register
            // 触发不必要的服务启动、网络重连和 token 刷新。
            PushModel.getInstance().registerDeviceToken(regId, pushBundleID, "MI");
            return;
        }
        MiPushClient.registerPush(context, PushKeys.xiaoMiAppID, PushKeys.xiaoMiAppKey);
    }

    private void initOPPO() {
        HeytapPushManager.init(mContext.get(), true);
        new Thread(() -> HeytapPushManager.register(mContext.get(), PushKeys.oppoAppKey, PushKeys.oppoAppSecret, new ICallBackResultService() {
            @Override
            public void onRegister(int i, String s) {
                if (i == 0) {
                    // 注册成功
                    Log.e("tu推送ID", HeytapPushManager.getRegisterID());
                    PushModel.getInstance().registerDeviceToken(s, WKPushApplication.getInstance().pushBundleID,"");
                }
            }

            @Override
            public void onUnRegister(int i) {

            }

            @Override
            public void onSetPushTime(int i, String s) {

            }

            @Override
            public void onGetPushStatus(int i, int i1) {

            }

            @Override
            public void onGetNotificationStatus(int i, int i1) {
            }

            @Override
            public void onError(int i, String s) {

            }
        })).start();

    }

    private void initVIVO() {
        try {
            PushClient.getInstance(mContext.get()).initialize();
            PushClient.getInstance(mContext.get()).turnOnPush(state -> {
                // TODO: 开关状态处理， 0代表成功
                String regId = PushClient.getInstance(mContext.get()).getRegId();
                if (!TextUtils.isEmpty(regId)) {
                    Log.e("获取vivopush", regId);
                    PushModel.getInstance().registerDeviceToken(regId, pushBundleID,"");
                }
            });

        } catch (VivoPushException e) {
            e.printStackTrace();
        }
    }

    private void addListener() {
        if (listenerAdded) {
            return;
        }
        listenerAdded = true;
        EndpointManager.getInstance().setMethod("show_open_notification_dialog", object -> {
            Context context = (Context) object;
            WKDialogUtils.getInstance().showDialog(context, context.getString(R.string.open_notification_title), context.getString(R.string.open_notification_content), true, "", context.getString(R.string.open_setting), 0, Theme.colorAccount, index -> {
                if (index == 1) {
                    WKOSUtils.openChannelSetting(context, WKConstants.newMsgChannelID);
                }
            });
            return null;
        });
        //注销推送
        EndpointManager.getInstance().setMethod("wk_logout", object -> {
            OsUtils.setBadge(WKBaseApplication.getInstance().getContext(), 0);
            // 后端解绑使用退出前快照的登录 token；失败不阻塞正常退出。
            PushModel.getInstance().unRegisterDeviceToken((code, msg) -> {
                if (code != HttpResponseCode.success) {
                    Log.w("注销push", code + ":" + msg);
                }
            });
            Context context = mContext == null ? null : mContext.get();
            if (context != null && OsUtils.isMiui()) {
                // 停止小米本地推送注册及其心跳/重连；下次登录会重新 registerPush。
                MiPushClient.unregisterPush(context);
            }
            return null;
        });

        //设置桌面红点数量
        EndpointManager.getInstance().setMethod("push_update_device_badge", object -> {
            int num = (int) object;
            PushModel.getInstance().registerBadge(num);
            OsUtils.setBadge(WKBaseApplication.getInstance().getContext(), num);
            return null;
        });
    }

    private void getPushToken() {
        if (TextUtils.isEmpty(WKConfig.getInstance().getUid())) {
            return;
        }
        if (HonorPushClient.getInstance().checkSupportHonorPush(mContext.get())) {
            initHonorPush(mContext.get());
        } else if (OsUtils.isMiui()) {
            initXiaoMiPush(mContext.get());
        } else if (OsUtils.isOppo()) {
            initOPPO();
        } else if (OsUtils.isVivo()) {
            initVIVO();
        }
    }

    private static void notifyChannel(Application context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String channelId = WKConstants.newMsgChannelID;
            String channelName = "Default_Channel";
            String channelDescription = "this is default channel!";
            NotificationChannel mNotificationChannel = new NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_DEFAULT);
            mNotificationChannel.setDescription(channelDescription);
            ((NotificationManager) context.getSystemService(Activity.NOTIFICATION_SERVICE)).createNotificationChannel(mNotificationChannel);
        }
    }
}

