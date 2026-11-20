package dev.opencab.core.protocol

/**
 * WiThrottle protocol version from the `VN` greeting.
 *
 * Ordering is by major then minor. [raw] preserves the server text for diagnostics.
 */
data class ProtocolVersion(
    val major: Int,
    val minor: Int,
    val raw: String = "$major.$minor",
) : Comparable<ProtocolVersion> {
    init {
        require(major >= 0 && minor >= 0) { "Protocol version must be non-negative" }
    }

    override fun compareTo(other: ProtocolVersion): Int = compareValuesBy(this, other, { it.major }, { it.minor })

    companion object {
        val V2_0: ProtocolVersion = ProtocolVersion(2, 0, "2.0")

        fun parse(text: String): ProtocolVersion? {
            val trimmed = text.trim()
            val parts = trimmed.split('.')
            if (parts.size !in 1..2) return null
            val major = parts[0].toIntOrNull() ?: return null
            val minor = if (parts.size == 2) parts[1].toIntOrNull() ?: return null else 0
            return if (major >= 0 && minor >= 0) ProtocolVersion(major, minor, trimmed) else null
        }
    }
}
