package com.amrg.plugins

import com.amrg.session.SessionManager
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import java.time.Duration
import java.util.UUID

fun Application.configureSockets() {
    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }
    routing {
        webSocket("/rtc") { // websocketSession
            val sessionId = UUID.randomUUID()
            try {
                SessionManager.onSessionStarted(sessionId, this)
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val text = frame.readText()
                        SessionManager.onMessage(sessionId, text)
                    }
                }
                println("Exiting incoming loop, closing session: $sessionId")
                SessionManager.onSessionClose(sessionId)
            } catch (e: ClosedReceiveChannelException){
                println("onClose $sessionId")
                SessionManager.onSessionClose(sessionId)
            } catch (e: Throwable){
                println("onError $sessionId")
                SessionManager.onSessionClose(sessionId)
            }
        }
    }
}
