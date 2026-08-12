package com.chat.base.utils;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.chat.base.R;

import java.util.WeakHashMap;

/** Displays a non-blocking explanation while the system permission window is visible. */
final class PermissionNotice {
    private static final WeakHashMap<Activity, View> NOTICES = new WeakHashMap<>();

    private PermissionNotice() {
    }

    static void show(Activity activity, CharSequence purpose) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        dismissImmediately(activity);

        ViewGroup content = activity.findViewById(android.R.id.content);
        if (content == null) return;

        View notice = LayoutInflater.from(activity).inflate(R.layout.view_permission_notice, content, false);
        TextView purposeView = notice.findViewById(R.id.permissionPurposeTv);
        purposeView.setText(purpose);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        int horizontalMargin = AndroidUtilities.dp(12);
        params.setMargins(horizontalMargin, AndroidUtilities.dp(24), horizontalMargin, 0);
        notice.setLayoutParams(params);
        notice.setClickable(false);
        notice.setFocusable(false);
        notice.setAlpha(0f);
        notice.setTranslationY(-AndroidUtilities.dp(16));
        content.addView(notice);
        NOTICES.put(activity, notice);
        notice.animate().alpha(1f).translationY(0f).setDuration(180L).start();
        notice.announceForAccessibility(activity.getString(R.string.authorization_request) + "，" + purpose);
    }

    static void dismiss(Activity activity) {
        if (activity == null) return;
        View notice = NOTICES.remove(activity);
        if (notice == null) return;
        notice.animate()
                .alpha(0f)
                .translationY(-AndroidUtilities.dp(12))
                .setDuration(150L)
                .withEndAction(() -> removeFromParent(notice))
                .start();
    }

    private static void dismissImmediately(Activity activity) {
        View previous = NOTICES.remove(activity);
        if (previous != null) {
            previous.animate().cancel();
            removeFromParent(previous);
        }
    }

    private static void removeFromParent(View view) {
        if (view.getParent() instanceof ViewGroup) {
            ((ViewGroup) view.getParent()).removeView(view);
        }
    }
}
