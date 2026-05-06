package com.chat.flagship.chatbg;

import android.app.Activity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.glide.GlideUtils;
import com.chat.base.utils.WKFileUtils;
import com.chat.flagship.R;
import com.chat.flagship.databinding.ActFlagshipChatBgPreviewLayoutBinding;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 聊天背景预览页
 * Created by Luckclouds and chatGPT.
 */
public class FlagshipChatBgPreviewActivity extends WKBaseActivity<ActFlagshipChatBgPreviewLayoutBinding> {
    private static final String TAG = "FlagshipChatBg";
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
    private int gradientStep;
    private boolean showPattern = true;
    private String loadedPatternUrl;

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
        Log.d(TAG, "preview init mode=" + previewMode + " channelId=" + channelId + " channelType=" + channelType + " localPath=" + localPath + " showBlur=" + showBlur + " itemUrl=" + (chatBgItem == null ? "" : chatBgItem.url) + " itemCover=" + (chatBgItem == null ? "" : chatBgItem.cover) + " itemSvg=" + (chatBgItem == null ? -1 : chatBgItem.isSvg));
    }

    @Override
    protected void initView() {
        applyTitleBarStyle();
        updateBlurState(showBlur);
        updatePreviewStatus();
        configureActionButtons();
        wkVBinding.rightStatusIv.setVisibility(View.VISIBLE);
        renderPreview();
    }

    @Override
    protected void initListener() {
        wkVBinding.blurBtn.setOnClickListener(v -> {
            updateBlurState(!wkVBinding.blurBtn.isSelected());
        });
        wkVBinding.refreshBtn.setOnClickListener(v -> {
            gradientStep = (gradientStep + 1) % 6;
            if (previewMode == MODE_PRESET && chatBgItem != null && chatBgItem.isSvg == 1) {
                FlagshipChatBgManager.getInstance().applyGradientBackground(
                        wkVBinding.previewIv,
                        chatBgItem.lightColors,
                        chatBgItem.darkColors,
                        gradientStep
                );
            }
        });
        wkVBinding.patternBtn.setOnClickListener(v -> {
            showPattern = !showPattern;
            updatePatternState();
            renderPreview();
        });
        wkVBinding.setBtn.setOnClickListener(v -> applyBackground());
    }

    private void renderPreview() {
        if (previewMode == MODE_DEFAULT) {
            Log.d(TAG, "renderPreview default");
            wkVBinding.previewIv.setImageDrawable(null);
            wkVBinding.previewIv.setBackground(null);
            wkVBinding.defaultTipTv.setVisibility(View.VISIBLE);
            return;
        }
        wkVBinding.defaultTipTv.setVisibility(View.GONE);
        if (previewMode == MODE_LOCAL) {
            Log.d(TAG, "renderPreview local path=" + localPath);
            wkVBinding.previewIv.setBackground(null);
            GlideUtils.getInstance().showImg(this, localPath, wkVBinding.previewIv);
            return;
        }
        if (chatBgItem == null) {
            Log.w(TAG, "renderPreview preset skipped: item null");
            return;
        }
        Log.d(TAG, "renderPreview preset url=" + chatBgItem.url + " cover=" + chatBgItem.cover + " svg=" + chatBgItem.isSvg
                + " lightColors=" + chatBgItem.lightColors + " darkColors=" + chatBgItem.darkColors);
        if (chatBgItem.isSvg == 1) {
            FlagshipChatBgManager.getInstance().applyGradientBackground(
                    wkVBinding.previewIv,
                    chatBgItem.lightColors,
                    chatBgItem.darkColors,
                    gradientStep
            );
            if (showPattern) {
                if (!TextUtils.equals(loadedPatternUrl, chatBgItem.url) || wkVBinding.previewIv.getDrawable() == null) {
                    wkVBinding.previewIv.setImageDrawable(null);
                    loadedPatternUrl = chatBgItem.url;
                    FlagshipChatBgManager.getInstance().loadRemoteSvgPattern(wkVBinding.previewIv, chatBgItem.url);
                }
            } else {
                loadedPatternUrl = null;
                wkVBinding.previewIv.setImageDrawable(null);
            }
        } else {
            loadedPatternUrl = null;
            wkVBinding.previewIv.setBackground(null);
            wkVBinding.previewIv.setImageDrawable(null);
            FlagshipChatBgManager.getInstance().loadPreviewImage(wkVBinding.previewIv, chatBgItem);
        }
    }

    private void applyBackground() {
        Log.d(TAG, "applyBackground mode=" + previewMode + " blurSelected=" + wkVBinding.blurBtn.isSelected());
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
        Log.d(TAG, "saveLocalImage from=" + localPath + " to=" + targetPath);
        new Thread(() -> {
            try {
                WKFileUtils.getInstance().fileCopy(localPath, targetPath);
                FlagshipChatBgConfig config = new FlagshipChatBgConfig();
                config.type = FlagshipChatBgConfig.TYPE_LOCAL;
                config.localPath = targetPath;
                config.blur = wkVBinding.blurBtn.isSelected();
                config.isSvg = 0;
                runOnUiThread(() -> onSaveSuccess(config));
            } catch (Exception e) {
                Log.e(TAG, "saveLocalImage fail msg=" + e.getMessage(), e);
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
        java.util.List<String> candidates = FlagshipChatBgManager.getInstance().buildRemoteAssetCandidates(chatBgItem.url);
        Log.d(TAG, "savePresetImage candidates=" + candidates + " targetPath=" + targetPath);
        new Thread(() -> {
            boolean success = false;
            Exception lastException = null;
            for (String remoteUrl : candidates) {
                HttpURLConnection connection = null;
                InputStream inputStream = null;
                FileOutputStream outputStream = null;
                try {
                    connection = (HttpURLConnection) new URL(remoteUrl).openConnection();
                    connection.setConnectTimeout(15000);
                    connection.setReadTimeout(15000);
                    connection.connect();
                    int code = connection.getResponseCode();
                    Log.d(TAG, "savePresetImage response=" + code + " contentType=" + connection.getContentType() + " url=" + remoteUrl);
                    if (code / 100 != 2) {
                        continue;
                    }
                    inputStream = connection.getInputStream();
                    outputStream = new FileOutputStream(targetPath);
                    byte[] buffer = new byte[8192];
                    int length;
                    while ((length = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, length);
                    }
                    outputStream.flush();
                    success = true;
                    break;
                } catch (Exception e) {
                    lastException = e;
                    Log.e(TAG, "savePresetImage candidate fail url=" + remoteUrl + " msg=" + e.getMessage(), e);
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
            }
            try {
                if (!success) {
                    throw lastException != null ? lastException : new IllegalStateException("all preset candidates failed");
                }
                FlagshipChatBgConfig config = new FlagshipChatBgConfig();
                config.type = chatBgItem.isDefault ? FlagshipChatBgConfig.TYPE_DEFAULT : FlagshipChatBgConfig.TYPE_PRESET;
                config.sourceUrl = chatBgItem.url;
                config.cover = chatBgItem.cover;
                config.localPath = targetPath;
                config.isSvg = chatBgItem.isSvg;
                config.blur = wkVBinding.blurBtn.isSelected();
                config.lightColors = chatBgItem.lightColors;
                config.darkColors = chatBgItem.darkColors;
                config.gradientStep = gradientStep;
                config.showPattern = showPattern;
                runOnUiThread(() -> onSaveSuccess(config));
            } catch (Exception e) {
                Log.e(TAG, "savePresetImage fail msg=" + e.getMessage(), e);
                runOnUiThread(this::onSaveFail);
            }
        }).start();
    }

    private void onSaveSuccess(@NonNull FlagshipChatBgConfig config) {
        loadingPopup.dismiss();
        Log.d(TAG, "onSaveSuccess type=" + config.type + " localPath=" + config.localPath + " isSvg=" + config.isSvg + " blur=" + config.blur);
        FlagshipChatBgStore.saveConfig(channelId, channelType, config);
        finishSuccess();
    }

    private void onSaveFail() {
        loadingPopup.dismiss();
        Log.e(TAG, "onSaveFail");
        showToast(R.string.flagship_chat_bg_save_failed);
    }

    private void updateBlurState(boolean selected) {
        wkVBinding.blurBtn.setSelected(selected);
        wkVBinding.blurCheckIv.setImageAlpha(selected ? 255 : 0);
        wkVBinding.blurLabelTv.setText(R.string.flagship_chat_bg_blur);
        wkVBinding.blurView.setVisibility(selected ? View.VISIBLE : View.GONE);
    }

    private void configureActionButtons() {
        wkVBinding.blurBtn.setVisibility(View.GONE);
        wkVBinding.refreshBtn.setVisibility(View.GONE);
        wkVBinding.patternBtn.setVisibility(View.GONE);
        if (previewMode == MODE_DEFAULT) {
            return;
        }
        if (previewMode == MODE_LOCAL) {
            wkVBinding.blurBtn.setVisibility(View.VISIBLE);
            return;
        }
        if (chatBgItem == null) {
            return;
        }
        if (chatBgItem.isSvg == 1) {
            wkVBinding.refreshBtn.setVisibility(View.VISIBLE);
            wkVBinding.patternBtn.setVisibility(View.VISIBLE);
            updatePatternState();
        } else {
            wkVBinding.blurBtn.setVisibility(View.VISIBLE);
        }
    }

    private void updatePatternState() {
        wkVBinding.patternBtn.setSelected(showPattern);
        wkVBinding.patternCheckIv.setImageAlpha(showPattern ? 255 : 0);
        wkVBinding.patternLabelTv.setText(R.string.flagship_chat_bg_pattern);
    }

    private void updatePreviewStatus() {
        String currentTime = new SimpleDateFormat("a hh:mm", Locale.CHINA).format(new Date());
        wkVBinding.leftStatusTv.setText(currentTime);
        wkVBinding.rightStatusTv.setText(currentTime);
    }

    private void finishSuccess() {
        Log.d(TAG, "finishSuccess");
        setResult(Activity.RESULT_OK);
        finish();
    }

    private void applyTitleBarStyle() {
        View titleBar = findViewById(com.chat.base.R.id.titleBarLayout);
        View statusBar = findViewById(com.chat.base.R.id.statusBarView);
        TextView titleCenterTv = findViewById(com.chat.base.R.id.titleCenterTv);
        TextView titleRightTv = findViewById(com.chat.base.R.id.titleRightTv);
        ImageView backIv = findViewById(com.chat.base.R.id.backIv);
        ImageView rightIv = findViewById(com.chat.base.R.id.titleRightIv);
        int white = ContextCompat.getColor(this, com.chat.base.R.color.white);
        if (titleBar != null) {
            titleBar.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        }
        if (statusBar != null) {
            statusBar.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        }
        if (titleCenterTv != null) {
            titleCenterTv.setTextColor(white);
        }
        if (titleRightTv != null) {
            titleRightTv.setTextColor(white);
        }
        if (backIv != null) {
            backIv.setColorFilter(white);
        }
        if (rightIv != null) {
            rightIv.setColorFilter(white);
        }
    }
}
