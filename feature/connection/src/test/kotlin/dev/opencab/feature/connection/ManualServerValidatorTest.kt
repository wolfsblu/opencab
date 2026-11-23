package dev.opencab.feature.connection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualServerValidatorTest {
    @Test
    fun acceptsHostnamesIpv4AndIpv6() {
        listOf("jmri.local", "192.168.1.24", "layout-1", "fe80::1", "[fe80::1]").forEach { host ->
            assertNull("expected $host to be valid", ManualServerValidator.validateHost(host))
        }
    }

    @Test
    fun rejectsEmptySchemesPathsAndSpaces() {
        assertEquals(ManualFieldError.Required, ManualServerValidator.validateHost("   "))
        assertEquals(
            ManualFieldError.InvalidHost,
            ManualServerValidator.validateHost("http://192.168.1.24"),
        )
        assertEquals(
            ManualFieldError.InvalidHost,
            ManualServerValidator.validateHost("192.168.1.24/withrottle"),
        )
        assertEquals(
            ManualFieldError.InvalidHost,
            ManualServerValidator.validateHost("192.168.1.24 extra"),
        )
        assertEquals(ManualFieldError.InvalidHost, ManualServerValidator.validateHost(":"))
    }

    @Test
    fun portMustBeNumericAndInRange() {
        assertNull(ManualServerValidator.validatePort("12090"))
        assertNull(ManualServerValidator.validatePort("1"))
        assertNull(ManualServerValidator.validatePort("65535"))
        assertEquals(ManualFieldError.Required, ManualServerValidator.validatePort(""))
        assertEquals(ManualFieldError.InvalidPort, ManualServerValidator.validatePort("abc"))
        assertEquals(ManualFieldError.InvalidPort, ManualServerValidator.validatePort("0"))
        assertEquals(ManualFieldError.InvalidPort, ManualServerValidator.validatePort("65536"))
    }

    @Test
    fun defaultPortIs12090() {
        assertEquals("12090", ManualEntryUiState().port)
        assertTrue(ManualServerValidator.validate("jmri.local", "12090").isValid)
    }
}
