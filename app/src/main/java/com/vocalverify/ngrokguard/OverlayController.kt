package com.vocalverify.ngrokguard

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.*
import android.widget.*
import kotlin.math.roundToInt

class OverlayController(private val context: Context, private val onScan: () -> Unit) {
    private val windows = context.getSystemService(WindowManager::class.java)
    private var root: LinearLayout? = null
    private lateinit var title: TextView; private lateinit var body: TextView; private lateinit var status: TextView
    private var positionX = 18; private var positionY = 72

    fun show(session: CallSession) {
        if (root != null) return
        fun dp(v: Int) = (v * context.resources.displayMetrics.density).roundToInt()
        fun text(value: String, size: Float, color: Int) = TextView(context).apply { text = value; textSize = size; setTextColor(color) }
        root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(12), dp(14), dp(12)); background = card(0xf20b111d.toInt(), 20, 0x33ffffff); elevation = dp(16).toFloat()
            val header = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
            // Only the label is draggable, so the close and scan controls remain tappable.
            header.addView(text("◇  VocalVerify Call Guard", 13f, Color.WHITE).apply { setOnTouchListener(drag()) }, LinearLayout.LayoutParams(0, -2, 1f))
            status = text("● LIVE", 12f, 0xff32d8a7.toInt()); header.addView(status)
            header.addView(text("  ×", 26f, 0xffd1dae5.toInt()).apply { setOnClickListener { hide() } })
            addView(header)
            title = text("⌛ Analyzing Caller Voice…", 17f, 0xff8ec5ff.toInt()).apply { setPadding(0, dp(10), 0, dp(4)) }; addView(title)
            body = text("Caller: ${session.caller} · Speakerphone scan", 12f, 0xffb2bdca.toInt()); addView(body)
            addView(LinearLayout(context).apply { setPadding(0, dp(8), 0, 0); addView(text("↻ Scan again", 12f, 0xffc7e2ff.toInt()).apply { setOnClickListener { onScan() } }) })
        }
        windows.addView(root, params())
    }
    fun update(verdict: Verdict) {
        val view = root ?: return
        when (verdict.state) {
            GuardState.HIGH_RISK -> apply(view, 0xff991b1b.toInt(), "🔴 VISHING ALERT: AI VOICE CLONE", "${verdict.matchedTarget.ifBlank { "High-risk voice pattern" }} · Synthetic risk ${(verdict.syntheticScore * 100).roundToInt()}%")
            GuardState.GENUINE -> apply(view, 0xff065f46.toInt(), "🟢 VERIFIED GENUINE VOICE", "${verdict.matchedTarget.ifBlank { "Identity signal" }} · ${(verdict.confidence * 100).roundToInt()}% confidence")
            GuardState.CONNECTION_ERROR -> apply(view, 0xff3f4b5f.toInt(), "⚠️ SERVER NOT CONNECTED", verdict.warning ?: "Check ngrok and endpoint.")
            GuardState.ANALYZING -> apply(view, 0xf20b111d.toInt(), "⌛ Analyzing Caller Voice…", verdict.warning ?: "Listening through speakerphone fallback")
        }
        if (verdict.state == GuardState.HIGH_RISK) context.getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 160, 90, 240), -1))
    }
    fun hide() { root?.let { windows.removeView(it); root = null } }
    private fun apply(view: LinearLayout, color: Int, headline: String, message: String) { view.background = card(color, 20, 0x55ffffff); title.text = headline; title.setTextColor(Color.WHITE); body.text = message }
    private fun card(color: Int, radius: Int, border: Int) = GradientDrawable().apply { setColor(color); cornerRadius = radius * context.resources.displayMetrics.density; setStroke(1, border) }
    private fun params() = WindowManager.LayoutParams(-2, -2, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.END; x = positionX; y = positionY }
    private fun drag() = object : View.OnTouchListener { var downX = 0f; var downY = 0f; var startX = 0; var startY = 0
        override fun onTouch(v: View, event: MotionEvent): Boolean = when (event.action) { MotionEvent.ACTION_DOWN -> { downX = event.rawX; downY = event.rawY; startX = positionX; startY = positionY; true }; MotionEvent.ACTION_MOVE -> { positionX = startX + (downX - event.rawX).toInt(); positionY = startY + (event.rawY - downY).toInt(); root?.let { windows.updateViewLayout(it, params()) }; true }; else -> true } }
}
