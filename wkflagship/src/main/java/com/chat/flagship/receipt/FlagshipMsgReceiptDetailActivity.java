package com.chat.flagship.receipt;

/**
 * 消息回执详情页
 * Created by Luckclouds and chatGPT.
 */
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.core.content.ContextCompat;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.net.HttpResponseCode;
import com.chat.flagship.R;
import com.chat.flagship.databinding.ActFlagshipMsgReceiptDetailLayoutBinding;
import com.chat.flagship.entity.FlagshipReceiptUser;
import com.chat.flagship.service.FlagshipReceiptModel;

import java.util.ArrayList;
import java.util.List;

public class FlagshipMsgReceiptDetailActivity extends WKBaseActivity<ActFlagshipMsgReceiptDetailLayoutBinding> {
    public static final String EXTRA_MESSAGE_ID = "messageId";
    public static final String EXTRA_READED_COUNT = "readedCount";
    public static final String EXTRA_UNREAD_COUNT = "unreadCount";

    private static final int FILTER_READED = 1;
    private static final int FILTER_UNREAD = 2;

    private String messageId;
    private int readedCount;
    private int unreadCount;
    private int currentFilter = FILTER_READED;
    private boolean readedLoaded;
    private boolean unreadLoaded;
    private final List<FlagshipReceiptUser> readedUsers = new ArrayList<>();
    private final List<FlagshipReceiptUser> unreadUsers = new ArrayList<>();
    private FlagshipMsgReceiptUserAdapter adapter;

    @Override
    protected ActFlagshipMsgReceiptDetailLayoutBinding getViewBinding() {
        return ActFlagshipMsgReceiptDetailLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(R.string.flagship_msg_receipt);
    }

    @Override
    protected void initPresenter() {
        messageId = getIntent().getStringExtra(EXTRA_MESSAGE_ID);
        readedCount = getIntent().getIntExtra(EXTRA_READED_COUNT, 0);
        unreadCount = getIntent().getIntExtra(EXTRA_UNREAD_COUNT, 0);
    }

    @Override
    protected void initView() {
        adapter = new FlagshipMsgReceiptUserAdapter();
        wkVBinding.recyclerView.setLayoutManager(new LinearLayoutManager(this));
        wkVBinding.recyclerView.setAdapter(adapter);
        bindTabs();
        loadData();
    }

    @Override
    protected void initListener() {
        wkVBinding.readedCountTv.setOnClickListener(v -> switchFilter(FILTER_READED));
        wkVBinding.unreadCountTv.setOnClickListener(v -> switchFilter(FILTER_UNREAD));
    }

    private void loadData() {
        if (TextUtils.isEmpty(messageId)) {
            wkVBinding.emptyTv.setVisibility(View.VISIBLE);
            wkVBinding.recyclerView.setVisibility(View.GONE);
            return;
        }
        readedLoaded = false;
        unreadLoaded = false;
        wkVBinding.loadingView.setVisibility(View.VISIBLE);
        wkVBinding.emptyTv.setVisibility(View.GONE);
        wkVBinding.recyclerView.setVisibility(View.GONE);
        loadReceiptUsers(FILTER_READED);
        loadReceiptUsers(FILTER_UNREAD);
    }

    private void loadReceiptUsers(int filter) {
        FlagshipReceiptModel.getInstance().getReceiptUsers(messageId, filter == FILTER_READED ? 1 : 0, (code, msg, list) -> {
            if (filter == FILTER_READED) {
                readedUsers.clear();
                if (list != null) {
                    readedUsers.addAll(list);
                }
                readedCount = readedUsers.size();
                readedLoaded = true;
            } else {
                unreadUsers.clear();
                if (list != null) {
                    unreadUsers.addAll(list);
                }
                unreadCount = unreadUsers.size();
                unreadLoaded = true;
            }
            bindTabs();
            renderList();
            if (code != HttpResponseCode.success && !TextUtils.isEmpty(msg)) {
                showToast(msg);
            }
        });
    }

    private void switchFilter(int filter) {
        if (currentFilter == filter) {
            return;
        }
        currentFilter = filter;
        bindTabs();
        renderList();
    }

    private void bindTabs() {
        bindTabStyle(wkVBinding.readedCountTv, currentFilter == FILTER_READED, getString(R.string.flagship_receipt_readed_count, readedCount));
        bindTabStyle(wkVBinding.unreadCountTv, currentFilter == FILTER_UNREAD, getString(R.string.flagship_receipt_unread_count, unreadCount));
    }

    private void bindTabStyle(TextView textView, boolean isSelected, String text) {
        textView.setText(text);
        textView.setBackgroundResource(isSelected ? R.drawable.shape_flagship_receipt_tab_selected : R.drawable.shape_flagship_receipt_tab_normal);
        textView.setTextColor(ContextCompat.getColor(this, isSelected ? R.color.flagship_receipt_tab_text_selected : R.color.flagship_receipt_tab_text_normal));
    }

    private void renderList() {
        if (!readedLoaded || !unreadLoaded) {
            wkVBinding.loadingView.setVisibility(View.VISIBLE);
            wkVBinding.emptyTv.setVisibility(View.GONE);
            wkVBinding.recyclerView.setVisibility(View.GONE);
            return;
        }
        wkVBinding.loadingView.setVisibility(View.GONE);
        List<FlagshipReceiptUser> source = currentFilter == FILTER_READED ? readedUsers : unreadUsers;
        adapter.setData(source);
        boolean isEmpty = source.isEmpty();
        wkVBinding.emptyTv.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        wkVBinding.recyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }
}
