package com.chat.uikit.setting;

import android.content.Intent;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.config.WKApiConfig;
import com.chat.base.config.WKConfig;
import com.chat.uikit.R;
import com.chat.uikit.databinding.ActDestroyAccountLayoutBinding;

public class DestroyAccountActivity extends WKBaseActivity<ActDestroyAccountLayoutBinding> {
    @Override
    protected ActDestroyAccountLayoutBinding getViewBinding() {
        return ActDestroyAccountLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(R.string.security_destroy_account);
    }

    @Override
    protected void initView() {
        String phone = WKConfig.getInstance().getUserInfo().phone;
        String maskPhone = maskPhone(phone);
        String content = getString(R.string.security_destroy_account_target, TextUtils.isEmpty(maskPhone) ? "--" : maskPhone);
        SpannableString spannableString = new SpannableString(content);
        int start = content.lastIndexOf(maskPhone);
        if (start >= 0 && !TextUtils.isEmpty(maskPhone)) {
            spannableString.setSpan(new android.text.style.ForegroundColorSpan(ContextCompat.getColor(this, R.color.red)), start, start + maskPhone.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        wkVBinding.accountTv.setText(spannableString);
        setupAgreementText();
    }

    @Override
    protected void initListener() {
        wkVBinding.cancelBtn.setOnClickListener(v -> finish());
        wkVBinding.destroyBtn.setOnClickListener(v -> {
            try {
                startActivity(new Intent(this, DestroyAccountVerifyActivity.class));
            } catch (Exception e) {
                showToast(R.string.unknown_error);
            }
        });
    }

    private void setupAgreementText() {
        String protocolName = getString(com.chat.base.R.string.kit_user_agreement);
        String content = getString(R.string.security_destroy_agreement_tip, protocolName);
        SpannableString spannableString = new SpannableString(content);
        int start = content.indexOf(protocolName);
        if (start >= 0) {
            spannableString.setSpan(new ClickableSpan() {
                @Override
                public void onClick(@NonNull View widget) {
                    showWebView(WKApiConfig.baseWebUrl + "user_agreement.html");
                }
            }, start, start + protocolName.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            spannableString.setSpan(new android.text.style.ForegroundColorSpan(ContextCompat.getColor(this, R.color.blue)), start, start + protocolName.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        wkVBinding.agreementTv.setMovementMethod(LinkMovementMethod.getInstance());
        wkVBinding.agreementTv.setHighlightColor(ContextCompat.getColor(this, android.R.color.transparent));
        wkVBinding.agreementTv.setText(spannableString);
    }

    private String maskPhone(String phone) {
        if (TextUtils.isEmpty(phone)) {
            return "";
        }
        if (phone.length() <= 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
