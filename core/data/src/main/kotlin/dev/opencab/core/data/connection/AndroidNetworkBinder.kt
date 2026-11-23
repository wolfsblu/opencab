package dev.opencab.core.data.connection

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production [NetworkBinder] for a phone that is attached to a layout's Wi-Fi access point while
 * cellular remains the default route.
 *
 * It chooses a non-restricted Wi-Fi or Ethernet network, binds the throttle socket to it, and
 * resolves the layout host through that network. The layout's AP does not need validated internet
 * access. If no such network exists, the socket uses the platform default as before.
 */
@Singleton
class AndroidNetworkBinder @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : NetworkBinder {
    override fun bind(
        socket: Socket,
        host: String,
        port: Int,
    ): InetSocketAddress? {
        val network = localNetwork() ?: return null
        return runCatching {
            network.bindSocket(socket)
            val addresses = network.getAllByName(host)
            val address = addresses.firstOrNull { it is Inet4Address } ?: addresses.firstOrNull()
                ?: return null
            InetSocketAddress(address, port)
        }.getOrNull()
    }

    @Suppress("DEPRECATION")
    private fun localNetwork(): Network? {
        val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return null
        return connectivity.allNetworks.firstOrNull { network ->
            val capabilities = connectivity.getNetworkCapabilities(network) ?: return@firstOrNull false
            val localTransport = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            localTransport &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
        }
    }
}
