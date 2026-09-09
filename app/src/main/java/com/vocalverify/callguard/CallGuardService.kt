package com.vocalverify.callguard

import android.app.*
import android.content.*
import android.graphics.Color
import android.os.*
import android.provider.Settings
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.util.UUID

class CallGuardService : Service() {
    companion object {
        const val ACTION_MONITOR = "com.vocalverify.callguard.MONITOR"
        const val ACTION_SIMULATE = "com.vocalverify.callguard.SIMULATE"
        const val EXTRA_OUTCOME = "outcome"
        private const val CHANNEL_SERVICE = "call_guard_service"
        private const val CHANNEL_ALERTS = "call_guard_alerts"
    }
    private val main = Handler(Looper.getMainLooper())
    private lateinit var telephony: TelephonyManager
    private var listener: PhoneStateListener? = null
    private var overlay: CallOverlayController? = null
    private var socket: HfTelephonySocket? = null
    private var microphone: MicrophoneChunker? = null
    private var activeSession: CallSession? = null

    override fun onCreate() { super.onCreate(); createChannels(); startForeground(1, serviceNotification("Monitoring calls")) }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_MONITOR -> registerCallListener()
            ACTION_SIMULATE -> simulate(intent.getStringExtra(EXTRA_OUTCOME) ?: "HIGH_RISK")
        }
        return START_STICKY
    }

    @Suppress("DEPRECATION") private fun registerCallListener() {
        if (listener != null) return
        telephony = getSystemService(TelephonyManager::class.java)
        listener = object : PhoneStateListener() {
            override fun onCallStateChanged(state: Int, incomingNumber: String?) {
                when (state) {
                    TelephonyManager.CALL_STATE_OFFHOOK -> beginSession(incomingNumber?.ifBlank { "Unknown" } ?: "Unknown", isDemo = false)
                    TelephonyManager.CALL_STATE_IDLE -> endSession()
                }
            }
        }
        telephony.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
    }

    private fun simulate(outcome: String) {
        beginSession("+91 98XXX XXXXX", isDemo = true)
        android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 35).startTone(android.media.ToneGenerator.TONE_PROP_BEEP2, 130)
        main.postDelayed({
            val verdict = if (outcome == "GENUINE") Verdict(GuardState.GENUINE, "Sarah Jenkins (CFO)", .981)
            else Verdict(GuardState.HIGH_RISK, "John Doe (CFO)", .06, .94)
            overlay?.update(verdict)
        }, 2_000)
    }

    private fun beginSession(caller: String, isDemo: Boolean) {
        if (activeSession != null) return
        val session = CallSession("call_sess_${UUID.randomUUID().toString().take(8)}", deviceId(), caller, if (isDemo) "SIMULATED" else "INBOUND", "AMR-WB")
        activeSession = session
        if (Settings.canDrawOverlays(this)) {
            overlay = CallOverlayController(this, { restartScan() }, { openDashboard() }).also { it.show(session) }
        }
        // This notification is intentionally explicit: the app only receives microphone audio.
        notificationManager().notify(2, NotificationCompat.Builder(this, CHANNEL_ALERTS)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("VocalVerify Live Scanning")
            .setContentText("Turn on Speakerphone for VocalVerify Live Scanning")
            .setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true).build())
        openSocket(session)
        microphone = MicrophoneChunker(this) { pcm -> socket?.sendPcm(session, pcm) }.also { it.start() }
    }

    private fun restartScan() { overlay?.update(Verdict(GuardState.ANALYZING, "", 0.0)) }
    private fun openDashboard() {
        val launch = packageManager.getLaunchIntentForPackage(packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (launch != null) startActivity(launch)
    }
    private fun openSocket(session: CallSession) {
        val raw = getSharedPreferences("settings", MODE_PRIVATE).getString("endpoint", "") ?: ""
        if (!raw.startsWith("wss://") || raw.contains("YOUR-HF-SPACE")) return
        val endpoint = raw.trimEnd('/') + "/ws/telephony/${session.deviceId}"
        socket = HfTelephonySocket(endpoint) { verdict -> main.post { overlay?.update(verdict) } }.also { it.connect() }
    }
    private fun endSession() {
        microphone?.stop(); microphone = null; socket?.close(); socket = null; overlay?.hide(); overlay = null; activeSession = null
        notificationManager().cancel(2)
    }
    private fun deviceId(): String {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        return prefs.getString("device_id", null) ?: "usr_phone_${UUID.randomUUID().toString().take(8)}".also { prefs.edit().putString("device_id", it).apply() }
    }
    private fun createChannels() {
        val manager = notificationManager()
        manager.createNotificationChannel(NotificationChannel(CHANNEL_SERVICE, "Call Guard service", NotificationManager.IMPORTANCE_LOW))
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ALERTS, "Call Guard alerts", NotificationManager.IMPORTANCE_HIGH))
    }
    private fun serviceNotification(message: String) = NotificationCompat.Builder(this, CHANNEL_SERVICE)
        .setSmallIcon(android.R.drawable.ic_lock_idle_lock).setContentTitle("VocalVerify Call Guard").setContentText(message).setOngoing(true).build()
    private fun notificationManager() = getSystemService(NotificationManager::class.java)
    override fun onBind(intent: Intent?) = null
    override fun onDestroy() { listener?.let { telephony.listen(it, PhoneStateListener.LISTEN_NONE) }; endSession(); super.onDestroy() }
}
