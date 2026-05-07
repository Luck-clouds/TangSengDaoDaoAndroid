package com.chat.uikit.setting;

import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.utils.WKTimeUtils;
import com.chat.uikit.R;
import com.chat.uikit.databinding.ActOfflineProtectionLayoutBinding;

import java.util.Locale;

public class OfflineProtectionActivity extends WKBaseActivity<ActOfflineProtectionLayoutBinding> {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private long startTimeSeconds;

    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            long elapsed = Math.max(0, WKTimeUtils.getInstance().getCurrentSeconds() - startTimeSeconds);
            wkVBinding.timerTv.setText(getString(R.string.security_disconnect_screen_timer, formatDuration(elapsed)));
            handler.postDelayed(this, 1000L);
        }
    };

    @Override
    protected ActOfflineProtectionLayoutBinding getViewBinding() {
        return ActOfflineProtectionLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected boolean supportSlideBack() {
        return false;
    }

    @Override
    protected void setTitle(TextView titleTv) {
    }

    @Override
    protected void initView() {
        SecurityPrivacyManager.getInstance().rememberOfflineActivity(this);
        startTimeSeconds = SecurityPrivacyManager.getInstance().getOfflineProtectionStartTimeSeconds();
        if (startTimeSeconds <= 0) {
            startTimeSeconds = WKTimeUtils.getInstance().getCurrentSeconds();
        }
        Drawable icon = getApplicationInfo().loadIcon(getPackageManager());
        wkVBinding.appIconIv.setImageDrawable(icon);
        CharSequence appName = getApplicationInfo().loadLabel(getPackageManager());
        wkVBinding.appNameTv.setText(appName);
        wkVBinding.bottomTipsTv.setText(getString(R.string.security_disconnect_screen_bottom_tips, appName));
        timerRunnable.run();
    }

    @Override
    protected void backListener(int type) {
    }

    @Override
    public void onBackPressed() {
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(timerRunnable);
        SecurityPrivacyManager.getInstance().forgetOfflineActivity(this);
        super.onDestroy();
    }

    private String formatDuration(long elapsedSeconds) {
        long hours = elapsedSeconds / 3600;
        long minutes = (elapsedSeconds % 3600) / 60;
        long seconds = elapsedSeconds % 60;
        if (hours > 0) {
            return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }
}
