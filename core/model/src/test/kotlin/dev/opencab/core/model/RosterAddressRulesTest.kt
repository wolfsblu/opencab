package dev.opencab.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RosterAddressRulesTest {
    @Test
    fun `short addresses accept the full short range`() {
        val validation = RosterAddressRules.validate("127", RosterAddressType.SHORT)

        val valid = validation as AddressValidation.Valid
        assertEquals(RosterAddress(127, RosterAddressType.SHORT), valid.address)
        assertFalse(valid.isAmbiguous)
    }

    @Test
    fun `an untyped number in the overlap defaults to short and is flagged ambiguous`() {
        val validation = RosterAddressRules.validate("42")

        val valid = validation as AddressValidation.Valid
        assertEquals(RosterAddress(42, RosterAddressType.SHORT), valid.address)
        assertTrue(valid.isAmbiguous)
    }

    @Test
    fun `an explicit long address in the overlap is valid but flagged ambiguous`() {
        val valid = RosterAddressRules.validate("42", RosterAddressType.LONG) as AddressValidation.Valid

        assertEquals(RosterAddress(42, RosterAddressType.LONG), valid.address)
        assertTrue(valid.isAmbiguous)
    }

    @Test
    fun `an explicit short address above the short range suggests long`() {
        val invalid = RosterAddressRules.validate("200", RosterAddressType.SHORT) as AddressValidation.Invalid

        assertEquals(AddressValidationError.SHORT_OUT_OF_RANGE, invalid.error)
        assertEquals(RosterAddressType.LONG, invalid.suggestedType)
    }

    @Test
    fun `an untyped number above the short range resolves to long`() {
        val valid = RosterAddressRules.validate("3412") as AddressValidation.Valid

        assertEquals(RosterAddress(3412, RosterAddressType.LONG), valid.address)
        assertFalse(valid.isAmbiguous)
    }

    @Test
    fun `addresses above the long maximum are rejected`() {
        val invalid = RosterAddressRules.validate("10240") as AddressValidation.Invalid

        assertEquals(AddressValidationError.LONG_OUT_OF_RANGE, invalid.error)
        assertNull(invalid.suggestedType)
    }

    @Test
    fun `zero and negative numbers are rejected`() {
        assertEquals(
            AddressValidationError.OUT_OF_RANGE,
            (RosterAddressRules.validate("0") as AddressValidation.Invalid).error,
        )
        assertEquals(
            AddressValidationError.OUT_OF_RANGE,
            (RosterAddressRules.validate("-1") as AddressValidation.Invalid).error,
        )
    }

    @Test
    fun `blank and non numeric input have distinct errors`() {
        assertEquals(
            AddressValidationError.REQUIRED,
            (RosterAddressRules.validate("  ") as AddressValidation.Invalid).error,
        )
        assertEquals(
            AddressValidationError.NOT_A_NUMBER,
            (RosterAddressRules.validate("3A") as AddressValidation.Invalid).error,
        )
    }

    @Test
    fun `wire text keeps the short and long distinction`() {
        assertEquals("S3", RosterAddress(3, RosterAddressType.SHORT).wireText)
        assertEquals("L3412", RosterAddress(3412, RosterAddressType.LONG).wireText)
        assertEquals("3 · short", RosterAddress(3, RosterAddressType.SHORT).displayText)
    }

    @Test
    fun `local function labels override server labels and defaults`() {
        val loco = RosterLoco(
            id = "one",
            name = " Black Five ",
            address = RosterAddress(3412, RosterAddressType.LONG),
            localFunctionLabels = mapOf(1 to "Whistle", 2 to " "),
            serverFunctionLabels = mapOf(1 to "Horn", 2 to "Bell", 3 to "Coupler"),
        )

        assertEquals("Black Five", loco.displayName)
        assertEquals("Whistle", loco.functionLabel(1))
        assertEquals("Bell", loco.functionLabel(2))
        assertEquals("Coupler", loco.functionLabel(3))
        assertEquals("F4", loco.functionLabel(4))
    }

    @Test
    fun `an unnamed loco falls back to its address`() {
        val loco = RosterLoco(
            id = "one",
            name = " ",
            address = RosterAddress(3, RosterAddressType.SHORT),
        )

        assertEquals("Loco 3", loco.displayName)
    }
}
