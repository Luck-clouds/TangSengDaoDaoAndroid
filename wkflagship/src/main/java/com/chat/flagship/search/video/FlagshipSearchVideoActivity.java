package com.chat.flagship.search.video;

import android.content.Intent;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.chat.base.act.PlayVideoActivity;
import com.chat.base.base.WKBaseActivity;
import com.chat.base.config.WKApiConfig;
import com.chat.base.entity.GlobalMessage;
import com.chat.base.entity.GlobalSearchReq;
import com.chat.base.search.GlobalSearchModel;
import com.chat.base.msgitem.WKContentType;
import com.chat.base.utils.WKReader;
import com.chat.base.utils.WKTimeUtils;
import com.chat.flagship.R;
import com.chat.flagship.databinding.ActFlagshipSearchRecordLayoutBinding;
import com.scwang.smart.refresh.layout.api.RefreshLayout;
import com.scwang.smart.refresh.layout.listener.OnRefreshLoadMoreListener;
import com.xinbida.wukongim.entity.WKChannelType;
import com.xinbida.wukongim.msgmodel.WKVideoContent;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 视频记录搜索页
 * Created by Luckclouds and chatGPT.
 */
public class FlagshipSearchVideoActivity extends WKBaseActivity<ActFlagshipSearchRecordLayoutBinding> {
    private String channelID;
    private byte channelType;
    private int page = 1;
    private FlagshipSearchVideoAdapter adapter;

    @Override
    protected ActFlagshipSearchRecordLayoutBinding getViewBinding() {
        return ActFlagshipSearchRecordLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(R.string.flagship_search_video);
    }

    @Override
    protected void initPresenter() {
        channelID = getIntent().getStringExtra("channel_id");
        channelType = getIntent().getByteExtra("channel_type", WKChannelType.PERSONAL);
    }

    @Override
    protected void initView() {
        adapter = new FlagshipSearchVideoAdapter();
        GridLayoutManager layoutManager = new GridLayoutManager(this, 4);
        layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                FlagshipSearchVideoEntity entity = adapter.getItem(position);
                return entity != null && entity.getItemType() == FlagshipSearchVideoEntity.TYPE_HEADER ? 4 : 1;
            }
        });
        wkVBinding.recyclerView.setLayoutManager(layoutManager);
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
        adapter.addChildClickViewIds(R.id.coverIv, R.id.previewTv, R.id.playIv);
        adapter.setOnItemChildClickListener((adapter1, view, position) -> {
            FlagshipSearchVideoEntity entity = (FlagshipSearchVideoEntity) adapter1.getData().get(position);
            if (entity != null && entity.getItemType() == FlagshipSearchVideoEntity.TYPE_ITEM) {
                openVideo(entity);
            }
        });
        getData();
    }

    private void getData() {
        ArrayList<Integer> contentType = new ArrayList<>();
        contentType.add(WKContentType.WK_VIDEO);
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
            List<FlagshipSearchVideoEntity> entities = buildEntities(globalSearch.messages);
            if (page == 1 && WKReader.isEmpty(entities)) {
                wkVBinding.nodataTv.setVisibility(View.VISIBLE);
                wkVBinding.refreshLayout.setEnableLoadMore(false);
                adapter.setList(new ArrayList<>());
                return null;
            }
            if (page > 1 && WKReader.isNotEmpty(adapter.getData()) && WKReader.isNotEmpty(entities)) {
                FlagshipSearchVideoEntity last = adapter.getData().get(adapter.getData().size() - 1);
                if (last != null && last.getItemType() == FlagshipSearchVideoEntity.TYPE_ITEM && TextUtils.equals(last.date, entities.get(0).date)) {
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

    private List<FlagshipSearchVideoEntity> buildEntities(List<GlobalMessage> messages) {
        List<FlagshipSearchVideoEntity> result = new ArrayList<>();
        for (GlobalMessage message : messages) {
            if (!(message.getMessageModel() instanceof WKVideoContent videoContent)) {
                continue;
            }
            String date = WKTimeUtils.getInstance().time2YearMonth(message.getTimestamp() * 1000);
            if (WKReader.isEmpty(result) || !TextUtils.equals(result.get(result.size() - 1).date, date)) {
                FlagshipSearchVideoEntity header = new FlagshipSearchVideoEntity();
                header.itemType = FlagshipSearchVideoEntity.TYPE_HEADER;
                header.date = date;
                result.add(header);
            }
            FlagshipSearchVideoEntity item = new FlagshipSearchVideoEntity();
            item.itemType = FlagshipSearchVideoEntity.TYPE_ITEM;
            item.date = date;
            item.message = message;
            item.coverPath = getCoverPath(videoContent);
            item.playPath = getPlayPath(videoContent);
            result.add(item);
        }
        return result;
    }

    private void openVideo(FlagshipSearchVideoEntity entity) {
        if (entity == null || TextUtils.isEmpty(entity.playPath)) {
            return;
        }
        Intent intent = new Intent(this, PlayVideoActivity.class);
        intent.putExtra("coverImg", entity.coverPath);
        intent.putExtra("url", entity.playPath);
        intent.putExtra("title", "");
        if (entity.message != null) {
            intent.putExtra("clientMsgNo", entity.message.getClient_msg_no());
        }
        startActivity(intent);
    }

    private String getCoverPath(WKVideoContent content) {
        if (!TextUtils.isEmpty(content.coverLocalPath)) {
            File file = new File(content.coverLocalPath);
            if (file.exists() && file.length() > 0L) {
                return content.coverLocalPath;
            }
        }
        if (!TextUtils.isEmpty(content.cover)) {
            return WKApiConfig.getShowUrl(content.cover);
        }
        return "";
    }

    private String getPlayPath(WKVideoContent content) {
        if (!TextUtils.isEmpty(content.localPath)) {
            File file = new File(content.localPath);
            if (file.exists() && file.length() > 0L) {
                return content.localPath;
            }
        }
        if (!TextUtils.isEmpty(content.url)) {
            return WKApiConfig.getShowUrl(content.url);
        }
        return "";
    }
}
