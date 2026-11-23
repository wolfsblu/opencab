package dev.opencab.core.data.connection

import dev.opencab.core.model.ConnectionError
import dev.opencab.core.model.DiscoveredServer
import kotlinx.coroutines.flow.Flow

/**
 * Progress of one mDNS browsing session.
 *
 * The flow stays active until the scan timeout elapses or the collector cancels. Results are
 * emitted progressively; [finished] marks the terminal snapshot.
 */
data class DiscoverySnapshot(
    val servers: List<DiscoveredServer> = emptyList(),
    val finished: Boolean = false,
    val error: ConnectionError? = null,
)

/** Best-effort local-network discovery. Manual entry is always a first-class fallback. */
interface ServerDiscovery {
    /**
     * Browses for the configured service. The flow is cancellable and ends no later than
     * [timeoutMillis]; the terminal snapshot has [DiscoverySnapshot.finished] set.
     */
    fun scan(timeoutMillis: Long = SCAN_TIMEOUT_MILLIS): Flow<DiscoverySnapshot>

    companion object {
        const val SERVICE_TYPE: String = "_withrottle._tcp"
        const val SCAN_TIMEOUT_MILLIS: Long = 8_000L
    }
}
