package com.example.networkspeedball

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val OVERLAY_PERMISSION_REQUEST = 1234

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnStart = findViewById<Button>(R.id.btnStart)
        val btnStop = findViewById<Button>(R.id.btnStop)
        val btnPermission = findViewById<Button>(R.id.btnPermission)

        btnPermission.setOnClickListener { requestOverlayPermission() }
        btnStart.setOnClickListener { startFloatingService() }
        btnStop.setOnClickListener { stopFloatingService() }

        checkOverlayPermission()
    }

    private fun checkOverlayPermission() {
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else true

        val btnPermission = findViewById<Button>(R.id.btnPermission)
        val btnStart = findViewById<Button>(R.id.btnStart)

        if (hasPermission) {
            btnPermission.text = getString(R.string.permission_granted)
            btnPermission.isEnabled = false
            btnPermission.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#334155"))
            )
            btnStart.isEnabled = true
            btnStart.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#00AAFF"))
            )
        } else {
            btnPermission.text = getString(R.string.grant_permission)
            btnPermission.isEnabled = true
            btnPermission.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#22C55E"))
            )
            btnStart.isEnabled = false
        }
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"))
            startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_PERMISSION_REQUEST) {
            checkOverlayPermission()
        }
    }

    private fun startFloatingService() {
        val intent = Intent(this, FloatingSpeedService::class.java)
        ContextCompat.startForegroundService(this, intent)
        Toast.makeText(this, "Floating speed ball started — drag to move, tap to minimize", Toast.LENGTH_LONG).show()
        finish()
    }

    private fun stopFloatingService() {
        val intent = Intent(this, FloatingSpeedService::class.java)
        stopService(intent)
        Toast.makeText(this, "Floating speed ball stopped", Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        checkOverlayPermission()
    }
}