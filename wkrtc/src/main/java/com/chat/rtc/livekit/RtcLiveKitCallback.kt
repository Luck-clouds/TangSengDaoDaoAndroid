package com.chat.rtc.livekit

import io.livekit.android.room.track.VideoTrack

interface RtcLiveKitCallback {
    fun onConnected()
    fun onLocalVideoTrack(track: VideoTrack, identity: String, name: String)
    fun onRemoteVideoTrack(track: VideoTrack, identity: String, name: String)
    fun onRemoteVideoMuteChanged(identity: String, name: String, muted: Boolean)
    fun onRemoteVideoRemoved(identity: String)
    fun onParticipantsChanged(participants: Map<String, String>)
    fun onError(message: String)
}
