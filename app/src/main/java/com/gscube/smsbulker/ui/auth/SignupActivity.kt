package com.gscube.smsbulker.ui.auth

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import com.gscube.smsbulker.MainActivity
import com.gscube.smsbulker.SmsBulkerApplication
import com.gscube.smsbulker.databinding.ActivitySignupBinding
import com.gscube.smsbulker.R
import javax.inject.Inject

class SignupActivity : AppCompatActivity() {
    private val TAG = "SignupActivity"
    private var _binding: ActivitySignupBinding? = null
    private val binding get() = _binding!!
    @Inject
    lateinit var viewModel: AuthViewModel
    private var isNavigating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        (application as SmsBulkerApplication).appComponent.inject(this)
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate called")

        try {
            _binding = ActivitySignupBinding.inflate(layoutInflater)
            setContentView(binding.root)

            setupClickListeners()
            observeViewModel()
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate: ${e.message}")
            finish()
            return
        }
    }

    private fun setupClickListeners() {
        binding.signupButton.setOnClickListener {
            val email = binding.emailInput.text.toString()
            val password = binding.passwordInput.text.toString()
            val companyName = binding.companyNameInput.text.toString()
            val phone = binding.phoneInput.text.toString()

            if (email.isBlank() || password.isBlank() || companyName.isBlank() || phone.isBlank()) {
                showError("Please fill in all fields")
                return@setOnClickListener
            }

            viewModel.signup(email, password, companyName, phone)
        }

        binding.loginLink.setOnClickListener {
            finish() // Go back to LoginActivity
        }
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.signupButton.isEnabled = !isLoading
        }

        viewModel.error.observe(this) { error ->
            error?.let { showError(it) }
        }

        viewModel.authSuccess.observe(this) { success ->
            if (success) {
                startMainActivity()
                finish()
            }
        }
    }

    private fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    private fun startMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }
} 