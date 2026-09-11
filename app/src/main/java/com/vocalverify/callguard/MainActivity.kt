package com.vocalverify.callguard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    companion object {
        const val DEFAULT_ENDPOINT = "https://wolf-text-comments-geological.trycloudflare.com"
    }

    private val runtimePermissions = arrayOf(
        Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG,
        Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS
    )

    private lateinit var endpointInput: EditText

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

    private fun saveCurrentEndpoint() {
        val url = endpointInput.text.toString().trim()
        val target = if (url.isNotBlank()) url else DEFAULT_ENDPOINT
        getSharedPreferences("settings", MODE_PRIVATE).edit().putString("endpoint", target).apply()
    }

    private fun buildScreen(): View {
        val pad = (24 * resources.displayMetrics.density).toInt()
        val saved = getSharedPreferences("settings", MODE_PRIVATE).getString("endpoint", "")
        val initialUrl = if (!saved.isNullOrBlank()) saved else DEFAULT_ENDPOINT
        getSharedPreferences("settings", MODE_PRIVATE).edit().putString("endpoint", initialUrl).apply()

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad); setBackgroundColor(0xff0a0d14.toInt())
            addView(TextView(context).apply { text = "VocalVerify\nCall Guard"; textSize = 29f; setTextColor(0xffffffff.toInt()) })
            addView(TextView(context).apply { text = "On-device overlay monitoring with real-time call & mic stream."; textSize = 15f; setTextColor(0xffb8c0cc.toInt()); setPadding(0, 16, 0, 20) })

            addView(TextView(context).apply { text = "SERVER ENDPOINT (CLOUDFLARE / LOCALHOST)"; textSize = 11f; setTextColor(0xff7dbdff.toInt()); setPadding(0, 0, 0, 6) })

            endpointInput = EditText(context).apply {
                hint = DEFAULT_ENDPOINT
                setTextColor(0xffffffff.toInt())
                setHintTextColor(0xff8f9aa8.toInt())
                setText(initialUrl)
                inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                    override fun afterTextChanged(s: Editable?) { saveCurrentEndpoint() }
                })
            }
            addView(endpointInput)

            addView(button("Save Endpoint") {
                saveCurrentEndpoint()
                val current = getSharedPreferences("settings", MODE_PRIVATE).getString("endpoint", "")
                Toast.makeText(context, "Saved: $current", Toast.LENGTH_SHORT).show()
            })

            addView(button("Simulate CEO Vishing Call (Test Connection)") {
                saveCurrentEndpoint()
                startDemo("HIGH_RISK")
            })

            addView(button("Simulate Legitimate Executive Call") {
                saveCurrentEndpoint()
                startDemo("GENUINE")
            })

            addView(button("Start call-state monitor") {
                saveCurrentEndpoint()
                ContextCompat.startForegroundService(this@MainActivity, Intent(this@MainActivity, CallGuardService::class.java).setAction(CallGuardService.ACTION_MONITOR))
                Toast.makeText(context, "Call monitor started! Turn on speakerphone during calls for live scanning.", Toast.LENGTH_LONG).show()
            })

            addView(TextView(context).apply {
                text = "Important: For live phone calls, turn on speakerphone so VocalVerify can analyze the audio. The floating overlay will appear automatically when in call."
                textSize = 13f
                setTextColor(0xff8f9aa8.toInt())
                setPadding(0, 24, 0, 0)
            })
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
        saveCurrentEndpoint()
        ContextCompat.startForegroundService(this, Intent(this, CallGuardService::class.java).apply {
            action = CallGuardService.ACTION_SIMULATE; putExtra(CallGuardService.EXTRA_OUTCOME, outcome)
        })
    }
}
