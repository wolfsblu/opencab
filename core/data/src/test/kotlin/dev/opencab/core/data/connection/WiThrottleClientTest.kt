package dev.opencab.core.data.connection

import dev.opencab.core.model.ConnectionError
import dev.opencab.core.protocol.LocoAddress
import dev.opencab.core.protocol.ThrottleKey
import dev.opencab.core.protocol.WiThrottleCommand
import dev.opencab.core.protocol.WiThrottleEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import kotlin.random.Random

class WiThrottleClientTest {
    @Test
    fun handshakeAndAcquireRoundTrip() = runBlocking {
        FakeTcpServer { connection ->
            connection.awaitLine("HUopencab-android")
            connection.awaitLine("NOpenCab")
            connection.send("VN2.0")
            connection.send("HTJMRI")
            connection.send("*10")
            connection.awaitLine("*+")

            val acquire = connection.awaitCommand("M")
            assertEquals("M0+L341<;>L341", acquire)
            connection.send("M0+L341<;>")
            connection.send("M0AL341<;>V0")
            connection.awaitClose()
        }.use { server ->
            val client = WiThrottleClient(testConfig())
            val events = Collections.synchronizedList(mutableListOf<WiThrottleEvent>())
            val collector = launchEvents(client, events)
            val connectJob = launch(Dispatchers.IO) { client.connect(server.endpoint) }

            withTimeout(TIMEOUT_MILLIS) {
                client.state.first { it is DccConnectionState.Connected }
            }

            assertTrue(
                client.send(
                    WiThrottleCommand.Acquire(
                        throttleKey = ThrottleKey.DEFAULT,
                        locoKey = LocoAddress.long(341),
                    ),
                ),
            )
            withTimeout(TIMEOUT_MILLIS) {
                while (events.none { it is WiThrottleEvent.LocoAdded }) delay(POLL_MILLIS)
            }
            assertTrue(events.any { it is WiThrottleEvent.Version })

            client.close()
            connectJob.join()
            collector.cancelAndJoin()
            assertTrue(client.state.value is DccConnectionState.Disconnected)
        }
    }

    @Test
    fun aPartialFrameDroppedAtEofIsNotEmittedAndTheLinkReconnects() = runBlocking {
        FakeTcpServer { connection ->
            connection.awaitLine("HUopencab-android")
            connection.awaitLine("NOpenCab")
            if (connection.index == 1) {
                connection.send("VN2.0")
                connection.sendRaw("M0AL341<;>V12")
                delay(50)
                connection.close()
            } else {
                delay(300)
                connection.send("VN2.0")
                connection.send("M0AL341<;>V0")
                connection.awaitClose()
            }
        }.use { server ->
            val client = WiThrottleClient(testConfig())
            val events = Collections.synchronizedList(mutableListOf<WiThrottleEvent>())
            val collector = launchEvents(client, events)
            val connectJob = launch(Dispatchers.IO) { client.connect(server.endpoint) }

            withTimeout(TIMEOUT_MILLIS) {
                client.state.first { it is DccConnectionState.Connected }
            }
            withTimeout(TIMEOUT_MILLIS) {
                client.state.first { it is DccConnectionState.Reconnecting }
            }
            withTimeout(TIMEOUT_MILLIS) {
                client.state.first { it is DccConnectionState.Connected }
            }
            withTimeout(TIMEOUT_MILLIS) {
                while (events.none { it is WiThrottleEvent.SpeedChanged && it.speed == 0 }) {
                    delay(POLL_MILLIS)
                }
            }

            assertFalse(
                events.any {
                    it is WiThrottleEvent.SpeedChanged && it.speed == 12
                },
            )

            client.close()
            connectJob.join()
            collector.cancelAndJoin()
        }
    }

    @Test
    fun aSilentServerTripsTheHeartbeatWatchdogAndReconnects() = runBlocking {
        FakeTcpServer { connection ->
            if (connection.index == 1) {
                connection.awaitLine("HUopencab-android")
                connection.awaitLine("NOpenCab")
                connection.send("VN2.0")
                connection.awaitClose()
            } else {
                delay(200)
                connection.send("VN2.0")
                connection.awaitClose()
            }
        }.use { server ->
            val client = WiThrottleClient(
                testConfig(
                    heartbeatIntervalMillis = 100,
                    heartbeatProbeTimeoutMillis = 200,
                ),
            )
            val connectJob = launch(Dispatchers.IO) { client.connect(server.endpoint) }

            withTimeout(TIMEOUT_MILLIS) {
                client.state.first { it is DccConnectionState.Connected }
            }
            val reconnecting = withTimeout(TIMEOUT_MILLIS) {
                client.state.first { it is DccConnectionState.Reconnecting }
            } as DccConnectionState.Reconnecting
            assertEquals(ConnectionError.TimedOut, reconnecting.lastError)

            withTimeout(TIMEOUT_MILLIS) {
                client.state.first {
                    it is DccConnectionState.Connected
                }
            }

            client.close()
            connectJob.join()
        }
    }

