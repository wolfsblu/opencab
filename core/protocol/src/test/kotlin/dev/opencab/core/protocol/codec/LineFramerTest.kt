package dev.opencab.core.protocol.codec

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LineFramerTest {
    @Test
    fun `splits linefeed frames`() {
        assertEquals(listOf("VN2.0", "PPA1"), frame("VN2.0\nPPA1\n"))
    }

    @Test
    fun `splits carriage-return linefeed frames without empty lines`() {
        assertEquals(listOf("VN2.0", "PPA1"), frame("VN2.0\r\nPPA1\r\n"))
    }

    @Test
    fun `splits bare carriage-return frames`() {
        assertEquals(listOf("VN2.0", "PPA1"), frame("VN2.0\rPPA1\r"))
    }

    @Test
    fun `accepts mixed newline styles`() {
        assertEquals(listOf("A", "B", "C"), frame("A\nB\r\nC\r"))
    }

    @Test
    fun `keeps a partial frame buffered`() {
        val framer = LineFramer()

        assertEquals(emptyList<String>(), framer.feed("VN2".toByteArray()))
        assertEquals(listOf("VN2.0"), framer.feed(".0\n".toByteArray()))
    }

    @Test
    fun `does not emit an extra frame when crlf crosses a read boundary`() {
        val framer = LineFramer()

        assertEquals(listOf("VN2.0"), framer.feed("VN2.0\r".toByteArray()))
        assertEquals(listOf("PPA1"), framer.feed("\nPPA1\r\n".toByteArray()))
    }

    @Test
    fun `keeps utf8 characters split across reads intact`() {
        val framer = LineFramer()
        val bytes = "HmGrüße\n".toByteArray(Charsets.UTF_8)
        val split = "HmGr".toByteArray(Charsets.UTF_8).size + 1

        assertEquals(emptyList<String>(), framer.feed(bytes.copyOfRange(0, split)))
        assertEquals(listOf("HmGrüße"), framer.feed(bytes.copyOfRange(split, bytes.size)))
    }

    @Test
    fun `ignores blank lines and empty chunks`() {
        assertEquals(emptyList<String>(), frame("", "\n", "\r\n", "\r"))
        assertEquals(listOf("A"), frame("\n\n", "A\n", "\n"))
    }

    @Test
    fun `reset discards a buffered partial frame`() {
        val framer = LineFramer()
        framer.feed("VN2".toByteArray())

        framer.reset()

        assertEquals(listOf("PPA1"), framer.feed("PPA1\n".toByteArray()))
    }

    @Test
    fun `split at every byte produces the same frames`() {
        val text = "VN2.0\r\nPPA1\n*10\r"
        val bytes = text.toByteArray(Charsets.UTF_8)
        val expected = listOf("VN2.0", "PPA1", "*10")

        for (split in 0..bytes.size) {
            val framer = LineFramer()
            val actual =
                framer.feed(bytes.copyOfRange(0, split)) +
                    framer.feed(bytes.copyOfRange(split, bytes.size))
            assertEquals("split at $split", expected, actual)
        }
    }

    @Test
    fun `never emits while no terminator has arrived`() {
        val framer = LineFramer()

        assertTrue(framer.feed("M0AL341<;>V23".toByteArray()).isEmpty())
        assertTrue(framer.feed("M0AL341<;>V24".toByteArray()).isEmpty())
        assertEquals(
            listOf("M0AL341<;>V23M0AL341<;>V24"),
            framer.feed("\n".toByteArray()),
        )
    }

    private fun frame(vararg chunks: String): List<String> {
        val framer = LineFramer()
        return chunks.flatMap { framer.feed(it.toByteArray(Charsets.UTF_8)) }
    }
}
