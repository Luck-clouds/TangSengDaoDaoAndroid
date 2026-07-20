package com.chat.login.ui;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.widget.Button;
import android.widget.EditText;

import androidx.core.content.ContextCompat;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.config.WKConfig;
import com.chat.base.entity.UserInfoEntity;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.ui.Theme;
import com.chat.login.R;
import com.chat.login.databinding.ActResetLoginPwdLayoutBinding;
import com.chat.login.entity.CountryCodeEntity;
import com.chat.login.service.LoginContract;
import com.chat.login.service.LoginPresenter;

import java.util.List;
import java.util.Objects;

/**
 * 2020-11-25 11:21
 * 重置登录密码
 */
public class WKResetLoginPwdActivity extends WKBaseActivity<ActResetLoginPwdLayoutBinding> implements LoginContract.LoginView {

    private final String code = "0086";
    private LoginPresenter presenter;

    @Override
    protected ActResetLoginPwdLayoutBinding getViewBinding() {
        return ActResetLoginPwdLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initPresenter() {
        presenter = new LoginPresenter(this);
    }

    @Override
    protected void initView() {
        wkVBinding.sureBtn.getBackground().setTint(Theme.colorAccount);
        wkVBinding.getVerCodeBtn.getBackground().setTint(Theme.colorAccount);
        Theme.setPressedBackground(wkVBinding.backIv);
        wkVBinding.backIv.setColorFilter(new PorterDuffColorFilter(ContextCompat.getColor(this, R.color.colorDark), PorterDuff.Mode.MULTIPLY));
        boolean canEditPhone = getIntent().getBooleanExtra("canEditPhone", false);
        wkVBinding.nameEt.setEnabled(canEditPhone);
        wkVBinding.nameEt.setText(WKConfig.getInstance().getUserInfo().phone);
        if (!canEditPhone || !TextUtils.isEmpty(Objects.requireNonNull(wkVBinding.nameEt.getText()).toString())) {
            wkVBinding.getVerCodeBtn.setEnabled(true);
            wkVBinding.getVerCodeBtn.setAlpha(1);
        }

        wkVBinding.resetLoginPwdTv.setText(String.format(getString(R.string.auth_phone_tips), getString(R.string.app_name)));
    }

    @Override
    protected void initListener() {
        wkVBinding.nameEt.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
                if (editable.length() > 0) {
                    wkVBinding.getVerCodeBtn.setEnabled(true);
                    wkVBinding.getVerCodeBtn.setAlpha(1f);
                } else {
                    wkVBinding.getVerCodeBtn.setEnabled(false);
                    wkVBinding.getVerCodeBtn.setAlpha(0.2f);
                }
                checkStatus();
            }
        });
        wkVBinding.verfiEt.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
                checkStatus();
            }
        });
        wkVBinding.pwdEt.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
                checkStatus();
            }
        });
        wkVBinding.checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                wkVBinding.pwdEt.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
            } else {
                wkVBinding.pwdEt.setTransformationMethod(PasswordTransformationMethod.getInstance());
            }
            wkVBinding.pwdEt.setSelection(Objects.requireNonNull(wkVBinding.pwdEt.getText()).length());
        });
        // 重置密码仅支持中国大陆手机号，区号固定为 +86，不开放地区选择入口。
        wkVBinding.chooseCodeTv.setEnabled(false);
        wkVBinding.chooseCodeTv.setClickable(false);
        wkVBinding.sureBtn.setOnClickListener(v -> {

            String phone = Objects.requireNonNull(wkVBinding.nameEt.getText()).toString();
            String verCode = wkVBinding.verfiEt.getText().toString();
            String pwd = wkVBinding.pwdEt.getText().toString();
            if (!TextUtils.isEmpty(phone) && !TextUtils.isEmpty(verCode) && !TextUtils.isEmpty(pwd)) {
                if (pwd.length() < 6 || pwd.length() > 16) {
                    showToast(R.string.pwd_length_error);
                } else {
                    loadingPopup.show();
                    presenter.resetPwd(code, phone, verCode, pwd);
                }
            }

        });
        wkVBinding.getVerCodeBtn.setOnClickListener(v -> {
            String phone = wkVBinding.nameEt.getText().toString();
            if (!TextUtils.isEmpty(phone)) {
                presenter.forgetPwd(code, phone);
            }
        });
        wkVBinding.backIv.setOnClickListener(v -> finish());
    }


    private void checkStatus() {
        String phone = wkVBinding.nameEt.getText().toString();
        String verCode = wkVBinding.verfiEt.getText().toString();
        String pwd = wkVBinding.pwdEt.getText().toString();
        if (!TextUtils.isEmpty(phone) && !TextUtils.isEmpty(verCode) && !TextUtils.isEmpty(pwd)) {
            wkVBinding.sureBtn.setAlpha(1f);
            wkVBinding.sureBtn.setEnabled(true);
        } else {
            wkVBinding.sureBtn.setAlpha(0.2f);
            wkVBinding.sureBtn.setEnabled(false);
        }
    }

    @Override
    public void loginResult(UserInfoEntity userInfoEntity) {

    }

    @Override
    public void setCountryCode(List<CountryCodeEntity> list) {

    }

    @Override
    public void setRegisterCodeSuccess(int code, String msg, int exist) {

    }

    @Override
    public void setLoginFail(int code, String uid, String phone) {

    }

    @Override
    public void setSendCodeResult(int code, String msg) {
        if (code == HttpResponseCode.success) {
            presenter.startTimer();
        } else {
            showToast(msg);
        }
    }

    @Override
    public void setResetPwdResult(int code, String msg) {
        if (code == HttpResponseCode.success) {
            finish();
        }
    }

    @Override
    public Button getVerificationCodeBtn() {
        return wkVBinding.getVerCodeBtn;

    }

    @Override
    public EditText getNameEt() {
        return wkVBinding.nameEt;
    }

    @Override
    public Context getContext() {
        return this;
    }

    @Override
    public void showError(String msg) {
        showToast(msg);
    }

    @Override
    public void hideLoading() {
        loadingPopup.dismiss();
    }
}
