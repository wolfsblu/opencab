package dev.opencab.feature.connection

import dev.opencab.core.model.ServerEndpoint

/** Result of validating the manual-entry fields. */
internal data class ManualValidation(
    val hostError: ManualFieldError? = null,
    val portError: ManualFieldError? = null,
) {
    val isValid: Boolean
        get() = hostError == null && portError == null
}

/**
 * Host and port validation for manual entry.
 *
 * The host check is intentionally permissive about DNS names and IPv6, and intentionally strict
 * about the mistakes people actually make: pasting `http://…`, including a path, or leaving
 * spaces in the field. It never performs DNS resolution.
 */
internal object ManualServerValidator {
    private val allowedHostCharacters = Regex("^[A-Za-z0-9._:%\\-\\[\\]]+$")

    fun validate(
        host: String,
        port: String,
    ): ManualValidation = ManualValidation(
        hostError = validateHost(host),
        portError = validatePort(port),
    )

    fun validateHost(host: String): ManualFieldError? {
        val trimmed = host.trim()
        return when {
            trimmed.isEmpty() -> ManualFieldError.Required
            trimmed.length > MAX_HOST_LENGTH -> ManualFieldError.InvalidHost
            !allowedHostCharacters.matches(trimmed) -> ManualFieldError.InvalidHost
            trimmed.none { it.isLetterOrDigit() } -> ManualFieldError.InvalidHost
            trimmed.count { it == ':' } == 1 -> ManualFieldError.InvalidHost
            trimmed.contains("]:") -> ManualFieldError.InvalidHost
            else -> null
        }
    }

    fun validatePort(port: String): ManualFieldError? {
        val trimmed = port.trim()
        if (trimmed.isEmpty()) return ManualFieldError.Required
        val value = trimmed.toIntOrNull() ?: return ManualFieldError.InvalidPort
        return if (value in ServerEndpoint.MIN_PORT..ServerEndpoint.MAX_PORT) {
            null
        } else {
            ManualFieldError.InvalidPort
        }
    }

    private const val MAX_HOST_LENGTH = 253
}
