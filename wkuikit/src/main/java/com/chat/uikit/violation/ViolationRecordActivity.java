package com.chat.uikit.violation;

import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.net.HttpResponseCode;
import com.chat.uikit.R;
import com.chat.uikit.databinding.ActViolationRecordBinding;
import com.scwang.smart.refresh.layout.api.RefreshLayout;
import com.scwang.smart.refresh.layout.listener.OnRefreshLoadMoreListener;

import java.util.List;

public class ViolationRecordActivity extends WKBaseActivity<ActViolationRecordBinding> {
    private static final int PAGE_SIZE = 20;
    private static final String TYPE_ALL = "all";
    private static final String TYPE_PUNISHMENT = "punishment";
    private static final String TYPE_COMPLAINT = "complaint";

    private final ViolationRecordAdapter adapter = new ViolationRecordAdapter();
    private String currentType = TYPE_ALL;
    private int currentPage;
    private int total;
    private boolean loading;

    @Override
    protected ActViolationRecordBinding getViewBinding() {
        return ActViolationRecordBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(R.string.violation_record);
    }

    @Override
    protected void initView() {
        initAdapter(wkVBinding.recyclerView, adapter);
        updateFilter();
    }

    @Override
    protected void initListener() {
        wkVBinding.allTv.setOnClickListener(v -> changeType(TYPE_ALL));
        wkVBinding.punishmentTv.setOnClickListener(v -> changeType(TYPE_PUNISHMENT));
        wkVBinding.complaintTv.setOnClickListener(v -> changeType(TYPE_COMPLAINT));
        wkVBinding.refreshLayout.setOnRefreshLoadMoreListener(new OnRefreshLoadMoreListener() {
            @Override
            public void onRefresh(@NonNull RefreshLayout refreshLayout) {
                loadPage(1, false);
            }

            @Override
            public void onLoadMore(@NonNull RefreshLayout refreshLayout) {
                if (adapter.getData().size() >= total) {
                    refreshLayout.finishLoadMoreWithNoMoreData();
                    return;
                }
                loadPage(currentPage + 1, false);
            }
        });
    }

    @Override
    protected void initData() {
        loadPage(1, true);
    }

    private void changeType(String type) {
        if (TextUtils.equals(currentType, type) || loading) {
            return;
        }
        currentType = type;
        currentPage = 0;
        total = 0;
        adapter.setList(null);
        updateFilter();
        loadPage(1, true);
    }

    private void loadPage(int page, boolean showLoading) {
        if (loading) {
            return;
        }
        loading = true;
        if (showLoading) {
            wkVBinding.loadingView.setVisibility(View.VISIBLE);
            wkVBinding.emptyTv.setVisibility(View.GONE);
        }
        ViolationRecordModel.getInstance().getRecords(currentType, page, PAGE_SIZE,
                (code, msg, resultTotal, records) -> {
                    loading = false;
                    wkVBinding.loadingView.setVisibility(View.GONE);
                    wkVBinding.refreshLayout.finishRefresh();
                    if (code != HttpResponseCode.success) {
                        wkVBinding.refreshLayout.finishLoadMore(false);
                        showToast(TextUtils.isEmpty(msg) ? getString(R.string.record_load_fail) : msg);
                        updateEmpty();
                        return;
                    }
                    currentPage = page;
                    total = resultTotal;
                    if (page == 1) {
                        adapter.setList(records);
                        wkVBinding.refreshLayout.resetNoMoreData();
                    } else {
                        adapter.addData(records);
                    }
                    updateLoadMore(records);
                    updateEmpty();
                });
    }

    private void updateLoadMore(List<ViolationRecord> records) {
        boolean hasMore = adapter.getData().size() < total && records != null && !records.isEmpty();
        wkVBinding.refreshLayout.setEnableLoadMore(hasMore);
        if (currentPage > 1) {
            if (hasMore) {
                wkVBinding.refreshLayout.finishLoadMore();
            } else {
                wkVBinding.refreshLayout.finishLoadMoreWithNoMoreData();
            }
        }
    }

    private void updateEmpty() {
        boolean empty = adapter.getData().isEmpty();
        wkVBinding.emptyTv.setVisibility(empty ? View.VISIBLE : View.GONE);
        wkVBinding.recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void updateFilter() {
        updateFilterItem(wkVBinding.allTv, TYPE_ALL.equals(currentType));
        updateFilterItem(wkVBinding.punishmentTv, TYPE_PUNISHMENT.equals(currentType));
        updateFilterItem(wkVBinding.complaintTv, TYPE_COMPLAINT.equals(currentType));
    }

    private void updateFilterItem(TextView textView, boolean selected) {
        textView.setSelected(selected);
        textView.setBackgroundResource(selected ? R.drawable.bg_record_filter_selected : R.drawable.bg_record_filter_normal);
        textView.setTextColor(ContextCompat.getColor(this, selected ? R.color.colorAccent : R.color.color999));
    }
}
