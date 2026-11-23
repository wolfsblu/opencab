package dev.opencab.core.data.throttle

import dev.opencab.core.protocol.LocoAddress
import dev.opencab.core.protocol.LocoId
import dev.opencab.core.protocol.ThrottleKey
import dev.opencab.core.protocol.WiThrottleCommand
import dev.opencab.core.protocol.WiThrottleEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ThrottleControllerSpeedTest {
    private val address = LocoAddress.long(341)

    @Test
    fun `speed writes are coalesced to ten hertz with the trailing value`() = runTest {
        val client = FakeDccClient { testScheduler.currentTime }
        val controller = controller(client)
        connect(client)
        acquire(controller, client, address)

        controller.setSpeed(address, 0.1)
        runCurrent()
        advanceTimeBy(50)
        controller.setSpeed(address, 0.2)
        runCurrent()
        controller.setSpeed(address, 0.3)
        runCurrent()
        advanceTimeBy(50)
        runCurrent()

        val speeds = client.commands().filterIsInstance<WiThrottleCommand.SetSpeed>()
        assertEquals(listOf(13, 38), speeds.map { it.speed })

        val speedAttempts = client.attempts.filter { it.command is WiThrottleCommand.SetSpeed }
        assertEquals(listOf(0L, 100L), speedAttempts.map { it.atMillis })
    }

    @Test
    fun `a burst ends with the final value even when intents stop`() = runTest {
        val client = FakeDccClient { testScheduler.currentTime }
        val controller = controller(client)
        connect(client)
        acquire(controller, client, address)

        controller.setSpeed(address, 0.1)
        runCurrent()
        advanceTimeBy(10)
        controller.setSpeed(address, 0.4)
        runCurrent()
        advanceTimeBy(10)
        controller.setSpeed(address, 0.7)
        runCurrent()
        advanceTimeBy(80)
        runCurrent()

        val speeds = client.commands().filterIsInstance<WiThrottleCommand.SetSpeed>()
        assertEquals(listOf(13, 88), speeds.map { it.speed })
    }

    @Test
    fun `stop bypasses coalescing and drops the pending value`() = runTest {
        val client = FakeDccClient { testScheduler.currentTime }
        val controller = controller(client)
        connect(client)
        acquire(controller, client, address)

        controller.setSpeed(address, 0.1)
        runCurrent()
        controller.setSpeed(address, 0.5)
        runCurrent()
        advanceTimeBy(20)
        controller.stop(address)
        runCurrent()

        val lastSpeed = client.commands().filterIsInstance<WiThrottleCommand.SetSpeed>().last()
        assertEquals(0, lastSpeed.speed)

        advanceTimeBy(500)
        runCurrent()
        assertTrue(
            client.commands().filterIsInstance<WiThrottleCommand.SetSpeed>().none { it.speed == 63 },
        )
    }

    @Test
    fun `emergency stop bypasses coalescing and wins over a pending speed`() = runTest {
        val client = FakeDccClient { testScheduler.currentTime }
        val controller = controller(client)
        connect(client)
        acquire(controller, client, address)

        controller.setSpeed(address, 0.1)
        runCurrent()
        controller.setSpeed(address, 0.9)
        runCurrent()
        advanceTimeBy(20)
        controller.emergencyStop(address)
        runCurrent()

        assertTrue(client.commands().last() is WiThrottleCommand.EmergencyStop)
        advanceTimeBy(500)
        runCurrent()
        assertTrue(
            client.commands().filterIsInstance<WiThrottleCommand.SetSpeed>().none { it.speed == 113 },
        )
        assertTrue(controller.uiState.value.slot(address)?.emergencyStop == true)
    }

    @Test
    fun `cancelling the engine never sends a trailing speed`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher)
        val client = FakeDccClient { testScheduler.currentTime }
        val controller = ThrottleController(scope, nowMillis = { testScheduler.currentTime })
        controller.link(client)
        runCurrent()
        connect(client)
        acquire(controller, client, address)

        controller.setSpeed(address, 0.1)
        runCurrent()
        controller.setSpeed(address, 0.8)
        runCurrent()
        scope.cancel()
        advanceTimeBy(500)
        runCurrent()

        assertTrue(
            client.commands().filterIsInstance<WiThrottleCommand.SetSpeed>().none { it.speed == 101 },
        )
    }

    @Test
    fun `server speed echo updates an idle slot`() = runTest {
        val client = FakeDccClient { testScheduler.currentTime }
        val controller = controller(client)
        connect(client)
        acquire(controller, client, address)

        client.emitEvent(
            WiThrottleEvent.SpeedChanged(
                ThrottleKey.DEFAULT,
                LocoId.of(address),
                63,
                emergencyStop = false,
            ),
        )
        runCurrent()

        assertEquals(0.5, controller.uiState.value.slot(address)?.speed ?: 0.0, 0.0001)
        assertFalse(controller.uiState.value.slot(address)?.emergencyStop ?: true)
    }

    @Test
    fun `closing the engine never sends a trailing speed`() = runTest {
        val client = FakeDccClient { testScheduler.currentTime }
        val controller = controller(client)
        connect(client)
        acquire(controller, client, address)
        controller.setSpeed(address, 0.1)
        runCurrent()
        controller.setSpeed(address, 0.7)
        runCurrent()

        controller.close()
        advanceTimeBy(500)
        runCurrent()

        assertTrue(
            client.commands().filterIsInstance<WiThrottleCommand.SetSpeed>().none { it.speed == 88 },
        )
    }

    @Test
    fun `set speed clamps values outside the normalized range`() = runTest {
        val client = FakeDccClient { testScheduler.currentTime }
        val controller = controller(client)
        connect(client)
        acquire(controller, client, address)

        controller.setSpeed(address, 2.0)
        runCurrent()

        val speed = client.commands().filterIsInstance<WiThrottleCommand.SetSpeed>().last()
        assertEquals(126, speed.speed)
        assertEquals(1.0, controller.uiState.value.slot(address)?.speed ?: 0.0, 0.0)
    }
}
