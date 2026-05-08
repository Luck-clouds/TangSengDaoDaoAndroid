package com.chat.uikit.setting;

import android.content.Context;
import android.content.Intent;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.widget.TextView;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.config.WKConfig;
import com.chat.base.config.WKSharedPreferencesUtil;
import com.chat.base.entity.UserInfoEntity;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.ui.Theme;
import com.chat.base.utils.WKCommonUtils;
import com.chat.base.utils.singleclick.SingleClickUtil;
import com.chat.uikit.R;
import com.chat.uikit.databinding.ActChatPwdSettingLayoutBinding;
import com.chat.uikit.user.service.UserModel;

/**
 * 聊天密码设置页。
 * 这里只维护账号级聊天密码，会话级开关仍然在单聊/群聊详情页控制。
 */
public class ChatPwdSettingActivity extends WKBaseActivity<ActChatPwdSettingLayoutBinding> {
    private boolean isSaving;

    public static Intent buildIntent(Context context) {
        return new Intent(context, ChatPwdSettingActivity.class);
    }

    @Override
    protected ActChatPwdSettingLayoutBinding getViewBinding() {
        return ActChatPwdSettingLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(R.string.chat_pwd_setting_title);
    }

    @Override
    protected void initView() {
        wkVBinding.saveBtn.getBackground().setTint(Theme.colorAccount);
        updateButtonStatus();
    }

    @Override
    protected void initListener() {
        TextWatcher textWatcher = new SimpleTextWatcher();
        wkVBinding.loginPwdEt.addTextChangedListener(textWatcher);
        wkVBinding.chatPwdEt.addTextChangedListener(textWatcher);
        wkVBinding.confirmChatPwdEt.addTextChangedListener(textWatcher);
        SingleClickUtil.onSingleClick(wkVBinding.saveBtn, v -> submit());
    }

    /**
     * 校验输入并提交聊天密码设置。
     */
    private void submit() {
        if (isSaving) {
            return;
        }
        String loginPwd = getInputValue(wkVBinding.loginPwdEt);
        String chatPwd = getInputValue(wkVBinding.chatPwdEt);
        String confirmPwd = getInputValue(wkVBinding.confirmChatPwdEt);
        if (TextUtils.isEmpty(loginPwd)) {
            showToast(R.string.chat_pwd_login_required);
            return;
        }
        if (!isValidChatPassword(chatPwd) || !isValidChatPassword(confirmPwd)) {
            showToast(R.string.chat_pwd_invalid);
            return;
        }
        if (!TextUtils.equals(chatPwd, confirmPwd)) {
            showToast(R.string.chat_pwd_not_match);
            return;
        }
        isSaving = true;
        wkVBinding.saveBtn.setEnabled(false);
        String loginPwdDigest = WKCommonUtils.digest(loginPwd);
        String chatPwdDigest = WKCommonUtils.digest(chatPwd);
        UserModel.getInstance().setChatPwd(loginPwdDigest, chatPwdDigest, (code, msg) -> {
            isSaving = false;
            wkVBinding.saveBtn.setEnabled(true);
            if (code != HttpResponseCode.success) {
                showToast(TextUtils.isEmpty(msg) ? getString(R.string.unknown_error) : msg);
                return;
            }
            saveLocalChatPassword(chatPwdDigest);
            showToast(R.string.chat_pwd_set_success);
            setResult(RESULT_OK);
            finish();
        });
    }

    private String getInputValue(TextView textView) {
        return textView.getText() == null ? "" : textView.getText().toString().trim();
    }

    private void updateButtonStatus() {
        boolean enabled = !TextUtils.isEmpty(getInputValue(wkVBinding.loginPwdEt))
                && !TextUtils.isEmpty(getInputValue(wkVBinding.chatPwdEt))
                && !TextUtils.isEmpty(getInputValue(wkVBinding.confirmChatPwdEt))
                && !isSaving;
        wkVBinding.saveBtn.setEnabled(enabled);
        wkVBinding.saveBtn.setAlpha(enabled ? 1f : 0.2f);
    }

    private boolean isValidChatPassword(String chatPwd) {
        return !TextUtils.isEmpty(chatPwd) && chatPwd.length() == 6 && TextUtils.isDigitsOnly(chatPwd);
    }

    /**
     * 设置成功后同步本地缓存，保证当前设备可以直接走本地校验。
     */
    private void saveLocalChatPassword(String chatPwdDigest) {
        UserInfoEntity userInfo = WKConfig.getInstance().getUserInfo();
        if (userInfo != null) {
            userInfo.chat_pwd = chatPwdDigest;
            WKConfig.getInstance().saveUserInfo(userInfo);
        }
        WKSharedPreferencesUtil.getInstance().putInt("wk_chat_pwd_count", 3);
    }

    /**
     * 复用登录密码页的按钮联动逻辑，输入框内容变化后实时刷新按钮状态。
     */
    private class SimpleTextWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            updateButtonStatus();
        }
    }
}
