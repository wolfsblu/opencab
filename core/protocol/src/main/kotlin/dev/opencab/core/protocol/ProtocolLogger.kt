package dev.opencab.core.protocol

/**
 * Minimal logging seam for the pure protocol layer.
 *
 * The module deliberately has no Android or SLF4J dependency. Callers can adapt this to their
 * logging system; the default is a no-op.
 */
fun interface ProtocolLogger {
    fun warn(message: String)

    companion object {
        val None: ProtocolLogger = ProtocolLogger { }
    }
}
