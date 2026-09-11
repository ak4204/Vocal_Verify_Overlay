package com.vocalverify.ngrokguard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private val permissions = arrayOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(screen())
        requestAccess()
    }

    private fun requestAccess() {
        if (!Settings.canDrawOverlays(this)) startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        val missing = permissions.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) ActivityCompat.requestPermissions(this, missing.toTypedArray(), 44)
    }

    private fun screen(): View {
        val density = resources.displayMetrics.density
        val pad = (22 * density).toInt()
        val prefs = getSharedPreferences("guard_settings", MODE_PRIVATE)
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad); setBackgroundColor(0xff091018.toInt())
            addView(label("◈ VocalVerify\nCall Guard", 29f, 0xffffffff.toInt()))
            addView(label("Live speakerphone safety scan · ngrok compatible", 15f, 0xff9fb0c0.toInt()).apply { setPadding(0, 10, 0, 26) })
            val endpoint = EditText(context).apply {
                hint = "https://your-tunnel.ngrok-free.app"; inputType = InputType.TYPE_TEXT_VARIATION_URI
                setText(prefs.getString("endpoint", "")); setTextColor(0xffffffff.toInt()); setHintTextColor(0xff718096.toInt())
            }
            addView(endpoint)
            addView(button("Save ngrok endpoint") { prefs.edit().putString("endpoint", endpoint.text.toString().trim()).apply(); toast("Endpoint saved") })
            addView(button("Enable Call Guard") {
                ContextCompat.startForegroundService(this@MainActivity, Intent(this@MainActivity, CallGuardService::class.java).setAction(CallGuardService.ACTION_MONITOR))
                toast("Call Guard is monitoring. The overlay appears once a call connects.")
            })
            addView(button("Test top-right overlay") {
                if (!Settings.canDrawOverlays(this@MainActivity)) { requestAccess(); return@button }
                ContextCompat.startForegroundService(this@MainActivity, Intent(this@MainActivity, CallGuardService::class.java).setAction(CallGuardService.ACTION_DEMO))
            })
            addView(label("For real calls: switch on speakerphone. Android does not allow a normal sideloaded app to directly read the remote call-audio stream. This app sends consented microphone audio as 16 kHz PCM-16 frames.", 13f, 0xff8e9eae.toInt()).apply { setPadding(0, 28, 0, 0) })
        }
    }
    private fun label(text: String, size: Float, color: Int) = TextView(this).apply { this.text = text; textSize = size; setTextColor(color) }
    private fun button(text: String, onClick: () -> Unit) = Button(this).apply { this.text = text; isAllCaps = false; gravity = Gravity.CENTER_VERTICAL; setOnClickListener { onClick() } }
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}
