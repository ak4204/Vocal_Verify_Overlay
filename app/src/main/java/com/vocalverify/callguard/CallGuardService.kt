package com.vocalverify.callguard

import android.app.*
import android.content.*
import android.graphics.Color
import android.os.*
import android.provider.Settings
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.math.sin

class CallGuardService : Service() {
    companion object {
        const val ACTION_MONITOR = "com.vocalverify.callguard.MONITOR"
        const val ACTION_SIMULATE = "com.vocalverify.callguard.SIMULATE"
        const val EXTRA_OUTCOME = "outcome"
        private const val CHANNEL_SERVICE = "call_guard_service"
        private const val CHANNEL_ALERTS = "call_guard_alerts"
        const val DEFAULT_ENDPOINT = "https://wolf-text-comments-geological.trycloudflare.com"
    }

    private val main = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var telephony: TelephonyManager
    private var listener: PhoneStateListener? = null
    private var overlay: CallOverlayController? = null
    private var socket: HfTelephonySocket? = null
    private var microphone: MicrophoneChunker? = null
    private var activeSession: CallSession? = null

    override fun onCreate() {
        super.onCreate()
        createChannels()
        startForeground(1, serviceNotification("Monitoring calls"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_MONITOR -> registerCallListener()
            ACTION_SIMULATE -> simulate(intent.getStringExtra(EXTRA_OUTCOME) ?: "HIGH_RISK")
        }
        return START_STICKY
    }

    @Suppress("DEPRECATION")
    private fun registerCallListener() {
        if (listener != null) return
        telephony = getSystemService(TelephonyManager::class.java)
        listener = object : PhoneStateListener() {
            override fun onCallStateChanged(state: Int, incomingNumber: String?) {
                when (state) {
                    TelephonyManager.CALL_STATE_OFFHOOK -> {
                        Log.i("CallGuardService", "Call OFFHOOK: $incomingNumber")
                        beginSession(incomingNumber?.ifBlank { "Incoming Call" } ?: "Incoming Call", isDemo = false)
                    }
                    TelephonyManager.CALL_STATE_IDLE -> {
                        Log.i("CallGuardService", "Call IDLE: ending session")
                        endSession()
                    }
                }
            }
        }
        telephony.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
    }

    private fun simulate(outcome: String) {
        val callerName = if (outcome == "GENUINE") "Sarah Jenkins (CFO)" else "John Doe (CEO)"
        beginSession("+91 98765 43210", isDemo = true)
        try {
            android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 35)
                .startTone(android.media.ToneGenerator.TONE_PROP_BEEP2, 130)
        } catch (e: Exception) {
            Log.d("CallGuardService", "ToneGenerator error: ${e.message}")
        }

        // Send simulated audio frames over WebSocket to test backend & dashboard
        executor.execute {
            try {
                Thread.sleep(800)
                val s = activeSession
                val sock = socket
                if (s != null && sock != null) {
                    val demoChunk = generateSyntheticVoiceChunk()
                    Log.i("CallGuardService", "Sending demo audio chunk to backend...")
                    sock.sendPcm(s, demoChunk)
                }
            } catch (e: Exception) {
                Log.w("CallGuardService", "Demo chunk transmit error: ${e.message}")
            }
        }

        // Guaranteed overlay update after 2.5s (in case server verdict takes longer)
        main.postDelayed({
            if (activeSession != null) {
                val verdict = if (outcome == "GENUINE") {
                    Verdict(GuardState.GENUINE, "Sarah Jenkins (CFO)", 0.981, 0.02)
                } else {
                    Verdict(GuardState.HIGH_RISK, "John Doe (CEO)", 0.06, 0.94)
                }
                overlay?.update(verdict)
            }
        }, 2500)
    }

