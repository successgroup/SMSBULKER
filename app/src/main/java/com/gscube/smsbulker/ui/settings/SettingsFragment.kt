package com.gscube.smsbulker.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import com.gscube.smsbulker.SmsBulkerApplication
import com.gscube.smsbulker.databinding.FragmentSettingsBinding
import com.gscube.smsbulker.di.ViewModelFactory
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    private val viewModel: SettingsViewModel by viewModels { viewModelFactory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (requireActivity().application as SmsBulkerApplication)
            .appComponent.inject(this)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupViews()
        observeViewModel()
    }

    private fun setupViews() {
        binding.apply {
            saveApiKeyButton.setOnClickListener {
                val apiKey = apiKeyInput.text.toString()
                if (apiKey.isNotBlank()) {
                    viewModel.saveApiKey(apiKey)
                }
            }
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collectLatest { state ->
                    binding.apply {
                        progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE
                        
                        // Update API key field if available
                        state.apiKey?.let { key ->
                            if (apiKeyInput.text.toString() != key) {
                                apiKeyInput.setText(key)
                            }
                        }
                        
                        // Show messages
                        state.message?.let { message ->
                            Snackbar.make(root, message, Snackbar.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
