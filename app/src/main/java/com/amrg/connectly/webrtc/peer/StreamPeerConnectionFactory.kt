package com.amrg.connectly.webrtc.peer

import android.content.Context
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import org.webrtc.AudioSource
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.EglBase
import org.webrtc.HardwareVideoEncoderFactory
import org.webrtc.IceCandidate
import org.webrtc.Logging
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnection.RTCConfiguration
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpTransceiver
import org.webrtc.SimulcastVideoEncoderFactory
import org.webrtc.SoftwareVideoEncoderFactory
import org.webrtc.VideoSource
import org.webrtc.audio.JavaAudioDeviceModule
import timber.log.Timber

class StreamPeerConnectionFactory(
    private val context: Context
) {

    val eglBaseContext by lazy {
        EglBase.create().eglBaseContext
    }
    private val videoDecoderFactory by lazy {
        DefaultVideoDecoderFactory(eglBaseContext)
    }

    private val videoEncoderFactory by lazy {
        val hardwareEncoderFactory = HardwareVideoEncoderFactory(eglBaseContext, true, true)
        SimulcastVideoEncoderFactory(hardwareEncoderFactory, SoftwareVideoEncoderFactory())
    }

    private val audioDeviceModel by lazy {
        JavaAudioDeviceModule.builder(context)
            .setUseHardwareAcousticEchoCanceler(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            .setUseHardwareNoiseSuppressor(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            .createAudioDeviceModule().also {
                it.setSpeakerMute(false)
                it.setMicrophoneMute(false)
            }
    }

    val rtcConfig = RTCConfiguration(
        listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        )
    ).apply {
        // it's very important to use new unified sdp semantics PLAN_B is deprecated
        sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
    }

    private val factory by lazy {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setInjectableLogger({ message, severity, label ->
                    when (severity) {
                        Logging.Severity.LS_VERBOSE -> {
                            Timber.v("[onLogMessage] label: $label, message: $message")
                        }

                        Logging.Severity.LS_INFO -> {
                            Timber.i("[onLogMessage] label: $label, message: $message")
                        }

                        Logging.Severity.LS_WARNING -> {
                            Timber.w("[onLogMessage] label: $label, message: $message")
                        }

                        Logging.Severity.LS_ERROR -> {
                            Timber.e("[onLogMessage] label: $label, message: $message")
                        }

                        Logging.Severity.LS_NONE -> {
                            Timber.d("[onLogMessage] label: $label, message: $message")
                        }

                        else -> {}
                    }
                }, Logging.Severity.LS_VERBOSE)
                .createInitializationOptions()
        )
        PeerConnectionFactory.builder()
            .setVideoDecoderFactory(videoDecoderFactory)
            .setVideoEncoderFactory(videoEncoderFactory)
            .setAudioDeviceModule(audioDeviceModel)
            .createPeerConnectionFactory()
    }


    fun createStreamPeerConnection(
        coroutineScope: CoroutineScope,
        configuration: RTCConfiguration,
        type: StreamPeerType,
        mediaConstraints: MediaConstraints,
        onStreamAdded: ((MediaStream) -> Unit)? = null,
        onIceCandidate: ((IceCandidate, StreamPeerType) -> Unit)? = null,
        onVideoTrack: ((RtpTransceiver?) -> Unit)? = null,
        onNegotiationNeeded: ((StreamPeerConnection, StreamPeerType) -> Unit)? = null,
    ): StreamPeerConnection {
        val peerConnection = StreamPeerConnection(
            coroutineScope = coroutineScope,
            type = type,
            mediaConstraints = mediaConstraints,
            onStreamAdded = onStreamAdded,
            onIceCandidate = onIceCandidate,
            onVideoTrack = onVideoTrack,
            onNegotiationNeeded = onNegotiationNeeded
        )
        val connection = requireNotNull(
            factory.createPeerConnection(configuration, peerConnection)
        )
        return peerConnection.apply { initialize(connection) }
    }

    fun makeVideoSource(isScreenCast: Boolean) = factory.createVideoSource(isScreenCast)

    fun makeVideoTrack(
        source: VideoSource,
        trackId: String
    ) = factory.createVideoTrack(trackId, source)

    fun makeAudioSource(mediaConstraints: MediaConstraints = MediaConstraints()) =
        factory.createAudioSource(mediaConstraints)

    fun makeAudioTrack(
        source: AudioSource,
        trackId: String
    ) = factory.createAudioTrack(trackId, source)
}