package dev.opencab.core.data.connection

import dev.opencab.core.model.ServerEndpoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.Closeable
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicInteger

/**
 * A real loopback TCP server for transport tests.
 *
 * Each accepted socket gets its own [FakeConnection]. [handler] runs on `Dispatchers.IO`, so a
 * handler can wait for client commands while sending scripted server lines. Closing the server
 * cancels every handler and closes the listening socket.
 */
internal class FakeTcpServer(
    private val handler: suspend (FakeConnection) -> Unit,
) : Closeable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val serverSocket = ServerSocket(
        0,
        50,
        InetAddress.getByName(LOOPBACK),
    )

    val endpoint: ServerEndpoint = ServerEndpoint(LOOPBACK, serverSocket.localPort)

    private val connections = AtomicInteger(0)

    init {
        scope.launch {
            acceptConnections()
        }
    }

    val connectionCount: Int
        get() = connections.get()

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private suspend fun acceptConnections() {
        while (scope.isActive) {
            val socket = try {
                serverSocket.accept()
            } catch (_: Exception) {
                return
            }
            val connection = FakeConnection(socket, connections.incrementAndGet())
            scope.launch {
                try {
                    handler(connection)
                } catch (_: Exception) {
                    // Tests assert on the client, not on handler teardown races.
                } finally {
                    connection.close()
                }
            }
        }
    }

    override fun close() {
        runCatching { serverSocket.close() }
        scope.cancel()
    }

    private companion object {
        const val LOOPBACK = "127.0.0.1"
    }
}

internal class FakeConnection(
    private val socket: Socket,
    val index: Int,
) : Closeable {
    private val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
    private val output: OutputStream = socket.getOutputStream()

    fun send(line: String) {
        synchronized(output) {
            output.write((line + "\n").toByteArray(Charsets.UTF_8))
            output.flush()
        }
    }

    fun sendRaw(text: String) {
        synchronized(output) {
            output.write(text.toByteArray(Charsets.UTF_8))
            output.flush()
        }
    }

    fun readLine(timeoutMillis: Int = DEFAULT_TIMEOUT_MILLIS): String? {
        socket.soTimeout = timeoutMillis
        return try {
            reader.readLine()
        } catch (_: SocketTimeoutException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    fun awaitLine(
        expected: String,
        timeoutMillis: Int = DEFAULT_TIMEOUT_MILLIS,
    ) {
        val actual = readLine(timeoutMillis)
        check(actual == expected) { "Expected '$expected' but received '$actual'" }
    }

    /** Skips heartbeats and other traffic until a line starts with [prefix]. */
    fun awaitCommand(
        prefix: String,
        timeoutMillis: Int = DEFAULT_TIMEOUT_MILLIS,
    ): String {
        val deadline = System.nanoTime() + timeoutMillis * NANOS_PER_MILLI
        while (System.nanoTime() < deadline) {
            val remaining = ((deadline - System.nanoTime()) / NANOS_PER_MILLI).toInt()
            val line = readLine(remaining.coerceAtLeast(1)) ?: break
            if (line.startsWith(prefix)) return line
        }
        error("No '$prefix' command within $timeoutMillis ms")
    }

    /** Blocks until the client closes the connection. */
    fun awaitClose(timeoutMillis: Int = DEFAULT_TIMEOUT_MILLIS) {
        socket.soTimeout = timeoutMillis
        try {
            while (reader.read() != -1) {
                // Consume the remaining client traffic.
            }
        } catch (_: SocketTimeoutException) {
            error("Client did not close within $timeoutMillis ms")
        } catch (_: Exception) {
            // A reset is a close.
        }
    }

    override fun close() {
        runCatching { socket.close() }
    }

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS: Int = 5_000
        private const val NANOS_PER_MILLI: Long = 1_000_000
    }
}
