package com.chat.rtc;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.chat.base.config.WKConfig;
import com.chat.base.config.WKConstants;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.endpoint.entity.CreateVideoCallMenu;
import com.chat.base.endpoint.entity.RTCMenu;
import com.chat.base.msg.IConversationContext;
import com.chat.base.msgitem.WKContentType;
import com.chat.base.msgitem.WKMsgItemViewManager;
import com.chat.base.net.IRequestResultListener;
import com.chat.base.utils.WKToastUtils;
import com.chat.rtc.entity.RtcCallPayload;
import com.chat.rtc.entity.RtcCallResp;
import com.chat.rtc.entity.RtcChannelStateResp;
import com.chat.rtc.entity.RtcSession;
import com.chat.rtc.livekit.RtcLiveKitClient;
import com.chat.rtc.message.RtcNoticeContent;
import com.chat.rtc.message.RtcNoticeProvider;
import com.chat.rtc.message.RtcRecordProvider;
import com.chat.rtc.message.RtcRecordContent;
import com.chat.rtc.net.RtcModel;
import com.chat.rtc.ui.RtcCallActivity;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKCMDKeys;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelMember;
import com.xinbida.wukongim.entity.WKChannelType;
import com.xinbida.wukongim.entity.WKMsg;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

public class RtcManager {
    private static final String TAG = "WKRTC";
    private static final long ENDED_SESSION_CLEAR_DELAY_MS = 1200L;
    private static final long CHANNEL_PROBE_MIN_INTERVAL_MS = 1200L;

    public interface SessionListener {
        void onSessionChanged(RtcSession session);
    }

    private static class Binder {
        private static final RtcManager MANAGER = new RtcManager();
    }

    public static RtcManager getInstance() {
        return Binder.MANAGER;
    }

    private Context appContext;
    private RtcSession session;
    private final List<SessionListener> listeners = new ArrayList<>();
    private final List<String> recentlyEndedCallIds = new ArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final LinkedHashSet<String> probingChannelKeys = new LinkedHashSet<>();
    private final HashMap<String, Long> lastProbeStartedAt = new HashMap<>();
    private boolean groupStateSyncing = false;
    private final Runnable groupCallStateSyncer = new Runnable() {
        @Override
        public void run() {
            if (!shouldSyncActiveGroupCallState()) {
                return;
            }
            syncActiveGroupCallState();
            mainHandler.postDelayed(this, 3000L);
        }
    };
    public void init(Context context) {
        appContext = context.getApplicationContext();
        Log.i(TAG, "rtc initialized");
        registerMessages();
        registerEndpoints();
    }

    private void registerMessages() {
        WKIM.getInstance().getMsgManager().registerContentMsg(RtcNoticeContent.class);
        WKIM.getInstance().getMsgManager().registerContentMsg(RtcRecordContent.class);
        WKMsgItemViewManager.getInstance().addChatItemViewProvider(WKContentType.rtcNotice, new RtcNoticeProvider());
        WKMsgItemViewManager.getInstance().addChatItemViewProvider(WKContentType.rtcRecord, new RtcRecordProvider());
        WKIM.getInstance().getMsgManager().addMessageStoreBeforeIntercept(msg -> {
            normalizeRtcMessage(msg);
            return true;
        });
        WKIM.getInstance().getMsgManager().addOnNewMsgListener("wkrtc_global_notice", msgList -> {
            if (msgList == null || msgList.isEmpty()) {
                return;
            }
            for (WKMsg msg : msgList) {
                normalizeRtcMessage(msg);
            }
        });
    }

    private void normalizeRtcMessage(WKMsg msg) {
        if (msg == null || TextUtils.isEmpty(msg.content)) {
            return;
        }
        try {
            JSONObject content = new JSONObject(msg.content);
            if (isRtcNoticePayload(content)) {
                msg.type = WKContentType.rtcNotice;
                handleRtcNoticeMessage(msg);
            } else if (isRtcRecordPayload(content)) {
                msg.type = WKContentType.rtcRecord;
            }
        } catch (Exception ignored) {
        }
    }

    private void handleRtcNoticeMessage(WKMsg msg) {
        // rtc_notice is a persistent group-message entry only. It must never be
        // promoted to a ringing incoming call; the user explicitly taps it to join.
    }

    private boolean isRtcNoticePayload(JSONObject content) {
        if (content == null) {
            return false;
        }
        String type = content.optString("type");
        return "rtc_notice".equals(type) || content.optInt("type", -1) == WKContentType.rtcNotice;
    }

    private boolean isRtcRecordPayload(JSONObject content) {
        if (content == null) {
            return false;
        }
        String type = content.optString("type");
        return "rtc_record".equals(type) || content.optInt("type", -1) == WKContentType.rtcRecord;
    }

