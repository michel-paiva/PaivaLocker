package com.example.paivalocker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.paivalocker.service.AppMonitorService

class ServiceRestartReceiver : BroadcastReceiver() {
    private val TAG = "ServiceRestartReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Service stopped, attempting to restart")
        val serviceIntent = Intent(context, AppMonitorService::class.java)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
} 