package com.chat.rtc.entity;

import com.xinbida.wukongim.entity.WKChannel;

import java.util.List;

public class RtcSession {
    public static final String OUTGOING = "outgoing";
    public static final String INCOMING = "incoming";
    public static final String JOINING = "joining";
    public static final String IN_CALL = "in_call";
    public static final String RECONNECTING = "reconnecting";
    public static final String ENDED = "ended";

    public String callId;
    public String roomName;
    public WKChannel channel;
    public int callType;
    public String status;
    public String serverStatus;
    public boolean incoming;
    public String fromUID;
    public String fromName;
    public List<String> inviteUIDs;
    public List<String> inviteNames;
    public long expireAt;
    public List<String> permissions;
    public boolean liveKitConnected;
    public boolean localCameraEnabled;
    public boolean localMicrophoneEnabled;
    public boolean minimized;
    public long connectedAt;
    public long lonelySinceAt;
    public List<String> joinedUIDs;
    public int liveParticipantCount;
    public boolean hadMultipleLiveParticipants;
    public boolean hadRemoteParticipantJoined;
    public boolean ending;
    public String error;
}
