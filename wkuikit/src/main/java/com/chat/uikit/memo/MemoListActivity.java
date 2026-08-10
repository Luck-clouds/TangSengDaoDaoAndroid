package com.chat.uikit.memo;

import android.content.Intent;
import android.view.View;
import android.widget.TextView;

import com.chat.base.base.WKBaseActivity;
import com.chat.uikit.R;
import com.chat.uikit.databinding.ActMemoListBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MemoListActivity extends WKBaseActivity<ActMemoListBinding> {
    private final MemoAdapter adapter = new MemoAdapter(new ArrayList<>());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected ActMemoListBinding getViewBinding() {
        return ActMemoListBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(R.string.memo);
    }

    @Override
    protected String getRightTvText(TextView textView) {
        return getString(R.string.memo_new);
    }

    @Override
    protected void rightLayoutClick() {
        startActivity(new Intent(this, MemoEditActivity.class));
    }

    @Override
    protected void initView() {
        initAdapter(wkVBinding.recyclerView, adapter);
    }

    @Override
    protected void initListener() {
        adapter.setOnItemClickListener((baseQuickAdapter, view, position) -> {
            MemoRecord item = adapter.getItem(position);
            Intent intent = new Intent(this, MemoEditActivity.class);
            intent.putExtra(MemoEditActivity.EXTRA_ID, item.id);
            startActivity(intent);
        });
        adapter.setOnItemLongClickListener((baseQuickAdapter, view, position) -> {
            confirmDelete(adapter.getItem(position));
            return true;
        });
        wkVBinding.emptyTv.setOnClickListener(v -> rightLayoutClick());
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadData();
    }

    private void loadData() {
        executor.execute(() -> {
            try {
                List<MemoRecord> records = MemoRepository.getAll();
                runOnUiThread(() -> {
                    adapter.setList(records);
                    updateEmpty(records.isEmpty());
                });
            } catch (Exception e) {
                runOnUiThread(() -> showToast(R.string.unknown_error));
            }
        });
    }

    private void confirmDelete(MemoRecord record) {
        showDialog(getString(R.string.memo_delete_confirm), index -> {
            if (index != 1) {
                return;
            }
            executor.execute(() -> {
                boolean success = MemoRepository.delete(record.id);
                runOnUiThread(() -> {
                    if (!success) {
                        showToast(R.string.memo_delete_fail);
                    }
                    loadData();
                });
            });
        });
    }

    private void updateEmpty(boolean empty) {
        wkVBinding.emptyTv.setVisibility(empty ? View.VISIBLE : View.GONE);
        wkVBinding.recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}
