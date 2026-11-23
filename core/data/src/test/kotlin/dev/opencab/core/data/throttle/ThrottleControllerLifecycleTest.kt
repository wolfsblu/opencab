package dev.opencab.core.data.throttle

import dev.opencab.core.data.connection.DccConnectionState
import dev.opencab.core.model.ConnectionError
import dev.opencab.core.protocol.Direction
import dev.opencab.core.protocol.LocoAddress
import dev.opencab.core.protocol.LocoId
import dev.opencab.core.protocol.ThrottleKey
import dev.opencab.core.protocol.WiThrottleCommand
import dev.opencab.core.protocol.WiThrottleEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ThrottleControllerLifecycleTest {
    private val address = LocoAddress.long(341)
    private val other = LocoAddress.short(3)

    @Test
    fun `acquire becomes controllable only after the server grants the address`() = runTest {
        val client = FakeDccClient { testScheduler.currentTime }
        val controller = controller(client)
        connect(client)

        controller.acquire(address, name = "Big Boy")
        runCurrent()

        val requested = controller.uiState.value.slot(address)
        assertEquals(ThrottleAcquisitionState.REQUESTED, requested?.acquisition)
        assertFalse(requested?.canControl ?: true)
        assertEquals("Big Boy", requested?.name)
        assertEquals(address, (client.commands().last() as WiThrottleCommand.Acquire).locoKey)

        client.emitEvent(WiThrottleEvent.LocoAdded(ThrottleKey.DEFAULT, LocoId.of(address)))
        runCurrent()

        val acquired = controller.uiState.value.slot(address)
        assertEquals(ThrottleAcquisitionState.ACQUIRED, acquired?.acquisition)
        assertTrue(acquired?.canControl ?: false)
        assertTrue(acquired?.desired ?: false)
    }

    @Test
    fun `release stops before releasing and removes the slot on confirmation`() = runTest {
        val client = FakeDccClient { testScheduler.currentTime }
        val controller = controller(client)
        connect(client)
        acquire(controller, client, address)
        controller.setSpeed(address, 0.5)
        runCurrent()

        controller.release(address)
        runCurrent()

        val tail = client.commands().takeLast(2)
        assertEquals(WiThrottleCommand.SetSpeed(ThrottleKey.DEFAULT, LocoId.of(address), 0), tail[0])
        assertEquals(WiThrottleCommand.Release(ThrottleKey.DEFAULT, LocoId.of(address)), tail[1])
        assertEquals(
            ThrottleAcquisitionState.RELEASING,
            controller.uiState.value.slot(address)?.acquisition,
        )

        client.emitEvent(WiThrottleEvent.LocoRemoved(ThrottleKey.DEFAULT, LocoId.of(address)))
        runCurrent()

        assertNull(controller.uiState.value.slot(address))
    }

    @Test
    fun `disconnect keeps last known speed and reconnect reacquires then stops`() = runTest {
        val client = FakeDccClient { testScheduler.currentTime }
        val controller = controller(client)
        connect(client)
        acquire(controller, client, address)
        controller.setSpeed(address, 0.5)
        runCurrent()

        client.emitState(
            DccConnectionState.Reconnecting(
                endpoint = connected().endpoint,
                attempt = 2,
                lastError = ConnectionError.ServerClosed,
                retryInMillis = 1_000,
            ),
        )
        runCurrent()

        val stale = controller.uiState.value.slot(address)
        assertEquals(ThrottleLinkState.RECONNECTING, controller.uiState.value.link)
        assertEquals(ThrottleHealth.STALE, stale?.health)
        assertEquals(0.5, stale?.speed ?: 0.0, 0.0)
        assertFalse(stale?.canControl ?: true)

        controller.setSpeed(address, 0.2)
        runCurrent()
        assertTrue(
            client.commands().filterIsInstance<WiThrottleCommand.SetSpeed>().none { it.speed == 25 },
        )

        val acquiresBefore = client.commands().filterIsInstance<WiThrottleCommand.Acquire>().size
        client.emitState(connected())
        runCurrent()

        val reconnecting = controller.uiState.value
        assertEquals(ThrottleLinkState.CONNECTED, reconnecting.link)
        assertEquals(0.0, reconnecting.slot(address)?.speed ?: 1.0, 0.0)
        assertEquals(
            acquiresBefore + 1,
            client.commands().filterIsInstance<WiThrottleCommand.Acquire>().size,
        )

        client.emitEvent(WiThrottleEvent.LocoAdded(ThrottleKey.DEFAULT, LocoId.of(address)))
        runCurrent()

        val restored = controller.uiState.value.slot(address)
        assertEquals(ThrottleAcquisitionState.ACQUIRED, restored?.acquisition)
        assertTrue(restored?.desired ?: false)
        assertEquals(0, client.commands().filterIsInstance<WiThrottleCommand.SetSpeed>().last().speed)
    }

    @Test
    fun `conflict exposes in use and take control resolves it`() = runTest {
        val client = FakeDccClient { testScheduler.currentTime }
        val controller = controller(client)
        connect(client)
        controller.acquire(address)
        runCurrent()

        client.emitEvent(WiThrottleEvent.StealRequested(ThrottleKey.DEFAULT, LocoId.of(address)))
        runCurrent()

        val conflict = controller.uiState.value.slot(address)
        assertEquals(ThrottleAcquisitionState.IN_USE, conflict?.acquisition)
        assertTrue(conflict?.issue is ThrottleIssue.InUse)
        assertFalse(conflict?.canControl ?: true)

        controller.takeControl(address)
        runCurrent()

        assertTrue(client.commands().last() is WiThrottleCommand.Steal)
        assertEquals(
            ThrottleAcquisitionState.TAKING_CONTROL,
            controller.uiState.value.slot(address)?.acquisition,
        )

        client.emitEvent(WiThrottleEvent.LocoAdded(ThrottleKey.DEFAULT, LocoId.of(address)))
        runCurrent()

        val acquired = controller.uiState.value.slot(address)
        assertEquals(ThrottleAcquisitionState.ACQUIRED, acquired?.acquisition)
        assertNull(acquired?.issue)
    }

    @Test
    fun `one acquire timeout does not affect another slot`() = runTest {
        val client = FakeDccClient { testScheduler.currentTime }
        val controller = controller(client, ThrottleConfig(acquireTimeoutMillis = 1_000))
        connect(client)

        controller.acquire(address)
        controller.acquire(other)
        runCurrent()
        client.emitEvent(WiThrottleEvent.LocoAdded(ThrottleKey.DEFAULT, LocoId.of(other)))
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()

        val failed = controller.uiState.value.slot(address)
        val acquired = controller.uiState.value.slot(other)
        assertEquals(ThrottleAcquisitionState.FAILED, failed?.acquisition)
        assertTrue(failed?.issue is ThrottleIssue.AcquireTimedOut)
        assertEquals(ThrottleAcquisitionState.ACQUIRED, acquired?.acquisition)
        assertTrue(acquired?.canControl ?: false)
    }

    @Test
    fun `a timed out acquire can be retried while still connected`() = runTest {
        val client = FakeDccClient { testScheduler.currentTime }
        val controller = controller(client, ThrottleConfig(acquireTimeoutMillis = 500))
        connect(client)
        controller.acquire(address)
        runCurrent()
        advanceTimeBy(500)
        runCurrent()

        assertEquals(
            ThrottleAcquisitionState.FAILED,
            controller.uiState.value.slot(address)?.acquisition,
        )
        val acquiresBefore = client.commands().filterIsInstance<WiThrottleCommand.Acquire>().size

        controller.acquire(address)
        runCurrent()

        assertEquals(
            acquiresBefore + 1,
            client.commands().filterIsInstance<WiThrottleCommand.Acquire>().size,
        )
        assertEquals(
            ThrottleAcquisitionState.REQUESTED,
            controller.uiState.value.slot(address)?.acquisition,
        )

        client.emitEvent(WiThrottleEvent.LocoAdded(ThrottleKey.DEFAULT, LocoId.of(address)))
        runCurrent()
        assertEquals(
            ThrottleAcquisitionState.ACQUIRED,
            controller.uiState.value.slot(address)?.acquisition,
        )
    }

    @Test
    fun `linking to an already connected client reacquires desired slots`() = runTest {
        val first = FakeDccClient { testScheduler.currentTime }
        val controller = controller(first)
        controller.acquire(address)
        runCurrent()

        val second = FakeDccClient { testScheduler.currentTime }
        second.emitState(connected())
        controller.link(second)
        runCurrent()

        assertEquals(ThrottleLinkState.CONNECTED, controller.uiState.value.link)
        assertTrue(second.commands().any { it is WiThrottleCommand.Acquire })
    }

    @Test
    fun `a failed acquire leaves other slots usable`() = runTest {
        val client = FakeDccClient { testScheduler.currentTime }
        client.sendPolicy = {
            !(it is WiThrottleCommand.Acquire && it.locoKey == address)
        }
        val controller = controller(client)
        connect(client)

        controller.acquire(address)
        controller.acquire(other)
        runCurrent()
        client.emitEvent(WiThrottleEvent.LocoAdded(ThrottleKey.DEFAULT, LocoId.of(other)))
        runCurrent()

        val failed = controller.uiState.value.slot(address)
        val acquired = controller.uiState.value.slot(other)
        assertEquals(ThrottleAcquisitionState.FAILED, failed?.acquisition)
        assertTrue(failed?.issue is ThrottleIssue.CommandFailed)
        assertEquals(ThrottleAcquisitionState.ACQUIRED, acquired?.acquisition)
        assertTrue(acquired?.canControl ?: false)
    }

    @Test
    fun `release cancels a pending coalesced speed`() = runTest {
        val client = FakeDccClient { testScheduler.currentTime }
        val controller = controller(client)
        connect(client)
        acquire(controller, client, address)
        controller.setSpeed(address, 0.1)
        runCurrent()
        controller.setSpeed(address, 0.7)
        runCurrent()

        controller.release(address)
        runCurrent()
        advanceTimeBy(500)
        runCurrent()

        assertTrue(
            client.commands().filterIsInstance<WiThrottleCommand.SetSpeed>().none { it.speed == 88 },
        )
    }

    @Test
    fun `release cancels a pending steal without taking control`() = runTest {
        val client = FakeDccClient { testScheduler.currentTime }
        val controller = controller(client)
        connect(client)
        controller.acquire(address)
        runCurrent()
        client.emitEvent(WiThrottleEvent.StealRequested(ThrottleKey.DEFAULT, LocoId.of(address)))
        runCurrent()

        controller.release(address)
        runCurrent()

        assertNull(controller.uiState.value.slot(address))
        assertTrue(client.commands().none { it is WiThrottleCommand.Steal })
    }

    @Test
    fun `unlink keeps desired slots and relinking reacquires them`() = runTest {
        val first = FakeDccClient { testScheduler.currentTime }
        val controller = controller(first)
        connect(first)
        acquire(controller, first, address)

        controller.unlink()
        runCurrent()

        val unlinked = controller.uiState.value
        assertEquals(ThrottleLinkState.UNLINKED, unlinked.link)
        assertEquals(ThrottleHealth.OFFLINE, unlinked.slot(address)?.health)
        assertFalse(unlinked.slot(address)?.canControl ?: true)
        assertTrue(unlinked.slot(address)?.desired ?: false)

        val second = FakeDccClient { testScheduler.currentTime }
        controller.link(second)
        runCurrent()
        connect(second)

        assertTrue(second.commands().any { it is WiThrottleCommand.Acquire })
        assertEquals(
            ThrottleAcquisitionState.REQUESTED,
            controller.uiState.value.slot(address)?.acquisition,
        )
    }

    @Test
    fun `direction changes are not coalesced with speed`() = runTest {
        val client = FakeDccClient { testScheduler.currentTime }
        val controller = controller(client)
        connect(client)
        acquire(controller, client, address)
        controller.setSpeed(address, 0.4)
        runCurrent()

        controller.setDirection(address, Direction.REVERSE)
        runCurrent()

        assertEquals(
            WiThrottleCommand.SetDirection(ThrottleKey.DEFAULT, LocoId.of(address), Direction.REVERSE),
            client.commands().last(),
        )
        assertEquals(
            Direction.REVERSE,
            controller.uiState.value.slot(address)?.direction,
        )
    }
}
