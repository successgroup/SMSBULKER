package com.gscube.smsbulker.ui.splash

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.animation.AnimationUtils
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import com.gscube.smsbulker.MainActivity
import com.gscube.smsbulker.R
import com.gscube.smsbulker.ui.auth.LoginActivity
import com.gscube.smsbulker.utils.SecureStorage

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {
    private lateinit var secureStorage: SecureStorage
    private val TAG = "SplashActivity"

    // Animation durations
    private val INITIAL_DELAY = 300L // Wait before starting animations
    private val ICON_FADE_DURATION = 400L // Icon fade in
    private val TEXT_FADE_DURATION = 600L // Initial text fade in
    private val TEXT_FADE_DELAY = 200L // Delay before text starts fading in
    private val COLOR_TRANSITION_DELAY = 1200L // When to start color transition
    private val COLOR_TRANSITION_DURATION = 1000L // How long the color transition takes
    private val FINAL_DELAY = 1500L // Extra time to show completed splash screen

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

            startAnimations()

        } catch (e: Exception) {
            Log.e(TAG, "Fatal error in onCreate: ${e.message}")
            Toast.makeText(this, "Fatal error during app startup", Toast.LENGTH_LONG).show()
        }
    }

    private fun startAnimations() {
        try {
            val rootLayout = findViewById<ConstraintLayout>(R.id.rootLayout)
            val splashIcon = findViewById<ImageView>(R.id.splashIcon)
            val appName = findViewById<TextView>(R.id.appName)
            val companyName = findViewById<TextView>(R.id.companyName)

            // Wait a bit before starting animations
            Handler(Looper.getMainLooper()).postDelayed({
                // First fade in the icon
                splashIcon.animate()
                    .alpha(1f)
                    .setDuration(ICON_FADE_DURATION)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .withEndAction {
                        // Start bounce animation after fade in
                        val chatBubbleAnim = AnimationUtils.loadAnimation(this, R.anim.chat_bubble_animation)
                        splashIcon.startAnimation(chatBubbleAnim)
                    }
                    .start()

                // Start text fade in with a slight delay
                Handler(Looper.getMainLooper()).postDelayed({
                    // Fade in app name
                    appName.animate()
                        .alpha(1f)
                        .setDuration(TEXT_FADE_DURATION)
                        .setInterpolator(AccelerateDecelerateInterpolator())
                        .start()

                    // Fade in company name slightly after app name
                    Handler(Looper.getMainLooper()).postDelayed({
                        companyName.animate()
                            .alpha(1f)
                            .setDuration(TEXT_FADE_DURATION)
                            .setInterpolator(AccelerateDecelerateInterpolator())
                            .start()
                    }, 100)
                }, TEXT_FADE_DELAY)

                // After initial animations, begin color transitions
                Handler(Looper.getMainLooper()).postDelayed({
                    // Background color transition
                    val backgroundFrom = ContextCompat.getColor(this, R.color.splash_background_start)
                    val backgroundTo = ContextCompat.getColor(this, R.color.splash_background_end)
                    
                    // Text color transitions
                    val textFrom = ContextCompat.getColor(this, R.color.splash_text_start)
                    val textTo = ContextCompat.getColor(this, R.color.splash_text_end)
                    val textSecondaryFrom = ContextCompat.getColor(this, R.color.splash_text_secondary_start)
                    val textSecondaryTo = ContextCompat.getColor(this, R.color.splash_text_secondary_end)

                    // Animate background
                    ValueAnimator.ofObject(ArgbEvaluator(), backgroundFrom, backgroundTo).apply {
                        duration = COLOR_TRANSITION_DURATION
                        addUpdateListener { animator -> 
                            val color = animator.animatedValue as Int
                            rootLayout.setBackgroundColor(color)
                            window.statusBarColor = color
                            window.navigationBarColor = color
                        }
                        start()
                    }

                    // Animate main text color
                    ValueAnimator.ofObject(ArgbEvaluator(), textFrom, textTo).apply {
                        duration = COLOR_TRANSITION_DURATION
                        addUpdateListener { animator ->
                            appName.setTextColor(animator.animatedValue as Int)
                        }
                        start()
                    }

                    // Animate secondary text color
                    ValueAnimator.ofObject(ArgbEvaluator(), textSecondaryFrom, textSecondaryTo).apply {
                        duration = COLOR_TRANSITION_DURATION
                        addUpdateListener { animator ->
                            companyName.setTextColor(animator.animatedValue as Int)
                        }
                        start()
                    }

                    // Navigate after all animations plus final delay
                    Handler(Looper.getMainLooper()).postDelayed({
                        navigateToNextScreen()
                    }, COLOR_TRANSITION_DURATION + FINAL_DELAY)
                }, COLOR_TRANSITION_DELAY)
            }, INITIAL_DELAY)

        } catch (e: Exception) {
            Log.e(TAG, "Error in animations: ${e.message}")
            // If animations fail, navigate after a short delay
            Handler(Looper.getMainLooper()).postDelayed({
                navigateToNextScreen()
            }, 1000)
        }
    }

    private fun navigateToNextScreen() {
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
    }
}