package dev.opencab.ui.navigation

import dev.opencab.feature.connection.CONNECTION_ROUTE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TopLevelDestinationsTest {
    @Test
    fun `top level routes are unique`() {
        val routes = topLevelDestinations.map { destination -> destination.route }

        assertEquals(routes.size, routes.toSet().size)
    }

    @Test
    fun `connection route is not a top level destination`() {
        val isTopLevel = topLevelDestinations.any { destination -> destination.route == CONNECTION_ROUTE }

        assertFalse(isTopLevel)
    }
}
