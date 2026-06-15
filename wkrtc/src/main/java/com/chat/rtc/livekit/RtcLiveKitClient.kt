package com.chat.rtc.livekit

import android.content.Context
import android.util.Log
import com.chat.rtc.entity.RtcCallResp
import io.livekit.android.ConnectOptions
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
import io.livekit.android.room.track.RemoteTrackPublication
import io.livekit.android.room.track.Track
import io.livekit.android.room.track.TrackPublication
import io.livekit.android.room.track.VideoTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object RtcLiveKitClient {
    private const val TAG = "RtcLiveKit"
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
        Log.i(TAG, "connect callId=${call?.call_id}, callType=$callType, livekitUrl=${livekit.url}, roomName=${call?.room_name}")
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
                                Log.i(
                                    TAG,
                                    "track published kind=${event.publication.kind}, muted=${event.publication.muted}, " +
                                        "subscribed=${event.publication.subscribed}, participant=${participantIdentity(event.participant)}, " +
                                        "local=${event.participant == nextRoom.localParticipant}, hasTrack=${event.publication.track != null}",
                                )
                                if (event.participant == nextRoom.localParticipant) {
                                    val videoTrack = event.publication.track as? VideoTrack ?: return@collect
                                    callback.onLocalVideoTrack(videoTrack, participantIdentity(event.participant), participantName(event.participant))
                                } else {
                                    ensureRemoteVideoSubscription(event.publication)
                                    val videoTrack = event.publication.track as? VideoTrack
                                    if (videoTrack != null) {
                                        callback.onRemoteVideoTrack(
                                            videoTrack,
                                            participantIdentity(event.participant),
                                            participantName(event.participant),
                                        )
                                    }
                                    if (event.publication.kind == Track.Kind.VIDEO && event.publication.muted) {
                                        callback.onRemoteVideoMuteChanged(
                                            participantIdentity(event.participant),
                                            participantName(event.participant),
                                            true,
                                        )
                                    }
                                }
                                emitParticipants(nextRoom, callback)
                            }

                            is RoomEvent.TrackSubscribed -> {
                                Log.i(
                                    TAG,
                                    "track subscribed kind=${event.publication.kind}, muted=${event.publication.muted}, " +
                                        "participant=${participantIdentity(event.participant)}, track=${event.track::class.java.simpleName}",
                                )
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
                                Log.i(TAG, "track muted kind=${event.publication.kind}, participant=${participantIdentity(event.participant)}")
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
                                Log.i(TAG, "track unmuted kind=${event.publication.kind}, participant=${participantIdentity(event.participant)}, hasTrack=${event.publication.track != null}")
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
                                Log.i(TAG, "track unsubscribed participant=${participantIdentity(event.participant)}, track=${event.track::class.java.simpleName}")
                                if (event.track is VideoTrack) {
                                    callback.onRemoteVideoRemoved(participantIdentity(event.participant))
                                }
                                emitParticipants(nextRoom, callback)
                            }

                            is RoomEvent.ParticipantConnected -> {
                                Log.i(TAG, "participant connected identity=${participantIdentity(event.participant)}, publications=${event.participant.trackPublications.size}")
                                ensureRemoteVideoSubscriptions(nextRoom)
                                emitParticipants(nextRoom, callback)
                            }

                            is RoomEvent.ParticipantDisconnected -> {
                                Log.i(TAG, "participant disconnected identity=${participantIdentity(event.participant)}")
                                callback.onRemoteVideoRemoved(participantIdentity(event.participant))
                                emitParticipants(nextRoom, callback)
                            }

                            else -> Unit
                        }
                    }
                }
                nextRoom.connect(livekit!!.url, livekit.token, ConnectOptions(autoSubscribe = true))
                if (activeVersion != connectionVersion) {
                    return@launch
                }
                Log.i(TAG, "room connected, remoteParticipants=${nextRoom.remoteParticipants.size}")
                ensureRemoteVideoSubscriptions(nextRoom)
                emitParticipants(nextRoom, callback)
                enableLocalMicrophone(nextRoom, activeVersion)
                if (activeVersion != connectionVersion) {
                    return@launch
                }
                callback.onConnected()
                if (callType == 1) {
                    scope.launch {
                        publishLocalCamera(nextRoom, callback, activeVersion)
                        if (activeVersion != connectionVersion) {
                            return@launch
                        }
                        ensureRemoteVideoSubscriptions(nextRoom)
                        emitRemoteVideos(nextRoom, callback)
                        emitParticipants(nextRoom, callback)
                        scope.launch {
                            delay(400)
                            if (activeVersion != connectionVersion) {
                                return@launch
                            }
                            ensureRemoteVideoSubscriptions(nextRoom)
                            emitLocalVideo(nextRoom, callback)
                            emitRemoteVideos(nextRoom, callback)
                            emitParticipants(nextRoom, callback)
                        }
                        scope.launch {
                            delay(1200)
                            if (activeVersion != connectionVersion) {
                                return@launch
                            }
                            ensureRemoteVideoSubscriptions(nextRoom)
                            emitRemoteVideos(nextRoom, callback)
                            emitParticipants(nextRoom, callback)
                        }
                        scope.launch {
                            repeat(8) {
                                delay(1000)
                                if (activeVersion != connectionVersion) {
                                    return@launch
                                }
                                ensureRemoteVideoSubscriptions(nextRoom)
                                emitRemoteVideos(nextRoom, callback)
                                emitParticipants(nextRoom, callback)
                            }
                        }
                    }
                } else {
                    ensureRemoteVideoSubscriptions(nextRoom)
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
            } catch (error: Throwable) {
                Log.w(TAG, "set camera enabled failed, enabled=$enabled, message=${error.message}", error)
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
            } catch (error: Throwable) {
                Log.w(TAG, "switch camera failed, message=${error.message}", error)
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
                ensureRemoteVideoSubscriptions(activeRoom)
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

    private suspend fun enableLocalMicrophone(room: Room, activeVersion: Int) {
        repeat(3) { attempt ->
            if (activeVersion != connectionVersion) {
                return
            }
            try {
                room.localParticipant.setMicrophoneEnabled(true)
                Log.i(TAG, "local microphone enabled, attempt=${attempt + 1}")
                return
            } catch (error: Throwable) {
                Log.w(TAG, "enable microphone failed, attempt=${attempt + 1}, message=${error.message}", error)
                delay(300)
            }
        }
    }

    private suspend fun publishLocalCamera(room: Room, callback: RtcLiveKitCallback, activeVersion: Int) {
        repeat(3) { attempt ->
            if (activeVersion != connectionVersion) {
                return
            }
            try {
                room.localParticipant.setCameraEnabled(true)
                delay(200)
                if (activeVersion != connectionVersion) {
                    return
                }
                emitLocalVideo(room, callback)
                Log.i(TAG, "local camera enabled, attempt=${attempt + 1}")
                return
            } catch (error: Throwable) {
                Log.w(TAG, "enable camera failed, attempt=${attempt + 1}, message=${error.message}", error)
                delay(500)
            }
        }
    }

    private fun emitRemoteVideos(room: Room, callback: RtcLiveKitCallback) {
        ensureRemoteVideoSubscriptions(room)
        Log.i(TAG, "emit remote videos, remoteParticipants=${room.remoteParticipants.size}")
        room.remoteParticipants.values.forEach { participant ->
            Log.i(TAG, "remote participant identity=${participantIdentity(participant)}, publications=${participant.trackPublications.size}")
            participant.trackPublications.values.forEach { publication ->
                val track = publication.track as? VideoTrack ?: return@forEach
                Log.i(TAG, "emit remote video identity=${participantIdentity(participant)}, muted=${publication.muted}, subscribed=${publication.subscribed}")
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

    private fun ensureRemoteVideoSubscriptions(room: Room) {
        room.remoteParticipants.values.forEach { participant ->
            participant.trackPublications.values.forEach { publication ->
                ensureRemoteVideoSubscription(publication)
            }
        }
    }

    private fun ensureRemoteVideoSubscription(publication: TrackPublication) {
        if (publication.kind != Track.Kind.VIDEO) {
            return
        }
        val remotePublication = publication as? RemoteTrackPublication ?: return
        try {
            remotePublication.setSubscribed(true)
            remotePublication.setEnabled(true)
            Log.i(TAG, "ensure remote video subscription muted=${remotePublication.muted}, subscribed=${remotePublication.subscribed}, hasTrack=${remotePublication.track != null}")
        } catch (_: Throwable) {
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
