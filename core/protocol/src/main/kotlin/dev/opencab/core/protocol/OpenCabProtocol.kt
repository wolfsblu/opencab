package dev.opencab.core.protocol

/**
 * Stable descriptor for the WiThrottle protocol subset implemented by this module.
 *
 * The wire codec, typed messages, and pure session reducer live alongside this descriptor. No
 * transport, socket, thread, or Android type is part of this module.
 */
object OpenCabProtocol {
    const val ID: String = "opencab"

    const val VERSION: Int = 1
}
