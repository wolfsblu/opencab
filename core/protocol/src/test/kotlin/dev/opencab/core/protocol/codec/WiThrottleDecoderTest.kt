package dev.opencab.core.protocol.codec

import dev.opencab.core.protocol.AddressType
import dev.opencab.core.protocol.ConsistInfo
import dev.opencab.core.protocol.ConsistMember
import dev.opencab.core.protocol.Direction
import dev.opencab.core.protocol.LocoAddress
import dev.opencab.core.protocol.LocoId
import dev.opencab.core.protocol.ProtocolVersion
import dev.opencab.core.protocol.RosterLoco
import dev.opencab.core.protocol.SpeedStepMode
import dev.opencab.core.protocol.ThrottleKey
import dev.opencab.core.protocol.TrackPowerState
import dev.opencab.core.protocol.WiThrottleEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WiThrottleDecoderTest {
    private val logs = mutableListOf<String>()

    private fun decoder(): WiThrottleDecoder = WiThrottleDecoder { message -> logs += message }

    @Test
    fun `decodes the documented initial greeting`() {
        val events =
            decoder().feed(
                """
                VN2.0
                RL1]\[SP 2101}|{2102}|{L
                PPA2
                PTT]\[Turnouts}|{Turnout]\[Closed}|{2]\[Thrown}|{4
                PRT]\[Routes}|{Route]\[Active}|{2]\[Inactive}|{4
                RCC0
                PW12080
                *10
                """.trimIndent() + "\n",
            )

        assertEquals(8, events.size)
        assertEquals(WiThrottleEvent.Version(ProtocolVersion.V2_0), events[0])
        assertEquals(
            WiThrottleEvent.RosterList(
                1,
                listOf(RosterLoco("SP 2101", LocoAddress(AddressType.LONG, 2102))),
            ),
            events[1],
        )
        assertEquals(WiThrottleEvent.TrackPower(TrackPowerState.UNKNOWN), events[2])
        assertTrue(events[3] is WiThrottleEvent.Unknown)
        assertTrue(events[4] is WiThrottleEvent.Unknown)
        assertEquals(WiThrottleEvent.ConsistListHeader(0, editable = true), events[5])
        assertEquals(WiThrottleEvent.WebPort(12080), events[6])
        assertEquals(WiThrottleEvent.HeartbeatInterval(10), events[7])
        assertEquals(2, logs.size)
    }

    @Test
    fun `decodes the documented acquire reply transcript`() {
        val events =
            decoder().feed(
                """
                M0+L41<;>
                M0LL41<;>]\[Headlight]\[Bell]\[Whistle]\[
                M0AL41<;>F00
                M0AL41<;>F01
                M0AL41<;>V0
                M0AL41<;>R1
                M0AL41<;>s1
                """.trimIndent() + "\n",
            )

        assertEquals(
            WiThrottleEvent.LocoAdded(ThrottleKey.DEFAULT, LocoId.of(LocoAddress.long(41))),
            events[0],
        )
        assertEquals(
            WiThrottleEvent.FunctionLabels(
                ThrottleKey.DEFAULT,
                LocoId.of(LocoAddress.long(41)),
                listOf("Headlight", "Bell", "Whistle", ""),
            ),
            events[1],
        )
        assertEquals(
            WiThrottleEvent.FunctionStateChanged(
                ThrottleKey.DEFAULT,
                LocoId.of(LocoAddress.long(41)),
                0,
                false,
            ),
            events[2],
        )
        assertEquals(
            WiThrottleEvent.FunctionStateChanged(
                ThrottleKey.DEFAULT,
                LocoId.of(LocoAddress.long(41)),
                1,
                false,
            ),
            events[3],
        )
        assertEquals(
            WiThrottleEvent.SpeedChanged(
                ThrottleKey.DEFAULT,
                LocoId.of(LocoAddress.long(41)),
                0,
                false,
            ),
            events[4],
        )
        assertEquals(
            WiThrottleEvent.DirectionChanged(
                ThrottleKey.DEFAULT,
                LocoId.of(LocoAddress.long(41)),
                Direction.FORWARD,
            ),
            events[5],
        )
        assertEquals(
            WiThrottleEvent.SpeedStepModeChanged(
                ThrottleKey.DEFAULT,
                LocoId.of(LocoAddress.long(41)),
                SpeedStepMode.NmraDcc128,
            ),
            events[6],
        )
    }

    @Test
    fun `decodes steal removal and broadcast messages`() {
        val events =
            decoder().feed(
                "MTSL341<;>L341\nM0-L341<;>\nM0A*<;>V30\nM0A*<;>R0\n",
            )

        assertEquals(
            WiThrottleEvent.StealRequested(ThrottleKey('T'), LocoId.of(LocoAddress.long(341))),
            events[0],
        )
        assertEquals(
            WiThrottleEvent.LocoRemoved(ThrottleKey.DEFAULT, LocoId.of(LocoAddress.long(341))),
            events[1],
        )
        assertEquals(
            WiThrottleEvent.SpeedChanged(ThrottleKey.DEFAULT, LocoId.All, 30, false),
            events[2],
        )
        assertEquals(
            WiThrottleEvent.DirectionChanged(ThrottleKey.DEFAULT, LocoId.All, Direction.REVERSE),
            events[3],
        )
    }

    @Test
    fun `negative speed is an emergency stop`() {
        val events = decoder().feed("M0A*<;>V-126\nM0A*<;>V-1\nM0A*<;>V126\n")

        assertEquals(WiThrottleEvent.SpeedChanged(ThrottleKey.DEFAULT, LocoId.All, 0, true), events[0])
        assertEquals(WiThrottleEvent.SpeedChanged(ThrottleKey.DEFAULT, LocoId.All, 0, true), events[1])
        assertEquals(WiThrottleEvent.SpeedChanged(ThrottleKey.DEFAULT, LocoId.All, 126, false), events[2])
    }

    @Test
    fun `decodes function state and momentary messages`() {
        val events =
            decoder().feed(
                "M0A*<;>F112\nM0A*<;>F012\nM0A*<;>m112\nM0A*<;>m012\nM0A*<;>f112\nM0A*<;>F10\n",
            )

        assertEquals(WiThrottleEvent.FunctionStateChanged(ThrottleKey.DEFAULT, LocoId.All, 12, true), events[0])
        assertEquals(WiThrottleEvent.FunctionStateChanged(ThrottleKey.DEFAULT, LocoId.All, 12, false), events[1])
        assertEquals(
            WiThrottleEvent.FunctionMomentaryChanged(ThrottleKey.DEFAULT, LocoId.All, 12, true),
            events[2],
        )
        assertEquals(
            WiThrottleEvent.FunctionMomentaryChanged(ThrottleKey.DEFAULT, LocoId.All, 12, false),
            events[3],
        )
        assertEquals(WiThrottleEvent.FunctionStateChanged(ThrottleKey.DEFAULT, LocoId.All, 12, true), events[4])
        assertEquals(WiThrottleEvent.FunctionStateChanged(ThrottleKey.DEFAULT, LocoId.All, 0, true), events[5])
    }

    @Test
    fun `decodes every documented speed step mode and preserves unknown names`() {
        val events = decoder().feed("M0A*<;>s1\nM0A*<;>s2\nM0A*<;>s4\nM0A*<;>s8\nM0A*<;>s16\nM0A*<;>sSOMETHING\n")

        assertEquals(SpeedStepMode.NmraDcc128, (events[0] as WiThrottleEvent.SpeedStepModeChanged).mode)
        assertEquals(SpeedStepMode.NmraDcc28, (events[1] as WiThrottleEvent.SpeedStepModeChanged).mode)
        assertEquals(SpeedStepMode.NmraDcc27, (events[2] as WiThrottleEvent.SpeedStepModeChanged).mode)
        assertEquals(SpeedStepMode.NmraDcc14, (events[3] as WiThrottleEvent.SpeedStepModeChanged).mode)
        assertEquals(SpeedStepMode.Motorola28, (events[4] as WiThrottleEvent.SpeedStepModeChanged).mode)
        assertEquals(SpeedStepMode.Other("SOMETHING"), (events[5] as WiThrottleEvent.SpeedStepModeChanged).mode)
    }

    @Test
    fun `decodes roster and consist lists`() {
        val events =
            decoder().feed(
                "RL0\n" +
                    "RL2]\\[RGS 41}|{41}|{L]\\[Test Loco}|{1234}|{L\n" +
                    "RCC2\n" +
                    "RCD}|{74(S)}|{74(S)]\\[3374(L)}|{true]\\[346(L)}|{false\n" +
                    "RCL0\n",
            )

        assertEquals(WiThrottleEvent.RosterList(0, emptyList()), events[0])
        assertEquals(
            WiThrottleEvent.RosterList(
                2,
                listOf(
                    RosterLoco("RGS 41", LocoAddress.long(41)),
                    RosterLoco("Test Loco", LocoAddress.long(1234)),
                ),
            ),
            events[1],
        )
        assertEquals(WiThrottleEvent.ConsistListHeader(2, editable = true), events[2])
        assertEquals(
            WiThrottleEvent.ConsistEntry(
                ConsistInfo(
                    LocoAddress.short(74),
                    "74(S)",
                    listOf(
                        ConsistMember(LocoAddress.long(3374), true),
                        ConsistMember(LocoAddress.long(346), false),
                    ),
                ),
            ),
            events[3],
        )
        assertEquals(WiThrottleEvent.ConsistListHeader(0, editable = false), events[4])
    }

    @Test
    fun `accepts a roster count that disagrees with the entries`() {
        val event = decoder().feed("RL5]\\[Only One}|{1}|{S\n").single()

        assertEquals(
            WiThrottleEvent.RosterList(5, listOf(RosterLoco("Only One", LocoAddress.short(1)))),
            event,
        )
    }

    @Test
    fun `decodes server metadata and alerts`() {
        val events =
            decoder().feed(
                "HTJMRI\nHtJMRI 5.10 My Layout\nHMaddress in use\nHminfo\nPW12080\nPFT65871<;>4\n",
            )

        assertEquals(WiThrottleEvent.ServerType("JMRI"), events[0])
        assertEquals(WiThrottleEvent.ServerDescription("JMRI 5.10 My Layout"), events[1])
        assertEquals(WiThrottleEvent.Alert("address in use"), events[2])
        assertEquals(WiThrottleEvent.Info("info"), events[3])
        assertEquals(WiThrottleEvent.WebPort(12080), events[4])
        assertEquals(WiThrottleEvent.FastClock(65871, 4.0), events[5])
    }

    @Test
    fun `unknown and unsupported messages become logged Unknown events`() {
        val lines =
            listOf(
                "PTA2LT92",
                "PRA2IO_RESET_LAYOUT",
                "PFC",
                "RPF}|{0]\\[F0}|{true",
                "RSF}|{0]\\[F0}|{true",
                "ZZsomething",
                "D200 90 90",
            )

        val events = decoder().feed(lines.joinToString("\n") + "\n")

        assertEquals(lines.size, events.size)
        events.forEachIndexed { index, event ->
            assertTrue("line ${lines[index]} -> $event", event is WiThrottleEvent.Unknown)
            assertEquals(lines[index], (event as WiThrottleEvent.Unknown).raw)
        }
        assertEquals(lines.size, logs.size)
    }

    @Test
    fun `malformed input never throws`() {
        val malformed =
            listOf(
                "M",
                "M0Zfoo",
                "M0A*<;>Vabc",
                "M0A*<;>V999",
                "M0A*<;>V",
                "M0A*<;>F",
                "M0A*<;>F2",
                "M0A*<;>s",
                "M0A*<;>m",
                "M0A*<;>qZ",
                "PPA",
                "PPA9",
                "VN",
                "PW",
                "RLx",
                "RL1]\\[Broken}|{not-a-number}|{S",
                "RCD}|{bad",
                "RCCx",
                "*x",
            )

        val decoder = decoder()
        malformed.forEach { line ->
            val event = decoder.decode(line)
            assertTrue("line $line -> $event", event is WiThrottleEvent.Unknown)
        }
        assertEquals(malformed.size, logs.size)
    }

    @Test
    fun `empty input is ignored and empty decode returns null`() {
        val decoder = decoder()

        assertTrue(decoder.feed("\n\r\n\r").isEmpty())
        assertNull(decoder.decode(""))
    }

    @Test
    fun `invalid utf8 bytes still produce an Unknown event`() {
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte(), '\n'.code.toByte())

        val events = decoder().feed(bytes)

        assertEquals(1, events.size)
        assertTrue(events.single() is WiThrottleEvent.Unknown)
    }

    @Test
    fun `duplicate lines produce duplicate events`() {
        val events = decoder().feed("PPA1\nPPA1\n")

        assertEquals(
            listOf(
                WiThrottleEvent.TrackPower(TrackPowerState.ON),
                WiThrottleEvent.TrackPower(TrackPowerState.ON),
            ),
            events,
        )
    }

    @Test
    fun `decodes unicode content`() {
        val events = decoder().feed("HmGrüße\nRL1]\\[Grüße}|{3}|{S\n")

        assertEquals(WiThrottleEvent.Info("Grüße"), events[0])
        assertEquals(
            WiThrottleEvent.RosterList(1, listOf(RosterLoco("Grüße", LocoAddress.short(3)))),
            events[1],
        )
    }

    @Test
    fun `split at every byte produces the same events`() {
        val text = "VN2.0\r\nM0A*<;>V23\nPPA1\r\n*10\r"
        val bytes = text.toByteArray(Charsets.UTF_8)
        val expected = decoder().feed(text)

        for (split in 0..bytes.size) {
            val decoder = decoder()
            val actual =
                decoder.feed(bytes.copyOfRange(0, split)) +
                    decoder.feed(bytes.copyOfRange(split, bytes.size))
            assertEquals("split at $split", expected, actual)
        }
    }

    @Test
    fun `captured transcript decodes identically in one chunk and byte by byte`() {
        val bytes =
            checkNotNull(javaClass.getResourceAsStream("/transcripts/jmri-acquire.txt"))
                .use { it.readBytes() }

        val whole = decoder().feed(bytes)
        val incremental = mutableListOf<WiThrottleEvent>()
        val streamed = decoder()
        bytes.forEach { byte -> incremental += streamed.feed(byteArrayOf(byte)) }

        assertEquals(17, whole.size)
        assertEquals(WiThrottleEvent.Version(ProtocolVersion.V2_0), whole.first())
        assertEquals(
            WiThrottleEvent.SpeedChanged(
                ThrottleKey.DEFAULT,
                LocoId.of(LocoAddress.long(41)),
                23,
                false,
            ),
            whole.last(),
        )
        assertEquals(whole, incremental)
        assertTrue(logs.isEmpty())
    }
}
