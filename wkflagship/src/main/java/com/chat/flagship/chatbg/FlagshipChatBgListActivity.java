package com.chat.flagship.chatbg;

import android.app.Activity;
import android.content.Intent;
import android.text.TextUtils;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.recyclerview.widget.GridLayoutManager;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.glide.ChooseMimeType;
import com.chat.base.glide.ChooseResult;
import com.chat.base.glide.GlideUtils;
import com.chat.base.net.IRequestResultListener;
import com.chat.base.utils.WKReader;
import com.chat.flagship.R;
import com.chat.flagship.databinding.ActFlagshipChatBgListLayoutBinding;
import com.xinbida.wukongim.entity.WKChannelType;

import java.util.ArrayList;
import java.util.List;

/**
 * 聊天背景列表页
 * Created by Luckclouds and chatGPT.
 */
public class FlagshipChatBgListActivity extends WKBaseActivity<ActFlagshipChatBgListLayoutBinding> {
    private static final String TAG = "FlagshipChatBg";
    public static final String EXTRA_CHANNEL_ID = "channel_id";
    public static final String EXTRA_CHANNEL_TYPE = "channel_type";

    private String channelId;
    private byte channelType;
    private FlagshipChatBgAdapter adapter;
    private FlagshipChatBgConfig currentConfig;

    private final ActivityResultLauncher<Intent> previewResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    setResult(Activity.RESULT_OK);
                    finish();
                }
            }
    );

    @Override
    protected ActFlagshipChatBgListLayoutBinding getViewBinding() {
        return ActFlagshipChatBgListLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(R.string.flagship_chat_bg);
    }

    @Override
    protected String getRightTvText(TextView textView) {
        return getString(R.string.flagship_chat_bg_album);
    }

    @Override
    protected int getRightIvResourceId(ImageView imageView) {
        return -1;
    }

    @Override
    protected void rightLayoutClick() {
        GlideUtils.getInstance().chooseIMG(this, 1, false, ChooseMimeType.img, false, new GlideUtils.ISelectBack() {
            @Override
            public void onBack(List<ChooseResult> paths) {
                if (WKReader.isEmpty(paths) || paths.get(0) == null || TextUtils.isEmpty(paths.get(0).path)) {
                    return;
                }
                openLocalPreview(paths.get(0).path);
            }

            @Override
            public void onCancel() {
            }
        });
    }

    @Override
    protected void initPresenter() {
        channelId = getIntent().getStringExtra(EXTRA_CHANNEL_ID);
        channelType = getIntent().getByteExtra(EXTRA_CHANNEL_TYPE, WKChannelType.PERSONAL);
        currentConfig = FlagshipChatBgStore.getConfig(channelId, channelType);
        Log.d(TAG, "list init channelId=" + channelId + " channelType=" + channelType + " currentType=" + (currentConfig == null ? "default" : currentConfig.type));
    }

    @Override
    protected void initView() {
        adapter = new FlagshipChatBgAdapter();
        wkVBinding.recyclerView.setLayoutManager(new GridLayoutManager(this, 3));
        wkVBinding.recyclerView.setAdapter(adapter);
        adapter.setCurrentConfig(currentConfig);
    }

    @Override
    protected void initListener() {
        adapter.setOnItemClickListener(this::openPresetPreview);
    }

    @Override
    protected void initData() {
        loadingPopup.show();
        FlagshipChatBgModel.getInstance().getChatBgList(new IRequestResultListener<List<FlagshipChatBgItem>>() {
            @Override
            public void onSuccess(List<FlagshipChatBgItem> result) {
                loadingPopup.dismiss();
                Log.d(TAG, "list api success count=" + (result == null ? 0 : result.size()));
                List<FlagshipChatBgItem> items = new ArrayList<>();
                FlagshipChatBgItem defaultItem = new FlagshipChatBgItem();
                defaultItem.isDefault = true;
                items.add(defaultItem);
                if (result != null) {
                    items.addAll(result);
                }
                adapter.setData(items);
                adapter.setCurrentConfig(currentConfig);
            }

            @Override
            public void onFail(int code, String msg) {
                loadingPopup.dismiss();
                Log.e(TAG, "list api fail code=" + code + " msg=" + msg);
                showToast(TextUtils.isEmpty(msg) ? getString(R.string.flagship_chat_bg_load_failed) : msg);
            }
        });
    }

    private void openPresetPreview(FlagshipChatBgItem item) {
        Log.d(TAG, "openPresetPreview isDefault=" + (item != null && item.isDefault) + " cover=" + (item == null ? "" : item.cover) + " url=" + (item == null ? "" : item.url) + " isSvg=" + (item == null ? -1 : item.isSvg));
        Intent intent = new Intent(this, FlagshipChatBgPreviewActivity.class);
        intent.putExtra(FlagshipChatBgPreviewActivity.EXTRA_CHANNEL_ID, channelId);
        intent.putExtra(FlagshipChatBgPreviewActivity.EXTRA_CHANNEL_TYPE, channelType);
        intent.putExtra(FlagshipChatBgPreviewActivity.EXTRA_PREVIEW_MODE, item != null && item.isDefault
                ? FlagshipChatBgPreviewActivity.MODE_DEFAULT
                : FlagshipChatBgPreviewActivity.MODE_PRESET);
        intent.putExtra(FlagshipChatBgPreviewActivity.EXTRA_CHAT_BG_ITEM, item);
        intent.putExtra(FlagshipChatBgPreviewActivity.EXTRA_SHOW_BLUR, false);
        previewResultLauncher.launch(intent);
    }

    private void openLocalPreview(String localPath) {
        Log.d(TAG, "openLocalPreview path=" + localPath);
        Intent intent = new Intent(this, FlagshipChatBgPreviewActivity.class);
        intent.putExtra(FlagshipChatBgPreviewActivity.EXTRA_CHANNEL_ID, channelId);
        intent.putExtra(FlagshipChatBgPreviewActivity.EXTRA_CHANNEL_TYPE, channelType);
        intent.putExtra(FlagshipChatBgPreviewActivity.EXTRA_PREVIEW_MODE, FlagshipChatBgPreviewActivity.MODE_LOCAL);
        intent.putExtra(FlagshipChatBgPreviewActivity.EXTRA_LOCAL_PATH, localPath);
        intent.putExtra(FlagshipChatBgPreviewActivity.EXTRA_SHOW_BLUR, true);
        previewResultLauncher.launch(intent);
    }
}