    @Test
    fun concurrentCloseIsIdempotent() = runBlocking {
        FakeTcpServer { connection ->
            connection.awaitLine("HUopencab-android")
            connection.awaitLine("NOpenCab")
            connection.send("VN2.0")
            connection.awaitClose()
        }.use { server ->
            val client = WiThrottleClient(testConfig())
            val connectJob = launch(Dispatchers.IO) { client.connect(server.endpoint) }
            withTimeout(TIMEOUT_MILLIS) {
                client.state.first { it is DccConnectionState.Connected }
            }

            repeat(16) {
                launch(Dispatchers.Default) { client.close() }
            }
            withTimeout(TIMEOUT_MILLIS) { connectJob.join() }

            assertTrue(client.state.value is DccConnectionState.Disconnected)
        }
    }

    @Test
    fun invalidHandshakeFailsWithProtocolMismatch() = runBlocking {
        FakeTcpServer { connection ->
            connection.awaitLine("HUopencab-android")
            connection.awaitLine("NOpenCab")
            connection.send("GARBAGE handshake")
            connection.awaitClose()
        }.use { server ->
            val client = WiThrottleClient(testConfig())
            val connectJob = launch(Dispatchers.IO) { client.connect(server.endpoint) }

            val failed = withTimeout(TIMEOUT_MILLIS) {
                client.state.first { it is DccConnectionState.Failed }
            } as DccConnectionState.Failed
            assertEquals(ConnectionError.ProtocolMismatch, failed.error)

            connectJob.join()
            val terminal = client.state.value as DccConnectionState.Failed
            assertEquals(ConnectionError.ProtocolMismatch, terminal.error)
        }
    }

    @Test
    fun oldServerVersionFailsWithUnsupportedVersion() = runBlocking {
        FakeTcpServer { connection ->
            connection.awaitLine("HUopencab-android")
            connection.awaitLine("NOpenCab")
            connection.send("VN1.6")
            connection.awaitClose()
        }.use { server ->
            val client = WiThrottleClient(testConfig())
            val connectJob = launch(Dispatchers.IO) { client.connect(server.endpoint) }

            val failed = withTimeout(TIMEOUT_MILLIS) {
                client.state.first { it is DccConnectionState.Failed }
            } as DccConnectionState.Failed
            assertEquals(ConnectionError.UnsupportedVersion(found = "1.6"), failed.error)

            connectJob.join()
            val terminal = client.state.value as DccConnectionState.Failed
            assertEquals(ConnectionError.UnsupportedVersion(found = "1.6"), terminal.error)
        }
    }

    @Test
    fun theClientCanBeReusedAfterClose() = runBlocking {
        FakeTcpServer { connection ->
            connection.awaitLine("HUopencab-android")
            connection.awaitLine("NOpenCab")
            connection.send("VN2.0")
            connection.awaitClose()
        }.use { server ->
            val client = WiThrottleClient(testConfig())

            val first = launch(Dispatchers.IO) { client.connect(server.endpoint) }
            withTimeout(TIMEOUT_MILLIS) {
                client.state.first { it is DccConnectionState.Connected }
            }
            client.close()
            first.join()

            val second = launch(Dispatchers.IO) { client.connect(server.endpoint) }
            withTimeout(TIMEOUT_MILLIS) {
                client.state.first { it is DccConnectionState.Connected }
            }
            assertTrue(server.connectionCount >= 2)
            client.close()
            second.join()
        }
    }

    @Test
    fun cancellingConnectClosesTheSocketAndTheSessionJob() = runBlocking {
        FakeTcpServer { connection ->
            connection.awaitLine("HUopencab-android")
            connection.awaitLine("NOpenCab")
            connection.send("VN2.0")
            connection.awaitClose()
        }.use { server ->
            val client = WiThrottleClient(testConfig())
            val connectJob = launch(Dispatchers.IO) { client.connect(server.endpoint) }
            withTimeout(TIMEOUT_MILLIS) {
                client.state.first { it is DccConnectionState.Connected }
            }

            connectJob.cancelAndJoin()

            assertTrue(connectJob.isCompleted)
            assertTrue(client.state.value is DccConnectionState.Disconnected)
        }
    }

    private fun CoroutineScope.launchEvents(
        client: DccClient,
        events: MutableList<WiThrottleEvent>,
    ) = launch(Dispatchers.Default) {
        client.events.collect { event -> events += event }
    }

    private fun testConfig(
        heartbeatIntervalMillis: Long = 10_000,
        heartbeatProbeTimeoutMillis: Long = 2_000,
    ): DccClientConfig = DccClientConfig(
        connectTimeoutMillis = 2_000,
        readTimeoutMillis = 50,
        greetingTimeoutMillis = 1_000,
        heartbeatIntervalMillis = heartbeatIntervalMillis,
        heartbeatSilenceMultiplier = 2.0,
        heartbeatProbeTimeoutMillis = heartbeatProbeTimeoutMillis,
        reconnectBaseMillis = 50,
        reconnectMaxMillis = 200,
        reconnectJitterFraction = 0.0,
        random = Random(0),
    )

    private companion object {
        const val TIMEOUT_MILLIS: Long = 5_000
        const val POLL_MILLIS: Long = 10
    }
}
