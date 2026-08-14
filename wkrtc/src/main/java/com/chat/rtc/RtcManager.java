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
    public void init(Context context) {
        appContext = context.getApplicationContext();
        Log.i(TAG, "rtc initialized");
        registerMessages();
        registerEndpoints();
    }

    private void registerMessages() {
        WKIM.getInstance().getMsgManager().registerContentMsg(RtcRecordContent.class);
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
            if ("rtc_notice".equals(content.optString("type"))
                    || content.optInt("type", -1) == WKContentType.rtcNotice) {
                msg.isDeleted = 1;
            } else if (isRtcRecordPayload(content)) {
                msg.type = WKContentType.rtcRecord;
            }
        } catch (Exception ignored) {
        }
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
            if (object instanceof WKChannel && ((WKChannel) object).channelType == WKChannelType.PERSONAL) {
                scheduleProbeChannelState((WKChannel) object, true);
            }
            return true;
        });
        EndpointManager.getInstance().setMethod("wk_p2p_call", object -> {
            if (object instanceof RTCMenu) {
                RTCMenu menu = (RTCMenu) object;
                IConversationContext conversation = menu.iConversationContext;
                if (conversation != null && conversation.getChatChannelInfo() != null
                        && conversation.getChatChannelInfo().channelType == WKChannelType.PERSONAL) {
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
        if (activity == null || channel == null || channel.channelType != WKChannelType.PERSONAL) {
            return;
        }
        if (isRtcOperationForbidden(channel)) {
            WKToastUtils.getInstance().showToastNormal(
                    activity.getString(R.string.wkrtc_outgoing_call_forbidden)
            );
            return;
        }
        if (isCalling()) {
            reopenCurrentCall(activity);
            return;
        }
        Intent intent = RtcCallActivity.newOutgoingIntent(activity, channel, callType, inviteUIDs, inviteNames);
        activity.startActivity(intent);
    }

    /**
     * RTC follows the existing message-send mute semantics: during a group-wide
     * mute, only the owner and administrators may operate calls. An individually
     * muted member is blocked regardless of role.
     */
    public boolean isRtcOperationForbidden(WKChannel targetChannel) {
        if (targetChannel == null || TextUtils.isEmpty(targetChannel.channelID)) {
            return false;
        }
        if (targetChannel.channelType != WKChannelType.PERSONAL) {
            return true;
        }
        WKChannel cachedChannel = WKIM.getInstance().getChannelManager().getChannel(
                targetChannel.channelID,
                targetChannel.channelType
        );
        boolean groupWideForbidden = targetChannel.forbidden == 1
                || (cachedChannel != null && cachedChannel.forbidden == 1);
        return groupWideForbidden;
    }

    public void openFromMessage(WKMsg msg) {
        if (msg == null || TextUtils.isEmpty(msg.channelID) || msg.channelType != WKChannelType.PERSONAL) {
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
        if (channel == null || channel.channelType != WKChannelType.PERSONAL) {
            if (listener != null) {
                listener.onFail(-1, "仅支持单聊音视频通话");
            }
            return;
        }
        List<String> effectiveInviteUIDs;
        effectiveInviteUIDs = new ArrayList<>();
        if (!TextUtils.isEmpty(channel.channelID)) {
            effectiveInviteUIDs.add(channel.channelID);
        }
        Log.i(TAG, "start outgoing call, channelId=" + (channel == null ? "" : channel.channelID)
                + ", channelType=" + (channel == null ? "" : channel.channelType)
                + ", callType=" + callType
                + ", loginUID=" + WKConfig.getInstance().getUid()
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
        notifyChanged();
        EndpointManager.getInstance().invoke("play_rtc_media", null);

        RtcModel.getInstance().startCall(
                UUID.randomUUID().toString(),
                channel.channelID,
                channel.channelType,
                callType == 1 ? "video" : "audio",
                getDeviceId(),
                effectiveInviteUIDs,
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
                || (session.liveKitConnected && (session.incoming || RtcSession.JOINING.equals(session.status)))) {
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
        notifyChanged();
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
            if (payload.channel_type != WKChannelType.PERSONAL) {
                Log.i(TAG, "ignore non-personal rtc invite, callId=" + payload.call_id
                        + ", channelType=" + payload.channel_type);
                clearIgnoredInviteEffects("non-personal invite");
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
            WKChannel incomingChannel = buildIncomingChannel(payload);
            if (isRtcOperationForbidden(incomingChannel)) {
                Log.i(TAG, "ignore invite while current member is muted, callId=" + payload.call_id);
                clearIgnoredInviteEffects("muted member invite");
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
            session.channel = incomingChannel;
            session.callType = "video".equals(payload.call_type) ? 1 : 0;
            session.status = RtcSession.INCOMING;
            session.incoming = true;
            session.fromUID = payload.from_uid;
            session.fromName = resolveUserName(payload.from_uid, payload.from_name);
            session.expireAt = payload.expire_at;
            session.localCameraEnabled = session.callType == 1;
            session.localMicrophoneEnabled = true;
            session.minimized = false;
            notifyChanged();
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
        if (payload != null && (payload.channel_type != WKChannelType.PERSONAL || payload.invite_all)) {
            Log.i(TAG, "ignore non-personal rtc cmd=" + cmd + ", channelType=" + payload.channel_type);
            clearIgnoredInviteEffects("non-personal rtc cmd");
            return true;
        }
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
            receiveInvite(payload);
            return true;
        }
        if (isCmd(rtcCmd, "notice")) {
            return true;
        }
        if (session == null) {
            return true;
        }
        if (isCmd(rtcCmd, "joined")
                && TextUtils.equals(session.callId, payload == null ? "" : payload.call_id)) {
            runOnMain(() -> {
                if (session != null) {
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
                Log.i(TAG, "ignore terminal cmd for other call, cmd=" + cmd
                        + ", callId=" + (payload == null ? "" : payload.call_id)
                        + ", currentCallId=" + (session == null ? "" : session.callId));
                return true;
            }
            finish();
            return true;
        }
        return true;
    }

    private boolean isPayloadForCurrentCall(RtcCallPayload payload) {
        return payload == null
                || TextUtils.isEmpty(payload.call_id)
                || session == null
                || TextUtils.equals(session.callId, payload.call_id);
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
        if (channelType != WKChannelType.PERSONAL) {
            return true;
        }
        if (TextUtils.isEmpty(channelID)) {
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

    public void probeChannelState(WKChannel channel, boolean showIncomingIfCalling) {
        if (channel == null || TextUtils.isEmpty(channel.channelID)
                || channel.channelType != WKChannelType.PERSONAL) {
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
        if (channel == null || TextUtils.isEmpty(channel.channelID)
                || channel.channelType != WKChannelType.PERSONAL) {
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
            if (channelType <= 0 || channelType == WKChannelType.PERSONAL) {
                WKChannel rtcChannel = new WKChannel(channelId, channelType);
                rtcChannel.channelType = WKChannelType.PERSONAL;
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

    private String resolveSessionTitle() {
        if (session == null) {
            return "";
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
