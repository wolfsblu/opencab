package dev.opencab.core.data.connection

import dev.opencab.core.model.ConnectionMode
import dev.opencab.core.model.ConnectionState
import dev.opencab.core.model.ConnectionTarget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the single app-wide link.
 *
 * Live sessions use [WiThrottleSession]; demo sessions use [DemoTransport]. An established link
 * that drops is retried with capped backoff and reported as [ConnectionState.Reconnecting]. An
 * initial failure is reported as [ConnectionState.Failed] so the connection screen can offer a
 * concrete retry.
 */
@Singleton
class DefaultConnectionRepository @Inject constructor(
    private val demoTransport: DemoTransport,
) : ConnectionRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val state: StateFlow<ConnectionState> = mutableState.asStateFlow()

    private var sessionJob: Job? = null
    private var liveSession: WiThrottleSession? = null
    private var generation: Long = 0

    @Suppress("TooGenericExceptionCaught")
    override fun connect(target: ConnectionTarget) {
        generation += 1
        val attemptGeneration = generation
        stopTransport()
        mutableState.value = ConnectionState.Connecting(target)

        sessionJob = scope.launch {
            try {
                when (target.mode) {
                    ConnectionMode.DEMO -> runDemo(target, attemptGeneration)
                    ConnectionMode.LIVE -> runLive(target, attemptGeneration)
                }
            } catch (_: CancellationException) {
                // The canceller owns the next state.
            } catch (throwable: Throwable) {
                if (attemptGeneration == generation) {
                    mutableState.value = ConnectionState.Failed(
                        target = target,
                        error = throwable.toConnectionError(),
                    )
                }
            }
        }
    }

    override fun cancel() {
        generation += 1
        stopTransport()
        mutableState.value = ConnectionState.Disconnected
    }

    override fun disconnect() {
        cancel()
    }

    private suspend fun runDemo(
        target: ConnectionTarget,
        attemptGeneration: Long,
    ) {
        demoTransport.run {
            if (attemptGeneration == generation) {
                mutableState.value = ConnectionState.Connected(target)
            }
        }
    }

    @Suppress("NestedBlockDepth", "ThrowsCount", "TooGenericExceptionCaught")
    private suspend fun runLive(
        target: ConnectionTarget,
        attemptGeneration: Long,
    ) {
        var everConnected = false
        var attempt = 0

        while (currentCoroutineContext().isActive) {
            val session = WiThrottleSession(target.endpoint)
            liveSession = session
            try {
                session.run {
                    everConnected = true
                    attempt = 0
                    if (attemptGeneration == generation) {
                        mutableState.value = ConnectionState.Connected(target)
                    }
                }
                throw SessionClosedException()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                if (attemptGeneration != generation) return
                if (!everConnected) throw throwable

                attempt += 1
                mutableState.value = ConnectionState.Reconnecting(
                    target = target,
                    attempt = attempt,
                )
                delay(backoffMillis(attempt))
            } finally {
                session.close()
                if (liveSession === session) liveSession = null
            }
        }
    }

    private fun stopTransport() {
        sessionJob?.cancel()
        sessionJob = null
        liveSession?.close()
        liveSession = null
    }

    private fun backoffMillis(attempt: Int): Long {
        val shift = (attempt - 1).coerceIn(0, 4)
        return minOf(MAX_BACKOFF_MILLIS, BASE_BACKOFF_MILLIS shl shift)
    }

    private companion object {
        const val BASE_BACKOFF_MILLIS: Long = 500
        const val MAX_BACKOFF_MILLIS: Long = 8_000
    }
}
