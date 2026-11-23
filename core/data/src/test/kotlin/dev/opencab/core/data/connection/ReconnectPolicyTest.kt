package dev.opencab.core.data.connection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ReconnectPolicyTest {
    @Test
    fun delayDoublesAndCapsWithoutJitter() {
        val policy = ReconnectPolicy(
            baseMillis = 500,
            maxMillis = 8_000,
            jitterFraction = 0.0,
            random = Random(0),
        )

        assertEquals(500, policy.delayMillis(1))
        assertEquals(1_000, policy.delayMillis(2))
        assertEquals(2_000, policy.delayMillis(3))
        assertEquals(4_000, policy.delayMillis(4))
        assertEquals(8_000, policy.delayMillis(5))
        assertEquals(8_000, policy.delayMillis(20))
    }

    @Test
    fun jitterStaysBetweenHalfAndTheFullExponentialDelay() {
        val policy = ReconnectPolicy(
            baseMillis = 1_000,
            maxMillis = 8_000,
            jitterFraction = 0.5,
            random = Random(42),
        )

        val delays = (1..30).map { attempt ->
            val expectedCeiling = minOf(8_000L, 1_000L shl (attempt - 1).coerceAtMost(4))
            val delay = policy.delayMillis(attempt)
            assertTrue(
                "attempt $attempt delay $delay outside 50–100% of $expectedCeiling",
                delay in (expectedCeiling / 2)..expectedCeiling,
            )
            delay
        }

        assertTrue("jitter should vary the delays", delays.distinct().size > 1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun attemptMustBePositive() {
        ReconnectPolicy(500, 8_000, 0.5, Random(0)).delayMillis(0)
    }
}
