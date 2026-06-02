package com.chat.rtc.livekit

import android.content.Context
import com.chat.rtc.entity.RtcCallResp
import io.livekit.android.LiveKit
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.renderer.SurfaceViewRenderer
import io.livekit.android.renderer.TextureViewRenderer
import io.livekit.android.room.Room
import io.livekit.android.room.participant.Participant
import io.livekit.android.room.participant.RemoteParticipant
import io.livekit.android.room.track.LocalAudioTrackOptions
import io.livekit.android.room.track.LocalVideoTrack
import io.livekit.android.room.track.Track
import io.livekit.android.room.track.VideoTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object RtcLiveKitClient {
    private var room: Room? = null
    private var roomContext: Context? = null
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var connectionVersion = 0

    @JvmStatic
    fun connect(context: Context, call: RtcCallResp?, callType: Int, callback: RtcLiveKitCallback) {
        val livekit = call?.livekit
        if (livekit?.url.isNullOrBlank() || livekit?.token.isNullOrBlank()) {
            callback.onError("通话服务没有返回 LiveKit 连接信息")
            return
        }
        disconnect()
        val activeVersion = ++connectionVersion
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                val nextRoom = LiveKit.create(context.applicationContext)
                nextRoom.localParticipant.audioTrackCaptureDefaults = LocalAudioTrackOptions(
                    noiseSuppression = true,
                    echoCancellation = true,
                    autoGainControl = true,
                    highPassFilter = true,
                    typingNoiseDetection = true,
                )
                RtcAudioHelper.configureForCall(context.applicationContext, callType == 1)
                room = nextRoom
                roomContext = context.applicationContext
                scope.launch {
                    nextRoom.events.collect { event ->
                        if (activeVersion != connectionVersion) {
                            return@collect
                        }
                        when (event) {
                            is RoomEvent.TrackPublished -> {
                                val videoTrack = event.publication.track as? VideoTrack ?: return@collect
                                if (event.participant == nextRoom.localParticipant) {
                                    callback.onLocalVideoTrack(videoTrack, participantIdentity(event.participant), participantName(event.participant))
                                }
                                emitParticipants(nextRoom, callback)
                            }

                            is RoomEvent.TrackSubscribed -> {
                                val videoTrack = event.track as? VideoTrack ?: return@collect
                                callback.onRemoteVideoTrack(videoTrack, participantIdentity(event.participant), participantName(event.participant))
                                if (event.publication.kind == Track.Kind.VIDEO && event.publication.muted) {
                                    callback.onRemoteVideoMuteChanged(
                                        participantIdentity(event.participant),
                                        participantName(event.participant),
                                        true,
                                    )
                                }
                                emitParticipants(nextRoom, callback)
                            }

                            is RoomEvent.TrackMuted -> {
                                if (event.participant is RemoteParticipant && event.publication.kind == Track.Kind.VIDEO) {
                                    callback.onRemoteVideoMuteChanged(
                                        participantIdentity(event.participant),
                                        participantName(event.participant),
                                        true,
                                    )
                                }
                                emitParticipants(nextRoom, callback)
                            }

                            is RoomEvent.TrackUnmuted -> {
                                if (event.participant is RemoteParticipant && event.publication.kind == Track.Kind.VIDEO) {
                                    callback.onRemoteVideoMuteChanged(
                                        participantIdentity(event.participant),
                                        participantName(event.participant),
                                        false,
                                    )
                                    val videoTrack = event.publication.track as? VideoTrack
                                    if (videoTrack != null) {
                                        callback.onRemoteVideoTrack(
                                            videoTrack,
                                            participantIdentity(event.participant),
                                            participantName(event.participant),
                                        )
                                    }
                                }
                                emitParticipants(nextRoom, callback)
                            }

                            is RoomEvent.TrackUnsubscribed -> {
                                if (event.track is VideoTrack) {
                                    callback.onRemoteVideoRemoved(participantIdentity(event.participant))
                                }
                                emitParticipants(nextRoom, callback)
                            }

                            is RoomEvent.ParticipantConnected -> {
                                emitParticipants(nextRoom, callback)
                            }

                            is RoomEvent.ParticipantDisconnected -> {
                                callback.onRemoteVideoRemoved(participantIdentity(event.participant))
                                emitParticipants(nextRoom, callback)
                            }

                            else -> Unit
                        }
                    }
                }
                nextRoom.connect(livekit!!.url, livekit.token)
                if (activeVersion != connectionVersion) {
                    return@launch
                }
                emitParticipants(nextRoom, callback)
                nextRoom.localParticipant.setMicrophoneEnabled(true)
                if (activeVersion != connectionVersion) {
                    return@launch
                }
                callback.onConnected()
                if (callType == 1) {
                    scope.launch {
                        try {
                            nextRoom.localParticipant.setCameraEnabled(true)
                            if (activeVersion != connectionVersion) {
                                return@launch
                            }
                            emitLocalVideo(nextRoom, callback)
                            emitRemoteVideos(nextRoom, callback)
                            emitParticipants(nextRoom, callback)
                        } catch (_: Throwable) {
                        }
                        scope.launch {
                            delay(400)
                            if (activeVersion != connectionVersion) {
                                return@launch
                            }
                            emitLocalVideo(nextRoom, callback)
                            emitRemoteVideos(nextRoom, callback)
                            emitParticipants(nextRoom, callback)
                        }
                        scope.launch {
                            delay(1200)
                            if (activeVersion != connectionVersion) {
                                return@launch
                            }
                            emitRemoteVideos(nextRoom, callback)
                            emitParticipants(nextRoom, callback)
                        }
                    }
                } else {
                    emitRemoteVideos(nextRoom, callback)
                }
            } catch (error: Throwable) {
                if (activeVersion != connectionVersion) {
                    return@launch
                }
                callback.onError(error.message ?: "连接通话失败")
                disconnect()
            }
        }
    }

    @JvmStatic
    fun initVideoRenderer(renderer: SurfaceViewRenderer) {
        room?.initVideoRenderer(renderer)
    }

    @JvmStatic
    fun initVideoRenderer(renderer: TextureViewRenderer) {
        room?.initVideoRenderer(renderer)
    }

    @JvmStatic
    fun setMicrophoneEnabled(enabled: Boolean) {
        val activeRoom = room ?: return
        val activeVersion = connectionVersion
        scope.launch {
            try {
                if (activeVersion != connectionVersion) {
                    return@launch
                }
                activeRoom.localParticipant.setMicrophoneEnabled(enabled)
            } catch (_: Throwable) {
            }
        }
    }

    @JvmStatic
    fun setCameraEnabled(enabled: Boolean, callback: RtcLiveKitCallback?) {
        val activeRoom = room ?: return
        val activeVersion = connectionVersion
        scope.launch {
            try {
                if (activeVersion != connectionVersion) {
                    return@launch
                }
                activeRoom.localParticipant.setCameraEnabled(enabled)
                if (enabled && callback != null) {
                    delay(200)
                    if (activeVersion != connectionVersion) {
                        return@launch
                    }
                    emitLocalVideo(activeRoom, callback)
                }
            } catch (_: Throwable) {
            }
        }
    }

    @JvmStatic
    fun switchCamera(callback: RtcLiveKitCallback?) {
        val activeRoom = room ?: return
        val activeVersion = connectionVersion
        scope.launch {
            try {
                if (activeVersion != connectionVersion) {
                    return@launch
                }
                val track = activeRoom.localParticipant
                    .getTrackPublication(Track.Source.CAMERA)
                    ?.track as? LocalVideoTrack
                track?.switchCamera()
                if (callback != null) {
                    delay(200)
                    if (activeVersion != connectionVersion) {
                        return@launch
                    }
                    emitLocalVideo(activeRoom, callback)
                }
            } catch (_: Throwable) {
            }
        }
    }

    @JvmStatic
    fun rebindState(callback: RtcLiveKitCallback?) {
        val activeRoom = room ?: return
        val activeVersion = connectionVersion
        if (callback == null) {
            return
        }
        scope.launch {
            try {
                if (activeVersion != connectionVersion) {
                    return@launch
                }
                callback.onConnected()
                emitLocalVideo(activeRoom, callback)
                emitRemoteVideos(activeRoom, callback)
                emitParticipants(activeRoom, callback)
            } catch (_: Throwable) {
            }
        }
    }

    @JvmStatic
    fun disconnect() {
        connectionVersion++
        val oldRoom = room
        val oldContext = roomContext
        val releaseScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        room = null
        roomContext = null
        scope.cancel()
        releaseScope.launch {
            try {
                oldRoom?.localParticipant?.setCameraEnabled(false)
            } catch (_: Throwable) {
            }
            try {
                oldRoom?.localParticipant?.setMicrophoneEnabled(false)
            } catch (_: Throwable) {
            }
            try {
                oldRoom?.disconnect()
            } catch (_: Throwable) {
            }
            delay(350L)
            try {
                oldRoom?.release()
            } catch (_: Throwable) {
            }
        }
        oldContext?.let { RtcAudioHelper.restore(it) }
    }

    private fun emitLocalVideo(room: Room, callback: RtcLiveKitCallback) {
        val track = room.localParticipant
            .getTrackPublication(Track.Source.CAMERA)
            ?.track as? VideoTrack
        if (track != null) {
            callback.onLocalVideoTrack(track, participantIdentity(room.localParticipant), participantName(room.localParticipant))
        }
    }

    private fun emitRemoteVideos(room: Room, callback: RtcLiveKitCallback) {
        room.remoteParticipants.values.forEach { participant ->
            participant.trackPublications.values.forEach { publication ->
                val track = publication.track as? VideoTrack ?: return@forEach
                callback.onRemoteVideoTrack(track, participantIdentity(participant), participantName(participant))
                if (publication.muted) {
                    callback.onRemoteVideoMuteChanged(
                        participantIdentity(participant),
                        participantName(participant),
                        true,
                    )
                }
            }
        }
    }

    private fun emitParticipants(room: Room, callback: RtcLiveKitCallback) {
        val participants = linkedMapOf<String, String>()
        participants[participantIdentity(room.localParticipant)] = participantName(room.localParticipant)
        room.remoteParticipants.values.forEach { participant ->
            participants[participantIdentity(participant)] = participantName(participant)
        }
        callback.onParticipantsChanged(participants)
    }

    private fun participantIdentity(participant: Participant): String {
        return participant.identity?.value ?: participant.sid.value
    }

    private fun participantName(participant: Participant): String {
        return participant.name?.takeIf { it.isNotBlank() } ?: participant.identity?.value ?: participant.sid.value
    }
}
