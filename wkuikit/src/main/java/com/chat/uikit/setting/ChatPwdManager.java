package com.chat.uikit.setting;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;

import com.chat.base.config.WKConfig;
import com.chat.base.endpoint.entity.ChatSettingCellMenu;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.ui.Theme;
import com.chat.base.ui.components.SwitchView;
import com.chat.base.utils.WKDialogUtils;
import com.chat.base.utils.WKToastUtils;
import com.chat.uikit.R;
import com.chat.uikit.contacts.service.FriendModel;
import com.chat.uikit.databinding.ItemChatPwdSwitchEntryLayoutBinding;
import com.chat.uikit.group.service.GroupModel;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelExtras;
import com.xinbida.wukongim.entity.WKChannelType;

import java.util.HashMap;

/**
 * 聊天密码入口与会话开关管理。
 * 统一放在安全与隐私模块下，复用聊天详情页已有的预留接口。
 */
public class ChatPwdManager {
    private ChatPwdManager() {
    }

    private static class Binder {
        private static final ChatPwdManager INSTANCE = new ChatPwdManager();
    }

    public static ChatPwdManager getInstance() {
        return Binder.INSTANCE;
    }

    /**
     * 打开聊天密码设置页。
     */
    public void openSetting(Context context) {
        if (context == null) {
            return;
        }
        Intent intent = ChatPwdSettingActivity.buildIntent(context);
        if (!(context instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        context.startActivity(intent);
    }

    /**
     * 构造单聊/群聊详情中的聊天密码开关项。
     */
    public View buildSettingView(Object object) {
        if (!(object instanceof ChatSettingCellMenu menu)) {
            return null;
        }
        byte channelType = menu.getChannelType();
        if (channelType != WKChannelType.PERSONAL && channelType != WKChannelType.GROUP) {
            return null;
        }
        Context context = menu.getParentLayout().getContext();
        ItemChatPwdSwitchEntryLayoutBinding binding = ItemChatPwdSwitchEntryLayoutBinding.inflate(LayoutInflater.from(context), menu.getParentLayout(), false);
        binding.nameTv.setText(R.string.chat_pwd);
        bindChatPwdSwitch(context, menu.getChannelID(), channelType, binding.chatPwdSwitch);
        return binding.getRoot();
    }

    private void bindChatPwdSwitch(Context context, String channelId, byte channelType, SwitchView switchView) {
        if (switchView == null || TextUtils.isEmpty(channelId)) {
            return;
        }
        switchView.setChecked(readChannelChatPwd(channelId, channelType) == 1);
        switchView.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!buttonView.isPressed()) {
                return;
            }
            if (isChecked && !hasGlobalChatPassword()) {
                switchView.setChecked(false);
                showNeedSetPasswordDialog(context);
                return;
            }
            updateChannelChatPwd(channelId, channelType, isChecked ? 1 : 0, (code, msg) -> {
                if (code == HttpResponseCode.success) {
                    updateChannelChatPwdLocal(channelId, channelType, isChecked ? 1 : 0);
                } else {
                    switchView.setChecked(!isChecked);
                    WKToastUtils.getInstance().showToastNormal(TextUtils.isEmpty(msg) ? context.getString(R.string.unknown_error) : msg);
                }
            });
        });
    }

    /**
     * 未设置全局聊天密码时，先引导用户去安全与隐私页完成设置。
     */
    private void showNeedSetPasswordDialog(Context context) {
        if (context instanceof Activity activity) {
            WKDialogUtils.getInstance().showDialog(activity,
                    activity.getString(R.string.chat_pwd),
                    activity.getString(R.string.chat_pwd_need_set_desc),
                    false,
                    activity.getString(R.string.cancel),
                    activity.getString(R.string.chat_pwd_go_set),
                    0,
                    Theme.colorAccount,
                    index -> {
                        if (index == 1) {
                            openSetting(activity);
                        }
                    });
            return;
        }
        openSetting(context);
    }

    private boolean hasGlobalChatPassword() {
        return WKConfig.getInstance().getUserInfo() != null
                && !TextUtils.isEmpty(WKConfig.getInstance().getUserInfo().chat_pwd);
    }

    private void updateChannelChatPwd(String channelId, byte channelType, int value, com.chat.base.net.ICommonListener listener) {
        if (channelType == WKChannelType.PERSONAL) {
            FriendModel.getInstance().updateUserSetting(channelId, "chat_pwd_on", value, listener);
        } else {
            GroupModel.getInstance().updateGroupSetting(channelId, "chat_pwd_on", value, listener);
        }
    }

    private int readChannelChatPwd(String channelId, byte channelType) {
        WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(channelId, channelType);
        if (channel == null || channel.remoteExtraMap == null || !channel.remoteExtraMap.containsKey(WKChannelExtras.chatPwdOn)) {
            return 0;
        }
        Object value = channel.remoteExtraMap.get(WKChannelExtras.chatPwdOn);
        return value instanceof Integer ? (int) value : 0;
    }

    /**
     * 服务端开关更新成功后先同步本地频道扩展，保证当前页面与列表状态立即一致。
     */
    private void updateChannelChatPwdLocal(String channelId, byte channelType, int value) {
        WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(channelId, channelType);
        if (channel == null) {
            channel = new WKChannel(channelId, channelType);
            channel.remoteExtraMap = new HashMap<>();
            channel.localExtra = new HashMap<>();
        } else if (channel.remoteExtraMap == null) {
            channel.remoteExtraMap = new HashMap<>();
        }
        channel.remoteExtraMap.put(WKChannelExtras.chatPwdOn, value);
        WKIM.getInstance().getChannelManager().saveOrUpdateChannel(channel);
    }
}
