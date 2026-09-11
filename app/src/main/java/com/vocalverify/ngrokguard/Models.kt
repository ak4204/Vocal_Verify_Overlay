package com.vocalverify.ngrokguard

enum class GuardState { ANALYZING, HIGH_RISK, GENUINE, CONNECTION_ERROR }

data class CallSession(
    val sessionId: String,
    val deviceId: String,
    val caller: String,
    val direction: String = "INBOUND",
    val codec: String = "PCM_16BIT / MIC"
)

data class Verdict(
    val state: GuardState = GuardState.ANALYZING,
    val outcome: String = "ANALYZING",
    val matchedTarget: String = "",
    val confidence: Double = 0.0,
    val syntheticScore: Double = 0.0,
    val warning: String? = null
)
