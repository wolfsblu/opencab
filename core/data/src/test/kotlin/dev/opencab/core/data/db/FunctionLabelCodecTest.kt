package dev.opencab.core.data.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FunctionLabelCodecTest {
    @Test
    fun `round trips sparse labels and preserves unicode`() {
        val labels = mapOf(0 to "Headlight", 2 to "Bell", 12 to "Wasserstand — prüfen")

        val decoded = FunctionLabelCodec.decode(FunctionLabelCodec.encode(labels))

        assertEquals(labels, decoded)
    }

    @Test
    fun `escapes line breaks and backslashes`() {
        val labels = mapOf(1 to "line\nbreak", 2 to "back\\slash")

        val encoded = FunctionLabelCodec.encode(labels)

        assertTrue(encoded.contains("\\n"))
        assertTrue(encoded.contains("\\\\"))
        assertEquals(labels, FunctionLabelCodec.decode(encoded))
    }

    @Test
    fun `blank labels and negative numbers are dropped`() {
        val encoded = FunctionLabelCodec.encode(mapOf(0 to " ", 1 to "Whistle", -2 to "Nope"))

        assertEquals("1=Whistle", encoded)
        assertTrue(FunctionLabelCodec.decode("").isEmpty())
        assertTrue(FunctionLabelCodec.decode("nonsense").isEmpty())
    }
}
