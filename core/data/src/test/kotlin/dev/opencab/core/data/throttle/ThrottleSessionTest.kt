package dev.opencab.core.data.throttle

import dev.opencab.core.data.connection.ConnectionRepository
import dev.opencab.core.data.connection.DccClient
import dev.opencab.core.model.ConnectionState
import dev.opencab.core.model.ConnectionTarget
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ThrottleSessionTest {
    @Test
    fun attachesToTheActiveClientAndDetachesWhenTheSessionEnds() = runTest {
        val repository = FakeConnectionRepository()
        val client = FakeDccClient { testScheduler.currentTime }
        val session = ThrottleSession(
            connectionRepository = repository,
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
        )
        runCurrent()
        assertEquals(ThrottleLinkState.UNLINKED, session.controller.uiState.value.link)

        repository.setClient(client)
        runCurrent()
        assertEquals(ThrottleLinkState.DISCONNECTED, session.controller.uiState.value.link)

        client.emitState(connected())
        runCurrent()
        assertEquals(ThrottleLinkState.CONNECTED, session.controller.uiState.value.link)

        repository.setClient(null)
        runCurrent()
        assertEquals(ThrottleLinkState.UNLINKED, session.controller.uiState.value.link)
    }

    private class FakeConnectionRepository : ConnectionRepository {
        private val mutableState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
        override val state: StateFlow<ConnectionState> = mutableState.asStateFlow()

        private val mutableClient = MutableStateFlow<DccClient?>(null)
        override val activeClient: StateFlow<DccClient?> = mutableClient.asStateFlow()

        override fun connect(target: ConnectionTarget) {
            mutableState.value = ConnectionState.Connected(target)
        }

        override fun cancel() {
            mutableState.value = ConnectionState.Disconnected
        }

        override fun disconnect() {
            mutableState.value = ConnectionState.Disconnected
        }

        fun setClient(client: DccClient?) {
            mutableClient.value = client
        }
    }
}
