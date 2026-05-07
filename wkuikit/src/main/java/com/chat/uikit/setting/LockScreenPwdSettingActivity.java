package com.chat.uikit.setting;

import android.content.Intent;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.TextView;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.config.WKConfig;
import com.chat.base.entity.BottomSheetItem;
import com.chat.base.entity.UserInfoEntity;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.ui.components.BottomSheet;
import com.chat.base.ui.Theme;
import com.chat.base.utils.AndroidUtilities;
import com.chat.base.utils.WKDialogUtils;
import com.chat.base.utils.singleclick.SingleClickUtil;
import com.chat.uikit.R;
import com.chat.uikit.databinding.ActLockScreenPwdSettingLayoutBinding;
import com.chat.uikit.user.service.UserModel;

public class LockScreenPwdSettingActivity extends WKBaseActivity<ActLockScreenPwdSettingLayoutBinding> {
    private static final int REQUEST_CHANGE_PASSWORD = 1001;
    private static final int[] DURATIONS = new int[]{0, 1, 5, 30, 60};

    private UserInfoEntity userInfo;

    @Override
    protected ActLockScreenPwdSettingLayoutBinding getViewBinding() {
        return ActLockScreenPwdSettingLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(R.string.security_lock_screen_password);
    }

    @Override
    protected void initPresenter() {
        userInfo = WKConfig.getInstance().getUserInfo();
    }

    @Override
    protected void initView() {
        refreshAutoLockText();
    }

    @Override
    protected void initListener() {
        SingleClickUtil.onSingleClick(wkVBinding.autoLockLayout, v -> showDurationDialog());
        SingleClickUtil.onSingleClick(wkVBinding.updatePwdLayout, v -> startActivityForResult(
                LockScreenPasswordActivity.buildIntent(this, LockScreenPasswordActivity.SCENE_CHANGE),
                REQUEST_CHANGE_PASSWORD));
        SingleClickUtil.onSingleClick(wkVBinding.closePwdLayout, v -> showClosePasswordDialog());
    }

    @Override
    protected void onResume() {
        super.onResume();
        userInfo = WKConfig.getInstance().getUserInfo();
        refreshAutoLockText();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CHANGE_PASSWORD && resultCode == RESULT_OK) {
            refreshAutoLockText();
        }
    }

    private void refreshAutoLockText() {
        wkVBinding.autoLockValueTv.setText(getDurationLabel(userInfo == null ? 0 : userInfo.lock_after_minute));
    }

    private void showDurationDialog() {
        java.util.List<BottomSheetItem> items = new java.util.ArrayList<>();
        for (int i = 0; i < DURATIONS.length; i++) {
            final int minute = DURATIONS[i];
            items.add(new BottomSheetItem(getDurationLabel(minute), 0, () -> updateLockAfterMinute(minute)));
        }
        CharSequence[] labels = new CharSequence[items.size()];
        int[] icons = new int[items.size()];
        for (int i = 0; i < items.size(); i++) {
            labels[i] = items.get(i).getText();
            icons[i] = items.get(i).getIcon();
        }
        BottomSheet.Builder builder = new BottomSheet.Builder(this, false);
        builder.setDimBehind(true);
        builder.setTitle(getString(R.string.security_auto_lock), false);
        builder.setItems(labels, icons, (dialog, which) -> items.get(which).getIClick().onClick());
        BottomSheet bottomSheet = builder.create();
        bottomSheet.show();
        bottomSheet.setCanceledOnTouchOutside(true);
        bottomSheet.setBackgroundColor(androidx.core.content.ContextCompat.getColor(this, R.color.screen_bg));
        int startPadding = AndroidUtilities.dp(AndroidUtilities.isRTL ? 16 : 34);
        int endPadding = AndroidUtilities.dp(AndroidUtilities.isRTL ? 34 : 16);
        for (BottomSheet.BottomSheetCell cell : bottomSheet.getItemViews()) {
            cell.getTextView().setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            cell.getTextView().setPadding(startPadding, 0, endPadding, 0);
        }
    }

    private void updateLockAfterMinute(int minute) {
        UserModel.getInstance().updateLockAfterMinute(minute, (code, msg) -> {
            if (code != HttpResponseCode.success) {
                showToast(TextUtils.isEmpty(msg) ? getString(R.string.unknown_error) : msg);
                return;
            }
            if (userInfo != null) {
                userInfo.lock_after_minute = minute;
                WKConfig.getInstance().saveUserInfo(userInfo);
            }
            refreshAutoLockText();
        });
    }

    private void showClosePasswordDialog() {
        WKDialogUtils.getInstance().showDialog(this, getString(R.string.security_close_lock_screen_password),
                getString(R.string.security_close_lock_screen_password_confirm), true,
                getString(R.string.cancel), getString(R.string.sure), 0, Theme.colorAccount, index -> {
                    if (index != 1) {
                        return;
                    }
                    UserModel.getInstance().deleteLockScreenPwd((code, msg) -> {
                        if (code != HttpResponseCode.success) {
                            showToast(TextUtils.isEmpty(msg) ? getString(R.string.unknown_error) : msg);
                            return;
                        }
                        SecurityPrivacyManager.getInstance().clearLocalLockPassword();
                        userInfo = WKConfig.getInstance().getUserInfo();
                        setResult(RESULT_OK);
                        finish();
                    });
                });
    }

    private String getDurationLabel(int minute) {
        if (minute <= 0) {
            return getString(R.string.security_lock_immediately);
        }
        if (minute == 1) {
            return getString(R.string.security_lock_after_one_minute);
        }
        if (minute == 60) {
            return getString(R.string.security_lock_after_one_hour);
        }
        return getString(R.string.security_lock_after_minutes, minute);
    }
}
