package com.chat.uikit.setting;

import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.config.WKConfig;
import com.chat.base.entity.UserInfoEntity;
import com.chat.base.entity.UserInfoSetting;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.utils.AndroidUtilities;
import com.chat.uikit.R;
import com.chat.uikit.databinding.ActDeviceLockLayoutBinding;
import com.chat.uikit.enity.Device;
import com.chat.uikit.user.service.UserModel;

import java.util.List;

public class DeviceLockActivity extends WKBaseActivity<ActDeviceLockLayoutBinding> {
    private UserInfoEntity userInfoEntity;

    @Override
    protected ActDeviceLockLayoutBinding getViewBinding() {
        return ActDeviceLockLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(R.string.security_device_lock);
    }

    @Override
    protected void initPresenter() {
        userInfoEntity = WKConfig.getInstance().getUserInfo();
        if (userInfoEntity.setting == null) {
            userInfoEntity.setting = new UserInfoSetting();
        }
    }

    @Override
    protected void initView() {
        wkVBinding.deviceLockSwitch.setChecked(userInfoEntity.setting.device_lock == 1);
        updateDeviceListVisibility(userInfoEntity.setting.device_lock == 1);
    }

    @Override
    protected void initData() {
        super.initData();
        UserModel.getInstance().getMySetting((code, msg, setting) -> {
            if (code == HttpResponseCode.success && setting != null) {
                userInfoEntity.setting = setting;
                WKConfig.getInstance().saveUserInfo(userInfoEntity);
                wkVBinding.deviceLockSwitch.setChecked(userInfoEntity.setting.device_lock == 1);
                if (userInfoEntity.setting.device_lock == 1) {
                    loadDeviceList();
                } else {
                    clearDeviceList();
                }
            }
        });
    }

    @Override
    protected void initListener() {
        wkVBinding.deviceLockSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!buttonView.isPressed()) {
                return;
            }
            try {
                UserModel.getInstance().updateUserSetting("device_lock", isChecked ? 1 : 0, (code, msg) -> {
                    if (code == HttpResponseCode.success) {
                        userInfoEntity.setting.device_lock = isChecked ? 1 : 0;
                        WKConfig.getInstance().saveUserInfo(userInfoEntity);
                        if (isChecked) {
                            loadDeviceList();
                        } else {
                            clearDeviceList();
                        }
                    } else {
                        wkVBinding.deviceLockSwitch.setChecked(userInfoEntity.setting.device_lock == 1);
                        updateDeviceListVisibility(userInfoEntity.setting.device_lock == 1);
                        showToast(TextUtils.isEmpty(msg) ? getString(R.string.unknown_error) : msg);
                    }
                });
            } catch (Exception e) {
                wkVBinding.deviceLockSwitch.setChecked(userInfoEntity.setting.device_lock == 1);
                updateDeviceListVisibility(userInfoEntity.setting.device_lock == 1);
                showToast(R.string.unknown_error);
            }
        });
    }

    private void loadDeviceList() {
        updateDeviceListVisibility(true);
        try {
            UserModel.getInstance().getDevices((code, msg, list) -> {
                if (code == HttpResponseCode.success) {
                    renderDeviceList(list);
                } else {
                    renderDeviceList(null);
                    if (!TextUtils.isEmpty(msg)) {
                        showToast(msg);
                    }
                }
            });
        } catch (Exception e) {
            renderDeviceList(null);
            showToast(R.string.unknown_error);
        }
    }

    private void renderDeviceList(List<Device> list) {
        wkVBinding.deviceListLayout.removeAllViews();
        if (list == null || list.isEmpty()) {
            TextView emptyTv = buildDeviceNameText(getString(R.string.security_device_list_empty));
            wkVBinding.deviceListLayout.addView(emptyTv);
            return;
        }
        for (Device item : list) {
            if (item == null || TextUtils.isEmpty(item.device_name)) {
                continue;
            }
            wkVBinding.deviceListLayout.addView(buildDeviceNameText(item.device_name));
        }
        if (wkVBinding.deviceListLayout.getChildCount() == 0) {
            wkVBinding.deviceListLayout.addView(buildDeviceNameText(getString(R.string.security_device_list_empty)));
        }
    }

    private TextView buildDeviceNameText(String text) {
        TextView textView = new TextView(this);
        textView.setText(text);
        textView.setTextColor(ContextCompat.getColor(this, R.color.colorDark));
        textView.setTextSize(15);
        textView.setGravity(Gravity.CENTER);
        textView.setPadding(AndroidUtilities.dp(15), AndroidUtilities.dp(14), AndroidUtilities.dp(15), AndroidUtilities.dp(14));
        textView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        return textView;
    }

    private void clearDeviceList() {
        wkVBinding.deviceListLayout.removeAllViews();
        updateDeviceListVisibility(false);
    }

    private void updateDeviceListVisibility(boolean isVisible) {
        int visibility = isVisible ? View.VISIBLE : View.GONE;
        wkVBinding.deviceListTitleTv.setVisibility(visibility);
        wkVBinding.deviceListContainer.setVisibility(visibility);
    }
}
