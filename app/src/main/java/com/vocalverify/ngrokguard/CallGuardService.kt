package com.vocalverify.ngrokguard

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.os.*
import android.provider.Settings
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.util.UUID

class CallGuardService : Service() {
    companion object {
        const val ACTION_MONITOR = "com.vocalverify.ngrokguard.MONITOR"
        const val ACTION_DEMO = "com.vocalverify.ngrokguard.DEMO"
        private const val SERVICE_CHANNEL = "guard_service"
        private const val ALERT_CHANNEL = "guard_alerts"
    }
    private val main = Handler(Looper.getMainLooper())
    private var manager: TelephonyManager? = null
    private var listener: PhoneStateListener? = null
    private var overlay: OverlayController? = null
    private var socket: TelephonySocket? = null
    private var microphone: AudioChunker? = null
    private var session: CallSession? = null
    private var incomingCallerNumber = "Number unavailable"

    override fun onCreate() {
        super.onCreate()
        channels()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(1, serviceNotification("Monitoring calls"), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(1, serviceNotification("Monitoring calls"))
            }
        } catch (e: Throwable) {
            try {
                startForeground(1, serviceNotification("Monitoring calls"))
            } catch (t: Throwable) {
                Log.e("CallGuardService", "startForeground failed", t)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_MONITOR -> monitor()
            ACTION_DEMO -> demo()
        }
        return START_STICKY
    }

    @Suppress("DEPRECATION")
    private fun monitor() {
        if (listener != null) return
        try {
            manager = getSystemService(TelephonyManager::class.java)
            listener = object : PhoneStateListener() {
                override fun onCallStateChanged(state: Int, incomingNumber: String?) {
                    when (state) {
                        TelephonyManager.CALL_STATE_RINGING -> {
                            incomingCallerNumber = incomingNumber?.takeIf { it.isNotBlank() } ?: "Number unavailable"
                        }
                        TelephonyManager.CALL_STATE_OFFHOOK -> begin(incomingNumber?.takeIf { it.isNotBlank() } ?: incomingCallerNumber)
                        TelephonyManager.CALL_STATE_IDLE -> {
                            finishCall()
                            incomingCallerNumber = "Number unavailable"
                        }
                    }
                }
            }
            manager?.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
        } catch (e: Throwable) {
            Log.e("CallGuardService", "monitor failed", e)
        }
    }

    private fun demo() {
        finishCall()
        begin("Demo caller (+91 98765 43210)")
    }

    private fun begin(caller: String) {
        if (session != null) return
        val newSession = CallSession("call_sess_${UUID.randomUUID().toString().take(10)}", deviceId(), caller)
        session = newSession
        try {
            if (Settings.canDrawOverlays(this)) {
                overlay = OverlayController(
                    context = this,
                    onScan = {
                        try {
                            session?.let { sess ->
                                val testPcm = ByteArray(48000)
                                socket?.send(sess, testPcm)
                            }
                        } catch (e: Throwable) {
                            Log.e("CallGuardService", "manual scan send error", e)
                        }
                    },
                    onClose = { finishCall() }
                ).also { it.show(newSession) }
            } else {
                notificationManager().notify(3, alert("Overlay permission needed", "Enable Display over other apps to show Call Guard on calls."))
            }
            notificationManager().notify(2, alert("VocalVerify Live Scanning", "Turn on Speakerphone for VocalVerify Live Scanning"))
        } catch (e: Throwable) {
            Log.e("CallGuardService", "Overlay show error", e)
        }

        try {
            val endpoint = ApiConfig.telephonyWebSocket(getSharedPreferences("guard_settings", MODE_PRIVATE).getString("endpoint", "") ?: "", newSession.deviceId)
            if (endpoint == null) {
                overlay?.update(Verdict(GuardState.CONNECTION_ERROR, warning = "No ngrok endpoint saved. Open Call Guard after this call to add it."))
            } else {
                socket = TelephonySocket(
                    url = endpoint,
                    onConnected = {
                        main.post {
                            try {
                                session?.let { sess ->
                                    val testAudio = ByteArray(48000)
                                    socket?.send(sess, testAudio)
                                }
                            } catch (e: Throwable) {
                                Log.e("CallGuardService", "Initial audio send failed", e)
                            }
                        }
                    },
                    callback = { verdict ->
                        main.post { overlay?.update(verdict) }
                    }
                ).also { it.connect() }
            }
            microphone = AudioChunker(this) { pcm -> socket?.send(newSession, pcm) }.also { it.start() }
        } catch (e: Throwable) {
            Log.e("CallGuardService", "begin startup error", e)
            overlay?.update(Verdict(GuardState.CONNECTION_ERROR, warning = "Guard startup failed: ${e.localizedMessage}"))
        }
    }

    private fun finishCall() {
        try {
            microphone?.stop()
            microphone = null
            socket?.close()
            socket = null
            overlay?.hide()
            overlay = null
            session = null
            notificationManager().cancel(2)
            notificationManager().cancel(3)
        } catch (e: Throwable) {
            Log.e("CallGuardService", "finishCall error", e)
        }
    }

    private fun deviceId(): String {
        val p = getSharedPreferences("guard_settings", MODE_PRIVATE)
        return p.getString("device_id", null) ?: "usr_phone_${UUID.randomUUID().toString().take(8)}".also { p.edit().putString("device_id", it).apply() }
    }

    private fun channels() {
        try {
            notificationManager().createNotificationChannel(NotificationChannel(SERVICE_CHANNEL, "Call Guard service", NotificationManager.IMPORTANCE_LOW))
            notificationManager().createNotificationChannel(NotificationChannel(ALERT_CHANNEL, "Call Guard alerts", NotificationManager.IMPORTANCE_HIGH))
        } catch (e: Throwable) {
            Log.e("CallGuardService", "channels error", e)
        }
    }

    private fun serviceNotification(message: String) = NotificationCompat.Builder(this, SERVICE_CHANNEL).setSmallIcon(android.R.drawable.ic_lock_idle_lock).setContentTitle("VocalVerify Call Guard").setContentText(message).setOngoing(true).build()
    private fun alert(title: String, message: String) = NotificationCompat.Builder(this, ALERT_CHANNEL).setSmallIcon(android.R.drawable.ic_dialog_alert).setContentTitle(title).setContentText(message).setPriority(NotificationCompat.PRIORITY_HIGH).build()
    private fun notificationManager() = getSystemService(NotificationManager::class.java)
    override fun onBind(intent: Intent?) = null
    override fun onDestroy() {
        try {
            listener?.let { manager?.listen(it, PhoneStateListener.LISTEN_NONE) }
            finishCall()
        } catch (e: Throwable) {
            Log.e("CallGuardService", "onDestroy error", e)
        }
        super.onDestroy()
    }
}
