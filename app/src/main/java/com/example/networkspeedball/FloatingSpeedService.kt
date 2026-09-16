package com.example.networkspeedball

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.net.TrafficStats
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat

class FloatingSpeedService : Service() {

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false
    private var minimized = false
    private var lastTouchRawX = 0f
    private var lastTouchRawY = 0f
    private var initialX = 0
    private var initialY = 0
    private var moved = false

    private var lastRx = 0L
    private var lastTx = 0L
    private var lastElapsedMs = 0L

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        startForegroundNotification()
        createFloatingView()
        sampleTraffic()
        isRunning = true
        scheduleNextUpdate()
    }

    private fun startForegroundNotification() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Speed ball", NotificationManager.IMPORTANCE_MIN)
            manager.createNotificationChannel(channel)
        }
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.floating_ball_desc))
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createFloatingView() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.END
        params.x = 20
        params.y = 200

        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_ball, null)
        windowManager.addView(floatingView, params)

        floatingView?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastTouchRawX = event.rawX
                    lastTouchRawY = event.rawY
                    moved = false
                    val lp = floatingView?.layoutParams as? WindowManager.LayoutParams
                    initialX = lp?.x ?: 0
                    initialY = lp?.y ?: 0
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - lastTouchRawX).toInt()
                    val dy = (event.rawY - lastTouchRawY).toInt()
                    if (Math.abs(dx) > TOUCH_SLOP || Math.abs(dy) > TOUCH_SLOP) {
                        moved = true
                        updatePosition(initialX - dx, initialY + dy)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        toggleMinimize()
                    }
                    true
                }
                else -> false
            }
        }
        floatingView?.setOnLongClickListener {
            stopSelf()
            true
        }
    }

    private fun updatePosition(x: Int, y: Int) {
        val lp = floatingView?.layoutParams as? WindowManager.LayoutParams ?: return
        lp.x = x
        lp.y = y
        windowManager.updateViewLayout(floatingView, lp)
    }

    private fun toggleMinimize() {
        minimized = !minimized
        val dl = floatingView?.findViewById<View>(R.id.tvDownload)
        val ul = floatingView?.findViewById<View>(R.id.tvUpload)
        val ping = floatingView?.findViewById<View>(R.id.tvPing)
        if (minimized) {
            ul?.visibility = View.GONE
            ping?.visibility = View.GONE
            dl?.scaleX = 0.7f
            dl?.scaleY = 0.7f
        } else {
            ul?.visibility = View.VISIBLE
            ping?.visibility = View.VISIBLE
            dl?.scaleX = 1f
            dl?.scaleY = 1f
        }
    }

    private fun scheduleNextUpdate() {
        handler.postDelayed({
            if (!isRunning) return@postDelayed
            val now = SystemClock.elapsedRealtime()
            val rx = TrafficStats.getTotalRxBytes()
            val tx = TrafficStats.getTotalTxBytes()
            val deltaMs = now - lastElapsedMs
            if (deltaMs > 0 && lastElapsedMs != 0L) {
                val rxDelta = rx - lastRx
                val txDelta = tx - lastTx
                if (rxDelta >= 0 && txDelta >= 0) {
                    updateDisplay(rxDelta * 1000.0 / deltaMs, txDelta * 1000.0 / deltaMs)
                }
            }
            lastRx = rx
            lastTx = tx
            lastElapsedMs = now
            scheduleNextUpdate()
        }, UPDATE_INTERVAL_MS)
    }

    private fun updateDisplay(rxBps: Double, txBps: Double) {
        val dl = floatingView?.findViewById<TextView>(R.id.tvDownload)
        val ul = floatingView?.findViewById<TextView>(R.id.tvUpload)
        dl?.text = "↓ " + formatSpeed(rxBps)
        ul?.text = "↑ " + formatSpeed(txBps)
    }

    private fun formatSpeed(bps: Double): String {
        return when {
            bps < 0 || TrafficStats.UNSUPPORTED.toLong() == bps.toLong() -> "--"
            bps < 1024 -> String.format("%.1f B/s", bps)
            bps < 1024 * 1024 -> String.format("%.1f KB/s", bps / 1024)
            bps < 1024 * 1024 * 1024 -> String.format("%.1f MB/s", bps / (1024 * 1024))
            else -> String.format("%.2f GB/s", bps / (1024 * 1024 * 1024))
        }
    }

    override fun onDestroy() {
        isRunning = false
        handler.removeCallbacksAndMessages(null)
        floatingView?.let { windowManager.removeView(it) }
        floatingView = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "speed_ball_channel"
        private const val NOTIFICATION_ID = 1001
        private const val UPDATE_INTERVAL_MS = 1000L
        private const val TOUCH_SLOP = 12
    }
}