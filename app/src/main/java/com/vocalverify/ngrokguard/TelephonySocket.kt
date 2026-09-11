package com.vocalverify.ngrokguard

import android.util.Base64
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class TelephonySocket(private val url: String, private val callback: (Verdict) -> Unit) : WebSocketListener() {
    private val client = OkHttpClient.Builder().pingInterval(20, TimeUnit.SECONDS).build()
    private var socket: WebSocket? = null
    fun connect() { socket = client.newWebSocket(Request.Builder().url(url).build(), this) }
    fun close() { socket?.close(1000, "call ended"); client.dispatcher.executorService.shutdown() }
    fun send(session: CallSession, pcm: ByteArray) {
        val frame = JSONObject().apply {
            put("session_id", session.sessionId); put("device_id", session.deviceId); put("target_profile_id", "cfo_sarah_jenkins")
            put("telecom_metadata", JSONObject().apply { put("caller_number", session.caller); put("call_direction", session.direction); put("codec", session.codec); put("timestamp", System.currentTimeMillis() / 1000) })
            put("audio_payload", JSONObject().apply { put("sample_rate", 16000); put("encoding", "PCM_16BIT"); put("audio_bytes_base64", Base64.encodeToString(pcm, Base64.NO_WRAP)) })
        }
        socket?.send(frame.toString())
    }
    override fun onOpen(webSocket: WebSocket, response: Response) = callback(Verdict(state = GuardState.ANALYZING, warning = "Secure stream connected. Analyzing speakerphone audio…"))
    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = callback(Verdict(state = GuardState.CONNECTION_ERROR, warning = "Server connection failed. Check your ngrok link and keep the tunnel open."))
    override fun onMessage(webSocket: WebSocket, text: String) {
        runCatching {
            val body = JSONObject(text)
            val outcome = body.optString("outcomeCode", body.optString("outcome_code", "ANALYZING"))
            val transcript = body.optString("transcript", body.optString("recognized_text", body.optString("text", "")))
            val keywordWarning = KeywordSafetyGuard.warningFor(transcript)
            val state = when {
                keywordWarning != null -> GuardState.KEYWORD_WARNING
                outcome == "AI_IMPERSONATION" || outcome == "HIGH_RISK" -> GuardState.HIGH_RISK
                outcome == "GENUINE" -> GuardState.GENUINE
                else -> GuardState.ANALYZING
            }
            callback(Verdict(state, outcome, body.optString("matchedTarget", ""), body.optDouble("confidence", 0.0), body.optDouble("syntheticScore", 0.0), keywordWarning))
        }
    }
}
