package com.chat.uikit.setting;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.chat.base.config.WKConfig;
import com.chat.base.config.WKSharedPreferencesUtil;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.utils.ActManagerUtils;
import com.chat.base.utils.WKTimeUtils;
import com.chat.uikit.WKUIKitApplication;
import com.chat.uikit.user.service.UserModel;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.message.type.WKConnectStatus;

import java.lang.ref.WeakReference;

public class SecurityPrivacyManager {
    private static final String IM_STATUS_LISTENER_KEY = "security_privacy_manager";
    private static final String OFFLINE_PROTECTION_START_TIME_KEY = "offline_protection_start_time";
    private static final long IM_DISCONNECT_DELAY_MS = 3500L;
    private static final long IM_RECONNECT_POLL_MS = 1000L;
    private static final long IM_RECONNECT_INTERVAL_MS = 3000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private WeakReference<Application> applicationRef;
    private WeakReference<OfflineProtectionActivity> offlineActivityRef;
    private WeakReference<LockScreenPasswordActivity> lockActivityRef;
    private boolean isInitialized;
    private boolean isAppForeground;
    private boolean isNetworkAvailable = true;
    private boolean isImConnected = true;
    private long lastReconnectAttemptAt;

    private final Runnable delayedShowOfflineRunnable = this::showOfflineProtectionInternal;
    private final Runnable imReconnectPollRunnable = new Runnable() {
        @Override
        public void run() {
            if (!shouldPollImReconnect()) {
                return;
            }
            tryReconnectIm(false);
            handler.postDelayed(this, IM_RECONNECT_POLL_MS);
        }
    };

    private SecurityPrivacyManager() {
    }

    private static class Binder {
        private static final SecurityPrivacyManager INSTANCE = new SecurityPrivacyManager();
    }

    public static SecurityPrivacyManager getInstance() {
        return Binder.INSTANCE;
    }

    public void init(@NonNull Application application) {
        if (isInitialized) {
            return;
        }
        isInitialized = true;
        applicationRef = new WeakReference<>(application);
        isNetworkAvailable = checkNetworkAvailable(application);
        registerLifecycle(application);
        registerNetworkCallback(application);
        registerImStatusListener();
        registerEndpoints();
    }

    private void registerEndpoints() {
        EndpointManager.getInstance().setMethod("chow_check_lock_screen_pwd", object -> {
            checkAndShowLockScreen();
            return null;
        });
        EndpointManager.getInstance().setMethod("show_disconnect_screen", object -> {
            evaluateOfflineProtection();
            return null;
        });
        EndpointManager.getInstance().setMethod("wk_close_disconnect_screen", object -> {
            cancelDelayedOfflineShow();
            closeOfflineProtection();
            return null;
        });
    }

    private void registerLifecycle(@NonNull Application application) {
        application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            private int startedCount = 0;

            @Override
            public void onActivityCreated(@NonNull Activity activity, android.os.Bundle savedInstanceState) {
            }

            @Override
            public void onActivityStarted(@NonNull Activity activity) {
                startedCount++;
                isAppForeground = startedCount > 0;
                evaluateOfflineProtection();
            }

            @Override
            public void onActivityResumed(@NonNull Activity activity) {
            }

            @Override
            public void onActivityPaused(@NonNull Activity activity) {
            }

            @Override
            public void onActivityStopped(@NonNull Activity activity) {
                startedCount = Math.max(0, startedCount - 1);
                isAppForeground = startedCount > 0;
                if (!isAppForeground) {
                    cancelDelayedOfflineShow();
                }
            }

            @Override
            public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull android.os.Bundle outState) {
            }

