package com.gscube.smsbulker.ui.auth

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.gscube.smsbulker.MainActivity
import com.gscube.smsbulker.databinding.FragmentSignupBinding
import com.gscube.smsbulker.SmsBulkerApplication
import javax.inject.Inject

class SignupFragment : Fragment() {
    private val TAG = "SignupFragment"
    private var _binding: FragmentSignupBinding? = null
    private val binding get() = _binding!!
    @Inject
    lateinit var viewModel: AuthViewModel
    private var isNavigating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        (requireActivity().application as SmsBulkerApplication).appComponent.inject(this)
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSignupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        try {
            setupClickListeners()
            observeViewModel()
        } catch (e: Exception) {
            Log.e(TAG, "Error in onViewCreated: ${e.message}")
            showError("Failed to initialize signup screen")
        }
    }

    private fun setupClickListeners() {
        binding.signupButton.setOnClickListener {
            try {
                if (isNavigating) {
                    Log.d(TAG, "Already navigating, ignoring signup click")
                    return@setOnClickListener
                }

                val email = binding.emailInput.text?.toString()
                val password = binding.passwordInput.text?.toString()
                val companyName = binding.companyNameInput.text?.toString()
                val phone = binding.phoneInput.text?.toString()

                if (email.isNullOrBlank() || password.isNullOrBlank() || 
                    companyName.isNullOrBlank() || phone.isNullOrBlank()) {
                    showError("Please fill in all fields")
                    return@setOnClickListener
                }

                binding.signupButton.isEnabled = false
                binding.progressBar.visibility = View.VISIBLE
                viewModel.signup(email, password, companyName, phone)
            } catch (e: Exception) {
                Log.e(TAG, "Error during signup: ${e.message}")
                showError("Failed to process signup")
                binding.signupButton.isEnabled = true
                binding.progressBar.visibility = View.GONE
            }
        }

        binding.loginLink.setOnClickListener {
            if (!isNavigating) {
                findNavController().navigateUp()
            }
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.let { owner ->
            viewModel.isLoading.observe(owner) { isLoading ->
                if (_binding != null) {
                    binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
                    binding.signupButton.isEnabled = !isLoading
                }
            }

            viewModel.error.observe(owner) { error ->
                error?.let {
                    Log.e(TAG, "Signup error: $it")
                    showError(it)
                    isNavigating = false
                    binding.signupButton.isEnabled = true
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
            binding.signupButton.isEnabled = true
            binding.progressBar.visibility = View.GONE
        }
    }

    private fun showError(message: String) {
        if (isAdded && activity?.isFinishing == false && _binding != null) {
            Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        isNavigating = false
    }
} 