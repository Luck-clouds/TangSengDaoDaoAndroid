package com.chat.flagship.screenshot;

/**
 * 会话级截屏监听管理器
 * Created by Luckclouds .
 */

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.MediaStore;
import android.text.TextUtils;

import androidx.core.content.ContextCompat;

import com.chat.base.msg.IConversationContext;
import com.chat.flagship.msgmodel.WKScreenShotContent;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelExtras;

import java.lang.ref.WeakReference;
import java.util.Locale;

public class FlagshipScreenShotManager {
    private static final long VALID_WINDOW_MILLIS = 10_000L;
    private static final long DEDUP_WINDOW_MILLIS = 1_500L;

    private final Object lock = new Object();
    private HandlerThread observerThread;
    private ContentObserver internalObserver;
    private ContentObserver externalObserver;
    private WeakReference<Activity> activityRef;
    private WeakReference<IConversationContext> conversationContextRef;
    private String lastShotKey;
    private long lastShotAt;

    private FlagshipScreenShotManager() {
    }

    private static class Binder {
        private static final FlagshipScreenShotManager INSTANCE = new FlagshipScreenShotManager();
    }

    public static FlagshipScreenShotManager getInstance() {
        return Binder.INSTANCE;
    }

    public synchronized void start(Activity activity) {
        if (!(activity instanceof IConversationContext)) {
            return;
        }
        IConversationContext conversationContext = (IConversationContext) activity;
        // 只有当前会话开启截屏通知时才注册监听，关闭时直接保持静默。
        if (!isScreenshotEnabled(conversationContext)) {
            stop(activity);
            return;
        }
        if (!hasReadPermission(activity)) {
            return;
        }
        Activity currentActivity = activityRef == null ? null : activityRef.get();
        if (currentActivity == activity && externalObserver != null && internalObserver != null) {
            conversationContextRef = new WeakReference<>(conversationContext);
            return;
        }
        unregisterObservers();
        activityRef = new WeakReference<>(activity);
        conversationContextRef = new WeakReference<>(conversationContext);
        ensureObserverThread();
        Handler handler = new Handler(observerThread.getLooper());
        externalObserver = new ScreenShotObserver(handler, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        internalObserver = new ScreenShotObserver(handler, MediaStore.Images.Media.INTERNAL_CONTENT_URI);
        ContentResolver contentResolver = activity.getContentResolver();
        contentResolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, externalObserver);
        contentResolver.registerContentObserver(MediaStore.Images.Media.INTERNAL_CONTENT_URI, true, internalObserver);
    }

    public synchronized void stop(Activity activity) {
        Activity currentActivity = activityRef == null ? null : activityRef.get();
        if (activity == null || currentActivity == null || currentActivity == activity) {
            unregisterObservers();
            activityRef = null;
            conversationContextRef = null;
        }
    }

    private void ensureObserverThread() {
        if (observerThread == null) {
            observerThread = new HandlerThread("flagship_screenshot_observer");
            observerThread.start();
        }
    }

    private void unregisterObservers() {
        Activity activity = activityRef == null ? null : activityRef.get();
        if (activity != null) {
            ContentResolver contentResolver = activity.getContentResolver();
            if (externalObserver != null) {
                contentResolver.unregisterContentObserver(externalObserver);
            }
            if (internalObserver != null) {
                contentResolver.unregisterContentObserver(internalObserver);
            }
        }
        externalObserver = null;
        internalObserver = null;
    }

