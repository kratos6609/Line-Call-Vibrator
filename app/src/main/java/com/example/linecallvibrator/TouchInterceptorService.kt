package com.example.linecallvibrator

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.content.BroadcastReceiver
import androidx.core.app.NotificationManagerCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class TouchInterceptorService : Service() {

    companion object {
        private const val TAG = "TouchInterceptor"
        const val ACTION_START_INTERCEPT = "com.example.linecallvibrator.START_TOUCH_INTERCEPT"
        const val ACTION_STOP_INTERCEPT = "com.example.linecallvibrator.STOP_TOUCH_INTERCEPT"
        const val ACTION_TOUCH_DETECTED = "com.example.linecallvibrator.TOUCH_DETECTED"
    }

    private var windowManager: WindowManager? = null
    private var touchView: View? = null
    private var screenReceiver: BroadcastReceiver? = null

    override fun onCreate() {
        super.onCreate()
        registerScreenReceiver()
        Log.d(TAG, "TouchInterceptorService created")
    }

    private fun registerScreenReceiver() {
        screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        Log.d(TAG, "Screen off, removing overlay")
                        removeOverlay()
                    }
                    Intent.ACTION_USER_PRESENT -> {
                        // User unlocked, keep overlay if vibrating
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screenReceiver!!, filter)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.action?.let {
            when (it) {
                ACTION_START_INTERCEPT -> showOverlay()
                ACTION_STOP_INTERCEPT -> removeOverlay()
            }
        }
        return START_NOT_STICKY
    }

    private fun showOverlay() {
        if (touchView != null) {
            Log.d(TAG, "Overlay already showing")
            return
        }

        // Check overlay permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Log.w(TAG, "No overlay permission, cannot show interceptor")
            return
        }

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        touchView = View(this).apply {
            setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        Log.d(TAG, "Touch detected! Stopping vibration")
                        // Broadcast to stop vibration
                        val stopIntent = Intent(ACTION_TOUCH_DETECTED)
                        stopIntent.setPackage(packageName)
                        LocalBroadcastManager.getInstance(this@TouchInterceptorService).sendBroadcast(stopIntent)
                        // Also send system broadcast for LineCallListenerService
                        sendBroadcast(Intent(ACTION_TOUCH_DETECTED).setPackage(packageName))
                        removeOverlay()
                        true
                    }
                    MotionEvent.ACTION_OUTSIDE -> {
                        // Touch outside (shouldn't happen with full screen)
                        Log.d(TAG, "Touch outside detected")
                        true
                    }
                    else -> false
                }
            }
            // Make sure it can receive touches
            isClickable = true
            isFocusable = false
            isFocusableInTouchMode = false
        }

        try {
            windowManager?.addView(touchView!!, params)
            Log.d(TAG, "Touch interceptor overlay shown")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show overlay", e)
            touchView = null
        }
    }

    private fun removeOverlay() {
        if (touchView != null && windowManager != null) {
            try {
                windowManager?.removeView(touchView)
                Log.d(TAG, "Touch interceptor overlay removed")
            } catch (e: Exception) {
                Log.w(TAG, "Error removing overlay", e)
            }
            touchView = null
            windowManager = null
        }
    }

    override fun onDestroy() {
        removeOverlay()
        screenReceiver?.let { unregisterReceiver(it) }
        super.onDestroy()
        Log.d(TAG, "TouchInterceptorService destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}