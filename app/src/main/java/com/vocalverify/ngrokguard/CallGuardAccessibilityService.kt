package com.vocalverify.ngrokguard

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class CallGuardAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i("CallGuardAccessibility", "Accessibility Service Connected. Audio capture privileges elevated.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We do not need to process screen events.
        // Being an active AccessibilityService elevates our audio capture priority
        // to bypass Android's background mic block during cellular calls.
    }

    override fun onInterrupt() {
        Log.i("CallGuardAccessibility", "Accessibility Service Interrupted.")
    }
}
