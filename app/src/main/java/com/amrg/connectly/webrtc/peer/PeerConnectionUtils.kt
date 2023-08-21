package com.amrg.connectly.webrtc.peer

import org.webrtc.AddIceObserver
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

suspend inline fun createValue(
    crossinline call: (SdpObserver) -> Unit
): Result<SessionDescription> = suspendCoroutine {
    val sdpObserver = object : SdpObserver {
        override fun onCreateSuccess(description: SessionDescription?) {
            if (description != null) {
                it.resume(Result.success(description))
            } else {
                it.resume(Result.failure(RuntimeException("Can't create offer, SessionDescription is null")))
            }
        }

        override fun onCreateFailure(error: String?) {
            it.resume(Result.failure(RuntimeException(error)))

        }

        override fun onSetSuccess() = Unit
        override fun onSetFailure(error: String?) = Unit
    }
    call(sdpObserver)
}

suspend inline fun setValue(
    crossinline call: (SdpObserver) -> Unit
): Result<Unit> = suspendCoroutine {
    val sdpObserver = object : SdpObserver {
        override fun onCreateSuccess(description: SessionDescription?) = Unit
        override fun onCreateFailure(error: String?) = Unit

        override fun onSetSuccess() = it.resume(Result.success(Unit))
        override fun onSetFailure(error: String?) =
            it.resume(Result.failure(RuntimeException(error)))
    }
    call(sdpObserver)
}

suspend fun PeerConnection.addRtcIceCandidate(candidate: IceCandidate): Result<Unit> =
    suspendCoroutine {
        val addIceObserver = object : AddIceObserver {
            override fun onAddSuccess() {
                it.resume(Result.success(Unit))
            }

            override fun onAddFailure(error: String?) {
                it.resume(Result.failure(RuntimeException(error)))
            }
        }
        addIceCandidate(candidate, addIceObserver)
    }

fun String.mungeCodecs(): String {
    return this.replace("vp9", "VP9").replace("vp8", "VP8").replace("h264", "H264")
}