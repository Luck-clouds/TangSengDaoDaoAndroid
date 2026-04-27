package com.chat.flagship.search.file;

import android.content.Intent;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.endpoint.EndpointSID;
import com.chat.base.endpoint.entity.ChatViewMenu;
import com.chat.base.entity.GlobalMessage;
import com.chat.base.entity.GlobalSearchReq;
import com.chat.base.msgitem.WKContentType;
import com.chat.base.search.GlobalSearchModel;
import com.chat.base.utils.WKReader;
import com.chat.base.utils.WKTimeUtils;
import com.chat.flagship.R;
import com.chat.flagship.databinding.ActFlagshipSearchRecordLayoutBinding;
import com.scwang.smart.refresh.layout.api.RefreshLayout;
import com.scwang.smart.refresh.layout.listener.OnRefreshLoadMoreListener;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannelType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 文件记录搜索页
 * Created by Luckclouds and chatGPT.
 */
public class FlagshipSearchFileActivity extends WKBaseActivity<ActFlagshipSearchRecordLayoutBinding> {
    private String channelID;
    private byte channelType;
    private int page = 1;
    private FlagshipSearchFileAdapter adapter;

    @Override
    protected ActFlagshipSearchRecordLayoutBinding getViewBinding() {
        return ActFlagshipSearchRecordLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(R.string.flagship_search_file);
    }

    @Override
    protected void initPresenter() {
        channelID = getIntent().getStringExtra("channel_id");
        channelType = getIntent().getByteExtra("channel_type", WKChannelType.PERSONAL);
    }

    @Override
    protected void initView() {
        adapter = new FlagshipSearchFileAdapter();
        wkVBinding.recyclerView.setLayoutManager(new LinearLayoutManager(this));
        wkVBinding.recyclerView.setAdapter(adapter);
        wkVBinding.refreshLayout.setEnableRefresh(false);
    }

    @Override
    protected void initListener() {
        wkVBinding.refreshLayout.setOnRefreshLoadMoreListener(new OnRefreshLoadMoreListener() {
            @Override
            public void onLoadMore(@NonNull RefreshLayout refreshLayout) {
                page++;
                getData();
            }

            @Override
            public void onRefresh(@NonNull RefreshLayout refreshLayout) {
            }
        });
        adapter.setOnItemClickListener((adapter1, view, position) -> {
            FlagshipSearchFileEntity entity = (FlagshipSearchFileEntity) adapter1.getData().get(position);
            if (entity != null && entity.getItemType() == FlagshipSearchFileEntity.TYPE_ITEM) {
                openFileDetail(entity);
            }
        });
        getData();
    }

    private void getData() {
        ArrayList<Integer> contentType = new ArrayList<>();
        contentType.add(WKContentType.WK_FILE);
        GlobalSearchReq req = new GlobalSearchReq(1, "", channelID, channelType, "", "", contentType, page, 20, 0, 0);
        GlobalSearchModel.INSTANCE.search(req, (code, msg, globalSearch) -> {
            wkVBinding.refreshLayout.finishLoadMore();
            wkVBinding.refreshLayout.finishRefresh();
            if (globalSearch == null || WKReader.isEmpty(globalSearch.messages)) {
                if (page == 1) {
                    wkVBinding.nodataTv.setVisibility(View.VISIBLE);
                    wkVBinding.refreshLayout.setEnableLoadMore(false);
                } else {
                    wkVBinding.refreshLayout.finishLoadMoreWithNoMoreData();
                }
                return null;
            }
            wkVBinding.nodataTv.setVisibility(View.GONE);
            wkVBinding.refreshLayout.setEnableLoadMore(true);
            List<FlagshipSearchFileEntity> entities = buildEntities(globalSearch.messages);
            if (page == 1 && WKReader.isEmpty(entities)) {
                wkVBinding.nodataTv.setVisibility(View.VISIBLE);
                wkVBinding.refreshLayout.setEnableLoadMore(false);
                adapter.setList(new ArrayList<>());
                return null;
            }
            if (page > 1 && WKReader.isNotEmpty(adapter.getData()) && WKReader.isNotEmpty(entities)) {
                FlagshipSearchFileEntity last = adapter.getData().get(adapter.getData().size() - 1);
                if (last != null && last.getItemType() == FlagshipSearchFileEntity.TYPE_ITEM && TextUtils.equals(last.date, entities.get(0).date)) {
                    entities.remove(0);
                }
            }
            if (page == 1) {
                adapter.setList(entities);
            } else if (WKReader.isNotEmpty(entities)) {
                adapter.addData(entities);
            }
            return null;
        });
    }

