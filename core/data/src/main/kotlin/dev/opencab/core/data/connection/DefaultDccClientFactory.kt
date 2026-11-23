package dev.opencab.core.data.connection

import javax.inject.Inject
import javax.inject.Singleton

/** Production factory: a fresh [WiThrottleClient] per connection attempt. */
@Singleton
class DefaultDccClientFactory @Inject constructor() : DccClientFactory {
    override fun create(): DccClient = WiThrottleClient()
}
