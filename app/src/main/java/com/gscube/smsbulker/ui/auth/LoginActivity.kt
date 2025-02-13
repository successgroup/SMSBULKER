package com.gscube.smsbulker.ui.auth

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.gscube.smsbulker.MainActivity
import com.gscube.smsbulker.R
import com.gscube.smsbulker.SmsBulkerApplication
import com.gscube.smsbulker.databinding.ActivityLoginBinding
import javax.inject.Inject

class LoginActivity : AppCompatActivity() {
    private val TAG = "LoginActivity"
    @Inject lateinit var viewModel: AuthViewModel
    private var _binding: ActivityLoginBinding? = null
    private val binding get() = _binding!!
    private lateinit var navController: NavController
    private var isNavigating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        (application as SmsBulkerApplication).appComponent.inject(this)
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate called")

        try {
            _binding = ActivityLoginBinding.inflate(layoutInflater)
            setContentView(binding.root)

            // Set up navigation first
            val navHostFragment = supportFragmentManager
                .findFragmentById(R.id.nav_host_fragment_auth) as? NavHostFragment
            if (navHostFragment == null) {
                Log.e(TAG, "NavHostFragment not found")
                finish()
                return
            }
            navController = navHostFragment.navController

            // Observe auth state
            viewModel.authSuccess.observe(this) { success ->
                Log.d(TAG, "Auth success observed in LoginActivity: $success")
                if (success && !isNavigating && !isFinishing) {
                    startMainActivity()
                }
            }

            // Check login state after navigation setup
            if (viewModel.isLoggedIn()) {
                Log.d(TAG, "User already logged in, starting MainActivity")
                startMainActivity()
                return
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate: ${e.message}")
            finish()
            return
        }
    }

    private fun startMainActivity() {
        if (isNavigating) {
            Log.d(TAG, "Already navigating to MainActivity")
            return
        }

        Log.d(TAG, "Starting MainActivity from LoginActivity")
        isNavigating = true
        
        try {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            Log.e(TAG, "Error starting MainActivity: ${e.message}")
            isNavigating = false
        }
    }

    override fun onBackPressed() {
        if (!::navController.isInitialized || navController.currentDestination?.id == R.id.loginFragment) {
            finish()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
        isNavigating = false
    }
} 