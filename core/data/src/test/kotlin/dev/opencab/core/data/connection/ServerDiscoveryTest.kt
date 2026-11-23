package dev.opencab.core.data.connection

import dev.opencab.core.model.DiscoveredServer
import dev.opencab.core.model.ServerEndpoint
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ServerDiscoveryTest {
    @Test
    fun fakeUsesTheDefaultTimeoutAndEmitsProgressively() = runTest {
        val server = DiscoveredServer("Junction Yard", ServerEndpoint("192.168.1.24"))
        val discovery = FakeServerDiscovery(
            listOf(
                DiscoverySnapshot(servers = listOf(server), finished = false),
                DiscoverySnapshot(servers = listOf(server), finished = true),
            ),
        )

        val snapshots = discovery.scan().toList()

        assertEquals(ServerDiscovery.SCAN_TIMEOUT_MILLIS, discovery.lastTimeoutMillis ?: -1)
        assertEquals(2, snapshots.size)
        assertEquals(server, snapshots.first().servers.single())
        assertEquals(true, snapshots.last().finished)
    }

    @Test
    fun collectingTheScanCanBeCancelled() = runTest {
        val discovery = FakeServerDiscovery(
            listOf(DiscoverySnapshot(finished = true)),
        )
        val job = launch { discovery.scan(1_000).collect { } }
        runCurrent()
        job.cancel()
        job.join()
    }
}