    public void addListener(SessionListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(SessionListener listener) {
        listeners.remove(listener);
    }

    public RtcSession currentSession() {
        return session;
    }

    public boolean isCalling() {
        return session != null && !RtcSession.ENDED.equals(session.status);
    }

    public String getDeviceId() {
        return WKConstants.getInstallDeviceID();
    }

    private void registerEndpoints() {
        EndpointManager.getInstance().setMethod("is_register_rtc", object -> true);
        EndpointManager.getInstance().setMethod("rtc_is_calling", object -> isCalling());
        EndpointManager.getInstance().setMethod("rtc_is_minimized", object -> session != null && session.minimized);
        EndpointManager.getInstance().setMethod("rtc_set_minimized", object -> {
            if (session != null && object instanceof Boolean) {
                session.minimized = (Boolean) object;
                notifyChanged();
            }
            return true;
        });
        EndpointManager.getInstance().setMethod("rtc_get_session_summary", object -> buildSessionSummary());
        EndpointManager.getInstance().setMethod("rtc_reopen_current", object -> {
            if (object instanceof Activity) {
                reopenCurrentCall((Activity) object);
                return true;
            }
            return false;
        });
        EndpointManager.getInstance().setMethod("rtc_probe_channel_state", object -> {
            if (object instanceof WKChannel) {
                scheduleProbeChannelState((WKChannel) object, true);
            }
            return true;
        });
        EndpointManager.getInstance().setMethod("rtc_maybe_finish_group_if_lonely", object -> {
            // Participant departures do not close a group room. The server owns
            // room lifetime and reports it via rtc.closed/state.
            return true;
        });
        EndpointManager.getInstance().setMethod("rtc_update_live_participant_uids", object -> {
            if (object instanceof List<?>) {
                syncLiveParticipantUIDs((List<?>) object);
            }
            return true;
        });
        EndpointManager.getInstance().setMethod("rtc_get_invite_excluded_uids", object -> buildInviteExcludedUIDs());
        EndpointManager.getInstance().setMethod("wk_p2p_call", object -> {
            if (object instanceof RTCMenu) {
                RTCMenu menu = (RTCMenu) object;
                IConversationContext conversation = menu.iConversationContext;
                if (conversation != null && conversation.getChatChannelInfo() != null) {
                    startCallActivity(
                            conversation.getChatActivity(),
                            conversation.getChatChannelInfo(),
                            menu.callType,
                            null,
                            null
                    );
                }
            }
            return true;
        });
        EndpointManager.getInstance().setMethod("create_video_call", object -> {
            if (object instanceof CreateVideoCallMenu) {
                CreateVideoCallMenu menu = (CreateVideoCallMenu) object;
                List<String> inviteUIDs = new ArrayList<>();
                List<String> inviteNames = new ArrayList<>();
                boolean inviteAll = menu.channelType == WKChannelType.GROUP
                        && (menu.WKChannels == null || menu.WKChannels.isEmpty());
                if (menu.WKChannels != null && !menu.WKChannels.isEmpty()) {
                    for (WKChannel channel : menu.WKChannels) {
                        if (channel == null || TextUtils.isEmpty(channel.channelID)) {
                            continue;
                        }
                        inviteUIDs.add(channel.channelID);
                        inviteNames.add(resolveChannelDisplayName(channel));
                    }
                }
                Log.i(TAG, "create_video_call prepared inviteUIDs=" + inviteUIDs + ", inviteNames=" + inviteNames
                        + ", inviteAll=" + inviteAll + ", channelId=" + menu.channelID
                        + ", channelType=" + menu.channelType + ", callType=" + menu.callType);
                startCallActivity(menu.activity, new WKChannel(menu.channelID, menu.channelType), menu.callType, inviteUIDs, inviteNames, inviteAll);
                return true;
            }
            return null;
        });
        EndpointManager.getInstance().setMethod("rtc_max_number", object -> 9);
        EndpointManager.getInstance().setMethod("rtc_offline_data", object -> {
            if (object instanceof List<?>) {
                for (Object item : (List<?>) object) {
                    if (item instanceof com.xinbida.wukongim.entity.WKCMD) {
                        com.xinbida.wukongim.entity.WKCMD cmd = (com.xinbida.wukongim.entity.WKCMD) item;
                        handleCmd(cmd.cmdKey, parsePayload(cmd.paramJsonObject));
                    }
                }
            }
            return true;
        });
        EndpointManager.getInstance().setMethod("show_calling_participants", object -> null);
        WKIM.getInstance().getCMDManager().addCmdListener("wkrtc", cmd -> {
            if (cmd == null) {
                Log.w(TAG, "receive null cmd");
                return;
            }
            Log.i(TAG, "receive cmd=" + cmd.cmdKey + ", payload=" + cmd.paramJsonObject);
            if (TextUtils.isEmpty(cmd.cmdKey)) {
                return;
            }
            if (handleMaybeRtcSync(cmd.cmdKey, cmd.paramJsonObject)) {
                return;
            }
            handleCmd(cmd.cmdKey, parsePayload(cmd.paramJsonObject));
        });
    }

    private void startCallActivity(Activity activity, WKChannel channel, int callType,
                                   List<String> inviteUIDs, List<String> inviteNames) {
        startCallActivity(activity, channel, callType, inviteUIDs, inviteNames, false);
    }

    private void startCallActivity(Activity activity, WKChannel channel, int callType,
                                   List<String> inviteUIDs, List<String> inviteNames, boolean inviteAll) {
        if (activity == null || channel == null) {
            return;
        }
        if (isCalling()) {
            reopenCurrentCall(activity);
            return;
        }
        Intent intent = RtcCallActivity.newOutgoingIntent(activity, channel, callType, inviteUIDs, inviteNames, inviteAll);
        activity.startActivity(intent);
    }

    private void appendAllGroupMembers(String groupId, List<String> inviteUIDs, List<String> inviteNames) {
        List<WKChannelMember> members = WKIM.getInstance().getChannelMembersManager().getMembers(groupId, WKChannelType.GROUP);
        String loginUid = WKConfig.getInstance().getUid();
        if (members == null) {
            return;
        }
        for (WKChannelMember member : members) {
            if (member == null || TextUtils.isEmpty(member.memberUID) || TextUtils.equals(member.memberUID, loginUid)) {
                continue;
            }
            if (inviteUIDs.contains(member.memberUID)) {
                continue;
            }
            inviteUIDs.add(member.memberUID);
            inviteNames.add(resolveMemberDisplayName(member));
        }
    }

    private String resolveChannelDisplayName(WKChannel channel) {
        if (channel == null) {
            return "";
        }
        if (!TextUtils.isEmpty(channel.channelRemark)) {
            return channel.channelRemark;
        }
        if (!TextUtils.isEmpty(channel.channelName)) {
            return channel.channelName;
        }
        return TextUtils.isEmpty(channel.channelID) ? "" : channel.channelID;
    }

    private String resolveMemberDisplayName(WKChannelMember member) {
        if (member == null) {
            return "";
        }
        if (!TextUtils.isEmpty(member.memberRemark)) {
            return member.memberRemark;
        }
        if (!TextUtils.isEmpty(member.memberName)) {
            return member.memberName;
        }
        return TextUtils.isEmpty(member.memberUID) ? "" : member.memberUID;
    }

    public void openFromMessage(WKMsg msg) {
        if (msg == null || TextUtils.isEmpty(msg.channelID)) {
            return;
        }
        if (session != null && !RtcSession.ENDED.equals(session.status)) {
            runOnMain(() -> {
                try {
                    Intent intent;
                    if (session.incoming) {
                        RtcCallPayload payload = new RtcCallPayload();
                        payload.call_id = session.callId;
                        payload.room_name = session.roomName;
                        payload.channel_id = session.channel == null ? msg.channelID : session.channel.channelID;
                        payload.channel_type = session.channel == null ? msg.channelType : session.channel.channelType;
                        payload.call_type = session.callType == 1 ? "video" : "audio";
                        payload.from_uid = session.fromUID;
                        payload.from_name = session.fromName;
                        intent = RtcCallActivity.newIncomingIntent(appContext, payload);
                    } else {
                        intent = RtcCallActivity.newOutgoingIntent(
                                appContext,
                                session.channel == null ? new WKChannel(msg.channelID, msg.channelType) : session.channel,
                                session.callType,
                                null,
                                session.inviteNames
                        );
                    }
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    appContext.startActivity(intent);
                } catch (Exception error) {
                    Log.w(TAG, "open current rtc session from message failed", error);
                }
            });
            return;
        }
        WKChannel channel = new WKChannel(msg.channelID, msg.channelType);
        scheduleProbeChannelState(channel, true);
    }

    public void openOngoingGroupCallFromMessage(WKMsg msg) {
        if (msg == null) {
            showCallEndedToast();
            return;
        }
        RtcCallPayload messagePayload = null;
        try {
            messagePayload = parsePayload(new JSONObject(msg.content == null ? "{}" : msg.content));
        } catch (Exception ignored) {
        }
        String expectedCallId = messagePayload == null ? "" : messagePayload.call_id;
        String channelId = messagePayload != null && !TextUtils.isEmpty(messagePayload.channel_id)
                ? messagePayload.channel_id
                : msg.channelID;
        byte channelType = messagePayload != null && messagePayload.channel_type > 0
                ? messagePayload.channel_type
                : msg.channelType;
        if (TextUtils.isEmpty(channelId) || channelType != WKChannelType.GROUP) {
            openFromMessage(msg);
            return;
        }
        RtcCallPayload finalMessagePayload = messagePayload;
        WKChannel channel = new WKChannel(channelId, channelType);
        RtcModel.getInstance().channelState(channel.channelType, channel.channelID, new IRequestResultListener<>() {
            @Override
            public void onSuccess(RtcChannelStateResp result) {
                runOnMain(() -> {
                    if (!isActiveGroupCallState(result, expectedCallId)) {
                        finishCurrentCallIfMatches(expectedCallId, channel);
                        showCallEndedToast();
                        return;
                    }
                    RtcCallPayload payload = payloadFromState(channel, result);
                    if (payload == null) {
                        finishCurrentCallIfMatches(expectedCallId, channel);
                        showCallEndedToast();
                        return;
                    }
                    if (finalMessagePayload != null) {
                        if (TextUtils.isEmpty(payload.call_type)) {
                            payload.call_type = finalMessagePayload.call_type;
                        }
                        if (TextUtils.isEmpty(payload.from_uid)) {
                            payload.from_uid = finalMessagePayload.from_uid;
                        }
                        if (TextUtils.isEmpty(payload.from_name)) {
                            payload.from_name = finalMessagePayload.from_name;
                        }
                    }
                    if (isCalling()) {
                        if (session != null && TextUtils.equals(session.callId, payload.call_id)) {
                            openCurrentCallFromApp();
                        } else {
                            WKToastUtils.getInstance().showToastNormal("\u6b63\u5728\u901a\u8bdd\u4e2d");
                        }
                        return;
                    }
                    joinOngoingGroupCall(payload);
                });
            }

            @Override
            public void onFail(int code, String msg) {
                runOnMain(() -> WKToastUtils.getInstance().showToastNormal(
                        TextUtils.isEmpty(msg) ? "\u52a0\u5165\u901a\u8bdd\u5931\u8d25" : msg
                ));
            }
        });
    }

    private void finishCurrentCallIfMatches(String expectedCallId, WKChannel channel) {
        if (session == null || RtcSession.ENDED.equals(session.status)) {
            return;
        }
        boolean sameCall = !TextUtils.isEmpty(expectedCallId) && TextUtils.equals(session.callId, expectedCallId);
        boolean sameChannel = channel != null
                && session.channel != null
                && session.channel.channelType == channel.channelType
                && TextUtils.equals(session.channel.channelID, channel.channelID);
        if (sameCall || sameChannel) {
            finish();
        }
    }

    private void joinOngoingGroupCall(RtcCallPayload payload) {
        if (payload == null || TextUtils.isEmpty(payload.call_id) || appContext == null) {
            showCallEndedToast();
            return;
        }
        if (isCalling()) {
            if (session != null && TextUtils.equals(session.callId, payload.call_id)) {
                openCurrentCallFromApp();
            } else {
                WKToastUtils.getInstance().showToastNormal("\u6b63\u5728\u901a\u8bdd\u4e2d");
            }
            return;
        }
        session = new RtcSession();
        session.callId = payload.call_id;
        session.roomName = payload.room_name;
        session.channel = buildIncomingChannel(payload);
        session.callType = "video".equals(payload.call_type) ? 1 : 0;
        session.status = RtcSession.JOINING;
        session.serverStatus = "connected";
        session.incoming = false;
        session.fromUID = payload.from_uid;
        session.fromName = resolveUserName(payload.from_uid, payload.from_name);
        session.expireAt = payload.expire_at;
        session.localCameraEnabled = session.callType == 1;
        session.localMicrophoneEnabled = true;
        session.minimized = false;
        session.liveParticipantCount = 0;
        notifyChanged();
        startGroupCallStateSyncIfNeeded();
        Intent intent = RtcCallActivity.newJoinIntent(appContext, payload);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try {
            appContext.startActivity(intent);
        } catch (Exception error) {
            Log.w(TAG, "start join activity from message failed", error);
        }
    }

    private boolean isActiveGroupCallState(RtcChannelStateResp state, String expectedCallId) {
        if (state == null || !state.existing || TextUtils.isEmpty(state.call_id)) {
            return false;
        }
        if (!TextUtils.isEmpty(expectedCallId) && !TextUtils.equals(expectedCallId, state.call_id)) {
            return false;
        }
        if (state.expire_at > 0 && state.expire_at <= nowSeconds()) {
            return false;
        }
        String status = TextUtils.isEmpty(state.status) ? "" : state.status.toLowerCase(java.util.Locale.US);
        return !TextUtils.equals(status, "ended")
                && !TextUtils.equals(status, "closed")
                && !TextUtils.equals(status, "cancelled")
                && !TextUtils.equals(status, "canceled")
                && !TextUtils.equals(status, "timeout")
                && !TextUtils.equals(status, "rejected");
    }

    private void openCurrentCallFromApp() {
        if (appContext == null || session == null || session.channel == null || RtcSession.ENDED.equals(session.status)) {
            return;
        }
        session.minimized = false;
        notifyChanged();
        RtcCallPayload payload = new RtcCallPayload();
        payload.call_id = session.callId;
        payload.room_name = session.roomName;
        payload.channel_id = session.channel.channelID;
        payload.channel_type = session.channel.channelType;
        payload.call_type = session.callType == 1 ? "video" : "audio";
        payload.from_uid = session.fromUID;
        payload.from_name = session.fromName;
        Intent intent = session.incoming
                ? RtcCallActivity.newIncomingIntent(appContext, payload)
                : RtcCallActivity.newJoinIntent(appContext, payload);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        appContext.startActivity(intent);
    }

    private void showCallEndedToast() {
        WKToastUtils.getInstance().showToastNormal("\u5df2\u7ed3\u675f\u901a\u8bdd");
    }

    public void startOutgoing(WKChannel channel, int callType, List<String> inviteUIDs, List<String> inviteNames,
                              com.chat.base.net.IRequestResultListener<RtcCallResp> listener) {
        startOutgoing(channel, callType, inviteUIDs, inviteNames, false, listener);
    }

    public void startOutgoing(WKChannel channel, int callType, List<String> inviteUIDs, List<String> inviteNames,
                              boolean inviteAll,
                              com.chat.base.net.IRequestResultListener<RtcCallResp> listener) {
        List<String> effectiveInviteUIDs;
        if (inviteAll) {
            effectiveInviteUIDs = new ArrayList<>();
        } else if (channel != null && channel.channelType == WKChannelType.PERSONAL) {
            effectiveInviteUIDs = new ArrayList<>();
            if (!TextUtils.isEmpty(channel.channelID)) {
                effectiveInviteUIDs.add(channel.channelID);
            }
        } else {
            effectiveInviteUIDs = inviteUIDs;
        }
        Log.i(TAG, "start outgoing call, channelId=" + (channel == null ? "" : channel.channelID)
                + ", channelType=" + (channel == null ? "" : channel.channelType)
                + ", callType=" + callType
                + ", loginUID=" + WKConfig.getInstance().getUid()
                + ", inviteAll=" + inviteAll
                + ", inviteUIDs=" + effectiveInviteUIDs);
        session = new RtcSession();
        session.channel = channel;
        session.callType = callType;
        session.status = RtcSession.OUTGOING;
        session.localCameraEnabled = callType == 1;
        session.localMicrophoneEnabled = true;
        session.minimized = false;
        session.inviteUIDs = effectiveInviteUIDs == null ? null : new ArrayList<>(effectiveInviteUIDs);
        session.inviteNames = inviteNames == null ? null : new ArrayList<>(inviteNames);
        session.joinedUIDs = new ArrayList<>();
        session.liveParticipantCount = 0;
        session.inviteAll = inviteAll;
        notifyChanged();
        EndpointManager.getInstance().invoke("play_rtc_media", null);

        RtcModel.getInstance().startCall(
                UUID.randomUUID().toString(),
                channel.channelID,
                channel.channelType,
                callType == 1 ? "video" : "audio",
                getDeviceId(),
                effectiveInviteUIDs,
                inviteAll,
                listener
        );
    }

    public void applyCallResp(RtcCallResp resp) {
        if (session == null || resp == null) {
            return;
        }
        session.callId = resp.call_id;
        session.roomName = resp.room_name;
        session.serverStatus = resp.status;
        session.permissions = resp.permissions;
        session.expireAt = resp.expire_at;
        session.minimized = false;
        session.liveKitConnected = resp.livekit != null
                && !TextUtils.isEmpty(resp.livekit.url)
                && !TextUtils.isEmpty(resp.livekit.token);
        if ("connected".equals(resp.status)
                || (session.liveKitConnected && (session.incoming || RtcSession.JOINING.equals(session.status)))
                || shouldTreatOutgoingInviteAllAsInCall()) {
            session.status = RtcSession.IN_CALL;
        } else {
            session.status = RtcSession.OUTGOING;
        }
        Log.i(TAG, "apply call response, callId=" + session.callId
                + ", status=" + resp.status
                + ", channelType=" + (session.channel == null ? "" : session.channel.channelType)
                + ", inviteUIDs=" + session.inviteUIDs);
        if ("connected".equals(resp.status)) {
            session.incoming = false;
        }
        if (RtcSession.IN_CALL.equals(session.status)) {
            session.incoming = false;
            if (session.connectedAt == 0L) {
                session.connectedAt = System.currentTimeMillis();
            }
            EndpointManager.getInstance().invoke("stop_rtc_media", null);
        }
        startGroupCallStateSyncIfNeeded();
        notifyChanged();
    }

    private boolean shouldTreatOutgoingInviteAllAsInCall() {
        return session != null
                && session.inviteAll
                && session.liveKitConnected
                && session.channel != null
                && session.channel.channelType == WKChannelType.GROUP;
    }

    public void receiveInvite(RtcCallPayload payload) {
        runOnMain(() -> {
            if (payload == null || isCalling()) {
                Log.w(TAG, "ignore invite, payload=" + payload + ", isCalling=" + isCalling());
                return;
            }
            if (TextUtils.isEmpty(payload.call_id)) {
                Log.w(TAG, "ignore invite without call_id");
                return;
            }
            if (isPayloadExpired(payload)) {
                Log.i(TAG, "ignore expired invite, callId=" + payload.call_id + ", expireAt=" + payload.expire_at);
                return;
            }
            if (isRecentlyEnded(payload.call_id)) {
                Log.i(TAG, "ignore recently ended invite, callId=" + payload.call_id);
                return;
            }
            if (isInviteFromSelf(payload)) {
                Log.i(TAG, "ignore self invite, callId=" + payload.call_id);
                return;
            }
            if (!isInviteForMe(payload)) {
                Log.i(TAG, "ignore invite not for current uid, callId=" + payload.call_id + ", inviteUIDs=" + getEffectiveInviteUIDs(payload)
                        + ", channelId=" + payload.channel_id + ", channelType=" + payload.channel_type);
                clearIgnoredInviteEffects("non-target invite");
                return;
            }
            Log.i(TAG, "receive invite, callId=" + payload.call_id
                    + ", channelId=" + payload.channel_id
                    + ", channelType=" + payload.channel_type
                    + ", callType=" + payload.call_type
                    + ", fromUID=" + payload.from_uid
                    + ", inviteUIDs=" + getEffectiveInviteUIDs(payload));
            session = new RtcSession();
            session.callId = payload.call_id;
            session.roomName = payload.room_name;
            session.channel = buildIncomingChannel(payload);
            session.callType = "video".equals(payload.call_type) ? 1 : 0;
            session.status = RtcSession.INCOMING;
            session.incoming = true;
            session.fromUID = payload.from_uid;
            session.fromName = resolveUserName(payload.from_uid, payload.from_name);
            session.expireAt = payload.expire_at;
            session.localCameraEnabled = session.callType == 1;
            session.localMicrophoneEnabled = true;
            session.minimized = false;
            session.liveParticipantCount = 0;
            notifyChanged();
            startGroupCallStateSyncIfNeeded();
            cancelIncomingNotification();
            EndpointManager.getInstance().invoke("cancel_rtc_notification", null);
            boolean appInForeground = false;
            Object foregroundResult = EndpointManager.getInstance().invoke("app_is_foreground", null);
            if (foregroundResult instanceof Boolean) {
                appInForeground = (Boolean) foregroundResult;
            }
            PendingIntent fullScreenIntent = showIncomingNotification(payload);
            if (!appInForeground) {
                EndpointManager.getInstance().invoke("show_rtc_notification", payload.from_uid);
                if (fullScreenIntent != null) {
                    try {
                        fullScreenIntent.send();
                    } catch (PendingIntent.CanceledException error) {
                        Log.w(TAG, "send full screen rtc intent failed", error);
                    }
                }
            } else {
                EndpointManager.getInstance().invoke("play_rtc_media", null);
            }
            Intent intent = RtcCallActivity.newIncomingIntent(appContext, payload);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            try {
                appContext.startActivity(intent);
            } catch (Exception error) {
                Log.w(TAG, "start incoming activity failed, fallback to notification", error);
            }
        });
    }

    public boolean handleCmd(String cmd, RtcCallPayload payload) {
        if (!isRtcCmd(cmd)) {
            return false;
        }
        String rtcCmd = normalizeRtcCmd(cmd);
        Log.i(TAG, "handle rtc cmd=" + cmd + ", callId=" + (payload == null ? "" : payload.call_id));
        if (isCmd(rtcCmd, "invite")) {
            if (payload == null) {
                return true;
            }
            if (isIncompleteInvitePayload(payload)) {
                if (shouldShowIncompleteDirectInvite(payload)) {
                    normalizeIncompleteInviteForDisplay(payload);
                    Log.i(TAG, "receive incomplete rtc.invite directly, show incoming first, callId="
                            + payload.call_id + ", channelId=" + payload.channel_id
                            + ", inviteUIDs=" + getEffectiveInviteUIDs(payload));
                    receiveInvite(payload);
                    probeIncomingTargets(payload.channel_id, payload.channel_type, payload.from_uid, false);
                    return true;
                }
                Log.i(TAG, "receive rtc.invite requiring state probe before showing incoming, callId="
                        + payload.call_id + ", channelId=" + payload.channel_id);
                probeIncomingTargets(payload.channel_id, payload.channel_type, payload.from_uid, true);
                return true;
            }
            if (payload != null
                    && payload.channel_type == WKChannelType.GROUP
                    && !isInviteForMe(payload)) {
                Log.i(TAG, "receive rtc.invite not explicitly targeted, ignore without state promotion, callId="
                        + payload.call_id + ", channelId=" + payload.channel_id);
                clearIgnoredInviteEffects("group invite fallback probe");
                return true;
            }
            receiveInvite(payload);
            return true;
        }
        if (isCmd(rtcCmd, "notice")) {
            // Online group notice only refreshes the join entry. It never rings and
            // never enters LiveKit without an explicit state check + /join action.
            Log.i(TAG, "receive rtc.notice as non-ringing group reminder, callId="
                    + (payload == null ? "" : payload.call_id));
            return true;
        }
        if (session == null) {
            return true;
        }
        if (isCmd(rtcCmd, "joined")
                && TextUtils.equals(session.callId, payload == null ? "" : payload.call_id)) {
            runOnMain(() -> {
                if (session != null) {
                    trackParticipantJoined(payload);
                    if (isJoinForCurrentUser(payload) || session.liveKitConnected) {
                        session.status = RtcSession.IN_CALL;
                        session.serverStatus = "connected";
                        session.incoming = false;
                        dismissIncomingAlert();
                        notifyChanged();
                    } else {
                        Log.i(TAG, "ignore joined status change for other participant, callId="
                                + (payload == null ? "" : payload.call_id)
                                + ", uid=" + (payload == null ? "" : payload.uid)
                                + ", answerUid=" + (payload == null ? "" : payload.answer_uid)
                                + ", answerDeviceId=" + (payload == null ? "" : payload.answer_device_id));
                    }
                }
            });
            return true;
        }
        if ((isCmd(rtcCmd, "cancelled") || isCmd(rtcCmd, "canceled")) && !isPayloadForCurrentCall(payload)) {
            Log.i(TAG, "ignore rtc cancel for other call, cmd=" + cmd
                    + ", callId=" + (payload == null ? "" : payload.call_id)
                    + ", currentCallId=" + (session == null ? "" : session.callId));
            return true;
        }
        if (isCmd(rtcCmd, "cancelled") || isCmd(rtcCmd, "canceled")) {
            if (payload != null
                    && "answered_on_other_device".equals(payload.reason)
                    && TextUtils.equals(payload.answer_device_id, getDeviceId())) {
                return true;
            }
            finish();
            return true;
        }
        if (isCmd(rtcCmd, "rejected") || isCmd(rtcCmd, "closed") || isCmd(rtcCmd, "timeout")) {
            if (!isPayloadForCurrentCall(payload)) {
                Log.i(TAG, "ignore group terminal cmd for other call, cmd=" + cmd
                        + ", callId=" + (payload == null ? "" : payload.call_id)
                        + ", currentCallId=" + (session == null ? "" : session.callId));
                return true;
            }
            trackParticipantTerminal(payload);
            if (shouldFinishForTerminalCmd(cmd, payload)) {
                finish();
            } else {
                Log.i(TAG, "ignore group terminal cmd for other participant, cmd=" + cmd
                        + ", callId=" + (payload == null ? "" : payload.call_id)
                        + ", uid=" + (payload == null ? "" : payload.uid)
                        + ", answerUid=" + (payload == null ? "" : payload.answer_uid));
            }
            return true;
        }
        return true;
    }

    private boolean shouldFinishForTerminalCmd(String cmd, RtcCallPayload payload) {
        if (session == null || session.channel == null || session.channel.channelType == WKChannelType.PERSONAL) {
            return true;
        }
        if (isCmd(cmd, "closed")) {
            return true;
        }
        return isTerminalForCurrentActor(payload);
    }

    private boolean isPayloadForCurrentCall(RtcCallPayload payload) {
        return payload == null
                || TextUtils.isEmpty(payload.call_id)
                || session == null
                || TextUtils.equals(session.callId, payload.call_id);
    }

    private void trackParticipantJoined(RtcCallPayload payload) {
        if (session == null || session.channel == null || session.channel.channelType == WKChannelType.PERSONAL) {
            return;
        }
        String loginUid = WKConfig.getInstance().getUid();
        if (session.joinedUIDs == null) {
            session.joinedUIDs = new ArrayList<>();
        }
        for (String participantUid : resolveParticipantCandidates(payload)) {
            if (TextUtils.isEmpty(participantUid) || TextUtils.equals(participantUid, loginUid)) {
                continue;
            }
            if (!session.joinedUIDs.contains(participantUid)) {
                session.joinedUIDs.add(participantUid);
            }
            session.hadRemoteParticipantJoined = true;
            removeParticipantFromInviteList(participantUid);
        }
    }

    private void trackParticipantTerminal(RtcCallPayload payload) {
        if (session == null || session.channel == null || session.channel.channelType == WKChannelType.PERSONAL) {
            return;
        }
        String loginUid = WKConfig.getInstance().getUid();
        boolean removedParticipant = false;
        for (String participantUid : resolveTerminalParticipantCandidates(payload)) {
            if (TextUtils.isEmpty(participantUid) || TextUtils.equals(participantUid, loginUid)) {
                continue;
            }
            removeParticipantFromInviteList(participantUid);
            if (session.joinedUIDs != null) {
                removedParticipant = session.joinedUIDs.remove(participantUid) || removedParticipant;
            }
        }
        if (!removedParticipant
                && RtcSession.IN_CALL.equals(session.status)
                && session.joinedUIDs != null
                && session.joinedUIDs.size() == 1) {
            String assumedLeftUid = session.joinedUIDs.remove(0);
            removeParticipantFromInviteList(assumedLeftUid);
            Log.i(TAG, "assume sole remote participant left group rtc, uid=" + assumedLeftUid
                    + ", callId=" + (payload == null ? "" : payload.call_id));
        }
        notifyChanged();
    }

    private void removeParticipantFromInviteList(String participantUid) {
        if (session == null || TextUtils.isEmpty(participantUid) || session.inviteUIDs == null) {
            return;
        }
        int index = session.inviteUIDs.indexOf(participantUid);
        if (index >= 0) {
            session.inviteUIDs.remove(index);
            if (session.inviteNames != null && index < session.inviteNames.size()) {
                session.inviteNames.remove(index);
            }
        }
    }

    private String resolveParticipantUid(RtcCallPayload payload) {
        if (payload == null) {
            return null;
        }
        if (!TextUtils.isEmpty(payload.answer_uid)) {
            return payload.answer_uid;
        }
        if (!TextUtils.isEmpty(payload.uid)) {
            return payload.uid;
        }
        if (!TextUtils.isEmpty(payload.from_uid)) {
            return payload.from_uid;
        }
        return null;
    }

    private List<String> resolveParticipantCandidates(RtcCallPayload payload) {
        ArrayList<String> candidates = new ArrayList<>();
        if (payload == null) {
            return candidates;
        }
        addUniqueCandidate(candidates, payload.answer_uid);
        addUniqueCandidate(candidates, payload.uid);
        if (candidates.isEmpty()) {
            addUniqueCandidate(candidates, payload.from_uid);
        }
        return candidates;
    }

    private List<String> resolveTerminalParticipantCandidates(RtcCallPayload payload) {
        ArrayList<String> candidates = new ArrayList<>();
        if (payload == null) {
            return candidates;
        }
        addUniqueCandidate(candidates, payload.answer_uid);
        addUniqueCandidate(candidates, payload.uid);
        List<String> inviteUIDs = getEffectiveInviteUIDs(payload);
        if (candidates.isEmpty() && inviteUIDs != null && inviteUIDs.size() == 1) {
            addUniqueCandidate(candidates, inviteUIDs.get(0));
        }
        return candidates;
    }

    private void addUniqueCandidate(List<String> candidates, String uid) {
        if (!TextUtils.isEmpty(uid) && !candidates.contains(uid)) {
            candidates.add(uid);
        }
    }

    private void syncLiveParticipantUIDs(List<?> participantUIDs) {
        if (session == null || session.channel == null || session.channel.channelType == WKChannelType.PERSONAL) {
            return;
        }
        String loginUid = WKConfig.getInstance().getUid();
        LinkedHashSet<String> remoteUIDs = new LinkedHashSet<>();
        for (Object value : participantUIDs) {
            if (!(value instanceof String)) {
                continue;
            }
            String uid = (String) value;
            if (TextUtils.isEmpty(uid) || TextUtils.equals(uid, loginUid) || uid.startsWith("invite_")) {
                continue;
            }
            remoteUIDs.add(uid);
        }
        if (session.joinedUIDs == null) {
            session.joinedUIDs = new ArrayList<>();
        }
        session.joinedUIDs.clear();
        session.joinedUIDs.addAll(remoteUIDs);
        if (!remoteUIDs.isEmpty()) {
            session.hadRemoteParticipantJoined = true;
        }
        for (String uid : remoteUIDs) {
            removeParticipantFromInviteList(uid);
        }
        notifyChanged();
    }

    private boolean isTerminalForCurrentActor(RtcCallPayload payload) {
        if (payload == null) {
            return false;
        }
        String loginUid = WKConfig.getInstance().getUid();
        if (!TextUtils.isEmpty(payload.answer_device_id) && TextUtils.equals(payload.answer_device_id, getDeviceId())) {
            return true;
        }
        return !TextUtils.isEmpty(loginUid)
                && (TextUtils.equals(payload.answer_uid, loginUid) || TextUtils.equals(payload.uid, loginUid));
    }

    private boolean isRtcCmd(String cmd) {
        String rtcCmd = normalizeRtcCmd(cmd);
        return !TextUtils.isEmpty(rtcCmd) && (rtcCmd.startsWith("rtc.") || rtcCmd.contains(".rtc."));
    }

    private String normalizeRtcCmd(String cmd) {
        return TextUtils.isEmpty(cmd) ? "" : cmd.replace('_', '.').toLowerCase(java.util.Locale.US);
    }

    private boolean handleMaybeRtcSync(String cmd, JSONObject jsonObject) {
        if (!isSyncMessageExtraCmd(cmd) || jsonObject == null || isCalling()) {
            return false;
        }
        String channelID = jsonObject.optString("channel_id");
        byte channelType = (byte) jsonObject.optInt("channel_type", WKChannelType.PERSONAL);
        if (TextUtils.isEmpty(channelID)) {
            return false;
        }
        if (channelType == WKChannelType.GROUP) {
            Log.i(TAG, "receive group syncMessageExtra, probe rtc state for invite targets, channelId=" + channelID);
            WKChannel channel = new WKChannel(channelID, channelType);
            scheduleProbeChannelState(channel, true);
            return false;
        }
        Log.i(TAG, "receive syncMessageExtra, probe rtc state for channelId=" + channelID + ", channelType=" + channelType);
        WKChannel channel = new WKChannel(channelID, channelType);
        scheduleProbeChannelState(channel, true);
        return false;
    }

    public void finish() {
        finish(true);
    }

    public void finish(boolean ignoreFutureInvites) {
        runOnMain(() -> {
            RtcSession activeSession = session;
            if (activeSession != null && RtcSession.ENDED.equals(activeSession.status)) {
                return;
            }
            if (activeSession != null) {
                activeSession.ending = true;
                activeSession.status = RtcSession.ENDED;
                activeSession.liveKitConnected = false;
                activeSession.minimized = false;
                if (ignoreFutureInvites) {
                    markRecentlyEnded(activeSession.callId);
                }
            }
            stopGroupCallStateSync();
            cancelIncomingNotification();
            EndpointManager.getInstance().invoke("cancel_rtc_notification", null);
            EndpointManager.getInstance().invoke("stop_rtc_media", null);
            if (activeSession != null) {
                notifyChanged();
                mainHandler.postDelayed(RtcLiveKitClient::disconnect, 120L);
                mainHandler.postDelayed(() -> {
                    if (session == activeSession && RtcSession.ENDED.equals(activeSession.status)) {
                        session = null;
                        notifyChanged();
                    }
                }, ENDED_SESSION_CLEAR_DELAY_MS);
            } else {
                RtcLiveKitClient.disconnect();
            }
        });
    }

    public void markInvitedMembers(List<String> uids, List<String> names) {
        runOnMain(() -> {
            if (session == null || uids == null || uids.isEmpty()) {
                return;
            }
            if (session.inviteUIDs == null) {
                session.inviteUIDs = new ArrayList<>();
            }
            if (session.inviteNames == null) {
                session.inviteNames = new ArrayList<>();
            }
            for (int i = 0; i < uids.size(); i++) {
                String uid = uids.get(i);
                if (TextUtils.isEmpty(uid) || session.inviteUIDs.contains(uid)) {
                    continue;
                }
                session.inviteUIDs.add(uid);
                session.inviteNames.add(names != null && i < names.size() ? names.get(i) : uid);
            }
            notifyChanged();
            startGroupCallStateSyncIfNeeded();
        });
    }

    public void dismissIncomingAlert() {
        runOnMain(() -> {
            cancelIncomingNotification();
            EndpointManager.getInstance().invoke("cancel_rtc_notification", null);
            EndpointManager.getInstance().invoke("stop_rtc_media", null);
        });
    }

    public void dismissIncomingNotificationOnly() {
        runOnMain(() -> {
            cancelIncomingNotification();
            EndpointManager.getInstance().invoke("cancel_rtc_notice_only", null);
        });
    }

    private boolean shouldSyncActiveGroupCallState() {
        return session != null
                && session.channel != null
                && session.channel.channelType == WKChannelType.GROUP
                && !RtcSession.ENDED.equals(session.status)
                && !TextUtils.isEmpty(session.callId);
    }

    private void startGroupCallStateSyncIfNeeded() {
        if (!shouldSyncActiveGroupCallState()) {
            return;
        }
        mainHandler.removeCallbacks(groupCallStateSyncer);
        mainHandler.postDelayed(groupCallStateSyncer, 800L);
    }

    private void stopGroupCallStateSync() {
        mainHandler.removeCallbacks(groupCallStateSyncer);
        groupStateSyncing = false;
    }

    private void syncActiveGroupCallState() {
        if (!shouldSyncActiveGroupCallState() || groupStateSyncing) {
            return;
        }
        RtcSession activeSession = session;
        WKChannel activeChannel = activeSession.channel;
        String activeCallId = activeSession.callId;
        groupStateSyncing = true;
        RtcModel.getInstance().channelState(activeChannel.channelType, activeChannel.channelID, new IRequestResultListener<>() {
            @Override
            public void onSuccess(RtcChannelStateResp result) {
                groupStateSyncing = false;
                if (session != activeSession) {
                    return;
                }
                if (!isActiveGroupCallState(result, activeCallId)) {
                    Log.i(TAG, "finish active group rtc because channel state ended, callId=" + activeCallId);
                    finish();
                    return;
                }
                syncInviteUIDsFromState(result);
            }

            @Override
            public void onFail(int code, String msg) {
                groupStateSyncing = false;
                Log.w(TAG, "sync active group rtc state failed, code=" + code + ", msg=" + msg);
            }
        });
    }

    private void syncInviteUIDsFromState(RtcChannelStateResp state) {
        if (session == null || state == null) {
            return;
        }
        List<String> inviteUIDs = getEffectiveInviteUIDs(state);
        if (inviteUIDs == null) {
            return;
        }
        LinkedHashSet<String> nextInviteUIDs = new LinkedHashSet<>();
        String loginUid = WKConfig.getInstance().getUid();
        if (inviteUIDs != null) {
            for (String uid : inviteUIDs) {
                if (TextUtils.isEmpty(uid) || TextUtils.equals(uid, loginUid)) {
                    continue;
                }
                if (session.joinedUIDs != null && session.joinedUIDs.contains(uid)) {
                    continue;
                }
                nextInviteUIDs.add(uid);
            }
        }
        ArrayList<String> oldInviteUIDs = session.inviteUIDs == null ? new ArrayList<>() : new ArrayList<>(session.inviteUIDs);
        if (oldInviteUIDs.equals(new ArrayList<>(nextInviteUIDs))) {
            return;
        }
        HashMap<String, String> oldNames = new HashMap<>();
        if (session.inviteUIDs != null) {
            for (int i = 0; i < session.inviteUIDs.size(); i++) {
                String uid = session.inviteUIDs.get(i);
                String name = session.inviteNames != null && i < session.inviteNames.size() ? session.inviteNames.get(i) : uid;
                oldNames.put(uid, name);
            }
        }
        session.inviteUIDs = new ArrayList<>(nextInviteUIDs);
        session.inviteNames = new ArrayList<>();
        for (String uid : session.inviteUIDs) {
            session.inviteNames.add(resolveUserName(uid, oldNames.get(uid)));
        }
        notifyChanged();
    }

    public void probeChannelState(WKChannel channel, boolean showIncomingIfCalling) {
        if (channel == null || TextUtils.isEmpty(channel.channelID)) {
            return;
        }
        probeChannelStateInternal(channel, showIncomingIfCalling, false);
        String loginUID = WKConfig.getInstance().getUid();
        if (channel.channelType == WKChannelType.PERSONAL
                && !TextUtils.isEmpty(loginUID)
                && !TextUtils.equals(loginUID, channel.channelID)) {
            probeChannelStateInternal(new WKChannel(loginUID, WKChannelType.PERSONAL), showIncomingIfCalling, true);
        }
    }

    private void scheduleProbeChannelState(WKChannel channel, boolean showIncomingIfCalling) {
        if (channel == null || TextUtils.isEmpty(channel.channelID)) {
            return;
        }
        probeChannelState(channel, showIncomingIfCalling);
        mainHandler.postDelayed(() -> probeChannelState(channel, showIncomingIfCalling), 500);
        mainHandler.postDelayed(() -> probeChannelState(channel, showIncomingIfCalling), 1200);
        mainHandler.postDelayed(() -> probeChannelState(channel, showIncomingIfCalling), 2500);
    }

    private void probeIncomingTargets(String channelId, byte channelType, String fromUid, boolean showIncomingIfCalling) {
        if (TextUtils.isEmpty(channelId) && channelType <= 0) {
            String loginUID = WKConfig.getInstance().getUid();
            if (!TextUtils.isEmpty(fromUid)) {
                scheduleProbeChannelState(new WKChannel(fromUid, WKChannelType.PERSONAL), showIncomingIfCalling);
            }
            if (!TextUtils.isEmpty(loginUID)) {
                scheduleProbeChannelState(new WKChannel(loginUID, WKChannelType.PERSONAL), showIncomingIfCalling);
            }
            return;
        }
        if (!TextUtils.isEmpty(channelId)) {
            if (channelType <= 0) {
                scheduleProbeChannelState(new WKChannel(channelId, WKChannelType.GROUP), showIncomingIfCalling);
                scheduleProbeChannelState(new WKChannel(channelId, WKChannelType.PERSONAL), showIncomingIfCalling);
            } else {
                WKChannel rtcChannel = new WKChannel(channelId, channelType);
                scheduleProbeChannelState(rtcChannel, showIncomingIfCalling);
            }
        }
        if (channelType == WKChannelType.PERSONAL
                && !TextUtils.isEmpty(fromUid)
                && !TextUtils.equals(fromUid, channelId)) {
            WKChannel peerChannel = new WKChannel(fromUid, WKChannelType.PERSONAL);
            scheduleProbeChannelState(peerChannel, showIncomingIfCalling);
        }
    }

    private void probeChannelStateInternal(WKChannel channel, boolean showIncomingIfCalling, boolean fallback) {
        if (!beginProbeChannelState(channel, fallback)) {
            return;
        }
        Log.i(TAG, "probe channel state, channelId=" + channel.channelID
                + ", channelType=" + channel.channelType + ", fallback=" + fallback);
        RtcModel.getInstance().channelState(channel.channelType, channel.channelID, new IRequestResultListener<>() {
            @Override
            public void onSuccess(RtcChannelStateResp result) {
                try {
                    if (result == null) {
                        Log.i(TAG, "probe channel state empty");
                        return;
                    }
                    Log.i(TAG, "probe channel state result, existing=" + result.existing
                            + ", status=" + result.status + ", callId=" + result.call_id
                            + ", fromUID=" + result.from_uid + ", inviteUIDs=" + getEffectiveInviteUIDs(result) + ", fallback=" + fallback);
                    if (isRecentlyEnded(result.call_id)) {
                        Log.i(TAG, "ignore recently ended channel state, callId=" + result.call_id);
                        return;
                    }
                    if (result.expire_at > 0 && result.expire_at <= nowSeconds()) {
                        Log.i(TAG, "ignore expired channel state, callId=" + result.call_id + ", expireAt=" + result.expire_at);
                        return;
                    }
                    if (!showIncomingIfCalling || isCalling() || !result.existing || TextUtils.isEmpty(result.call_id)) {
                        return;
                    }
                    if (!shouldPromoteIncomingFromState(channel, result)) {
                        return;
                    }
                    RtcCallPayload payload = payloadFromState(channel, result);
                    if (payload != null && !isInviteFromSelf(payload)) {
                        Log.i(TAG, "probe promotes invite, callId=" + payload.call_id + ", inviteUIDs=" + getEffectiveInviteUIDs(payload)
                                + ", channelId=" + payload.channel_id + ", channelType=" + payload.channel_type);
                        receiveInvite(payload);
                    }
                } finally {
                    endProbeChannelState(channel, fallback);
                }
            }

            @Override
            public void onFail(int code, String msg) {
                try {
                    Log.w(TAG, "probe channel state failed, code=" + code + ", msg=" + msg);
                } finally {
                    endProbeChannelState(channel, fallback);
                }
            }
        });
    }

    private boolean beginProbeChannelState(WKChannel channel, boolean fallback) {
        String key = buildProbeChannelKey(channel, fallback);
        if (TextUtils.isEmpty(key)) {
            return false;
        }
        long now = System.currentTimeMillis();
        synchronized (this) {
            if (probingChannelKeys.contains(key)) {
                return false;
            }
            Long lastStartedAt = lastProbeStartedAt.get(key);
            if (lastStartedAt != null && now - lastStartedAt < CHANNEL_PROBE_MIN_INTERVAL_MS) {
                return false;
            }
            probingChannelKeys.add(key);
            lastProbeStartedAt.put(key, now);
        }
        return true;
    }

    private void endProbeChannelState(WKChannel channel, boolean fallback) {
        String key = buildProbeChannelKey(channel, fallback);
        if (TextUtils.isEmpty(key)) {
            return;
        }
        synchronized (this) {
            probingChannelKeys.remove(key);
        }
    }

    private String buildProbeChannelKey(WKChannel channel, boolean fallback) {
        if (channel == null || TextUtils.isEmpty(channel.channelID)) {
            return "";
        }
        return channel.channelType + ":" + channel.channelID + ":" + fallback;
    }

    private void notifyChanged() {
        for (SessionListener listener : new ArrayList<>(listeners)) {
            listener.onSessionChanged(session);
        }
    }

    private RtcCallPayload parsePayload(JSONObject jsonObject) {
        if (jsonObject == null) {
            return null;
        }
        JSONObject data = jsonObject.optJSONObject("rtc_call");
        if (data == null) {
            data = jsonObject.optJSONObject("rtcCall");
        }
        if (data == null) {
            data = jsonObject.optJSONObject("param");
        }
        if (data == null) {
            data = jsonObject.optJSONObject("call");
        }
        if (data == null) {
            data = jsonObject.optJSONObject("data");
        }
        if (data == null) {
            data = jsonObject;
        }
        RtcCallPayload payload = new RtcCallPayload();
        payload.call_id = optStringAny(data, "call_id", "callId", "callID");
        payload.room_name = optStringAny(data, "room_name", "roomName");
        payload.channel_id = optStringAny(data, "channel_id", "channelId");
        payload.channel_type = (byte) optIntAny(data, 0, "channel_type", "channelType");
        payload.call_type = optStringAny(data, "call_type", "callType");
        payload.from_uid = optStringAny(data, "from_uid", "fromUid", "fromUID");
        payload.from_name = optStringAny(data, "from_name", "fromName");
        payload.expire_at = optLongAny(data, 0L, "expire_at", "expireAt");
        payload.answer_uid = optStringAny(data, "answer_uid", "answerUid", "answerUID");
        payload.answer_device_id = optStringAny(data, "answer_device_id", "answerDeviceId", "answerDeviceID");
        payload.reason = data.optString("reason");
        payload.uid = data.optString("uid");
        payload.invite_all = data.optBoolean("invite_all", data.optBoolean("inviteAll", false));
        payload.invite_uids = parseUidArray(data, "invite_uids", "inviteUIDs", "inviteUids", "uids", "to_uids", "toUIDs");
        payload.target_uids = parseUidArray(data, "target_uids", "targetUIDs", "targetUids", "uids", "to_uids", "toUIDs");
        if ((payload.invite_uids == null || payload.invite_uids.isEmpty())
                && (payload.target_uids == null || payload.target_uids.isEmpty())) {
            payload.invite_uids = parseUidArray(data, "uids");
        }
        if ((payload.invite_uids == null || payload.invite_uids.isEmpty())
                && payload.target_uids != null && !payload.target_uids.isEmpty()) {
            payload.invite_uids = new ArrayList<>(payload.target_uids);
        }
        if (payload.channel_type <= 0 && getEffectiveInviteUIDs(payload) != null && !getEffectiveInviteUIDs(payload).isEmpty()) {
            payload.channel_type = WKChannelType.GROUP;
        }
        if (TextUtils.isEmpty(payload.call_type)) {
            payload.call_type = "audio";
        }
        return payload;
    }

    private RtcCallPayload payloadFromState(WKChannel channel, RtcChannelStateResp state) {
        if (state == null) {
            return null;
        }
        RtcCallPayload payload = new RtcCallPayload();
        payload.call_id = state.call_id;
        payload.room_name = state.room_name;
        payload.channel_id = !TextUtils.isEmpty(state.channel_id) ? state.channel_id : channel.channelID;
        payload.channel_type = state.channel_type > 0 ? state.channel_type : channel.channelType;
        payload.call_type = state.call_type;
        payload.from_uid = state.from_uid;
        payload.from_name = state.from_name;
        payload.invite_uids = state.invite_uids;
        payload.target_uids = state.target_uids;
        if ((payload.invite_uids == null || payload.invite_uids.isEmpty())
                && payload.target_uids != null && !payload.target_uids.isEmpty()) {
            payload.invite_uids = new ArrayList<>(payload.target_uids);
        }
        payload.expire_at = state.expire_at;
        return payload;
    }

    private WKChannel buildIncomingChannel(RtcCallPayload payload) {
        if (payload.channel_type == WKChannelType.PERSONAL
                && !TextUtils.isEmpty(payload.from_uid)
                && !TextUtils.equals(payload.from_uid, WKConfig.getInstance().getUid())) {
            return new WKChannel(payload.from_uid, WKChannelType.PERSONAL);
        }
        return new WKChannel(payload.channel_id, payload.channel_type);
    }

    private boolean isInviteFromSelf(RtcCallPayload payload) {
        return payload != null
                && !TextUtils.isEmpty(payload.from_uid)
                && TextUtils.equals(payload.from_uid, WKConfig.getInstance().getUid());
    }

    private boolean isIncompleteInvitePayload(RtcCallPayload payload) {
        return payload != null
                && (!TextUtils.isEmpty(payload.call_id))
                && (TextUtils.isEmpty(payload.channel_id)
                || payload.channel_type <= 0
                || TextUtils.isEmpty(payload.call_type));
    }

    private boolean isInviteForMe(RtcCallPayload payload) {
        if (payload == null) {
            return true;
        }
        List<String> inviteUIDs = getEffectiveInviteUIDs(payload);
        if (payload.channel_type == WKChannelType.GROUP) {
            String uid = WKConfig.getInstance().getUid();
            if (TextUtils.isEmpty(uid)) {
                return false;
            }
            if (inviteUIDs != null && !inviteUIDs.isEmpty()) {
                return inviteUIDs.contains(uid);
            }
            return false;
        }
        if (inviteUIDs == null || inviteUIDs.isEmpty()) {
            return true;
        }
        String uid = WKConfig.getInstance().getUid();
        return TextUtils.isEmpty(uid) || inviteUIDs.contains(uid);
    }

    private boolean shouldPromoteIncomingFromState(WKChannel requestChannel, RtcChannelStateResp state) {
        if (state == null) {
            return false;
        }
        byte effectiveChannelType = state.channel_type > 0
                ? state.channel_type
                : requestChannel == null ? 0 : requestChannel.channelType;
        String effectiveChannelId = !TextUtils.isEmpty(state.channel_id)
                ? state.channel_id
                : requestChannel == null ? "" : requestChannel.channelID;
        if (effectiveChannelType == WKChannelType.GROUP) {
            List<String> inviteUIDs = getEffectiveInviteUIDs(state);
            String uid = WKConfig.getInstance().getUid();
            if (TextUtils.isEmpty(uid) || !isCurrentUserInGroup(effectiveChannelId)) {
                return false;
            }
            if (inviteUIDs != null && !inviteUIDs.isEmpty()) {
                return inviteUIDs.contains(uid);
            }
            return false;
        }
        if (effectiveChannelType == WKChannelType.PERSONAL && "calling".equals(state.status)) {
            return true;
        }
        return false;
    }

    private List<String> getEffectiveInviteUIDs(RtcCallPayload payload) {
        if (payload == null) {
            return null;
        }
        if (payload.invite_uids != null && !payload.invite_uids.isEmpty()) {
            return payload.invite_uids;
        }
        return payload.target_uids;
    }

    private List<String> getEffectiveInviteUIDs(RtcChannelStateResp state) {
        if (state == null) {
            return null;
        }
        if (state.invite_uids != null && !state.invite_uids.isEmpty()) {
            return state.invite_uids;
        }
        return state.target_uids;
    }

    private String optStringAny(JSONObject data, String... keys) {
        if (data == null || keys == null) {
            return "";
        }
        for (String key : keys) {
            String value = data.optString(key);
            if (!TextUtils.isEmpty(value)) {
                return value;
            }
        }
        return "";
    }

    private int optIntAny(JSONObject data, int fallback, String... keys) {
        if (data == null || keys == null) {
            return fallback;
        }
        for (String key : keys) {
            if (data.has(key)) {
                return data.optInt(key, fallback);
            }
        }
        return fallback;
    }

    private long optLongAny(JSONObject data, long fallback, String... keys) {
        if (data == null || keys == null) {
            return fallback;
        }
        for (String key : keys) {
            if (data.has(key)) {
                return data.optLong(key, fallback);
            }
        }
        return fallback;
    }

    private List<String> parseUidArray(JSONObject data, String... keys) {
        JSONArray uidArray = null;
        if (data != null && keys != null) {
            for (String key : keys) {
                uidArray = data.optJSONArray(key);
                if (uidArray != null) {
                    break;
                }
            }
        }
        if (uidArray == null) {
            return null;
        }
        List<String> values = new ArrayList<>();
        for (int i = 0; i < uidArray.length(); i++) {
            String uid = uidArray.optString(i);
            if (!TextUtils.isEmpty(uid)) {
                values.add(uid);
            }
        }
        return values;
    }

    private boolean shouldShowIncompleteDirectInvite(RtcCallPayload payload) {
        if (payload == null || TextUtils.isEmpty(payload.call_id) || isInviteFromSelf(payload)) {
            return false;
        }
        List<String> inviteUIDs = getEffectiveInviteUIDs(payload);
        if (payload.channel_type == WKChannelType.GROUP
                && !TextUtils.isEmpty(payload.channel_id)
                && (inviteUIDs == null || inviteUIDs.isEmpty())) {
            return false;
        }
        String uid = WKConfig.getInstance().getUid();
        return inviteUIDs == null
                || inviteUIDs.isEmpty()
                || (!TextUtils.isEmpty(uid) && inviteUIDs.contains(uid));
    }

    private void normalizeIncompleteInviteForDisplay(RtcCallPayload payload) {
        if (payload == null) {
            return;
        }
        if (payload.channel_type <= 0) {
            payload.channel_type = !TextUtils.isEmpty(payload.channel_id) ? WKChannelType.GROUP : WKChannelType.PERSONAL;
        }
        if (TextUtils.isEmpty(payload.channel_id)) {
            payload.channel_id = !TextUtils.isEmpty(payload.from_uid) ? payload.from_uid : WKConfig.getInstance().getUid();
        }
        if (TextUtils.isEmpty(payload.call_type)) {
            payload.call_type = "audio";
        }
        String uid = WKConfig.getInstance().getUid();
        if (!TextUtils.isEmpty(uid) && (payload.invite_uids == null || payload.invite_uids.isEmpty())) {
            ArrayList<String> currentUserOnly = new ArrayList<>();
            currentUserOnly.add(uid);
            payload.invite_uids = currentUserOnly;
            payload.target_uids = new ArrayList<>(currentUserOnly);
        }
    }

    private boolean isJoinForCurrentUser(RtcCallPayload payload) {
        if (payload == null) {
            return false;
        }
        String loginUid = WKConfig.getInstance().getUid();
        if (!TextUtils.isEmpty(payload.answer_device_id) && TextUtils.equals(payload.answer_device_id, getDeviceId())) {
            return true;
        }
        if (!TextUtils.isEmpty(loginUid) && TextUtils.equals(payload.answer_uid, loginUid)) {
            return true;
        }
        return !TextUtils.isEmpty(loginUid) && TextUtils.equals(payload.uid, loginUid);
    }

    private boolean isCurrentUserInGroup(String groupId) {
        String uid = WKConfig.getInstance().getUid();
        if (TextUtils.isEmpty(groupId) || TextUtils.isEmpty(uid)) {
            return true;
        }
        try {
            return WKIM.getInstance().getChannelMembersManager()
                    .getMember(groupId, WKChannelType.GROUP, uid) != null;
        } catch (Exception ignored) {
            return true;
        }
    }

    private boolean isPayloadExpired(RtcCallPayload payload) {
        return payload != null && payload.expire_at > 0 && payload.expire_at <= nowSeconds();
    }

    private long nowSeconds() {
        return System.currentTimeMillis() / 1000L;
    }

    private boolean isCmd(String cmd, String action) {
        return TextUtils.equals(cmd, "rtc." + action) || (!TextUtils.isEmpty(cmd) && cmd.endsWith(".rtc." + action));
    }

    private boolean isSyncMessageExtraCmd(String cmd) {
        return TextUtils.equals(cmd, "syncMessageExtra")
                || TextUtils.equals(cmd, WKCMDKeys.wk_sync_message_extra);
    }

    private boolean isTerminalCmd(String cmd) {
        return isCmd(cmd, "cancelled")
                || isCmd(cmd, "canceled")
                || isCmd(cmd, "rejected")
                || isCmd(cmd, "closed")
                || isCmd(cmd, "timeout");
    }

    private void markRecentlyEnded(String callId) {
        if (TextUtils.isEmpty(callId) || recentlyEndedCallIds.contains(callId)) {
            return;
        }
        recentlyEndedCallIds.add(callId);
        if (recentlyEndedCallIds.size() > 30) {
            recentlyEndedCallIds.remove(0);
        }
    }

    private boolean isRecentlyEnded(String callId) {
        return !TextUtils.isEmpty(callId) && recentlyEndedCallIds.contains(callId);
    }

    private ArrayList<String> buildInviteExcludedUIDs() {
        ArrayList<String> excluded = new ArrayList<>();
        String loginUid = WKConfig.getInstance().getUid();
        addUniqueCandidate(excluded, loginUid);
        if (session == null) {
            return excluded;
        }
        if (session.joinedUIDs != null) {
            for (String uid : session.joinedUIDs) {
                addUniqueCandidate(excluded, uid);
            }
        }
        return excluded;
    }

    private HashMap<String, Object> buildSessionSummary() {
        if (session == null || session.channel == null || RtcSession.ENDED.equals(session.status)) {
            return null;
        }
        HashMap<String, Object> summary = new HashMap<>();
        summary.put("channelId", session.channel.channelID);
        summary.put("channelType", session.channel.channelType);
        summary.put("callType", session.callType);
        summary.put("minimized", session.minimized);
        summary.put("title", resolveMiniSummaryTitle());
        summary.put("subtitle", resolveMiniSummarySubtitle());
        return summary;
    }

    private String resolveMiniSummaryTitle() {
        if (session == null) {
            return "";
        }
        if (session.channel != null && session.channel.channelType == WKChannelType.PERSONAL) {
            String peerName = resolveUserName(session.channel.channelID, null);
            if (!TextUtils.isEmpty(peerName)) {
                return peerName;
            }
        }
        return resolveSessionTitle();
    }

    private String resolveMiniSummarySubtitle() {
        if (session == null) {
            return "";
        }
        if (RtcSession.IN_CALL.equals(session.status)) {
            if (session.channel != null && session.channel.channelType != WKChannelType.PERSONAL) {
                return formatMiniDuration(session.connectedAt) + " · " + miniGroupOnlineCount() + "人在线";
            }
            return formatMiniDuration(session.connectedAt);
        }
        return resolveSessionSubtitle();
    }

    private String formatMiniDuration(long connectedAt) {
        if (connectedAt <= 0L) {
            return "00:00";
        }
        long elapsedSeconds = Math.max(0L, (System.currentTimeMillis() - connectedAt) / 1000L);
        long minutes = elapsedSeconds / 60L;
        long seconds = elapsedSeconds % 60L;
        return String.format(java.util.Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }

    private int miniGroupOnlineCount() {
        if (session == null) {
            return 0;
        }
        int count = 1;
        if (session.joinedUIDs != null) {
            count += session.joinedUIDs.size();
        }
        return Math.max(1, count);
    }

    private String resolveSessionTitle() {
        if (session == null) {
            return "";
        }
        if (session.channel != null && session.channel.channelType != WKChannelType.PERSONAL) {
            WKChannel localChannel = WKIM.getInstance().getChannelManager().getChannel(session.channel.channelID, session.channel.channelType);
            String groupName = localChannel == null ? null : localChannel.channelName;
            if (TextUtils.isEmpty(groupName)) {
                groupName = session.channel.channelID;
            }
            return groupName;
        }
        String peerName = resolveUserName(session.channel == null ? null : session.channel.channelID, null);
        if (!TextUtils.isEmpty(peerName)) {
            return peerName;
        }
        if (!TextUtils.isEmpty(session.fromName)) {
            return session.fromName;
        }
        if (session.inviteNames != null && !session.inviteNames.isEmpty()) {
            return session.inviteNames.get(0);
        }
        return session.channel.channelID;
    }

    private String resolveSessionSubtitle() {
        if (session == null) {
            return "";
        }
        boolean video = session.callType == 1;
        if (RtcSession.INCOMING.equals(session.status)) {
            return video ? "视频来电" : "语音来电";
        }
        if (RtcSession.OUTGOING.equals(session.status) || RtcSession.JOINING.equals(session.status)) {
            return video ? "视频通话中" : "语音通话中";
        }
        if (RtcSession.IN_CALL.equals(session.status)) {
            return video ? "视频通话中" : "语音通话中";
        }
        return video ? "视频通话" : "语音通话";
    }

    private void reopenCurrentCall(Activity activity) {
        if (activity == null || session == null || session.channel == null || RtcSession.ENDED.equals(session.status)) {
            return;
        }
        session.minimized = false;
        notifyChanged();
        Intent intent;
        if (session.incoming) {
            RtcCallPayload payload = new RtcCallPayload();
            payload.call_id = session.callId;
            payload.room_name = session.roomName;
            payload.channel_id = session.channel.channelID;
            payload.channel_type = session.channel.channelType;
            payload.call_type = session.callType == 1 ? "video" : "audio";
            payload.from_uid = session.fromUID;
            payload.from_name = session.fromName;
            intent = RtcCallActivity.newIncomingIntent(activity, payload);
        } else {
            intent = RtcCallActivity.newOutgoingIntent(activity, session.channel, session.callType, session.inviteUIDs, session.inviteNames);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        activity.startActivity(intent);
    }

    private PendingIntent showIncomingNotification(RtcCallPayload payload) {
        if (appContext == null || payload == null) {
            return null;
        }
        ensureRtcNotificationChannel();
        Intent intent = RtcCallActivity.newIncomingIntent(appContext, payload);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                appContext,
                370100,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        String title = resolveUserName(payload.from_uid, payload.from_name);
        if (TextUtils.isEmpty(title)) {
            title = "音视频通话";
        }
        String text = "video".equals(payload.call_type) ? "邀请你进行视频通话" : "邀请你进行语音通话";
        Notification notification = new NotificationCompat.Builder(appContext, WKConstants.newRTCChannelID)
                .setSmallIcon(android.R.drawable.sym_call_incoming)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .setFullScreenIntent(pendingIntent, true)
                .build();
        NotificationManager manager = (NotificationManager) appContext.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(370100, notification);
        }
        return pendingIntent;
    }

    private void cancelIncomingNotification() {
        if (appContext == null) {
            return;
        }
        NotificationManager manager = (NotificationManager) appContext.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.cancel(370100);
        }
    }

    private void clearIgnoredInviteEffects(String reason) {
        if (isCalling()) {
            return;
        }
        Log.i(TAG, "clear ignored rtc invite effects, reason=" + reason);
        cancelIncomingNotification();
        EndpointManager.getInstance().invoke("cancel_rtc_notification", null);
        EndpointManager.getInstance().invoke("stop_rtc_media", null);
    }

    private String resolveUserName(String uid, String fallback) {
        if (!TextUtils.isEmpty(uid)) {
            WKChannel user = WKIM.getInstance().getChannelManager().getChannel(uid, WKChannelType.PERSONAL);
            if (user != null) {
                if (!TextUtils.isEmpty(user.channelName)) {
                    return user.channelName;
                }
                if (!TextUtils.isEmpty(user.channelRemark)) {
                    return user.channelRemark;
                }
            }
        }
        return fallback;
    }

    private void ensureRtcNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || appContext == null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                WKConstants.newRTCChannelID,
                "音视频邀请通知",
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription("音视频通话邀请");
        channel.enableVibration(true);
        channel.setVibrationPattern(new long[]{0, 1000, 1000, 1000});
        channel.setSound(
                Uri.parse("android.resource://" + appContext.getPackageName() + "/raw/newrtc"),
                Notification.AUDIO_ATTRIBUTES_DEFAULT
        );
        NotificationManager manager = (NotificationManager) appContext.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    private void runOnMain(Runnable runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
        } else {
            mainHandler.post(runnable);
        }
    }
}
