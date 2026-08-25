package com.chat.push;

import android.app.Activity;
import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;

import com.huawei.agconnect.config.AGConnectServicesConfig;
import com.huawei.hms.aaid.HmsInstanceId;
import com.huawei.hms.api.HuaweiApiAvailability;
import com.chat.base.WKBaseApplication;
import com.chat.base.config.WKConstants;
import com.chat.base.endpoint.EndpointCategory;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.endpoint.entity.LoginMenu;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.ui.Theme;
import com.chat.base.utils.WKDialogUtils;
import com.chat.base.utils.systembar.WKOSUtils;
import com.chat.push.debug.PushDebugLogger;
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
        MessageBadgeController.getInstance().init(context);
        PushDebugLogger.info("初始化华为推送，package=" + pushBundleID
                + "，已登录=" + WKConstants.isLogin());
        addListener();
        initPush();
    }

    public void registerNotificationDialog(String pushBundleID, final Context context) {
        this.pushBundleID = pushBundleID;
        this.mContext = new WeakReference<>(context);
        MessageBadgeController.getInstance().init(context);
        PushDebugLogger.info("华为推送通知设置入口已注册，package=" + pushBundleID);
        addListener();
    }

    private void initPush() {
        if (mContext == null || mContext.get() == null) {
            PushDebugLogger.warn("推送初始化终止：Context 不可用");
            return;
        }
        notifyChannel(WKBaseApplication.getInstance().application);
        pushInitialized = true;
        PushDebugLogger.info("开始初始化 Push Kit 6.13.0.301，HMS Core Installer 6.13.0.304");
        logDeviceEnvironment(mContext.get().getApplicationContext());
        requestHuaweiToken();
    }

    private void logDeviceEnvironment(Context context) {
        PushDebugLogger.info("设备环境：manufacturer=" + Build.MANUFACTURER
                + "，brand=" + Build.BRAND
                + "，model=" + Build.MODEL
                + "，Android=" + Build.VERSION.RELEASE
                + "，SDK=" + Build.VERSION.SDK_INT);
        try {
            PackageInfo packageInfo = context.getPackageManager()
                    .getPackageInfo("com.huawei.hwid", 0);
            long versionCode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    ? packageInfo.getLongVersionCode() : packageInfo.versionCode;
            PushDebugLogger.info("HMS Core 已安装：versionName=" + packageInfo.versionName
                    + "，versionCode=" + versionCode);
        } catch (PackageManager.NameNotFoundException e) {
            PushDebugLogger.warn("未检测到 HMS Core（com.huawei.hwid）");
        } catch (Exception e) {
            PushDebugLogger.error("读取 HMS Core 版本失败", e);
        }
    }

    /**
     * 使用 agconnect-services.json 中的 APP ID 获取华为 Push Token。
     * 华为要求 getToken 在工作线程调用，且不能高频重复申请。
     */
    private void requestHuaweiToken() {
        Context context = mContext == null ? null : mContext.get();
        if (!pushInitialized || context == null) {
            PushDebugLogger.warn("跳过 Token 请求：pushInitialized=" + pushInitialized
                    + "，context=" + (context != null));
            return;
        }
        final Context appContext = context.getApplicationContext();
        try {
            int availabilityCode = HuaweiApiAvailability.getInstance()
                    .isHuaweiMobileServicesAvailable(appContext);
            PushDebugLogger.info("HMS Core 可用性检查：code=" + availabilityCode
                    + "，" + HuaweiApiAvailability.getInstance().getErrorString(availabilityCode));
        } catch (Exception e) {
            PushDebugLogger.error("HMS Core 可用性检查异常", e);
        }
        PushDebugLogger.info("开始解析华为 APP ID：package=" + appContext.getPackageName());
        final AppIdResult appIdResult = resolveHuaweiAppId(appContext);
        final String appId = appIdResult.appId;
        if (TextUtils.isEmpty(appId)) {
            Log.w("HuaweiPush", "Unable to resolve Huawei app id from AGConnect or manifest");
            PushDebugLogger.warn("Token 请求终止：所有 APP ID 来源均为空；请检查 AGConnect 构建资源和 Manifest 元数据");
            return;
        }
        PushDebugLogger.info("APP ID 解析成功：appId=" + appId + "，source="
                + appIdResult.source + "，准备请求 scope=HCM 的 Token");
        final long queuedAt = SystemClock.elapsedRealtime();
        new Thread(() -> {
            long startedAt = SystemClock.elapsedRealtime();
            PushDebugLogger.info("Token 工作线程启动：排队耗时=" + (startedAt - queuedAt)
                    + "ms，appId=" + appId + "，scope=HCM");
            try {
                String token = HmsInstanceId.getInstance(appContext).getToken(appId, "HCM");
                long elapsed = SystemClock.elapsedRealtime() - startedAt;
                if (!TextUtils.isEmpty(token)) {
                    PushDebugLogger.info("主动获取 Token 成功：耗时=" + elapsed + "ms，token="
                            + PushDebugLogger.maskToken(token));
                    onHuaweiToken(token);
                } else {
                    PushDebugLogger.warn("getToken 返回空值：耗时=" + elapsed
                            + "ms，等待 HmsMessageService.onNewToken 回调");
                }
            } catch (Exception e) {
                Log.e("HuaweiPush", "Failed to obtain Huawei push token", e);
                PushDebugLogger.error("主动获取华为 Token 失败：耗时="
                        + (SystemClock.elapsedRealtime() - startedAt) + "ms，appId=" + appId
                        + "，scope=HCM", e);
            }
        }, "HuaweiPushToken").start();
    }

    /**
     * 新版 AGConnect 优先走加密资源；老设备或资源 Context 不兼容时，回退到
     * app_info 节点以及 HMS 已合并进 Manifest 的公开 APP ID。
     */
    private AppIdResult resolveHuaweiAppId(Context context) {
        String clientAppId = readAgConnectValue(context, "client/app_id");
        if (!TextUtils.isEmpty(clientAppId)) {
            return new AppIdResult(clientAppId, "AGConnect client/app_id");
        }

        String appInfoAppId = readAgConnectValue(context, "app_info/app_id");
        if (!TextUtils.isEmpty(appInfoAppId)) {
            return new AppIdResult(appInfoAppId, "AGConnect app_info/app_id");
        }

        try {
            ApplicationInfo applicationInfo = context.getPackageManager().getApplicationInfo(
                    context.getPackageName(), PackageManager.GET_META_DATA);
            Bundle metaData = applicationInfo.metaData;
            Object rawValue = metaData == null ? null
                    : metaData.get("com.huawei.hms.client.appid");
            String manifestAppId = normalizeAppId(rawValue == null ? null
                    : String.valueOf(rawValue));
            PushDebugLogger.info("APP ID 候选[Manifest com.huawei.hms.client.appid]：raw="
                    + printableValue(rawValue) + "，normalized="
                    + printableValue(manifestAppId));
            if (!TextUtils.isEmpty(manifestAppId)) {
                return new AppIdResult(manifestAppId, "AndroidManifest meta-data");
            }
        } catch (Exception e) {
            PushDebugLogger.error("读取 Manifest 华为 APP ID 失败：package="
                    + context.getPackageName(), e);
        }
        return new AppIdResult("", "none");
    }

    private String readAgConnectValue(Context context, String path) {
        try {
            String rawValue = AGConnectServicesConfig.fromContext(context).getString(path);
            String normalized = normalizeAppId(rawValue);
            PushDebugLogger.info("APP ID 候选[" + path + "]：raw="
                    + printableValue(rawValue) + "，normalized=" + printableValue(normalized));
            return normalized;
        } catch (Exception e) {
            PushDebugLogger.error("读取 AGConnect 配置失败：path=" + path, e);
            return "";
        }
    }

    private static String normalizeAppId(String value) {
        if (TextUtils.isEmpty(value)) return "";
        String normalized = value.trim();
        if (normalized.regionMatches(true, 0, "appid=", 0, 6)) {
            normalized = normalized.substring(6).trim();
        }
        return normalized;
    }

    private static String printableValue(Object value) {
        if (value == null) return "<null>";
        String text = String.valueOf(value);
        return TextUtils.isEmpty(text) ? "<empty>" : text;
    }

    private static final class AppIdResult {
        final String appId;
        final String source;

        AppIdResult(String appId, String source) {
            this.appId = appId;
            this.source = source;
        }
    }

    /** Token 主动获取和 SDK 刷新回调统一走这里，登录后上传到业务服务端。 */
    public void onHuaweiToken(String token) {
        if (TextUtils.isEmpty(token)) {
            PushDebugLogger.warn("收到空的华为 Token，忽略");
            return;
        }
        huaweiToken = token;
        PushDebugLogger.info("Token 已保存：" + PushDebugLogger.maskToken(token));
        uploadHuaweiTokenIfLoggedIn();
    }

    private void uploadHuaweiTokenIfLoggedIn() {
        if (!WKConstants.isLogin()) {
            PushDebugLogger.warn("暂不上报 Token：用户未登录，登录成功后将补传");
            return;
        }
        if (TextUtils.isEmpty(huaweiToken)) {
            PushDebugLogger.warn("暂不上报 Token：Token 为空");
            return;
        }
        if (TextUtils.isEmpty(pushBundleID)) {
            PushDebugLogger.warn("暂不上报 Token：bundleId 为空");
            return;
        }
        PushDebugLogger.info("准备向业务服务端上报 Token，device_type=HMS，bundleId="
                + pushBundleID);
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
            PushDebugLogger.info("收到退出登录事件，开始解绑推送 Token");
            MessageBadgeController.getInstance().clearForLogout();
            // 后端解绑使用退出前快照的登录 token；失败不阻塞正常退出。
            PushModel.getInstance().unRegisterDeviceToken((code, msg) -> {
                if (code != HttpResponseCode.success) {
                    Log.w("注销push", code + ":" + msg);
                    PushDebugLogger.warn("业务服务端解绑 Token 失败：code=" + code + "，msg=" + msg);
                } else {
                    PushDebugLogger.info("业务服务端解绑 Token 成功");
                }
            });
            return null;
        });

        // Token 可能在登录前取得；登录完成后补传，避免首次登录漏注册。
        EndpointManager.getInstance().setMethod("huawei_push_after_login", EndpointCategory.loginMenus,
                object -> new LoginMenu(() -> {
                    PushDebugLogger.info("收到登录成功事件，检查是否需要补传推送 Token");
                    if (TextUtils.isEmpty(huaweiToken)) {
                        requestHuaweiToken();
                    } else {
                        uploadHuaweiTokenIfLoggedIn();
                    }
                }));

        //设置桌面红点数量
        EndpointManager.getInstance().setMethod("push_update_device_badge", object -> {
            int num = (int) object;
            MessageBadgeController.getInstance().sync(num);
            return null;
        });
        EndpointManager.getInstance().setMethod("push_refresh_device_badge", object -> {
            int num = (int) object;
            MessageBadgeController.getInstance().refresh(num);
            return null;
        });
        EndpointManager.getInstance().setMethod("push_clear_message_notification", object -> {
            MessageBadgeController.getInstance().clearMessageNotification();
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

