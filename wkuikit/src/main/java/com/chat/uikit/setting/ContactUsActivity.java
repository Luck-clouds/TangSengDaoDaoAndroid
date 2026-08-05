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
    private String email = "";

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
        qrCodeUrl = WKApiConfig.getShowUrl(trim(config.contact_wecom_qrcode));
        email = trim(config.contact_email);
    }

    @Override
    protected void initView() {
        wkVBinding.wecomLayout.setVisibility(TextUtils.isEmpty(qrCodeUrl) ? View.GONE : View.VISIBLE);
        wkVBinding.emailLayout.setVisibility(TextUtils.isEmpty(email) ? View.GONE : View.VISIBLE);
        wkVBinding.phoneLayout.setVisibility(View.GONE);
        wkVBinding.emailTv.setText(getString(R.string.contact_email_value, email));
        if (!TextUtils.isEmpty(qrCodeUrl)) {
            loadQrCode();
        }
    }

    @Override
    protected void initListener() {
        SingleClickUtil.onSingleClick(wkVBinding.qrCodeIv, view -> previewQrCode());
        SingleClickUtil.onSingleClick(wkVBinding.retryTv, view -> loadQrCode());
        SingleClickUtil.onSingleClick(wkVBinding.emailLayout, view -> openEmail());
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
                || !TextUtils.isEmpty(trim(config.contact_email)));
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
