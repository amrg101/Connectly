package com.amrg.connectly.ui.screens.stage

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.amrg.connectly.R
import com.amrg.connectly.webrtc.WebRTCSessionState

@Composable
fun StageScreen(
    state: WebRTCSessionState,
    onJoinCall: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        var enabledCall by remember { mutableStateOf(false) }

        val text = when (state) {
            WebRTCSessionState.Offline -> {
                enabledCall = false
                stringResource(id = R.string.button_start_session)
            }

            WebRTCSessionState.Impossible -> {
                enabledCall = false
                stringResource(id = R.string.session_impossible)
            }

            WebRTCSessionState.Ready -> {
                enabledCall = true
                stringResource(id = R.string.session_ready)
            }

            WebRTCSessionState.Creating -> {
                enabledCall = true
                stringResource(id = R.string.session_creating)
                stringResource(id = R.string.session_accept)
            }

            WebRTCSessionState.Active -> {
                enabledCall = false
                stringResource(id = R.string.session_active)
            }
        }

        Button(
            modifier = Modifier.align(Alignment.Center),
            enabled = enabledCall,
            onClick = { onJoinCall.invoke() }
        ) {
            Text(
                text = text,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}