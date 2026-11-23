package dev.opencab.core.data.throttle

import dev.opencab.core.data.connection.ConnectionRepository
import dev.opencab.core.data.connection.DccClient
import dev.opencab.core.model.ConnectionState
import dev.opencab.core.model.ConnectionTarget
import dev.opencab.core.model.RosterAddress
import dev.opencab.core.model.RosterAddressType
import dev.opencab.core.model.RosterLoco
import dev.opencab.core.protocol.LocoAddress
import dev.opencab.core.protocol.LocoId
import dev.opencab.core.protocol.ThrottleKey
import dev.opencab.core.protocol.WiThrottleCommand
import dev.opencab.core.protocol.WiThrottleEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ThrottleSessionTest {
    @Test
    fun attachesToTheActiveClientAndDetachesWhenTheSessionEnds() = runTest {
        val repository = FakeConnectionRepository()
        val client = FakeDccClient { testScheduler.currentTime }
        val session = ThrottleSession(
            connectionRepository = repository,
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
        )
        runCurrent()
        assertEquals(ThrottleLinkState.UNLINKED, session.controller.uiState.value.link)

        repository.setClient(client)
        runCurrent()
        assertEquals(ThrottleLinkState.DISCONNECTED, session.controller.uiState.value.link)

        client.emitState(connected())
        runCurrent()
        assertEquals(ThrottleLinkState.CONNECTED, session.controller.uiState.value.link)

        repository.setClient(null)
        runCurrent()
        assertEquals(ThrottleLinkState.UNLINKED, session.controller.uiState.value.link)
    }

    @Test
    fun duplicatePrimaryAcquisitionIsANoOp() = runTest {
        val repository = FakeConnectionRepository()
        val client = FakeDccClient { testScheduler.currentTime }
        val session = ThrottleSession(
            connectionRepository = repository,
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
        )
        repository.setClient(client)
        runCurrent()
        client.emitState(connected())
        runCurrent()
        val loco = rosterLoco(3, RosterAddressType.SHORT)
        val address = LocoAddress.short(3)

        assertEquals(PrimaryAcquireResult.REQUESTED, session.acquirePrimary(loco))
        runCurrent()
        client.emitEvent(WiThrottleEvent.LocoAdded(ThrottleKey.DEFAULT, LocoId.of(address)))
        runCurrent()
        val acquires = client.commands().filterIsInstance<WiThrottleCommand.Acquire>().size

        assertEquals(PrimaryAcquireResult.ALREADY_ACTIVE, session.acquirePrimary(loco))
        runCurrent()

        assertEquals(1, session.controller.uiState.value.slots.size)
        assertEquals(acquires, client.commands().filterIsInstance<WiThrottleCommand.Acquire>().size)
    }

    @Test
    fun selectingAnotherLocoReleasesThePreviousSlotFirst() = runTest {
        val repository = FakeConnectionRepository()
        val client = FakeDccClient { testScheduler.currentTime }
        val session = ThrottleSession(
            connectionRepository = repository,
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
        )
        repository.setClient(client)
        runCurrent()
        client.emitState(connected())
        runCurrent()
        val first = rosterLoco(3, RosterAddressType.SHORT)
        val second = rosterLoco(3412, RosterAddressType.LONG)
        session.acquirePrimary(first)
        runCurrent()
        client.emitEvent(
            WiThrottleEvent.LocoAdded(ThrottleKey.DEFAULT, LocoId.of(LocoAddress.short(3))),
        )
        runCurrent()

        assertEquals(PrimaryAcquireResult.REQUESTED, session.acquirePrimary(second))
        runCurrent()

        val tail = client.commands().takeLast(3)
        assertTrue(tail.first() is WiThrottleCommand.SetSpeed)
        assertTrue(tail[1] is WiThrottleCommand.Release)
        val acquire = tail.last() as WiThrottleCommand.Acquire
        assertEquals(LocoAddress.long(3412), acquire.locoKey)
        assertEquals(RosterAddress(3412, RosterAddressType.LONG), session.activeAddress())
    }

    private fun rosterLoco(
        number: Int,
        type: RosterAddressType,
    ): RosterLoco = RosterLoco(
        id = "loco-$number",
        name = "Loco $number",
        address = RosterAddress(number, type),
    )

    private class FakeConnectionRepository : ConnectionRepository {
        private val mutableState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
        override val state: StateFlow<ConnectionState> = mutableState.asStateFlow()

        private val mutableClient = MutableStateFlow<DccClient?>(null)
        override val activeClient: StateFlow<DccClient?> = mutableClient.asStateFlow()

        override fun connect(target: ConnectionTarget) {
            mutableState.value = ConnectionState.Connected(target)
        }

        override fun cancel() {
            mutableState.value = ConnectionState.Disconnected
        }

        override fun disconnect() {
            mutableState.value = ConnectionState.Disconnected
        }

        fun setClient(client: DccClient?) {
            mutableClient.value = client
        }
    }
}
