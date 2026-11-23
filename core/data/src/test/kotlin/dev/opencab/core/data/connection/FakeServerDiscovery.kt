package dev.opencab.core.data.connection

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Deterministic [ServerDiscovery] for tests that do not need a real mDNS stack.
 *
 * It records the timeout the caller requested and emits scripted snapshots. Collection is
 * cancellable like the real `NsdManager` implementation.
 */
internal class FakeServerDiscovery(
    private val snapshots: List<DiscoverySnapshot> = listOf(DiscoverySnapshot(finished = true)),
    private val failure: Throwable? = null,
) : ServerDiscovery {
    var scanCount: Int = 0
        private set

    var lastTimeoutMillis: Long? = null
        private set

    override fun scan(timeoutMillis: Long): Flow<DiscoverySnapshot> {
        scanCount += 1
        lastTimeoutMillis = timeoutMillis
        return flow {
            if (failure != null) throw failure
            snapshots.forEach { emit(it) }
        }
    }
}
