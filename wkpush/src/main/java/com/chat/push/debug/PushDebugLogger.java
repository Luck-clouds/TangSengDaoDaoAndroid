package com.chat.push.debug;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

/** HUAWEI 分支设备端推送诊断日志。 */
public final class PushDebugLogger {
    private static final String TAG = "HuaweiPush";
    private static final String PREFS_NAME = "huawei_push_debug";
    private static final String PREFS_LOGS = "logs";
    private static final int MAX_LOG_COUNT = 200;
    private static final String SEPARATOR = "\u001e";
    private static final Object LOCK = new Object();
    private static final ArrayDeque<String> LOGS = new ArrayDeque<>();
    private static final CopyOnWriteArrayList<Listener> LISTENERS = new CopyOnWriteArrayList<>();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static final SimpleDateFormat TIME_FORMAT =
            new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault());

    private static volatile Context appContext;
    private static volatile boolean loaded;

    private PushDebugLogger() {
    }

    public static void init(Context context) {
        if (context == null) return;
        appContext = context.getApplicationContext();
        synchronized (LOCK) {
            if (loaded) return;
            loaded = true;
            String saved = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .getString(PREFS_LOGS, "");
            if (!TextUtils.isEmpty(saved)) {
                String[] items = saved.split(SEPARATOR, -1);
                for (String item : items) {
                    if (!TextUtils.isEmpty(item)) LOGS.addLast(item);
                }
                trimLocked();
            }
        }
    }

    public static void info(String message) {
        Log.i(TAG, message);
        append("I", message);
    }

    public static void warn(String message) {
        Log.w(TAG, message);
        append("W", message);
    }

    public static void error(String message, Throwable error) {
        String detail = message;
        if (error != null) {
            detail += " | " + describeThrowable(error);
            Log.e(TAG, message, error);
        } else {
            Log.e(TAG, message);
        }
        append("E", detail);
    }

    public static String maskToken(String token) {
        if (TextUtils.isEmpty(token)) return "<empty>";
        int length = token.length();
        if (length <= 8) return "*** (len=" + length + ")";
        return token.substring(0, 4) + "…" + token.substring(length - 4)
                + " (len=" + length + ")";
    }

    public static String snapshot() {
        synchronized (LOCK) {
            return TextUtils.join("\n", LOGS);
        }
    }

    public static void clear() {
        synchronized (LOCK) {
            LOGS.clear();
            persistLocked();
        }
        notifyListeners();
    }

    public static void addListener(Listener listener) {
        if (listener == null) return;
        LISTENERS.addIfAbsent(listener);
        MAIN_HANDLER.post(() -> listener.onLogsChanged(snapshot()));
    }

    public static void removeListener(Listener listener) {
        LISTENERS.remove(listener);
    }

    private static void append(String level, String message) {
        String time;
        synchronized (TIME_FORMAT) {
            time = TIME_FORMAT.format(new Date());
        }
        String line = time + " [" + level + "][" + Thread.currentThread().getName()
                + "] " + safe(message);
        synchronized (LOCK) {
            LOGS.addLast(line);
            trimLocked();
            persistLocked();
        }
        notifyListeners();
    }

    private static void trimLocked() {
        while (LOGS.size() > MAX_LOG_COUNT) LOGS.removeFirst();
    }

    private static void persistLocked() {
        Context context = appContext;
        if (context == null) return;
        SharedPreferences.Editor editor = context
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
        editor.putString(PREFS_LOGS, TextUtils.join(SEPARATOR, LOGS)).apply();
    }

    private static void notifyListeners() {
        String logs = snapshot();
        MAIN_HANDLER.post(() -> {
            for (Listener listener : new ArrayList<>(LISTENERS)) {
                listener.onLogsChanged(logs);
            }
        });
    }

    private static String safe(String value) {
        return value == null ? "" : value.replace('\n', ' ').replace('\r', ' ');
    }

    private static String describeThrowable(Throwable error) {
        StringBuilder builder = new StringBuilder();
        Throwable current = error;
        int depth = 0;
        while (current != null && depth < 4) {
            if (depth > 0) builder.append(" <- caused by ");
            builder.append(current.getClass().getName());
            String message = safe(current.getMessage());
            if (!TextUtils.isEmpty(message)) builder.append(": ").append(message);
            current = current.getCause();
            depth++;
        }
        return builder.toString();
    }

    public interface Listener {
        void onLogsChanged(String logs);
    }
}
