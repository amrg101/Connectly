package com.amrg.connectly.webrtc.peer

import kotlinx.serialization.Serializable

@Serializable
data class Message(
    val state: String,
    val value: String
)
