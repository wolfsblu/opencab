package dev.opencab.core.protocol.codec

import dev.opencab.core.protocol.AddressType
import dev.opencab.core.protocol.Direction
import dev.opencab.core.protocol.LocoAddress
import dev.opencab.core.protocol.LocoId
import dev.opencab.core.protocol.LocoSelection
import dev.opencab.core.protocol.RemovalAction
import dev.opencab.core.protocol.SpeedStepMode
import dev.opencab.core.protocol.ThrottleKey
import dev.opencab.core.protocol.TrackPowerState
import dev.opencab.core.protocol.WiThrottleCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class WiThrottleEncoderTest {
    private val address = LocoAddress.long(341)
    private val consist = LocoAddress.short(74)
    private val key = ThrottleKey.DEFAULT
    private val target = LocoId.of(address)

    @Test
    fun `encodes golden wire lines from the protocol documentation`() {
        assertEquals("NOpenCab", encode(WiThrottleCommand.SetDeviceName("OpenCab")))
        assertEquals("HUopencab-android", encode(WiThrottleCommand.SetHardwareId("opencab-android")))
        assertEquals("*", encode(WiThrottleCommand.Heartbeat))
        assertEquals("*+", encode(WiThrottleCommand.EnableHeartbeatMonitoring))
        assertEquals("*-", encode(WiThrottleCommand.DisableHeartbeatMonitoring))
        assertEquals("Q", encode(WiThrottleCommand.Quit))
        assertEquals("PPA1", encode(WiThrottleCommand.SetTrackPower(TrackPowerState.ON)))
        assertEquals("M0+L341<;>L341", encode(WiThrottleCommand.Acquire(key, address)))
        assertEquals("M0+S3<;>ED&RGW 341", encode(rosterAcquire()))
        assertEquals("M0-L341<;>r", encode(WiThrottleCommand.Release(key, target)))
        assertEquals("M0-*<;>r", encode(WiThrottleCommand.Release(key, LocoId.All)))
        assertEquals("M0-L341<;>d", encode(WiThrottleCommand.Release(key, target, RemovalAction.DISPATCH)))
        assertEquals("M0AL341<;>V30", encode(WiThrottleCommand.SetSpeed(key, target, 30)))
        assertEquals("M0AL341<;>R0", encode(WiThrottleCommand.SetDirection(key, target, Direction.REVERSE)))
        assertEquals("M0AL341<;>I", encode(WiThrottleCommand.SetIdle(key, target)))
        assertEquals("M0AL341<;>X", encode(WiThrottleCommand.EmergencyStop(key, target)))
        assertEquals("M0AL341<;>F112", encode(WiThrottleCommand.PressFunction(key, target, 12)))
        assertEquals("M0AL341<;>F012", encode(WiThrottleCommand.ReleaseFunction(key, target, 12)))
        assertEquals("M0AL341<;>f112", encode(WiThrottleCommand.ForceFunction(key, target, 12, true)))
        assertEquals("M0AL341<;>m112", encode(WiThrottleCommand.SetFunctionMomentary(key, target, 12, true)))
        assertEquals("M0AL341<;>s2", encode(WiThrottleCommand.SetSpeedStepMode(key, target, SpeedStepMode.NmraDcc28)))
        assertEquals("M0AL341<;>qV", encode(WiThrottleCommand.QuerySpeed(key, target)))
        assertEquals("M0AL341<;>qR", encode(WiThrottleCommand.QueryDirection(key, target)))
        assertEquals("M0AL341<;>qs", encode(WiThrottleCommand.QuerySpeedStepMode(key, target)))
        assertEquals("M0AL341<;>qm", encode(WiThrottleCommand.QueryMomentaryStates(key, target)))
        assertEquals("M0AL341<;>CL346", encode(WiThrottleCommand.SetConsistLead(key, target, LocoAddress.long(346))))
        assertEquals("M0SL341<;>L341", encode(WiThrottleCommand.Steal(key, address)))
        assertEquals(
            "RC+<;>S74<;>My consist<:>L341<;>true",
            encode(WiThrottleCommand.ConsistAdd(consist, "My consist", address, true)),
        )
        assertEquals("RC-<;>S74<:>L341", encode(WiThrottleCommand.ConsistRemoveLoco(consist, address)))
        assertEquals(
            "RCP<;>S74<:>L346<;>L3374",
            encode(
                WiThrottleCommand.ConsistSetOrder(
                    consist,
                    listOf(LocoAddress.long(346), LocoAddress.long(3374)),
                ),
            ),
        )
        assertEquals("RCR<;>S74", encode(WiThrottleCommand.ConsistDelete(consist)))
    }

    @Test
    fun `every command round-trips through the command decoder`() {
        allCommands().forEach { command ->
            val wire = encode(command)
            assertEquals("round trip for $wire", command, WiThrottleCommandDecoder.decode(wire))
        }
    }

    @Test
    fun `rejects commands that would produce malformed wire text`() {
        assertThrows(IllegalArgumentException::class.java) {
            WiThrottleCommand.SetDeviceName("bad\nname")
        }
        assertThrows(IllegalArgumentException::class.java) {
            WiThrottleCommand.SetHardwareId("bad\rname")
        }
        assertThrows(IllegalArgumentException::class.java) {
            WiThrottleCommand.SetSpeed(key, target, 127)
        }
        assertThrows(IllegalArgumentException::class.java) {
            WiThrottleCommand.PressFunction(key, target, 32)
        }
        assertThrows(IllegalArgumentException::class.java) {
            WiThrottleCommand.SetTrackPower(TrackPowerState.UNKNOWN)
        }
        assertThrows(IllegalArgumentException::class.java) {
            WiThrottleCommand.ConsistAdd(consist, "bad\nname", address, true)
        }
        assertThrows(IllegalArgumentException::class.java) {
            WiThrottleCommand.ConsistSetOrder(consist, emptyList())
        }
        assertThrows(IllegalArgumentException::class.java) {
            LocoAddress(AddressType.SHORT, -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            LocoAddress(AddressType.LONG, LocoAddress.MAX_NUMBER + 1)
        }
    }

    @Test
    fun `command decoder rejects malformed commands`() {
        val malformed =
            listOf(
                "M0A*<;>V999",
                "M0A*<;>V-1",
                "M0A*<;>F199",
                "M0A*<;>Z",
                "M0+<;>L341",
                "PPA2",
                "RCF<;>L341<:>21<;>1",
                "RC+<;>S74<:>L341",
                "ZZ",
            )

        malformed.forEach { line -> assertNull(line, WiThrottleCommandDecoder.decode(line)) }
    }

    @Test
    fun `command decoder tolerates the action form of release`() {
        assertEquals(
            WiThrottleCommand.Release(key, target, RemovalAction.RELEASE),
            WiThrottleCommandDecoder.decode("M0AL341<;>r"),
        )
        assertEquals(
            WiThrottleCommand.Release(key, target, RemovalAction.DISPATCH),
            WiThrottleCommandDecoder.decode("M0AL341<;>d"),
        )
    }

    private fun encode(command: WiThrottleCommand): String = WiThrottleEncoder.encode(command)

    private fun rosterAcquire(): WiThrottleCommand.Acquire = WiThrottleCommand.Acquire(
        ThrottleKey.DEFAULT,
        LocoAddress.short(3),
        LocoSelection.RosterEntry("D&RGW 341"),
    )

    private fun allCommands(): List<WiThrottleCommand> = listOf(
        WiThrottleCommand.SetDeviceName("OpenCab"),
        WiThrottleCommand.SetHardwareId("opencab-android"),
        WiThrottleCommand.Heartbeat,
        WiThrottleCommand.EnableHeartbeatMonitoring,
        WiThrottleCommand.DisableHeartbeatMonitoring,
        WiThrottleCommand.Quit,
        WiThrottleCommand.SetTrackPower(TrackPowerState.OFF),
        WiThrottleCommand.Acquire(key, address),
        rosterAcquire(),
        WiThrottleCommand.Release(key, target),
        WiThrottleCommand.Release(key, LocoId.All),
        WiThrottleCommand.Release(key, target, RemovalAction.DISPATCH),
        WiThrottleCommand.SetSpeed(key, target, 0),
        WiThrottleCommand.SetSpeed(key, LocoId.All, 126),
        WiThrottleCommand.SetDirection(key, target, Direction.REVERSE),
        WiThrottleCommand.SetDirection(key, target, Direction.FORWARD),
        WiThrottleCommand.SetIdle(key, target),
        WiThrottleCommand.EmergencyStop(key, target),
        WiThrottleCommand.PressFunction(key, target, 0),
        WiThrottleCommand.PressFunction(key, target, 31),
        WiThrottleCommand.ReleaseFunction(key, target, 0),
        WiThrottleCommand.ReleaseFunction(key, target, 31),
        WiThrottleCommand.ForceFunction(key, target, 0, true),
        WiThrottleCommand.ForceFunction(key, target, 31, false),
        WiThrottleCommand.SetFunctionMomentary(key, target, 0, true),
        WiThrottleCommand.SetFunctionMomentary(key, target, 31, false),
        WiThrottleCommand.SetSpeedStepMode(key, target, SpeedStepMode.NmraDcc128),
        WiThrottleCommand.SetSpeedStepMode(key, target, SpeedStepMode.NmraDcc28),
        WiThrottleCommand.SetSpeedStepMode(key, target, SpeedStepMode.NmraDcc27),
        WiThrottleCommand.SetSpeedStepMode(key, target, SpeedStepMode.NmraDcc14),
        WiThrottleCommand.SetSpeedStepMode(key, target, SpeedStepMode.Motorola28),
        WiThrottleCommand.SetSpeedStepMode(key, target, SpeedStepMode.Other("SOMETHING")),
        WiThrottleCommand.QuerySpeed(key, target),
        WiThrottleCommand.QueryDirection(key, target),
        WiThrottleCommand.QuerySpeedStepMode(key, target),
        WiThrottleCommand.QueryMomentaryStates(key, target),
        WiThrottleCommand.SetConsistLead(key, target, LocoAddress.long(346)),
        WiThrottleCommand.Steal(key, address),
        WiThrottleCommand.ConsistAdd(consist, "My consist", address, true),
        WiThrottleCommand.ConsistAdd(consist, "Reverse", address, false),
        WiThrottleCommand.ConsistRemoveLoco(consist, address),
        WiThrottleCommand.ConsistSetOrder(
            consist,
            listOf(LocoAddress.long(346), LocoAddress.long(3374)),
        ),
        WiThrottleCommand.ConsistDelete(consist),
    )
}
