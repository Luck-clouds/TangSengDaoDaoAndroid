package com.chat.uikit.setting;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.chat.base.config.WKConfig;
import com.chat.base.config.WKSharedPreferencesUtil;
import com.chat.base.utils.ActManagerUtils;
import com.chat.base.utils.WKCommonUtils;
import com.chat.base.utils.WKTimeUtils;
import com.chat.uikit.WKUIKitApplication;

import java.lang.ref.WeakReference;

public class TeenModeManager {
    private static final String KEY_ENABLED = "teen_mode_enabled";
    private static final String KEY_PASSWORD = "teen_mode_password";
    private static final String KEY_RETRY_COUNT = "teen_mode_retry_count";
    private static final String KEY_LAST_VERIFY_AT = "teen_mode_last_verify_at";
    private static final String KEY_PENDING_VERIFY = "teen_mode_pending_verify";
    private static final int MAX_RETRY_COUNT = 5;
    private static final long VERIFY_VALID_DURATION_SECONDS = 24 * 60 * 60L;

    private WeakReference<TeenModePasswordActivity> passwordActivityRef;

    private TeenModeManager() {
    }

    private static class Binder {
        private static final TeenModeManager INSTANCE = new TeenModeManager();
    }

    public static TeenModeManager getInstance() {
        return Binder.INSTANCE;
    }

    public boolean isEnabled() {
        return WKSharedPreferencesUtil.getInstance().getBooleanWithUID(KEY_ENABLED, false);
    }

    public void enable(@NonNull String passwordDigest) {
        WKSharedPreferencesUtil.getInstance().putBooleanWithUID(KEY_ENABLED, true);
        WKSharedPreferencesUtil.getInstance().putSPWithUID(KEY_PASSWORD, passwordDigest);
        WKSharedPreferencesUtil.getInstance().putIntWithUID(KEY_RETRY_COUNT, MAX_RETRY_COUNT);
        WKSharedPreferencesUtil.getInstance().putLongWithUID(KEY_LAST_VERIFY_AT, 0);
        WKSharedPreferencesUtil.getInstance().putBooleanWithUID(KEY_PENDING_VERIFY, false);
    }

    public void disable() {
        clearLocalData();
    }

    public void clearLocalData() {
        WKSharedPreferencesUtil.getInstance().putBooleanWithUID(KEY_ENABLED, false);
        WKSharedPreferencesUtil.getInstance().putSPWithUID(KEY_PASSWORD, "");
        WKSharedPreferencesUtil.getInstance().putIntWithUID(KEY_RETRY_COUNT, MAX_RETRY_COUNT);
        WKSharedPreferencesUtil.getInstance().putLongWithUID(KEY_LAST_VERIFY_AT, 0);
        WKSharedPreferencesUtil.getInstance().putBooleanWithUID(KEY_PENDING_VERIFY, false);
    }

    public void markVerifyPending() {
        if (!isEnabled()) {
            return;
        }
        WKSharedPreferencesUtil.getInstance().putBooleanWithUID(KEY_PENDING_VERIFY, true);
    }

    public boolean shouldVerifyOnForeground() {
        if (!isEnabled()) {
            return false;
        }
        if (!WKSharedPreferencesUtil.getInstance().getBooleanWithUID(KEY_PENDING_VERIFY, false)) {
            return false;
        }
        long lastVerifyAt = WKSharedPreferencesUtil.getInstance().getLongWithUID(KEY_LAST_VERIFY_AT, 0);
        if (lastVerifyAt <= 0) {
            return true;
        }
        long currentSeconds = WKTimeUtils.getInstance().getCurrentSeconds();
        return currentSeconds - lastVerifyAt >= VERIFY_VALID_DURATION_SECONDS;
    }

    public void onPasswordVerified() {
        WKSharedPreferencesUtil.getInstance().putIntWithUID(KEY_RETRY_COUNT, MAX_RETRY_COUNT);
        WKSharedPreferencesUtil.getInstance().putLongWithUID(KEY_LAST_VERIFY_AT, WKTimeUtils.getInstance().getCurrentSeconds());
        WKSharedPreferencesUtil.getInstance().putBooleanWithUID(KEY_PENDING_VERIFY, false);
        SecurityPrivacyManager.getInstance().refreshProtectionState();
    }

    public boolean verifyPassword(@NonNull String password) {
        String passwordDigest = WKCommonUtils.digest(password);
        return TextUtils.equals(passwordDigest, getPasswordDigest());
    }

    public int consumeRetryCountOnError() {
        int remainCount = getRemainRetryCount() - 1;
        remainCount = Math.max(remainCount, 0);
        WKSharedPreferencesUtil.getInstance().putIntWithUID(KEY_RETRY_COUNT, remainCount);
        return remainCount;
    }

    public int getRemainRetryCount() {
        return WKSharedPreferencesUtil.getInstance().getIntWithUID(KEY_RETRY_COUNT, MAX_RETRY_COUNT);
    }

    public void resetRetryCount() {
        WKSharedPreferencesUtil.getInstance().putIntWithUID(KEY_RETRY_COUNT, MAX_RETRY_COUNT);
    }

    public void rememberPasswordActivity(@NonNull TeenModePasswordActivity activity) {
        passwordActivityRef = new WeakReference<>(activity);
    }

    public void forgetPasswordActivity(@NonNull TeenModePasswordActivity activity) {
        if (passwordActivityRef != null && passwordActivityRef.get() == activity) {
            passwordActivityRef.clear();
            passwordActivityRef = null;
        }
    }

    public boolean isPasswordActivityShowing() {
        return passwordActivityRef != null
                && passwordActivityRef.get() != null
                && !passwordActivityRef.get().isFinishing();
    }

    public boolean checkAndShowTeenMode() {
        if (!isLoggedIn() || !shouldVerifyOnForeground()) {
            return false;
        }
        if (isPasswordActivityShowing()) {
            return true;
        }
        Activity currentActivity = ActManagerUtils.getInstance().getCurrentActivity();
        if (currentActivity instanceof TeenModePasswordActivity) {
            return true;
        }
        SecurityPrivacyManager.getInstance().closeOfflineProtectionScreen();
        Intent intent = TeenModePasswordActivity.buildIntent(getLaunchContext(currentActivity), TeenModePasswordActivity.SCENE_VERIFY_UNLOCK);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (currentActivity == null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        Context context = getLaunchContext(currentActivity);
        if (context == null) {
            return false;
        }
        context.startActivity(intent);
        return true;
    }

    public void forgetPasswordAndLogout() {
        clearLocalData();
        WKUIKitApplication.getInstance().exitLogin(0);
    }

    private Context getLaunchContext(Activity currentActivity) {
        if (currentActivity != null) {
            return currentActivity;
        }
        return WKUIKitApplication.getInstance().getContext();
    }

    private boolean isLoggedIn() {
        return !TextUtils.isEmpty(WKConfig.getInstance().getToken()) && WKConfig.getInstance().getUserInfo() != null;
    }

    private String getPasswordDigest() {
        return WKSharedPreferencesUtil.getInstance().getSPWithUID(KEY_PASSWORD);
    }
}
