package com.vocalverify.ngrokguard

import android.util.Base64
import android.util.Log
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class TelephonySocket(
    private val url: String,
    private val onConnected: () -> Unit = {},
    private val callback: (Verdict) -> Unit
) : WebSocketListener() {
    private val client = OkHttpClient.Builder().pingInterval(20, TimeUnit.SECONDS).build()
    private var socket: WebSocket? = null

    fun connect() {
        try {
            val safeUrl = when {
                url.startsWith("wss://", ignoreCase = true) -> "https://" + url.substring(6)
                url.startsWith("ws://", ignoreCase = true) -> "http://" + url.substring(5)
                else -> url
            }
            val request = Request.Builder()
                .url(safeUrl)
                .addHeader("ngrok-skip-browser-warning", "true")
                .addHeader("User-Agent", "VocalVerify-Android/1.0")
                .build()
            socket = client.newWebSocket(request, this)
        } catch (e: Throwable) {
            Log.e("TelephonySocket", "WebSocket connect error", e)
            callback(Verdict(state = GuardState.CONNECTION_ERROR, warning = "Connect error: ${e.localizedMessage ?: "Invalid URL"}"))
        }
    }

    fun close() {
        try {
            socket?.close(1000, "call ended")
            client.dispatcher.executorService.shutdown()
        } catch (e: Exception) {
            Log.e("TelephonySocket", "WebSocket close error", e)
        }
    }

    fun send(session: CallSession, pcm: ByteArray) {
        try {
            val frame = JSONObject().apply {
                put("session_id", session.sessionId)
                put("device_id", session.deviceId)
                put("target_profile_id", "cfo_sarah_jenkins")
                put("telecom_metadata", JSONObject().apply {
                    put("caller_number", session.caller)
                    put("call_direction", session.direction)
                    put("codec", session.codec)
                    put("timestamp", System.currentTimeMillis() / 1000)
                })
                put("audio_payload", JSONObject().apply {
                    put("sample_rate", 16000)
                    put("encoding", "PCM_16BIT")
                    put("audio_bytes_base64", Base64.encodeToString(pcm, Base64.NO_WRAP))
                })
            }
            socket?.send(frame.toString())
        } catch (e: Exception) {
            Log.e("TelephonySocket", "WebSocket send error", e)
        }
    }

    override fun onOpen(webSocket: WebSocket, response: Response) {
        callback(Verdict(state = GuardState.ANALYZING, warning = "Secure stream connected. Analyzing speakerphone audio..."))
        onConnected()
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        Log.e("TelephonySocket", "WebSocket failure", t)
        callback(Verdict(state = GuardState.CONNECTION_ERROR, warning = "Server connection failed: ${t.localizedMessage ?: "Check ngrok link"}"))
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        runCatching {
            val body = JSONObject(text)
            val outcome = body.optString("outcomeCode", body.optString("outcome_code", "ANALYZING"))
            val state = when {
                outcome == "AI_IMPERSONATION" || outcome == "HIGH_RISK" -> GuardState.HIGH_RISK
                outcome == "GENUINE" -> GuardState.GENUINE
                else -> GuardState.ANALYZING
            }
            callback(Verdict(state, outcome, body.optString("matchedTarget", ""), body.optDouble("confidence", 0.0), body.optDouble("syntheticScore", 0.0)))
        }
    }
}
