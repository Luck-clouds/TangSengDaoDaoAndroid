package com.chat.flagship.chatbg;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.glide.GlideUtils;
import com.chat.base.utils.SvgHelper;
import com.chat.base.utils.WKFileUtils;
import com.chat.flagship.R;
import com.chat.flagship.databinding.ActFlagshipChatBgPreviewLayoutBinding;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 聊天背景预览页
 * Created by Luckclouds and chatGPT.
 */
public class FlagshipChatBgPreviewActivity extends WKBaseActivity<ActFlagshipChatBgPreviewLayoutBinding> {
    public static final String EXTRA_CHANNEL_ID = "channel_id";
    public static final String EXTRA_CHANNEL_TYPE = "channel_type";
    public static final String EXTRA_PREVIEW_MODE = "preview_mode";
    public static final String EXTRA_LOCAL_PATH = "local_path";
    public static final String EXTRA_CHAT_BG_ITEM = "chat_bg_item";
    public static final String EXTRA_SHOW_BLUR = "show_blur";

    public static final int MODE_DEFAULT = 0;
    public static final int MODE_PRESET = 1;
    public static final int MODE_LOCAL = 2;

    private String channelId;
    private byte channelType;
    private int previewMode;
    private String localPath;
    private boolean showBlur;
    private FlagshipChatBgItem chatBgItem;

    @Override
    protected ActFlagshipChatBgPreviewLayoutBinding getViewBinding() {
        return ActFlagshipChatBgPreviewLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(R.string.flagship_chat_bg_preview);
    }

    @Override
    protected void initPresenter() {
        channelId = getIntent().getStringExtra(EXTRA_CHANNEL_ID);
        channelType = getIntent().getByteExtra(EXTRA_CHANNEL_TYPE, (byte) 1);
        previewMode = getIntent().getIntExtra(EXTRA_PREVIEW_MODE, MODE_DEFAULT);
        localPath = getIntent().getStringExtra(EXTRA_LOCAL_PATH);
        showBlur = getIntent().getBooleanExtra(EXTRA_SHOW_BLUR, false);
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            Object object = extras.getSerializable(EXTRA_CHAT_BG_ITEM);
            if (object instanceof FlagshipChatBgItem item) {
                chatBgItem = item;
            }
        }
    }

    @Override
    protected void initView() {
        wkVBinding.blurLayout.setVisibility(showBlur ? View.VISIBLE : View.GONE);
        wkVBinding.blurSwitch.setChecked(false);
        wkVBinding.blurView.setVisibility(View.GONE);
        renderPreview();
    }

    @Override
    protected void initListener() {
        wkVBinding.blurSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> wkVBinding.blurView.setVisibility(isChecked ? View.VISIBLE : View.GONE));
        wkVBinding.setBtn.setOnClickListener(v -> applyBackground());
    }

    private void renderPreview() {
        if (previewMode == MODE_DEFAULT) {
            wkVBinding.previewIv.setImageDrawable(null);
            wkVBinding.defaultTipTv.setVisibility(View.VISIBLE);
            return;
        }
        wkVBinding.defaultTipTv.setVisibility(View.GONE);
        if (previewMode == MODE_LOCAL) {
            GlideUtils.getInstance().showImg(this, localPath, wkVBinding.previewIv);
            return;
        }
        if (chatBgItem == null) {
            return;
        }
        String previewUrl = TextUtils.isEmpty(chatBgItem.cover) ? chatBgItem.url : chatBgItem.cover;
        GlideUtils.getInstance().showImg(this, previewUrl, wkVBinding.previewIv);
    }

    private void applyBackground() {
        if (previewMode == MODE_DEFAULT) {
            FlagshipChatBgStore.clearConfig(channelId, channelType);
            finishSuccess();
            return;
        }
        loadingPopup.show();
        if (previewMode == MODE_LOCAL) {
            saveLocalImage();
            return;
        }
        savePresetImage();
    }

    private void saveLocalImage() {
        String targetPath = FlagshipChatBgManager.getInstance().createCacheTargetPath(localPath, 0);
        new Thread(() -> {
            try {
                WKFileUtils.getInstance().fileCopy(localPath, targetPath);
                FlagshipChatBgConfig config = new FlagshipChatBgConfig();
                config.type = FlagshipChatBgConfig.TYPE_LOCAL;
                config.localPath = targetPath;
                config.blur = wkVBinding.blurSwitch.isChecked();
                config.isSvg = 0;
                runOnUiThread(() -> onSaveSuccess(config));
            } catch (Exception e) {
                runOnUiThread(() -> onSaveFail());
            }
        }).start();
    }

    private void savePresetImage() {
        if (chatBgItem == null || TextUtils.isEmpty(chatBgItem.url)) {
            onSaveFail();
            return;
        }
        String targetPath = FlagshipChatBgManager.getInstance().createCacheTargetPath(chatBgItem.url, chatBgItem.isSvg);
        new Thread(() -> {
            HttpURLConnection connection = null;
            InputStream inputStream = null;
            FileOutputStream outputStream = null;
            try {
                connection = (HttpURLConnection) new URL(chatBgItem.url).openConnection();
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);
                connection.connect();
                inputStream = connection.getInputStream();
                outputStream = new FileOutputStream(targetPath);
                byte[] buffer = new byte[8192];
                int length;
                while ((length = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, length);
                }
                outputStream.flush();
                FlagshipChatBgConfig config = new FlagshipChatBgConfig();
                config.type = chatBgItem.isDefault ? FlagshipChatBgConfig.TYPE_DEFAULT : FlagshipChatBgConfig.TYPE_PRESET;
                config.sourceUrl = chatBgItem.url;
                config.cover = chatBgItem.cover;
                config.localPath = targetPath;
                config.isSvg = chatBgItem.isSvg;
                config.blur = false;
                runOnUiThread(() -> onSaveSuccess(config));
            } catch (Exception ignored) {
                runOnUiThread(this::onSaveFail);
            } finally {
                try {
                    if (outputStream != null) {
                        outputStream.close();
                    }
                    if (inputStream != null) {
                        inputStream.close();
                    }
                } catch (Exception ignored) {
                }
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }).start();
    }

    private void onSaveSuccess(@NonNull FlagshipChatBgConfig config) {
        loadingPopup.dismiss();
        FlagshipChatBgStore.saveConfig(channelId, channelType, config);
        finishSuccess();
    }

    private void onSaveFail() {
        loadingPopup.dismiss();
        showToast(R.string.flagship_chat_bg_save_failed);
    }

    private void finishSuccess() {
        setResult(Activity.RESULT_OK);
        finish();
    }
}
