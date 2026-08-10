package com.chat.uikit.memo;

import android.text.TextUtils;
import android.widget.TextView;

import com.chat.base.base.WKBaseActivity;
import com.chat.uikit.R;
import com.chat.uikit.databinding.ActMemoEditBinding;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MemoEditActivity extends WKBaseActivity<ActMemoEditBinding> {
    public static final String EXTRA_ID = "memo_id";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private long memoId;
    private MemoRecord record = new MemoRecord();
    private String originalTitle = "";
    private String originalContent = "";
    private boolean saving;

    @Override
    protected ActMemoEditBinding getViewBinding() {
        return ActMemoEditBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initPresenter() {
        memoId = getIntent().getLongExtra(EXTRA_ID, 0);
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(memoId > 0 ? R.string.memo : R.string.memo_new);
    }

    @Override
    protected String getRightTvText(TextView textView) {
        return getString(R.string.memo_save);
    }

    @Override
    protected void rightLayoutClick() {
        save();
    }

    @Override
    protected void initData() {
        if (memoId <= 0) {
            return;
        }
        executor.execute(() -> {
            try {
                MemoRecord loaded = MemoRepository.get(memoId);
                runOnUiThread(() -> {
                    if (loaded == null) {
                        finish();
                        return;
                    }
                    record = loaded;
                    originalTitle = safe(loaded.title);
                    originalContent = safe(loaded.content);
                    wkVBinding.memoTitleEt.setText(originalTitle);
                    wkVBinding.memoContentEt.setText(originalContent);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    showToast(R.string.unknown_error);
                    finish();
                });
            }
        });
    }

    private void save() {
        if (saving) {
            return;
        }
        String title = wkVBinding.memoTitleEt.getText().toString().trim();
        String content = wkVBinding.memoContentEt.getText().toString().trim();
        if (TextUtils.isEmpty(title) && TextUtils.isEmpty(content)) {
            showToast(R.string.memo_input);
            return;
        }
        saving = true;
        record.title = title;
        record.content = content;
        executor.execute(() -> {
            try {
                MemoRepository.save(record);
                runOnUiThread(() -> {
                    originalTitle = title;
                    originalContent = content;
                    showToast(R.string.memo_saved);
                    finish();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    saving = false;
                    showToast(R.string.memo_save_fail);
                });
            }
        });
    }

    @Override
    protected void backListener(int type) {
        if (saving || !hasChanges()) {
            finish();
            return;
        }
        showDialog(getString(R.string.memo_discard), index -> {
            if (index == 1) {
                finish();
            }
        });
    }

    private boolean hasChanges() {
        return !TextUtils.equals(originalTitle, wkVBinding.memoTitleEt.getText().toString())
                || !TextUtils.equals(originalContent, wkVBinding.memoContentEt.getText().toString());
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}