            @Override
            public void onActivityDestroyed(@NonNull Activity activity) {
            }
        });
    }

    private void registerNetworkCallback(@NonNull Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return;
        }
        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();
        connectivityManager.registerNetworkCallback(request, new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                updateNetworkAvailability(checkNetworkAvailable(context));
            }

            @Override
            public void onLost(@NonNull Network network) {
                updateNetworkAvailability(checkNetworkAvailable(context));
            }

            @Override
            public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities networkCapabilities) {
                updateNetworkAvailability(checkNetworkAvailable(context));
            }
        });
    }

    private void registerImStatusListener() {
        WKIM.getInstance().getConnectionManager().addOnConnectionStatusListener(IM_STATUS_LISTENER_KEY, (status, reason) -> {
            isImConnected = status == WKConnectStatus.success
                    || status == WKConnectStatus.syncMsg
                    || status == WKConnectStatus.syncCompleted;
            evaluateOfflineProtection();
        });
    }

    public void checkAndShowLockScreen() {
        if (!isLoggedIn()) {
            return;
        }
        if (isLockActivityShowing()) {
            return;
        }
        if (!hasLockScreenPassword()) {
            evaluateOfflineProtection();
            return;
        }
        long lockStartTime = WKSharedPreferencesUtil.getInstance().getLong("lock_start_time");
        if (lockStartTime <= 0) {
            evaluateOfflineProtection();
            return;
        }
        int lockAfterMinute = WKConfig.getInstance().getUserInfo().lock_after_minute;
        long elapsedSeconds = Math.max(0, WKTimeUtils.getInstance().getCurrentSeconds() - lockStartTime);
        boolean shouldLock = lockAfterMinute <= 0 ? elapsedSeconds > 0 : elapsedSeconds >= lockAfterMinute * 60L;
        if (!shouldLock) {
            evaluateOfflineProtection();
            return;
        }
        closeOfflineProtection();
        Activity currentActivity = ActManagerUtils.getInstance().getCurrentActivity();
        if (currentActivity instanceof LockScreenPasswordActivity) {
            return;
        }
        Intent intent = LockScreenPasswordActivity.buildIntent(getLaunchContext(currentActivity), LockScreenPasswordActivity.SCENE_UNLOCK);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        launchIntent(currentActivity, intent);
    }

    public void onLockScreenVerified() {
        WKSharedPreferencesUtil.getInstance().putInt("wk_lock_screen_pwd_count", 5);
        WKSharedPreferencesUtil.getInstance().putLong("lock_start_time", WKTimeUtils.getInstance().getCurrentSeconds());
        handler.postDelayed(this::evaluateOfflineProtection, 200L);
    }

    public void rememberLockActivity(LockScreenPasswordActivity activity) {
        lockActivityRef = new WeakReference<>(activity);
    }

    public void forgetLockActivity(LockScreenPasswordActivity activity) {
        if (lockActivityRef != null && lockActivityRef.get() == activity) {
            lockActivityRef.clear();
            lockActivityRef = null;
        }
    }

    public void rememberOfflineActivity(OfflineProtectionActivity activity) {
        offlineActivityRef = new WeakReference<>(activity);
    }

    public void forgetOfflineActivity(OfflineProtectionActivity activity) {
        if (offlineActivityRef != null && offlineActivityRef.get() == activity) {
            offlineActivityRef.clear();
            offlineActivityRef = null;
        }
    }

    public void refreshProtectionState() {
        evaluateOfflineProtection();
    }

    public long getOfflineProtectionStartTimeSeconds() {
        return WKSharedPreferencesUtil.getInstance().getLong(OFFLINE_PROTECTION_START_TIME_KEY);
    }

    public void forgetPasswordAndLogout(@NonNull Context context) {
        UserModel.getInstance().deleteLockScreenPwd((code, msg) -> {
            clearLocalLockPassword();
            WKUIKitApplication.getInstance().exitLogin(0);
        });
    }

    public void clearLocalLockPassword() {
        if (WKConfig.getInstance().getUserInfo() != null) {
            com.chat.base.entity.UserInfoEntity userInfo = WKConfig.getInstance().getUserInfo();
            userInfo.lock_screen_pwd = "";
            WKConfig.getInstance().saveUserInfo(userInfo);
        }
        WKSharedPreferencesUtil.getInstance().putInt("wk_lock_screen_pwd_count", 5);
        WKSharedPreferencesUtil.getInstance().putLong("lock_start_time", 0);
        handler.post(this::evaluateOfflineProtection);
    }

    private void updateNetworkAvailability(boolean available) {
        boolean wasAvailable = isNetworkAvailable;
        isNetworkAvailable = available;
        if (available && !wasAvailable) {
            startImReconnectPolling(true);
        }
        evaluateOfflineProtection();
    }

    private void evaluateOfflineProtection() {
        if (!isLoggedIn() || !isOfflineProtectionEnabled()) {
            cancelDelayedOfflineShow();
            cancelImReconnectPolling();
            clearOfflineProtectionStartTime();
            closeOfflineProtection();
            return;
        }
        if (!isAppForeground) {
            cancelDelayedOfflineShow();
            cancelImReconnectPolling();
            closeOfflineProtection();
            return;
        }
        if (isLockActivityShowing()) {
            cancelDelayedOfflineShow();
            cancelImReconnectPolling();
            return;
        }
        if (!isNetworkAvailable) {
            cancelDelayedOfflineShow();
            cancelImReconnectPolling();
            ensureOfflineProtectionStartTime();
            showOfflineProtectionInternal();
            return;
        }
        if (isImConnected) {
            cancelDelayedOfflineShow();
            cancelImReconnectPolling();
            clearOfflineProtectionStartTime();
            closeOfflineProtection();
            return;
        }
        cancelDelayedOfflineShow();
        ensureOfflineProtectionStartTime();
        startImReconnectPolling(false);
        handler.postDelayed(delayedShowOfflineRunnable, IM_DISCONNECT_DELAY_MS);
    }

    private void showOfflineProtectionInternal() {
        if (!isLoggedIn() || !isAppForeground || !isOfflineProtectionEnabled()) {
            return;
        }
        if (isLockActivityShowing() || isOfflineActivityShowing()) {
            return;
        }
        Activity currentActivity = ActManagerUtils.getInstance().getCurrentActivity();
        Intent intent = new Intent(getLaunchContext(currentActivity), OfflineProtectionActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        launchIntent(currentActivity, intent);
    }

    private void closeOfflineProtection() {
        OfflineProtectionActivity activity = offlineActivityRef == null ? null : offlineActivityRef.get();
        if (activity != null && !activity.isFinishing()) {
            activity.finish();
        }
    }

    private void launchIntent(Activity currentActivity, Intent intent) {
        if (!(currentActivity instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Context context = getLaunchContext(null);
            if (context != null) {
                context.startActivity(intent);
            }
            return;
        }
        currentActivity.startActivity(intent);
    }

    private Context getLaunchContext(Activity currentActivity) {
        if (currentActivity != null) {
            return currentActivity;
        }
        return applicationRef == null ? null : applicationRef.get();
    }

    private boolean isOfflineActivityShowing() {
        return offlineActivityRef != null && offlineActivityRef.get() != null && !offlineActivityRef.get().isFinishing();
    }

    private boolean isLockActivityShowing() {
        return lockActivityRef != null && lockActivityRef.get() != null && !lockActivityRef.get().isFinishing();
    }

    private boolean isOfflineProtectionEnabled() {
        return WKConfig.getInstance().getUserInfo() != null
                && WKConfig.getInstance().getUserInfo().setting != null
                && WKConfig.getInstance().getUserInfo().setting.offline_protection == 1;
    }

    private boolean hasLockScreenPassword() {
        return WKConfig.getInstance().getUserInfo() != null
                && !TextUtils.isEmpty(WKConfig.getInstance().getUserInfo().lock_screen_pwd);
    }

    private boolean isLoggedIn() {
        return !TextUtils.isEmpty(WKConfig.getInstance().getToken()) && WKConfig.getInstance().getUserInfo() != null;
    }

    private void cancelDelayedOfflineShow() {
        handler.removeCallbacks(delayedShowOfflineRunnable);
    }

    private void startImReconnectPolling(boolean reconnectImmediately) {
        cancelImReconnectPolling();
        if (!shouldPollImReconnect()) {
            return;
        }
        if (reconnectImmediately) {
            tryReconnectIm(true);
        }
        handler.postDelayed(imReconnectPollRunnable, IM_RECONNECT_POLL_MS);
    }

    private void cancelImReconnectPolling() {
        handler.removeCallbacks(imReconnectPollRunnable);
    }

    private boolean shouldPollImReconnect() {
        return isLoggedIn()
                && isAppForeground
                && isOfflineProtectionEnabled()
                && !isLockActivityShowing()
                && isNetworkAvailable
                && !isImConnected;
    }

    private void tryReconnectIm(boolean force) {
        long now = SystemClock.elapsedRealtime();
        if (!force && now - lastReconnectAttemptAt < IM_RECONNECT_INTERVAL_MS) {
            return;
        }
        lastReconnectAttemptAt = now;
        WKUIKitApplication.getInstance().startChat();
    }

    private void ensureOfflineProtectionStartTime() {
        if (getOfflineProtectionStartTimeSeconds() > 0) {
            return;
        }
        WKSharedPreferencesUtil.getInstance().putLong(OFFLINE_PROTECTION_START_TIME_KEY, WKTimeUtils.getInstance().getCurrentSeconds());
    }

    private void clearOfflineProtectionStartTime() {
        WKSharedPreferencesUtil.getInstance().putLong(OFFLINE_PROTECTION_START_TIME_KEY, 0);
    }

    private boolean checkNetworkAvailable(@NonNull Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network network = connectivityManager.getActiveNetwork();
            if (network == null) {
                return false;
            }
            NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
            if (capabilities == null) {
                return false;
            }
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        }
        android.net.NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnected();
    }
}
