package dev.opencab.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ServerEndpointTest {
    @Test
    fun displayUsesHostAndPort() {
        assertEquals("192.168.1.24:12090", ServerEndpoint("192.168.1.24").display)
        assertEquals("jmri.local:12091", ServerEndpoint("jmri.local", 12091).display)
    }

    @Test
    fun ipv6LiteralsAreBracketedExactlyOnce() {
        assertEquals("[fe80::1]:12090", ServerEndpoint("[fe80::1]").display)
        assertEquals("[fe80::1]:12090", ServerEndpoint("fe80::1").display)
    }

    @Test
    fun portMustBeInRange() {
        assertThrows(IllegalArgumentException::class.java) {
            ServerEndpoint("jmri.local", 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ServerEndpoint("jmri.local", 65536)
        }
    }
}
