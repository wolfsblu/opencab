package dev.opencab.feature.roster

import dev.opencab.core.model.RosterAddress
import dev.opencab.core.model.RosterAddressType
import dev.opencab.core.model.RosterLoco
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RosterSearchTest {
    private val blackFive = loco(
        id = "black-five",
        name = "Black Five",
        number = 3412,
        type = RosterAddressType.LONG,
        favorite = true,
        lastUsed = 5_000L,
    )
    private val jinty = loco(
        id = "jinty",
        name = "Jinty",
        number = 3,
        type = RosterAddressType.SHORT,
        lastUsed = 9_000L,
    )
    private val class08 = loco(
        id = "class-08",
        name = "Class 08",
        number = 8,
        type = RosterAddressType.SHORT,
        lastUsed = 7_000L,
    )
    private val diesel = loco(
        id = "diesel",
        name = "Deltic",
        number = 5502,
        type = RosterAddressType.LONG,
    )

    private val all = listOf(blackFive, jinty, class08, diesel)

    @Test
    fun `favorites and recents are grouped without duplicates`() {
        val sections = RosterSearch.sections(all, "")

        assertEquals(
            listOf(PickerSectionKind.Favorites, PickerSectionKind.Recents, PickerSectionKind.All),
            sections.map { it.kind },
        )
        assertEquals(listOf(blackFive), sections.first().locos)
        assertEquals(listOf(jinty, class08), sections[1].locos)
        assertEquals(listOf(diesel), sections[2].locos)
    }

    @Test
    fun `recents are capped and sorted newest first`() {
        val many = (1..8).map { index ->
            loco(
                id = "recent-$index",
                name = "Recent $index",
                number = index,
                type = RosterAddressType.SHORT,
                lastUsed = index.toLong(),
            )
        }

        val recents = RosterSearch.sections(many, "").first { it.kind == PickerSectionKind.Recents }

        assertEquals(4, recents.locos.size)
        assertEquals(listOf("Recent 8", "Recent 7", "Recent 6", "Recent 5"), recents.locos.map { it.name })
    }

    @Test
    fun `search matches names case insensitively`() {
        assertEquals(listOf(blackFive), RosterSearch.filter(all, "black"))
        assertEquals(listOf(blackFive), RosterSearch.filter(all, "FIVE"))
    }

    @Test
    fun `search matches address digits and short or long prefix`() {
        assertEquals(listOf(jinty), RosterSearch.filter(all, "S3"))
        assertEquals(listOf(blackFive), RosterSearch.filter(all, "3412"))
        assertEquals(listOf(blackFive), RosterSearch.filter(all, "L3412"))
        assertEquals(listOf(blackFive), RosterSearch.filter(all, "L 3412"))
    }

    @Test
    fun `a query returns one matches section and no result is an empty list`() {
        val sections = RosterSearch.sections(all, "jinty")

        assertEquals(1, sections.size)
        assertEquals(PickerSectionKind.Matches, sections.single().kind)
        assertEquals(listOf(jinty), sections.single().locos)
        assertTrue(RosterSearch.sections(all, "nothing").isEmpty())
    }

    @Test
    fun `empty roster produces no sections`() {
        assertTrue(RosterSearch.sections(emptyList(), "").isEmpty())
        assertTrue(RosterSearch.sections(emptyList(), "3").isEmpty())
    }

    @Test
    fun `roster items sort favorites then recents then name and mark the active address`() {
        val items = listOf(diesel, class08, blackFive, jinty).toRosterItems(activeAddressKey = "S3")

        assertEquals(listOf("Black Five", "Jinty", "Class 08", "Deltic"), items.map { it.name })
        assertTrue(items.first { it.id == "black-five" }.isFavorite)
        assertTrue(items.first { it.id == "jinty" }.isActive)
    }

    private fun loco(
        id: String,
        name: String,
        number: Int,
        type: RosterAddressType,
        favorite: Boolean = false,
        lastUsed: Long? = null,
    ): RosterLoco = RosterLoco(
        id = id,
        name = name,
        address = RosterAddress(number, type),
        isFavorite = favorite,
        lastUsedEpochMillis = lastUsed,
    )
}
