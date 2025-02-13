package com.gscube.smsbulker.ui.auth

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.gscube.smsbulker.MainActivity
import com.gscube.smsbulker.R
import com.gscube.smsbulker.SmsBulkerApplication
import com.gscube.smsbulker.databinding.FragmentLoginBinding
import com.gscube.smsbulker.di.ViewModelFactory
import javax.inject.Inject

class LoginFragment : Fragment() {
    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    private val TAG = "LoginFragment"
    private var isNavigating = false

    @Inject
    lateinit var viewModelFactory: ViewModelFactory
    private lateinit var viewModel: AuthViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (requireActivity().application as SmsBulkerApplication).appComponent.inject(this)
        viewModel = ViewModelProvider(this, viewModelFactory)[AuthViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        try {
            setupClickListeners()
            observeViewModel()
        } catch (e: Exception) {
            Log.e(TAG, "Error in onViewCreated: ${e.message}")
            showError("Failed to initialize login screen")
        }
    }

    private fun setupClickListeners() {
        binding.loginButton.setOnClickListener {
            try {
                if (isNavigating) {
                    Log.d(TAG, "Already navigating, ignoring login click")
                    return@setOnClickListener
                }

                val email = binding.emailInput.text?.toString()
                val password = binding.passwordInput.text?.toString()

                if (email.isNullOrBlank() || password.isNullOrBlank()) {
                    showError("Please fill in all fields")
                    return@setOnClickListener
                }

                Log.d(TAG, "Attempting login with email: $email")
                binding.loginButton.isEnabled = false
                binding.progressBar.visibility = View.VISIBLE
                viewModel.login(email, password)
            } catch (e: Exception) {
                Log.e(TAG, "Error during login: ${e.message}")
                showError("Failed to process login")
                binding.loginButton.isEnabled = true
                binding.progressBar.visibility = View.GONE
            }
        }

        binding.signupLink.setOnClickListener {
            if (!isNavigating) {
                findNavController().navigate(R.id.action_loginFragment_to_signupFragment)
            }
        }

        binding.forgotPasswordLink.setOnClickListener {
            val email = binding.emailInput.text?.toString()
            if (email.isNullOrBlank()) {
                showError("Please enter your email address")
                return@setOnClickListener
            }
            viewModel.resetPassword(email)
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.let { owner ->
            viewModel.isLoading.observe(owner) { isLoading ->
                if (_binding != null) {
                    binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
                    binding.loginButton.isEnabled = !isLoading
                }
            }

            viewModel.error.observe(owner) { error ->
                error?.let { 
                    Log.e(TAG, "Login error: $it")
                    showError(it)
                    isNavigating = false
                    binding.loginButton.isEnabled = true
                    binding.progressBar.visibility = View.GONE
                    viewModel.clearError()
                }
            }

            viewModel.authSuccess.observe(owner) { success ->
                Log.d(TAG, "Auth success: $success")
                if (success && !isNavigating && isAdded && activity?.isFinishing == false) {
                    startMainActivity()
                }
            }

            viewModel.passwordResetSent.observe(owner) { sent ->
                if (sent && _binding != null) {
                    Snackbar.make(
                        binding.root,
                        "Password reset email sent. Please check your inbox.",
                        Snackbar.LENGTH_LONG
                    ).show()
                    viewModel.clearPasswordResetSent()
                }
            }
        }
    }

    private fun showError(message: String) {
        if (isAdded && activity?.isFinishing == false && _binding != null) {
            Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun startMainActivity() {
        if (isNavigating) {
            Log.d(TAG, "Already navigating, skipping startMainActivity")
            return
        }
        
        Log.d(TAG, "Starting MainActivity")
        try {
            isNavigating = true
            val intent = Intent(requireContext(), MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            requireActivity().finish()
        } catch (e: Exception) {
            Log.e(TAG, "Error starting MainActivity: ${e.message}")
            showError("Error starting main screen")
            isNavigating = false
            binding.loginButton.isEnabled = true
            binding.progressBar.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        isNavigating = false
    }
} 