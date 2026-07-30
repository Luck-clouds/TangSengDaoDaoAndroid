package com.chat.base.utils;

import android.app.Activity;
import android.view.Window;
import android.view.WindowManager;

import com.chat.base.config.WKConfig;
import com.chat.base.entity.WKAPPConfig;

/**
 * 根据服务端 appconfig 统一应用全局截屏策略。
 */
public final class WKScreenCapturePolicy {
    private WKScreenCapturePolicy() {
    }

    public static void apply(Activity activity) {
        if (activity == null || activity.isFinishing()) {
            return;
        }
        Window window = activity.getWindow();
        if (window == null) {
            return;
        }
        WKAPPConfig config = WKConfig.getInstance().getAppConfig();
        if (config.global_screenshot_on == 0) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }
    }
}
