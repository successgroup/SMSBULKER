package com.gscube.smsbulker.ui.splash

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.gscube.smsbulker.MainActivity
import com.gscube.smsbulker.R
import com.gscube.smsbulker.ui.auth.LoginActivity
import com.gscube.smsbulker.utils.SecureStorage

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {
    private lateinit var secureStorage: SecureStorage
    private val TAG = "SplashActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)
            Log.d(TAG, "Starting SplashActivity")
            setContentView(R.layout.activity_splash)
            
            try {
                secureStorage = SecureStorage(applicationContext)
                Log.d(TAG, "SecureStorage initialized")
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing SecureStorage: ${e.message}")
                Toast.makeText(this, "Error initializing secure storage", Toast.LENGTH_LONG).show()
                return
            }

            // Delay for 2 seconds to show splash screen
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    // Check if user is logged in
                    val isLoggedIn = try {
                        secureStorage.isLoggedIn()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error checking login status: ${e.message}")
                        false
                    }
                    Log.d(TAG, "Login status: $isLoggedIn")

                    val intent = if (isLoggedIn) {
                        Log.d(TAG, "Navigating to MainActivity")
                        Intent(this, MainActivity::class.java)
                    } else {
                        Log.d(TAG, "Navigating to LoginActivity")
                        Intent(this, LoginActivity::class.java)
                    }
                    // Add flags to clear the back stack
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                } catch (e: Exception) {
                    Log.e(TAG, "Error during navigation: ${e.message}")
                    Toast.makeText(this, "Error during app initialization", Toast.LENGTH_LONG).show()
                }
            }, 2000)
        } catch (e: Exception) {
            Log.e(TAG, "Fatal error in onCreate: ${e.message}")
            Toast.makeText(this, "Fatal error during app startup", Toast.LENGTH_LONG).show()
        }
    }
} 