    private boolean isScreenshotEnabled(IConversationContext conversationContext) {
        WKChannel channel = conversationContext.getChatChannelInfo();
        if (channel == null || channel.remoteExtraMap == null || !channel.remoteExtraMap.containsKey(WKChannelExtras.screenshot)) {
            return false;
        }
        Object value = channel.remoteExtraMap.get(WKChannelExtras.screenshot);
        if (value instanceof Number) {
            return ((Number) value).intValue() == 1;
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value) == 1;
            } catch (Exception ignored) {
                return false;
            }
        }
        return false;
    }

    private boolean hasReadPermission(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_MEDIA_IMAGES)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
        }
        return ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    private void onMediaContentChanged(Uri contentUri) {
        Activity activity = activityRef == null ? null : activityRef.get();
        IConversationContext conversationContext = conversationContextRef == null ? null : conversationContextRef.get();
        if (activity == null || conversationContext == null || activity.isFinishing()) {
            return;
        }
        if (!isScreenshotEnabled(conversationContext)) {
            return;
        }
        ScreenShotMedia media = queryLatestMedia(activity.getContentResolver(), contentUri);
        if (media == null || !media.isRecent() || !media.isScreenshot()) {
            return;
        }
        synchronized (lock) {
            // 系统截图通常会触发多次媒体库变更，这里按短时间窗口去重，避免连发多条提示。
            if (!TextUtils.isEmpty(lastShotKey) && TextUtils.equals(lastShotKey, media.uniqueKey)
                    && System.currentTimeMillis() - lastShotAt < DEDUP_WINDOW_MILLIS) {
                return;
            }
            lastShotKey = media.uniqueKey;
            lastShotAt = System.currentTimeMillis();
        }
        activity.runOnUiThread(() -> {
            Activity currentActivity = activityRef == null ? null : activityRef.get();
            IConversationContext currentConversationContext = conversationContextRef == null ? null : conversationContextRef.get();
            if (currentActivity == null || currentConversationContext == null || currentActivity.isFinishing()) {
                return;
            }
            if (!isScreenshotEnabled(currentConversationContext)) {
                return;
            }
            currentConversationContext.sendMessage(new WKScreenShotContent());
        });
    }

    private ScreenShotMedia queryLatestMedia(ContentResolver contentResolver, Uri contentUri) {
        String[] projection;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projection = new String[]{
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.RELATIVE_PATH,
                    MediaStore.Images.Media.DATE_ADDED
            };
        } else {
            projection = new String[]{
                    MediaStore.Images.Media.DATA,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.DATE_ADDED
            };
        }
        try (Cursor cursor = contentResolver.query(contentUri, projection, null, null, MediaStore.Images.Media.DATE_ADDED + " DESC")) {
            if (cursor == null || !cursor.moveToFirst()) {
                return null;
            }
            String path = getString(cursor, MediaStore.Images.Media.DATA);
            String displayName = getString(cursor, MediaStore.Images.Media.DISPLAY_NAME);
            String relativePath = getString(cursor, MediaStore.Images.Media.RELATIVE_PATH);
            long dateAdded = 0L;
            int dateAddedIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATE_ADDED);
            if (dateAddedIndex >= 0) {
                dateAdded = cursor.getLong(dateAddedIndex) * 1000L;
            }
            return new ScreenShotMedia(path, displayName, relativePath, dateAdded);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String getString(Cursor cursor, String columnName) {
        int index = cursor.getColumnIndex(columnName);
        if (index >= 0) {
            return cursor.getString(index);
        }
        return "";
    }

    private static class ScreenShotMedia {
        private final String path;
        private final String displayName;
        private final String relativePath;
        private final long dateAdded;
        private final String uniqueKey;

        private ScreenShotMedia(String path, String displayName, String relativePath, long dateAdded) {
            this.path = path;
            this.displayName = displayName;
            this.relativePath = relativePath;
            this.dateAdded = dateAdded;
            String source = !TextUtils.isEmpty(path) ? path : relativePath + "/" + displayName;
            this.uniqueKey = source + "_" + dateAdded;
        }

        private boolean isRecent() {
            return dateAdded > 0 && System.currentTimeMillis() - dateAdded <= VALID_WINDOW_MILLIS;
        }

        private boolean isScreenshot() {
            // 兼容常见系统截图目录和文件名关键字，中英文都做一次兜底判断。
            String target = String.format(Locale.US, "%s %s %s", path, relativePath, displayName).toLowerCase(Locale.US);
            return target.contains("screenshot")
                    || target.contains("screen_shot")
                    || target.contains("screen-shot")
                    || target.contains("screen shot")
                    || target.contains("截屏")
                    || target.contains("截图")
                    || target.contains("屏幕截图")
                    || target.contains("screenshots");
        }
    }

    private class ScreenShotObserver extends ContentObserver {
        private final Uri contentUri;

        private ScreenShotObserver(Handler handler, Uri contentUri) {
            super(handler);
            this.contentUri = contentUri;
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            onMediaContentChanged(contentUri);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            super.onChange(selfChange, uri);
            onMediaContentChanged(uri == null ? contentUri : uri);
        }
    }
}
