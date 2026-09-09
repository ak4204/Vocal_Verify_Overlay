package com.vocalverify.callguard

import android.util.Base64
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class HfTelephonySocket(private val endpoint: String, private val onVerdict: (Verdict) -> Unit) : WebSocketListener() {
    private val client = OkHttpClient.Builder().pingInterval(20, TimeUnit.SECONDS).build()
    private var socket: WebSocket? = null
    fun connect() { socket = client.newWebSocket(Request.Builder().url(endpoint).build(), this) }
    fun close() { socket?.close(1000, "call ended"); client.dispatcher.executorService.shutdown() }
    fun sendPcm(session: CallSession, pcm: ByteArray) {
        val json = JSONObject().apply {
            put("session_id", session.sessionId); put("device_id", session.deviceId); put("target_profile_id", "cfo_sarah_jenkins")
            put("telecom_metadata", JSONObject().apply { put("caller_number", session.callerNumber); put("call_direction", session.direction); put("codec", session.codec); put("timestamp", System.currentTimeMillis() / 1000) })
            put("audio_payload", JSONObject().apply { put("sample_rate", 16000); put("encoding", "PCM_16BIT"); put("audio_bytes_base64", Base64.encodeToString(pcm, Base64.NO_WRAP)) })
        }
        socket?.send(json.toString())
    }
    override fun onMessage(webSocket: WebSocket, text: String) {
        runCatching {
            val payload = JSONObject(text); val outcome = payload.optString("outcomeCode", payload.optString("outcome_code", "ANALYZING"))
            val state = when (outcome) { "HIGH_RISK", "AI_IMPERSONATION" -> GuardState.HIGH_RISK; "GENUINE" -> GuardState.GENUINE; else -> GuardState.ANALYZING }
            onVerdict(Verdict(state, payload.optString("matchedTarget", "Unknown"), payload.optDouble("confidence", 0.0), payload.optDouble("syntheticScore", 0.0)))
        }
    }
}
