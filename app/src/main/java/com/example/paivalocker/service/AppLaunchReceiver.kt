package com.example.paivalocker.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class AppLaunchReceiver : BroadcastReceiver() {
    private val TAG = "AppLaunchReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_LAUNCHER)) {
            val packageName = intent.`package` ?: return
            Log.d(TAG, "App launched: $packageName")
            
            // Forward the app launch to our service
            val serviceIntent = Intent(context, AppMonitorService::class.java).apply {
                action = Intent.ACTION_MAIN
                putExtra("package_name", packageName)
            }
            context.startService(serviceIntent)
        }
    }
} 