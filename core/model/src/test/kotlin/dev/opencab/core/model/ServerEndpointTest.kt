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
    fun hostMustBeAPlainAddress() {
        val invalidHosts = listOf(
            "http://jmri.local",
            "jmri.local/path",
            "jmri.local extra",
            "  ",
            ":",
            "jmri.local:12090",
            "[fe80::1]:12090",
        )
        invalidHosts.forEach { host ->
            assertThrows("expected $host to be rejected", IllegalArgumentException::class.java) {
                ServerEndpoint(host)
            }
        }
    }

    @Test
    fun hostLengthIsBounded() {
        assertThrows(IllegalArgumentException::class.java) {
            ServerEndpoint("a".repeat(ServerEndpoint.MAX_HOST_LENGTH + 1))
        }
    }

    @Test
    fun savedServerRequiresAnIdAndName() {
        assertThrows(IllegalArgumentException::class.java) {
            SavedServer(id = "", name = "Home", endpoint = ServerEndpoint("jmri.local"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            SavedServer(id = "id", name = " ", endpoint = ServerEndpoint("jmri.local"))
        }
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
