package com.chat.login.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import androidx.core.content.ContextCompat;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.common.WKCommonModel;
import com.chat.base.config.WKApiConfig;
import com.chat.base.config.WKConfig;
import com.chat.base.config.WKSharedPreferencesUtil;
import com.chat.base.endpoint.EndpointCategory;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.endpoint.entity.LoginMenu;
import com.chat.base.endpoint.entity.OtherLoginResultMenu;
import com.chat.base.entity.UserInfoEntity;
import com.chat.base.entity.WKAPPConfig;
import com.chat.base.ui.Theme;
import com.chat.base.utils.AndroidUtilities;
import com.chat.base.utils.SoftKeyboardUtils;
import com.chat.base.utils.WKDialogUtils;
import com.chat.base.utils.WKReader;
import com.chat.base.utils.singleclick.SingleClickUtil;
import com.chat.login.R;
import com.chat.login.databinding.ActLoginLayoutBinding;
import com.chat.login.entity.CountryCodeEntity;
import com.chat.login.service.LoginContract;
import com.chat.login.service.LoginPresenter;

import java.util.List;
import java.util.Objects;

/**
 * 2020-02-26 15:55
 * 登录
 */
public class WKLoginActivity extends WKBaseActivity<ActLoginLayoutBinding> implements LoginContract.LoginView {
    private WKAPPConfig wkappConfig;
    private final String code = "0086";
    private LoginPresenter loginPresenter;

