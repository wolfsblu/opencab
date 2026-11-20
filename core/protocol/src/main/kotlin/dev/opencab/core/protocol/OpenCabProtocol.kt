package dev.opencab.core.protocol

/**
 * Describes the cab-bus protocol that OpenCab will speak.
 *
 * Framing, transport, and command semantics are intentionally absent from this milestone.
 * Keeping the descriptor here gives later protocol work a stable module boundary.
 */
object OpenCabProtocol {
    const val ID: String = "opencab"

    const val VERSION: Int = 0
}
