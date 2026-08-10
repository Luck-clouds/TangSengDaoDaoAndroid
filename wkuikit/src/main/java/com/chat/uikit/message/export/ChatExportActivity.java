package com.chat.uikit.message.export;

import android.net.Uri;
import android.view.View;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.chat.base.base.WKBaseActivity;
import com.chat.uikit.R;
import com.chat.uikit.databinding.ActChatExportBinding;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ChatExportActivity extends WKBaseActivity<ActChatExportBinding> {
    private final ActivityResultLauncher<String> createDocument = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/zip"), this::onTargetSelected);
    private ChatExportManager.ExportHandle exportHandle;
    private boolean exporting;

    @Override
    protected ActChatExportBinding getViewBinding() {
        return ActChatExportBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(R.string.chat_export);
    }

    @Override
    protected void initListener() {
        wkVBinding.exportBtn.setOnClickListener(v -> {
            if (exporting) {
                return;
            }
            String time = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            createDocument.launch(getString(R.string.chat_export_file_name, time));
        });
    }

    private void onTargetSelected(Uri uri) {
        if (uri == null) {
            return;
        }
        setExporting(true);
        exportHandle = ChatExportManager.export(this, uri, wkVBinding.includeFilesCb.isChecked(),
                new ChatExportManager.Callback() {
                    @Override
                    public void onProgress(int progress) {
                        wkVBinding.progressBar.setProgress(progress);
                    }

                    @Override
                    public void onSuccess(ChatExportManager.ExportResult result) {
                        setExporting(false);
                        if (result.missingCount > 0) {
                            showToast(getString(R.string.chat_export_partial, result.missingCount));
                        } else {
                            showToast(R.string.chat_export_success);
                        }
                    }

                    @Override
                    public void onNoData() {
                        setExporting(false);
                        showToast(R.string.chat_export_no_data);
                    }

                    @Override
                    public void onFail() {
                        setExporting(false);
                        showToast(R.string.chat_export_fail);
                    }
                });
    }

    private void setExporting(boolean value) {
        exporting = value;
        wkVBinding.exportBtn.setEnabled(!value);
        wkVBinding.includeFilesCb.setEnabled(!value);
        wkVBinding.progressBar.setVisibility(value ? View.VISIBLE : View.GONE);
        wkVBinding.statusTv.setVisibility(value ? View.VISIBLE : View.GONE);
        wkVBinding.statusTv.setText(R.string.chat_export_running);
    }

    @Override
    protected void backListener(int type) {
        if (exporting && exportHandle != null) {
            exportHandle.cancel();
        }
        finish();
    }

    @Override
    protected void onDestroy() {
        if (exporting && exportHandle != null) {
            exportHandle.cancel();
        }
        super.onDestroy();
    }
}
