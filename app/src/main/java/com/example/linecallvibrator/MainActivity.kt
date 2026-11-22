package com.example.linecallvibrator

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class MainActivity : AppCompatActivity() {

    private lateinit var btnOpenNotificationAccess: Button
    private lateinit var tvStatus: TextView
    private lateinit var btnTestStart: Button
    private lateinit var btnTestStop: Button
    private lateinit var tvTestLog: TextView

    private val TAG = "MainActivity"

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "line_call_vibration_channel"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnOpenNotificationAccess = findViewById(R.id.btnOpenNotificationAccess)
        tvStatus = findViewById(R.id.tvStatus)
        btnTestStart = findViewById(R.id.btnTestStart)
        btnTestStop = findViewById(R.id.btnTestStop)
        tvTestLog = findViewById(R.id.tvTestLog)

        btnOpenNotificationAccess.setOnClickListener {
            openNotificationAccessSettings()
        }

        btnTestStart.setOnClickListener {
            appendLog("測試：啟動 6 次震動")
            sendTestStart()
        }

        btnTestStop.setOnClickListener {
            appendLog("測試：停止震動")
            sendTestStop()
        }

        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        val enabled = isNotificationListenerEnabled()
        if (enabled) {
            tvStatus.text = getString(R.string.status_access_granted)
            tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            btnOpenNotificationAccess.isEnabled = false
            btnOpenNotificationAccess.text = "已啟用 ✓"
        } else {
            tvStatus.text = getString(R.string.status_access_denied)
            tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            btnOpenNotificationAccess.isEnabled = true
            btnOpenNotificationAccess.text = getString(R.string.btn_open_notification_access)
        }
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val packageName = packageName
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat?.contains(packageName) == true
    }

    private fun openNotificationAccessSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        } else {
            Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(intent)
        } catch (e: Exception) {
            val fallbackIntent = Intent(Settings.ACTION_SETTINGS)
            fallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(fallbackIntent)
        }
    }

    private fun appendLog(msg: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        val newLog = "[$timestamp] $msg"
        tvTestLog.post {
            val current = tvTestLog.text.toString()
            val lines = current.split("\n")
            val updated = if (lines.size >= 20) lines.takeLast(19).joinToString("\n") + "\n$newLog" else current + "\n$newLog"
            tvTestLog.text = updated
        }
        Log.d(TAG, newLog)
    }

    private fun sendTestStart() {
        val intent = Intent("com.example.linecallvibrator.TEST_START")
        intent.setPackage(packageName)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun sendTestStop() {
        val intent = Intent("com.example.linecallvibrator.TEST_STOP")
        intent.setPackage(packageName)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }
}