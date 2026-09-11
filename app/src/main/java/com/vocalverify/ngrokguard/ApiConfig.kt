package com.vocalverify.ngrokguard

/** Accepts ngrok, Cloud Run, a custom domain, or an already-prefixed websocket origin. */
object ApiConfig {
    fun telephonyWebSocket(origin: String, deviceId: String): String? {
        var value = origin.trim().trimEnd('/')
        if (value.isBlank()) return null
        value = when {
            value.startsWith("wss://") || value.startsWith("ws://") -> value
            value.startsWith("https://") -> "wss://${value.removePrefix("https://")}"
            value.startsWith("http://") -> "ws://${value.removePrefix("http://")}"
            else -> "wss://$value"
        }
        return value.substringBefore("/ws/telephony").trimEnd('/') + "/ws/telephony/$deviceId"
    }
}
