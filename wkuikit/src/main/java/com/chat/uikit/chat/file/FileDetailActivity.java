package com.chat.uikit.chat.file;

import android.net.Uri;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.config.WKApiConfig;
import com.chat.base.net.ud.WKDownloader;
import com.chat.base.net.ud.WKProgressManager;
import com.chat.base.utils.WKToastUtils;
import com.chat.uikit.R;
import com.chat.uikit.chat.msgmodel.WKFileContent;
import com.chat.uikit.databinding.ActFileDetailLayoutBinding;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKMsg;

public class FileDetailActivity extends WKBaseActivity<ActFileDetailLayoutBinding> {
    public static final String EXTRA_NAME = "name";
    public static final String EXTRA_SIZE = "size";
    public static final String EXTRA_URL = "url";
    public static final String EXTRA_LOCAL_PATH = "localPath";
    public static final String EXTRA_URI = "uri";
    public static final String EXTRA_CLIENT_MSG_NO = "clientMsgNo";
    public static final String EXTRA_FROM_RECENT = "fromRecent";

    private String name;
    private long size;
    private String url;
    private String localPath;
    private String uriString;
    private String clientMsgNo;
    private boolean fromRecent;

    @Override
    protected ActFileDetailLayoutBinding getViewBinding() {
        return ActFileDetailLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(R.string.file);
    }

    @Override
    protected void initView() {
        name = getIntent().getStringExtra(EXTRA_NAME);
        size = getIntent().getLongExtra(EXTRA_SIZE, 0L);
        url = getIntent().getStringExtra(EXTRA_URL);
        localPath = getIntent().getStringExtra(EXTRA_LOCAL_PATH);
        uriString = getIntent().getStringExtra(EXTRA_URI);
        clientMsgNo = getIntent().getStringExtra(EXTRA_CLIENT_MSG_NO);
        fromRecent = getIntent().getBooleanExtra(EXTRA_FROM_RECENT, false);

        wkVBinding.fileBadgeTv.setText(FileMessageUtils.getFileBadgeText(name));
        wkVBinding.fileNameTv.setText(TextUtils.isEmpty(name) ? getString(R.string.unknown_file) : name);
        wkVBinding.fileSizeTv.setText(FileMessageUtils.formatFileSize(size));
        updateButtonState();
    }

    @Override
    protected void initListener() {
        wkVBinding.actionBtn.setOnClickListener(v -> {
            if (FileMessageUtils.isLocalFileAvailable(localPath)) {
                FileMessageUtils.openFile(this, localPath);
                return;
            }
            if (fromRecent) {
                resolveRecentFileAndOpen();
                return;
            }
            if (TextUtils.isEmpty(url)) {
                WKToastUtils.getInstance().showToastNormal(getString(R.string.file_not_exists));
                return;
            }
            downloadAndOpen();
        });
    }

    private void resolveRecentFileAndOpen() {
        new Thread(() -> {
            Uri uri = TextUtils.isEmpty(uriString) ? null : Uri.parse(uriString);
            String path = FileMessageUtils.ensureLocalPath(this, uri, localPath, name);
            runOnUiThread(() -> {
                if (TextUtils.isEmpty(path)) {
                    WKToastUtils.getInstance().showToastNormal(getString(R.string.file_not_exists));
                    return;
                }
                localPath = path;
                updateButtonState();
                FileMessageUtils.openFile(this, path);
            });
        }).start();
    }

    private void downloadAndOpen() {
        String showUrl = WKApiConfig.getShowUrl(url);
        String targetPath = FileMessageUtils.buildDownloadPath(name);
        wkVBinding.progressLayout.setVisibility(View.VISIBLE);
        wkVBinding.downloadProgress.setProgress(0);
        wkVBinding.progressTv.setText("0%");
        wkVBinding.actionBtn.setEnabled(false);
        wkVBinding.actionBtn.setText(R.string.downloading_file);
        WKDownloader.Companion.getInstance().download(showUrl, targetPath, new WKProgressManager.IProgress() {
            @Override
            public void onProgress(@Nullable Object tag, int progress) {
                runOnUiThread(() -> {
                    wkVBinding.downloadProgress.setProgress(progress);
                    wkVBinding.progressTv.setText(progress + "%");
                });
            }

            @Override
            public void onSuccess(@Nullable Object tag, @Nullable String path) {
                runOnUiThread(() -> {
                    String finalPath = TextUtils.isEmpty(path) ? targetPath : path;
                    localPath = finalPath;
                    updateLocalMessagePath(finalPath);
                    updateButtonState();
                    FileMessageUtils.openFile(FileDetailActivity.this, finalPath);
                });
            }

            @Override
            public void onFail(@Nullable Object tag, @Nullable String msg) {
                runOnUiThread(() -> {
                    wkVBinding.actionBtn.setEnabled(true);
                    wkVBinding.progressLayout.setVisibility(View.GONE);
                    wkVBinding.downloadProgress.setProgress(0);
                    wkVBinding.progressTv.setText("0%");
                    wkVBinding.actionBtn.setText(R.string.download_open);
                    WKToastUtils.getInstance().showToastNormal(getString(com.chat.base.R.string.download_err));
                });
            }
        });
    }

    private void updateLocalMessagePath(@NonNull String path) {
        if (TextUtils.isEmpty(clientMsgNo)) {
            return;
        }
        WKMsg msg = WKIM.getInstance().getMsgManager().getWithClientMsgNO(clientMsgNo);
        if (msg == null || !(msg.baseContentMsgModel instanceof WKFileContent fileContent)) {
            return;
        }
        fileContent.localPath = path;
        msg.baseContentMsgModel = fileContent;
        WKIM.getInstance().getMsgManager().updateContentAndRefresh(clientMsgNo, fileContent, false);
    }

    private void updateButtonState() {
        boolean localAvailable = FileMessageUtils.isLocalFileAvailable(localPath);
        wkVBinding.actionBtn.setEnabled(true);
        if (localAvailable) {
            wkVBinding.actionBtn.setText(R.string.open_now);
            wkVBinding.progressLayout.setVisibility(View.GONE);
            return;
        }
        wkVBinding.downloadProgress.setProgress(0);
        wkVBinding.progressTv.setText("0%");
        wkVBinding.progressLayout.setVisibility(View.GONE);
        wkVBinding.actionBtn.setText(fromRecent ? R.string.open_now : R.string.download_open);
    }
}
