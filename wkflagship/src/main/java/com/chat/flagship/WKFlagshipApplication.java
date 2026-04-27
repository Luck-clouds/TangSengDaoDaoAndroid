package com.chat.flagship;

/**
 * 旗舰模块初始化入口
 * Created by Luckclouds .
 */

import android.app.Application;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.widget.TextView;

import com.chat.base.endpoint.EndpointManager;
import com.chat.base.endpoint.EndpointCategory;
import com.chat.base.endpoint.entity.CanReactionMenu;
import com.chat.base.endpoint.entity.ChatSettingCellMenu;
import com.chat.base.endpoint.entity.EditImgMenu;
import com.chat.base.endpoint.entity.EditMsgMenu;
import com.chat.base.endpoint.entity.MsgReactionMenu;
import com.chat.base.endpoint.entity.SearchChatContentMenu;
import com.chat.base.endpoint.entity.ShowMsgReactionMenu;
import com.chat.base.msgitem.WKContentType;
import com.chat.base.msgitem.WKMsgItemViewManager;
import com.chat.flagship.databinding.ItemMsgRemindEntryLayoutBinding;
import com.chat.flagship.msgmodel.WKScreenShotContent;
import com.chat.flagship.picture.FlagshipPictureEditorManager;
import com.chat.flagship.provider.WKScreenShotProvider;
import com.chat.flagship.reaction.FlagshipReactionManager;
import com.chat.flagship.screenshot.FlagshipScreenShotManager;
import com.chat.flagship.search.file.FlagshipSearchFileActivity;
import com.chat.flagship.search.video.FlagshipSearchVideoActivity;
import com.chat.flagship.service.FlagshipReactionModel;
import com.chat.flagship.setting.MsgRemindSettingActivity;
import com.xinbida.wukongim.entity.WKMsg;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannelType;

import java.lang.ref.WeakReference;

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
        WKMsgItemViewManager.getInstance().addChatItemViewProvider(WKContentType.screenshot, new WKScreenShotProvider());
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
            if (!(object instanceof ChatSettingCellMenu)) {
                return null;
            }
            ChatSettingCellMenu menu = (ChatSettingCellMenu) object;
            byte channelType = menu.getChannelType();
            if (channelType != WKChannelType.PERSONAL && channelType != WKChannelType.GROUP) {
                return null;
            }
            Context context = menu.getParentLayout().getContext();
            ItemMsgRemindEntryLayoutBinding binding = ItemMsgRemindEntryLayoutBinding.inflate(LayoutInflater.from(context), menu.getParentLayout(), false);
            binding.nameTv.setText(R.string.flagship_msg_remind_setting);
            binding.getRoot().setOnClickListener(v -> {
                Intent intent = new Intent(context, MsgRemindSettingActivity.class);
                intent.putExtra(MsgRemindSettingActivity.EXTRA_CHANNEL_ID, menu.getChannelID());
                intent.putExtra(MsgRemindSettingActivity.EXTRA_CHANNEL_TYPE, channelType);
                if (!(context instanceof android.app.Activity)) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                }
                context.startActivity(intent);
            });
            return binding.getRoot();
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

    private Application getApplication() {
        return applicationRef == null ? null : applicationRef.get();
    }
}
