package dev.opencab.core.data.connection

import java.net.InetSocketAddress
import java.net.Socket

/**
 * Binds an outbound throttle socket to the local network that can reach the layout.
 *
 * Android keeps cellular as the default network when a layout's access point has no internet
 * connection. An unbound socket then leaves through cellular and never reaches a private address
 * such as `192.168.4.1`. Implementations bind the socket to the Wi-Fi/Ethernet network and resolve
 * the host through that network before the caller connects.
 *
 * Returning `null` means "use the system default network and DNS". The direct implementation is
 * the test/emulator default; [AndroidNetworkBinder] is the production implementation.
 */
fun interface NetworkBinder {
    fun bind(
        socket: Socket,
        host: String,
        port: Int,
    ): InetSocketAddress?

    companion object {
        val Direct: NetworkBinder = NetworkBinder { _, _, _ -> null }
    }
}
