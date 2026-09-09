package com.vocalverify.callguard

enum class GuardState { ANALYZING, HIGH_RISK, GENUINE }

data class Verdict(
    val state: GuardState,
    val displayName: String,
    val confidence: Double,
    val syntheticScore: Double = 0.0
)

data class CallSession(
    val sessionId: String,
    val deviceId: String,
    val callerNumber: String = "Unknown",
    val direction: String = "INBOUND",
    val codec: String = "Unknown"
)
