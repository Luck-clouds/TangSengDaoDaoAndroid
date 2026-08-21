package com.chat.uikit.setting;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.chat.base.base.WKBaseActivity;
import com.chat.base.config.WKApiConfig;
import com.chat.base.config.WKConfig;
import com.chat.base.entity.WKAPPConfig;
import com.chat.base.utils.WKDialogUtils;
import com.chat.base.utils.singleclick.SingleClickUtil;
import com.chat.uikit.R;
import com.chat.uikit.databinding.ActContactUsLayoutBinding;

import java.util.ArrayList;
import java.util.Collections;

/**
 * 联系我们
 */
public class ContactUsActivity extends WKBaseActivity<ActContactUsLayoutBinding> {
    private String qrCodeUrl = "";
    private String wecomTitle = "";
    private String wecomTips = "";
    private String email = "";
    private String phone = "";

    @Override
    protected ActContactUsLayoutBinding getViewBinding() {
        return ActContactUsLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(R.string.contact_us);
    }

    @Override
    protected void initPresenter() {
        WKAPPConfig config = WKConfig.getInstance().getAppConfig();
        String qrCode = trim(config.contact_wecom_qrcode);
        qrCodeUrl = TextUtils.isEmpty(qrCode) ? "" : WKApiConfig.getShowUrl(qrCode);
        wecomTitle = trim(config.contact_wecom_title);
        wecomTips = trim(config.contact_wecom_tips);
        email = trim(config.contact_email);
        phone = trim(config.contact_phone);
    }

    @Override
    protected void initView() {
        boolean hasQrCode = !TextUtils.isEmpty(qrCodeUrl);
        boolean hasWecom = hasQrCode || !TextUtils.isEmpty(wecomTitle) || !TextUtils.isEmpty(wecomTips);
        boolean hasOtherContact = !TextUtils.isEmpty(email) || !TextUtils.isEmpty(phone);
        wkVBinding.wecomLayout.setVisibility(hasWecom ? View.VISIBLE : View.GONE);
        wkVBinding.wecomTitleTv.setVisibility(TextUtils.isEmpty(wecomTitle) ? View.GONE : View.VISIBLE);
        wkVBinding.wecomTitleTv.setText(wecomTitle);
        wkVBinding.wecomTipsTv.setVisibility(TextUtils.isEmpty(wecomTips) ? View.GONE : View.VISIBLE);
        wkVBinding.wecomTipsTv.setText(wecomTips);
        wkVBinding.qrCodeContainer.setVisibility(hasQrCode ? View.VISIBLE : View.GONE);
        wkVBinding.emailLayout.setVisibility(TextUtils.isEmpty(email) ? View.GONE : View.VISIBLE);
        wkVBinding.phoneLayout.setVisibility(TextUtils.isEmpty(phone) ? View.GONE : View.VISIBLE);
        wkVBinding.contactMethodsTitle.setVisibility(hasOtherContact ? View.VISIBLE : View.GONE);
        wkVBinding.emptyTv.setVisibility(hasWecom || hasOtherContact ? View.GONE : View.VISIBLE);
        wkVBinding.emailTv.setText(email);
        wkVBinding.phoneTv.setText(phone);
        wkVBinding.qrCodeIv.setContentDescription(
                !TextUtils.isEmpty(wecomTitle) ? wecomTitle : wecomTips
        );
        if (hasQrCode) {
            loadQrCode();
        }
    }

    @Override
    protected void initListener() {
        SingleClickUtil.onSingleClick(wkVBinding.qrCodeIv, view -> previewQrCode());
        SingleClickUtil.onSingleClick(wkVBinding.retryTv, view -> loadQrCode());
        SingleClickUtil.onSingleClick(wkVBinding.emailLayout, view -> openEmail());
        SingleClickUtil.onSingleClick(wkVBinding.phoneLayout, view -> openPhone());
    }

    private void loadQrCode() {
        wkVBinding.loadingView.setVisibility(View.VISIBLE);
        wkVBinding.retryTv.setVisibility(View.GONE);
        Glide.with(this)
                .load(qrCodeUrl)
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model,
                                                Target<Drawable> target, boolean isFirstResource) {
                        wkVBinding.loadingView.setVisibility(View.GONE);
                        wkVBinding.retryTv.setVisibility(View.VISIBLE);
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(Drawable resource, Object model,
                                                   Target<Drawable> target, DataSource dataSource,
                                                   boolean isFirstResource) {
                        wkVBinding.loadingView.setVisibility(View.GONE);
                        wkVBinding.retryTv.setVisibility(View.GONE);
                        return false;
                    }
                })
                .into(wkVBinding.qrCodeIv);
    }

    private void previewQrCode() {
        if (TextUtils.isEmpty(qrCodeUrl) || wkVBinding.retryTv.getVisibility() == View.VISIBLE) {
            return;
        }
        WKDialogUtils.getInstance().showImagePopup(
                this,
                Collections.singletonList((Object) qrCodeUrl),
                Collections.singletonList((ImageView) wkVBinding.qrCodeIv),
                wkVBinding.qrCodeIv,
                0,
                new ArrayList<>(),
                null,
                null
        );
    }

    private void openEmail() {
        Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", email, null));
        startOrCopy(intent, email);
    }

    private void openPhone() {
        Intent intent = new Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", phone, null));
        startOrCopy(intent, phone);
    }

    private void startOrCopy(Intent intent, String value) {
        if (getPackageManager().queryIntentActivities(intent, 0).isEmpty()) {
            copyContact(value);
            return;
        }
        try {
            startActivity(intent);
        } catch (Exception exception) {
            copyContact(value);
        }
    }

    private void copyContact(String value) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.contact_us), value));
            Toast.makeText(this, R.string.contact_copied, Toast.LENGTH_SHORT).show();
        }
    }

    public static boolean hasContact(WKAPPConfig config) {
        return config != null && (!TextUtils.isEmpty(trim(config.contact_wecom_qrcode))
                || !TextUtils.isEmpty(trim(config.contact_wecom_title))
                || !TextUtils.isEmpty(trim(config.contact_wecom_tips))
                || !TextUtils.isEmpty(trim(config.contact_email))
                || !TextUtils.isEmpty(trim(config.contact_phone)));
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
