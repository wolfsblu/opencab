package dev.opencab.core.data.connection

import dev.opencab.core.model.SavedServer
import dev.opencab.core.model.ServerEndpoint
import java.util.Base64

/**
 * Line-oriented persistence codec for saved layouts.
 *
 * Each field is URL-safe base64 of UTF-8, separated by `.`. Layout names may contain any
 * character, including the separators, without breaking the record. Unknown or damaged lines are
 * skipped rather than failing the whole store.
 */
internal object SavedServerCodec {
    private const val RECORD_SEPARATOR = '\n'
    private const val FIELD_SEPARATOR = '.'
    private const val FIELD_COUNT = 5

    fun encode(servers: List<SavedServer>): String = servers.joinToString(RECORD_SEPARATOR.toString()) { server ->
        listOf(
            server.id,
            server.name,
            server.endpoint.host,
            server.endpoint.port.toString(),
            server.lastConnectedEpochMillis?.toString().orEmpty(),
        ).joinToString(FIELD_SEPARATOR.toString()) { encodeField(it) }
    }

    fun decode(text: String): List<SavedServer> = text
        .lineSequence()
        .mapNotNull(::decodeRecord)
        .toList()

    @Suppress("ReturnCount")
    private fun decodeRecord(line: String): SavedServer? {
        if (line.isBlank()) return null
        val fields = line.split(FIELD_SEPARATOR).mapNotNull(::decodeField)
        val port = fields.getOrNull(3)?.toIntOrNull()
        if (fields.size != FIELD_COUNT || port == null) return null
        if (port !in ServerEndpoint.MIN_PORT..ServerEndpoint.MAX_PORT) return null
        val id = fields[0]
        val name = fields[1]
        val host = fields[2]
        if (id.isBlank() || name.isBlank() || host.isBlank()) return null
        val lastConnected = fields[4].takeIf { it.isNotEmpty() }?.toLongOrNull()
        return SavedServer(
            id = id,
            name = name,
            endpoint = ServerEndpoint(host = host, port = port),
            lastConnectedEpochMillis = lastConnected,
        )
    }

    private fun encodeField(value: String): String = Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(value.toByteArray(Charsets.UTF_8))

    private fun decodeField(value: String): String? = runCatching {
        String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)
    }.getOrNull()
}
