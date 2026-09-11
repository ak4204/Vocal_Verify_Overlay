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
            addView(TextView(context).apply { text = "On-device overlay monitoring with a real-time call & mic stream."; textSize = 15f; setTextColor(0xffb8c0cc.toInt()); setPadding(0, 16, 0, 24) })
            
            val endpoint = EditText(context).apply {
                hint = "https://xxxx.trycloudflare.com or http://10.0.2.2:8080"
                setTextColor(0xffffffff.toInt())
                setHintTextColor(0xff8f9aa8.toInt())
                setText(getSharedPreferences("settings", MODE_PRIVATE).getString("endpoint", ""))
                inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI
            }
            addView(endpoint)
            addView(button("Save Server Endpoint (Cloudflare / Localhost)") {
                val url = endpoint.text.toString().trim()
                getSharedPreferences("settings", MODE_PRIVATE).edit().putString("endpoint", url).apply()
                Toast.makeText(context, if (url.isNotBlank()) "Endpoint saved: $url" else "Endpoint cleared", Toast.LENGTH_SHORT).show()
            })
            addView(button("Simulate CEO Vishing Call") { startDemo("HIGH_RISK") })
            addView(button("Simulate Legitimate Executive Call") { startDemo("GENUINE") })
            addView(button("Start call-state monitor") { 
                ContextCompat.startForegroundService(this@MainActivity, Intent(this@MainActivity, CallGuardService::class.java).setAction(CallGuardService.ACTION_MONITOR))
                Toast.makeText(context, "Call monitor started. Turn on speakerphone during calls.", Toast.LENGTH_SHORT).show()
            })
            addView(TextView(context).apply { text = "Important: Android sideloaded apps capture microphone audio with user consent. Turn on speakerphone during cellular calls for real-time live screening."; textSize = 13f; setTextColor(0xff8f9aa8.toInt()); setPadding(0, 28, 0, 0) })
        }
    }

    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; gravity = Gravity.CENTER_VERTICAL; setOnClickListener { action() }
    }

    private fun startDemo(outcome: String) {
        if (!Settings.canDrawOverlays(this)) { 
            Toast.makeText(this, "Please grant 'Display over other apps' permission first", Toast.LENGTH_LONG).show()
            requestRequiredAccess()
            return 
        }
        ContextCompat.startForegroundService(this, Intent(this, CallGuardService::class.java).apply {
            action = CallGuardService.ACTION_SIMULATE; putExtra(CallGuardService.EXTRA_OUTCOME, outcome)
        })
    }
}
