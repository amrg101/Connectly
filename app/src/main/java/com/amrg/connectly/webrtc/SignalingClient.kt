package com.amrg.connectly.webrtc

import com.amrg.connectly.shared.Constants.SIGNALING_SERVER_IP_ADDRESS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener

class SignalingClient {

    companion object {
        const val RTC_ADDRESS = "wss://$SIGNALING_SERVER_IP_ADDRESS/rtc"
    }

    private val signalingScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val client = OkHttpClient()
    private val request = Request.Builder()
        .url(RTC_ADDRESS)
        .build()

    private val ws = client.newWebSocket(request, SignalingWebSocketListener())

    private val _stateSessionFlow = MutableStateFlow(WebRTCSessionState.Offline)
    val stateSessionFlow: StateFlow<WebRTCSessionState> = _stateSessionFlow

    private val _stateCommandFlow = MutableSharedFlow<Pair<SignalingCommand, String>>()
    val stateCommandFlow: SharedFlow<Pair<SignalingCommand, String>> = _stateCommandFlow

    fun sendCommand(command: SignalingCommand, message: String) {
        ws.send("$command $message")
    }

    private inner class SignalingWebSocketListener : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
            super.onMessage(webSocket, text)
            when {
                text.startsWith(SignalingCommand.STATE.toString(), true) -> handleStateMessage(text)
                text.startsWith(SignalingCommand.OFFER.toString(), true) -> handleSignalingCommand(
                    SignalingCommand.OFFER,
                    text
                )

                text.startsWith(SignalingCommand.ANSWER.toString(), true) -> handleSignalingCommand(
                    SignalingCommand.ANSWER,
                    text
                )

                text.startsWith(SignalingCommand.ICE.toString(), true) -> handleSignalingCommand(
                    SignalingCommand.ICE,
                    text
                )
            }
        }
    }

    private fun handleSignalingCommand(command: SignalingCommand, message: String) {
        val value = getSeparatedMessage(message)
        signalingScope.launch {
            _stateCommandFlow.emit(command to value)
        }
    }

    private fun handleStateMessage(message: String) {
        val state = getSeparatedMessage(message)
        _stateSessionFlow.value = WebRTCSessionState.valueOf(state)
    }

    private fun getSeparatedMessage(text: String) = text.substringAfter(' ')

    fun dispose() {
        _stateSessionFlow.value = WebRTCSessionState.Offline
        signalingScope.cancel()
        ws.cancel()
    }
}

enum class SignalingCommand {
    STATE, // Command for WebRTCSessionState
    OFFER, // to send or receive offer
    ANSWER, // to send or receive answer
    ICE // to send and receive ice candidates
}

enum class WebRTCSessionState {
    Active, // Offer and Answer messages has been sent
    Creating, // Creating session, offer has been sent
    Ready, // Both clients available and ready to initiate session
    Impossible, // We have less than two clients connected to the server
    Offline // unable to connect signaling server
}