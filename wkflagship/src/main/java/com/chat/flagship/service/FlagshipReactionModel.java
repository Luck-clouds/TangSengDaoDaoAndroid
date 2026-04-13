package com.chat.flagship.service;

/**
 * 消息回应业务模型
 * Created by Luckclouds and chatGPT.
 */

import android.text.TextUtils;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.chat.base.base.WKBaseModel;
import com.chat.base.msg.ChatAdapter;
import com.chat.base.msgitem.WKContentType;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.net.IRequestResultListener;
import com.chat.base.net.entity.CommonResponse;
import com.chat.base.utils.WKToastUtils;
import com.chat.flagship.R;
import com.chat.flagship.entity.FlagshipReactionSyncEntity;
import com.chat.flagship.reaction.FlagshipReactionManager;
import com.xinbida.wukongim.entity.WKMsg;
import com.xinbida.wukongim.entity.WKMsgReaction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public class FlagshipReactionModel extends WKBaseModel {
    private static final int SYNC_LIMIT = 200;
    private final Map<ChatAdapter, Set<String>> syncedChannelMap = Collections.synchronizedMap(new WeakHashMap<>());

    private FlagshipReactionModel() {
    }

    private static class Binder {
        private static final FlagshipReactionModel INSTANCE = new FlagshipReactionModel();
    }

    public static FlagshipReactionModel getInstance() {
        return Binder.INSTANCE;
    }

    public void toggleReaction(WKMsg msg, String emoji, ChatAdapter chatAdapter) {
        if (!canToggle(msg) || TextUtils.isEmpty(emoji)) {
            return;
        }
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("message_id", msg.messageID);
        jsonObject.put("channel_id", msg.channelID);
        jsonObject.put("channel_type", msg.channelType);
        jsonObject.put("emoji", emoji);
        request(createService(FlagshipReactionService.class).toggleReaction(jsonObject), new IRequestResultListener<CommonResponse>() {
            @Override
            public void onSuccess(CommonResponse result) {
                if (result != null && (result.status == HttpResponseCode.success || result.status == 0)) {
                    syncMessageReaction(msg, chatAdapter, true);
                    return;
                }
                if (chatAdapter != null && chatAdapter.getConversationContext() != null && chatAdapter.getConversationContext().getChatActivity() != null && result != null && !TextUtils.isEmpty(result.msg)) {
                    WKToastUtils.getInstance().showToastNormal(result.msg);
                }
            }

            @Override
            public void onFail(int code, String msgText) {
                if (chatAdapter != null && chatAdapter.getConversationContext() != null && chatAdapter.getConversationContext().getChatActivity() != null) {
                    WKToastUtils.getInstance().showToastNormal(TextUtils.isEmpty(msgText) ? chatAdapter.getConversationContext().getChatActivity().getString(R.string.flagship_reaction_action_failed) : msgText);
                }
            }
        });
    }

    public void syncMessageReaction(WKMsg msg, ChatAdapter chatAdapter, boolean quiet) {
        if (!canToggle(msg)) {
            return;
        }
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("seq", msg.messageSeq);
        jsonObject.put("channel_id", msg.channelID);
        jsonObject.put("channel_type", msg.channelType);
        jsonObject.put("limit", SYNC_LIMIT);
        request(createService(FlagshipReactionService.class).syncReaction(jsonObject), new IRequestResultListener<JSONArray>() {
            @Override
            public void onSuccess(JSONArray result) {
                List<FlagshipReactionSyncEntity> syncEntities = parseSyncEntities(result);
                List<WKMsgReaction> reactionList = FlagshipReactionManager.buildMessageActiveReactions(syncEntities, msg.messageID);
                msg.reactionList = reactionList;
                updateChatAdapterReaction(chatAdapter, msg.messageID, reactionList);
            }

            @Override
            public void onFail(int code, String msgText) {
                if (!quiet && chatAdapter != null && chatAdapter.getConversationContext() != null && chatAdapter.getConversationContext().getChatActivity() != null) {
                    WKToastUtils.getInstance().showToastNormal(TextUtils.isEmpty(msgText) ? chatAdapter.getConversationContext().getChatActivity().getString(R.string.flagship_reaction_sync_failed) : msgText);
                }
            }
        });
    }

    public void ensureChannelReactionSynced(WKMsg msg, ChatAdapter chatAdapter) {
        if (!canToggle(msg) || chatAdapter == null) {
            return;
        }
        String syncKey = buildChannelSyncKey(msg.channelID, msg.channelType);
        synchronized (syncedChannelMap) {
            Set<String> channelKeys = syncedChannelMap.get(chatAdapter);
            if (channelKeys == null) {
                channelKeys = new HashSet<>();
                syncedChannelMap.put(chatAdapter, channelKeys);
            }
            if (channelKeys.contains(syncKey)) {
                return;
            }
            channelKeys.add(syncKey);
        }
        syncChannelReaction(msg.channelID, msg.channelType, chatAdapter, true, syncKey);
    }

    private void syncChannelReaction(String channelId, byte channelType, ChatAdapter chatAdapter, boolean quiet, String syncKey) {
        if (TextUtils.isEmpty(channelId) || chatAdapter == null) {
            return;
        }
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("seq", 0);
        jsonObject.put("channel_id", channelId);
        jsonObject.put("channel_type", channelType);
        jsonObject.put("limit", SYNC_LIMIT);
        request(createService(FlagshipReactionService.class).syncReaction(jsonObject), new IRequestResultListener<JSONArray>() {
            @Override
            public void onSuccess(JSONArray result) {
                List<FlagshipReactionSyncEntity> syncEntities = parseSyncEntities(result);
                applyChannelReactionSync(chatAdapter, channelId, channelType, syncEntities);
            }

            @Override
            public void onFail(int code, String msgText) {
                removeChannelSyncMark(chatAdapter, syncKey);
                if (!quiet && chatAdapter.getConversationContext() != null && chatAdapter.getConversationContext().getChatActivity() != null) {
                    WKToastUtils.getInstance().showToastNormal(TextUtils.isEmpty(msgText) ? chatAdapter.getConversationContext().getChatActivity().getString(R.string.flagship_reaction_sync_failed) : msgText);
                }
            }
        });
    }

    private boolean canToggle(WKMsg msg) {
        if (msg == null || TextUtils.isEmpty(msg.messageID) || TextUtils.isEmpty(msg.channelID) || msg.messageSeq <= 0) {
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
        return msg.type != WKContentType.WK_VOICE && msg.type != WKContentType.WK_VIDEO && msg.type != WKContentType.systemMsg && msg.type != WKContentType.screenshot;
    }

    private List<FlagshipReactionSyncEntity> parseSyncEntities(JSONArray array) {
        List<FlagshipReactionSyncEntity> list = new ArrayList<>();
        if (array == null) {
            return list;
        }
        for (int i = 0, size = array.size(); i < size; i++) {
            JSONObject object = array.getJSONObject(i);
            if (object == null) {
                continue;
            }
            FlagshipReactionSyncEntity entity = new FlagshipReactionSyncEntity();
            entity.messageId = object.getString("message_id");
            entity.uid = object.getString("uid");
            entity.name = object.getString("name");
            entity.channelId = object.getString("channel_id");
            Integer channelType = object.getInteger("channel_type");
            entity.channelType = channelType == null ? 0 : channelType.byteValue();
            Long seq = object.getLong("seq");
            entity.seq = seq == null ? 0L : seq;
            entity.emoji = object.getString("emoji");
            Integer isDeleted = object.getInteger("is_deleted");
            entity.isDeleted = isDeleted == null ? 0 : isDeleted;
            entity.createdAt = object.getString("created_at");
            list.add(entity);
        }
        return list;
    }

    private void applyChannelReactionSync(ChatAdapter chatAdapter, String channelId, byte channelType, List<FlagshipReactionSyncEntity> syncEntities) {
        if (chatAdapter == null) {
            return;
        }
        Map<String, List<FlagshipReactionSyncEntity>> messageMap = new LinkedHashMap<>();
        for (FlagshipReactionSyncEntity entity : syncEntities) {
            if (entity == null || TextUtils.isEmpty(entity.messageId) || !TextUtils.equals(channelId, entity.channelId) || entity.channelType != channelType) {
                continue;
            }
            List<FlagshipReactionSyncEntity> list = messageMap.get(entity.messageId);
            if (list == null) {
                list = new ArrayList<>();
                messageMap.put(entity.messageId, list);
            }
            list.add(entity);
        }
        List<com.chat.base.msgitem.WKUIChatMsgItemEntity> data = chatAdapter.getData();
        if (data == null || data.isEmpty()) {
            return;
        }
        for (int i = 0, size = data.size(); i < size; i++) {
            WKMsg wkMsg = data.get(i).wkMsg;
            if (wkMsg == null || !TextUtils.equals(channelId, wkMsg.channelID) || wkMsg.channelType != channelType || TextUtils.isEmpty(wkMsg.messageID)) {
                continue;
            }
            List<FlagshipReactionSyncEntity> messageEntities = messageMap.get(wkMsg.messageID);
            if (messageEntities == null) {
                continue;
            }
            List<WKMsgReaction> reactionList = FlagshipReactionManager.buildMessageActiveReactions(messageEntities, wkMsg.messageID);
            wkMsg.reactionList = reactionList;
            chatAdapter.notifyReaction(i, reactionList);
        }
    }

    private void updateChatAdapterReaction(ChatAdapter chatAdapter, String messageId, List<WKMsgReaction> reactionList) {
        if (chatAdapter == null || TextUtils.isEmpty(messageId)) {
            return;
        }
        List<com.chat.base.msgitem.WKUIChatMsgItemEntity> data = chatAdapter.getData();
        if (data == null || data.isEmpty()) {
            return;
        }
        for (int i = 0, size = data.size(); i < size; i++) {
            if (data.get(i).wkMsg != null && TextUtils.equals(messageId, data.get(i).wkMsg.messageID)) {
                data.get(i).wkMsg.reactionList = reactionList;
                chatAdapter.notifyReaction(i, reactionList);
                break;
            }
        }
    }

    private void removeChannelSyncMark(ChatAdapter chatAdapter, String syncKey) {
        if (chatAdapter == null || TextUtils.isEmpty(syncKey)) {
            return;
        }
        synchronized (syncedChannelMap) {
            Set<String> channelKeys = syncedChannelMap.get(chatAdapter);
            if (channelKeys != null) {
                channelKeys.remove(syncKey);
                if (channelKeys.isEmpty()) {
                    syncedChannelMap.remove(chatAdapter);
                }
            }
        }
    }

    private String buildChannelSyncKey(String channelId, byte channelType) {
        return channelId + "_" + channelType;
    }
}
