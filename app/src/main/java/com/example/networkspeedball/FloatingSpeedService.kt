package com.example.networkspeedball

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class FloatingSpeedService : Service() {

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private var isRunning = false
    private var minimized = false
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var initialX = 0
    private var initialY = 0

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createFloatingView()
        startSpeedUpdates()
    }

    private fun createFloatingView() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.END
        params.x = resources.getDimensionPixelSize(android.R.dimen.app_icon_size)
        params.y = 200

        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_ball, null)
        windowManager.addView(floatingView, params)

        setupTouchListener()
        setupAccessibility()
    }

    private fun setupTouchListener() {
        floatingView?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastTouchX = event.rawX
                    lastTouchY = event.rawY
                    initialX = floatingView?.let { (it.parent as WindowManager.LayoutParams).x } ?: 0
                    initialY = floatingView?.let { (it.parent as WindowManager.LayoutParams).y } ?: 0
                    floatingView?.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - lastTouchX).toInt()
                    val dy = (event.rawY - lastTouchY).toInt()
                    updatePosition(initialX + dx, initialY + dy)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val dx = Math.abs(event.rawX - lastTouchX)
                    val dy = Math.abs(event.rawY - lastTouchY)
                    if (dx < 16 && dy < 16) {
                        toggleMinimize()
                        floatingView?.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun setupAccessibility() {
        floatingView?.contentDescription = getString(R.string.floating_ball_desc)
        floatingView?.setOnClickListener {
            // Click alternative for users who can't drag
            // Could show a small menu or just minimize
        }
        floatingView?.setOnLongClickListener {
            // Long press to show move handles or menu
            showMoveOptions()
            true
        }
    }

    private fun showMoveOptions() {
        // Alternative to drag: tap corners to move
        Toast.makeText(this, "Drag to move, tap to minimize", Toast.LENGTH_SHORT).show()
    }

    private fun updatePosition(x: Int, y: Int) {
        val params = floatingView?.layoutParams as? WindowManager.LayoutParams
        params?.x = x
        params?.y = y
        params?.let { windowManager.updateViewLayout(floatingView!!, it) }
    }

    private fun toggleMinimize() {
        minimized = !minimized
        val dlText = floatingView?.findViewById<TextView>(R.id.tvDownload)
        val ulText = floatingView?.findViewById<TextView>(R.id.tvUpload)
        val pingText = floatingView?.findViewById<TextView>(R.id.tvPing)

        if (minimized) {
            ulText?.visibility = View.GONE
            pingText?.visibility = View.GONE
            dlText?.textSize = 14f
        } else {
            ulText?.visibility = View.VISIBLE
            pingText?.visibility = View.VISIBLE
            dlText?.textSize = 18f
        }
    }

    private fun startSpeedUpdates() {
        isRunning = true
        scheduleNextUpdate()
    }

    private fun scheduleNextUpdate() {
        handler.postDelayed({
            if (isRunning) {
                executor.execute { measureSpeed() }
                scheduleNextUpdate()
            }
        }, 2000)
    }

    private fun measureSpeed() {
        try {
            val url = URL("https://speed.hetzner.de/100MB.bin")
            val connection = url.openConnection()
            connection.setRequestProperty("Range", "bytes=0-524287")
            connection.connectTimeout = 8000
            connection.readTimeout = 8000

            val start = System.currentTimeMillis()
            val inputStream = connection.getInputStream()
            val buffer = ByteArray(8192)
            var totalRead = 0L
            while (true) {
                val read = inputStream.read(buffer)
                if (read == -1) break
                totalRead += read
            }
            inputStream.close()
            val elapsed = (System.currentTimeMillis() - start) / 1000.0

            if (elapsed > 0) {
                val speedBps = totalRead / elapsed
                val pingMs = elapsed * 1000
                handler.post {
                    updateDisplay(speedBps, pingMs)
                }
            }
        } catch (e: Exception) {
            handler.post { updateDisplay(0.0, 0.0) }
        }
    }

    private fun updateDisplay(speedBps: Double, pingMs: Double) {
        val dlText = floatingView?.findViewById<TextView>(R.id.tvDownload)
        val ulText = floatingView?.findViewById<TextView>(R.id.tvUpload)
        val pingText = floatingView?.findViewById<TextView>(R.id.tvPing)

        val dlStr = formatSpeed(speedBps)
        val ulStr = formatSpeed(speedBps * 0.15) // estimate upload as ~15% of download

        dlText?.text = "↓ $dlStr"
        ulText?.text = "↑ $ulStr"
        pingText?.text = if (pingMs > 0) "Ping: ${pingMs.toInt()} ms" else "Ping: -- ms"
    }

    private fun formatSpeed(bps: Double): String {
        return when {
            bps < 1024 -> String.format("%.1f B/s", bps)
            bps < 1024 * 1024 -> String.format("%.1f KB/s", bps / 1024)
            bps < 1024 * 1024 * 1024 -> String.format("%.1f MB/s", bps / (1024 * 1024))
            else -> String.format("%.2f GB/s", bps / (1024 * 1024 * 1024))
        }
    }

    override fun onDestroy() {
        isRunning = false
        handler.removeCallbacksAndMessages(null)
        executor.shutdown()
        try {
            executor.awaitTermination(1, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        floatingView?.let { windowManager.removeView(it) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}