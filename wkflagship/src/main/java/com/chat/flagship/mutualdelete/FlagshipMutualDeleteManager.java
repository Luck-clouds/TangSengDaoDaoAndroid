package com.chat.flagship.mutualdelete;

import android.app.Activity;
import android.content.Context;
import android.text.TextUtils;

import com.chat.base.WKBaseApplication;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.endpoint.entity.ChatItemPopupMenu;
import com.chat.base.msg.ChatAdapter;
import com.chat.base.msg.IConversationContext;
import com.chat.base.msgitem.WKContentType;
import com.chat.base.msgitem.WKUIChatMsgItemEntity;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.utils.WKToastUtils;
import com.chat.flagship.R;
import com.chat.uikit.message.MsgModel;
import com.xinbida.wukongim.entity.WKChannelType;
import com.xinbida.wukongim.entity.WKMsg;

import java.util.List;

/**
 * 负责旗舰模块里的“双向删除”菜单展示和交互。
 */
public class FlagshipMutualDeleteManager {

    private FlagshipMutualDeleteManager() {
    }

    private static class Binder {
        private static final FlagshipMutualDeleteManager INSTANCE = new FlagshipMutualDeleteManager();
    }

    public static FlagshipMutualDeleteManager getInstance() {
        return Binder.INSTANCE;
    }

    public ChatItemPopupMenu buildMenu(Object object) {
        if (!(object instanceof WKMsg msg) || !canMutualDelete(msg)) {
            return null;
        }
        Context context = WKBaseApplication.getInstance().getContext();
        String title = context == null ? "双向删除" : context.getString(R.string.flagship_mutual_delete);
        return new ChatItemPopupMenu(com.chat.base.R.mipmap.msg_delete, title, this::mutualDelete);
    }

    private void mutualDelete(WKMsg msg, IConversationContext conversationContext) {
        Activity activity = conversationContext.getChatActivity();
        FlagshipMutualDeleteModel.getInstance().mutualDelete(msg, (code, errorMsg) -> {
            if (code == HttpResponseCode.success || code == 0) {
                // 先更新当前页，再补一次 extra 同步，让其他会话状态和本地数据库跟上。
                removeMsgFromAdapter(msg, conversationContext.getChatAdapter());
                MsgModel.getInstance().syncExtraMsg(msg.channelID, msg.channelType);
                return;
            }
            if (activity != null) {
                String showMsg = TextUtils.isEmpty(errorMsg)
                        ? activity.getString(R.string.flagship_mutual_delete_failed)
                        : errorMsg;
                WKToastUtils.getInstance().showToastNormal(showMsg);
            }
        });
    }

    private boolean canMutualDelete(WKMsg msg) {
        if (msg == null || TextUtils.isEmpty(msg.channelID) || TextUtils.isEmpty(msg.messageID) || msg.messageSeq <= 0) {
            return false;
        }
        if (msg.channelType != WKChannelType.PERSONAL && msg.channelType != WKChannelType.GROUP) {
            return false;
        }
        if (msg.isDeleted == 1 || msg.flame == 1) {
            return false;
        }
        if (msg.remoteExtra != null && (msg.remoteExtra.revoke == 1 || msg.remoteExtra.isMutualDeleted == 1)) {
            return false;
        }
        if (WKContentType.isSystemMsg(msg.type) || WKContentType.isLocalMsg(msg.type)) {
            return false;
        }
        return msg.type != WKContentType.screenshot && msg.type != WKContentType.approveGroupMember;
    }

    private void removeMsgFromAdapter(WKMsg msg, ChatAdapter chatAdapter) {
        if (msg == null || chatAdapter == null || chatAdapter.getData() == null || chatAdapter.getData().isEmpty()) {
            return;
        }
        EndpointManager.getInstance().invoke("stop_reaction_animation", null);
        int tempIndex = -1;
        List<WKUIChatMsgItemEntity> data = chatAdapter.getData();
        for (int i = 0, size = data.size(); i < size; i++) {
            WKMsg itemMsg = data.get(i).wkMsg;
            if (itemMsg == null) {
                continue;
            }
            if (itemMsg.clientSeq == msg.clientSeq || TextUtils.equals(itemMsg.clientMsgNO, msg.clientMsgNO)) {
                tempIndex = i;
                if (i - 1 >= 0) {
                    data.get(i - 1).nextMsg = i + 1 <= data.size() - 1 ? data.get(i + 1).wkMsg : null;
                }
                if (i + 1 <= data.size() - 1) {
                    data.get(i + 1).previousMsg = i - 1 >= 0 ? data.get(i - 1).wkMsg : null;
                }
                chatAdapter.removeAt(i);
                break;
            }
        }
        if (tempIndex < 0) {
            return;
        }
        int timeIndex = tempIndex - 1;
        if (timeIndex < 0 || timeIndex > chatAdapter.getData().size() - 1) {
            return;
        }
        // 和聊天页现有移除规则保持一致，消息删掉后把孤立的时间分隔条一起收掉。
        if (chatAdapter.getData().get(timeIndex).wkMsg.type == WKContentType.msgPromptTime) {
            if (timeIndex - 1 >= 0) {
                chatAdapter.getData().get(timeIndex - 1).nextMsg =
                        timeIndex + 1 <= chatAdapter.getData().size() - 1 ? chatAdapter.getData().get(timeIndex + 1).wkMsg : null;
            }
            if (timeIndex + 1 <= chatAdapter.getData().size() - 1) {
                chatAdapter.getData().get(timeIndex + 1).previousMsg =
                        timeIndex - 1 >= 0 ? chatAdapter.getData().get(timeIndex - 1).wkMsg : null;
            }
            chatAdapter.removeAt(timeIndex);
        }
    }
}
