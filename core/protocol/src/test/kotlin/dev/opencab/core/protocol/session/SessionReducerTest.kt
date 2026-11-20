package dev.opencab.core.protocol.session

import dev.opencab.core.protocol.AddressType
import dev.opencab.core.protocol.LocoAddress
import dev.opencab.core.protocol.LocoId
import dev.opencab.core.protocol.LocoSelection
import dev.opencab.core.protocol.ProtocolVersion
import dev.opencab.core.protocol.RosterLoco
import dev.opencab.core.protocol.ThrottleKey
import dev.opencab.core.protocol.TrackPowerState
import dev.opencab.core.protocol.WiThrottleCommand
import dev.opencab.core.protocol.WiThrottleEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionReducerTest {
    private val address = LocoAddress.short(3)
    private val longAddress = LocoAddress.long(341)
    private val key = ThrottleKey.DEFAULT
    private val target = LocoId.of(address)

    @Test
    fun `link up sends hardware id then device name and awaits greeting`() {
        val reducer = SessionReducer()

        val commands = reducer.handle(SessionInput.LinkUp(0))

        assertEquals(
            listOf(
                WiThrottleCommand.SetHardwareId("opencab"),
                WiThrottleCommand.SetDeviceName("OpenCab"),
            ),
            commands,
        )
        assertEquals(LinkState.CONNECTED, reducer.state.link)
        assertEquals(HandshakeState.AwaitingGreeting, reducer.state.handshake)
        assertEquals(0L, reducer.state.lastOutboundAtMillis)
    }

    @Test
    fun `link up uses configured identity`() {
        val reducer =
            SessionReducer(
                SessionConfig(deviceName = "Club Cab", hardwareId = "device-7"),
            )

        val commands = reducer.handle(SessionInput.LinkUp(0))

        assertEquals(
            listOf(
                WiThrottleCommand.SetHardwareId("device-7"),
                WiThrottleCommand.SetDeviceName("Club Cab"),
            ),
            commands,
        )
    }

    @Test
    fun `duplicate link up is idempotent`() {
        val reducer = SessionReducer()
        reducer.handle(SessionInput.LinkUp(0))

        assertTrue(reducer.handle(SessionInput.LinkUp(1)).isEmpty())
        assertEquals(0L, reducer.state.lastOutboundAtMillis)
    }

    @Test
    fun `supported version completes the handshake`() {
        val reducer = ready()

        assertEquals(HandshakeState.Ready, reducer.state.handshake)
        assertEquals(ProtocolVersion.V2_0, reducer.state.server.protocolVersion)
        assertTrue(reducer.state.canControl)
    }

    @Test
    fun `unsupported version blocks control commands and reacquisition`() {
        val reducer = SessionReducer()
        reducer.handle(SessionInput.LinkUp(0))
        assertTrue(reducer.send(SessionIntent.Acquire(address), 1).isEmpty())

        val version = ProtocolVersion(1, 9, "1.9")
        assertTrue(reducer.send(WiThrottleEvent.Version(version), 2).isEmpty())

        assertEquals(HandshakeState.UnsupportedVersion(version), reducer.state.handshake)
        assertEquals(LocoStatus.IDLE, reducer.state.loco(address)?.status)
        assertTrue(reducer.state.loco(address)?.desired == true)
        assertFalse(reducer.state.canControl)
        assertTrue(reducer.send(SessionIntent.Acquire(address), 3).isEmpty())
    }

    @Test
    fun `acquire is queued until the handshake and sent exactly once`() {
        val reducer = SessionReducer()
        reducer.handle(SessionInput.LinkUp(0))

        assertTrue(reducer.send(SessionIntent.Acquire(address), 1).isEmpty())
        assertEquals(LocoStatus.IDLE, reducer.state.loco(address)?.status)
        assertTrue(reducer.state.loco(address)?.desired == true)

        assertEquals(
            listOf(WiThrottleCommand.Acquire(key, address)),
            reducer.send(WiThrottleEvent.Version(ProtocolVersion.V2_0), 2),
        )
        assertEquals(LocoStatus.REQUESTED, reducer.state.loco(address)?.status)

        assertTrue(reducer.send(WiThrottleEvent.Version(ProtocolVersion.V2_0), 3).isEmpty())
        assertTrue(reducer.send(SessionIntent.Acquire(address), 4).isEmpty())
    }

    @Test
    fun `loco added promotes requested to acquired and duplicates are harmless`() {
        val reducer = ready()
        assertEquals(
            listOf(WiThrottleCommand.Acquire(key, address)),
            reducer.send(SessionIntent.Acquire(address), 2),
        )

        assertTrue(reducer.send(WiThrottleEvent.LocoAdded(key, target), 3).isEmpty())
        assertEquals(LocoStatus.ACQUIRED, reducer.state.loco(address)?.status)
        assertEquals(key, reducer.state.loco(address)?.throttleKey)

        val before = reducer.state
        assertTrue(reducer.send(WiThrottleEvent.LocoAdded(key, target), 4).isEmpty())
        assertEquals(before, reducer.state)
        assertTrue(reducer.send(SessionIntent.Acquire(address), 5).isEmpty())
    }

    @Test
    fun `unsolicited loco added creates an acquired slot`() {
        val reducer = ready()

        reducer.send(
            WiThrottleEvent.RosterList(1, listOf(RosterLoco("Yard Goat", longAddress))),
            2,
        )
        assertTrue(reducer.send(WiThrottleEvent.LocoAdded(key, LocoId.of(longAddress)), 3).isEmpty())

        val slot = reducer.state.loco(longAddress)
        assertEquals(LocoStatus.ACQUIRED, slot?.status)
        assertEquals("Yard Goat", slot?.name)
        assertFalse(slot?.desired ?: true)
    }

    @Test
    fun `heartbeat is negotiated enabled and sent on schedule`() {
        val reducer = ready()
        assertEquals(
            listOf(WiThrottleCommand.EnableHeartbeatMonitoring),
            reducer.send(WiThrottleEvent.HeartbeatInterval(10), 2),
        )
        assertTrue(reducer.state.heartbeat.required)
        assertTrue(reducer.state.heartbeat.monitoringEnabled)
        assertEquals(2L, reducer.state.lastOutboundAtMillis)

        assertTrue(reducer.handle(SessionInput.Tick(5_001)).isEmpty())
        assertEquals(listOf(WiThrottleCommand.Heartbeat), reducer.handle(SessionInput.Tick(5_002)))
        assertEquals(5_002L, reducer.state.heartbeat.lastSentAtMillis)
        assertTrue(reducer.handle(SessionInput.Tick(5_003)).isEmpty())
        assertEquals(listOf(WiThrottleCommand.Heartbeat), reducer.handle(SessionInput.Tick(10_002)))
    }

    @Test
    fun `any outbound command defers the heartbeat`() {
        val reducer = ready()
        reducer.send(WiThrottleEvent.HeartbeatInterval(10), 2)
        reducer.acquired(address, now = 100)
        reducer.send(SessionIntent.SetSpeed(address, 20), 6_000)

        assertTrue(reducer.handle(SessionInput.Tick(10_000)).isEmpty())
        assertEquals(listOf(WiThrottleCommand.Heartbeat), reducer.handle(SessionInput.Tick(11_000)))
    }

    @Test
    fun `duplicate heartbeat interval does not repeat enable`() {
        val reducer = ready()
        reducer.send(WiThrottleEvent.HeartbeatInterval(10), 2)

        assertTrue(reducer.send(WiThrottleEvent.HeartbeatInterval(10), 3).isEmpty())
        assertTrue(reducer.state.heartbeat.monitoringEnabled)
    }

    @Test
    fun `server disabled heartbeat clears monitoring and stops ticks`() {
        val reducer = ready()
        reducer.send(WiThrottleEvent.HeartbeatInterval(10), 2)

        assertEquals(
            listOf(WiThrottleCommand.DisableHeartbeatMonitoring),
            reducer.send(WiThrottleEvent.HeartbeatInterval(0), 3),
        )
        assertFalse(reducer.state.heartbeat.required)
        assertFalse(reducer.state.heartbeat.monitoringEnabled)
        assertTrue(reducer.handle(SessionInput.Tick(10_000)).isEmpty())
    }

    @Test
    fun `configured heartbeat disable leaves monitoring off`() {
        val reducer = SessionReducer(SessionConfig(heartbeat = HeartbeatPolicy(enabled = false)))
        reducer.awaitReady()
        assertTrue(reducer.send(WiThrottleEvent.HeartbeatInterval(10), 2).isEmpty())
        assertFalse(reducer.state.heartbeat.monitoringEnabled)
        assertTrue(reducer.handle(SessionInput.Tick(10_000)).isEmpty())
    }

    @Test
    fun `heartbeat intent toggles server monitoring`() {
        val reducer = ready()

        assertEquals(
            listOf(WiThrottleCommand.EnableHeartbeatMonitoring),
            reducer.send(SessionIntent.SetHeartbeatEnabled(true), 2),
        )
        assertTrue(reducer.state.heartbeat.monitoringEnabled)
        assertTrue(reducer.send(SessionIntent.SetHeartbeatEnabled(true), 3).isEmpty())

        assertEquals(
            listOf(WiThrottleCommand.DisableHeartbeatMonitoring),
            reducer.send(SessionIntent.SetHeartbeatEnabled(false), 4),
        )
        assertFalse(reducer.state.heartbeat.monitoringEnabled)
    }

    @Test
    fun `track power is decoded and read-only by default`() {
        val reducer = ready()

        assertTrue(reducer.send(WiThrottleEvent.TrackPower(TrackPowerState.ON), 2).isEmpty())
        assertEquals(TrackPowerState.ON, reducer.state.power)

        assertTrue(reducer.send(SessionIntent.SetTrackPower(TrackPowerState.OFF), 3).isEmpty())
        assertEquals(TrackPowerState.ON, reducer.state.power)
    }

    @Test
    fun `track power can be commanded when explicitly enabled`() {
        val reducer = SessionReducer(SessionConfig(allowTrackPowerControl = true))
        reducer.awaitReady()

        assertEquals(
            listOf(WiThrottleCommand.SetTrackPower(TrackPowerState.OFF)),
            reducer.send(SessionIntent.SetTrackPower(TrackPowerState.OFF), 2),
        )
        assertEquals(TrackPowerState.OFF, reducer.state.power)
        assertTrue(reducer.send(SessionIntent.SetTrackPower(TrackPowerState.UNKNOWN), 3).isEmpty())
    }

    @Test
    fun `server metadata alerts and unknown messages are folded into state`() {
        val reducer = ready()

        reducer.send(WiThrottleEvent.ServerType("JMRI"), 2)
        reducer.send(WiThrottleEvent.ServerDescription("JMRI 5.10 Test"), 3)
        reducer.send(WiThrottleEvent.WebPort(12080), 4)
        reducer.send(WiThrottleEvent.Alert("address in use"), 5)
        reducer.send(WiThrottleEvent.Info("hello"), 6)
        reducer.send(WiThrottleEvent.FastClock(123, 4.0), 7)
        assertTrue(reducer.send(WiThrottleEvent.Unknown("ZZ", "unknown"), 8).isEmpty())

        assertEquals("JMRI", reducer.state.server.type)
        assertEquals("JMRI 5.10 Test", reducer.state.server.description)
        assertEquals(12080, reducer.state.server.webPort)
        assertEquals("address in use", reducer.state.lastAlert)
        assertEquals("hello", reducer.state.lastInfo)
        assertEquals(1, reducer.state.unknownMessageCount)
    }

    @Test
    fun `quit emits the quit command`() {
        val reducer = ready()

        assertEquals(listOf(WiThrottleCommand.Quit), reducer.send(SessionIntent.Quit, 2))
    }

    @Test
    fun `identical input sequences produce identical state and commands`() {
        val first = runScript()
        val second = runScript()

        assertEquals(first.second, second.second)
        assertEquals(first.first.state, second.first.state)
    }

    @Test
    fun `acquire selects roster entry through the command layer`() {
        val reducer = ready()
        val rosterAcquire =
            WiThrottleCommand.Acquire(
                key,
                longAddress,
                LocoSelection.RosterEntry("D&RGW 341"),
            )

        assertEquals(
            listOf(rosterAcquire),
            reducer.send(
                SessionIntent.Acquire(longAddress, rosterEntryId = "D&RGW 341"),
                2,
            ),
        )

        reducer.handle(SessionInput.LinkDown(3))
        reducer.handle(SessionInput.LinkUp(4))
        assertEquals(
            listOf(rosterAcquire),
            reducer.send(WiThrottleEvent.Version(ProtocolVersion.V2_0), 5),
        )
    }

    @Test
    fun `protocol version parsing and ordering`() {
        assertEquals(ProtocolVersion.V2_0, ProtocolVersion.parse("2.0"))
        assertEquals(0, ProtocolVersion.parse("2.0")?.compareTo(ProtocolVersion(2, 0)))
        assertTrue(ProtocolVersion(2, 1) > ProtocolVersion.V2_0)
        assertNull(ProtocolVersion.parse("not-a-version"))
        assertNull(ProtocolVersion.parse("2.0.1"))
    }

    @Test
    fun `address wire formatting distinguishes short and long`() {
        assertEquals("S3", LocoAddress(AddressType.SHORT, 3).toWire())
        assertEquals("L341", LocoAddress(AddressType.LONG, 341).toWire())
        assertEquals("*", LocoId.All.toWire())
    }

    private fun runScript(): Pair<SessionReducer, List<WiThrottleCommand>> {
        val reducer = ready()
        val commands = mutableListOf<WiThrottleCommand>()
        commands += reducer.send(SessionIntent.Acquire(address), 2)
        commands += reducer.send(WiThrottleEvent.LocoAdded(key, target), 3)
        commands += reducer.send(SessionIntent.SetSpeed(address, 20), 4)
        commands += reducer.send(WiThrottleEvent.HeartbeatInterval(10), 5)
        commands += reducer.handle(SessionInput.Tick(6_000))
        return reducer to commands
    }
}
