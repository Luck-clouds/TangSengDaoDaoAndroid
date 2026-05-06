package com.chat.flagship;

/**
 * 旗舰模块初始化入口
 * Created by Luckclouds .
 */

import android.app.Application;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.chat.base.common.WKCommonModel;
import com.chat.base.config.WKApiConfig;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.endpoint.EndpointCategory;
import com.chat.base.endpoint.entity.CanReactionMenu;
import com.chat.base.endpoint.entity.ChatItemPopupMenu;
import com.chat.base.endpoint.entity.ChatSettingCellMenu;
import com.chat.base.endpoint.entity.EditImgMenu;
import com.chat.base.endpoint.entity.EditMsgMenu;
import com.chat.base.endpoint.entity.MsgConfig;
import com.chat.base.endpoint.entity.MsgReactionMenu;
import com.chat.base.endpoint.entity.ReadMsgMenu;
import com.chat.base.endpoint.entity.ReadMsgDetailMenu;
import com.chat.base.endpoint.entity.SearchChatContentMenu;
import com.chat.base.endpoint.entity.ShowMsgReactionMenu;
import com.chat.base.msg.IConversationContext;
import com.chat.base.msgitem.WKContentType;
import com.chat.base.msgitem.WKMsgItemViewManager;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.ui.components.SwitchView;
import com.chat.base.utils.WKToastUtils;
import com.chat.flagship.chatbg.FlagshipChatBgManager;
import com.chat.flagship.databinding.ItemFlagshipMsgReceiptEntryLayoutBinding;
import com.chat.flagship.msgmodel.WKRichTextContent;
import com.chat.flagship.msgmodel.WKScreenShotContent;
import com.chat.flagship.picture.FlagshipPictureEditorManager;
import com.chat.flagship.provider.WKRichTextProvider;
import com.chat.flagship.provider.WKScreenShotProvider;
import com.chat.flagship.reaction.FlagshipReactionManager;
import com.chat.flagship.receipt.FlagshipMsgReceiptDetailActivity;
import com.chat.flagship.richtext.FlagshipRichTextManager;
import com.chat.flagship.screenshot.FlagshipScreenShotManager;
import com.chat.flagship.search.file.FlagshipSearchFileActivity;
import com.chat.flagship.search.video.FlagshipSearchVideoActivity;
import com.chat.flagship.service.FlagshipReactionModel;
import com.chat.flagship.service.FlagshipMessageReadModel;
import com.chat.flagship.service.FlagshipSettingModel;
import com.chat.flagship.setting.MsgRemindSettingActivity;
import com.chat.base.config.WKConfig;
import com.xinbida.wukongim.entity.WKMsg;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelType;

import java.lang.ref.WeakReference;
import java.util.HashMap;

public class WKFlagshipApplication {
    private WeakReference<Application> applicationRef;

    private WKFlagshipApplication() {
    }

    private static class Binder {
        private static final WKFlagshipApplication INSTANCE = new WKFlagshipApplication();
    }

    public static WKFlagshipApplication getInstance() {
        return Binder.INSTANCE;
    }

    public void init(Application application) {
        applicationRef = new WeakReference<>(application);
        // 注册截屏消息体和对应的系统提示样式。
        WKIM.getInstance().getMsgManager().registerContentMsg(WKScreenShotContent.class);
        WKIM.getInstance().getMsgManager().registerContentMsg(WKRichTextContent.class);
        WKMsgItemViewManager.getInstance().addChatItemViewProvider(WKContentType.screenshot, new WKScreenShotProvider());
        WKMsgItemViewManager.getInstance().addChatItemViewProvider(WKContentType.richText, new WKRichTextProvider());
        EndpointManager.getInstance().setMethod(EndpointCategory.msgConfig + WKContentType.richText, object -> new MsgConfig(true));
        registerEndpoints();
    }