    private List<FlagshipSearchFileEntity> buildEntities(List<GlobalMessage> messages) {
        List<FlagshipSearchFileEntity> result = new ArrayList<>();
        for (GlobalMessage message : messages) {
            if (message.getContentType() != WKContentType.WK_FILE) {
                continue;
            }
            String date = WKTimeUtils.getInstance().time2YearMonth(message.getTimestamp() * 1000);
            if (WKReader.isEmpty(result) || !TextUtils.equals(result.get(result.size() - 1).date, date)) {
                FlagshipSearchFileEntity header = new FlagshipSearchFileEntity();
                header.itemType = FlagshipSearchFileEntity.TYPE_HEADER;
                header.date = date;
                result.add(header);
            }
            FlagshipSearchFileEntity item = new FlagshipSearchFileEntity();
            item.itemType = FlagshipSearchFileEntity.TYPE_ITEM;
            item.date = date;
            item.displayTime = WKTimeUtils.getInstance().getTimeString(message.getTimestamp() * 1000);
            item.senderUID = message.getFrom_uid();
            item.senderName = resolveSenderName(message);
            item.fileName = readString(message.getPayload(), "name", getString(R.string.flagship_search_file_unknown_name));
            item.fileExt = resolveExt(message.getPayload(), item.fileName);
            item.fileUrl = readString(message.getPayload(), "url", "");
            item.localPath = readString(message.getPayload(), "localPath", "");
            item.clientMsgNo = message.getClient_msg_no();
            item.fileSize = readLong(message.getPayload(), "size");
            item.message = message;
            result.add(item);
        }
        return result;
    }

    private String resolveSenderName(GlobalMessage message) {
        if (message.getFrom_channel() != null) {
            String remark = message.getFrom_channel().getChannel_remark();
            if (!TextUtils.isEmpty(remark)) {
                return remark;
            }
            String name = message.getFrom_channel().getChannel_name();
            if (!TextUtils.isEmpty(name)) {
                return name;
            }
        }
        if (!TextUtils.isEmpty(message.getFrom_uid())) {
            return message.getFrom_uid();
        }
        return "";
    }

    private String resolveExt(Map<String, Object> payload, String fileName) {
        String ext = readString(payload, "ext", "");
        if (!TextUtils.isEmpty(ext)) {
            return ext;
        }
        if (!TextUtils.isEmpty(fileName)) {
            int index = fileName.lastIndexOf('.');
            if (index >= 0 && index < fileName.length() - 1) {
                return fileName.substring(index + 1);
            }
        }
        return "";
    }

    private String readString(Map<String, Object> payload, String key, String defaultValue) {
        if (payload == null) {
            return defaultValue;
        }
        Object value = payload.get(key);
        if (value instanceof String stringValue && !TextUtils.isEmpty(stringValue)) {
            return stringValue;
        }
        return defaultValue;
    }

    private long readLong(Map<String, Object> payload, String key) {
        if (payload == null) {
            return 0L;
        }
        Object value = payload.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String stringValue && !TextUtils.isEmpty(stringValue)) {
            try {
                return Long.parseLong(stringValue);
            } catch (NumberFormatException ignored) {
            }
        }
        return 0L;
    }

    private void openFileDetail(FlagshipSearchFileEntity entity) {
        if (entity == null) {
            return;
        }
        try {
            Intent intent = new Intent();
            intent.setClassName(getPackageName(), "com.chat.uikit.chat.file.FileDetailActivity");
            intent.putExtra("name", entity.fileName);
            intent.putExtra("size", entity.fileSize);
            intent.putExtra("url", entity.fileUrl);
            intent.putExtra("localPath", entity.localPath);
            intent.putExtra("clientMsgNo", entity.clientMsgNo);
            intent.putExtra("fromRecent", false);
            startActivity(intent);
        } catch (Exception ignored) {
            if (entity.message == null || entity.message.getChannel() == null) {
                return;
            }
            long orderSeq = WKIM.getInstance().getMsgManager().getMessageOrderSeq(
                    entity.message.getMessage_seq(),
                    entity.message.getChannel().getChannel_id(),
                    entity.message.getChannel().getChannel_type()
            );
            EndpointManager.getInstance().invoke(EndpointSID.chatView, new ChatViewMenu(
                    this,
                    channelID,
                    channelType,
                    orderSeq,
                    false
            ));
        }
    }
}
