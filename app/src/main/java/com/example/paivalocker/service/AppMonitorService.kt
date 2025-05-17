package com.example.paivalocker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
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
    private var currentLockedPackage: String? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        appPreferences = AppPreferences(applicationContext)
        usageStatsManager = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createMonitoringNotification())
        startMonitoring()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "App Monitor Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors app launches for PaivaLocker"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createMonitoringNotification(): Notification {
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
            .build()
    }

    private fun showAuthenticationNotification(packageName: String) {
        val appName = getAppName(packageName)
        
        val authIntent = Intent(this, AuthenticationActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
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
            .setContentText("Verify identity to open $appName")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(authPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(packageName.hashCode(), notification)
    }

    private fun getAppName(packageName: String): String {
        return try {
            val pm = packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName
        }
    }

    private fun startMonitoring() {
        if (isMonitoring) return
        isMonitoring = true
        handler.post(object : Runnable {
            override fun run() {
                checkAppUsage()
                if (isMonitoring) {
                    handler.postDelayed(this, 2000) // Check every 2 seconds
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
                if (packageName != this.packageName) { // Don't lock ourselves
                    Log.d(TAG, "App moved to foreground: $packageName")
                    checkAndLockApp(packageName)
                }
            }
        }
        lastEventTime = currentTime
    }

    private fun checkAndLockApp(packageName: String) {
        serviceScope.launch {
            try {
                if (isAppLocked(packageName)) {
                    Log.d(TAG, "App is locked: $packageName")
                    currentLockedPackage = packageName
                    withContext(Dispatchers.Main) {
                        handleLockedApp(packageName)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking locked apps", e)
            }
        }
    }

    private suspend fun isAppLocked(packageName: String): Boolean {
        val lockedApps = appPreferences.lockedApps.first()
        return packageName in lockedApps
    }

    private fun handleLockedApp(packageName: String) {
        // Minimize the locked app
        val homeIntent = Intent(Intent.ACTION_MAIN)
        homeIntent.addCategory(Intent.CATEGORY_HOME)
        homeIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(homeIntent)
        
        // Show authentication notification
        showAuthenticationNotification(packageName)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        stopMonitoring()
        serviceScope.cancel()
    }
}