    private void registerEndpoints() {
        EndpointManager.getInstance().setMethod("edit_img", object -> {
            if (object instanceof EditImgMenu menu) {
                FlagshipPictureEditorManager.getInstance().openByEditImg(menu);
            }
            return null;
        });
        EndpointManager.getInstance().setMethod("editMsg", object -> {
            if (object instanceof EditMsgMenu menu) {
                FlagshipPictureEditorManager.getInstance().openByEditMsg(menu);
            }
            return null;
        });
        EndpointManager.getInstance().setMethod("set_chat_bg", object -> {
            if (object instanceof com.chat.base.endpoint.entity.SetChatBgMenu menu) {
                FlagshipChatBgManager.getInstance().apply(menu);
            }
            return null;
        });
        EndpointManager.getInstance().setMethod("show_rich_edit", object -> {
            if (object instanceof IConversationContext conversationContext) {
                FlagshipRichTextManager.getInstance().open(conversationContext);
            }
            return null;
        });
        EndpointManager.getInstance().setMethod("read_msg", object -> {
            if (object instanceof ReadMsgMenu menu) {
                FlagshipMessageReadModel.getInstance().markRead(menu.getChannelID(), menu.getChannelType(), menu.getMsgIds());
            }
            return null;
        });
        EndpointManager.getInstance().setMethod("show_receipt", object -> {
            if (!(object instanceof WKMsg wkMsg)) {
                return false;
            }
            if (!TextUtils.equals(wkMsg.fromUID, WKConfig.getInstance().getUid())) {
                return false;
            }
            if (TextUtils.isEmpty(wkMsg.messageID) || wkMsg.isDeleted == 1
                    || (wkMsg.remoteExtra != null && wkMsg.remoteExtra.revoke == 1)) {
                return false;
            }
            boolean msgReceiptEnabled = wkMsg.setting != null && wkMsg.setting.receipt == 1;
            boolean hasReceiptCount = wkMsg.remoteExtra != null
                    && (wkMsg.remoteExtra.readedCount > 0 || wkMsg.remoteExtra.unreadCount > 0);
            WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(wkMsg.channelID, wkMsg.channelType);
            boolean channelReceiptEnabled = channel != null && channel.receipt == 1;
            return msgReceiptEnabled || channelReceiptEnabled || hasReceiptCount;
        });
        EndpointManager.getInstance().setMethod("show_msg_read_detail", object -> {
            if (!(object instanceof ReadMsgDetailMenu menu) || TextUtils.isEmpty(menu.messageID)) {
                return null;
            }
            Context context = menu.iConversationContext != null && menu.iConversationContext.getChatActivity() != null
                    ? menu.iConversationContext.getChatActivity()
                    : getApplication();
            if (context == null) {
                return null;
            }
            WKMsg wkMsg = WKIM.getInstance().getMsgManager().getWithMessageID(menu.messageID);
            Intent intent = new Intent(context, FlagshipMsgReceiptDetailActivity.class);
            intent.putExtra(FlagshipMsgReceiptDetailActivity.EXTRA_MESSAGE_ID, menu.messageID);
            if (wkMsg != null && wkMsg.remoteExtra != null) {
                intent.putExtra(FlagshipMsgReceiptDetailActivity.EXTRA_READED_COUNT, wkMsg.remoteExtra.readedCount);
                intent.putExtra(FlagshipMsgReceiptDetailActivity.EXTRA_UNREAD_COUNT, wkMsg.remoteExtra.unreadCount);
            }
            if (!(context instanceof Activity)) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            context.startActivity(intent);
            return null;
        });
        EndpointManager.getInstance().setMethod("flagship_edit_text_msg", EndpointCategory.wkChatPopupItem, 85, object -> {
            if (!(object instanceof WKMsg wkMsg)) {
                return null;
            }
            if (wkMsg.type != WKContentType.WK_TEXT) {
                return null;
            }
            if (!TextUtils.equals(wkMsg.fromUID, WKConfig.getInstance().getUid())) {
                return null;
            }
            return new ChatItemPopupMenu(R.mipmap.ic_flagship_msg_edit, getApplication().getString(com.chat.base.R.string.str_edit), (msg, iConversationContext) -> {
                if (iConversationContext != null) {
                    iConversationContext.showEdit(msg);
                }
            });
        });
        EndpointManager.getInstance().setMethod("flagship_copy_rich_text_msg", EndpointCategory.wkChatPopupItem, 90, object -> {
            if (!(object instanceof WKMsg wkMsg)) {
                return null;
            }
            if (wkMsg.type != WKContentType.richText) {
                return null;
            }
            return new ChatItemPopupMenu(com.chat.base.R.mipmap.msg_copy, getApplication().getString(com.chat.base.R.string.copy), (msg, iConversationContext) -> {
                if (!(msg.baseContentMsgModel instanceof WKRichTextContent richTextContent)) {
                    return;
                }
                String copyText = richTextContent.content;
                if (TextUtils.isEmpty(copyText)) {
                    copyText = richTextContent.getDisplayContent();
                }
                if (!TextUtils.isEmpty(copyText)) {
                    ClipboardManager clipboardManager = (ClipboardManager) getApplication().getSystemService(Context.CLIPBOARD_SERVICE);
                    if (clipboardManager != null) {
                        clipboardManager.setPrimaryClip(ClipData.newPlainText("rich_text", copyText));
                    }
                    WKToastUtils.getInstance().showToastNormal(getApplication().getString(com.chat.base.R.string.copyed));
                }
            });
        });
        EndpointManager.getInstance().setMethod("reaction_sticker", object -> FlagshipReactionManager.getReactionStickers());
        EndpointManager.getInstance().setMethod("is_show_reaction", object -> canShowReaction(object));
        EndpointManager.getInstance().setMethod("wk_msg_reaction", object -> {
            if (object instanceof MsgReactionMenu menu) {
                FlagshipReactionModel.getInstance().toggleReaction(menu.wkMsg, menu.emoji, menu.chatAdapter);
            }
            return null;
        });
        EndpointManager.getInstance().setMethod("show_msg_reaction", object -> {
            if (object instanceof ShowMsgReactionMenu menu) {
                FlagshipReactionManager.bindReactionView(menu);
            }
            return null;
        });
        EndpointManager.getInstance().setMethod("refresh_msg_reaction", object -> {
            if (object instanceof ShowMsgReactionMenu menu) {
                FlagshipReactionManager.bindReactionView(menu);
            }
            return null;
        });
        EndpointManager.getInstance().setMethod("stop_reaction_animation", object -> {
            FlagshipReactionManager.dismissReactionUsersDialog();
            return null;
        });
        // 复用聊天页已有的 start/stop_screen_shot 挂点，由旗舰模块接管真正监听逻辑。
        EndpointManager.getInstance().setMethod("start_screen_shot", object -> {
            if (object instanceof Activity activity) {
                FlagshipScreenShotManager.getInstance().start(activity);
            }
            return null;
        });
        EndpointManager.getInstance().setMethod("stop_screen_shot", object -> {
            if (object instanceof Activity activity) {
                FlagshipScreenShotManager.getInstance().stop(activity);
            }
            return null;
        });
        EndpointManager.getInstance().setMethod("msg_remind_view", object -> {
            if (!(object instanceof ChatSettingCellMenu menu)) {
                return null;
            }
            byte channelType = menu.getChannelType();
            if (channelType != WKChannelType.PERSONAL && channelType != WKChannelType.GROUP) {
                return null;
            }
            Context context = menu.getParentLayout().getContext();
            View view = createSettingEntryView(context, context.getString(R.string.flagship_msg_remind_setting), null);
            view.setOnClickListener(v -> {
                Intent intent = new Intent(context, MsgRemindSettingActivity.class);
                intent.putExtra(MsgRemindSettingActivity.EXTRA_CHANNEL_ID, menu.getChannelID());
                intent.putExtra(MsgRemindSettingActivity.EXTRA_CHANNEL_TYPE, channelType);
                if (!(context instanceof android.app.Activity)) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                }
                context.startActivity(intent);
            });
            return view;
        });
        EndpointManager.getInstance().setMethod("msg_receipt_view", object -> {
            if (!(object instanceof ChatSettingCellMenu menu)) {
                return null;
            }
            byte channelType = menu.getChannelType();
            if (channelType != WKChannelType.PERSONAL && channelType != WKChannelType.GROUP) {
                return null;
            }
            Context context = menu.getParentLayout().getContext();
            ItemFlagshipMsgReceiptEntryLayoutBinding binding = ItemFlagshipMsgReceiptEntryLayoutBinding.inflate(LayoutInflater.from(context), menu.getParentLayout(), false);
            binding.nameTv.setText(R.string.flagship_msg_receipt);
            bindReceiptSwitch(binding.receiptSwitch, menu.getChannelID(), channelType);
            return binding.getRoot();
        });
        EndpointManager.getInstance().setMethod("chat_bg_view", object -> {
            if (!(object instanceof ChatSettingCellMenu menu)) {
                return null;
            }
            byte channelType = menu.getChannelType();
            if (channelType != WKChannelType.PERSONAL && channelType != WKChannelType.GROUP) {
                return null;
            }
            Context context = menu.getParentLayout().getContext();
            View view = createSettingEntryView(context, context.getString(R.string.flagship_chat_bg), null);
            view.setOnClickListener(v -> FlagshipChatBgManager.getInstance().openList(context, menu.getChannelID(), channelType));
            return view;
        });
        EndpointManager.getInstance().setMethod("flagship_search_message_with_video", EndpointCategory.wkSearchChatContent, 97, object -> {
            if (!(object instanceof com.xinbida.wukongim.entity.WKChannel channel)) {
                return null;
            }
            return new SearchChatContentMenu(getApplication().getString(R.string.flagship_search_video), (channelID, channelType) -> {
                Intent intent = new Intent(getApplication(), FlagshipSearchVideoActivity.class);
                intent.putExtra("channel_id", channel.channelID);
                intent.putExtra("channel_type", channel.channelType);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getApplication().startActivity(intent);
            });
        });
        EndpointManager.getInstance().setMethod("flagship_search_message_with_file", EndpointCategory.wkSearchChatContent, 97, object -> {
            if (!(object instanceof com.xinbida.wukongim.entity.WKChannel channel)) {
                return null;
            }
            return new SearchChatContentMenu(getApplication().getString(R.string.flagship_search_file), (channelID, channelType) -> {
                Intent intent = new Intent(getApplication(), FlagshipSearchFileActivity.class);
                intent.putExtra("channel_id", channel.channelID);
                intent.putExtra("channel_type", channel.channelType);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getApplication().startActivity(intent);
            });
        });
    }

    private boolean canShowReaction(Object object) {
        if (!(object instanceof CanReactionMenu menu)) {
            return false;
        }
        if (menu.getConfig() != null && !menu.getConfig().isCanShowReaction) {
            return false;
        }
        WKMsg msg = menu.getMMsg();
        if (msg == null) {
            return false;
        }
        if (TextUtils.isEmpty(msg.messageID) || TextUtils.isEmpty(msg.channelID) || msg.messageSeq <= 0) {
            return false;
        }
        if (msg.isDeleted == 1 || msg.flame == 1) {
            return false;
        }
        if (msg.remoteExtra != null && msg.remoteExtra.revoke == 1) {
            return false;
        }
        if (WKContentType.isSystemMsg(msg.type) || WKContentType.isLocalMsg(msg.type)) {
            return false;
        }
        return msg.type != WKContentType.WK_VOICE
                && msg.type != WKContentType.WK_VIDEO
                && msg.type != WKContentType.systemMsg
                && msg.type != WKContentType.screenshot;
    }

    private View createSettingEntryView(Context context, String title, String subtitle) {
        View view = LayoutInflater.from(context).inflate(com.chat.uikit.R.layout.view_group_manage_entry_layout, null, false);
        TextView titleTv = view.findViewById(com.chat.uikit.R.id.titleTv);
        TextView subtitleTv = view.findViewById(com.chat.uikit.R.id.subtitleTv);
        titleTv.setText(title);
        if (!TextUtils.isEmpty(subtitle)) {
            subtitleTv.setVisibility(View.VISIBLE);
            subtitleTv.setText(subtitle);
        } else {
            subtitleTv.setVisibility(View.GONE);
        }
        return view;
    }

    private void bindReceiptSwitch(SwitchView switchView, String channelId, byte channelType) {
        if (switchView == null || TextUtils.isEmpty(channelId)) {
            return;
        }
        switchView.setChecked(readChannelReceipt(channelId, channelType) == 1);
        switchView.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!buttonView.isPressed()) {
                return;
            }
            FlagshipSettingModel.getInstance().updateSetting(channelId, channelType, "receipt", isChecked ? 1 : 0, (code, msg) -> {
                if (code == HttpResponseCode.success) {
                    updateChannelReceiptLocal(channelId, channelType, isChecked ? 1 : 0);
                    WKCommonModel.getInstance().getChannel(channelId, channelType, (resultCode, resultMsg, entity) -> {
                        if (entity != null) {
                            switchView.setChecked(entity.receipt == 1);
                        }
                    });
                } else {
                    switchView.setChecked(!isChecked);
                    if (!TextUtils.isEmpty(msg)) {
                        WKToastUtils.getInstance().showToastNormal(msg);
                    }
                }
            });
        });
        WKCommonModel.getInstance().getChannel(channelId, channelType, (code, msg, entity) -> {
            if (entity != null && switchView.getWindowToken() != null) {
                switchView.setChecked(entity.receipt == 1);
            }
        });
    }

    private int readChannelReceipt(String channelId, byte channelType) {
        WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(channelId, channelType);
        return channel == null ? 0 : channel.receipt;
    }

    private void updateChannelReceiptLocal(String channelId, byte channelType, int receipt) {
        WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(channelId, channelType);
        if (channel == null) {
            channel = new WKChannel(channelId, channelType);
            channel.avatar = WKApiConfig.getShowAvatar(channelId, channelType);
            channel.remoteExtraMap = new HashMap<>();
            channel.localExtra = new HashMap<>();
        }
        channel.receipt = receipt;
        WKIM.getInstance().getChannelManager().saveOrUpdateChannel(channel);
    }

    private Application getApplication() {
        return applicationRef == null ? null : applicationRef.get();
    }
}
