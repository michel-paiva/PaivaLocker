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
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.TaskStackBuilder
import com.example.paivalocker.AuthenticationActivity
import com.example.paivalocker.MainActivity
import com.example.paivalocker.R
import com.example.paivalocker.data.AppPreferences
import com.example.paivalocker.receiver.ScreenLockReceiver
import com.example.paivalocker.receiver.ServiceRestartReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.cancel
import android.app.ActivityOptions
import android.app.ActivityManager
import android.content.Context

class AppMonitorService : Service() {
    private lateinit var appPreferences: AppPreferences
    private lateinit var usageStatsManager: UsageStatsManager
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val TAG = "AppMonitorService"
    private var lastEventTime = 0L
    private val handler = Handler(Looper.getMainLooper())
    private var isMonitoring = false
    private val MONITORING_NOTIFICATION_ID = 1
    private val AUTH_NOTIFICATION_ID = 2
    private val CHANNEL_ID = "AppMonitorChannel"
    private var currentLockedPackage: String? = null
    private val MONITORING_INTERVAL = 450L // Check every 2 seconds instead of 1
    private var lastCheckedPackage: String? = null
    private var lastCheckTime = 0L
    private val MIN_CHECK_INTERVAL = 300L // Minimum time between checks
    private var screenLockReceiver: ScreenLockReceiver? = null
    private var authenticatingPackage: String? = null // Track which app is being authenticated

    companion object {
        private const val KEY_LAUNCH_OVERLAY = "android.intent.extra.LAUNCH_OVERLAY"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        appPreferences = AppPreferences(applicationContext)
        usageStatsManager = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        createNotificationChannel()
        startForeground(MONITORING_NOTIFICATION_ID, createMonitoringNotification())
        screenLockReceiver = ScreenLockReceiver.register(this)
        startMonitoring()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "App Monitor Service",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Monitors app launches for PaivaLocker"
                    setShowBadge(true)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    enableLights(true)
                    enableVibration(true)
                }
                val notificationManager = getSystemService(NotificationManager::class.java)
                notificationManager.createNotificationChannel(channel)
                Log.d(TAG, "Notification channel created successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create notification channel", e)
            }
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
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    private fun showAuthenticationNotification(packageName: String) {
        try {
            val appName = getAppName(packageName)
            Log.d(TAG, "Showing authentication notification for $appName ($packageName)")
            
            // Create full screen intent for immediate attention
            val fullScreenIntent = Intent(this, AuthenticationActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("package_name", packageName)
            }
            
            val fullScreenPendingIntent = PendingIntent.getActivity(
                this,
                packageName.hashCode(),
                fullScreenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        
            // Build the notification
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Authentication Required")
                .setContentText("Verify identity to open $appName")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .setAutoCancel(true)
                .setTimeoutAfter(30000) // Auto-cancel after 30 seconds
                .build()
        
            // Show as a new notification
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.notify(AUTH_NOTIFICATION_ID, notification)
            Log.d(TAG, "Authentication notification shown successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show authentication notification", e)
        }
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
        Log.d(TAG, "Starting app monitoring")
        
        handler.post(object : Runnable {
            override fun run() {
                try {
                    val currentTime = System.currentTimeMillis()
                    // Only check if enough time has passed since last check
                    if (currentTime - lastCheckTime >= MIN_CHECK_INTERVAL) {
                        lastCheckTime = currentTime
                        checkAppUsage()
                    }
                    
                    if (isMonitoring) {
                        handler.postDelayed(this, MONITORING_INTERVAL)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in monitoring loop", e)
                    isMonitoring = false
                    startMonitoring()
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
        
        var foundEvent = false
        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            foundEvent = true
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                val packageName = event.packageName
                // Skip if we're already authenticating this app
                if (packageName != authenticatingPackage && packageName != lastCheckedPackage) {
                    Log.d(TAG, "App moved to foreground: $packageName")
                    lastCheckedPackage = packageName
                    checkAndLockApp(packageName)
                }
            }
        }
        
        if (!foundEvent) {
            // If no events were found, check current foreground app
            val currentApp = getCurrentForegroundApp()
            if (currentApp != null && currentApp != authenticatingPackage && currentApp != lastCheckedPackage) {
                Log.d(TAG, "Current foreground app: $currentApp")
                lastCheckedPackage = currentApp
                checkAndLockApp(currentApp)
            }
        }
        
        lastEventTime = currentTime
    }

    private fun getCurrentForegroundApp(): String? {
        return try {
            val currentTime = System.currentTimeMillis()
            val stats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                currentTime - 1000,
                currentTime
            )
            
            stats?.maxByOrNull { it.lastTimeUsed }?.packageName
        } catch (e: Exception) {
            Log.e(TAG, "Error getting current foreground app", e)
            null
        }
    }

    private fun checkAndLockApp(packageName: String) {
        serviceScope.launch {
            try {
                if (isAppLocked(packageName)) {
                    val authTimes = appPreferences.appAuthTimes.first()
                    val lastAuthTime = authTimes[packageName] ?: 0L
                    val currentTime = System.currentTimeMillis()
                    val timeSinceLastAuth = currentTime - lastAuthTime
                    
                    if (timeSinceLastAuth > 60000) { // 60 seconds
                        Log.d(TAG, "App is locked: $packageName")
                        currentLockedPackage = packageName
                        withContext(Dispatchers.Main) {
                            handleLockedApp(packageName)
                        }
                    } else {
                        Log.d(TAG, "Skipping auth - recently authenticated for $packageName")
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
        try {
            Log.d(TAG, "Handling locked app: $packageName")
            
            // Special handling for PaivaLocker itself
            if (packageName == this.packageName) {
                Log.d(TAG, "Handling PaivaLocker authentication")
                
                // Check if we're in the main activity
                val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val tasks = am.getRunningTasks(1)
                val currentActivity = tasks[0].topActivity
                
                if (currentActivity?.className == "com.example.paivalocker.MainActivity") {
                    Log.d(TAG, "In main activity, showing biometrics")
                    // Start authentication activity directly for PaivaLocker
                    val authIntent = Intent(this, AuthenticationActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        putExtra("package_name", packageName)
                    }
                    startActivity(authIntent)
                } else {
                    Log.d(TAG, "Not in main activity, skipping biometrics")
                }
                return
            }
            
            // For other apps, proceed with normal handling
            authenticatingPackage = packageName
            
            // Show authentication notification
            showAuthenticationNotification(packageName)
            
            // Start the overlay service
            val overlayIntent = Intent(this, OverlayService::class.java).apply {
                putExtra("package_name", packageName)
            }
            startService(overlayIntent)
            
            // Minimize the app after a short delay
            handler.postDelayed({
                try {
                    val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_HOME)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(homeIntent)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to minimize app", e)
                }
                // Clear the authenticating package after a delay
                handler.postDelayed({
                    authenticatingPackage = null
                }, 2000)
            }, 300)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle locked app", e)
            authenticatingPackage = null
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
        stopMonitoring()
        screenLockReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering screen lock receiver", e)
            }
        }
        serviceScope.cancel()
        
        // Send broadcast to restart service
        val broadcastIntent = Intent(this, ServiceRestartReceiver::class.java).apply {
            action = "com.example.paivalocker.RESTART_SERVICE"
        }
        sendBroadcast(broadcastIntent)
    }
}