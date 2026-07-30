package com.chat.flagship.screenshot;

import android.app.Activity;

/**
 * 截屏通知兼容入口。
 *
 * 全局截屏权限改由 appconfig 控制后，客户端不再监听系统截屏，也不再发送截屏消息。
 * 保留 start/stop 方法是为了兼容聊天页现有的 endpoint 调用。
 */
public class FlagshipScreenShotManager {
    private FlagshipScreenShotManager() {
    }

    private static class Binder {
        private static final FlagshipScreenShotManager INSTANCE = new FlagshipScreenShotManager();
    }

    public static FlagshipScreenShotManager getInstance() {
        return Binder.INSTANCE;
    }

    public void start(Activity activity) {
        // 不注册媒体库监听。
    }

    public void stop(Activity activity) {
        // 兼容旧调用，无需清理监听。
    }
}
