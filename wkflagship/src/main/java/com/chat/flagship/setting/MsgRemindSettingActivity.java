package com.chat.flagship.setting;

/**
 * 会话提醒设置页面
 * Created by Luckclouds .
 */

import android.text.TextUtils;
import android.widget.TextView;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.common.WKCommonModel;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.ui.components.SwitchView;
import com.chat.flagship.R;
import com.chat.flagship.databinding.ActMsgRemindSettingLayoutBinding;
import com.chat.flagship.service.FlagshipSettingModel;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelExtras;
import com.xinbida.wukongim.entity.WKChannelType;

import java.util.HashMap;

public class MsgRemindSettingActivity extends WKBaseActivity<ActMsgRemindSettingLayoutBinding> {
    public static final String EXTRA_CHANNEL_ID = "channelId";
    public static final String EXTRA_CHANNEL_TYPE = "channelType";

    private String channelId;
    private byte channelType;

    @Override
    protected ActMsgRemindSettingLayoutBinding getViewBinding() {
        return ActMsgRemindSettingLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(R.string.flagship_msg_remind_setting);
    }

    @Override
    protected void initPresenter() {
        channelId = getIntent().getStringExtra(EXTRA_CHANNEL_ID);
        channelType = getIntent().getByteExtra(EXTRA_CHANNEL_TYPE, WKChannelType.PERSONAL);
    }

    @Override
    protected void initView() {
        bindLocalState();
        loadRemoteState();
    }

    @Override
    protected void initListener() {
        wkVBinding.screenshotSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (buttonView.isPressed()) {
                updateSetting(WKChannelExtras.screenshot, isChecked, wkVBinding.screenshotSwitch);
            }
        });
        wkVBinding.revokeRemindSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (buttonView.isPressed()) {
                updateSetting(WKChannelExtras.revokeRemind, isChecked, wkVBinding.revokeRemindSwitch);
            }
        });
    }

    private void loadRemoteState() {
        if (TextUtils.isEmpty(channelId)) {
            return;
        }
        // 打开页面后主动拉一次最新频道信息，避免只显示旧缓存。
        WKCommonModel.getInstance().getChannel(channelId, channelType, (code, msg, entity) -> {
            if (code == HttpResponseCode.success) {
                bindLocalState();
            }
        });
    }

    private void bindLocalState() {
        WKChannel channel = getChannel();
        wkVBinding.screenshotSwitch.setChecked(readExtra(channel, WKChannelExtras.screenshot) == 1);
        wkVBinding.revokeRemindSwitch.setChecked(readExtra(channel, WKChannelExtras.revokeRemind) == 1);
    }

    private void updateSetting(String key, boolean isChecked, SwitchView switchView) {
        if (TextUtils.isEmpty(channelId)) {
            switchView.setChecked(!isChecked);
            return;
        }
        // 开关更新成功后同步回写本地频道扩展字段，保证当前页与聊天页读取到的是同一份状态。
        FlagshipSettingModel.getInstance().updateSetting(channelId, channelType, key, isChecked ? 1 : 0, (code, msg) -> {
            if (code == HttpResponseCode.success) {
                updateChannelExtra(key, isChecked ? 1 : 0);
            } else {
                switchView.setChecked(!isChecked);
                if (!TextUtils.isEmpty(msg)) {
                    showToast(msg);
                }
            }
        });
    }

    private WKChannel getChannel() {
        return WKIM.getInstance().getChannelManager().getChannel(channelId, channelType);
    }

    private int readExtra(WKChannel channel, String key) {
        if (channel == null || channel.remoteExtraMap == null || !channel.remoteExtraMap.containsKey(key)) {
            return 0;
        }
        Object value = channel.remoteExtraMap.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (Exception ignored) {
                return 0;
            }
        }
        return 0;
    }

    @SuppressWarnings("unchecked")
    private void updateChannelExtra(String key, int value) {
        WKChannel channel = getChannel();
        if (channel == null) {
            channel = new WKChannel(channelId, channelType);
        }
        HashMap<String, Object> remoteExtraMap;
        if (channel.remoteExtraMap instanceof HashMap) {
            remoteExtraMap = (HashMap<String, Object>) channel.remoteExtraMap;
        } else {
            remoteExtraMap = new HashMap<>();
        }
        remoteExtraMap.put(key, value);
        channel.remoteExtraMap = remoteExtraMap;
        WKIM.getInstance().getChannelManager().saveOrUpdateChannel(channel);
    }
}
