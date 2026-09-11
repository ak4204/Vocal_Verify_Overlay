package com.vocalverify.ngrokguard

/**
 * App-side policy list. It scans a transcript included by the server response;
 * PCM samples cannot be searched for spoken words without an STT engine/model.
 */
object KeywordSafetyGuard {
    private val sensitiveTerms = listOf(
        "otp", "one time password", "verification code", "pin", "cvv", "card number",
        "upi", "bank account", "money", "transfer", "send money", "password", "refund",
        "screen share", "remote access", "aadhar", "aadhaar"
    )
    fun warningFor(transcript: String?): String? {
        val value = transcript?.lowercase() ?: return null
        val hits = sensitiveTerms.filter { value.contains(it) }.distinct()
        if (hits.isEmpty()) return null
        return "Sensitive topic heard: ${hits.take(3).joinToString(", ")}. Never share OTP, PIN, CVV, passwords, or transfer money on a call."
    }
}
