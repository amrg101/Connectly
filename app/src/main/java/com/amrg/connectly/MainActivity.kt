package com.amrg.connectly

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.amrg.connectly.ui.screens.stage.StageScreen
import com.amrg.connectly.ui.screens.video.VideoCallScreen
import com.amrg.connectly.ui.theme.ConnectlyTheme
import com.amrg.connectly.webrtc.SignalingClient
import com.amrg.connectly.webrtc.peer.StreamPeerConnectionFactory
import com.amrg.connectly.webrtc.session.LocalWebRtcSessionManager
import com.amrg.connectly.webrtc.session.WebRtcSessionManager

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestPermissions(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO), 0)

        val sessionManager by lazy {
            WebRtcSessionManager(
                this,
                SignalingClient(),
                StreamPeerConnectionFactory(this)
            )
        }

        setContent {
            CompositionLocalProvider(LocalWebRtcSessionManager provides sessionManager) {
                ConnectlyTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        var onCallScreen by remember { mutableStateOf(false) }
                        val state by sessionManager.signalingClient.stateSessionFlow.collectAsState()

                        if (!onCallScreen) {
                            StageScreen(state = state) {
                                onCallScreen = true
                            }
                        } else {
                            VideoCallScreen()
                        }
                    }
                }
            }
        }
    }
}
