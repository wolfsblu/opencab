package dev.opencab.core.data.throttle

import dev.opencab.core.data.connection.DccConnectionState
import dev.opencab.core.model.ConnectionError
import dev.opencab.core.protocol.LocoAddress
import dev.opencab.core.protocol.LocoId
import dev.opencab.core.protocol.ThrottleKey
import dev.opencab.core.protocol.TrackPowerState
import dev.opencab.core.protocol.WiThrottleCommand
import dev.opencab.core.protocol.WiThrottleEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ThrottleControllerFunctionTest {
    private val address = LocoAddress.long(341)

    @Test
    fun `labels and latching functions round-trip through the engine`() = runTest {
        val client = FakeDccClient { testScheduler.currentTime }
        val controller = controller(client)
        connect(client)
        acquire(controller, client, address)

        client.emitEvent(
            WiThrottleEvent.FunctionLabels(
                ThrottleKey.DEFAULT,
                LocoId.of(address),
                listOf("Headlight", "Bell", "Horn"),
            ),
        )
        runCurrent()

        val labelled = controller.uiState.value.slot(address)
        assertEquals(29, labelled?.functions?.size)
        assertEquals("Headlight", labelled?.function(0)?.label)
        assertEquals("Bell", labelled?.function(1)?.label)
        assertEquals("Horn", labelled?.function(2)?.label)
        assertEquals("F3", labelled?.function(3)?.label)

        controller.toggleFunction(address, 0)
        runCurrent()

        assertTrue(controller.uiState.value.slot(address)?.function(0)?.isOn ?: false)
        assertEquals(
            WiThrottleCommand.ForceFunction(ThrottleKey.DEFAULT, LocoId.of(address), 0, true),
            client.commands().last(),
        )

        controller.toggleFunction(address, 0)
        runCurrent()

        assertFalse(controller.uiState.value.slot(address)?.function(0)?.isOn ?: true)
        assertEquals(
            WiThrottleCommand.ForceFunction(ThrottleKey.DEFAULT, LocoId.of(address), 0, false),
            client.commands().last(),
        )
    }

    @Test
    fun `momentary functions pulse on and release automatically`() = runTest {
        val client = FakeDccClient { testScheduler.currentTime }
        val controller = controller(client, ThrottleConfig(momentaryPulseMillis = 250))
        connect(client)
        acquire(controller, client, address)
        client.emitEvent(
            WiThrottleEvent.FunctionMomentaryChanged(
                ThrottleKey.DEFAULT,
                LocoId.of(address),
                1,
                true,
            ),
        )
        runCurrent()

        controller.toggleFunction(address, 1)
        runCurrent()

        assertTrue(controller.uiState.value.slot(address)?.function(1)?.isOn ?: false)
        assertEquals(
            WiThrottleCommand.ForceFunction(ThrottleKey.DEFAULT, LocoId.of(address), 1, true),
            client.commands().last(),
        )

        advanceTimeBy(249)
        runCurrent()
        assertTrue(controller.uiState.value.slot(address)?.function(1)?.isOn ?: false)

        advanceTimeBy(1)
        runCurrent()
        assertFalse(controller.uiState.value.slot(address)?.function(1)?.isOn ?: true)
        assertEquals(
            WiThrottleCommand.ForceFunction(ThrottleKey.DEFAULT, LocoId.of(address), 1, false),
            client.commands().last(),
        )
    }

    @Test
    fun `stale and out-of-range functions ignore input`() = runTest {
        val client = FakeDccClient { testScheduler.currentTime }
        val controller = controller(client)
        connect(client)
        acquire(controller, client, address)

        controller.toggleFunction(address, 29)
        runCurrent()
        assertTrue(client.commands().none { it is WiThrottleCommand.ForceFunction })

        client.emitState(
            DccConnectionState.Reconnecting(
                endpoint = connected().endpoint,
                attempt = 1,
                lastError = ConnectionError.ServerClosed,
                retryInMillis = 500,
            ),
        )
        runCurrent()
        val before = client.attempts.size

        controller.toggleFunction(address, 0)
        runCurrent()

        assertEquals(before, client.attempts.size)
        assertFalse(controller.uiState.value.slot(address)?.function(0)?.isEnabled ?: true)
    }

    @Test
    fun `server function state wins and disables nothing while live`() = runTest {
        val client = FakeDccClient { testScheduler.currentTime }
        val controller = controller(client)
        connect(client)
        acquire(controller, client, address)

        client.emitEvent(
            WiThrottleEvent.FunctionStateChanged(
                ThrottleKey.DEFAULT,
                LocoId.of(address),
                2,
                true,
            ),
        )
        client.emitEvent(
            WiThrottleEvent.FunctionMomentaryChanged(
                ThrottleKey.DEFAULT,
                LocoId.of(address),
                2,
                true,
            ),
        )
        runCurrent()

        val function = controller.uiState.value.slot(address)?.function(2)
        assertTrue(function?.isOn ?: false)
        assertTrue(function?.isMomentary ?: false)
        assertTrue(function?.isEnabled ?: false)
    }

    @Test
    fun `track power and per-loco emergency stop stay independent`() = runTest {
        val client = FakeDccClient { testScheduler.currentTime }
        val controller = controller(client)
        connect(client)
        acquire(controller, client, address)

        client.emitEvent(WiThrottleEvent.TrackPower(TrackPowerState.OFF))
        runCurrent()

        assertEquals(TrackPowerState.OFF, controller.uiState.value.trackPower)
        assertFalse(controller.uiState.value.slot(address)?.emergencyStop ?: true)
        assertTrue(controller.uiState.value.slot(address)?.canControl ?: false)

        controller.emergencyStop(address)
        runCurrent()

        assertTrue(controller.uiState.value.slot(address)?.emergencyStop ?: false)
        assertEquals(TrackPowerState.OFF, controller.uiState.value.trackPower)
        assertTrue(client.commands().last() is WiThrottleCommand.EmergencyStop)

        client.emitEvent(WiThrottleEvent.TrackPower(TrackPowerState.ON))
        runCurrent()

        assertEquals(TrackPowerState.ON, controller.uiState.value.trackPower)
        assertTrue(controller.uiState.value.slot(address)?.emergencyStop ?: false)
    }

    @Test
    fun `nudge moves by whole steps in the configured mode`() = runTest {
        val client = FakeDccClient { testScheduler.currentTime }
        val controller = controller(client)
        connect(client)
        acquire(controller, client, address, speedSteps = SpeedSteps.FOURTEEN)

        controller.nudge(address, 1)
        runCurrent()

        val slot = controller.uiState.value.slot(address)
        assertEquals(1, slot?.step)
        assertEquals(1.0 / 14.0, slot?.speed ?: 0.0, 0.0001)
        assertEquals(9, client.commands().filterIsInstance<WiThrottleCommand.SetSpeed>().last().speed)

        controller.nudge(address, -1)
        runCurrent()
        assertEquals(0, controller.uiState.value.slot(address)?.step)
    }

    @Test
    fun `speed step mode changes are sent and echoed`() = runTest {
        val client = FakeDccClient { testScheduler.currentTime }
        val controller = controller(client)
        connect(client)
        acquire(controller, client, address)

        controller.setSpeedSteps(address, SpeedSteps.TWENTY_EIGHT)
        runCurrent()

        assertEquals(
            WiThrottleCommand.SetSpeedStepMode(
                ThrottleKey.DEFAULT,
                LocoId.of(address),
                SpeedSteps.TWENTY_EIGHT.protocolMode,
            ),
            client.commands().last(),
        )
        assertEquals(SpeedSteps.TWENTY_EIGHT, controller.uiState.value.slot(address)?.speedSteps)

        client.emitEvent(
            WiThrottleEvent.SpeedStepModeChanged(
                ThrottleKey.DEFAULT,
                LocoId.of(address),
                SpeedSteps.FOURTEEN.protocolMode,
            ),
        )
        runCurrent()

        assertEquals(SpeedSteps.FOURTEEN, controller.uiState.value.slot(address)?.speedSteps)
    }
}
