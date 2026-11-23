package dev.opencab.core.data.connection

import dev.opencab.core.model.ConnectionError
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class ConnectionErrorMappingTest {
    @Test
    fun mapsTransportFailuresToTypedUserSafeErrors() {
        assertEquals(ConnectionError.TimedOut, SocketTimeoutException().toConnectionError())
        assertEquals(ConnectionError.TimedOut, GreetingTimeoutException().toConnectionError())
        assertEquals(ConnectionError.ConnectionRefused, ConnectException().toConnectionError())
        assertEquals(ConnectionError.HostNotFound, UnknownHostException().toConnectionError())
        assertEquals(ConnectionError.Unreachable, NoRouteToHostException().toConnectionError())
        assertEquals(
            ConnectionError.LocalNetworkPermissionDenied,
            SecurityException().toConnectionError(),
        )
        assertEquals(ConnectionError.ServerClosed, SessionClosedException().toConnectionError())
        assertEquals(ConnectionError.NotWiThrottle, ServerRejectedException().toConnectionError())
        assertEquals(ConnectionError.Unknown, IOException().toConnectionError())
    }

    @Test
    fun unsupportedVersionKeepsOnlyTheVersionText() {
        val error = UnsupportedVersionException("1.6").toConnectionError()

        assertEquals(ConnectionError.UnsupportedVersion(found = "1.6"), error)
    }
}
