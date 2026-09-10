package com.vocalverify.callguard

/**
 * Converts a user-entered service address into the single WebSocket endpoint
 * expected by the backend. It accepts Cloudflare Quick Tunnel, Cloud Run,
 * Hugging Face, LAN, and already-prefixed ws/wss URLs.
 */
object ApiConfig {
    fun telephonyWebSocket(input: String, deviceId: String): String? {
        var address = input.trim().trimEnd('/')
        if (address.isBlank()) return null

        address = when {
            address.startsWith("wss://") || address.startsWith("ws://") -> address
            address.startsWith("https://") -> "wss://${address.removePrefix("https://")}" 
            address.startsWith("http://") -> "ws://${address.removePrefix("http://")}" 
            // A bare Space name remains convenient, but any real hostname
            // (trycloudflare.com, run.app, LAN IP, custom domain) is preserved.
            !address.contains('.') && !address.contains(':') -> "wss://$address.hf.space"
            else -> "wss://$address"
        }

        address = address.trimEnd('/')
        val base = address.substringBefore("/ws/telephony")
        return "$base/ws/telephony/$deviceId"
    }
}
