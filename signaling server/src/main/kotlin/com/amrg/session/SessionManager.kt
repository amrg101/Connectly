package com.amrg.session

import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

object SessionManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()

    private val clients = mutableMapOf<UUID, DefaultWebSocketServerSession>()

    private var sessionState = WebRTCSessionState.Impossible

    fun onSessionStarted(sessionId: UUID, session: DefaultWebSocketServerSession){
        scope.launch {
            if (clients.size > 1){
                scope.launch(NonCancellable) {
                    session.send(Frame.Close())
                }
                return@launch
            }
            clients[sessionId] = session
            session.send("Added as a client: $sessionId")
            if (clients.size > 1){
                sessionState = WebRTCSessionState.Ready
            }
            notifyClientsUpdated()
        }
    }

    fun onMessage(sessionId: UUID, message: String){
        when {
            message.startsWith(MessageType.STATE.toString(), true) -> handleState(sessionId)
            message.startsWith(MessageType.OFFER.toString(), true) -> handleOffer(sessionId, message)
            message.startsWith(MessageType.ANSWER.toString(), true) -> handleAnswer(sessionId, message)
            message.startsWith(MessageType.ICE.toString(), true) -> handleIce(sessionId, message)
        }
    }

    private fun handleIce(sessionId: UUID, message: String) {
        println("handling ice from $sessionId")
        val clientsToSendIce= clients.filterKeys { it != sessionId }.values.first()
        clientsToSendIce.send(message)
    }

    private fun handleAnswer(sessionId: UUID, message: String) {
        if (sessionState != WebRTCSessionState.Creating){
            error("Session should be in Creating state to handle answer")
        }
        println("handling answer from $sessionId")
        val clientsToSendAnswer = clients.filterKeys { it != sessionId }.values.first()
        clientsToSendAnswer.send(message)
        sessionState = WebRTCSessionState.Active
        notifyClientsUpdated()
    }

    private fun handleOffer(sessionId: UUID, message: String) {
        if (sessionState != WebRTCSessionState.Ready){
            error("Should be ready for accepting the offer")
        }
        sessionState = WebRTCSessionState.Creating
        println("handling offer from $sessionId")
        notifyClientsUpdated()
        val clientsToSendOffer = clients.filterKeys { it != sessionId }.values.first()
        clientsToSendOffer.send(message)
    }

    private fun handleState(sessionId: UUID) {
        scope.launch {
            clients[sessionId]?.send("${MessageType.STATE} $sessionState")
        }
    }

    fun onSessionClose(sessionId: UUID){
        scope.launch {
            mutex.withLock {
                clients.remove(sessionId)
                sessionState = WebRTCSessionState.Impossible
                notifyClientsUpdated()
            }
        }
    }

    private fun notifyClientsUpdated(){
        clients.forEach { _, client ->
            client.send("${MessageType.STATE} $sessionState")
        }
    }
    private fun DefaultWebSocketServerSession.send(message: String){
        scope.launch {
            this@send.send(Frame.Text(message))
        }
    }
}

enum class WebRTCSessionState {
    Active, // Offer and Answer messages has been sent
    Creating, // Creating session, offer has been sent
    Ready, // Both clients available and ready to initiate session
    Impossible // We have less than two clients
}

enum class MessageType {
    STATE,
    OFFER,
    ANSWER,
    ICE
}
