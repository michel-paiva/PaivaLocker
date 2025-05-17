package com.example.paivalocker.service

import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.example.paivalocker.AuthenticationActivity
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
    private var currentPackage: String? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        appPreferences = AppPreferences(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "android.intent.action.PACKAGE_ADDED" ||
            intent?.action == "android.intent.action.PACKAGE_REPLACED") {
            val packageName = intent.data?.schemeSpecificPart
            if (packageName != null) {
                checkAndLockApp(packageName)
            }
        }
        
        return START_STICKY
    }

    private fun checkAndLockApp(packageName: String) {
        serviceScope.launch {
            try {
                val lockedApps = withContext(Dispatchers.IO) {
                    appPreferences.lockedApps.first()
                }
                if (packageName in lockedApps) {
                    // Launch authentication activity
                    val authIntent = Intent(applicationContext, AuthenticationActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        putExtra("package_name", packageName)
                    }
                    startActivity(authIntent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
} 