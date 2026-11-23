package dev.opencab.core.data.connection

import dev.opencab.core.model.ServerEndpoint
import dev.opencab.core.protocol.LocoAddress
import dev.opencab.core.protocol.ThrottleKey
import dev.opencab.core.protocol.WiThrottleCommand
import dev.opencab.core.protocol.WiThrottleEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DemoTransportTest {
    @Test
    fun connectServesAGreetingAndScriptedAcquireSpeedAndFunctions() = runTest {
        val transport = DemoTransport()
        val events = mutableListOf<WiThrottleEvent>()
        backgroundScope.launch { transport.events.collect { events += it } }
        val connectJob = launch { transport.connect(ServerEndpoint("demo")) }
        runCurrent()

        advanceTimeBy(DemoTransport.CONNECT_DELAY_MILLIS + 1)
        runCurrent()

        assertTrue(transport.state.value is DccConnectionState.Connected)
        assertTrue(events.any { it is WiThrottleEvent.Version })
        assertTrue(events.any { it is WiThrottleEvent.RosterList })

        val address = LocoAddress.long(3412)
        assertTrue(
            transport.send(
                WiThrottleCommand.Acquire(
                    throttleKey = ThrottleKey.DEFAULT,
                    locoKey = address,
                ),
            ),
        )
        runCurrent()
        assertTrue(events.any { it == WiThrottleEvent.LocoAdded(ThrottleKey.DEFAULT, address.toLocoId()) })

        assertTrue(
            transport.send(
                WiThrottleCommand.SetSpeed(
                    throttleKey = ThrottleKey.DEFAULT,
                    target = address.toLocoId(),
                    speed = 42,
                ),
            ),
        )
        runCurrent()
        assertTrue(
            events.any {
                it is WiThrottleEvent.SpeedChanged && it.speed == 42
            },
        )

        assertTrue(
            transport.send(
                WiThrottleCommand.PressFunction(
                    throttleKey = ThrottleKey.DEFAULT,
                    target = address.toLocoId(),
                    function = 1,
                ),
            ),
        )
        runCurrent()
        assertTrue(
            events.any {
                it is WiThrottleEvent.FunctionStateChanged && it.function == 1 && it.isOn
            },
        )

        transport.close()
        connectJob.join()
        assertTrue(transport.state.value is DccConnectionState.Disconnected)
        assertFalse(
            transport.send(
                WiThrottleCommand.SetSpeed(
                    throttleKey = ThrottleKey.DEFAULT,
                    target = address.toLocoId(),
                    speed = 0,
                ),
            ),
        )
    }

    @Test
    fun simulatedDropReconnectsAndReplaysTheGreeting() = runTest {
        val transport = DemoTransport()
        val states = mutableListOf<DccConnectionState>()
        backgroundScope.launch { transport.state.collect { states += it } }
        val connectJob = launch { transport.connect(ServerEndpoint("demo")) }
        runCurrent()
        advanceTimeBy(DemoTransport.CONNECT_DELAY_MILLIS + 1)
        runCurrent()
        assertTrue(transport.state.value is DccConnectionState.Connected)

        assertTrue(transport.simulateDisconnect())
        runCurrent()

        assertTrue(transport.state.value is DccConnectionState.Reconnecting)
        assertTrue(states.any { it is DccConnectionState.Reconnecting })

        advanceTimeBy(DemoTransport.RECONNECT_DELAY_MILLIS + 1)
        runCurrent()

        assertTrue(transport.state.value is DccConnectionState.Connected)
        assertEquals(
            2,
            states.count { it is DccConnectionState.Connected },
        )

        transport.close()
        connectJob.join()
    }

    @Test
    fun closeDuringConnectingLeavesNoJobBehind() = runTest {
        val transport = DemoTransport()
        val connectJob = launch { transport.connect(ServerEndpoint("demo")) }
        runCurrent()
        assertTrue(transport.state.value is DccConnectionState.Connecting)

        transport.close()
        connectJob.join()

        assertTrue(connectJob.isCompleted)
        assertTrue(transport.state.value is DccConnectionState.Disconnected)
    }
}

private fun LocoAddress.toLocoId(): dev.opencab.core.protocol.LocoId = dev.opencab.core.protocol.LocoId.of(this)
