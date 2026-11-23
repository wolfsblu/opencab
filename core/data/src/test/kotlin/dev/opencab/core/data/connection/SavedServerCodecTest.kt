package dev.opencab.core.data.connection

import dev.opencab.core.model.SavedServer
import dev.opencab.core.model.ServerEndpoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedServerCodecTest {
    @Test
    fun roundTripPreservesNamesWithSeparatorsAndMissingLastConnected() {
        val servers = listOf(
            SavedServer(
                id = "id-1",
                name = "Club layout | main.line\nshed",
                endpoint = ServerEndpoint(host = "192.168.1.24", port = 12090),
                lastConnectedEpochMillis = 1_700_000_000_000,
            ),
            SavedServer(
                id = "id-2",
                name = "Test bench",
                endpoint = ServerEndpoint(host = "jmri.local", port = 12091),
                lastConnectedEpochMillis = null,
            ),
        )

        val decoded = SavedServerCodec.decode(SavedServerCodec.encode(servers))

        assertEquals(servers, decoded)
    }

    @Test
    fun damagedRecordsAreSkippedWithoutLosingValidOnes() {
        val valid = SavedServer(
            id = "id-1",
            name = "Good",
            endpoint = ServerEndpoint("10.0.0.2", 12090),
        )
        val encoded = SavedServerCodec.encode(listOf(valid)) + "\nnot-a-record\n" + "a.b.c"

        val decoded = SavedServerCodec.decode(encoded)

        assertEquals(listOf(valid), decoded)
    }

    @Test
    fun outOfRangePortIsRejected() {
        // id=id, name=Bad, host=10.0.0.2, port=0, no last-connected value.
        val encoded = "aWQ.QmFk.MTAuMC4wLjI.MA."

        assertTrue(SavedServerCodec.decode(encoded).isEmpty())
    }
}
