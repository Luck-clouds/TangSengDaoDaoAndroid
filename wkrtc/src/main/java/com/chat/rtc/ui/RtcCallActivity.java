package com.chat.rtc.ui;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Bundle;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.chat.base.config.WKConfig;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.net.IRequestResultListener;
import com.chat.base.net.entity.CommonResponse;
import com.chat.base.utils.WKToastUtils;
import com.chat.rtc.R;
import com.chat.rtc.RtcManager;
import com.chat.rtc.entity.RtcCallPayload;
import com.chat.rtc.entity.RtcCallResp;
import com.chat.rtc.entity.RtcSession;
import com.chat.rtc.livekit.RtcAudioHelper;
import com.chat.rtc.livekit.RtcLiveKitCallback;
import com.chat.rtc.livekit.RtcLiveKitClient;
import com.chat.rtc.message.RtcNoticeContent;
import com.chat.rtc.net.RtcModel;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelMember;
import com.xinbida.wukongim.entity.WKChannelType;
import com.xinbida.wukongim.WKIM;

import io.livekit.android.renderer.SurfaceViewRenderer;
import io.livekit.android.renderer.TextureViewRenderer;
import io.livekit.android.room.track.VideoTrack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public class RtcCallActivity extends Activity implements RtcManager.SessionListener {
    private static final String TAG = "WKRTC";
    private static final int REQUEST_MEDIA_PERMISSION = 3701;
    private static final int REQUEST_PICK_INVITE_MEMBERS = 3702;
    private static final String EXTRA_DIRECTION = "direction";
    private static final String EXTRA_CHANNEL_ID = "channel_id";
    private static final String EXTRA_CHANNEL_TYPE = "channel_type";
    private static final String EXTRA_CALL_TYPE = "call_type";
    private static final String EXTRA_INVITE_UIDS = "invite_uids";
    private static final String EXTRA_INVITE_NAMES = "invite_names";
    private static final String EXTRA_CALL_ID = "call_id";
    private static final String EXTRA_ROOM_NAME = "room_name";
    private static final String EXTRA_FROM_UID = "from_uid";
    private static final String EXTRA_FROM_NAME = "from_name";
    private static final String EXTRA_INVITE_ALL = "invite_all";
    private static final String DIRECTION_OUTGOING = "outgoing";
    private static final String DIRECTION_INCOMING = "incoming";
    private static final String DIRECTION_JOIN = "join";
    private static final String LOCAL_TILE_ID = "__local__";

    private TextView titleTv;
    private TextView statusTv;
    private TextView avatarTv;
    private FrameLayout groupAvatarCluster;
    private TextView groupFooterTitleTv;
    private LinearLayout controlsLayout;
    private LinearLayout contentLayout;
    private LinearLayout headerLayout;
    private FrameLayout mediaContainer;
    private View videoTopScrim;
    private View videoBottomScrim;
    private View audioLeftOrb;
    private View audioRightOrb;
    private View audioBottomGlow;
    private GridLayout videoGrid;
    private FrameLayout localTile;
    private FrameLayout privateRemoteContainer;
    private FrameLayout privateLocalContainer;
    private TextView privateRemoteNameTv;
    private TextView privateLocalNameTv;
    private FrameLayout privateRemotePlaceholder;
    private TextView privateRemoteAvatarTv;
    private TextView privateRemotePlaceholderNameTv;
    private TextureViewRenderer groupLocalRenderer;
    private SurfaceViewRenderer remoteRenderer;
    private TextureViewRenderer localRenderer;
    private VideoTrack remoteVideoTrack;
    private VideoTrack localVideoTrack;
    private final Map<String, FrameLayout> remoteTiles = new HashMap<>();
    private final Map<String, TextureViewRenderer> remoteRenderers = new HashMap<>();
    private final Map<String, VideoTrack> remoteVideoTracks = new HashMap<>();
    private final Map<String, String> participantNames = new HashMap<>();
    private final Map<String, String> groupParticipantRoster = new HashMap<>();
    private final List<TextView> groupAvatarLabels = new ArrayList<>();
    private TextView groupAvatarOverflowTv;
    private String selectedGroupTileId;
    private boolean remoteRendererReady;
    private boolean localRendererReady;
    private boolean privateLocalPrimary;
    private boolean remoteVideoMuted = true;
    private WKChannel channel;
    private int initialCallType;
    private int callType;
    private String direction;
    private ArrayList<String> inviteUIDs;
    private ArrayList<String> inviteNames;
    private boolean inviteAll;
    private RtcLiveKitCallback liveKitCallback;
    private String activeLiveKitCallId;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private long inCallStartedAt;
    private boolean speakerEnabled;
    private AlertDialog keypadDialog;
    private AlertDialog participantsDialog;
    private AlertDialog inviteDialog;
    private boolean closingActivity;
    private boolean activityDestroyed;
    private boolean endScreenPending;
    private boolean endToastShown;
    private boolean cameraPausedForInvitePicker;
    private final Runnable endScreenFinishRunnable = () -> {
        if (endScreenPending) {
            finish();
        }
    };
    private final Runnable durationTicker = new Runnable() {
        @Override
        public void run() {
            RtcSession session = RtcManager.getInstance().currentSession();
            if (session != null && RtcSession.IN_CALL.equals(session.status)) {
                if (isGroupCall()) {
                    statusTv.setText(groupStatusText(session));
                } else if (callType == 0) {
                    statusTv.setText(formatDuration());
                }
                uiHandler.postDelayed(this, 1000L);
            }
        }
    };

    public static Intent newOutgoingIntent(Context context, WKChannel channel, int callType,
                                           List<String> inviteUIDs, List<String> inviteNames) {
        return newOutgoingIntent(context, channel, callType, inviteUIDs, inviteNames, false);
    }

    public static Intent newOutgoingIntent(Context context, WKChannel channel, int callType,
                                           List<String> inviteUIDs, List<String> inviteNames, boolean inviteAll) {
        Intent intent = new Intent(context, RtcCallActivity.class);
        intent.putExtra(EXTRA_DIRECTION, DIRECTION_OUTGOING);
        intent.putExtra(EXTRA_CHANNEL_ID, channel.channelID);
        intent.putExtra(EXTRA_CHANNEL_TYPE, channel.channelType);
        intent.putExtra(EXTRA_CALL_TYPE, callType);
        intent.putExtra(EXTRA_INVITE_ALL, inviteAll);
        if (inviteUIDs != null) {
            intent.putStringArrayListExtra(EXTRA_INVITE_UIDS, new ArrayList<>(inviteUIDs));
        }
        if (inviteNames != null) {
            intent.putStringArrayListExtra(EXTRA_INVITE_NAMES, new ArrayList<>(inviteNames));
        }
        return intent;
    }

    public static Intent newIncomingIntent(Context context, RtcCallPayload payload) {
        Intent intent = new Intent(context, RtcCallActivity.class);
        intent.putExtra(EXTRA_DIRECTION, DIRECTION_INCOMING);
        String channelID = payload.channel_id;
        if (payload.channel_type == WKChannelType.PERSONAL && !TextUtils.isEmpty(payload.from_uid)) {
            channelID = payload.from_uid;
        }
        intent.putExtra(EXTRA_CHANNEL_ID, channelID);
        intent.putExtra(EXTRA_CHANNEL_TYPE, payload.channel_type);
        intent.putExtra(EXTRA_CALL_TYPE, "video".equals(payload.call_type) ? 1 : 0);
        intent.putExtra(EXTRA_CALL_ID, payload.call_id);
        intent.putExtra(EXTRA_ROOM_NAME, payload.room_name);
        intent.putExtra(EXTRA_FROM_UID, payload.from_uid);
        intent.putExtra(EXTRA_FROM_NAME, payload.from_name);
        return intent;
    }

    public static Intent newJoinIntent(Context context, RtcCallPayload payload) {
        Intent intent = newIncomingIntent(context, payload);
        intent.putExtra(EXTRA_DIRECTION, DIRECTION_JOIN);
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureWindow();
        readExtras();
        RtcSession currentSession = RtcManager.getInstance().currentSession();
        boolean resumingExistingSession = shouldResumeExistingSession(currentSession);
        if (resumingExistingSession) {
            syncUiStateFromSession(currentSession);
        }
        speakerEnabled = callType == 1;
        buildLayout();
        RtcManager.getInstance().addListener(this);
        if (currentSession != null) {
            currentSession.minimized = false;
        }
        if (DIRECTION_INCOMING.equals(direction)) {
            RtcManager.getInstance().dismissIncomingNotificationOnly();
        }
        RtcSession renderSession = currentSession;
        if (!resumingExistingSession && DIRECTION_OUTGOING.equals(direction)
                && currentSession != null && RtcSession.ENDED.equals(currentSession.status)) {
            renderSession = null;
        }
        renderSession(renderSession);

        if (DIRECTION_JOIN.equals(direction)) {
            joinCurrentCallAfterPermission();
        } else if (resumingExistingSession) {
            rebindLiveKitStateIfNeeded(currentSession);
        } else if (DIRECTION_OUTGOING.equals(direction)) {
            startOutgoingAfterPermission();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent == null) {
            return;
        }
        clearPendingEndScreen();
        releaseVideoRenderers();
        String previousChannelId = channel == null ? null : channel.channelID;
        byte previousChannelType = channel == null ? WKChannelType.PERSONAL : channel.channelType;
        int previousCallType = callType;
        setIntent(intent);
        readExtras();
        RtcSession currentSession = RtcManager.getInstance().currentSession();
        boolean resumingExistingSession = shouldResumeExistingSession(currentSession);
        if (resumingExistingSession) {
            syncUiStateFromSession(currentSession);
        }
        boolean layoutChanged = previousCallType != callType
                || previousChannelType != (channel == null ? WKChannelType.PERSONAL : channel.channelType)
                || !TextUtils.equals(previousChannelId, channel == null ? null : channel.channelID);
        if (layoutChanged) {
            recreate();
            return;
        }
        if (currentSession != null) {
            currentSession.minimized = false;
        }
        if (DIRECTION_INCOMING.equals(direction)) {
            RtcManager.getInstance().dismissIncomingNotificationOnly();
        }
        speakerEnabled = callType == 1;
        if (DIRECTION_JOIN.equals(direction)) {
            renderSession(currentSession);
            joinCurrentCallAfterPermission();
        } else if (!resumingExistingSession && DIRECTION_OUTGOING.equals(direction)) {
            clearPendingEndScreen();
            renderSession(null);
            startOutgoingAfterPermission();
        } else {
            renderSession(currentSession);
        }
        if (resumingExistingSession) {
            rebindLiveKitStateIfNeeded(currentSession);
        }
    }

    @Override
    protected void onDestroy() {
        activityDestroyed = true;
        uiHandler.removeCallbacks(durationTicker);
        uiHandler.removeCallbacks(endScreenFinishRunnable);
        stopDurationTicker();
        if (keypadDialog != null && keypadDialog.isShowing()) {
            keypadDialog.dismiss();
        }
        if (participantsDialog != null && participantsDialog.isShowing()) {
            participantsDialog.dismiss();
        }
        if (inviteDialog != null && inviteDialog.isShowing()) {
            inviteDialog.dismiss();
        }
        releaseVideoRenderers();
        RtcManager.getInstance().removeListener(this);
        super.onDestroy();
    }

    @Override
    public void finish() {
        if (closingActivity || isFinishing() || isDestroyed()) {
            return;
        }
        closingActivity = true;
        super.finish();
    }

    @Override
    public void onBackPressed() {
        RtcSession session = RtcManager.getInstance().currentSession();
        if (session != null && !RtcSession.ENDED.equals(session.status)) {
            minimizeToChat();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_PICK_INVITE_MEMBERS) {
            return;
        }
        restoreCameraAfterInvitePicker();
        if (resultCode != RESULT_OK || data == null) {
            return;
        }
        ArrayList<String> selectedUIDs = data.getStringArrayListExtra("selectedUIDs");
        ArrayList<String> selectedNames = data.getStringArrayListExtra("selectedNames");
        if (selectedUIDs == null || selectedUIDs.isEmpty()) {
            return;
        }
        inviteMembersToCurrentCall(selectedUIDs, selectedNames);
    }

    @Override
    public void onSessionChanged(RtcSession session) {
        runOnUiThread(() -> {
            if (session == null || RtcSession.ENDED.equals(session.status)) {
                releaseVideoRenderers();
            }
            renderSession(session);
        });
    }

    private void configureWindow() {
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        window.setStatusBarColor(ContextCompat.getColor(this, R.color.wkrtc_bg));
        window.setNavigationBarColor(ContextCompat.getColor(this, R.color.wkrtc_bg));
    }

    private void readExtras() {
        Intent intent = getIntent();
        direction = intent.getStringExtra(EXTRA_DIRECTION);
        callType = intent.getIntExtra(EXTRA_CALL_TYPE, 0);
        initialCallType = callType;
        String channelId = intent.getStringExtra(EXTRA_CHANNEL_ID);
        byte channelType = intent.getByteExtra(EXTRA_CHANNEL_TYPE, WKChannelType.PERSONAL);
        channel = new WKChannel(channelId, channelType);
        inviteUIDs = intent.getStringArrayListExtra(EXTRA_INVITE_UIDS);
        inviteNames = intent.getStringArrayListExtra(EXTRA_INVITE_NAMES);
        inviteAll = intent.getBooleanExtra(EXTRA_INVITE_ALL, false);
    }

    private void buildLayout() {
        FrameLayout root = new FrameLayout(this);
        root.setBackground(backgroundDrawable());

        mediaContainer = new FrameLayout(this);
        mediaContainer.setBackgroundColor(Color.rgb(17, 21, 29));
        root.addView(mediaContainer, new FrameLayout.LayoutParams(-1, -1));

        if (callType == 1) {
            addVideoScrims();
        } else {
            addAudioBackdrop();
        }

        if (isGroupCall()) {
            videoGrid = new GridLayout(this);
            videoGrid.setUseDefaultMargins(false);
            videoGrid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
            mediaContainer.addView(videoGrid, new FrameLayout.LayoutParams(-1, -1));
        } else {
            privateRemoteContainer = new FrameLayout(this);
            mediaContainer.addView(privateRemoteContainer, new FrameLayout.LayoutParams(-1, -1));
            remoteRenderer = new SurfaceViewRenderer(this);
            remoteRenderer.setMirror(false);
            remoteRenderer.setVisibility(callType == 1 ? View.VISIBLE : View.GONE);
            privateRemoteContainer.addView(remoteRenderer, new FrameLayout.LayoutParams(-1, -1));
            privateRemotePlaceholder = buildVideoPlaceholder();
            privateRemoteAvatarTv = privateRemotePlaceholder.findViewWithTag("avatar");
            privateRemotePlaceholderNameTv = privateRemotePlaceholder.findViewWithTag("title");
            privateRemotePlaceholder.setVisibility(View.GONE);
            privateRemoteContainer.addView(privateRemotePlaceholder, new FrameLayout.LayoutParams(-1, -1));
            privateRemoteNameTv = buildOverlayNameLabel();
            FrameLayout.LayoutParams remoteNameLp = new FrameLayout.LayoutParams(-2, dp(24));
            remoteNameLp.gravity = Gravity.LEFT | Gravity.BOTTOM;
            remoteNameLp.leftMargin = dp(16);
            remoteNameLp.bottomMargin = dp(20);
            privateRemoteContainer.addView(privateRemoteNameTv, remoteNameLp);
        }

        contentLayout = new LinearLayout(this);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setGravity(Gravity.CENTER_HORIZONTAL);
        contentLayout.setPadding(dp(20), dp(72), dp(20), dp(32));
        root.addView(contentLayout, new FrameLayout.LayoutParams(-1, -1));

        headerLayout = new LinearLayout(this);
        headerLayout.setOrientation(LinearLayout.VERTICAL);
        headerLayout.setGravity(Gravity.CENTER_HORIZONTAL);
        contentLayout.addView(headerLayout, new LinearLayout.LayoutParams(-1, -2));

        groupAvatarCluster = buildGroupAvatarCluster();
        LinearLayout.LayoutParams clusterLp = new LinearLayout.LayoutParams(dp(240), dp(96));
        headerLayout.addView(groupAvatarCluster, clusterLp);

        avatarTv = new TextView(this);
        avatarTv.setGravity(Gravity.CENTER);
        avatarTv.setTextColor(Color.WHITE);
        avatarTv.setTextSize(40);
        avatarTv.setTypeface(Typeface.DEFAULT_BOLD);
        avatarTv.setBackground(circleDrawable(Color.argb(235, 83, 132, 255)));
        avatarTv.setPadding(0, 0, 0, dp(4));
        LinearLayout.LayoutParams avatarLp = new LinearLayout.LayoutParams(dp(128), dp(128));
        headerLayout.addView(avatarTv, avatarLp);

        titleTv = new TextView(this);
        titleTv.setGravity(Gravity.CENTER);
        titleTv.setTextColor(Color.WHITE);
        titleTv.setTextSize(30);
        titleTv.setTypeface(Typeface.DEFAULT_BOLD);
        titleTv.setSingleLine(true);
        titleTv.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, -2);
        titleLp.topMargin = dp(24);
        headerLayout.addView(titleTv, titleLp);

        statusTv = new TextView(this);
        statusTv.setGravity(Gravity.CENTER);
        statusTv.setTextColor(Color.WHITE);
        statusTv.setTextSize(14);
        statusTv.setPadding(0, 0, 0, 0);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(-2, -2);
        statusLp.topMargin = dp(12);
        headerLayout.addView(statusTv, statusLp);

        View spacer = new View(this);
        contentLayout.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1));

        controlsLayout = new LinearLayout(this);
        controlsLayout.setOrientation(LinearLayout.VERTICAL);
        controlsLayout.setGravity(Gravity.CENTER_HORIZONTAL);
        contentLayout.addView(controlsLayout, new LinearLayout.LayoutParams(-1, -2));

        if (isGroupCall()) {
            groupLocalRenderer = new TextureViewRenderer(this);
            groupLocalRenderer.setMirror(false);
            localTile = createVideoTile(groupLocalRenderer, LOCAL_TILE_ID, getString(R.string.wkrtc_me));
            videoGrid.addView(localTile);
            updateGroupGridLayout();
        } else {
            privateLocalContainer = new FrameLayout(this);
            privateLocalContainer.setBackground(roundedDrawable(Color.argb(80, 255, 255, 255), dp(24)));
            localRenderer = new TextureViewRenderer(this);
            localRenderer.setMirror(false);
            privateLocalContainer.addView(localRenderer, new FrameLayout.LayoutParams(-1, -1));
            privateLocalNameTv = buildOverlayNameLabel();
            privateLocalNameTv.setText(getString(R.string.wkrtc_me));
            FrameLayout.LayoutParams localNameLp = new FrameLayout.LayoutParams(-2, dp(22));
            localNameLp.gravity = Gravity.LEFT | Gravity.BOTTOM;
            localNameLp.leftMargin = dp(8);
            localNameLp.bottomMargin = dp(8);
            privateLocalContainer.addView(privateLocalNameTv, localNameLp);
            privateLocalContainer.setOnClickListener(v -> togglePrivatePrimaryVideo());
        mediaContainer.addView(privateLocalContainer);
        updatePrivateVideoLayout();
        privateLocalContainer.setVisibility(callType == 1 ? View.VISIBLE : View.GONE);
        }
        if (isGroupCall()) {
            groupLocalRenderer.setVisibility(callType == 1 ? View.VISIBLE : View.GONE);
        } else {
            localRenderer.setVisibility(callType == 1 ? View.VISIBLE : View.GONE);
        }

        groupFooterTitleTv = new TextView(this);
        groupFooterTitleTv.setTextColor(Color.WHITE);
        groupFooterTitleTv.setTextSize(15);
        groupFooterTitleTv.setTypeface(Typeface.DEFAULT_BOLD);
        groupFooterTitleTv.setPadding(dp(16), dp(6), dp(16), dp(6));
        groupFooterTitleTv.setBackground(roundedDrawable(Color.argb(110, 7, 11, 19), dp(14)));
        groupFooterTitleTv.setVisibility(View.GONE);
        FrameLayout.LayoutParams groupFooterLp = new FrameLayout.LayoutParams(-2, -2);
        groupFooterLp.gravity = Gravity.LEFT | Gravity.BOTTOM;
        groupFooterLp.leftMargin = dp(16);
        groupFooterLp.bottomMargin = dp(272);
        mediaContainer.addView(groupFooterTitleTv, groupFooterLp);

        addTopActionButtons(root);
        setContentView(root);
    }

    private void startOutgoingAfterPermission() {
        clearPendingEndScreen();
        if (!hasMediaPermissions()) {
            ActivityCompat.requestPermissions(this, permissionsForCall(), REQUEST_MEDIA_PERMISSION);
            return;
        }
        RtcManager.getInstance().startOutgoing(channel, callType, inviteUIDs, inviteNames, inviteAll, new IRequestResultListener<>() {
            @Override
            public void onSuccess(RtcCallResp result) {
                RtcManager.getInstance().applyCallResp(result);
                sendInitialGroupNoticeFallback();
                connectLiveKit(result);
            }

            @Override
            public void onFail(int code, String msg) {
                WKToastUtils.getInstance().showToastNormal(TextUtils.isEmpty(msg) ? getString(R.string.wkrtc_call_failed) : msg);
                RtcManager.getInstance().finish();
                finish();
            }
        });
    }

    private void joinCurrentCallAfterPermission() {
        if (!hasMediaPermissions()) {
            ActivityCompat.requestPermissions(this, permissionsForCall(), REQUEST_MEDIA_PERMISSION);
            return;
        }
        RtcSession session = RtcManager.getInstance().currentSession();
        if (session == null || TextUtils.isEmpty(session.callId)) {
            WKToastUtils.getInstance().showToastNormal("\u5df2\u7ed3\u675f\u901a\u8bdd");
            finish();
            return;
        }
        if (session.liveKitConnected && RtcSession.IN_CALL.equals(session.status)) {
            renderSession(session);
            return;
        }
        session.status = RtcSession.JOINING;
        session.incoming = false;
        renderSession(session);
        RtcModel.getInstance().joinCall(session.callId, RtcManager.getInstance().getDeviceId(), "", new IRequestResultListener<>() {
            @Override
            public void onSuccess(RtcCallResp result) {
                RtcManager.getInstance().applyCallResp(result);
                connectLiveKit(result);
            }

            @Override
            public void onFail(int code, String msg) {
                WKToastUtils.getInstance().showToastNormal(TextUtils.isEmpty(msg) ? "\u52a0\u5165\u901a\u8bdd\u5931\u8d25" : msg);
                RtcManager.getInstance().finish(false);
                finish();
            }
        });
    }

    private void acceptIncoming() {
        if (!hasMediaPermissions()) {
            ActivityCompat.requestPermissions(this, permissionsForCall(), REQUEST_MEDIA_PERMISSION);
            return;
        }
        RtcManager.getInstance().dismissIncomingAlert();
        RtcSession session = RtcManager.getInstance().currentSession();
        if (session == null || TextUtils.isEmpty(session.callId)) {
            return;
        }
        session.status = RtcSession.JOINING;
        renderSession(session);
        RtcModel.getInstance().joinCall(session.callId, RtcManager.getInstance().getDeviceId(), "", new IRequestResultListener<>() {
            @Override
            public void onSuccess(RtcCallResp result) {
                RtcManager.getInstance().applyCallResp(result);
                connectLiveKit(result);
            }

            @Override
            public void onFail(int code, String msg) {
                WKToastUtils.getInstance().showToastNormal(TextUtils.isEmpty(msg) ? getString(R.string.wkrtc_call_failed) : msg);
                RtcManager.getInstance().finish();
                finish();
            }
        });
    }

    private void hangup() {
        RtcSession session = RtcManager.getInstance().currentSession();
        if (session == null || TextUtils.isEmpty(session.callId)) {
            releaseVideoRenderers();
            RtcManager.getInstance().finish();
            return;
        }
        String callId = session.callId;
        IRequestResultListener<CommonResponse> listener = new IRequestResultListener<>() {
            @Override
            public void onSuccess(CommonResponse result) {
                Log.i(TAG, "hangup request success, callId=" + callId);
            }

            @Override
            public void onFail(int code, String msg) {
                Log.w(TAG, "hangup request failed, callId=" + callId + ", code=" + code + ", msg=" + msg);
            }
        };
        if (RtcSession.INCOMING.equals(session.status)) {
            RtcModel.getInstance().rejectCall(callId, RtcManager.getInstance().getDeviceId(), listener);
        } else if (RtcSession.OUTGOING.equals(session.status) && "calling".equals(session.serverStatus)) {
            RtcModel.getInstance().cancelCall(callId, RtcManager.getInstance().getDeviceId(), listener);
        } else if (session.channel != null && session.channel.channelType == WKChannelType.PERSONAL) {
            RtcModel.getInstance().closeCall(callId, RtcManager.getInstance().getDeviceId(), listener);
        } else {
            RtcModel.getInstance().leaveCall(callId, RtcManager.getInstance().getDeviceId(), listener);
        }
        boolean canBeReinvitedToGroupCall = session.channel != null
                && session.channel.channelType == WKChannelType.GROUP
                && (RtcSession.INCOMING.equals(session.status) || RtcSession.IN_CALL.equals(session.status));
        releaseVideoRenderers();
        RtcManager.getInstance().finish(!canBeReinvitedToGroupCall);
    }

    private void connectLiveKit(RtcCallResp result) {
        if (!isActivityUsable()) {
            return;
        }
        statusTv.setText(R.string.wkrtc_connecting);
        RtcSession session = RtcManager.getInstance().currentSession();
        activeLiveKitCallId = result != null && !TextUtils.isEmpty(result.call_id)
                ? result.call_id
                : session == null ? "" : session.callId;
        liveKitCallback = buildLiveKitCallback(activeLiveKitCallId);
        RtcLiveKitClient.connect(this, result, callType, liveKitCallback);
    }

    private RtcLiveKitCallback buildLiveKitCallback(String expectedCallId) {
        return new RtcLiveKitCallback() {
            @Override
            public void onConnected() {
                runOnUiThread(() -> {
                    if (!isLiveKitCallbackActive(expectedCallId)) {
                        return;
                    }
                    RtcSession session = RtcManager.getInstance().currentSession();
                    if (session != null) {
                        session.liveKitConnected = true;
                        session.incoming = false;
                        if (!RtcSession.OUTGOING.equals(session.status) || isInviteAllGroupSession(session)) {
                            session.status = RtcSession.IN_CALL;
                        }
                        renderSession(session);
                    }
                });
            }

            @Override
            public void onLocalVideoTrack(VideoTrack track, String identity, String name) {
                runOnUiThread(() -> {
                    if (isLiveKitCallbackActive(expectedCallId)) {
                        attachLocalVideo(track, identity, name);
                    }
                });
            }

            @Override
            public void onRemoteVideoTrack(VideoTrack track, String identity, String name) {
                runOnUiThread(() -> {
                    if (isLiveKitCallbackActive(expectedCallId)) {
                        attachRemoteVideo(track, identity, name);
                    }
                });
            }

            @Override
            public void onRemoteVideoMuteChanged(String identity, String name, boolean muted) {
                runOnUiThread(() -> {
                    if (isLiveKitCallbackActive(expectedCallId)) {
                        updateRemoteVideoMuted(identity, name, muted);
                    }
                });
            }

            @Override
            public void onRemoteVideoRemoved(String identity) {
                runOnUiThread(() -> {
                    if (isLiveKitCallbackActive(expectedCallId)) {
                        detachRemoteVideo(identity);
                    }
                });
            }

            @Override
            public void onParticipantsChanged(Map<String, String> participants) {
                runOnUiThread(() -> {
                    if (isLiveKitCallbackActive(expectedCallId)) {
                        updateParticipantsRoster(participants);
                    }
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    if (!isLiveKitCallbackActive(expectedCallId)) {
                        return;
                    }
                    WKToastUtils.getInstance().showToastNormal(message);
                    RtcManager.getInstance().finish();
                    finish();
                });
            }
        };
    }

    private boolean shouldResumeExistingSession(RtcSession session) {
        return session != null
                && session.channel != null
                && !RtcSession.ENDED.equals(session.status)
                && channel != null
                && TextUtils.equals(channel.channelID, session.channel.channelID)
                && channel.channelType == session.channel.channelType;
    }

    private void syncUiStateFromSession(RtcSession session) {
        if (session == null) {
            return;
        }
        channel = session.channel;
        callType = session.callType;
        initialCallType = session.callType;
        inviteUIDs = session.inviteUIDs == null ? null : new ArrayList<>(session.inviteUIDs);
        inviteNames = session.inviteNames == null ? null : new ArrayList<>(session.inviteNames);
        inviteAll = session.inviteAll;
    }

    private void rebindLiveKitStateIfNeeded(RtcSession session) {
        if (session == null || !session.liveKitConnected) {
            return;
        }
        activeLiveKitCallId = session.callId;
        liveKitCallback = buildLiveKitCallback(activeLiveKitCallId);
        RtcLiveKitClient.rebindState(liveKitCallback);
    }

    private boolean isActivityUsable() {
        return !activityDestroyed && !closingActivity && !isFinishing() && !isDestroyed();
    }

    private boolean isLiveKitCallbackActive(String expectedCallId) {
        if (!isActivityUsable()) {
            return false;
        }
        RtcSession session = RtcManager.getInstance().currentSession();
        return session != null
                && !RtcSession.ENDED.equals(session.status)
                && (TextUtils.isEmpty(expectedCallId) || TextUtils.equals(expectedCallId, session.callId))
                && (TextUtils.isEmpty(activeLiveKitCallId) || TextUtils.equals(activeLiveKitCallId, session.callId));
    }

    private boolean isInviteAllGroupSession(RtcSession session) {
        return session != null
                && session.inviteAll
                && session.channel != null
                && session.channel.channelType == WKChannelType.GROUP;
    }

    private void attachLocalVideo(VideoTrack track, String identity, String name) {
        if (track == null || !isActivityUsable()) {
            return;
        }
        RtcSession session = RtcManager.getInstance().currentSession();
        if (callType != 1 && (session == null || !session.localCameraEnabled)) {
            return;
        }
        String localDisplayName = resolveParticipantName(identity, name);
        if (TextUtils.isEmpty(localDisplayName)) {
            localDisplayName = getString(R.string.wkrtc_me);
        }
        participantNames.put(TextUtils.isEmpty(identity) ? "local" : identity, localDisplayName);
        if (!TextUtils.isEmpty(identity)) {
            groupParticipantRoster.put(identity, localDisplayName);
        }
        if (privateLocalNameTv != null) {
            privateLocalNameTv.setText(getString(R.string.wkrtc_me));
        }
        if (isGroupCall()) {
            if (groupLocalRenderer == null) {
                return;
            }
            if (!localRendererReady) {
                RtcLiveKitClient.initVideoRenderer(groupLocalRenderer);
                groupLocalRenderer.setMirror(false);
                groupLocalRenderer.setEnableHardwareScaler(false);
                localRendererReady = true;
            }
        } else {
            if (localRenderer == null) {
                return;
            }
            if (!localRendererReady) {
                RtcLiveKitClient.initVideoRenderer(localRenderer);
                localRenderer.setMirror(false);
                localRenderer.setEnableHardwareScaler(false);
                localRendererReady = true;
            }
        }
        if (localVideoTrack != null) {
            if (isGroupCall()) {
                localVideoTrack.removeRenderer(groupLocalRenderer);
            } else {
                localVideoTrack.removeRenderer(localRenderer);
            }
        }
        localVideoTrack = track;
        if (isGroupCall()) {
            groupLocalRenderer.clearImage();
            localVideoTrack.addRenderer(groupLocalRenderer);
            groupLocalRenderer.setVisibility(View.VISIBLE);
        } else {
            localRenderer.clearImage();
            localVideoTrack.addRenderer(localRenderer);
            localRenderer.setVisibility(View.VISIBLE);
            updateLocalPreviewVisibility();
        }
        updateGroupGridLayout();
        updateGroupCallChrome(session);
    }

    private void attachRemoteVideo(VideoTrack track, String identity, String name) {
        if (track == null || !isActivityUsable()) {
            return;
        }
        remoteVideoMuted = false;
        String displayName = resolveParticipantName(identity, name);
        participantNames.put(TextUtils.isEmpty(identity) ? String.valueOf(track.hashCode()) : identity, displayName);
        if (!TextUtils.isEmpty(identity)) {
            groupParticipantRoster.put(identity, displayName);
        }
        if (isGroupCall()) {
            attachGroupRemoteVideo(track, TextUtils.isEmpty(identity) ? String.valueOf(track.hashCode()) : identity, displayName);
            return;
        }
        updatePrivateRemotePlaceholder(displayName, false);
        if (privateRemoteNameTv != null) {
            privateRemoteNameTv.setText(displayName);
            privateRemoteNameTv.setVisibility(View.VISIBLE);
        }
        if (remoteRenderer == null) {
            return;
        }
        if (!remoteRendererReady) {
            RtcLiveKitClient.initVideoRenderer(remoteRenderer);
            remoteRenderer.setMirror(false);
            remoteRenderer.setEnableHardwareScaler(false);
            remoteRendererReady = true;
        }
        remoteRenderer.setMirror(false);
        if (remoteVideoTrack == track) {
            return;
        }
        if (remoteVideoTrack != null) {
            remoteVideoTrack.removeRenderer(remoteRenderer);
        }
        remoteVideoTrack = track;
        remoteVideoTrack.addRenderer(remoteRenderer);
        remoteRenderer.setVisibility(View.VISIBLE);
        updateSingleCallChrome(RtcManager.getInstance().currentSession());
    }

    private void attachGroupRemoteVideo(VideoTrack track, String identity, String name) {
        if (track == null || !isActivityUsable() || videoGrid == null) {
            return;
        }
        TextureViewRenderer renderer = remoteRenderers.get(identity);
        VideoTrack oldTrack = remoteVideoTracks.get(identity);
        if (renderer == null) {
            renderer = new TextureViewRenderer(this);
            RtcLiveKitClient.initVideoRenderer(renderer);
            renderer.setMirror(false);
            renderer.setEnableHardwareScaler(false);
            remoteRenderers.put(identity, renderer);
            FrameLayout tile = createVideoTile(renderer, identity, name);
            remoteTiles.put(identity, tile);
        } else {
            updateTileLabel(remoteTiles.get(identity), name);
            FrameLayout tile = remoteTiles.get(identity);
            if (tile != null) {
                tile.setVisibility(View.VISIBLE);
            }
        }
        renderer.setMirror(false);
        updateTilePlaceholder(remoteTiles.get(identity), name, false);
        if (oldTrack == track) {
            return;
        }
        if (oldTrack != null) {
            oldTrack.removeRenderer(renderer);
        }
        remoteVideoTracks.put(identity, track);
        track.addRenderer(renderer);
        renderer.setVisibility(View.VISIBLE);
        updateGroupGridLayout();
        updateGroupCallChrome(RtcManager.getInstance().currentSession());
    }

    private void detachRemoteVideo(String identity) {
        remoteVideoMuted = true;
        if (callType == 0) {
            updateSingleCallChrome(RtcManager.getInstance().currentSession());
            return;
        }
        if (isGroupCall()) {
            TextureViewRenderer renderer = remoteRenderers.get(identity);
            VideoTrack track = remoteVideoTracks.remove(identity);
            FrameLayout tile = remoteTiles.get(identity);
            if (track != null && renderer != null) {
                track.removeRenderer(renderer);
            }
            if (renderer != null) {
                renderer.clearImage();
                renderer.setVisibility(View.INVISIBLE);
            }
            if (tile != null) {
                tile.setVisibility(View.GONE);
                if (tile.getParent() == videoGrid) {
                    videoGrid.removeView(tile);
                }
            }
            participantNames.remove(identity);
            groupParticipantRoster.remove(identity);
            if (TextUtils.equals(selectedGroupTileId, identity)) {
                selectedGroupTileId = null;
            }
            updateGroupGridLayout();
            updateGroupCallChrome(RtcManager.getInstance().currentSession());
            return;
        }
        if (remoteVideoTrack != null && remoteRenderer != null) {
            remoteVideoTrack.removeRenderer(remoteRenderer);
        }
        remoteVideoTrack = null;
        if (remoteRenderer != null) {
            remoteRenderer.clearImage();
            remoteRenderer.setVisibility(View.INVISIBLE);
        }
        updatePrivateRemotePlaceholder(resolveParticipantName(identity, participantNames.get(identity)), false);
        updateSingleCallChrome(RtcManager.getInstance().currentSession());
    }

    private void updateRemoteVideoMuted(String identity, String name, boolean muted) {
        remoteVideoMuted = muted;
        if (callType == 0) {
            updateSingleCallChrome(RtcManager.getInstance().currentSession());
            return;
        }
        String resolvedName = resolveParticipantName(identity, name);
        participantNames.put(identity, resolvedName);
        if (!TextUtils.isEmpty(identity)) {
            groupParticipantRoster.put(identity, resolvedName);
        }
        if (isGroupCall()) {
            FrameLayout tile = remoteTiles.get(identity);
            TextureViewRenderer renderer = remoteRenderers.get(identity);
            if (tile == null) {
                return;
            }
            updateTileLabel(tile, resolvedName);
            updateTilePlaceholder(tile, resolvedName, muted);
            if (renderer != null) {
                if (muted) {
                    renderer.clearImage();
                    renderer.setVisibility(View.INVISIBLE);
                } else {
                    renderer.setVisibility(View.VISIBLE);
                }
            }
            updateGroupCallChrome(RtcManager.getInstance().currentSession());
            return;
        }
        if (!TextUtils.isEmpty(resolvedName) && privateRemoteNameTv != null) {
            privateRemoteNameTv.setText(resolvedName);
        }
        updatePrivateRemotePlaceholder(resolvedName, muted);
        if (remoteRenderer != null) {
            if (muted) {
                remoteRenderer.clearImage();
                remoteRenderer.setVisibility(View.INVISIBLE);
            } else {
                remoteRenderer.setVisibility(View.VISIBLE);
            }
        }
        updateSingleCallChrome(RtcManager.getInstance().currentSession());
    }

    private void releaseVideoRenderers() {
        if (localVideoTrack != null) {
            safeRemoveRenderer(localVideoTrack, groupLocalRenderer);
            safeRemoveRenderer(localVideoTrack, localRenderer);
        }
        if (remoteVideoTrack != null && remoteRenderer != null) {
            safeRemoveRenderer(remoteVideoTrack, remoteRenderer);
        }
        for (Map.Entry<String, VideoTrack> entry : new HashMap<>(remoteVideoTracks).entrySet()) {
            TextureViewRenderer renderer = remoteRenderers.get(entry.getKey());
            if (renderer != null) {
                safeRemoveRenderer(entry.getValue(), renderer);
            }
        }
        localVideoTrack = null;
        remoteVideoTrack = null;
        remoteVideoTracks.clear();
        remoteTiles.clear();
        groupParticipantRoster.clear();
        selectedGroupTileId = null;
        if (groupLocalRenderer != null && localRendererReady) {
            safeReleaseRenderer(groupLocalRenderer);
        }
        if (localRenderer != null && localRendererReady) {
            safeReleaseRenderer(localRenderer);
        }
        if (remoteRenderer != null && remoteRendererReady) {
            safeReleaseRenderer(remoteRenderer);
        }
        for (TextureViewRenderer renderer : remoteRenderers.values()) {
            safeReleaseRenderer(renderer);
        }
        remoteRenderers.clear();
        localRendererReady = false;
        remoteRendererReady = false;
    }

    private void safeRemoveRenderer(VideoTrack track, TextureViewRenderer renderer) {
        if (track == null || renderer == null) {
            return;
        }
        try {
            track.removeRenderer(renderer);
        } catch (Throwable error) {
            Log.w(TAG, "remove texture renderer failed", error);
        }
    }

    private void safeRemoveRenderer(VideoTrack track, SurfaceViewRenderer renderer) {
        if (track == null || renderer == null) {
            return;
        }
        try {
            track.removeRenderer(renderer);
        } catch (Throwable error) {
            Log.w(TAG, "remove surface renderer failed", error);
        }
    }

    private void safeReleaseRenderer(TextureViewRenderer renderer) {
        if (renderer == null) {
            return;
        }
        try {
            renderer.clearImage();
            renderer.release();
        } catch (Throwable error) {
            Log.w(TAG, "release texture renderer failed", error);
        }
    }

    private void safeReleaseRenderer(SurfaceViewRenderer renderer) {
        if (renderer == null) {
            return;
        }
        try {
            renderer.clearImage();
            renderer.release();
        } catch (Throwable error) {
            Log.w(TAG, "release surface renderer failed", error);
        }
    }

    private void updateGroupGridLayout() {
        if (!isGroupCall() || videoGrid == null) {
            return;
        }
        List<View> orderedTiles = buildOrderedGroupTiles();
        videoGrid.removeAllViews();
        int count = Math.max(orderedTiles.size(), 1);
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        boolean useFocusLayout = count > 4 || !TextUtils.isEmpty(selectedGroupTileId);
        if (!useFocusLayout && count == 2) {
            int spacing = dp(8);
            int horizontalPadding = dp(20);
            int availableWidth = Math.max(1, screenWidth - horizontalPadding * 2 - spacing);
            int cellWidth = availableWidth / 2;
            int cellHeight = Math.min((int) (cellWidth * 1.28f), (int) (screenHeight * 0.34f));
            videoGrid.setColumnCount(2);
            videoGrid.setRowCount(1);
            FrameLayout.LayoutParams gridLp = new FrameLayout.LayoutParams(
                    cellWidth * 2 + spacing,
                    cellHeight
            );
            gridLp.gravity = Gravity.CENTER;
            videoGrid.setLayoutParams(gridLp);
            for (int i = 0; i < orderedTiles.size(); i++) {
                View child = orderedTiles.get(i);
                detachGroupTileFromParent(child);
                GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
                lp.width = cellWidth;
                lp.height = cellHeight;
                lp.setMargins(i == 0 ? 0 : spacing, 0, 0, 0);
                videoGrid.addView(child, lp);
            }
            return;
        }
        if (useFocusLayout) {
            int spacing = dp(4);
            int horizontalPadding = dp(8);
            int availableWidth = Math.max(1, screenWidth - horizontalPadding * 2 - spacing * 2);
            int primaryHeight = Math.min(dp(392), (int) (screenHeight * 0.46f));
            int thumbWidth = availableWidth / 3;
            int thumbHeight = Math.min(dp(132), Math.max(dp(96), (int) (thumbWidth * 1.04f)));
            int otherCount = Math.max(0, count - 1);
            int thumbRows = (int) Math.ceil(otherCount / 3f);

            videoGrid.setColumnCount(3);
            videoGrid.setRowCount(1 + Math.max(1, thumbRows));
            FrameLayout.LayoutParams gridLp = new FrameLayout.LayoutParams(-1, primaryHeight + (thumbRows * (thumbHeight + spacing)) + spacing * 3);
            gridLp.gravity = Gravity.TOP;
            gridLp.leftMargin = horizontalPadding;
            gridLp.rightMargin = horizontalPadding;
            gridLp.topMargin = dp(54);
            videoGrid.setLayoutParams(gridLp);

            for (int i = 0; i < orderedTiles.size(); i++) {
                View child = orderedTiles.get(i);
                detachGroupTileFromParent(child);
                GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
                if (i == 0) {
                    lp.rowSpec = GridLayout.spec(0, 1);
                    lp.columnSpec = GridLayout.spec(0, 3);
                    lp.width = availableWidth + spacing * 2;
                    lp.height = primaryHeight;
                } else {
                    int index = i - 1;
                    lp.rowSpec = GridLayout.spec(1 + index / 3, 1);
                    lp.columnSpec = GridLayout.spec(index % 3, 1);
                    lp.width = thumbWidth;
                    lp.height = thumbHeight;
                }
                lp.setMargins(spacing, spacing, spacing, spacing);
                videoGrid.addView(child, lp);
            }
            return;
        }

        int columns = count <= 1 ? 1 : 2;
        int rows = (int) Math.ceil(count / (float) columns);
        int spacing = dp(4);
        int horizontalPadding = dp(8);
        int availableWidth = Math.max(1, screenWidth - horizontalPadding * 2 - spacing);
        int cellWidth = count == 1 ? availableWidth : availableWidth / 2;
        int cellHeight = Math.min(dp(195), Math.max(dp(160), (int) (cellWidth * 1.02f)));
        videoGrid.setColumnCount(columns);
        videoGrid.setRowCount(rows);
        FrameLayout.LayoutParams gridLp = new FrameLayout.LayoutParams(-1, rows * cellHeight + Math.max(0, rows - 1) * spacing);
        gridLp.gravity = Gravity.TOP;
        gridLp.leftMargin = horizontalPadding;
        gridLp.rightMargin = horizontalPadding;
        gridLp.topMargin = dp(54);
        videoGrid.setLayoutParams(gridLp);
        for (int i = 0; i < orderedTiles.size(); i++) {
            View child = orderedTiles.get(i);
            detachGroupTileFromParent(child);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = cellWidth;
            lp.height = cellHeight;
            lp.setMargins(i % columns == 0 ? 0 : spacing, i < columns ? 0 : spacing, 0, 0);
            videoGrid.addView(child, lp);
        }
    }

    private void detachGroupTileFromParent(View child) {
        if (child != null && child.getParent() instanceof ViewGroup) {
            ((ViewGroup) child.getParent()).removeView(child);
        }
    }

    private boolean isGroupCall() {
        return channel != null && channel.channelType != WKChannelType.PERSONAL;
    }

    private FrameLayout createVideoTile(View renderer, String identity, String name) {
        FrameLayout tile = new FrameLayout(this);
        tile.setTag(identity);
        tile.setBackground(roundedDrawable(Color.rgb(22, 26, 33), dp(10)));
        tile.addView(renderer, new FrameLayout.LayoutParams(-1, -1));
        tile.setOnClickListener(v -> focusGroupTile(identity));
        FrameLayout placeholder = buildVideoPlaceholder();
        placeholder.setTag("placeholder");
        tile.addView(placeholder, new FrameLayout.LayoutParams(-1, -1));
        updateTilePlaceholder(tile, name, false);
        TextView label = new TextView(this);
        label.setTag("nameLabel");
        label.setText(name);
        label.setTextColor(Color.WHITE);
        label.setTextSize(12);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setGravity(Gravity.CENTER);
        label.setPadding(dp(10), dp(4), dp(10), dp(4));
        label.setBackground(roundedDrawable(Color.argb(138, 8, 12, 18), dp(12)));
        FrameLayout.LayoutParams labelLp = new FrameLayout.LayoutParams(-2, dp(24));
        labelLp.gravity = Gravity.LEFT | Gravity.BOTTOM;
        labelLp.leftMargin = dp(12);
        labelLp.bottomMargin = dp(12);
        tile.addView(label, labelLp);

        TextView status = new TextView(this);
        status.setTag("statusLabel");
        status.setTextColor(Color.parseColor("#C9D0D9"));
        status.setTextSize(11);
        status.setText("已开启摄像头");
        FrameLayout.LayoutParams statusLp = new FrameLayout.LayoutParams(-2, -2);
        statusLp.gravity = Gravity.LEFT | Gravity.BOTTOM;
        statusLp.leftMargin = dp(12);
        statusLp.bottomMargin = dp(30);
        tile.addView(status, statusLp);
        return tile;
    }

    private List<View> buildOrderedGroupTiles() {
        List<View> ordered = new ArrayList<>();
        int visibleCount = 0;
        if (localTile != null && localTile.getVisibility() == View.VISIBLE) {
            visibleCount++;
        }
        for (FrameLayout tile : remoteTiles.values()) {
            if (tile != null && tile.getVisibility() == View.VISIBLE) {
                visibleCount++;
            }
        }
        if (!TextUtils.isEmpty(selectedGroupTileId)) {
            View focused = resolveGroupTile(selectedGroupTileId);
            if (focused != null) {
                appendGroupTileIfVisible(ordered, focused);
            }
        } else if (visibleCount > 4) {
            View defaultFocused = resolveGroupTile(LOCAL_TILE_ID);
            if (defaultFocused != null) {
                appendGroupTileIfVisible(ordered, defaultFocused);
            }
        }
        appendGroupTileIfVisible(ordered, localTile);
        for (FrameLayout tile : remoteTiles.values()) {
            appendGroupTileIfVisible(ordered, tile);
        }
        return ordered;
    }

    private void appendGroupTileIfVisible(List<View> ordered, View child) {
        if (child != null && child.getVisibility() == View.VISIBLE && !ordered.contains(child)) {
            ordered.add(child);
        }
    }

    private View resolveGroupTile(String identity) {
        if (TextUtils.equals(identity, LOCAL_TILE_ID)) {
            return localTile;
        }
        return remoteTiles.get(identity);
    }

    private void focusGroupTile(String identity) {
        if (TextUtils.equals(selectedGroupTileId, identity)) {
            return;
        }
        selectedGroupTileId = identity;
        uiHandler.post(this::updateGroupGridLayout);
    }

    private FrameLayout buildVideoPlaceholder() {
        FrameLayout placeholder = new FrameLayout(this);
        placeholder.setBackground(roundedDrawable(Color.rgb(22, 26, 33), dp(10)));
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams contentLp = new FrameLayout.LayoutParams(-1, -1);
        placeholder.addView(content, contentLp);

        TextView avatar = new TextView(this);
        avatar.setTag("avatar");
        avatar.setGravity(Gravity.CENTER);
        avatar.setTextColor(Color.WHITE);
        avatar.setTextSize(22);
        avatar.setTypeface(Typeface.DEFAULT_BOLD);
        avatar.setBackground(circleDrawable(Color.argb(230, 49, 79, 70)));
        content.addView(avatar, new LinearLayout.LayoutParams(dp(56), dp(56)));

        TextView title = new TextView(this);
        title.setTag("title");
        title.setTextColor(Color.parseColor("#C9D0D9"));
        title.setTextSize(12);
        title.setGravity(Gravity.CENTER);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-2, -2);
        titleLp.topMargin = dp(10);
        content.addView(title, titleLp);
        return placeholder;
    }

    private TextView buildOverlayNameLabel() {
        TextView label = new TextView(this);
        label.setTextColor(Color.WHITE);
        label.setTextSize(12);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setGravity(Gravity.CENTER);
        label.setPadding(dp(6), dp(2), dp(6), dp(2));
        label.setBackground(roundedDrawable(Color.argb(120, 7, 11, 19), dp(10)));
        return label;
    }

    private void updateTileLabel(FrameLayout tile, String name) {
        if (tile == null) {
            return;
        }
        View view = tile.findViewWithTag("nameLabel");
        if (view instanceof TextView) {
            ((TextView) view).setText(name);
        }
    }

    private void updateTilePlaceholder(FrameLayout tile, String name, boolean showPlaceholder) {
        if (tile == null) {
            return;
        }
        View placeholderView = tile.findViewWithTag("placeholder");
        if (placeholderView != null) {
            placeholderView.setVisibility(showPlaceholder ? View.VISIBLE : View.GONE);
            if (placeholderView instanceof FrameLayout) {
                View avatarView = ((FrameLayout) placeholderView).findViewWithTag("avatar");
                View titleView = ((FrameLayout) placeholderView).findViewWithTag("title");
                if (avatarView instanceof TextView) {
                    ((TextView) avatarView).setText(TextUtils.isEmpty(name) ? "" : name.substring(0, 1));
                }
                if (titleView instanceof TextView) {
                    ((TextView) titleView).setText(showPlaceholder ? "未开启摄像头" : "");
                }
            }
        }
        View labelView = tile.findViewWithTag("nameLabel");
        if (labelView != null) {
            labelView.setVisibility(View.VISIBLE);
        }
        View statusView = tile.findViewWithTag("statusLabel");
        if (statusView instanceof TextView) {
            ((TextView) statusView).setText(showPlaceholder ? "未开启摄像头" : "已开启摄像头");
        }
    }

    private void updatePrivateRemotePlaceholder(String name, boolean showPlaceholder) {
        if (privateRemotePlaceholder != null) {
            privateRemotePlaceholder.setVisibility(View.GONE);
        }
        if (privateRemoteNameTv != null) {
            privateRemoteNameTv.setVisibility(View.GONE);
        }
    }

    private void updatePrivateVideoLayout() {
        if (privateRemoteContainer == null || privateLocalContainer == null) {
            return;
        }
        int smallWidth = dp(104);
        int smallHeight = dp(148);
        int smallRight = dp(16);
        int smallTop = dp(56);

        FrameLayout.LayoutParams remoteLp;
        FrameLayout.LayoutParams localLp;
        if (privateLocalPrimary) {
            remoteLp = new FrameLayout.LayoutParams(smallWidth, smallHeight);
            remoteLp.gravity = Gravity.RIGHT | Gravity.TOP;
            remoteLp.rightMargin = smallRight;
            remoteLp.topMargin = smallTop;

            localLp = new FrameLayout.LayoutParams(-1, -1);
        } else {
            remoteLp = new FrameLayout.LayoutParams(-1, -1);

            localLp = new FrameLayout.LayoutParams(smallWidth, smallHeight);
            localLp.gravity = Gravity.RIGHT | Gravity.TOP;
            localLp.rightMargin = smallRight;
            localLp.topMargin = smallTop;
        }
        privateRemoteContainer.setLayoutParams(remoteLp);
        privateLocalContainer.setLayoutParams(localLp);
        mediaContainer.removeView(privateRemoteContainer);
        mediaContainer.removeView(privateLocalContainer);
        if (privateLocalPrimary) {
            mediaContainer.addView(privateLocalContainer);
            mediaContainer.addView(privateRemoteContainer);
        } else {
            mediaContainer.addView(privateRemoteContainer);
            mediaContainer.addView(privateLocalContainer);
        }
    }

    private void togglePrivatePrimaryVideo() {
        if (isGroupCall() || callType != 1) {
            return;
        }
        privateLocalPrimary = !privateLocalPrimary;
        updatePrivateVideoLayout();
    }

    private String resolveParticipantName(String identity, String fallback) {
        String loginUid = WKConfig.getInstance().getUid();
        List<String> candidates = buildParticipantCandidates(identity, fallback);
        for (String candidate : candidates) {
            if (!TextUtils.isEmpty(candidate) && TextUtils.equals(candidate, loginUid)) {
                return getString(R.string.wkrtc_me);
            }
        }
        if (channel != null && channel.channelType == WKChannelType.PERSONAL) {
            WKChannel targetChannel = WKIM.getInstance().getChannelManager().getChannel(channel.channelID, WKChannelType.PERSONAL);
            if (targetChannel != null && !TextUtils.isEmpty(targetChannel.channelName)) {
                return targetChannel.channelName;
            }
            RtcSession session = RtcManager.getInstance().currentSession();
            if (session != null && !TextUtils.isEmpty(session.fromName)) {
                return session.fromName;
            }
        }
        if (channel != null && channel.channelType == WKChannelType.GROUP && !TextUtils.isEmpty(channel.channelID)) {
            String groupMemberName = resolveGroupMemberName(channel.channelID, candidates);
            if (!TextUtils.isEmpty(groupMemberName)) {
                return groupMemberName;
            }
        }
        for (String candidate : candidates) {
            WKChannel user = TextUtils.isEmpty(candidate) ? null : WKIM.getInstance().getChannelManager().getChannel(candidate, WKChannelType.PERSONAL);
            if (user != null) {
                if (!TextUtils.isEmpty(user.channelName)) return user.channelName;
                if (!TextUtils.isEmpty(user.channelRemark)) return user.channelRemark;
            }
        }
        RtcSession session = RtcManager.getInstance().currentSession();
        if (session != null) {
            if (!TextUtils.isEmpty(session.fromUID) && candidates.contains(session.fromUID) && !TextUtils.isEmpty(session.fromName)) {
                return session.fromName;
            }
            if (session.inviteUIDs != null && session.inviteNames != null) {
                for (int i = 0; i < session.inviteUIDs.size(); i++) {
                    String inviteUid = session.inviteUIDs.get(i);
                    if (!TextUtils.isEmpty(inviteUid) && candidates.contains(inviteUid)) {
                        if (session.inviteNames.size() > i && !TextUtils.isEmpty(session.inviteNames.get(i))) {
                            return session.inviteNames.get(i);
                        }
                    }
                }
            }
        }
        if (!TextUtils.isEmpty(fallback) && !looksLikeRawIdentity(fallback)) {
            return fallback;
        }
        if (!TextUtils.isEmpty(identity) && !looksLikeRawIdentity(identity)) {
            return identity;
        }
        if (!TextUtils.isEmpty(fallback)) {
            return "";
        }
        if (!TextUtils.isEmpty(identity)) {
            return "";
        }
        return "";
    }

    private List<String> buildParticipantCandidates(String identity, String fallback) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        addCandidate(candidates, identity);
        addCandidate(candidates, fallback);
        for (String value : new ArrayList<>(candidates)) {
            if (TextUtils.isEmpty(value)) {
                continue;
            }
            String normalized = value.trim();
            addSplitCandidates(candidates, normalized, "@");
            addSplitCandidates(candidates, normalized, ":");
            addSplitCandidates(candidates, normalized, "\\|");
            addSplitCandidates(candidates, normalized, "/");
            addSplitCandidates(candidates, normalized, "#");
            addSplitCandidates(candidates, normalized, ",");
        }
        return new ArrayList<>(candidates);
    }

    private void addSplitCandidates(LinkedHashSet<String> candidates, String value, String regex) {
        String[] parts = value.split(regex);
        if (parts.length <= 1) {
            return;
        }
        for (String part : parts) {
            addCandidate(candidates, part);
        }
    }

    private void addCandidate(LinkedHashSet<String> candidates, String value) {
        if (TextUtils.isEmpty(value)) {
            return;
        }
        String trimmed = value.trim();
        if (!TextUtils.isEmpty(trimmed)) {
            candidates.add(trimmed);
        }
    }

    private String resolveGroupMemberName(String groupId, List<String> candidates) {
        for (String candidate : candidates) {
            if (TextUtils.isEmpty(candidate)) {
                continue;
            }
            WKChannelMember member = WKIM.getInstance().getChannelMembersManager().getMember(groupId, WKChannelType.GROUP, candidate);
            if (member != null) {
                if (!TextUtils.isEmpty(member.memberName)) return member.memberName;
                if (!TextUtils.isEmpty(member.memberRemark)) return member.memberRemark;
            }
        }
        List<WKChannelMember> members = WKIM.getInstance().getChannelMembersManager().getMembers(groupId, WKChannelType.GROUP);
        if (members == null) {
            return "";
        }
        for (WKChannelMember member : members) {
            if (member == null || TextUtils.isEmpty(member.memberUID)) {
                continue;
            }
            for (String candidate : candidates) {
                if (TextUtils.isEmpty(candidate)) {
                    continue;
                }
                if (candidate.contains(member.memberUID) || member.memberUID.contains(candidate)) {
                    if (!TextUtils.isEmpty(member.memberName)) return member.memberName;
                    if (!TextUtils.isEmpty(member.memberRemark)) return member.memberRemark;
                }
            }
        }
        return "";
    }

    private boolean looksLikeRawIdentity(String value) {
        if (TextUtils.isEmpty(value)) {
            return false;
        }
        if (value.matches(".*[\\u4e00-\\u9fa5].*")) {
            return false;
        }
        return value.matches("[A-Za-z0-9_:@#\\-/,\\.]{6,}");
    }

    private String getStringLabel(String value) {
        return value;
    }

    private void renderSession(RtcSession session) {
        if (endScreenPending && session == null) {
            return;
        }
        String name = displayName(session);
        titleTv.setText(name);
        avatarTv.setText(TextUtils.isEmpty(name) ? "" : name.substring(0, 1));
        if (isGroupCall()) {
            updateGroupCallChrome(session);
        } else {
            updateSingleCallChrome(session);
        }
        if (session == null) {
            endScreenPending = false;
            endToastShown = false;
            uiHandler.removeCallbacks(endScreenFinishRunnable);
            if (isGroupCall()) {
                statusTv.setText(groupStatusText(null));
            } else {
                statusTv.setText(callType == 1 ? R.string.wkrtc_video_call : R.string.wkrtc_audio_call);
            }
            renderOutgoingControls(false);
            return;
        }
        if (!RtcSession.ENDED.equals(session.status)) {
            clearPendingEndScreen();
        }
        if (RtcSession.INCOMING.equals(session.status)) {
            stopDurationTicker();
            if (isGroupCall()) {
                statusTv.setText(groupStatusText(session));
            } else {
                statusTv.setText(callType == 1 ? R.string.wkrtc_incoming_video : R.string.wkrtc_incoming_audio);
            }
            renderIncomingControls();
        } else if (RtcSession.JOINING.equals(session.status)) {
            stopDurationTicker();
            statusTv.setText(isGroupCall() ? groupStatusText(session) : getString(R.string.wkrtc_connecting));
            renderInCallControls();
        } else if (RtcSession.IN_CALL.equals(session.status)) {
            if (session.connectedAt == 0L) {
                session.connectedAt = System.currentTimeMillis();
            }
            inCallStartedAt = session.connectedAt;
            if (isGroupCall() && callType == 0) {
                startDurationTicker();
                statusTv.setText(groupStatusText(session));
            } else if (!isVideoUi(session) && !isGroupCall()) {
                startDurationTicker();
                statusTv.setText(formatDuration());
            } else {
                stopDurationTicker();
                statusTv.setText(isGroupCall() ? groupStatusText(session) : getString(R.string.wkrtc_in_call));
            }
            renderInCallControls();
        } else if (RtcSession.ENDED.equals(session.status)) {
            stopDurationTicker();
            statusTv.setText(R.string.wkrtc_call_ended);
            controlsLayout.removeAllViews();
            endScreenPending = true;
            if (!endToastShown) {
                endToastShown = true;
                WKToastUtils.getInstance().showToastNormal(getString(R.string.wkrtc_call_ended));
            }
            uiHandler.removeCallbacks(endScreenFinishRunnable);
            uiHandler.postDelayed(endScreenFinishRunnable, 900L);
        } else {
            stopDurationTicker();
            statusTv.setText(isGroupCall() ? groupStatusText(session) : getString(callType == 1 ? R.string.wkrtc_waiting_accept : R.string.wkrtc_calling));
            renderOutgoingControls(!TextUtils.isEmpty(session.callId));
        }
    }

    private void clearPendingEndScreen() {
        endScreenPending = false;
        endToastShown = false;
        uiHandler.removeCallbacks(endScreenFinishRunnable);
    }

    private void renderCallHeader(boolean show) {
        int visibility = show ? View.VISIBLE : View.GONE;
        if (headerLayout != null) {
            headerLayout.setVisibility(visibility);
        }
    }

    private String displayName(RtcSession session) {
        if (session != null) {
            List<String> inviteDisplayNames = resolveInviteDisplayNames(session);
            if (!inviteDisplayNames.isEmpty()) {
                return TextUtils.join("、", inviteDisplayNames);
            }
            if (!TextUtils.isEmpty(session.fromUID)) {
                String resolved = resolveParticipantName(session.fromUID, session.fromName);
                if (!TextUtils.isEmpty(resolved)) {
                    return resolved;
                }
            }
            if (!TextUtils.isEmpty(session.fromName)) {
                return session.fromName;
            }
        }
        if (channel == null) {
            return "";
        }
        WKChannel localChannel = WKIM.getInstance().getChannelManager().getChannel(channel.channelID, channel.channelType);
        if (localChannel != null) {
            if (!TextUtils.isEmpty(localChannel.channelName)) return localChannel.channelName;
            if (!TextUtils.isEmpty(localChannel.channelRemark)) return localChannel.channelRemark;
        }
        return channel.channelID;
    }

    private List<String> resolveInviteDisplayNames(RtcSession session) {
        List<String> names = new ArrayList<>();
        if (session == null) {
            return names;
        }
        if (session.inviteUIDs != null && !session.inviteUIDs.isEmpty()) {
            for (int i = 0; i < session.inviteUIDs.size(); i++) {
                String uid = session.inviteUIDs.get(i);
                String fallback = null;
                if (session.inviteNames != null && session.inviteNames.size() > i) {
                    fallback = session.inviteNames.get(i);
                }
                String resolved = resolveParticipantName(uid, fallback);
                if (!TextUtils.isEmpty(resolved) && !names.contains(resolved)) {
                    names.add(resolved);
                }
            }
        }
        if (!names.isEmpty()) {
            return names;
        }
        if (session.inviteNames != null) {
            for (String inviteName : session.inviteNames) {
                if (!TextUtils.isEmpty(inviteName) && !names.contains(inviteName)) {
                    names.add(inviteName);
                }
            }
        }
        return names;
    }

    private void renderIncomingControls() {
        controlsLayout.removeAllViews();
        if (callType == 1) {
            controlsLayout.addView(buildActionRow(
                    actionButton(R.drawable.wkrtc_ic_hangup, getString(R.string.wkrtc_reject), R.color.wkrtc_red, true, v -> hangup()),
                    actionButton(R.drawable.wkrtc_ic_message, getString(R.string.wkrtc_message), R.color.wkrtc_button, false, v -> openChatFromCall(true)),
                    actionButton(R.drawable.wkrtc_ic_phone_answer, getString(R.string.wkrtc_answer), R.color.wkrtc_green, true, v -> acceptIncoming())
            ));
            return;
        }
        if (isGroupCall()) {
            controlsLayout.addView(buildActionRow(
                    actionButton(currentAudioRouteIcon(), currentAudioRouteLabel(), R.color.wkrtc_button, false, v -> toggleSpeaker()),
                    actionButton(R.drawable.wkrtc_ic_message, "成员", R.color.wkrtc_button, false, v -> showParticipantsSheet()),
                    actionButton(R.drawable.wkrtc_ic_message, getString(R.string.wkrtc_message), R.color.wkrtc_button, false, v -> openChatFromCall(true))
            ));
            addControlColumnGap(dp(28));
            controlsLayout.addView(buildActionRow(
                    actionButton(R.drawable.wkrtc_ic_hangup, getString(R.string.wkrtc_reject), R.color.wkrtc_red, true, v -> hangup()),
                    actionButton(R.drawable.wkrtc_ic_phone_answer, getString(R.string.wkrtc_answer), R.color.wkrtc_green, true, v -> acceptIncoming())
            ));
            return;
        }
        controlsLayout.addView(buildActionRow(
                actionButton(currentAudioRouteIcon(), currentAudioRouteLabel(), R.color.wkrtc_button, false, v -> toggleSpeaker()),
                actionButton(R.drawable.wkrtc_ic_video, getString(R.string.wkrtc_video), R.color.wkrtc_button, false, v -> acceptIncomingAsVideo()),
                actionButton(R.drawable.wkrtc_ic_message, getString(R.string.wkrtc_message), R.color.wkrtc_button, false, v -> openChatFromCall(true))
        ));
        addControlColumnGap(dp(28));
        controlsLayout.addView(buildActionRow(
                actionButton(R.drawable.wkrtc_ic_hangup, getString(R.string.wkrtc_reject), R.color.wkrtc_red, true, v -> hangup()),
                actionButton(R.drawable.wkrtc_ic_phone_answer, getString(R.string.wkrtc_answer), R.color.wkrtc_green, true, v -> acceptIncoming())
        ));
    }

    private void renderOutgoingControls(boolean hasCallId) {
        controlsLayout.removeAllViews();
        if (isGroupCall() && callType == 0) {
            controlsLayout.addView(buildActionRow(
                    actionButton(R.drawable.wkrtc_ic_mic, getString(R.string.wkrtc_mic), R.color.wkrtc_button, false, v -> toggleMicrophone()),
                    actionButton(currentAudioRouteIcon(), currentAudioRouteLabel(), R.color.wkrtc_button, false, v -> toggleSpeaker()),
                    actionButton(R.drawable.wkrtc_ic_message, "成员", R.color.wkrtc_button, false, v -> showParticipantsSheet())
            ));
            addControlColumnGap(dp(32));
            controlsLayout.addView(actionButton(R.drawable.wkrtc_ic_hangup, getString(R.string.wkrtc_hangup), R.color.wkrtc_red, true, v -> hangup()));
            return;
        }
        if (callType == 1) {
            controlsLayout.addView(buildActionRow(
                    actionButton(R.drawable.wkrtc_ic_mic, getString(R.string.wkrtc_mic), R.color.wkrtc_button, false, v -> toggleMicrophone()),
                    actionButton(currentAudioRouteIcon(), currentAudioRouteLabel(), R.color.wkrtc_button, false, v -> toggleSpeaker()),
                    actionButton(R.drawable.wkrtc_ic_video_off, getString(R.string.wkrtc_close_video), isLocalCameraOn() ? R.color.wkrtc_button : R.color.wkrtc_red, false, v -> toggleCamera())
            ));
            addControlColumnGap(dp(26));
        } else {
            controlsLayout.addView(buildActionRow(
                    actionButton(R.drawable.wkrtc_ic_mic, getString(R.string.wkrtc_mic), R.color.wkrtc_button, false, v -> toggleMicrophone()),
                    actionButton(currentAudioRouteIcon(), currentAudioRouteLabel(), R.color.wkrtc_button, false, v -> toggleSpeaker()),
                    actionButton(R.drawable.wkrtc_ic_video_off, getString(R.string.wkrtc_close_video), isLocalCameraOn() ? R.color.wkrtc_button : R.color.wkrtc_red, false, v -> toggleCamera())
            ));
            addControlColumnGap(dp(32));
        }
        controlsLayout.addView(actionButton(R.drawable.wkrtc_ic_hangup, getString(R.string.wkrtc_hangup), R.color.wkrtc_red, true, v -> hangup()));
    }

    private void renderInCallControls() {
        controlsLayout.removeAllViews();
        RtcSession session = RtcManager.getInstance().currentSession();
        boolean micEnabled = session == null || session.localMicrophoneEnabled;
        boolean cameraEnabled = session == null || session.localCameraEnabled;
        boolean upgradedFromAudio = initialCallType == 0 && cameraEnabled;
        if (isGroupCall()) {
            if (callType == 1) {
                controlsLayout.addView(buildActionRow(
                        actionButton(R.drawable.wkrtc_ic_flip, getString(R.string.wkrtc_flip), cameraEnabled ? R.color.wkrtc_button : R.color.wkrtc_red, false, v -> switchCamera()),
                        actionButton(R.drawable.wkrtc_ic_mic, getString(R.string.wkrtc_mic), micEnabled ? R.color.wkrtc_button : R.color.wkrtc_red, false, v -> toggleMicrophone()),
                        actionButton(R.drawable.wkrtc_ic_video_off, getString(R.string.wkrtc_video), cameraEnabled ? R.color.wkrtc_button : R.color.wkrtc_red, false, v -> toggleCamera()),
                        actionButton(currentAudioRouteIcon(), currentAudioRouteLabel(), R.color.wkrtc_button, false, v -> toggleSpeaker())
                ));
            } else {
                controlsLayout.addView(buildActionRow(
                        actionButton(R.drawable.wkrtc_ic_mic, getString(R.string.wkrtc_mic), micEnabled ? R.color.wkrtc_button : R.color.wkrtc_red, false, v -> toggleMicrophone()),
                        actionButton(currentAudioRouteIcon(), currentAudioRouteLabel(), R.color.wkrtc_button, false, v -> toggleSpeaker()),
                        actionButton(R.drawable.wkrtc_ic_message, "成员", R.color.wkrtc_button, false, v -> showParticipantsSheet())
                ));
            }
            addControlColumnGap(dp(26));
            controlsLayout.addView(actionButton(R.drawable.wkrtc_ic_hangup, "结束通话", R.color.wkrtc_red, true, v -> hangup()));
            return;
        }
        LinearLayout utilityRow = new LinearLayout(this);
        utilityRow.setOrientation(LinearLayout.HORIZONTAL);
        utilityRow.setGravity(Gravity.CENTER);
        utilityRow.addView(actionButton(
                R.drawable.wkrtc_ic_mic,
                getString(R.string.wkrtc_mic),
                micEnabled ? R.color.wkrtc_button : R.color.wkrtc_red,
                false,
                v -> toggleMicrophone()
        ));
        if (callType == 1 && !upgradedFromAudio) {
            addControlGap(utilityRow);
            utilityRow.addView(actionButton(
                    R.drawable.wkrtc_ic_flip,
                    getString(R.string.wkrtc_flip),
                    cameraEnabled ? R.color.wkrtc_button : R.color.wkrtc_red,
                    false,
                    v -> switchCamera()
            ));
            addControlGap(utilityRow);
            utilityRow.addView(actionButton(R.drawable.wkrtc_ic_video_off, getString(R.string.wkrtc_close_video), cameraEnabled ? R.color.wkrtc_button : R.color.wkrtc_red, false, v -> toggleCamera()));
            addControlGap(utilityRow);
            utilityRow.addView(actionButton(currentAudioRouteIcon(), currentAudioRouteLabel(), R.color.wkrtc_button, false, v -> toggleSpeaker()));
        } else {
            addControlGap(utilityRow);
            utilityRow.addView(actionButton(currentAudioRouteIcon(), currentAudioRouteLabel(), R.color.wkrtc_button, false, v -> toggleSpeaker()));
            addControlGap(utilityRow);
            utilityRow.addView(actionButton(R.drawable.wkrtc_ic_video_off, getString(R.string.wkrtc_close_video), isLocalCameraOn() ? R.color.wkrtc_button : R.color.wkrtc_red, false, v -> toggleCamera()));
        }
        controlsLayout.addView(utilityRow);
        addControlColumnGap(dp(26));
        controlsLayout.addView(actionButton(R.drawable.wkrtc_ic_hangup, getString(R.string.wkrtc_hangup), R.color.wkrtc_red, true, v -> hangup()));
    }

    private void toggleMicrophone() {
        RtcSession session = RtcManager.getInstance().currentSession();
        if (session == null) {
            return;
        }
        session.localMicrophoneEnabled = !session.localMicrophoneEnabled;
        RtcLiveKitClient.setMicrophoneEnabled(session.localMicrophoneEnabled);
        renderInCallControls();
    }

    private void toggleSpeaker() {
        speakerEnabled = !speakerEnabled;
        RtcAudioHelper.setSpeakerEnabled(this, speakerEnabled);
        RtcSession session = RtcManager.getInstance().currentSession();
        if (session == null || RtcSession.OUTGOING.equals(session.status)) {
            renderOutgoingControls(session != null && !TextUtils.isEmpty(session.callId));
        } else if (RtcSession.INCOMING.equals(session.status)) {
            renderIncomingControls();
        } else {
            renderInCallControls();
        }
    }

    private int currentAudioRouteIcon() {
        return speakerEnabled ? R.drawable.wkrtc_ic_speaker : R.drawable.wkrtc_ic_earpiece;
    }

    private String currentAudioRouteLabel() {
        return speakerEnabled ? getString(R.string.wkrtc_speaker) : "听筒";
    }

    private void acceptIncomingAsVideo() {
        RtcSession session = RtcManager.getInstance().currentSession();
        if (session != null) {
            session.localCameraEnabled = true;
        }
        callType = 1;
        speakerEnabled = true;
        updateSingleCallChrome(session);
        acceptIncoming();
    }

    private void toggleCamera() {
        RtcSession session = RtcManager.getInstance().currentSession();
        if (session == null) {
            return;
        }
        session.localCameraEnabled = !session.localCameraEnabled;
        if (session.localCameraEnabled) {
            speakerEnabled = true;
            callType = 1;
        } else if (initialCallType == 0) {
            callType = 0;
        }
        RtcLiveKitClient.setCameraEnabled(session.localCameraEnabled, liveKitCallback);
        if (groupLocalRenderer != null) {
            groupLocalRenderer.setVisibility(session.localCameraEnabled ? View.VISIBLE : View.INVISIBLE);
        }
        if (localRenderer != null) {
            localRenderer.setVisibility(session.localCameraEnabled ? View.VISIBLE : View.INVISIBLE);
        }
        if (initialCallType == 0 && remoteRenderer != null && !session.localCameraEnabled) {
            remoteRenderer.clearImage();
            remoteRenderer.setVisibility(View.INVISIBLE);
        }
        if (session.localCameraEnabled && remoteRenderer != null && remoteVideoTrack != null) {
            remoteVideoTrack.removeRenderer(remoteRenderer);
            remoteVideoTrack.addRenderer(remoteRenderer);
            remoteRenderer.setVisibility(View.VISIBLE);
        }
        updateLocalPreviewVisibility();
        if (isGroupCall()) {
            updateGroupCallChrome(session);
            updateGroupGridLayout();
        } else {
            updateSingleCallChrome(session);
        }
        renderInCallControls();
    }

    private void switchCamera() {
        RtcSession session = RtcManager.getInstance().currentSession();
        if (session == null || callType != 1 || !session.localCameraEnabled) {
            return;
        }
        RtcLiveKitClient.switchCamera(liveKitCallback);
    }

    private LinearLayout actionButton(int iconRes, String text, int colorRes, boolean primary, View.OnClickListener listener) {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setGravity(Gravity.CENTER_HORIZONTAL);
        wrapper.setOnClickListener(listener);

        int buttonSize = primary ? dp(80) : dp(56);
        FrameLayout iconBg = new FrameLayout(this);
        iconBg.setBackground(circleDrawable(ContextCompat.getColor(this, colorRes)));
        LinearLayout.LayoutParams iconBgLp = new LinearLayout.LayoutParams(buttonSize, buttonSize);
        wrapper.addView(iconBg, iconBgLp);

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(Color.WHITE);
        FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams(primary ? dp(28) : dp(24), primary ? dp(28) : dp(24));
        iconLp.gravity = Gravity.CENTER;
        iconBg.addView(icon, iconLp);

        TextView label = new TextView(this);
        label.setText(text);
        label.setTextColor(Color.WHITE);
        label.setTextSize(13);
        label.setGravity(Gravity.CENTER);
        label.setSingleLine(false);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(-2, -2);
        labelLp.topMargin = dp(10);
        wrapper.addView(label, labelLp);

        return wrapper;
    }

    private LinearLayout buildActionRow(View... actions) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        for (int i = 0; i < actions.length; i++) {
            row.addView(actions[i]);
            if (i < actions.length - 1) {
                addControlGap(row);
            }
        }
        return row;
    }

    private void addControlGap(LinearLayout row) {
        View gap = new View(this);
        row.addView(gap, new LinearLayout.LayoutParams(dp(30), 1));
    }

    private void addControlColumnGap(int height) {
        View gap = new View(this);
        controlsLayout.addView(gap, new LinearLayout.LayoutParams(1, height));
    }

    private boolean hasMediaPermissions() {
        for (String permission : permissionsForCall()) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private String[] permissionsForCall() {
        if (callType == 1) {
            return new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA};
        }
        return new String[]{Manifest.permission.RECORD_AUDIO};
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_MEDIA_PERMISSION) {
            return;
        }
        if (!hasMediaPermissions()) {
            WKToastUtils.getInstance().showToastNormal(getString(callType == 1 ? R.string.wkrtc_permission_video : R.string.wkrtc_permission_audio));
            finish();
            return;
        }
        if (DIRECTION_INCOMING.equals(direction)) {
            acceptIncoming();
        } else if (DIRECTION_JOIN.equals(direction)) {
            joinCurrentCallAfterPermission();
        } else {
            startOutgoingAfterPermission();
        }
    }

    private GradientDrawable circleDrawable(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        return drawable;
    }

    private GradientDrawable roundedDrawable(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private GradientDrawable backgroundDrawable() {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{
                        Color.parseColor("#0A0F17"),
                        Color.parseColor("#131A24"),
                        Color.parseColor("#1C1218")
                }
        );
        return drawable;
    }

    private void applySingleCallVisualState(RtcSession session) {
        if (isGroupCall()) {
            return;
        }
        avatarTv.setBackground(circleDrawable(callType == 1 ? Color.argb(235, 52, 57, 67) : Color.argb(235, 31, 44, 39)));
        titleTv.setTextSize(callType == 1 ? 24 : 28);
        statusTv.setTextSize(callType == 1 ? 14 : 16);
        statusTv.setBackground(null);
        statusTv.setTextColor(Color.parseColor("#D0D4DB"));

        if (session == null) {
            statusTv.setTextColor(callType == 1 ? Color.parseColor("#1ED760") : Color.parseColor("#1ED760"));
            return;
        }

        if (callType == 1) {
            if (RtcSession.INCOMING.equals(session.status)) {
                statusTv.setTextColor(Color.parseColor("#C7CBD1"));
            } else if (RtcSession.OUTGOING.equals(session.status) || RtcSession.JOINING.equals(session.status)) {
                statusTv.setTextColor(Color.parseColor("#1ED760"));
            } else if (RtcSession.IN_CALL.equals(session.status)) {
                statusTv.setTextColor(Color.WHITE);
            }
        } else {
            if (RtcSession.INCOMING.equals(session.status)) {
                statusTv.setTextColor(Color.parseColor("#D6D8DB"));
            } else if (RtcSession.OUTGOING.equals(session.status)) {
                statusTv.setTextColor(Color.parseColor("#1ED760"));
            } else if (RtcSession.IN_CALL.equals(session.status)) {
                statusTv.setTextColor(Color.WHITE);
            }
        }
    }

    private void startDurationTicker() {
        uiHandler.removeCallbacks(durationTicker);
        uiHandler.post(durationTicker);
    }

    private void stopDurationTicker() {
        inCallStartedAt = 0L;
        uiHandler.removeCallbacks(durationTicker);
    }

    private String formatDuration() {
        if (inCallStartedAt <= 0L) {
            return "00:00";
        }
        long elapsed = Math.max(0L, (System.currentTimeMillis() - inCallStartedAt) / 1000L);
        long minutes = elapsed / 60L;
        long seconds = elapsed % 60L;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private void showPlaceholderToast() {
        WKToastUtils.getInstance().showToastNormal(getString(R.string.wkrtc_feature_coming_soon));
    }

    private void showDialpadDialog() {
        if (keypadDialog != null && keypadDialog.isShowing()) {
            keypadDialog.dismiss();
            return;
        }
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(24), dp(24), dp(24));
        root.setBackground(roundedDrawable(Color.parseColor("#1C2027"), dp(24)));

        TextView title = new TextView(this);
        title.setText(getString(R.string.wkrtc_keypad));
        title.setTextColor(Color.WHITE);
        title.setTextSize(18);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(3);
        LinearLayout.LayoutParams gridLp = new LinearLayout.LayoutParams(-1, -2);
        gridLp.topMargin = dp(18);
        root.addView(grid, gridLp);

        String[] items = {"1","2","3","4","5","6","7","8","9","*","0","#"};
        for (String item : items) {
            TextView key = new TextView(this);
            key.setText(item);
            key.setTextColor(Color.WHITE);
            key.setTextSize(26);
            key.setGravity(Gravity.CENTER);
            key.setBackground(roundedDrawable(Color.parseColor("#2B3038"), dp(18)));
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = 0;
            lp.height = dp(64);
            lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            lp.setMargins(dp(6), dp(6), dp(6), dp(6));
            key.setLayoutParams(lp);
            grid.addView(key);
        }

        keypadDialog = new AlertDialog.Builder(this)
                .setView(root)
                .create();
        if (keypadDialog.getWindow() != null) {
            keypadDialog.getWindow().setBackgroundDrawable(roundedDrawable(Color.TRANSPARENT, dp(24)));
        }
        keypadDialog.show();
    }

    private void showParticipantsSheet() {
        if (!isGroupCall()) {
            return;
        }
        if (participantsDialog != null && participantsDialog.isShowing()) {
            participantsDialog.dismiss();
        }
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackground(roundedDrawable(Color.parseColor("#171C23"), dp(24)));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(18), dp(20), dp(24));
        scrollView.addView(root, new ScrollView.LayoutParams(-1, -2));

        TextView title = new TextView(this);
        title.setText("当前成员 " + groupParticipantCount(RtcManager.getInstance().currentSession()) + " 人");
        title.setTextColor(Color.WHITE);
        title.setTextSize(18);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        List<String> memberNames = resolveGroupParticipantNames();
        for (String memberName : memberNames) {
            root.addView(buildParticipantRow(memberName));
        }

        participantsDialog = new AlertDialog.Builder(this).setView(scrollView).create();
        participantsDialog.show();
        Window dialogWindow = participantsDialog.getWindow();
        if (dialogWindow != null) {
            dialogWindow.setBackgroundDrawable(roundedDrawable(Color.TRANSPARENT, dp(24)));
            dialogWindow.setGravity(Gravity.BOTTOM);
            dialogWindow.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }

    private void addTopActionButtons(FrameLayout root) {
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams topBarLp = new FrameLayout.LayoutParams(-1, -2);
        topBarLp.gravity = Gravity.TOP;
        topBarLp.topMargin = dp(18);
        topBarLp.leftMargin = dp(16);
        topBarLp.rightMargin = dp(16);
        root.addView(topBar, topBarLp);

        topBar.addView(buildTopIconButton(R.drawable.wkrtc_ic_minimize, v -> minimizeToChat()));
        View spacer = new View(this);
        topBar.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        if (isGroupCall()) {
            topBar.addView(buildTopIconButton(R.drawable.wkrtc_ic_add_member, v -> pickMembersToInvite()));
        }
    }

    private View buildTopIconButton(int resId, View.OnClickListener clickListener) {
        FrameLayout wrap = new FrameLayout(this);
        wrap.setBackground(roundedDrawable(Color.argb(108, 15, 20, 28), dp(18)));
        wrap.setPadding(dp(10), dp(10), dp(10), dp(10));
        ImageView icon = new ImageView(this);
        icon.setImageResource(resId);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        icon.setOnClickListener(clickListener);
        wrap.addView(icon, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(42), dp(42));
        wrap.setLayoutParams(lp);
        return wrap;
    }

    private void minimizeToChat() {
        if (channel == null || TextUtils.isEmpty(channel.channelID)) {
            finish();
            return;
        }
        RtcSession session = RtcManager.getInstance().currentSession();
        if (session != null) {
            session.minimized = true;
        }
        Intent intent = new Intent();
        intent.setClassName(this, "com.chat.uikit.chat.ChatActivity");
        intent.putExtra("channelId", channel.channelID);
        intent.putExtra("channelType", channel.channelType);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private void showInviteMembersSheet() {
        if (!isGroupCall()) {
            return;
        }
        if (inviteDialog != null && inviteDialog.isShowing()) {
            inviteDialog.dismiss();
        }
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(16), dp(20), dp(24));
        root.setBackground(roundedDrawable(Color.parseColor("#171C23"), dp(24)));
        root.addView(buildInviteOptionRow("邀请全体成员通话", v -> {
            if (inviteDialog != null) {
                inviteDialog.dismiss();
            }
            inviteAllRemainingMembers();
        }));
        root.addView(buildInviteOptionRow("选择成员通话", v -> {
            if (inviteDialog != null) {
                inviteDialog.dismiss();
            }
            pickMembersToInvite();
        }));
        inviteDialog = new AlertDialog.Builder(this).setView(root).create();
        inviteDialog.show();
        Window dialogWindow = inviteDialog.getWindow();
        if (dialogWindow != null) {
            dialogWindow.setBackgroundDrawable(roundedDrawable(Color.TRANSPARENT, dp(24)));
            dialogWindow.setGravity(Gravity.BOTTOM);
            dialogWindow.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }

    private View buildInviteOptionRow(String text, View.OnClickListener listener) {
        TextView option = new TextView(this);
        option.setText(text);
        option.setTextColor(Color.WHITE);
        option.setTextSize(17);
        option.setGravity(Gravity.CENTER_VERTICAL);
        option.setPadding(dp(8), dp(16), dp(8), dp(16));
        option.setOnClickListener(listener);
        return option;
    }

    private void inviteAllRemainingMembers() {
        List<WKChannelMember> members = WKIM.getInstance().getChannelMembersManager().getMembers(channel.channelID, channel.channelType);
        if (members == null || members.isEmpty()) {
            WKToastUtils.getInstance().showToastNormal("暂无可邀请成员");
            return;
        }
        ArrayList<String> uids = new ArrayList<>();
        ArrayList<String> names = new ArrayList<>();
        LinkedHashSet<String> excluded = buildExcludedInviteUids();
        String loginUid = WKConfig.getInstance().getUid();
        for (WKChannelMember member : members) {
            if (member == null || TextUtils.isEmpty(member.memberUID) || TextUtils.equals(member.memberUID, loginUid) || excluded.contains(member.memberUID)) {
                continue;
            }
            uids.add(member.memberUID);
            String displayName = !TextUtils.isEmpty(member.memberRemark) ? member.memberRemark : member.memberName;
            names.add(TextUtils.isEmpty(displayName) ? member.memberUID : displayName);
        }
        inviteMembersToCurrentCall(uids, names);
    }

    private LinkedHashSet<String> buildExcludedInviteUids() {
        LinkedHashSet<String> excluded = new LinkedHashSet<>();
        String loginUid = WKConfig.getInstance().getUid();
        if (!TextUtils.isEmpty(loginUid)) {
            excluded.add(loginUid);
        }
        RtcSession session = RtcManager.getInstance().currentSession();
        if (session != null && session.joinedUIDs != null) {
            excluded.addAll(session.joinedUIDs);
        }
        for (String uid : groupParticipantRoster.keySet()) {
            if (!TextUtils.isEmpty(uid) && !uid.startsWith("invite_")) {
                excluded.add(uid);
            }
        }
        return excluded;
    }

    private void pickMembersToInvite() {
        pauseCameraForInvitePicker();
        Intent intent = new Intent();
        intent.setClassName(this, "com.chat.uikit.group.ChooseVideoCallMembersActivity");
        intent.putExtra("channelID", channel.channelID);
        intent.putExtra("channelType", channel.channelType);
        intent.putExtra("isCreate", false);
        intent.putExtra("callType", callType);
        intent.putStringArrayListExtra("excludeUIDs", new ArrayList<>(buildExcludedInviteUids()));
        startActivityForResult(intent, REQUEST_PICK_INVITE_MEMBERS);
    }

    private void pauseCameraForInvitePicker() {
        RtcSession session = RtcManager.getInstance().currentSession();
        if (session == null || callType != 1 || !session.localCameraEnabled) {
            return;
        }
        cameraPausedForInvitePicker = true;
        session.localCameraEnabled = false;
        RtcLiveKitClient.setCameraEnabled(false, liveKitCallback);
        applyLocalCameraVisibility(false);
        updateLocalPreviewVisibility();
        if (isGroupCall()) {
            updateGroupCallChrome(session);
            updateGroupGridLayout();
        } else {
            updateSingleCallChrome(session);
        }
        renderInCallControls();
    }

    private void restoreCameraAfterInvitePicker() {
        if (!cameraPausedForInvitePicker) {
            return;
        }
        cameraPausedForInvitePicker = false;
        if (!isActivityUsable()) {
            return;
        }
        RtcSession session = RtcManager.getInstance().currentSession();
        if (session == null || RtcSession.ENDED.equals(session.status)) {
            return;
        }
        session.localCameraEnabled = true;
        speakerEnabled = true;
        callType = 1;
        RtcLiveKitClient.setCameraEnabled(true, liveKitCallback);
        applyLocalCameraVisibility(true);
        updateLocalPreviewVisibility();
        if (isGroupCall()) {
            updateGroupCallChrome(session);
            updateGroupGridLayout();
        } else {
            updateSingleCallChrome(session);
        }
        renderInCallControls();
    }

    private void applyLocalCameraVisibility(boolean visible) {
        if (groupLocalRenderer != null) {
            groupLocalRenderer.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
        }
        if (localRenderer != null) {
            localRenderer.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
        }
    }

    private void inviteMembersToCurrentCall(List<String> uids, List<String> names) {
        RtcSession session = RtcManager.getInstance().currentSession();
        if (session == null || TextUtils.isEmpty(session.callId)) {
            Log.w(TAG, "invite members ignored because session or callId missing");
            return;
        }
        ArrayList<String> effectiveUids = new ArrayList<>();
        ArrayList<String> effectiveNames = new ArrayList<>();
        LinkedHashSet<String> excluded = buildExcludedInviteUids();
        for (int i = 0; i < uids.size(); i++) {
            String uid = uids.get(i);
            if (TextUtils.isEmpty(uid) || excluded.contains(uid)) {
                continue;
            }
            effectiveUids.add(uid);
            effectiveNames.add(names != null && i < names.size() ? names.get(i) : uid);
        }
        if (effectiveUids.isEmpty()) {
            WKToastUtils.getInstance().showToastNormal("暂无可邀请成员");
            return;
        }
        sendInviteNoticeFallback(session, effectiveUids, false);
        RtcModel.getInstance().inviteMembers(
                session.callId,
                session.channel == null ? "" : session.channel.channelID,
                session.channel == null ? channel.channelType : session.channel.channelType,
                session.callType == 1 ? "video" : "audio",
                effectiveUids,
                new IRequestResultListener<>() {
            @Override
            public void onSuccess(CommonResponse result) {
                RtcManager.getInstance().markInvitedMembers(effectiveUids, effectiveNames);
                WKToastUtils.getInstance().showToastNormal("已发出邀请");
            }

            @Override
            public void onFail(int code, String msg) {
                WKToastUtils.getInstance().showToastNormal(TextUtils.isEmpty(msg) ? "邀请失败" : msg);
            }
        });
    }

    private void sendInitialGroupNoticeFallback() {
        RtcSession session = RtcManager.getInstance().currentSession();
        if (session == null
                || session.channel == null
                || session.channel.channelType != WKChannelType.GROUP
                || !session.inviteAll) {
            return;
        }
        sendInviteNoticeFallback(session, session.inviteUIDs, true);
    }

    private void sendInviteNoticeFallback(RtcSession session, List<String> targetUIDs, boolean inviteAllNotice) {
        if (session == null
                || session.channel == null
                || session.channel.channelType != WKChannelType.GROUP
                || TextUtils.isEmpty(session.callId)
                || (!inviteAllNotice && (targetUIDs == null || targetUIDs.isEmpty()))) {
            return;
        }
        try {
            RtcNoticeContent content = new RtcNoticeContent();
            content.callId = session.callId;
            content.roomName = session.roomName;
            content.channelId = session.channel.channelID;
            content.channelType = session.channel.channelType;
            content.callType = session.callType == 1 ? "video" : "audio";
            content.fromUid = WKConfig.getInstance().getUid();
            content.fromName = currentUserDisplayName();
            content.expireAt = System.currentTimeMillis() / 1000L + 180L;
            content.targetUIDs = targetUIDs == null ? new ArrayList<>() : new ArrayList<>(targetUIDs);
            content.inviteAll = inviteAllNotice;
            WKIM.getInstance().getMsgManager().send(content, session.channel);
            Log.i(TAG, "send rtc_notice fallback for invite, callId=" + session.callId + ", targetUIDs=" + targetUIDs);
        } catch (Exception error) {
            Log.w(TAG, "send rtc_notice fallback failed", error);
        }
    }

    private String currentUserDisplayName() {
        String uid = WKConfig.getInstance().getUid();
        if (!TextUtils.isEmpty(uid)) {
            WKChannel user = WKIM.getInstance().getChannelManager().getChannel(uid, WKChannelType.PERSONAL);
            if (user != null) {
                if (!TextUtils.isEmpty(user.channelRemark)) {
                    return user.channelRemark;
                }
                if (!TextUtils.isEmpty(user.channelName)) {
                    return user.channelName;
                }
            }
        }
        return uid;
    }

    private View buildParticipantRow(String name) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(14), 0, dp(14));

        TextView avatar = new TextView(this);
        avatar.setText(shortName(name));
        avatar.setGravity(Gravity.CENTER);
        avatar.setTextColor(Color.WHITE);
        avatar.setTextSize(17);
        avatar.setTypeface(Typeface.DEFAULT_BOLD);
        avatar.setBackground(circleDrawable(Color.parseColor("#2A313D")));
        row.addView(avatar, new LinearLayout.LayoutParams(dp(42), dp(42)));

        TextView label = new TextView(this);
        label.setText(name);
        label.setTextColor(Color.WHITE);
        label.setTextSize(15);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(-1, -2);
        labelLp.leftMargin = dp(14);
        row.addView(label, labelLp);
        return row;
    }

    private boolean isLocalCameraOn() {
        RtcSession session = RtcManager.getInstance().currentSession();
        return session != null && session.localCameraEnabled;
    }

    private void updateLocalPreviewVisibility() {
        if (isGroupCall() || privateLocalContainer == null) {
            return;
        }
        RtcSession session = RtcManager.getInstance().currentSession();
        boolean showPreview = session != null && session.localCameraEnabled;
        privateLocalContainer.setVisibility(showPreview ? View.VISIBLE : View.GONE);
        if (!showPreview && localRenderer != null) {
            localRenderer.clearImage();
            localRenderer.setVisibility(View.INVISIBLE);
        }
    }

    private void openChatFromCall(boolean dismissIncoming) {
        if (channel == null || TextUtils.isEmpty(channel.channelID)) {
            return;
        }
        Intent intent = new Intent();
        intent.setClassName(this, "com.chat.uikit.chat.ChatActivity");
        intent.putExtra("channelId", channel.channelID);
        intent.putExtra("channelType", channel.channelType);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        if (dismissIncoming) {
            dismissIncomingForMessage();
        }
    }

    private void dismissIncomingForMessage() {
        RtcSession session = RtcManager.getInstance().currentSession();
        if (session == null || !RtcSession.INCOMING.equals(session.status) || TextUtils.isEmpty(session.callId)) {
            finish();
            return;
        }
        RtcManager.getInstance().dismissIncomingAlert();
        RtcModel.getInstance().rejectCall(session.callId, RtcManager.getInstance().getDeviceId(), new IRequestResultListener<>() {
            @Override
            public void onSuccess(CommonResponse result) {
            }

            @Override
            public void onFail(int code, String msg) {
            }
        });
        RtcManager.getInstance().finish();
        finish();
    }

    private void updateSingleCallChrome(RtcSession session) {
        applySingleCallVisualState(session);
        renderCallHeader(shouldShowHeader(session));
        updateBackdropVisibility(session);
        updateRemoteIdentityVisibility(session);
        updateLocalPreviewVisibility();
    }

    private void updateGroupCallChrome(RtcSession session) {
        titleTv.setTextSize(callType == 1 ? 28 : 30);
        statusTv.setTextSize(callType == 1 ? 15 : 16);
        statusTv.setTextColor(callType == 1 ? Color.parseColor("#C9D0D9") : Color.parseColor("#1ED760"));
        avatarTv.setVisibility(View.GONE);
        if (groupAvatarCluster != null) {
            groupAvatarCluster.setVisibility(View.VISIBLE);
        }
        updateGroupAvatarCluster(resolveGroupParticipantNames().isEmpty() ? resolveGroupPreviewNames(session) : resolveGroupParticipantNames());
        boolean inVideoCall = session != null && RtcSession.IN_CALL.equals(session.status) && callType == 1;
        if (videoGrid != null) {
            videoGrid.setVisibility(inVideoCall ? View.VISIBLE : View.GONE);
        }
        if (groupFooterTitleTv != null) {
            if (inVideoCall) {
                groupFooterTitleTv.setVisibility(View.VISIBLE);
                groupFooterTitleTv.setText(displayName(session) + " · " + groupParticipantCount(session) + "人通话");
            } else {
                groupFooterTitleTv.setVisibility(View.GONE);
            }
        }
        renderCallHeader(!inVideoCall);
        if (audioLeftOrb != null) audioLeftOrb.setVisibility(callType == 1 ? View.GONE : View.VISIBLE);
        if (audioRightOrb != null) audioRightOrb.setVisibility(callType == 1 ? View.GONE : View.VISIBLE);
        if (audioBottomGlow != null) audioBottomGlow.setVisibility(callType == 1 ? View.GONE : View.VISIBLE);
        if (videoTopScrim != null) videoTopScrim.setVisibility(callType == 1 ? View.VISIBLE : View.GONE);
        if (videoBottomScrim != null) videoBottomScrim.setVisibility(callType == 1 ? View.VISIBLE : View.GONE);
    }

    private boolean shouldShowHeader(RtcSession session) {
        if (initialCallType == 1) {
            return remoteVideoMuted || remoteVideoTrack == null;
        }
        return !isVideoUi(session);
    }

    private boolean isVideoUi(RtcSession session) {
        if (initialCallType == 0) {
            return session != null && session.localCameraEnabled;
        }
        return callType == 1 || (session != null && session.localCameraEnabled) || remoteVideoTrack != null;
    }

    private void updateBackdropVisibility(RtcSession session) {
        boolean videoUi = isVideoUi(session);
        if (videoTopScrim != null) videoTopScrim.setVisibility(videoUi ? View.VISIBLE : View.GONE);
        if (videoBottomScrim != null) videoBottomScrim.setVisibility(videoUi ? View.VISIBLE : View.GONE);
        if (audioLeftOrb != null) audioLeftOrb.setVisibility(videoUi ? View.GONE : View.VISIBLE);
        if (audioRightOrb != null) audioRightOrb.setVisibility(videoUi ? View.GONE : View.VISIBLE);
        if (audioBottomGlow != null) audioBottomGlow.setVisibility(videoUi ? View.GONE : View.VISIBLE);
    }

    private void updateRemoteIdentityVisibility(RtcSession session) {
        if (privateRemotePlaceholder == null) {
            return;
        }
        privateRemotePlaceholder.setVisibility(View.GONE);
        if (privateRemoteNameTv != null) {
            privateRemoteNameTv.setVisibility(View.GONE);
        }
    }

    private void addVideoScrims() {
        videoTopScrim = new View(this);
        GradientDrawable topDrawable = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{
                        Color.argb(170, 6, 10, 16),
                        Color.argb(50, 6, 10, 16),
                        Color.TRANSPARENT
                }
        );
        videoTopScrim.setBackground(topDrawable);
        mediaContainer.addView(videoTopScrim, new FrameLayout.LayoutParams(-1, dp(320)));

        videoBottomScrim = new View(this);
        GradientDrawable bottomDrawable = new GradientDrawable(
                GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{
                        Color.argb(225, 8, 11, 19),
                        Color.argb(170, 8, 11, 19),
                        Color.TRANSPARENT
                }
        );
        videoBottomScrim.setBackground(bottomDrawable);
        FrameLayout.LayoutParams bottomLp = new FrameLayout.LayoutParams(-1, dp(360));
        bottomLp.gravity = Gravity.BOTTOM;
        mediaContainer.addView(videoBottomScrim, bottomLp);
    }

    private void addAudioBackdrop() {
        audioLeftOrb = new View(this);
        audioLeftOrb.setBackground(circleDrawable(Color.argb(70, 109, 96, 255)));
        FrameLayout.LayoutParams leftLp = new FrameLayout.LayoutParams(dp(220), dp(220));
        leftLp.leftMargin = -dp(56);
        leftLp.topMargin = dp(120);
        mediaContainer.addView(audioLeftOrb, leftLp);

        audioRightOrb = new View(this);
        audioRightOrb.setBackground(circleDrawable(Color.argb(52, 255, 91, 125)));
        FrameLayout.LayoutParams rightLp = new FrameLayout.LayoutParams(dp(280), dp(280));
        rightLp.gravity = Gravity.RIGHT | Gravity.BOTTOM;
        rightLp.rightMargin = -dp(80);
        rightLp.bottomMargin = dp(84);
        mediaContainer.addView(audioRightOrb, rightLp);

        audioBottomGlow = new View(this);
        GradientDrawable bottomDrawable = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{
                        Color.TRANSPARENT,
                        Color.argb(80, 118, 83, 255)
                }
        );
        audioBottomGlow.setBackground(bottomDrawable);
        FrameLayout.LayoutParams bottomLp = new FrameLayout.LayoutParams(-1, dp(260));
        bottomLp.gravity = Gravity.BOTTOM;
        mediaContainer.addView(audioBottomGlow, bottomLp);
    }

    private FrameLayout buildGroupAvatarCluster() {
        FrameLayout cluster = new FrameLayout(this);
        cluster.setVisibility(View.GONE);
        int[] lefts = {dp(24), dp(82), dp(140)};
        for (int i = 0; i < 3; i++) {
            TextView label = new TextView(this);
            label.setGravity(Gravity.CENTER);
            label.setTextColor(Color.WHITE);
            label.setTextSize(26);
            label.setTypeface(Typeface.DEFAULT_BOLD);
            label.setBackground(circleDrawable(Color.parseColor("#2A313D")));
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(76), dp(76));
            lp.leftMargin = lefts[i];
            cluster.addView(label, lp);
            groupAvatarLabels.add(label);
        }
        groupAvatarOverflowTv = new TextView(this);
        groupAvatarOverflowTv.setGravity(Gravity.CENTER);
        groupAvatarOverflowTv.setTextColor(Color.WHITE);
        groupAvatarOverflowTv.setTextSize(18);
        groupAvatarOverflowTv.setTypeface(Typeface.DEFAULT_BOLD);
        groupAvatarOverflowTv.setBackground(circleDrawable(Color.parseColor("#454D59")));
        FrameLayout.LayoutParams overflowLp = new FrameLayout.LayoutParams(dp(44), dp(44));
        overflowLp.leftMargin = dp(168);
        overflowLp.topMargin = dp(48);
        cluster.addView(groupAvatarOverflowTv, overflowLp);
        return cluster;
    }

    private void updateGroupAvatarCluster(List<String> names) {
        if (groupAvatarLabels.isEmpty()) {
            return;
        }
        int total = names.size();
        for (int i = 0; i < groupAvatarLabels.size(); i++) {
            TextView label = groupAvatarLabels.get(i);
            if (i < total) {
                label.setVisibility(View.VISIBLE);
                label.setText(shortName(names.get(i)));
            } else {
                label.setVisibility(View.INVISIBLE);
            }
        }
        if (groupAvatarOverflowTv != null) {
            int extra = Math.max(0, total - 3);
            groupAvatarOverflowTv.setVisibility(extra > 0 ? View.VISIBLE : View.GONE);
            if (extra > 0) {
                groupAvatarOverflowTv.setText("+" + extra);
            }
        }
    }

    private List<String> resolveGroupPreviewNames(RtcSession session) {
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        if (session != null && !TextUtils.isEmpty(session.fromName)) {
            ordered.add(session.fromName);
        }
        ordered.addAll(resolveInviteDisplayNames(session));
        ordered.addAll(resolveGroupParticipantNames());
        ordered.remove("");
        return new ArrayList<>(ordered);
    }

    private void updateParticipantsRoster(Map<String, String> participants) {
        groupParticipantRoster.clear();
        ArrayList<String> liveParticipantUIDs = new ArrayList<>();
        if (participants != null) {
            for (Map.Entry<String, String> entry : participants.entrySet()) {
                if (!TextUtils.isEmpty(entry.getKey())) {
                    liveParticipantUIDs.add(entry.getKey());
                }
                String resolvedName = resolveParticipantName(entry.getKey(), entry.getValue());
                if (!TextUtils.isEmpty(resolvedName)) {
                    groupParticipantRoster.put(entry.getKey(), resolvedName);
                }
            }
        }
        int liveParticipantCount = groupParticipantRoster.size();
        if (groupParticipantRoster.isEmpty()) {
            RtcSession session = RtcManager.getInstance().currentSession();
            List<String> inviteDisplayNames = resolveInviteDisplayNames(session);
            for (int i = 0; i < inviteDisplayNames.size(); i++) {
                String name = inviteDisplayNames.get(i);
                if (!TextUtils.isEmpty(name)) {
                    groupParticipantRoster.put("invite_" + i, name);
                }
            }
            if (session != null && !TextUtils.isEmpty(session.fromUID)) {
                String fromName = resolveParticipantName(session.fromUID, session.fromName);
                if (!TextUtils.isEmpty(fromName)) {
                    groupParticipantRoster.put(session.fromUID, fromName);
                }
            }
        }
        RtcSession session = RtcManager.getInstance().currentSession();
        if (isGroupCall()
                && session != null
                && RtcSession.IN_CALL.equals(session.status)) {
            EndpointManager.getInstance().invoke("rtc_update_live_participant_uids", liveParticipantUIDs);
            EndpointManager.getInstance().invoke("rtc_maybe_finish_group_if_lonely", liveParticipantCount);
        }
        updateGroupCallChrome(RtcManager.getInstance().currentSession());
    }

    private List<String> resolveGroupParticipantNames() {
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        if (!groupParticipantRoster.isEmpty()) {
            ordered.addAll(groupParticipantRoster.values());
        } else {
            RtcSession session = RtcManager.getInstance().currentSession();
            if (session != null && !TextUtils.isEmpty(session.fromName)) {
                ordered.add(resolveParticipantName(session.fromUID, session.fromName));
            }
            ordered.addAll(resolveInviteDisplayNames(session));
        }
        ordered.remove("");
        return new ArrayList<>(ordered);
    }

    private String shortName(String value) {
        if (TextUtils.isEmpty(value)) {
            return "";
        }
        return value.substring(0, 1);
    }

    private int groupParticipantCount(RtcSession session) {
        int liveCount = groupParticipantRoster.isEmpty() ? 0 : groupParticipantRoster.size();
        if (liveCount > 0) {
            return liveCount;
        }
        int previewCount = resolveGroupPreviewNames(session).size();
        return Math.max(2, previewCount);
    }

    private String groupStatusText(RtcSession session) {
        if (session == null) {
            return callType == 1 ? "正在邀请成员加入视频通话…" : "正在邀请成员加入语音通话…";
        }
        if (RtcSession.INCOMING.equals(session.status)) {
            String inviter = resolveParticipantName(session.fromUID, session.fromName);
            if (TextUtils.isEmpty(inviter)) {
                inviter = "有人";
            }
            return inviter + (callType == 1 ? "邀请你加入群视频" : "邀请你加入群语音");
        }
        if (RtcSession.IN_CALL.equals(session.status)) {
            if (callType == 1) {
                return "通话中";
            }
            return formatDuration() + " · " + groupParticipantCount(session) + " 人在线";
        }
        if (RtcSession.JOINING.equals(session.status)) {
            return "正在接入群通话…";
        }
        return callType == 1 ? "正在邀请成员加入视频通话…" : "正在邀请成员加入语音通话…";
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
