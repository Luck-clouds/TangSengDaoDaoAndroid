package com.chat.push;

import android.app.Activity;
import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import com.huawei.agconnect.config.AGConnectServicesConfig;
import com.huawei.hms.aaid.HmsInstanceId;
import com.chat.base.WKBaseApplication;
import com.chat.base.config.WKConstants;
import com.chat.base.endpoint.EndpointCategory;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.endpoint.entity.LoginMenu;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.ui.Theme;
import com.chat.base.utils.WKDialogUtils;
import com.chat.base.utils.systembar.WKOSUtils;
import com.chat.push.service.PushModel;
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
    private volatile boolean pushInitialized = false;
    private volatile String huaweiToken = "";

    public static WKPushApplication getInstance() {
        return PushApplicationBinder.push;
    }


    //初始化推送服务
    public void init(String pushBundleID, final Context context) {
        this.pushBundleID = pushBundleID;
        this.mContext = new WeakReference<>(context);
        addListener();
        initPush();
    }

    public void registerNotificationDialog(String pushBundleID, final Context context) {
        this.pushBundleID = pushBundleID;
        this.mContext = new WeakReference<>(context);
        addListener();
    }

    private void initPush() {
        if (mContext == null || mContext.get() == null) return;
        notifyChannel(WKBaseApplication.getInstance().application);
        pushInitialized = true;
        requestHuaweiToken();
    }

    /**
     * 使用 agconnect-services.json 中的 APP ID 获取华为 Push Token。
     * 华为要求 getToken 在工作线程调用，且不能高频重复申请。
     */
    private void requestHuaweiToken() {
        Context context = mContext == null ? null : mContext.get();
        if (!pushInitialized || context == null) return;
        final Context appContext = context.getApplicationContext();
        final String appId;
        try {
            appId = AGConnectServicesConfig.fromContext(appContext).getString("client/app_id");
        } catch (Exception e) {
            Log.w("HuaweiPush", "Unable to read app/agconnect-services.json", e);
            return;
        }
        if (TextUtils.isEmpty(appId)) {
            Log.w("HuaweiPush", "Missing client/app_id. Add app/agconnect-services.json before testing push.");
            return;
        }
        new Thread(() -> {
            try {
                String token = HmsInstanceId.getInstance(appContext).getToken(appId, "HCM");
                if (!TextUtils.isEmpty(token)) {
                    onHuaweiToken(token);
                }
            } catch (Exception e) {
                Log.e("HuaweiPush", "Failed to obtain Huawei push token", e);
            }
        }, "HuaweiPushToken").start();
    }

    /** Token 主动获取和 SDK 刷新回调统一走这里，登录后上传到业务服务端。 */
    public void onHuaweiToken(String token) {
        if (TextUtils.isEmpty(token)) return;
        huaweiToken = token;
        uploadHuaweiTokenIfLoggedIn();
    }

    private void uploadHuaweiTokenIfLoggedIn() {
        if (!WKConstants.isLogin() || TextUtils.isEmpty(huaweiToken)
                || TextUtils.isEmpty(pushBundleID)) {
            return;
        }
        PushModel.getInstance().registerDeviceToken(huaweiToken, pushBundleID);
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
            return null;
        });

        // Token 可能在登录前取得；登录完成后补传，避免首次登录漏注册。
        EndpointManager.getInstance().setMethod("huawei_push_after_login", EndpointCategory.loginMenus,
                object -> new LoginMenu(() -> {
                    if (TextUtils.isEmpty(huaweiToken)) {
                        requestHuaweiToken();
                    } else {
                        uploadHuaweiTokenIfLoggedIn();
                    }
                }));

        //设置桌面红点数量
        EndpointManager.getInstance().setMethod("push_update_device_badge", object -> {
            int num = (int) object;
            PushModel.getInstance().registerBadge(num);
            OsUtils.setBadge(WKBaseApplication.getInstance().getContext(), num);
            return null;
        });
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

