package io.wsign.signing.internal

import io.wsign.signing.SigningEvent
import io.wsign.signing.SigningEventListener
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Connects the event listener given to `WsignSigning.Contract` with the signing activity it starts. Only a random
 * token travels in the intent; the listener stays in memory until the session ends.
 */
internal object SigningEventBus {
    private val listeners = ConcurrentHashMap<String, SigningEventListener>()

    fun register(listener: SigningEventListener): String {
        val token = UUID.randomUUID().toString()
        listeners[token] = listener
        return token
    }

    fun unregister(token: String) {
        listeners.remove(token)
    }

    fun dispatch(token: String?, event: SigningEvent) {
        if (token == null) return
        listeners[token]?.onSigningEvent(event)
    }

    internal fun isRegistered(token: String): Boolean = listeners.containsKey(token)
}