    private fun generateSyntheticVoiceChunk(): ByteArray {
        val sr = 16000
        val duration = 1.5
        val samples = (sr * duration).toInt()
        val pcm = ByteArray(samples * 2)
        for (i in 0 until samples) {
            val t = i.toDouble() / sr
            val wave = 0.5 * sin(2.0 * Math.PI * 180.0 * t) + 0.3 * sin(2.0 * Math.PI * 360.0 * t)
            val v = (wave * 32767.0).coerceIn(-32768.0, 32767.0).toInt().toShort()
            pcm[i * 2] = (v.toInt() and 0xFF).toByte()
            pcm[i * 2 + 1] = ((v.toInt() shr 8) and 0xFF).toByte()
        }
        return pcm
    }

    private fun beginSession(caller: String, isDemo: Boolean) {
        if (activeSession != null) return
        val session = CallSession(
            "call_sess_${UUID.randomUUID().toString().take(8)}",
            deviceId(),
            caller,
            if (isDemo) "SIMULATED" else "INBOUND",
            "AMR-WB"
        )
        activeSession = session

        if (Settings.canDrawOverlays(this)) {
            overlay = CallOverlayController(this, { restartScan() }, { openDashboard() }).also { it.show(session) }
        }

        notificationManager().notify(
            2,
            NotificationCompat.Builder(this, CHANNEL_ALERTS)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("VocalVerify Live Scanning")
                .setContentText("Turn on Speakerphone for VocalVerify Live Scanning")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
        )

        openSocket(session)

        microphone = MicrophoneChunker(this) { pcm ->
            Log.d("CallGuardService", "Microphone chunk received (${pcm.size} bytes), forwarding to socket")
            socket?.sendPcm(session, pcm)
        }.also { it.start() }
    }

    private fun restartScan() {
        overlay?.update(Verdict(GuardState.ANALYZING, "", 0.0))
    }

    private fun openDashboard() {
        val launch = packageManager.getLaunchIntentForPackage(packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (launch != null) startActivity(launch)
    }

    private fun openSocket(session: CallSession) {
        val saved = getSharedPreferences("settings", MODE_PRIVATE).getString("endpoint", "")
        val raw = if (!saved.isNullOrBlank()) saved.trim() else DEFAULT_ENDPOINT
        val endpoint = ApiConfig.telephonyWebSocket(raw, session.deviceId)
        if (endpoint == null) {
            Log.e("CallGuardService", "Invalid endpoint configured: $raw")
            return
        }

        Log.i("CallGuardService", "Opening telephony socket: $endpoint")
        socket = HfTelephonySocket(endpoint) { verdict ->
            Log.i("CallGuardService", "Verdict received from backend: ${verdict.state} ${verdict.displayName}")
            main.post { overlay?.update(verdict) }
        }.also { it.connect() }
    }

    private fun endSession() {
        microphone?.stop()
        microphone = null
        socket?.close()
        socket = null
        overlay?.hide()
        overlay = null
        activeSession = null
        notificationManager().cancel(2)
    }

    private fun deviceId(): String {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        return prefs.getString("device_id", null) ?: "usr_phone_${UUID.randomUUID().toString().take(8)}".also {
            prefs.edit().putString("device_id", it).apply()
        }
    }

    private fun createChannels() {
        val manager = notificationManager()
        manager.createNotificationChannel(NotificationChannel(CHANNEL_SERVICE, "Call Guard service", NotificationManager.IMPORTANCE_LOW))
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ALERTS, "Call Guard alerts", NotificationManager.IMPORTANCE_HIGH))
    }

    private fun serviceNotification(message: String) = NotificationCompat.Builder(this, CHANNEL_SERVICE)
        .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
        .setContentTitle("VocalVerify Call Guard")
        .setContentText(message)
        .setOngoing(true)
        .build()

    private fun notificationManager() = getSystemService(NotificationManager::class.java)

    override fun onBind(intent: Intent?) = null

    override fun onDestroy() {
        listener?.let { telephony.listen(it, PhoneStateListener.LISTEN_NONE) }
        endSession()
        super.onDestroy()
    }
}
