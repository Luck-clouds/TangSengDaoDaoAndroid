package com.chat.push.debug;

import android.app.Activity;
import android.app.Application;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.chat.base.utils.ActManagerUtils;

/** 无系统悬浮窗权限的应用内推送日志面板。 */
public final class PushLogOverlay implements Application.ActivityLifecycleCallbacks,
        PushDebugLogger.Listener {
    private static final PushLogOverlay INSTANCE = new PushLogOverlay();

    private Application application;
    private Activity activity;
    private LinearLayout rootView;
    private TextView logView;
    private ScrollView scrollView;
    private boolean expanded;
    private int lastLeft = -1;
    private int lastTop = -1;

    private PushLogOverlay() {
    }

    public static void install(Context context) {
        if (context == null) return;
        Context appContext = context.getApplicationContext();
        if (!(appContext instanceof Application)) return;
        PushDebugLogger.init(appContext);
        INSTANCE.installInternal((Application) appContext);
    }

    private synchronized void installInternal(Application app) {
        if (application == null) {
            application = app;
            application.registerActivityLifecycleCallbacks(this);
            PushDebugLogger.addListener(this);
            PushDebugLogger.info("设备端华为推送日志悬浮窗已启用");
        }
        Activity current = ActManagerUtils.getInstance().getCurrentActivity();
        if (current != null) current.runOnUiThread(() -> attach(current));
    }

    private void attach(Activity target) {
        if (target == null || target.isFinishing()) return;
        if (activity == target && rootView != null && rootView.getParent() != null) return;
        detach();
        activity = target;
        View decor = target.getWindow().getDecorView();
        if (!(decor instanceof ViewGroup)) return;

        rootView = new LinearLayout(target);
        rootView.setOrientation(LinearLayout.VERTICAL);
        rootView.setElevation(dp(target, 12));
        rebuildContent();

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.TOP | Gravity.START;
        ((ViewGroup) decor).addView(rootView, params);
        rootView.post(() -> {
            if (rootView == null) return;
            int maxLeft = Math.max(0, decor.getWidth() - rootView.getWidth());
            int maxTop = Math.max(0, decor.getHeight() - rootView.getHeight());
            setPosition(lastLeft < 0 ? maxLeft - dp(target, 12) : Math.min(lastLeft, maxLeft),
                    lastTop < 0 ? dp(target, 72) : Math.min(lastTop, maxTop));
        });
    }

    private void rebuildContent() {
        if (rootView == null || activity == null) return;
        rootView.removeAllViews();
        if (!expanded) {
            rootView.setBackground(null);
            Button button = new Button(activity);
            button.setText("HMS\n日志");
            button.setTextSize(11);
            button.setTextColor(Color.WHITE);
            button.setAllCaps(false);
            button.setPadding(dp(activity, 6), 0, dp(activity, 6), 0);
            button.setBackground(rounded(0xE6C62828, dp(activity, 24)));
            View.OnClickListener expandListener = v -> {
                expanded = true;
                rebuildContent();
            };
            button.setOnClickListener(expandListener);
            button.setOnTouchListener(new DragTouchListener(expandListener));
            rootView.addView(button, new LinearLayout.LayoutParams(dp(activity, 58), dp(activity, 48)));
            rootView.post(this::clampPosition);
            return;
        }

        int panelWidth = Math.min(dp(activity, 360),
                activity.getResources().getDisplayMetrics().widthPixels - dp(activity, 16));
        rootView.setBackground(rounded(0xEE101010, dp(activity, 8)));
        LinearLayout header = new LinearLayout(activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(activity, 8), dp(activity, 4), dp(activity, 4), dp(activity, 4));

        TextView title = new TextView(activity);
        title.setText("华为推送诊断日志（可拖动）");
        title.setTextColor(Color.WHITE);
        title.setTextSize(13);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(activity, 40), 1));

        Button copy = smallButton("复制");
        copy.setOnClickListener(v -> copyLogs());
        header.addView(copy);
        Button clear = smallButton("清空");
        clear.setOnClickListener(v -> PushDebugLogger.clear());
        header.addView(clear);
        Button close = smallButton("收起");
        close.setOnClickListener(v -> {
            expanded = false;
            rebuildContent();
        });
        header.addView(close);
        header.setOnTouchListener(new DragTouchListener(null));
        rootView.addView(header, new LinearLayout.LayoutParams(panelWidth, dp(activity, 48)));

        scrollView = new ScrollView(activity);
        logView = new TextView(activity);
        logView.setTextColor(0xFFE8E8E8);
        logView.setTextSize(11);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setTextIsSelectable(true);
        logView.setPadding(dp(activity, 8), dp(activity, 4), dp(activity, 8), dp(activity, 8));
        scrollView.addView(logView, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        rootView.addView(scrollView, new LinearLayout.LayoutParams(panelWidth, dp(activity, 330)));
        updateLogs(PushDebugLogger.snapshot());
        rootView.post(this::clampPosition);
    }

    private Button smallButton(String text) {
        Button button = new Button(activity);
        button.setText(text);
        button.setTextSize(10);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(activity, 5), 0, dp(activity, 5), 0);
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setLayoutParams(new LinearLayout.LayoutParams(dp(activity, 46), dp(activity, 38)));
        return button;
    }

    private void copyLogs() {
        if (activity == null) return;
        ClipboardManager clipboard = (ClipboardManager)
                activity.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("Huawei Push Logs",
                    PushDebugLogger.snapshot()));
            Toast.makeText(activity, "推送日志已复制", Toast.LENGTH_SHORT).show();
        }
    }

    private void setPosition(int left, int top) {
        if (rootView == null || !(rootView.getLayoutParams() instanceof FrameLayout.LayoutParams)) return;
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) rootView.getLayoutParams();
        params.gravity = Gravity.TOP | Gravity.START;
        params.leftMargin = Math.max(0, left);
        params.topMargin = Math.max(0, top);
        rootView.setLayoutParams(params);
        lastLeft = params.leftMargin;
        lastTop = params.topMargin;
    }

    private void clampPosition() {
        if (rootView == null || !(rootView.getParent() instanceof View)) return;
        View parent = (View) rootView.getParent();
        int maxLeft = Math.max(0, parent.getWidth() - rootView.getWidth());
        int maxTop = Math.max(0, parent.getHeight() - rootView.getHeight());
        setPosition(Math.min(lastLeft < 0 ? maxLeft : lastLeft, maxLeft),
                Math.min(lastTop < 0 ? 0 : lastTop, maxTop));
    }

    private void detach() {
        if (rootView != null && rootView.getParent() instanceof ViewGroup) {
            ((ViewGroup) rootView.getParent()).removeView(rootView);
        }
        rootView = null;
        logView = null;
        scrollView = null;
        activity = null;
    }

    private void updateLogs(String logs) {
        if (logView == null) return;
        logView.setText(TextUtils.isEmpty(logs) ? "暂无推送日志" : logs);
        if (scrollView != null) scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
    }

    @Override
    public void onLogsChanged(String logs) {
        if (activity != null) activity.runOnUiThread(() -> updateLogs(logs));
    }

    @Override
    public void onActivityResumed(Activity activity) {
        attach(activity);
    }

    @Override
    public void onActivityDestroyed(Activity destroyed) {
        if (activity == destroyed) detach();
    }

    @Override public void onActivityCreated(Activity activity, Bundle state) { }
    @Override public void onActivityStarted(Activity activity) { }
    @Override public void onActivityPaused(Activity activity) { }
    @Override public void onActivityStopped(Activity activity) { }
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) { }

    private static GradientDrawable rounded(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private final class DragTouchListener implements View.OnTouchListener {
        private final View.OnClickListener clickListener;
        private float downRawX;
        private float downRawY;
        private int startLeft;
        private int startTop;
        private boolean moved;

        DragTouchListener(View.OnClickListener clickListener) {
            this.clickListener = clickListener;
        }

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            if (rootView == null) return false;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downRawX = event.getRawX();
                    downRawY = event.getRawY();
                    FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) rootView.getLayoutParams();
                    startLeft = params.leftMargin;
                    startTop = params.topMargin;
                    moved = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    int dx = Math.round(event.getRawX() - downRawX);
                    int dy = Math.round(event.getRawY() - downRawY);
                    moved |= Math.abs(dx) > dp(view.getContext(), 4)
                            || Math.abs(dy) > dp(view.getContext(), 4);
                    View parent = (View) rootView.getParent();
                    int maxLeft = Math.max(0, parent.getWidth() - rootView.getWidth());
                    int maxTop = Math.max(0, parent.getHeight() - rootView.getHeight());
                    setPosition(Math.min(maxLeft, startLeft + dx), Math.min(maxTop, startTop + dy));
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!moved && clickListener != null) clickListener.onClick(view);
                    return true;
                default:
                    return false;
            }
        }
    }
}
