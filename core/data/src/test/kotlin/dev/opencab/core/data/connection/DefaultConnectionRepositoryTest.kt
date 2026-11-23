package dev.opencab.core.data.connection

import dev.opencab.core.model.ConnectionError
import dev.opencab.core.model.ConnectionState
import dev.opencab.core.model.ConnectionTarget
import dev.opencab.core.model.ServerEndpoint
import dev.opencab.core.protocol.ProtocolVersion
import dev.opencab.core.protocol.WiThrottleCommand
import dev.opencab.core.protocol.WiThrottleEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultConnectionRepositoryTest {
    private val endpoint = ServerEndpoint("192.168.1.24", 12090)
    private val target = ConnectionTarget("Junction Yard", endpoint)

    @Test
    fun mapsClientStatesIntoTheUserFacingLinkState() = runTest {
        val client = FakeDccClient()
        val repository = repository(client)

        repository.connect(target)
        assertTrue(repository.state.value is ConnectionState.Connecting)
        assertSame(client, repository.activeClient.value)

        client.emit(
            DccConnectionState.Connected(
                endpoint = endpoint,
                serverVersion = ProtocolVersion.V2_0,
                heartbeatIntervalMillis = 5_000,
            ),
        )
        assertTrue(repository.state.value is ConnectionState.Connected)

        client.emit(
            DccConnectionState.Reconnecting(
                endpoint = endpoint,
                attempt = 3,
                lastError = ConnectionError.ServerClosed,
                retryInMillis = 2_000,
            ),
        )
        val reconnecting = repository.state.value as ConnectionState.Reconnecting
        assertEquals(3, reconnecting.attempt)
        assertEquals(target, reconnecting.target)

        client.emit(DccConnectionState.Failed(endpoint, ConnectionError.TimedOut))
        val failed = repository.state.value as ConnectionState.Failed
        assertEquals(ConnectionError.TimedOut, failed.error)
        assertEquals(target, failed.target)

        repository.cancel()
        assertTrue(repository.state.value is ConnectionState.Disconnected)
        assertNull(repository.activeClient.value)
        assertTrue(client.closed)
    }

    @Test
    fun terminalFailureSurvivesTheClientReturning() = runTest {
        val client = FakeDccClient()
        val repository = repository(client)

        repository.connect(target)
        client.emit(DccConnectionState.Failed(endpoint, ConnectionError.ProtocolMismatch))
        client.finishConnect()

        val failed = repository.state.value as ConnectionState.Failed
        assertEquals(ConnectionError.ProtocolMismatch, failed.error)
        assertTrue(client.closed)
    }

    private fun TestScope.repository(client: FakeDccClient) = DefaultConnectionRepository(
        clientFactory = DccClientFactory { client },
        ioDispatcher = UnconfinedTestDispatcher(testScheduler),
    )

    private class FakeDccClient : DccClient {
        private val mutableState = MutableStateFlow<DccConnectionState>(DccConnectionState.Disconnected)
        override val state: StateFlow<DccConnectionState> = mutableState.asStateFlow()
        override val events: Flow<WiThrottleEvent> = emptyFlow()

        private val stop = CompletableDeferred<Unit>()
        var closed: Boolean = false
            private set

        override suspend fun connect(endpoint: ServerEndpoint) {
            stop.await()
        }

        override suspend fun send(command: WiThrottleCommand): Boolean = true

        override fun trySend(command: WiThrottleCommand): Boolean = true

        override fun close() {
            closed = true
            mutableState.value = DccConnectionState.Disconnected
            stop.complete(Unit)
        }

        fun emit(state: DccConnectionState) {
            mutableState.value = state
        }

        fun finishConnect() {
            stop.complete(Unit)
        }
    }
}
