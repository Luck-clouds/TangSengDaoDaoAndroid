package com.chat.uikit.setting;

import android.content.Intent;
import android.widget.TextView;

import com.chat.base.base.WKBaseActivity;
import com.chat.uikit.databinding.ActTeenModeLayoutBinding;

public class TeenModeActivity extends WKBaseActivity<ActTeenModeLayoutBinding> {
    private static final int REQUEST_ENABLE = 1001;
    private static final int REQUEST_DISABLE = 1002;

    @Override
    protected ActTeenModeLayoutBinding getViewBinding() {
        return ActTeenModeLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(com.chat.uikit.R.string.teen_mode);
    }

    @Override
    protected void initView() {
        refreshSwitchState();
    }

    @Override
    protected void initListener() {
        wkVBinding.teenModeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!buttonView.isPressed()) {
                return;
            }
            if (isChecked) {
                startActivityForResult(TeenModePasswordActivity.buildIntent(this, TeenModePasswordActivity.SCENE_CREATE), REQUEST_ENABLE);
            } else {
                startActivityForResult(TeenModePasswordActivity.buildIntent(this, TeenModePasswordActivity.SCENE_VERIFY_DISABLE), REQUEST_DISABLE);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshSwitchState();
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        refreshSwitchState();
    }

    private void refreshSwitchState() {
        wkVBinding.teenModeSwitch.setChecked(TeenModeManager.getInstance().isEnabled());
    }
}
