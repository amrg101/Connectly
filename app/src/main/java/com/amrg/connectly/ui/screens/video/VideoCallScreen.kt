package com.amrg.connectly.ui.screens.video

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.amrg.connectly.R
import com.amrg.connectly.ui.components.VideoRenderer
import com.amrg.connectly.ui.theme.Primary
import com.amrg.connectly.webrtc.WebRTCSessionState
import com.amrg.connectly.webrtc.session.LocalWebRtcSessionManager
import kotlin.system.exitProcess

@Composable
fun VideoCallScreen() {

    val sessionManager = LocalWebRtcSessionManager.current

    var localVideoShown by remember { mutableStateOf(true) }

    LaunchedEffect(key1 = Unit) {
        sessionManager.onSessionScreenReady()
    }

    LaunchedEffect(key1 = localVideoShown) {
        if (!localVideoShown){
            sessionManager.updateLocalVideoTrack()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        var parentSize: IntSize by remember { mutableStateOf(IntSize(0, 0)) }

        val remoteVideoTrackState by sessionManager.remoteVideoTrackFlow.collectAsState(null)
        val remoteVideoTrack = remoteVideoTrackState

        val localVideoTrackState by sessionManager.localVideoTrackFlow.collectAsState(null)
        val localVideoTrack = localVideoTrackState

        val remoteVideoTrackEnabledState by sessionManager.remoteVideoTrackEnabledState.collectAsState()
        val remoteAudioTrackEnabledState by sessionManager.remoteAudioTrackEnabledState.collectAsState()

        val sessionState by sessionManager.signalingClient.stateSessionFlow.collectAsState()

        var callMediaState by remember { mutableStateOf(CallMediaState()) }

        val context = LocalContext.current

        when (sessionState) {
            WebRTCSessionState.Impossible, WebRTCSessionState.Offline -> {
                Toast.makeText(context, stringResource(R.string.call_ended), Toast.LENGTH_SHORT)
                    .show()
                exitProcess(0)
            }
            else -> Unit
        }

        if (remoteVideoTrack != null && remoteVideoTrackEnabledState) {
            VideoRenderer(
                videoTrack = remoteVideoTrack,
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { parentSize = it }
            )
        } else {
            Box(modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { parentSize = it }
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_video_muted),
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

        if (localVideoTrack != null && callMediaState.isCameraEnabled) {
            FloatingVideoRenderer(
                modifier = Modifier
                    .size(width = 150.dp, height = 210.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .align(Alignment.TopEnd),
                videoTrack = localVideoTrack,
                parentBounds = parentSize,
                paddingValues = PaddingValues(0.dp)
            )
        } else {
            localVideoShown = false
        }

        if (!remoteAudioTrackEnabledState){
            Box(modifier = Modifier
                .padding(24.dp)
                .size(40.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_mute_mic),
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

        VideoCallControls(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            callMediaState = callMediaState,
            onCallAction = { action ->
                when (action) {
                    is CallAction.ToggleMicroPhone -> {
                        val enabled = callMediaState.isMicrophoneEnabled.not()
                        val micState = if (enabled) MicState.ENABLED.toString() else MicState.DISABLED.toString()
                        callMediaState = callMediaState.copy(isMicrophoneEnabled = enabled)
                        sessionManager.enableMicrophone(enabled)
                        sessionManager.sendData(RemoteCallState.MIC_STATE.name, micState)
                    }

                    is CallAction.ToggleCamera -> {
                        val enabled = callMediaState.isCameraEnabled.not()
                        val cameraState = if (enabled) CameraState.ENABLED.toString() else CameraState.DISABLED.toString()
                        callMediaState = callMediaState.copy(isCameraEnabled = enabled)
                        sessionManager.enableCamera(enabled)
                        sessionManager.sendData(RemoteCallState.CAMERA_STATE.name, cameraState)
                    }

                    CallAction.FlipCamera -> sessionManager.flipCamera()
                    CallAction.LeaveCall -> {
                        sessionManager.disconnect()
                        exitProcess(0)
                    }
                }
            })
    }

}