package dev.opencab.core.data.db

/**
 * A compact, deterministic encoding for sparse function labels.
 *
 * One `number=label` entry per line, sorted by function number. Backslashes and line breaks are
 * escaped so a label can be any single-line text without pulling a JSON dependency into the
 * persistence layer.
 */
object FunctionLabelCodec {
    fun encode(labels: Map<Int, String>): String = labels
        .filter { (number, label) -> number >= 0 && label.isNotBlank() }
        .toSortedMap()
        .entries
        .joinToString(separator = "\n") { (number, label) -> "$number=${escape(label)}" }

    fun decode(encoded: String): Map<Int, String> {
        if (encoded.isEmpty()) return emptyMap()
        return buildMap {
            encoded.lineSequence().forEach { line ->
                val separator = line.indexOf('=')
                if (separator <= 0) return@forEach
                val number = line.substring(0, separator).toIntOrNull() ?: return@forEach
                val label = unescape(line.substring(separator + 1))
                if (number >= 0 && label.isNotBlank()) put(number, label)
            }
        }
    }

    private fun escape(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\n", "\\n")
        .replace("\r", "\\r")

    private fun unescape(value: String): String {
        val result = StringBuilder(value.length)
        var index = 0
        while (index < value.length) {
            val character = value[index]
            if (character == '\\' && index + 1 < value.length) {
                when (value[index + 1]) {
                    'n' -> result.append('\n')

                    'r' -> result.append('\r')

                    '\\' -> result.append('\\')

                    else -> {
                        result.append(character)
                        result.append(value[index + 1])
                    }
                }
                index += 2
            } else {
                result.append(character)
                index += 1
            }
        }
        return result.toString()
    }
}
