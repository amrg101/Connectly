package com.amrg.connectly.ui.screens.video

data class CallMediaState(
    val isCameraEnabled: Boolean = true,
    val isMicrophoneEnabled: Boolean = true
)

enum class RemoteCallState{
    CAMERA_STATE,
    MIC_STATE
}

enum class CameraState {
    ENABLED,
    DISABLED
}

enum class MicState {
    ENABLED,
    DISABLED
}
