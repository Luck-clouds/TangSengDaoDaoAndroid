package com.chat.push;

import android.app.NotificationManager;
import android.content.Context;
import android.text.TextUtils;

import com.chat.base.config.WKConfig;
import com.chat.push.service.PushModel;

/**
 * Keeps the launcher badge, message notification and server badge in sync.
 */
public final class MessageBadgeController {
    private static final int MESSAGE_NOTIFICATION_ID = 1;

    private Context applicationContext;
    private int lastReportedBadge = -1;

    private MessageBadgeController() {
    }

    private static class ControllerHolder {
        private static final MessageBadgeController INSTANCE = new MessageBadgeController();
    }

    public static MessageBadgeController getInstance() {
        return ControllerHolder.INSTANCE;
    }

    public synchronized void init(Context context) {
        if (context != null) {
            applicationContext = context.getApplicationContext();
        }
    }

    public synchronized void sync(int unreadCount) {
        if (applicationContext == null) {
            return;
        }

        int badgeCount = Math.max(0, unreadCount);
        OsUtils.setBadge(applicationContext, badgeCount);
        if (badgeCount == 0) {
            clearMessageNotification();
        }

        if (badgeCount != lastReportedBadge
                && !TextUtils.isEmpty(WKConfig.getInstance().getToken())) {
            lastReportedBadge = badgeCount;
            PushModel.getInstance().registerBadge(badgeCount);
        }
    }

    public synchronized void refresh(int unreadCount) {
        lastReportedBadge = -1;
        sync(unreadCount);
    }

    public synchronized void clearMessageNotification() {
        if (applicationContext == null) {
            return;
        }

        NotificationManager notificationManager =
                (NotificationManager) applicationContext.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.cancel(MESSAGE_NOTIFICATION_ID);
        }
    }

    public synchronized void clearForLogout() {
        if (applicationContext != null) {
            OsUtils.setBadge(applicationContext, 0);
            clearMessageNotification();
        }
        lastReportedBadge = -1;
    }
}
