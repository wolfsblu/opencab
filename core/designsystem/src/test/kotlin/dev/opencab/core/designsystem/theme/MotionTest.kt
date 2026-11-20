package dev.opencab.core.designsystem.theme

import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionTest {
    @Test
    fun `expressive motion is spring based when motion is allowed`() {
        val motion = CabMotion.Default

        assertTrue(motion.expressiveSpatialDefault<Float>() is SpringSpec)
        assertTrue(motion.standardSpatialDefault<Float>() is SpringSpec)
    }

    @Test
    fun `reduced motion collapses springs to snap`() {
        val motion = CabMotion(reduced = true)

        assertTrue(motion.expressiveSpatialDefault<Float>() is SnapSpec)
        assertTrue(motion.arcFill is SnapSpec)
    }

    @Test
    fun `speed arc eases linearly over 120 milliseconds`() {
        val spec = CabMotion.Default.arcFill

        assertTrue(spec is TweenSpec)
        assertEquals(120, (spec as TweenSpec).durationMillis)
    }

    @Test
    fun `throttle readout never uses a spatial spring`() {
        val spec = CabMotion.Default.arcFill

        assertTrue(spec is TweenSpec)
    }
}
