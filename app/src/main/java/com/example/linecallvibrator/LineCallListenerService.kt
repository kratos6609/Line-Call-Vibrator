package com.example.linecallvibrator

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class LineCallListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "LineCallListener"
        private const val LINE_PACKAGE = "jp.naver.line.android"
        private const val SIMULATED_NOTIFICATION_ID = 9999
        private const val VIBRATION_INTERVAL_MS = 2500L
        private const val MAX_VIBRATION_COUNT = 6
        private const val NOTIFICATION_CHANNEL_ID = "line_call_vibration_channel"
        private const val FG_SERVICE_CHANNEL_ID = "fg_service_channel"
        private const val FOREGROUND_SERVICE_NOTIFICATION_ID = 8888

        @Suppress("UNUSED_PARAMETER")
        fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val mainChannel = NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    context.getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.notification_channel_desc)
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 500, 200, 500)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
                nm.createNotificationChannel(mainChannel)

                val fgChannel = NotificationChannel(
                    FG_SERVICE_CHANNEL_ID,
                    "前台服務",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "前台服務運行通知"
                    enableVibration(false)
                    lockscreenVisibility = Notification.VISIBILITY_SECRET
                }
                nm.createNotificationChannel(fgChannel)
            }
        }

        // Test actions (kept simple)
        const val ACTION_TEST_START = "com.example.linecallvibrator.TEST_START"
        const val ACTION_TEST_STOP = "com.example.linecallvibrator.TEST_STOP"
    }

    private val handler = Handler(Looper.getMainLooper())
    private var vibrationCount = 0
    private var isVibrating = false
    private var testReceiver: BroadcastReceiver? = null
    private val vibrationRunnable = object : Runnable {
        override fun run() {
            if (vibrationCount < MAX_VIBRATION_COUNT) {
                postSimulatedNotification()
                vibrationCount++
                handler.postDelayed(this, VIBRATION_INTERVAL_MS)
            } else {
                stopVibration()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel(this)
        registerTestReceiver()
        Log.d(TAG, "Service created")
    }

    private fun registerTestReceiver() {
        testReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    ACTION_TEST_START -> {
                        Log.d(TAG, "Test start requested")
                        startVibration()
                    }
                    ACTION_TEST_STOP -> {
                        Log.d(TAG, "Test stop requested")
                        stopVibration()
                    }
                }
            }
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(testReceiver!!,
            IntentFilter().apply { addAction(ACTION_TEST_START); addAction(ACTION_TEST_STOP) })
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != LINE_PACKAGE) return
        if (isVibrating) return // Already vibrating for a call

        val n = sbn.notification
        val extras = n.extras
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getString(Notification.EXTRA_TEXT) ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
        val allText = "$title $text $bigText".lowercase()

        // Exclude outgoing calls (主動撥出)
        val outgoingKeywords = listOf("撥打中", "撥出", "outgoing", "dialing", "呼叫中")
        if (outgoingKeywords.any { allText.contains(it) }) {
            Log.d(TAG, "Outgoing call excluded: title='$title'")
            return
        }

        // Quick exclusion: ended/missed/voicemail
        val exclude = listOf("未接", "已結束", "結束", "語音訊息", "通話記錄", "取消", "拒接",
            "missed", "ended", "finish", "voicemail", "call log", "cancelled", "rejected",
            "不在服務區", "關機", "無法接通")
        if (exclude.any { allText.contains(it) }) return

        // Must be CALL category
        if (n.category != Notification.CATEGORY_CALL) return

        // Has call actions or incoming keywords
        val hasAction = n.actions?.any { a ->
            val t = a.title?.toString()?.lowercase() ?: ""
            t.contains("接聽") || t.contains("掛斷") || t.contains("answer") ||
            t.contains("decline") || t.contains("reject") || t.contains("hang up")
        } == true
        val hasKeyword = listOf("來電中", "撥打中", "incoming", "calling", "視訊來電", "語音來電")
            .any { allText.contains(it.lowercase()) }

        if (hasAction || hasKeyword) {
            Log.d(TAG, "Incoming call detected: title='$title'")
            startVibration()
        }
    }

    private fun startVibration() {
        if (isVibrating) return
        isVibrating = true
        vibrationCount = 0
        Log.d(TAG, "Starting vibration (6 times)")

        startForegroundServiceCompat()
        handler.post(vibrationRunnable)
    }

    private fun stopVibration() {
        if (!isVibrating) return
        isVibrating = false
        vibrationCount = 0
        handler.removeCallbacks(vibrationRunnable)
        cancelSimulatedNotification()
        stopForegroundServiceCompat()
        Log.d(TAG, "Vibration stopped")
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName != LINE_PACKAGE) return
        if (isVibrating) {
            Log.d(TAG, "Call notification removed, stopping vibration")
            stopVibration()
        }
    }

    private fun startForegroundServiceCompat() {
        val intent = Intent(this, VibrationForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
    }

    private fun stopForegroundServiceCompat() {
        stopService(Intent(this, VibrationForegroundService::class.java))
    }

    private fun postSimulatedNotification() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pi = PendingIntent.getActivity(this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val notif = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_phone_call)
            .setContentTitle(getString(R.string.simulated_notification_title))
            .setContentText(getString(R.string.simulated_notification_text))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .setDefaults(NotificationCompat.DEFAULT_VIBRATE or NotificationCompat.DEFAULT_LIGHTS)
            .setContentIntent(pi).build()

        NotificationManagerCompat.from(this).notify(SIMULATED_NOTIFICATION_ID, notif)
        Log.d(TAG, "Vibration notification #${vibrationCount + 1}/$MAX_VIBRATION_COUNT sent")
    }

    private fun cancelSimulatedNotification() {
        NotificationManagerCompat.from(this).cancel(SIMULATED_NOTIFICATION_ID)
    }

    override fun onDestroy() {
        testReceiver?.let { LocalBroadcastManager.getInstance(this).unregisterReceiver(it) }
        stopVibration()
        super.onDestroy()
    }
}