    @Override
    protected ActLoginLayoutBinding getViewBinding() {
        return ActLoginLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initPresenter() {
        loginPresenter = new LoginPresenter(this);
    }

    @Override
    protected void initView() {
        wkVBinding.loginBtn.getBackground().setTint(Theme.colorAccount);
        wkVBinding.privacyPolicyTv.setTextColor(Theme.colorAccount);
        wkVBinding.userAgreementTv.setTextColor(Theme.colorAccount);
        wkVBinding.registerTv.setTextColor(Theme.colorAccount);
        wkVBinding.forgetPwdTv.setTextColor(Theme.colorAccount);
        wkVBinding.checkbox.setResId(getContext(), R.mipmap.round_check2);
        wkVBinding.checkbox.setDrawBackground(true);
        wkVBinding.checkbox.setHasBorder(true);
        wkVBinding.checkbox.setStrokeWidth(AndroidUtilities.dp(1));
        wkVBinding.checkbox.setBorderColor(ContextCompat.getColor(getContext(), R.color.color999));
        wkVBinding.checkbox.setSize(18);
        wkVBinding.checkbox.setColor(Theme.colorAccount, ContextCompat.getColor(getContext(), R.color.white));
        wkVBinding.checkbox.setVisibility(View.VISIBLE);
        wkVBinding.checkbox.setEnabled(true);
        wkVBinding.checkbox.setChecked(false, true);
        int from = getIntent().getIntExtra("from", 0);
        if (from == 1 || from == 2) {
            String content = getString(R.string.wk_ban);
            if (from == 1) {
                content = getString(R.string.other_device_login);
            }
            WKDialogUtils.getInstance().showSingleBtnDialog(this, "", content, getString(R.string.sure), index -> {
            });
        }
        UserInfoEntity userInfoEntity = WKConfig.getInstance().getUserInfo();
        if (userInfoEntity != null) {
            if (!TextUtils.isEmpty(userInfoEntity.phone)) {
                wkVBinding.nameEt.setText(userInfoEntity.phone);
                wkVBinding.nameEt.setSelection(userInfoEntity.phone.length());

            }
        }
        wkVBinding.loginTitleTv.setText(String.format(getString(R.string.login_title), getString(R.string.app_name)));
        wkVBinding.privacyPolicyTv.setOnClickListener(v -> showWebView(WKApiConfig.baseWebUrl + "privacy_policy.html"));
        wkVBinding.userAgreementTv.setOnClickListener(v -> showWebView(WKApiConfig.baseWebUrl + "user_agreement.html"));
        //  EndpointManager.getInstance().invoke("other_login_view", new OtherLoginViewMenu(this, wkVBinding.otherView));
    }

    @Override
    public boolean supportSlideBack() {
        return false;
    }

    @Override
    protected void initListener() {
        wkVBinding.myTv.setOnClickListener(view1 -> wkVBinding.checkbox.setChecked(!wkVBinding.checkbox.isChecked(), true));
        wkVBinding.checkbox.setOnClickListener(view1 -> wkVBinding.checkbox.setChecked(!wkVBinding.checkbox.isChecked(), true));

        wkVBinding.checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                wkVBinding.pwdEt.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
            } else {
                wkVBinding.pwdEt.setTransformationMethod(PasswordTransformationMethod.getInstance());
            }
            wkVBinding.pwdEt.setSelection(Objects.requireNonNull(wkVBinding.pwdEt.getText()).length());
        });
        wkVBinding.loginBtn.setOnClickListener(v -> {
            if (checkEditInputIsEmpty(wkVBinding.nameEt, R.string.name_not_null)) return;
            if (checkEditInputIsEmpty(wkVBinding.pwdEt, R.string.pwd_not_null)) return;
            if (code.equals("0086") && Objects.requireNonNull(wkVBinding.nameEt.getText()).toString().length() != 11) {
                showSingleBtnDialog(getString(R.string.phone_error));
                return;
            }
            if (!wkVBinding.checkbox.isChecked()) {
                showSingleBtnDialog(getString(R.string.agree_auth_tips));
                return;
            }
            if (Objects.requireNonNull(wkVBinding.pwdEt.getText()).toString().length() < 6 || wkVBinding.pwdEt.getText().toString().length() > 16) {
                showSingleBtnDialog(getString(R.string.pwd_length_error));
                return;
            }
            loadingPopup.show();
            loadingPopup.setTitle(getString(R.string.logging_in));
            String name = Objects.requireNonNull(wkVBinding.nameEt.getText()).toString();
            loginPresenter.login(code + name, wkVBinding.pwdEt.getText().toString());
        });
        SingleClickUtil.onSingleClick(wkVBinding.registerTv, v -> startActivity(new Intent(this, WKRegisterActivity.class)));
        // 登录仅支持中国大陆手机号，区号固定为 +86，不开放地区选择入口。
        wkVBinding.chooseCodeTv.setEnabled(false);
        wkVBinding.chooseCodeTv.setClickable(false);
        SingleClickUtil.onSingleClick(wkVBinding.forgetPwdTv, v -> {
            Intent intent = new Intent(this, WKResetLoginPwdActivity.class);
            intent.putExtra("canEditPhone", true);
            startActivity(intent);
        });

        EndpointManager.getInstance().setMethod("other_login_result", object -> {
            OtherLoginResultMenu menu = (OtherLoginResultMenu) object;
            if (menu.getCode() == 0) {
                loginResult(menu.getUserInfoEntity());
            } else {
                setLoginFail(menu.getCode(), menu.getUserInfoEntity().uid, menu.getUserInfoEntity().phone);
            }
            return null;
        });
        wkVBinding.baseUrlTv.setOnClickListener(v -> {
            if (wkappConfig == null || wkappConfig.can_modify_api_url == 0) {
                return;
            }
            String url = WKSharedPreferencesUtil.getInstance().getSP("api_base_url", "");
            WKDialogUtils.getInstance().showInputDialog(this, getString(R.string.update_api), getString(R.string.update_api_content), url, getString(R.string.update_api_ip), 100, text -> {
                if (!TextUtils.isEmpty(text)) {
                    if (!text.toLowerCase().startsWith("http")) {
                        text = "http://" + text;
                    }
                    WKSharedPreferencesUtil.getInstance().putSP("api_base_url", text);
                    showBaseUrl();
                    EndpointManager.getInstance().invoke("update_base_url", text);
                }
            });
        });
        wkVBinding.resetTv.setOnClickListener(view -> {
            WKSharedPreferencesUtil.getInstance().putSP("api_base_url", "");
            EndpointManager.getInstance().invoke("update_base_url", "");
        });
        showBaseUrl();
    }

    @Override
    protected void initData() {
        super.initData();
        WKCommonModel.getInstance().getAppConfig((code, msg, wkappConfig) -> {
            this.wkappConfig = wkappConfig;
            if (wkappConfig != null && wkappConfig.can_modify_api_url == 1) {
                wkVBinding.settingLayout.setVisibility(View.VISIBLE);
            }
        });
    }

    private void showBaseUrl() {
        String apiURL = WKSharedPreferencesUtil.getInstance().getSP("api_base_url");
        if (!TextUtils.isEmpty(apiURL)) {
            wkVBinding.baseUrlTv.setText(apiURL);
            wkVBinding.resetTv.setVisibility(View.VISIBLE);
        } else {
            wkVBinding.baseUrlTv.setText(R.string.update_api);
            wkVBinding.resetTv.setVisibility(View.GONE);
        }
    }

    @Override
    public void loginResult(UserInfoEntity userInfoEntity) {
        SoftKeyboardUtils.getInstance().hideInput(this, wkVBinding.pwdEt);

        if (TextUtils.isEmpty(userInfoEntity.name)) {
            Intent intent = new Intent(this, PerfectUserInfoActivity.class);
            startActivity(intent);
            finish();
        } else {
            hideLoading();
            new Handler(Objects.requireNonNull(Looper.myLooper())).postDelayed(() -> {
                List<LoginMenu> list = EndpointManager.getInstance().invokes(EndpointCategory.loginMenus, null);
                if (WKReader.isNotEmpty(list)) {
                    for (LoginMenu menu : list) {
                        if (menu.iMenuClick != null) menu.iMenuClick.onClick();
                    }
                }
                finish();
            }, 200);
        }
    }

    @Override
    public void setCountryCode(List<CountryCodeEntity> list) {

    }

    @Override
    public void setRegisterCodeSuccess(int code, String msg, int exist) {

    }

    @Override
    public void setLoginFail(int code, String uid, String phone) {
        Intent intent = new Intent(this, LoginAuthActivity.class);
        intent.putExtra("phone", phone);
        intent.putExtra("uid", uid);
        startActivity(intent);
    }

    @Override
    public void setSendCodeResult(int code, String msg) {

    }

    @Override
    public void setResetPwdResult(int code, String msg) {

    }

    @Override
    public Button getVerificationCodeBtn() {
        return null;
    }

    @Override
    public EditText getNameEt() {
        return null;
    }

    @Override
    public void showError(String msg) {
        showSingleBtnDialog(msg);
    }

    @Override
    public void hideLoading() {
        loadingPopup.dismiss();
    }


    @Override
    public Context getContext() {
        return this;
    }

    @Override
    public void finish() {
        super.finish();
        EndpointManager.getInstance().remove("other_login_result");
    }
}
