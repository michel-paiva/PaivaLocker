package com.example.paivalocker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.paivalocker.AuthenticationActivity
import com.example.paivalocker.MainActivity
import com.example.paivalocker.R
import com.example.paivalocker.data.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.cancel

class AppMonitorService : Service() {
    private lateinit var appPreferences: AppPreferences
    private lateinit var usageStatsManager: UsageStatsManager
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val TAG = "AppMonitorService"
    private var lastEventTime = 0L
    private val handler = Handler(Looper.getMainLooper())
    private var isMonitoring = false
    private val NOTIFICATION_ID = 1
    private val CHANNEL_ID = "AppMonitorChannel"
    private lateinit var appLaunchReceiver: AppLaunchReceiver
    private var currentLockedPackage: String? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        appPreferences = AppPreferences(applicationContext)
        usageStatsManager = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        startMonitoring()
        
        // Create and register the receiver
        appLaunchReceiver = AppLaunchReceiver()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_MAIN)
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        registerReceiver(appLaunchReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "App Monitor Service",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Monitors app launches for PaivaLocker"
                enableVibration(true)
                enableLights(true)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PaivaLocker Active")
            .setContentText("Monitoring app launches")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun showAuthenticationNotification(packageName: String) {
        val authIntent = Intent(this, AuthenticationActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("package_name", packageName)
        }
        
        val authPendingIntent = PendingIntent.getActivity(
            this,
            packageName.hashCode(),
            authIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Authentication Required")
            .setContentText("Tap to authenticate for $packageName")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(authPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(packageName.hashCode(), notification)
    }

    private fun startMonitoring() {
        if (isMonitoring) return
        isMonitoring = true
        handler.post(object : Runnable {
            override fun run() {
                checkAppUsage()
                if (isMonitoring) {
                    handler.postDelayed(this, 1000) // Check every second
                }
            }
        })
    }

    private fun stopMonitoring() {
        isMonitoring = false
        handler.removeCallbacksAndMessages(null)
    }

    private fun checkAppUsage() {
        val currentTime = System.currentTimeMillis()
        val usageEvents = usageStatsManager.queryEvents(lastEventTime, currentTime)
        val event = UsageEvents.Event()
        
        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                val packageName = event.packageName
                Log.d(TAG, "App moved to foreground: $packageName")
                checkAndLockApp(packageName)
            }
        }
        lastEventTime = currentTime
    }

    private fun checkAndLockApp(packageName: String) {
        serviceScope.launch {
            try {
                val lockedApps = withContext(Dispatchers.IO) {
                    appPreferences.lockedApps.first()
                }
                Log.d(TAG, "Locked apps: $lockedApps")
                if (packageName in lockedApps) {
                    Log.d(TAG, "App is locked: $packageName")
                    currentLockedPackage = packageName
                    showAuthenticationNotification(packageName)
                } else {
                    Log.d(TAG, "App is not locked: $packageName")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking locked apps", e)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        try {
            unregisterReceiver(appLaunchReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver", e)
        }
        stopMonitoring()
        serviceScope.cancel()
    }
} 