package dev.opencab.core.protocol.codec

import java.io.ByteArrayOutputStream

/**
 * Incremental byte framer for WiThrottle's line-oriented wire format.
 *
 * Accepts LF, CR, and CRLF terminators, including a CR and LF split across two reads. Empty lines
 * are discarded. A partial frame remains buffered until its terminator arrives.
 */
internal class LineFramer {
    private val buffer = ByteArrayOutputStream()
    private var previousWasCarriageReturn = false

    fun feed(chunk: ByteArray): List<String> {
        val lines = mutableListOf<String>()
        for (byte in chunk) {
            when (byte.toInt() and BYTE_MASK) {
                CARRIAGE_RETURN -> {
                    emitLine(lines)
                    previousWasCarriageReturn = true
                }

                LINE_FEED -> {
                    if (previousWasCarriageReturn) {
                        previousWasCarriageReturn = false
                    } else {
                        emitLine(lines)
                    }
                }

                else -> {
                    previousWasCarriageReturn = false
                    buffer.write(byte.toInt() and BYTE_MASK)
                }
            }
        }
        return lines
    }

    fun reset() {
        buffer.reset()
        previousWasCarriageReturn = false
    }

    private fun emitLine(lines: MutableList<String>) {
        if (buffer.size() == 0) return
        lines += buffer.toString(Charsets.UTF_8)
        buffer.reset()
    }

    private companion object {
        const val BYTE_MASK = 0xFF
        const val CARRIAGE_RETURN = 0x0D
        const val LINE_FEED = 0x0A
    }
}
