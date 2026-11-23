package dev.opencab.core.model

import java.util.UUID

/** A WiThrottle server address. The port defaults to the protocol's well-known port. */
data class ServerEndpoint(
    val host: String,
    val port: Int = DEFAULT_PORT,
) {
    init {
        require(host.isNotBlank()) { "Host must not be blank" }
        require(host == host.trim()) { "Host must not contain surrounding whitespace" }
        require(!host.any { it.isWhitespace() }) { "Host must not contain whitespace" }
        require(!host.contains("://")) { "Host must not contain a scheme" }
        require(!host.contains('/')) { "Host must not contain a path" }
        require(host.length <= MAX_HOST_LENGTH) { "Host is too long: ${host.length}" }
        require(host.any { it.isLetterOrDigit() }) { "Host must contain a letter or digit" }
        require(host.count { it == ':' } != 1) { "Host must not include a port" }
        require(!host.contains("]:")) { "Host must not include a port" }
        require(port in MIN_PORT..MAX_PORT) { "Port out of range: $port" }
    }

    /** `host:port`, the only address form the UI shows. IPv6 literals are bracketed. */
    val display: String = host.removeSurrounding("[", "]").let { displayHost ->
        if (displayHost.contains(':')) "[$displayHost]:$port" else "$displayHost:$port"
    }

    companion object {
        const val DEFAULT_PORT: Int = 12090
        const val MIN_PORT: Int = 1
        const val MAX_PORT: Int = 65535
        const val MAX_HOST_LENGTH: Int = 253
    }
}

/** A server announced by mDNS on the local network. */
data class DiscoveredServer(
    val name: String,
    val endpoint: ServerEndpoint,
)

/** A server the operator has connected to before. */
data class SavedServer(
    val id: String,
    val name: String,
    val endpoint: ServerEndpoint,
    val lastConnectedEpochMillis: Long? = null,
) {
    init {
        require(id.isNotBlank()) { "Saved server id must not be blank" }
        require(name.isNotBlank()) { "Saved server name must not be blank" }
    }

    companion object {
        fun create(
            name: String,
            endpoint: ServerEndpoint,
            lastConnectedEpochMillis: Long? = null,
        ): SavedServer = SavedServer(
            id = UUID.randomUUID().toString(),
            name = name,
            endpoint = endpoint,
            lastConnectedEpochMillis = lastConnectedEpochMillis,
        )
    }
}

/** Whether a session is talking to a real layout or the built-in demo transport. */
enum class ConnectionMode {
    LIVE,
    DEMO,
}

/**
 * Everything needed to open a session, independent of where the endpoint came from.
 *
 * The UI passes this to the connection repository; it never sees a socket or protocol type.
 */
data class ConnectionTarget(
    val name: String,
    val endpoint: ServerEndpoint,
    val mode: ConnectionMode = ConnectionMode.LIVE,
) {
    init {
        require(name.isNotBlank()) { "Connection name must not be blank" }
    }

    companion object {
        const val DEMO_NAME: String = "Demo layout"

        fun demo(): ConnectionTarget = ConnectionTarget(
            name = DEMO_NAME,
            endpoint = ServerEndpoint(host = "demo", port = ServerEndpoint.DEFAULT_PORT),
            mode = ConnectionMode.DEMO,
        )
    }
}

/**
 * User-safe connection failures.
 *
 * These are domain values, not exception text. The UI maps each value to its own copy and a
 * recovery action, so a raw socket message can never reach the screen.
 */
sealed interface ConnectionError {
    /** The OS blocked local-network access for discovery or the socket. */
    data object LocalNetworkPermissionDenied : ConnectionError

    /** The address accepted the socket but never completed the WiThrottle greeting. */
    data object TimedOut : ConnectionError

    /** The host name did not resolve. */
    data object HostNotFound : ConnectionError

    /** The host resolved but the port refused the connection. */
    data object ConnectionRefused : ConnectionError

    /** No route to the layout, or the network is unavailable. */
    data object Unreachable : ConnectionError

    /** The server closed an established or half-open link. */
    data object ServerClosed : ConnectionError

    /** The server answered, but with an older WiThrottle version than OpenCab supports. */
    data class UnsupportedVersion(
        val found: String,
        val required: String = "2.0",
    ) : ConnectionError

    /** The socket answered with something that is not a WiThrottle greeting. */
    data object NotWiThrottle : ConnectionError

    /** The handshake was WiThrottle-shaped but contained an unrecognised message. */
    data object ProtocolMismatch : ConnectionError

    /** Anything else. The UI still offers a retry rather than showing the exception. */
    data object Unknown : ConnectionError
}

/**
 * High-level state of the connection between the app and a command station.
 *
 * The connection repository owns this state; composables only render it.
 */
sealed interface ConnectionState {
    data object Disconnected : ConnectionState

    data class Connecting(
        val target: ConnectionTarget,
    ) : ConnectionState

    data class Connected(
        val target: ConnectionTarget,
    ) : ConnectionState

    data class Reconnecting(
        val target: ConnectionTarget,
        val attempt: Int,
    ) : ConnectionState

    data class Failed(
        val target: ConnectionTarget,
        val error: ConnectionError,
    ) : ConnectionState
}
