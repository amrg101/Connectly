package com.amrg.connectly.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.amrg.connectly.webrtc.session.LocalWebRtcSessionManager
import org.webrtc.RendererCommon
import org.webrtc.VideoTrack

@Composable
fun VideoRenderer(
    videoTrack: VideoTrack,
    modifier: Modifier = Modifier
) {
    val trackState: MutableState<VideoTrack?> = remember { mutableStateOf(null) }
    var view: VideoTextureViewRenderer? by remember { mutableStateOf(null) }

    DisposableEffect(videoTrack) {
        onDispose {
            cleanTrack(trackState, view)
        }
    }

    val sessionManager = LocalWebRtcSessionManager.current
    AndroidView(
        factory = { context ->
            VideoTextureViewRenderer(context).apply {
                init(sessionManager.peerConnectionFactory.eglBaseContext,
                    object : RendererCommon.RendererEvents {
                        override fun onFirstFrameRendered() = Unit

                        override fun onFrameResolutionChanged(p0: Int, p1: Int, p2: Int) = Unit
                    }
                )
                setupTrack(trackState, videoTrack, this)
                view = this
            }
        },
        update = { v -> setupTrack(trackState, videoTrack, v) },
        modifier = modifier
    )
}

private fun setupTrack(
    trackState: MutableState<VideoTrack?>,
    videoTrack: VideoTrack,
    renderer: VideoTextureViewRenderer?
) {
    if (trackState.value == videoTrack) return
    cleanTrack(trackState, renderer)
    trackState.value = videoTrack
    videoTrack.addSink(renderer)
}

private fun cleanTrack(
    trackState: MutableState<VideoTrack?>,
    renderer: VideoTextureViewRenderer?
) {
    renderer.let { trackState.value?.removeSink(it) }
    trackState.value = null
}