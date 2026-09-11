package com.vocalverify.callguard

import android.util.Base64
import android.util.Log
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class HfTelephonySocket(private val endpoint: String, private val onVerdict: (Verdict) -> Unit) : WebSocketListener() {
    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // No read timeout for streaming websockets
        .build()
    private var socket: WebSocket? = null

    fun connect() {
        Log.i("VocalVerifySocket", "Connecting to telephony WebSocket: $endpoint")
        val req = Request.Builder().url(endpoint).build()
        socket = client.newWebSocket(req, this)
    }

    fun close() {
        socket?.close(1000, "call ended")
        client.dispatcher.executorService.shutdown()
    }

    fun sendPcm(session: CallSession, pcm: ByteArray) {
        val s = socket
        if (s == null) {
            Log.w("VocalVerifySocket", "Cannot send PCM: socket is null")
            return
        }
        val json = JSONObject().apply {
            put("session_id", session.sessionId)
            put("device_id", session.deviceId)
            put("target_profile_id", "cfo_sarah_jenkins")
            put("telecom_metadata", JSONObject().apply {
                put("caller_number", session.callerNumber)
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
        s.send(json.toString())
    }

    override fun onOpen(webSocket: WebSocket, response: Response) {
        Log.i("VocalVerifySocket", "Telephony WebSocket connected successfully to $endpoint")
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        runCatching {
            val payload = JSONObject(text)
            val outcome = payload.optString("outcomeCode", payload.optString("outcome_code", "ANALYZING"))
            val state = when (outcome) {
                "HIGH_RISK", "AI_IMPERSONATION" -> GuardState.HIGH_RISK
                "GENUINE" -> GuardState.GENUINE
                else -> GuardState.ANALYZING
            }
            val verdict = Verdict(
                state,
                payload.optString("matchedTarget", "Unknown"),
                payload.optDouble("confidence", 0.0),
                payload.optDouble("syntheticScore", 0.0)
            )
            Log.d("VocalVerifySocket", "Received verdict: state=$state target=${verdict.displayName} score=${verdict.syntheticScore}")
            onVerdict(verdict)
        }.onFailure { err ->
            Log.e("VocalVerifySocket", "Error parsing incoming verdict: ${err.message}", err)
        }
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        val code = response?.code
        val msg = response?.message
        Log.e("VocalVerifySocket", "WebSocket failure (HTTP $code $msg): ${t.message}", t)
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        Log.i("VocalVerifySocket", "WebSocket closed ($code): $reason")
    }
}
