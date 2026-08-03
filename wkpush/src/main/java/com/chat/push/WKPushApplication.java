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
import com.vivo.push.PushConfig;
import com.vivo.push.listener.IPushQueryActionListener;
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
    private final ICallBackResultService oppoPushCallback = new ICallBackResultService() {
        @Override
        public void onRegister(int responseCode, String registerId, String packageName, String miniPackageName) {
            if (responseCode == 0 && !TextUtils.isEmpty(registerId)) {
                Log.i("OPPO推送", "注册成功，RegistrationID=" + registerId);
                PushModel.getInstance().registerDeviceToken(
                        registerId,
                        pushBundleID,
                        PushModel.DEVICE_TYPE_OPPO
                );
            } else {
                Log.w("OPPO推送", "注册失败:" + responseCode);
            }
        }

        @Override
        public void onUnRegister(int responseCode, String packageName, String miniPackageName) {
            if (responseCode == 0) {
                Log.i("OPPO推送", "注销成功");
            } else {
                Log.w("OPPO推送", "注销失败:" + responseCode);
            }
            reRegisterAfterLateLogoutIfNeeded();
        }

        @Override
        public void onSetPushTime(int responseCode, String result) {
        }

        @Override
        public void onGetPushStatus(int responseCode, int status) {
        }

        @Override
        public void onGetNotificationStatus(int responseCode, int status) {
        }

        @Override
        public void onError(int errorCode, String message, String packageName, String miniPackageName) {
            Log.w("OPPO推送", "SDK错误:" + errorCode + ":" + message);
        }
    };

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
                    Log.i("荣耀推送", "获取Token成功");
                    PushModel.getInstance().registerDeviceToken(
                            token,
                            pushBundleID,
                            PushModel.DEVICE_TYPE_HONOR
                    );
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
        Context context = getContext();
        if (context == null) return;
        HeytapPushManager.init(context, false);
        if (!HeytapPushManager.isSupportPush(context)) {
            Log.w("OPPO推送", "当前设备不支持OPPO推送");
            return;
        }
        String cachedRegisterId = HeytapPushManager.getRegisterID();
        if (!TextUtils.isEmpty(cachedRegisterId)) {
            // 本地缓存只能说明 SDK 曾注册过，不能证明该 RegId 在 OPPO
            // 平台仍然有效。保留读取用于诊断，但禁止使用缓存值上报。
            Log.i("OPPO推送", "检测到历史RegId，重新注册获取最新Token");
        }
        new Thread(
                () -> HeytapPushManager.register(
                        context,
                        PushKeys.oppoAppKey,
                        PushKeys.oppoAppSecret,
                        oppoPushCallback
                ),
                "oppo-push-register"
        ).start();
    }

    private void initVIVO() {
        Context context = getContext();
        if (context == null) return;
        try {
            PushClient pushClient = PushClient.getInstance(context);
            PushConfig config = new PushConfig.Builder()
                    .agreePrivacyStatement(true)
                    .build();
            pushClient.initialize(config);
            if (!pushClient.isSupport()) {
                Log.w("vivo推送", "当前设备不支持vivo推送");
                return;
            }
            pushClient.turnOnPush(state -> {
                if (state != 0) {
                    Log.w("vivo推送", "打开推送失败:" + state);
                    return;
                }
                pushClient.getRegId(new IPushQueryActionListener() {
                    @Override
                    public void onSuccess(String regId) {
                        if (!TextUtils.isEmpty(regId)) {
                            Log.i("vivo推送", "获取RegId成功");
                            PushModel.getInstance().registerDeviceToken(
                                    regId,
                                    pushBundleID,
                                    PushModel.DEVICE_TYPE_VIVO
                            );
                        }
                    }

                    @Override
                    public void onFail(Integer errorCode) {
                        Log.w("vivo推送", "获取RegId失败:" + errorCode);
                    }
                });
            });
        } catch (VivoPushException e) {
            Log.e("vivo推送", "初始化失败", e);
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
            unregisterVendorPush(getContext());
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

    private Context getContext() {
        return mContext == null ? null : mContext.get();
    }

    private void unregisterVendorPush(Context context) {
        if (context == null) return;
        HonorPushClient honorPushClient = HonorPushClient.getInstance();
        if (honorPushClient.checkSupportHonorPush(context)) {
            honorPushClient.init(context, false);
            honorPushClient.deletePushToken(new HonorPushCallback<Void>() {
                @Override
                public void onSuccess(Void result) {
                    Log.i("荣耀推送", "Token注销成功");
                    reRegisterAfterLateLogoutIfNeeded();
                }

                @Override
                public void onFailure(int code, String errorMessage) {
                    Log.w("荣耀推送", "Token注销失败:" + code + ":" + errorMessage);
                    reRegisterAfterLateLogoutIfNeeded();
                }
            });
        } else if (OsUtils.isMiui()) {
            MiPushClient.unregisterPush(context);
        } else if (OsUtils.isOppo()) {
            HeytapPushManager.init(context, false);
            if (HeytapPushManager.isSupportPush(context)) {
                new Thread(
                        () -> HeytapPushManager.unRegister(
                                context,
                                PushKeys.oppoAppKey,
                                PushKeys.oppoAppSecret,
                                null,
                                oppoPushCallback
                        ),
                        "oppo-push-unregister"
                ).start();
            }
        } else if (OsUtils.isVivo()) {
            unregisterVivoPush(context);
        }
    }

    private void unregisterVivoPush(Context context) {
        try {
            PushClient pushClient = PushClient.getInstance(context);
            PushConfig config = new PushConfig.Builder()
                    .agreePrivacyStatement(true)
                    .build();
            pushClient.initialize(config);
            if (!pushClient.isSupport()) return;
            pushClient.deleteRegid(state -> {
                if (state == 0) {
                    Log.i("vivo推送", "RegId注销成功");
                } else {
                    Log.w("vivo推送", "RegId注销失败:" + state);
                }
                pushClient.turnOffPush(turnOffState -> {
                    if (turnOffState != 0) {
                        Log.w("vivo推送", "关闭推送失败:" + turnOffState);
                    }
                    reRegisterAfterLateLogoutIfNeeded();
                });
            });
        } catch (VivoPushException e) {
            Log.e("vivo推送", "注销初始化失败", e);
        }
    }

    /**
     * 厂商注销是异步的。若用户在回调返回前已经重新登录，立即重新注册，
     * 避免旧注销回调晚到而清掉新会话的厂商 Token。
     */
    private void reRegisterAfterLateLogoutIfNeeded() {
        if (WKConstants.isLogin()) {
            getPushToken();
        }
    }

    private void getPushToken() {
        Context context = getContext();
        if (context == null || TextUtils.isEmpty(WKConfig.getInstance().getUid())) {
            return;
        }
        if (HonorPushClient.getInstance().checkSupportHonorPush(context)) {
            initHonorPush(context);
        } else if (OsUtils.isMiui()) {
            initXiaoMiPush(context);
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

