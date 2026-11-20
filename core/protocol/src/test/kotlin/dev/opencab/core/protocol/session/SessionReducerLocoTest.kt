package dev.opencab.core.protocol.session

import dev.opencab.core.protocol.ConsistInfo
import dev.opencab.core.protocol.ConsistMember
import dev.opencab.core.protocol.Direction
import dev.opencab.core.protocol.LocoAddress
import dev.opencab.core.protocol.LocoId
import dev.opencab.core.protocol.ProtocolVersion
import dev.opencab.core.protocol.RosterLoco
import dev.opencab.core.protocol.SpeedStepMode
import dev.opencab.core.protocol.ThrottleKey
import dev.opencab.core.protocol.WiThrottleCommand
import dev.opencab.core.protocol.WiThrottleEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionReducerLocoTest {
    private val address = LocoAddress.short(3)
    private val longAddress = LocoAddress.long(341)
    private val key = ThrottleKey.DEFAULT
    private val target = LocoId.of(address)

    @Test
    fun `broadcast loco add is ignored and broadcast removal clears every slot`() {
        val reducer = ready()
        assertTrue(reducer.send(WiThrottleEvent.LocoAdded(key, LocoId.All), 2).isEmpty())
        assertTrue(reducer.state.locos.isEmpty())

        reducer.acquired(address, now = 10)
        reducer.acquired(longAddress, now = 20)
        assertTrue(reducer.send(WiThrottleEvent.LocoRemoved(key, LocoId.All), 30).isEmpty())

        assertEquals(LocoStatus.LOST, reducer.state.loco(address)?.status)
        assertEquals(LocoStatus.LOST, reducer.state.loco(longAddress)?.status)
    }

    @Test
    fun `release sends stop then release and removal clears the slot`() {
        val reducer = ready()
        reducer.acquired(address)
        reducer.send(SessionIntent.SetSpeed(address, 30), 20)

        val releaseCommands = reducer.send(SessionIntent.Release(address), 30)

        assertEquals(
            listOf(
                WiThrottleCommand.SetSpeed(key, target, 0),
                WiThrottleCommand.Release(key, target),
            ),
            releaseCommands,
        )
        assertEquals(LocoStatus.RELEASING, reducer.state.loco(address)?.status)
        assertFalse(reducer.state.loco(address)?.desired ?: true)
        assertTrue(reducer.send(SessionIntent.Release(address), 31).isEmpty())

        assertTrue(reducer.send(WiThrottleEvent.LocoRemoved(key, target), 32).isEmpty())
        assertNull(reducer.state.loco(address))
        assertTrue(reducer.send(SessionIntent.Release(address), 33).isEmpty())
    }

    @Test
    fun `release of a requested loco sends only the release`() {
        val reducer = ready()
        reducer.send(SessionIntent.Acquire(address), 2)

        assertEquals(
            listOf(WiThrottleCommand.Release(key, target)),
            reducer.send(SessionIntent.Release(address), 3),
        )
    }

    @Test
    fun `unexpected removal marks the slot lost and reconnect reacquires it`() {
        val reducer = ready()
        reducer.acquired(address)

        assertTrue(reducer.send(WiThrottleEvent.LocoRemoved(key, target), 20).isEmpty())
        assertEquals(LocoStatus.LOST, reducer.state.loco(address)?.status)
        assertTrue(reducer.state.loco(address)?.desired == true)

        assertTrue(reducer.handle(SessionInput.LinkDown(21)).isEmpty())
        assertEquals(LinkState.DISCONNECTED, reducer.state.link)
        assertEquals(LocoStatus.LOST, reducer.state.loco(address)?.status)

        assertEquals(
            listOf(
                WiThrottleCommand.SetHardwareId("opencab"),
                WiThrottleCommand.SetDeviceName("OpenCab"),
            ),
            reducer.handle(SessionInput.LinkUp(22)),
        )
        assertEquals(
            listOf(WiThrottleCommand.Acquire(key, address)),
            reducer.send(WiThrottleEvent.Version(ProtocolVersion.V2_0), 23),
        )
        assertEquals(LocoStatus.REQUESTED, reducer.state.loco(address)?.status)
    }

    @Test
    fun `released locos are not reacquired after reconnect`() {
        val reducer = ready()
        reducer.acquired(address)
        reducer.send(SessionIntent.Release(address), 20)
        reducer.send(WiThrottleEvent.LocoRemoved(key, target), 21)

        reducer.handle(SessionInput.LinkDown(22))
        reducer.handle(SessionInput.LinkUp(23))

        assertTrue(reducer.send(WiThrottleEvent.Version(ProtocolVersion.V2_0), 24).isEmpty())
    }

    @Test
    fun `link loss degrades live and commanded speed to last known`() {
        val reducer = ready()
        reducer.acquired(address)
        reducer.send(SessionIntent.SetSpeed(address, 30), 20)
        assertEquals(SpeedSource.COMMANDED, reducer.state.loco(address)?.speedSource)

        reducer.send(WiThrottleEvent.SpeedChanged(key, target, 25, false), 21)
        assertEquals(SpeedSource.LIVE, reducer.state.loco(address)?.speedSource)

        reducer.handle(SessionInput.LinkDown(22))

        val slot = reducer.state.loco(address)
        assertEquals(LocoStatus.LOST, slot?.status)
        assertEquals(SpeedSource.LAST_KNOWN, slot?.speedSource)
        assertEquals(25, slot?.speed)
        assertEquals(HandshakeState.Idle, reducer.state.handshake)
        assertTrue(reducer.handle(SessionInput.LinkDown(23)).isEmpty())
    }

    @Test
    fun `steal prompt and confirmation follow the documented sequence`() {
        val reducer = ready()
        reducer.send(SessionIntent.Acquire(longAddress), 2)

        assertTrue(reducer.send(WiThrottleEvent.StealRequested(key, LocoId.of(longAddress)), 3).isEmpty())
        assertEquals(LocoStatus.STEAL_PENDING, reducer.state.loco(longAddress)?.status)
        assertTrue(reducer.state.loco(longAddress)?.desired == true)

        assertEquals(
            listOf(WiThrottleCommand.Steal(key, longAddress)),
            reducer.send(SessionIntent.ConfirmSteal(longAddress), 4),
        )
        assertEquals(LocoStatus.STEALING, reducer.state.loco(longAddress)?.status)
        assertTrue(reducer.send(SessionIntent.ConfirmSteal(longAddress), 5).isEmpty())

        assertTrue(reducer.send(WiThrottleEvent.LocoAdded(key, LocoId.of(longAddress)), 6).isEmpty())
        assertEquals(LocoStatus.ACQUIRED, reducer.state.loco(longAddress)?.status)
        assertTrue(reducer.send(WiThrottleEvent.StealRequested(key, LocoId.of(longAddress)), 7).isEmpty())
        assertEquals(LocoStatus.ACQUIRED, reducer.state.loco(longAddress)?.status)
    }

    @Test
    fun `release cancels a pending steal without commands`() {
        val reducer = ready()
        reducer.send(SessionIntent.Acquire(longAddress), 2)
        reducer.send(WiThrottleEvent.StealRequested(key, LocoId.of(longAddress)), 3)

        assertTrue(reducer.send(SessionIntent.Release(longAddress), 4).isEmpty())
        assertNull(reducer.state.loco(longAddress))
    }

    @Test
    fun `speed commands are optimistic and adopt server echoes`() {
        val reducer = ready()
        reducer.acquired(address)

        assertEquals(
            listOf(WiThrottleCommand.SetSpeed(key, target, 30)),
            reducer.send(SessionIntent.SetSpeed(address, 30), 20),
        )
        assertEquals(30, reducer.state.loco(address)?.speed)
        assertEquals(SpeedSource.COMMANDED, reducer.state.loco(address)?.speedSource)

        reducer.send(WiThrottleEvent.SpeedChanged(key, target, 25, false), 21)
        assertEquals(25, reducer.state.loco(address)?.speed)
        assertEquals(SpeedSource.LIVE, reducer.state.loco(address)?.speedSource)

        assertTrue(reducer.send(SessionIntent.SetSpeed(address, 25), 22).isEmpty())
        assertEquals(
            listOf(WiThrottleCommand.SetSpeed(key, target, 0)),
            reducer.send(SessionIntent.SetSpeed(address, 0), 23),
        )
    }

    @Test
    fun `invalid speed is ignored and server emergency stop is recorded`() {
        val reducer = ready()
        reducer.acquired(address)

        assertTrue(reducer.send(SessionIntent.SetSpeed(address, 127), 20).isEmpty())
        assertEquals(0, reducer.state.loco(address)?.speed)

        reducer.send(WiThrottleEvent.SpeedChanged(key, target, 0, true), 21)
        assertTrue(reducer.state.loco(address)?.emergencyStop == true)
        assertEquals(SpeedSource.LIVE, reducer.state.loco(address)?.speedSource)

        assertEquals(
            listOf(WiThrottleCommand.EmergencyStop(key, target)),
            reducer.send(SessionIntent.EmergencyStop(address), 22),
        )
        assertTrue(reducer.state.loco(address)?.emergencyStop == true)
        assertEquals(SpeedSource.COMMANDED, reducer.state.loco(address)?.speedSource)
    }

    @Test
    fun `broadcast speed and function events apply to every slot`() {
        val reducer = ready()
        reducer.acquired(address, now = 10)
        reducer.acquired(longAddress, now = 20)

        reducer.send(WiThrottleEvent.SpeedChanged(key, LocoId.All, 42, false), 30)
        reducer.send(WiThrottleEvent.FunctionStateChanged(key, LocoId.All, 2, true), 31)

        assertEquals(42, reducer.state.loco(address)?.speed)
        assertEquals(42, reducer.state.loco(longAddress)?.speed)
        assertTrue(reducer.state.loco(address)?.function(2)?.isOn == true)
        assertTrue(reducer.state.loco(longAddress)?.function(2)?.isOn == true)
    }

    @Test
    fun `direction and speed step mode commands update state`() {
        val reducer = ready()
        reducer.acquired(address)

        assertEquals(
            listOf(WiThrottleCommand.SetDirection(key, target, Direction.REVERSE)),
            reducer.send(SessionIntent.SetDirection(address, Direction.REVERSE), 20),
        )
        assertEquals(Direction.REVERSE, reducer.state.loco(address)?.direction)
        assertTrue(reducer.send(SessionIntent.SetDirection(address, Direction.REVERSE), 21).isEmpty())

        assertEquals(
            listOf(WiThrottleCommand.SetSpeedStepMode(key, target, SpeedStepMode.NmraDcc28)),
            reducer.send(SessionIntent.SetSpeedStepMode(address, SpeedStepMode.NmraDcc28), 22),
        )
        assertEquals(SpeedStepMode.NmraDcc28, reducer.state.loco(address)?.speedStepMode)
        assertTrue(reducer.send(SessionIntent.SetSpeedStepMode(address, SpeedStepMode.NmraDcc28), 23).isEmpty())

        reducer.send(WiThrottleEvent.DirectionChanged(key, target, Direction.FORWARD), 24)
        reducer.send(WiThrottleEvent.SpeedStepModeChanged(key, target, SpeedStepMode.NmraDcc128), 25)
        assertEquals(Direction.FORWARD, reducer.state.loco(address)?.direction)
        assertEquals(SpeedStepMode.NmraDcc128, reducer.state.loco(address)?.speedStepMode)
    }

    @Test
    fun `latching function presses toggle optimistically`() {
        val reducer = ready()
        reducer.acquired(address)

        assertEquals(
            listOf(WiThrottleCommand.PressFunction(key, target, 1)),
            reducer.send(SessionIntent.PressFunction(address, 1), 20),
        )
        assertTrue(reducer.state.loco(address)?.function(1)?.isOn == true)

        assertEquals(
            listOf(WiThrottleCommand.PressFunction(key, target, 1)),
            reducer.send(SessionIntent.PressFunction(address, 1), 21),
        )
        assertFalse(reducer.state.loco(address)?.function(1)?.isOn ?: true)
    }

    @Test
    fun `momentary functions turn on while pressed and off on release`() {
        val reducer = ready()
        reducer.acquired(address)

        assertEquals(
            listOf(WiThrottleCommand.SetFunctionMomentary(key, target, 1, true)),
            reducer.send(SessionIntent.SetFunctionMomentary(address, 1, true), 20),
        )
        assertTrue(reducer.state.loco(address)?.function(1)?.isMomentary == true)

        assertEquals(
            listOf(WiThrottleCommand.PressFunction(key, target, 1)),
            reducer.send(SessionIntent.PressFunction(address, 1), 21),
        )
        assertTrue(reducer.state.loco(address)?.function(1)?.isOn == true)

        assertEquals(
            listOf(WiThrottleCommand.ReleaseFunction(key, target, 1)),
            reducer.send(SessionIntent.ReleaseFunction(address, 1), 22),
        )
        assertFalse(reducer.state.loco(address)?.function(1)?.isOn ?: true)
    }

    @Test
    fun `forced functions and server function events update absolute state`() {
        val reducer = ready()
        reducer.acquired(address)

        assertEquals(
            listOf(WiThrottleCommand.ForceFunction(key, target, 5, true)),
            reducer.send(SessionIntent.ForceFunction(address, 5, true), 20),
        )
        assertTrue(reducer.state.loco(address)?.function(5)?.isOn == true)

        reducer.send(WiThrottleEvent.FunctionStateChanged(key, target, 5, false), 21)
        reducer.send(WiThrottleEvent.FunctionMomentaryChanged(key, target, 5, true), 22)
        assertFalse(reducer.state.loco(address)?.function(5)?.isOn ?: true)
        assertTrue(reducer.state.loco(address)?.function(5)?.isMomentary == true)

        assertTrue(reducer.send(SessionIntent.PressFunction(address, 32), 23).isEmpty())
    }

    @Test
    fun `function labels and roster names are stored`() {
        val reducer = ready()
        reducer.send(
            WiThrottleEvent.RosterList(1, listOf(RosterLoco("Yard Goat", longAddress))),
            2,
        )

        assertEquals(listOf(RosterLoco("Yard Goat", longAddress)), reducer.state.roster)
        reducer.send(SessionIntent.Acquire(longAddress), 3)
        assertEquals("Yard Goat", reducer.state.loco(longAddress)?.name)
        reducer.send(WiThrottleEvent.LocoAdded(key, LocoId.of(longAddress)), 4)

        reducer.send(
            WiThrottleEvent.FunctionLabels(
                key,
                LocoId.of(longAddress),
                listOf("Headlight", "Bell"),
            ),
            5,
        )
        assertEquals(listOf("Headlight", "Bell"), reducer.state.loco(longAddress)?.functionLabels)
    }

    @Test
    fun `query intents emit the documented query commands`() {
        val reducer = ready()
        reducer.acquired(address)

        assertEquals(
            listOf(WiThrottleCommand.QuerySpeed(key, target)),
            reducer.send(SessionIntent.QuerySpeed(address), 20),
        )
        assertEquals(
            listOf(WiThrottleCommand.QueryDirection(key, target)),
            reducer.send(SessionIntent.QueryDirection(address), 21),
        )
        assertEquals(
            listOf(WiThrottleCommand.QuerySpeedStepMode(key, target)),
            reducer.send(SessionIntent.QuerySpeedStepMode(address), 22),
        )
        assertEquals(
            listOf(WiThrottleCommand.QueryMomentaryStates(key, target)),
            reducer.send(SessionIntent.QueryMomentaryStates(address), 23),
        )
    }

    @Test
    fun `consist intents emit commands and update optimistic state`() {
        val reducer = ready()
        val consist = LocoAddress.short(74)

        assertEquals(
            listOf(WiThrottleCommand.ConsistAdd(consist, "Mine", longAddress, true)),
            reducer.send(SessionIntent.AddToConsist(consist, "Mine", longAddress), 2),
        )
        assertEquals(
            ConsistInfo(consist, "Mine", listOf(ConsistMember(longAddress, true))),
            reducer.state.consists.single(),
        )

        val second = LocoAddress.long(346)
        reducer.send(SessionIntent.AddToConsist(consist, "Mine", second, false), 3)
        assertEquals(2, reducer.state.consists.single().members.size)

        assertEquals(
            listOf(WiThrottleCommand.ConsistRemoveLoco(consist, longAddress)),
            reducer.send(SessionIntent.RemoveFromConsist(consist, longAddress), 4),
        )
        assertEquals(listOf(second), reducer.state.consists.single().members.map { it.address })

        assertEquals(
            listOf(WiThrottleCommand.ConsistSetOrder(consist, listOf(second, longAddress))),
            reducer.send(SessionIntent.SetConsistOrder(consist, listOf(second, longAddress)), 5),
        )
        assertEquals(listOf(second, longAddress), reducer.state.consists.single().members.map { it.address })

        assertEquals(
            listOf(WiThrottleCommand.ConsistDelete(consist)),
            reducer.send(SessionIntent.DeleteConsist(consist), 6),
        )
        assertTrue(reducer.state.consists.isEmpty())
    }

    @Test
    fun `consist lead requires an acquired loco`() {
        val reducer = ready()
        assertTrue(reducer.send(SessionIntent.SetConsistLead(address, longAddress), 2).isEmpty())

        reducer.acquired(address)
        assertEquals(
            listOf(WiThrottleCommand.SetConsistLead(key, target, longAddress)),
            reducer.send(SessionIntent.SetConsistLead(address, longAddress), 20),
        )
    }

    @Test
    fun `consist events update the cached consist list`() {
        val reducer = ready()
        val consist = LocoAddress.short(74)

        reducer.send(WiThrottleEvent.ConsistListHeader(1, editable = true), 2)
        assertEquals(1, reducer.state.consistCount)
        reducer.send(
            WiThrottleEvent.ConsistEntry(
                ConsistInfo(consist, "Mine", listOf(ConsistMember(longAddress, true))),
            ),
            3,
        )
        assertEquals(consist, reducer.state.consists.single().address)

        reducer.send(
            WiThrottleEvent.ConsistEntry(
                ConsistInfo(consist, "Renamed", listOf(ConsistMember(longAddress, false))),
            ),
            4,
        )
        assertEquals(1, reducer.state.consists.size)
        assertEquals("Renamed", reducer.state.consists.single().id)
        assertFalse(reducer.state.consists.single().members.single().forward)
    }
}
