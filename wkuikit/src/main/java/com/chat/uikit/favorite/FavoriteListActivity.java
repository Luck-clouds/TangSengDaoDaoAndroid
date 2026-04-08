package com.chat.uikit.favorite;

import android.content.Intent;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.utils.WKReader;
import com.chat.uikit.R;
import com.chat.uikit.databinding.ActCommonRefreshListLayoutBinding;
import com.scwang.smart.refresh.layout.api.RefreshLayout;
import com.scwang.smart.refresh.layout.listener.OnRefreshLoadMoreListener;

import java.util.ArrayList;

public class FavoriteListActivity extends WKBaseActivity<ActCommonRefreshListLayoutBinding> {
    private static final int PAGE_SIZE = 20;
    private boolean firstResume = true;

    private final FavoriteListAdapter adapter = new FavoriteListAdapter(new FavoriteListAdapter.IClick() {
        @Override
        public void onClick(FavoriteEntity entity) {
            Intent intent = new Intent(FavoriteListActivity.this, FavoriteDetailActivity.class);
            intent.putExtra(FavoriteDetailActivity.EXTRA_FAVORITE, entity);
            startActivity(intent);
        }

        @Override
        public void onDelete(FavoriteEntity entity) {
            deleteFavorite(entity);
        }

        @Override
        public void onPreview(FavoriteEntity entity, ImageView imageView) {
            openDetail(entity);
        }
    });
    private int pageIndex = 1;
    private int totalCount = 0;

    @Override
    protected ActCommonRefreshListLayoutBinding getViewBinding() {
        return ActCommonRefreshListLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(R.string.favorite_title);
    }

    @Override
    protected void initView() {
        wkVBinding.noDataTv.setText(R.string.favorite_empty);
        initAdapter(wkVBinding.recyclerView, adapter);
    }

    @Override
    protected void initListener() {
        wkVBinding.refreshLayout.setOnRefreshLoadMoreListener(new OnRefreshLoadMoreListener() {
            @Override
            public void onLoadMore(@NonNull RefreshLayout refreshLayout) {
                pageIndex++;
                loadFavorites();
            }

            @Override
            public void onRefresh(@NonNull RefreshLayout refreshLayout) {
                pageIndex = 1;
                loadFavorites();
            }
        });
    }

    @Override
    protected void initData() {
        super.initData();
        loadFavorites();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (firstResume) {
            firstResume = false;
            return;
        }
        if (pageIndex == 1) {
            loadFavorites();
        }
    }

    private void openDetail(FavoriteEntity entity) {
        Intent intent = new Intent(FavoriteListActivity.this, FavoriteDetailActivity.class);
        intent.putExtra(FavoriteDetailActivity.EXTRA_FAVORITE, entity);
        startActivity(intent);
    }

    private void loadFavorites() {
        FavoriteModel.getInstance().getFavoriteList(pageIndex, PAGE_SIZE, (code, msg, totalCount, list) -> {
            if (pageIndex == 1) {
                wkVBinding.refreshLayout.finishRefresh();
            }
            if (code != HttpResponseCode.success) {
                if (pageIndex > 1) {
                    wkVBinding.refreshLayout.finishLoadMore(false);
                }
                if (pageIndex > 1) {
                    pageIndex--;
                }
                showToast(msg);
                return;
            }
            this.totalCount = totalCount;
            if (pageIndex == 1) {
                adapter.setList(list);
            } else if (WKReader.isNotEmpty(list)) {
                adapter.addData(list);
            }
            boolean hasData = WKReader.isNotEmpty(adapter.getData());
            wkVBinding.noDataTv.setVisibility(hasData ? View.GONE : View.VISIBLE);
            wkVBinding.recyclerView.setVisibility(hasData ? View.VISIBLE : View.GONE);
            boolean enableLoadMore = hasData && adapter.getData().size() < totalCount && WKReader.isNotEmpty(list);
            if (pageIndex > 1 && !enableLoadMore) {
                wkVBinding.refreshLayout.finishLoadMoreWithNoMoreData();
                wkVBinding.refreshLayout.setEnableLoadMore(false);
            } else if (pageIndex > 1) {
                wkVBinding.refreshLayout.finishLoadMore();
                wkVBinding.refreshLayout.setEnableLoadMore(true);
            } else if (pageIndex == 1) {
                wkVBinding.refreshLayout.resetNoMoreData();
                wkVBinding.refreshLayout.setEnableLoadMore(enableLoadMore);
            }
            if (!hasData) {
                adapter.setList(new ArrayList<>());
            }
        });
    }

    private void deleteFavorite(FavoriteEntity entity) {
        FavoriteModel.getInstance().deleteFavorite(this, entity, (code, msg) -> {
            if (code != HttpResponseCode.success) {
                if (!TextUtils.isEmpty(msg)) {
                    showToast(msg);
                }
                return;
            }
            int index = adapter.getData().indexOf(entity);
            if (index >= 0) {
                adapter.removeAt(index);
            }
            totalCount = Math.max(totalCount - 1, 0);
            boolean hasData = WKReader.isNotEmpty(adapter.getData());
            wkVBinding.noDataTv.setVisibility(hasData ? View.GONE : View.VISIBLE);
            wkVBinding.recyclerView.setVisibility(hasData ? View.VISIBLE : View.GONE);
            wkVBinding.refreshLayout.setEnableLoadMore(hasData && adapter.getData().size() < totalCount);
        });
    }

}
