package com.vocalverify.callguard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private val runtimePermissions = arrayOf(
        Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG,
        Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestRequiredAccess()
        setContentView(buildScreen())
    }

    private fun requestRequiredAccess() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        val missing = runtimePermissions.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) ActivityCompat.requestPermissions(this, missing.toTypedArray(), 8)
    }

    private fun buildScreen(): View {
        val pad = (24 * resources.displayMetrics.density).toInt()
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad); setBackgroundColor(0xff0a0d14.toInt())
            addView(TextView(context).apply { text = "VocalVerify\nCall Guard"; textSize = 29f; setTextColor(0xffffffff.toInt()) })
            addView(TextView(context).apply { text = "On-device overlay monitoring with a consented demo microphone stream."; textSize = 15f; setTextColor(0xffb8c0cc.toInt()); setPadding(0, 16, 0, 32) })
            val endpoint = EditText(context).apply {
                hint = "https://your-server.example"; setTextColor(0xffffffff.toInt()); setHintTextColor(0xff8f9aa8.toInt())
                setText(getSharedPreferences("settings", MODE_PRIVATE).getString("endpoint", "")); inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI
            }
            addView(endpoint)
            addView(button("Save Hugging Face endpoint") { getSharedPreferences("settings", MODE_PRIVATE).edit().putString("endpoint", endpoint.text.toString().trim()).apply() })
            addView(button("Simulate CEO Vishing Call") { startDemo("HIGH_RISK") })
            addView(button("Simulate Legitimate Executive Call") { startDemo("GENUINE") })
            addView(button("Start call-state monitor") { ContextCompat.startForegroundService(this@MainActivity, Intent(this@MainActivity, CallGuardService::class.java).setAction(CallGuardService.ACTION_MONITOR)) })
            addView(TextView(context).apply { text = "Important: Android does not grant third-party sideloaded apps access to the other party's cellular call audio. CAPTURE_AUDIO_OUTPUT is signature-only. The production path sends microphone/demo audio only after explicit consent."; textSize = 13f; setTextColor(0xff8f9aa8.toInt()); setPadding(0, 36, 0, 0) })
        }
    }

    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; gravity = Gravity.CENTER_VERTICAL; setOnClickListener { action() }
    }

    private fun startDemo(outcome: String) {
        if (!Settings.canDrawOverlays(this)) { requestRequiredAccess(); return }
        ContextCompat.startForegroundService(this, Intent(this, CallGuardService::class.java).apply {
            action = CallGuardService.ACTION_SIMULATE; putExtra(CallGuardService.EXTRA_OUTCOME, outcome)
        })
    }
}
