package dev.opencab.core.data.connection

import dev.opencab.core.model.ConnectionError
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class ConnectionErrorMappingTest {
    @Test
    fun mapsTransportFailuresToTypedUserSafeErrors() {
        assertEquals(ConnectionError.TimedOut, SocketTimeoutException().toConnectionError())
        assertEquals(ConnectionError.TimedOut, GreetingTimeoutException().toConnectionError())
        assertEquals(ConnectionError.TimedOut, HeartbeatTimeoutException().toConnectionError())
        assertEquals(ConnectionError.ConnectionRefused, ConnectException().toConnectionError())
        assertEquals(ConnectionError.HostNotFound, UnknownHostException().toConnectionError())
        assertEquals(ConnectionError.Unreachable, NoRouteToHostException().toConnectionError())
        assertEquals(
            ConnectionError.LocalNetworkPermissionDenied,
            SecurityException().toConnectionError(),
        )
        assertEquals(ConnectionError.ServerClosed, SessionClosedException().toConnectionError())
        assertEquals(ConnectionError.NotWiThrottle, ServerRejectedException().toConnectionError())
        assertEquals(ConnectionError.ProtocolMismatch, ProtocolMismatchException().toConnectionError())
        assertEquals(
            ConnectionError.LocalNetworkPermissionDenied,
            SocketException("Permission denied").toConnectionError(),
        )
        assertEquals(
            ConnectionError.LocalNetworkPermissionDenied,
            SocketException("operation not permitted").toConnectionError(),
        )
        assertEquals(
            ConnectionError.Unreachable,
            SocketException("Network is unreachable").toConnectionError(),
        )
        assertEquals(ConnectionError.Unknown, IOException().toConnectionError())
    }

    @Test
    fun nonRetryableErrorsAreMarkedAsSuch() {
        assertEquals(false, ConnectionError.ProtocolMismatch.isRetryable())
        assertEquals(false, ConnectionError.LocalNetworkPermissionDenied.isRetryable())
        assertEquals(false, ConnectionError.NotWiThrottle.isRetryable())
        assertEquals(false, ConnectionError.UnsupportedVersion(found = "1.6").isRetryable())
        assertEquals(true, ConnectionError.ServerClosed.isRetryable())
        assertEquals(true, ConnectionError.TimedOut.isRetryable())
        assertEquals(true, ConnectionError.Unreachable.isRetryable())
    }

    @Test
    fun unsupportedVersionKeepsOnlyTheVersionText() {
        val error = UnsupportedVersionException("1.6").toConnectionError()

        assertEquals(ConnectionError.UnsupportedVersion(found = "1.6"), error)
    }
}
