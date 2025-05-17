package com.example.paivalocker.service

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import com.example.paivalocker.R
import android.util.Log
import android.view.WindowManager.LayoutParams
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import android.app.Activity
import android.content.Context
import com.example.paivalocker.AuthenticationActivity
import android.provider.Settings
import android.content.pm.PackageManager
import android.os.Build

class OverlayService : Service() {
    private val TAG = "OverlayService"
    private var windowManager: WindowManager? = null
    private var dotView: View? = null
    private var authView: View? = null
    private var dotParams: LayoutParams? = null
    private var authParams: LayoutParams? = null
    private var currentPackageName: String? = null

    override fun onCreate() {
        super.onCreate()
        if (!canDrawOverlays()) {
            Log.e(TAG, "Overlay permission not granted")
            stopSelf()
            return
        }
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        setupDot()
    }

    private fun canDrawOverlays(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun setupDot() {
        try {
            dotView = LayoutInflater.from(this).inflate(R.layout.overlay_dot, null)
            dotParams = LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    LayoutParams.TYPE_PHONE
                },
                LayoutParams.FLAG_NOT_FOCUSABLE or LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 0
                y = 100
            }

            windowManager?.addView(dotView, dotParams)
            Log.d(TAG, "Dot overlay added")
        } catch (e: Exception) {
            Log.e(TAG, "Error adding dot overlay", e)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.hasExtra("package_name") == true) {
            val packageName = intent.getStringExtra("package_name")
            if (packageName != null) {
                showAuthenticationOverlay(packageName)
            }
        }
        return START_STICKY
    }

    fun showAuthenticationOverlay(packageName: String) {
        if (authView != null) return // Already showing
        if (!canDrawOverlays()) {
            Log.e(TAG, "Cannot show auth overlay: permission not granted")
            return
        }

        currentPackageName = packageName

        try {
            authView = LayoutInflater.from(this).inflate(R.layout.overlay_authentication, null)
            authParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    LayoutParams.TYPE_PHONE
                },
                LayoutParams.FLAG_NOT_FOCUSABLE or LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER
            }

            // Set up the authentication button
            val authenticateButton = authView?.findViewById<Button>(R.id.authenticateButton)
            authenticateButton?.setOnClickListener {
                startAuthentication()
            }

            // Set the app name
            val appNameText = authView?.findViewById<TextView>(R.id.appNameText)
            appNameText?.text = getAppName(packageName)

            windowManager?.addView(authView, authParams)
            Log.d(TAG, "Auth overlay added for $packageName")
        } catch (e: Exception) {
            Log.e(TAG, "Error adding auth overlay", e)
        }
    }

    private fun startAuthentication() {
        val intent = Intent(this, AuthenticationActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("package_name", currentPackageName)
        }
        startActivity(intent)
        hideAuthenticationOverlay()
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

    fun hideAuthenticationOverlay() {
        try {
            if (authView != null) {
                windowManager?.removeView(authView)
                authView = null
                currentPackageName = null
                Log.d(TAG, "Auth overlay removed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing auth overlay", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        try {
            if (dotView != null) {
                windowManager?.removeView(dotView)
                dotView = null
            }
            if (authView != null) {
                windowManager?.removeView(authView)
                authView = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up overlays", e)
        }
    }
} 