package com.chat.uikit.setting;

import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.net.HttpResponseCode;
import com.chat.uikit.R;
import com.chat.uikit.WKUIKitApplication;
import com.chat.uikit.databinding.ActDestroyAccountVerifyLayoutBinding;
import com.chat.uikit.user.service.UserModel;

public class DestroyAccountVerifyActivity extends WKBaseActivity<ActDestroyAccountVerifyLayoutBinding> {
    private CountDownTimer countDownTimer;

    @Override
    protected ActDestroyAccountVerifyLayoutBinding getViewBinding() {
        return ActDestroyAccountVerifyLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(R.string.security_input_verify_code);
    }

    @Override
    protected String getRightTvText(TextView textView) {
        return getString(R.string.sure);
    }

    @Override
    protected void initView() {
        updateConfirmState();
    }

    @Override
    protected void initData() {
        super.initData();
        sendDestroySms();
    }

    @Override
    protected void initListener() {
        wkVBinding.codeEt.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                updateConfirmState();
            }
        });
        wkVBinding.resendCodeTv.setOnClickListener(v -> {
            if (v.isEnabled()) {
                sendDestroySms();
            }
        });
    }

    @Override
    protected void rightLayoutClick() {
        super.rightLayoutClick();
        submitDestroy();
    }

    private void updateConfirmState() {
        boolean enable = !TextUtils.isEmpty(wkVBinding.codeEt.getText());
        setRightViewEnabled(enable);
        TextView rightTv = findViewById(com.chat.base.R.id.titleRightTv);
        if (rightTv != null) {
            rightTv.setAlpha(enable ? 1f : 0.35f);
            rightTv.setTextColor(ContextCompat.getColor(this, R.color.popupTextColor));
        }
    }

    private void sendDestroySms() {
        try {
            UserModel.getInstance().sendDestroySms((code, msg) -> {
                if (code == HttpResponseCode.success) {
                    startCountDown();
                } else if (!TextUtils.isEmpty(msg)) {
                    showToast(msg);
                }
            });
        } catch (Exception e) {
            showToast(R.string.unknown_error);
        }
    }

    private void submitDestroy() {
        String codeValue = wkVBinding.codeEt.getText().toString().trim();
        if (TextUtils.isEmpty(codeValue)) {
            return;
        }
        try {
            loadingPopup.show();
            UserModel.getInstance().destroyAccount(codeValue, (code, msg) -> {
                loadingPopup.dismiss();
                if (code == HttpResponseCode.success) {
                    showToast(R.string.security_destroy_success);
                    new Handler(Looper.getMainLooper()).postDelayed(() -> WKUIKitApplication.getInstance().exitLogin(0), 300);
                } else {
                    showToast(TextUtils.isEmpty(msg) ? getString(R.string.unknown_error) : msg);
                }
            });
        } catch (Exception e) {
            loadingPopup.dismiss();
            showToast(R.string.unknown_error);
        }
    }

    private void startCountDown() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        wkVBinding.resendCodeTv.setEnabled(false);
        countDownTimer = new CountDownTimer(60000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                long seconds = Math.max(1, millisUntilFinished / 1000);
                wkVBinding.resendCodeTv.setText(getString(R.string.security_resend_code_countdown, seconds));
                wkVBinding.resendCodeTv.setAlpha(0.75f);
            }

            @Override
            public void onFinish() {
                wkVBinding.resendCodeTv.setEnabled(true);
                wkVBinding.resendCodeTv.setAlpha(1f);
                wkVBinding.resendCodeTv.setText(R.string.security_resend_code);
            }
        };
        countDownTimer.start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }
    }
}
