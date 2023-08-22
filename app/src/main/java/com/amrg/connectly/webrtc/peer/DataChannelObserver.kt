package com.amrg.connectly.webrtc.peer

import org.webrtc.DataChannel

class DataChannelObserver(
    private val onMessageListener: (buffer: DataChannel.Buffer) -> Unit
) : DataChannel.Observer {

    override fun onMessage(buffer: DataChannel.Buffer) {
        onMessageListener.invoke(buffer)
    }

    override fun onBufferedAmountChange(amout: Long) = Unit

    override fun onStateChange() = Unit
}