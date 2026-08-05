package com.chat.uikit.setting;

import android.content.Intent;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.config.WKConfig;
import com.chat.base.entity.UserInfoEntity;
import com.chat.base.entity.UserInfoSetting;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.utils.singleclick.SingleClickUtil;
import com.chat.uikit.R;
import com.chat.uikit.databinding.ActSecurityPrivacyLayoutBinding;
import com.chat.uikit.user.service.UserModel;

public class SecurityPrivacyActivity extends WKBaseActivity<ActSecurityPrivacyLayoutBinding> {
    private UserInfoEntity userInfoEntity;

    @Override
    protected ActSecurityPrivacyLayoutBinding getViewBinding() {
        return ActSecurityPrivacyLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(R.string.security_privacy);
    }

    @Override
    protected void initPresenter() {
        userInfoEntity = WKConfig.getInstance().getUserInfo();
        if (userInfoEntity.setting == null) {
            userInfoEntity.setting = new UserInfoSetting();
        }
        EndpointManager.getInstance().setMethod("refresh_security_privacy_config", object -> {
            renderSetting();
            return null;
        });
    }

    @Override
    protected void initView() {
        wkVBinding.refreshLayout.setEnableOverScrollDrag(true);
        wkVBinding.refreshLayout.setEnableLoadMore(false);
        wkVBinding.refreshLayout.setEnableRefresh(false);
        wkVBinding.chatPwdLayout.setVisibility(View.VISIBLE);
        renderSetting();
    }

    @Override
    protected void initData() {
        super.initData();
        refreshRemoteSetting();
    }

    @Override
    protected void initListener() {
        wkVBinding.searchByPhoneSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!buttonView.isPressed()) {
                return;
            }
            updateSearchSetting("search_by_phone", isChecked, () -> userInfoEntity.setting.search_by_phone = isChecked ? 1 : 0,
                    () -> wkVBinding.searchByPhoneSwitch.setChecked(userInfoEntity.setting.search_by_phone == 1));
        });
        wkVBinding.searchByShortSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!buttonView.isPressed()) {
                return;
            }
            updateSearchSetting("search_by_short", isChecked, () -> userInfoEntity.setting.search_by_short = isChecked ? 1 : 0,
                    () -> wkVBinding.searchByShortSwitch.setChecked(userInfoEntity.setting.search_by_short == 1));
        });
        wkVBinding.offlineProtectionSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!buttonView.isPressed()) {
                return;
            }
            updateSearchSetting("offline_protection", isChecked, () -> {
                        userInfoEntity.setting.offline_protection = isChecked ? 1 : 0;
                        SecurityPrivacyManager.getInstance().refreshProtectionState();
                    },
                    () -> wkVBinding.offlineProtectionSwitch.setChecked(userInfoEntity.setting.offline_protection == 1));
        });
        SingleClickUtil.onSingleClick(wkVBinding.loginPwdLayout, v -> {
            try {
                EndpointManager.getInstance().invoke("chow_reset_login_pwd_view", null);
            } catch (Exception e) {
                showToast(R.string.security_not_open);
            }
        });
        SingleClickUtil.onSingleClick(wkVBinding.chatPwdLayout, v -> {
            startActivity(ChatPwdSettingActivity.buildIntent(this));
        });
        SingleClickUtil.onSingleClick(wkVBinding.lockScreenPwdLayout, v -> {
            if (TextUtils.isEmpty(userInfoEntity.lock_screen_pwd)) {
                startActivity(LockScreenPasswordActivity.buildIntent(this, LockScreenPasswordActivity.SCENE_CREATE));
            } else {
                startActivity(LockScreenPasswordActivity.buildIntent(this, LockScreenPasswordActivity.SCENE_VERIFY_SETTINGS));
            }
        });
        SingleClickUtil.onSingleClick(wkVBinding.deviceLockLayout, v -> startActivity(new Intent(this, DeviceLockActivity.class)));
        SingleClickUtil.onSingleClick(wkVBinding.blacklistLayout, v -> startActivity(new Intent(this, BlacklistActivity.class)));
        SingleClickUtil.onSingleClick(wkVBinding.destroyAccountLayout, v -> startActivity(new Intent(this, DestroyAccountActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        userInfoEntity = WKConfig.getInstance().getUserInfo();
        if (userInfoEntity.setting == null) {
            userInfoEntity.setting = new UserInfoSetting();
        }
        renderSetting();
    }

    private void renderSetting() {
        boolean showOfflineProtection = WKConfig.getInstance().getAppConfig().isOfflineProtectionVisible();
        wkVBinding.offlineProtectionLayout.setVisibility(showOfflineProtection ? View.VISIBLE : View.GONE);
        wkVBinding.searchByPhoneSwitch.setChecked(userInfoEntity.setting.search_by_phone == 1);
        wkVBinding.searchByShortSwitch.setChecked(userInfoEntity.setting.search_by_short == 1);
        wkVBinding.offlineProtectionSwitch.setChecked(userInfoEntity.setting.offline_protection == 1);
        wkVBinding.deviceLockStatusTv.setText(userInfoEntity.setting.device_lock == 1 ? R.string.enabled : R.string.disabled);
    }

    private void refreshRemoteSetting() {
        try {
            UserModel.getInstance().getMySetting((code, msg, setting) -> {
                if (code != HttpResponseCode.success || setting == null) {
                    return;
                }
                userInfoEntity.setting = setting;
                WKConfig.getInstance().saveUserInfo(userInfoEntity);
                renderSetting();
                SecurityPrivacyManager.getInstance().refreshProtectionState();
            });
        } catch (Exception e) {
            showToast(R.string.unknown_error);
        }
    }

    private void updateSearchSetting(String key, boolean isChecked, Runnable successAction, Runnable rollbackAction) {
        try {
            UserModel.getInstance().updateUserSetting(key, isChecked ? 1 : 0, (code, msg) -> {
                if (code == HttpResponseCode.success) {
                    successAction.run();
                    WKConfig.getInstance().saveUserInfo(userInfoEntity);
                    renderSetting();
                } else {
                    rollbackAction.run();
                    showToast(TextUtils.isEmpty(msg) ? getString(R.string.unknown_error) : msg);
                }
            });
        } catch (Exception e) {
            rollbackAction.run();
            showToast(R.string.unknown_error);
        }
    }

    @Override
    protected void onDestroy() {
        EndpointManager.getInstance().remove("refresh_security_privacy_config");
        super.onDestroy();
    }

}
