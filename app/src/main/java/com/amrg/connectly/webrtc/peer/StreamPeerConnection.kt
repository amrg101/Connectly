package com.amrg.connectly.webrtc.peer

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.IceCandidateErrorEvent
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SessionDescription
import timber.log.Timber

class StreamPeerConnection(
    private val coroutineScope: CoroutineScope,
    private val type: StreamPeerType,
    private val mediaConstraints: MediaConstraints,
    private val onStreamAdded: ((MediaStream) -> Unit)?,
    private val onIceCandidate: ((IceCandidate, StreamPeerType) -> Unit)?,
    private val onVideoTrack: ((RtpTransceiver?) -> Unit)?,
    private val onNegotiationNeeded: ((StreamPeerConnection, StreamPeerType) -> Unit)?,
) : PeerConnection.Observer {

    lateinit var connection: PeerConnection

    private val pendingCandidateMutex = Mutex()
    private val pendingIceCandidate = mutableListOf<IceCandidate>()

    fun initialize(connection: PeerConnection) {
        this.connection = connection
    }

    suspend fun createOffer(): Result<SessionDescription> {
        return createValue { connection.createOffer(it, mediaConstraints) }
    }

    suspend fun createAnswer(): Result<SessionDescription> {
        return createValue { connection.createAnswer(it, mediaConstraints) }
    }

    suspend fun setRemoteDescription(sessionDescription: SessionDescription): Result<Unit> {
        return setValue {
            connection.setRemoteDescription(
                it, SessionDescription(
                    sessionDescription.type,
                    sessionDescription.description.mungeCodecs()
                )
            )
        }.also {
            pendingCandidateMutex.withLock {
                pendingIceCandidate.forEach { candidate ->
                    connection.addIceCandidate(candidate)
                }
                pendingIceCandidate.clear()
            }
        }
    }

    suspend fun setLocalDescription(sessionDescription: SessionDescription): Result<Unit> {
        return setValue {
            connection.setLocalDescription(
                it, SessionDescription(
                    sessionDescription.type,
                    sessionDescription.description.mungeCodecs()
                )
            )
        }
    }

    suspend fun addIceCandidate(candidate: IceCandidate): Result<Unit> {
        if (connection.remoteDescription == null) {
            pendingCandidateMutex.withLock {
                pendingIceCandidate.add(candidate)
            }
            return Result.failure(RuntimeException("remote description is null"))
        }
        return connection.addRtcIceCandidate(candidate)
    }

    override fun onIceCandidate(candidate: IceCandidate?) {
        if (candidate == null) return
        onIceCandidate?.invoke(candidate, type)
    }

    override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {
        super.onAddTrack(receiver, mediaStreams)
        mediaStreams?.forEach { mediaStream ->
            mediaStream.audioTracks.forEach { audioTrack ->
                audioTrack.setEnabled(true)
            }
            onStreamAdded?.invoke(mediaStream)
        }
    }

    override fun onAddStream(mediaStream: MediaStream?) {
        if (mediaStream == null) return
        onStreamAdded?.invoke(mediaStream)
    }

    override fun onRenegotiationNeeded() {
        onNegotiationNeeded?.invoke(this, type)
    }

    override fun onTrack(transceiver: RtpTransceiver?) {
        super.onTrack(transceiver)
        onVideoTrack?.invoke(transceiver)
    }

    override fun onIceCandidateError(event: IceCandidateErrorEvent?) {
        super.onIceCandidateError(event)
        Timber.e("onIceCandidateError, event: $event")
    }

    override fun onSignalingChange(newState: PeerConnection.SignalingState?) {
        Timber.d("onSignalingChange, newState: $newState")
    }

    override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
        Timber.i("onIceConnectionChange, newState: $newState")
    }

    override fun onIceConnectionReceivingChange(receiving: Boolean) {
        Timber.i("onIceConnectionReceivingChange, receiving: $receiving ")
    }

    override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) {
        Timber.i("onIceGatheringChange, newState: $newState ")
    }

    override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {
        Timber.i("onIceCandidatesRemoved, candidates: $candidates ")
    }

    override fun onRemoveStream(mediaStream: MediaStream?) {
        Timber.i("onRemoveStream, mediaStream: $mediaStream ")
    }

    override fun onDataChannel(data: DataChannel?) = Unit
}