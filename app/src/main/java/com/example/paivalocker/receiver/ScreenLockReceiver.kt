package com.example.paivalocker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import com.example.paivalocker.data.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ScreenLockReceiver : BroadcastReceiver() {
    private val TAG = "ScreenLockReceiver"
    private lateinit var appPreferences: AppPreferences
    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_SCREEN_OFF || 
            intent.action == Intent.ACTION_USER_PRESENT) {
            Log.d(TAG, "Screen state changed: ${intent.action}")
            appPreferences = AppPreferences(context)
            scope.launch {
                appPreferences.clearAllAuthTimes()
            }
        }
    }

    companion object {
        fun register(context: Context): ScreenLockReceiver {
            val receiver = ScreenLockReceiver()
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            context.registerReceiver(receiver, filter)
            return receiver
        }
    }
} 