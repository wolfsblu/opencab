package dev.opencab.core.protocol

/**
 * Rejects text that would break newline framing. This is a programming-error guard for outbound
 * commands; inbound text is never rejected by the codec.
 */
internal fun requireSingleLine(value: String, field: String) {
    require('\r' !in value && '\n' !in value) { "$field must not contain a newline" }
}
