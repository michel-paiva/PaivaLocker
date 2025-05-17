package com.example.paivalocker

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.example.paivalocker.service.OverlayService

class AuthenticationActivity : AppCompatActivity() {
    private var targetPackage: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        targetPackage = intent.getStringExtra("package_name")
        
        if (targetPackage == null) {
            Toast.makeText(this, "Invalid app", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        checkBiometricAvailability()
    }

    private fun checkBiometricAvailability() {
        val biometricManager = BiometricManager.from(this)
        when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                showBiometricPrompt()
            }
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                Toast.makeText(this, "No biometric hardware available", Toast.LENGTH_LONG).show()
                finish()
            }
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                Toast.makeText(this, "Biometric hardware unavailable", Toast.LENGTH_LONG).show()
                finish()
            }
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                Toast.makeText(this, "No biometrics enrolled. Please set up fingerprint or face authentication in your device settings.", Toast.LENGTH_LONG).show()
                // Open security settings
                val intent = Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS)
                startActivity(intent)
                finish()
            }
            else -> {
                Toast.makeText(this, "Biometric authentication not available", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun showBiometricPrompt() {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    // Authentication successful, launch the target app
                    targetPackage?.let { packageName ->
                        try {
                            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
                            if (launchIntent != null) {
                                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                startActivity(launchIntent)
                            }
                        } catch (e: Exception) {
                            Toast.makeText(this@AuthenticationActivity, "Failed to launch app", Toast.LENGTH_SHORT).show()
                        }
                    }
                    // Hide the overlay
                    val overlayIntent = Intent(this@AuthenticationActivity, OverlayService::class.java)
                    stopService(overlayIntent)
                    finish()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    Toast.makeText(this@AuthenticationActivity, "Authentication failed: $errString", Toast.LENGTH_SHORT).show()
                    // Hide the overlay
                    val overlayIntent = Intent(this@AuthenticationActivity, OverlayService::class.java)
                    stopService(overlayIntent)
                    finish()
                }

                override fun onAuthenticationFailed() {
                    Toast.makeText(this@AuthenticationActivity, "Authentication failed", Toast.LENGTH_SHORT).show()
                    // Hide the overlay
                    val overlayIntent = Intent(this@AuthenticationActivity, OverlayService::class.java)
                    stopService(overlayIntent)
                    finish()
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Authentication Required")
            .setSubtitle("Please authenticate to access this app")
            .setNegativeButtonText("Cancel")
            .build()

        biometricPrompt.authenticate(promptInfo)
    }
} 