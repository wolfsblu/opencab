package dev.opencab.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContrastTest {
    @Test
    fun `black on white is the maximum ratio`() {
        assertEquals(21f, CabContrast.ratio(Color.Black, Color.White), 0.01f)
    }

    @Test
    fun `identical colours have a ratio of one`() {
        assertEquals(1f, CabContrast.ratio(Color(0xFF336699), Color(0xFF336699)), 0.001f)
    }

    @Test
    fun `branded light scheme is readable`() {
        assertTrue(CabContrast.isReadable(LightColorScheme))
    }

    @Test
    fun `branded dark scheme is readable`() {
        assertTrue(CabContrast.isReadable(DarkColorScheme))
    }

    @Test
    fun `high contrast schemes are readable`() {
        assertTrue(CabContrast.isReadable(LightHighContrastColorScheme))
        assertTrue(CabContrast.isReadable(DarkHighContrastColorScheme))
    }
}
