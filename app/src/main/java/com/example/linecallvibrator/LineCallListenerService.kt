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
        private const val MAX_VIBRATION_COUNT = 6 // safety limit retained
        private const val NOTIFICATION_CHANNEL_ID = "line_call_vibration_channel"
        private const val FG_SERVICE_CHANNEL_ID = "fg_service_channel"
        const val ACTION_TEST_START = "com.example.linecallvibrator.TEST_START"
        const val ACTION_TEST_STOP = "com.example.linecallvibrator.TEST_STOP"
    }

    private val handler = Handler(Looper.getMainLooper())
    private var vibrationCount = 0
    private var isVibrating = false
    private var activeCallNotificationKey: String? = null
    private var activeCallerName: String = ""
    private var testReceiver: BroadcastReceiver? = null

    private val vibrationRunnable = object : Runnable {
        override fun run() {
            if (isVibrating && vibrationCount < MAX_VIBRATION_COUNT) {
                postSimulatedNotification(activeCallerName)
                vibrationCount++
                handler.postDelayed(this, VIBRATION_INTERVAL_MS)
            } else stopVibration()
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val mainChannel = NotificationChannel(NOTIFICATION_CHANNEL_ID,
                context.getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.notification_channel_desc)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            nm.createNotificationChannel(mainChannel)
            val fgChannel = NotificationChannel(FG_SERVICE_CHANNEL_ID, "前台服務", NotificationManager.IMPORTANCE_LOW).apply {
                description = "前台服務運行通知"
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
            nm.createNotificationChannel(fgChannel)
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
                    ACTION_TEST_START -> startVibration("測試")
                    ACTION_TEST_STOP -> stopVibration()
                }
            }
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(testReceiver!!,
            IntentFilter().apply { addAction(ACTION_TEST_START); addAction(ACTION_TEST_STOP) })
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != LINE_PACKAGE) return
        val n = sbn.notification
        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val actions = n.actions?.map { it.title?.toString().orEmpty() }.orEmpty()
        val allText = "$title $text $subText $bigText ${actions.joinToString(" ")}".lowercase()

        // Log the actual notification shape to aid device-specific debugging.
        Log.d(TAG, "LINE notification: id=${sbn.id}, key=${sbn.key}, category=${n.category}, title='$title', text='$text', sub='$subText', big='$bigText', actions=$actions, flags=${n.flags}")

        if (!isCallLike(n, allText, actions)) return

        // When the same call notification changes to a connected/ended state, stop.
        val connectedOrEnded = listOf("通話中", "已接聽", "接聽後", "已接通", "正在通話", "call in progress", "connected", "in call", "answered", "call ended", "ended", "結束", "已結束", "取消", "cancelled", "rejected", "declined", "missed", "未接").any { allText.contains(it) }
        val outgoing = listOf("撥打中", "撥出", "呼叫中", "正在呼叫", "outgoing", "dialing", "dialling", "you are calling", "發出電話", "呼出").any { allText.contains(it) }
        if (outgoing) {
            Log.d(TAG, "Outgoing-call notification ignored")
            if (isVibrating && activeCallNotificationKey == sbn.key) stopVibration()
            return
        }
        if (connectedOrEnded) {
            Log.d(TAG, "Call state appears connected/ended; stopping vibration")
            if (isVibrating && (activeCallNotificationKey == sbn.key || activeCallNotificationKey == null)) stopVibration()
            return
        }

        val incomingSignals = listOf("來電中", "語音來電", "視訊來電", "incoming", "incoming call", "calling", "voice call", "video call", "接聽").any { allText.contains(it) }
        val hasAnswerAction = actions.any { it.lowercase().contains("接聽") || it.lowercase().contains("answer") }
        if (n.category == Notification.CATEGORY_CALL && (incomingSignals || hasAnswerAction)) {
            if (!isVibrating) {
                activeCallNotificationKey = sbn.key
                // Notification title is commonly the contact name; avoid using generic LINE labels.
                activeCallerName = title.takeIf { it.isNotBlank() && !it.equals("LINE", true) && !it.contains("來電") && !it.contains("incoming", true) }.orEmpty()
                Log.d(TAG, "Incoming call detected; caller='$activeCallerName'")
                startVibration(activeCallerName)
            } else if (activeCallNotificationKey == sbn.key) {
                // Refresh displayed caller information if LINE updates the notification.
                activeCallerName = title.takeIf { it.isNotBlank() }.orEmpty().ifBlank { activeCallerName }
            }
        }
    }

    private fun isCallLike(n: Notification, allText: String, actions: List<String>): Boolean {
        val callAction = actions.any { t ->
            val lower = t.lowercase()
            listOf("接聽", "掛斷", "answer", "decline", "reject", "hang up", "end call").any { lower.contains(it) }
        }
        val callWords = listOf("來電中", "incoming call", "incoming", "語音來電", "視訊來電", "calling", "呼叫中", "通話中", "voice call", "video call").any { allText.contains(it.lowercase()) }
        return n.category == Notification.CATEGORY_CALL || callAction || callWords
    }

    private fun startVibration(callerName: String = "") {
        if (isVibrating) return
        isVibrating = true
        vibrationCount = 0
        activeCallerName = callerName
        Log.d(TAG, "Starting vibration (limited to $MAX_VIBRATION_COUNT)")
        startForegroundServiceCompat()
        handler.post(vibrationRunnable)
    }

    private fun stopVibration() {
        if (!isVibrating) return
        isVibrating = false
        vibrationCount = 0
        activeCallNotificationKey = null
        activeCallerName = ""
        handler.removeCallbacks(vibrationRunnable)
        cancelSimulatedNotification()
        stopForegroundServiceCompat()
        Log.d(TAG, "Vibration stopped")
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName == LINE_PACKAGE && isVibrating && sbn.key == activeCallNotificationKey) {
            Log.d(TAG, "Tracked call notification removed; stopping vibration")
            stopVibration()
        }
    }

    private fun startForegroundServiceCompat() {
        val intent = Intent(this, VibrationForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
    }

    private fun stopForegroundServiceCompat() { stopService(Intent(this, VibrationForegroundService::class.java)) }

    private fun postSimulatedNotification(callerName: String) {
        val intent = Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK }
        val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val displayTitle = if (callerName.isBlank()) getString(R.string.simulated_notification_title) else "LINE 來電：$callerName"
        val notif = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_phone_call)
            .setContentTitle(displayTitle)
            .setContentText(getString(R.string.simulated_notification_text))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .setDefaults(NotificationCompat.DEFAULT_VIBRATE or NotificationCompat.DEFAULT_LIGHTS)
            .setContentIntent(pi).build()
        NotificationManagerCompat.from(this).notify(SIMULATED_NOTIFICATION_ID, notif)
        Log.d(TAG, "Simulated vibration notification #${vibrationCount + 1}/$MAX_VIBRATION_COUNT sent")
    }

    private fun cancelSimulatedNotification() { NotificationManagerCompat.from(this).cancel(SIMULATED_NOTIFICATION_ID) }

    override fun onDestroy() {
        testReceiver?.let { LocalBroadcastManager.getInstance(this).unregisterReceiver(it) }
        stopVibration()
        super.onDestroy()
    }
}
