package com.vocalverify.ngrokguard

/** Accepts ngrok, Cloud Run, a custom domain, or an already-prefixed websocket origin. */
object ApiConfig {
    fun telephonyWebSocket(origin: String, deviceId: String): String? {
        var value = origin.trim().trimEnd('/')
        if (value.isBlank()) return null
        // Ensure scheme is http/https for OkHttp Request.Builder (OkHttp automatically upgrades to ws/wss for WebSocket)
        value = when {
            value.startsWith("wss://", ignoreCase = true) -> "https://" + value.substring(6)
            value.startsWith("ws://", ignoreCase = true) -> "http://" + value.substring(5)
            value.startsWith("https://", ignoreCase = true) || value.startsWith("http://", ignoreCase = true) -> value
            else -> "https://$value"
        }
        return value.substringBefore("/ws/telephony").trimEnd('/') + "/ws/telephony/$deviceId"
    }
}
