package com.vervan.chat.server

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * One device/program that has talked to the local API server this session, keyed by IP address.
 *
 * [userAgent] is whatever the client sent (a browser UA string, `OpenAI/Python 1.x`, `curl/8.x`,
 * or blank for something that sends none) — it's the only identity an HTTP client offers beyond
 * its address, and together they're enough to answer the question that actually matters: "is this
 * my laptop, or something I don't recognize?"
 */
data class ApiClientInfo(
    val address: String,
    val userAgent: String,
    val firstSeenAt: Long,
    val lastSeenAt: Long,
    val requestCount: Int,
    val lastPath: String,
    /** Requests this client made that were rejected for a missing/invalid key. A non-zero count on
     * an address you don't recognize is the signal worth acting on. */
    val unauthorizedCount: Int,
    /** True once any request from this address presented a valid key — always false while the key
     * requirement is off, since nothing was ever verified. */
    val authenticated: Boolean
) {
    /** Loopback means something on this phone itself (the bundled web UI opened locally, or another
     * app on the device); anything else arrived over the network. */
    val isLocal: Boolean get() = address == "127.0.0.1" || address == "::1" || address == "0:0:0:0:0:0:0:1"
}

/**
 * Live "who is talking to this server" view, owned by the app (not by [LocalApiServer]) so it
 * survives the server being restarted when a setting changes.
 *
 * Deliberately in-memory and session-scoped, like [com.vervan.chat.system.NetworkAuditLog]: this
 * exists to let the user see what is connected *right now*, not to build a durable log of who used
 * their phone — persisting client addresses across restarts would create a small tracking record
 * the app has no reason to keep.
 */
class ApiClientRegistry {
    private val _clients = MutableStateFlow<List<ApiClientInfo>>(emptyList())

    /** Most recently active first. */
    val clients: StateFlow<List<ApiClientInfo>> = _clients

    fun record(address: String, userAgent: String, path: String, authorized: Boolean, authChecked: Boolean) {
        val now = System.currentTimeMillis()
        val key = address.ifBlank { "unknown" }
        // CAS loop rather than read-then-write: NanoHTTPD serves each connection on its own thread,
        // so concurrent requests from two clients would otherwise lose one another's updates.
        _clients.update { current ->
            val existing = current.firstOrNull { it.address == key }
            val updated = if (existing == null) {
                ApiClientInfo(
                    address = key,
                    userAgent = userAgent.take(MAX_USER_AGENT_CHARS),
                    firstSeenAt = now,
                    lastSeenAt = now,
                    requestCount = 1,
                    lastPath = path.take(MAX_PATH_CHARS),
                    unauthorizedCount = if (authChecked && !authorized) 1 else 0,
                    authenticated = authChecked && authorized
                )
            } else {
                existing.copy(
                    // A client that changes its UA (a browser update, a different tool on the same
                    // machine) should show what it is *now*, not what it was first seen as.
                    userAgent = userAgent.ifBlank { existing.userAgent }.take(MAX_USER_AGENT_CHARS),
                    lastSeenAt = now,
                    requestCount = existing.requestCount + 1,
                    lastPath = path.take(MAX_PATH_CHARS),
                    unauthorizedCount = existing.unauthorizedCount + if (authChecked && !authorized) 1 else 0,
                    // Sticky: one authenticated request proves this client holds the key, and a
                    // later unauthenticated probe from the same address doesn't unprove it.
                    authenticated = existing.authenticated || (authChecked && authorized)
                )
            }
            (listOf(updated) + current.filterNot { it.address == key }).take(MAX_CLIENTS)
        }
    }

    fun clear() {
        _clients.value = emptyList()
    }

    companion object {
        // Bounded so a scanner hitting the port from many addresses can't grow this without limit.
        private const val MAX_CLIENTS = 25
        private const val MAX_USER_AGENT_CHARS = 120
        private const val MAX_PATH_CHARS = 80
    }
}
