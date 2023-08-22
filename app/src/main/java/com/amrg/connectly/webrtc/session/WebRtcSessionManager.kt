package com.amrg.connectly.webrtc.session

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.content.getSystemService
import com.amrg.connectly.ui.screens.video.CameraState
import com.amrg.connectly.webrtc.SignalingClient
import com.amrg.connectly.webrtc.SignalingCommand
import com.amrg.connectly.webrtc.WebRTCSessionState
import com.amrg.connectly.webrtc.audio.AudioSwitchHandler
import com.amrg.connectly.webrtc.peer.StreamPeerConnection
import com.amrg.connectly.webrtc.peer.StreamPeerConnectionFactory
import com.amrg.connectly.webrtc.peer.StreamPeerType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.webrtc.Camera2Capturer
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraEnumerationAndroid
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStreamTrack
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoTrack
import timber.log.Timber
import java.util.UUID

val LocalWebRtcSessionManager: ProvidableCompositionLocal<WebRtcSessionManager> =
    staticCompositionLocalOf { error("WebRtcSessionManager was not initialized!") }

class WebRtcSessionManager(
    private val context: Context,
    val signalingClient: SignalingClient,
    val peerConnectionFactory: StreamPeerConnectionFactory
) {

    companion object {
        private const val ICE_SEPARATOR = '$'
    }

    private val sessionManagerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _localVideoTrackFlow = MutableSharedFlow<VideoTrack>()
    val localVideoTrackFlow: SharedFlow<VideoTrack> = _localVideoTrackFlow

    private val _remoteVideoTrackFlow = MutableSharedFlow<VideoTrack>()
    val remoteVideoTrackFlow: SharedFlow<VideoTrack> = _remoteVideoTrackFlow

    private val _remoteVideoTrackEnabledState = MutableStateFlow(true)
    val remoteVideoTrackEnabledState: StateFlow<Boolean> = _remoteVideoTrackEnabledState

    private val mediaConstraints = MediaConstraints().apply {
        mandatory.addAll(
            listOf(
                MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"),
                MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true")
            )
        )
    }

    private val cameraManager by lazy { context.getSystemService<CameraManager>() }
    private val videoCapturer by lazy { buildCameraCapture() }
    private val cameraEnumerator by lazy { Camera2Enumerator(context) }

    private val resolution: CameraEnumerationAndroid.CaptureFormat
        get() = getCameraResolution()

    private val surfaceTextureHelper = SurfaceTextureHelper.create(
        "SurfaceTextureHelperThread",
        peerConnectionFactory.eglBaseContext
    )

    private val videoSource by lazy {
        peerConnectionFactory.makeVideoSource(videoCapturer.isScreencast).apply {
            videoCapturer.initialize(surfaceTextureHelper, context, this.capturerObserver)
            videoCapturer.startCapture(resolution.width, resolution.height, 30)
        }
    }

    private val localVideoTrack by lazy {
        peerConnectionFactory.makeVideoTrack(
            videoSource,
            "Vide${UUID.randomUUID()}"
        )
    }

    private val audioHandler: AudioSwitchHandler by lazy {
        AudioSwitchHandler(context)
    }

    private val audioManager by lazy {
        context.getSystemService<AudioManager>()
    }

    private val audioConstraints: MediaConstraints by lazy {
        buildAudioConstraints()
    }

    private val audioSource by lazy {
        peerConnectionFactory.makeAudioSource(audioConstraints)
    }

    private val localAudioTrack by lazy {
        peerConnectionFactory.makeAudioTrack(
            audioSource,
            "Audio${UUID.randomUUID()}"
        )
    }

    private var offer: String? = null

     val peerConnection: StreamPeerConnection by lazy {
        peerConnectionFactory.createStreamPeerConnection(
            sessionManagerScope,
            peerConnectionFactory.rtcConfig,
            StreamPeerType.SUBSCRIBER,
            mediaConstraints,
            onIceCandidate = { iceCandidate, _ ->
                signalingClient.sendCommand(
                    SignalingCommand.ICE,
                    "${iceCandidate.sdpMid}$ICE_SEPARATOR${iceCandidate.sdpMLineIndex}$ICE_SEPARATOR${iceCandidate.sdp}"
                )
            },
            onVideoTrack = { rtpTransceiver ->
                val track = rtpTransceiver?.receiver?.track() ?: return@createStreamPeerConnection
                if (track.kind() == MediaStreamTrack.VIDEO_TRACK_KIND) {
                    val videoTrack = track as VideoTrack
                    sessionManagerScope.launch {
                        _remoteVideoTrackFlow.emit(videoTrack)
                    }
                }
            },
            onMessage = { (state, value) ->
               when(state){
                   CameraState::name.toString() -> handleCameraStateMessage(value)
               }
            }
        )
    }

    init {
        sessionManagerScope.launch {
            signalingClient.stateCommandFlow.collect { (command, value) ->
                when (command) {
                    SignalingCommand.OFFER -> handleOffer(value)
                    SignalingCommand.ANSWER -> handleAnswer(value)
                    SignalingCommand.ICE -> handleIce(value)
                    else -> Unit
                }
            }
        }
    }

    private fun handleCameraStateMessage(value: String){
        when(CameraState.valueOf(value)){
            CameraState.ENABLED ->  _remoteVideoTrackEnabledState.value = true
            CameraState.DISABLED ->  _remoteVideoTrackEnabledState.value = false
        }
    }

    private suspend fun handleIce(iceMsg: String) {
        val iceArray = iceMsg.split(ICE_SEPARATOR)
        peerConnection.addIceCandidate(
            IceCandidate(
                iceArray[0],
                iceArray[1].toInt(),
                iceArray[2],
            )
        )
    }

    private suspend fun handleAnswer(sdp: String) {
        peerConnection.setRemoteDescription(
            SessionDescription(SessionDescription.Type.ANSWER, sdp)
        )
    }

    private fun handleOffer(sdp: String) {
        offer = sdp
    }


    fun onSessionScreenReady() {
        setupAudio()
        peerConnection.connection.addTrack(localVideoTrack)
        peerConnection.connection.addTrack(localAudioTrack)
        updateLocalVideoTrack()
        sessionManagerScope.launch {
            if (offer != null) {
                sendAnswer()
            } else {
                sendOffer()
            }
        }
    }

    fun updateLocalVideoTrack(){
        sessionManagerScope.launch {
            _localVideoTrackFlow.emit(localVideoTrack)
        }
    }

    fun flipCamera() {
        (videoCapturer as? Camera2Capturer)?.switchCamera(null)
    }

    fun enableMicrophone(enabled: Boolean) {
        audioManager?.isMicrophoneMute = !enabled
    }

    fun enableCamera(enabled: Boolean) {
        if (enabled) {
            videoCapturer.startCapture(resolution.width, resolution.height, 30)
        } else {
            videoCapturer.stopCapture()
        }
    }

    fun disconnect() {
        // dispose audio & video tracks.
        remoteVideoTrackFlow.replayCache.forEach { videoTrack ->
            videoTrack.dispose()
        }
        localVideoTrackFlow.replayCache.forEach { videoTrack ->
            videoTrack.dispose()
        }
        localAudioTrack.dispose()
        localVideoTrack.dispose()

        // dispose audio handler and video capturer.
        audioHandler.stop()
        videoCapturer.stopCapture()
        videoCapturer.dispose()

        // dispose peerConnection.
        peerConnection.dispose()

        // dispose signaling clients and socket.
        signalingClient.sendCommand(SignalingCommand.STATE, "${WebRTCSessionState.Impossible}")
        signalingClient.dispose()
    }

    private suspend fun sendAnswer() {
        peerConnection.setRemoteDescription(
            SessionDescription(
                SessionDescription.Type.OFFER,
                offer
            )
        )
        val answer = peerConnection.createAnswer().getOrThrow()
        val result = peerConnection.setLocalDescription(answer)
        result.onSuccess {
            signalingClient.sendCommand(
                SignalingCommand.ANSWER,
                answer.description
            )
        }
    }

    private suspend fun sendOffer() {
        val offer = peerConnection.createOffer().getOrThrow()
        val result = peerConnection.setLocalDescription(offer)
        result.onSuccess {
            signalingClient.sendCommand(
                SignalingCommand.OFFER,
                offer.description
            )
        }
    }

    private fun buildCameraCapture(): VideoCapturer {
        val manager = cameraManager ?: throw RuntimeException("Camera was not initialized")

        val ids = manager.cameraIdList
        var hasFrontCamera = false
        var cameraId = ""

        ids.forEach { id ->
            val cameraLensFacing =
                manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING)
            if (cameraLensFacing == CameraMetadata.LENS_FACING_FRONT) {
                hasFrontCamera = true
                cameraId = id
            }
        }
        if (!hasFrontCamera && ids.isNotEmpty()) {
            cameraId = ids.first()
        }

        return Camera2Capturer(context, cameraId, null)
    }

    private fun getCameraResolution(): CameraEnumerationAndroid.CaptureFormat {
        val frontCamera = cameraEnumerator.deviceNames.first {
            cameraEnumerator.isFrontFacing(it)
        }
        val supportedResolution = cameraEnumerator.getSupportedFormats(frontCamera) ?: emptyList()
        return supportedResolution.firstOrNull {
            it.width == 1080 || it.width == 720 || it.width == 480 || it.width == 360
        } ?: error("There is no matched resolution!")
    }

    private fun buildAudioConstraints(): MediaConstraints {
        val mediaConstraints = MediaConstraints()
        val items = listOf(
            MediaConstraints.KeyValuePair(
                "googEchoCancellation",
                true.toString()
            ),
            MediaConstraints.KeyValuePair(
                "googAutoGainControl",
                true.toString()
            ),
            MediaConstraints.KeyValuePair(
                "googHighpassFilter",
                true.toString()
            ),
            MediaConstraints.KeyValuePair(
                "googNoiseSuppression",
                true.toString()
            ),
            MediaConstraints.KeyValuePair(
                "googTypingNoiseDetection",
                true.toString()
            )
        )

        return mediaConstraints.apply {
            with(optional) {
                add(MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"))
                addAll(items)
            }
        }
    }

    private fun setupAudio() {
        audioHandler.start()
        audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val devices = audioManager?.availableCommunicationDevices ?: return
            val deviceType = AudioDeviceInfo.TYPE_BUILTIN_SPEAKER

            val device = devices.firstOrNull { it.type == deviceType } ?: return

            val isCommunicationDeviceSet = audioManager?.setCommunicationDevice(device)
            Timber.d("[setupAudio] isCommunicationDeviceSet: $isCommunicationDeviceSet")
        }
    }
}