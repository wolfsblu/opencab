package dev.opencab.core.protocol.codec

import dev.opencab.core.protocol.LocoId
import dev.opencab.core.protocol.ThrottleKey
import dev.opencab.core.protocol.WiThrottleCommand

internal object FunctionCommandDecoder {
    fun decodeButton(
        key: ThrottleKey,
        target: LocoId,
        payload: String,
    ): WiThrottleCommand? {
        val parsed = parseFunctionBody(payload.substring(1)) ?: return null
        return if (parsed.second) {
            WiThrottleCommand.PressFunction(key, target, parsed.first)
        } else {
            WiThrottleCommand.ReleaseFunction(key, target, parsed.first)
        }
    }

    fun decodeForce(
        key: ThrottleKey,
        target: LocoId,
        payload: String,
    ): WiThrottleCommand? {
        val parsed = parseFunctionBody(payload.substring(1)) ?: return null
        return WiThrottleCommand.ForceFunction(key, target, parsed.first, parsed.second)
    }

    fun decodeMomentary(
        key: ThrottleKey,
        target: LocoId,
        payload: String,
    ): WiThrottleCommand? {
        val parsed = parseFunctionBody(payload.substring(1)) ?: return null
        return WiThrottleCommand.SetFunctionMomentary(key, target, parsed.first, parsed.second)
    }

    private fun parseFunctionBody(body: String): Pair<Int, Boolean>? {
        if (body.length < 2 || (body[0] != '0' && body[0] != '1')) return null
        val number = body.substring(1).toIntOrNull() ?: return null
        if (number !in WiThrottleCommand.MIN_FUNCTION..WiThrottleCommand.MAX_FUNCTION) return null
        return number to (body[0] == '1')
    }
}
