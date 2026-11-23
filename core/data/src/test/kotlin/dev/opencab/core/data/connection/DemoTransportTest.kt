package dev.opencab.core.data.connection

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DemoTransportTest {
    @Test
    fun runSignalsReadyAfterTheSimulatedHandshakeAndStaysAlive() = runTest {
        val transport = DemoTransport()
        var readyCount = 0

        val job = launch {
            transport.run { readyCount += 1 }
        }

        runCurrent()
        assertEquals(0, readyCount)

        advanceTimeBy(DemoTransport.CONNECT_DELAY_MILLIS + 1)
        runCurrent()
        assertEquals(1, readyCount)
        assertEquals(false, job.isCompleted)

        job.cancel()
    }
}
