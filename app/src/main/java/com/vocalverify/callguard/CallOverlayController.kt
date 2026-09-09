package com.vocalverify.callguard

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.*
import android.widget.*
import kotlin.math.roundToInt

/** Small system overlay; it deliberately contains no call controls or call-log contents. */
class CallOverlayController(private val context: Context, private val onScan: () -> Unit, private val onDetails: () -> Unit) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var root: LinearLayout? = null
    private lateinit var headline: TextView
    private lateinit var detail: TextView
    private lateinit var status: TextView
    private var x = 0; private var y = 80

    fun show(session: CallSession) {
        if (root != null) return
        val density = context.resources.displayMetrics.density
        fun px(v: Int) = (v * density).roundToInt()
        fun text(value: String, size: Float, color: Int) = TextView(context).apply { text = value; textSize = size; setTextColor(color) }
        root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; setPadding(px(14), px(11), px(12), px(10))
            background = rounded(0xe60a0d14.toInt(), px(18), 0x26ffffff)
            elevation = px(12).toFloat()
            val header = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
            header.addView(text("◈  VocalVerify Live Guard", 13f, Color.WHITE), LinearLayout.LayoutParams(0, -2, 1f))
            status = text("● Active", 12f, 0xff34d399.toInt()); header.addView(status)
            header.addView(text("  ×", 26f, 0xffcbd5e1.toInt()).apply { setOnClickListener { hide() } })
            addView(header)
            headline = text("⏳ Analyzing Caller Voice…", 16f, 0xff93c5fd.toInt()).apply { setPadding(0, px(10), 0, px(4)) }; addView(headline)
            detail = text("Caller: ${session.callerNumber}  |  Codec: ${session.codec}", 12f, 0xffaab4c3.toInt()); addView(detail)
            val controls = LinearLayout(context).apply { setPadding(0, px(9), 0, 0) }
            controls.addView(action("↻ Scan") { onScan() })
            controls.addView(action("Details") { onDetails() })
            addView(controls)
            setOnTouchListener(dragListener())
        }
        windowManager.addView(root, params())
    }

    fun update(verdict: Verdict) {
        val card = root ?: return
        when (verdict.state) {
            GuardState.ANALYZING -> {
                card.background = rounded(0xe60a0d14.toInt(), 36, 0x26ffffff)
                headline.setTextColor(0xff93c5fd.toInt()); headline.text = "⏳ Analyzing Caller Voice…"
                detail.text = "Listening through speakerphone fallback"
            }
            GuardState.HIGH_RISK -> {
                card.background = rounded(0xff991b1b.toInt(), 36, 0x66ffffff)
                headline.setTextColor(Color.WHITE); headline.text = "🔴 VISHING ALERT: AI VOICE CLONE"
                detail.text = "Matched Target: ${verdict.displayName}  |  Synthetic Risk: ${(verdict.syntheticScore * 100).roundToInt()}%"
                context.getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 180, 100, 260), -1))
            }
            GuardState.GENUINE -> {
                card.background = rounded(0xff065f46.toInt(), 36, 0x66ffffff)
                headline.setTextColor(Color.WHITE); headline.text = "🟢 VERIFIED GENUINE VOICE"
                detail.text = "Identity Match: ${verdict.displayName}  |  ${(verdict.confidence * 100).toString().take(4)}% Confidence"
            }
        }
    }

    fun hide() { root?.let { windowManager.removeView(it); root = null } }

    private fun action(label: String, listener: () -> Unit) = TextView(context).apply {
        text = label; textSize = 12f; setTextColor(0xffdbeafe.toInt()); setPadding(0, 0, 24, 0); setOnClickListener { listener() }
    }
    private fun rounded(fill: Int, radius: Int, stroke: Int) = GradientDrawable().apply { setColor(fill); cornerRadius = radius.toFloat(); setStroke(1, stroke) }
    private fun params() = WindowManager.LayoutParams(-2, -2, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.END; x = this@CallOverlayController.x; y = this@CallOverlayController.y }
    private fun dragListener() = object : View.OnTouchListener {
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0
        override fun onTouch(v: View, e: android.view.MotionEvent): Boolean = when (e.action) {
            android.view.MotionEvent.ACTION_DOWN -> { downX = e.rawX; downY = e.rawY; startX = x; startY = y; false }
            android.view.MotionEvent.ACTION_MOVE -> { x = startX + (downX - e.rawX).toInt(); y = startY + (e.rawY - downY).toInt(); root?.let { windowManager.updateViewLayout(it, params()) }; true }
            else -> false
        }
    }
}
