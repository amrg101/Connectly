package com.amrg.connectly.ui.screens.video

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.amrg.connectly.R
import com.amrg.connectly.ui.components.VideoRenderer
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

        val sessionState = sessionManager.signalingClient.stateSessionFlow.collectAsState()

        var callMediaState by remember { mutableStateOf(CallMediaState()) }

        val context = LocalContext.current

        when (sessionState.value) {
            WebRTCSessionState.Impossible, WebRTCSessionState.Offline -> {
                Toast.makeText(context, stringResource(R.string.call_ended), Toast.LENGTH_SHORT)
                    .show()
                exitProcess(0)
            }

            else -> Unit
        }

        if (remoteVideoTrack != null) {
            VideoRenderer(
                videoTrack = remoteVideoTrack,
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { parentSize = it }
            )
        }

        if (localVideoTrack != null) {
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

        VideoCallControls(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            callMediaState = callMediaState,
            onCallAction = { action ->
                when (action) {
                    is CallAction.ToggleMicroPhone -> {
                        val enabled = callMediaState.isMicrophoneEnabled.not()
                        callMediaState = callMediaState.copy(isMicrophoneEnabled = enabled)
                        sessionManager.enableMicrophone(enabled)
                    }

                    is CallAction.ToggleCamera -> {
                        val enabled = callMediaState.isCameraEnabled.not()
                        callMediaState = callMediaState.copy(isCameraEnabled = enabled)
                        sessionManager.enableCamera(enabled)
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