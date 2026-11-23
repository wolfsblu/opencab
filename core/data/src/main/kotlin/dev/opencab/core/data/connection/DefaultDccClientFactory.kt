package dev.opencab.core.data.connection

import javax.inject.Inject
import javax.inject.Singleton

/** Production factory: a fresh [WiThrottleClient] per connection attempt. */
@Singleton
class DefaultDccClientFactory @Inject constructor(
    private val networkBinder: NetworkBinder,
) : DccClientFactory {
    override fun create(): DccClient = WiThrottleClient(networkBinder = networkBinder)
}
