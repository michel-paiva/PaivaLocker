package com.example.paivalocker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.paivalocker.service.AppMonitorService

class BootReceiver : BroadcastReceiver() {
    private val TAG = "BootReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Received action: ${intent.action}")
        
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED -> {
                Log.d(TAG, "Boot action detected, scheduling service start")
                // Add a delay to ensure system is ready
                Handler(Looper.getMainLooper()).postDelayed({
                    startService(context)
                }, 10000) // 10 second delay
            }
        }
    }

    private fun startService(context: Context) {
        try {
            Log.d(TAG, "Starting AppMonitorService")
            val serviceIntent = Intent(context, AppMonitorService::class.java)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            Log.d(TAG, "Service start intent sent")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start service", e)
        }
    }
} 