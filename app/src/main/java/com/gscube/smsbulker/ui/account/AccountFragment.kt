package com.gscube.smsbulker.ui.account

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.gscube.smsbulker.SmsBulkerApplication
import com.gscube.smsbulker.R
import com.gscube.smsbulker.databinding.FragmentAccountBinding
import com.gscube.smsbulker.utils.showErrorSnackbar
import com.gscube.smsbulker.utils.showSuccessSnackbar
import com.gscube.smsbulker.ui.auth.LoginActivity
import javax.inject.Inject
import kotlinx.coroutines.launch

class AccountFragment : Fragment() {
    private var _binding: FragmentAccountBinding? = null
    private val binding get() = _binding!!
    
    @Inject
    lateinit var viewModel: AccountViewModel
    
    override fun onCreate(savedInstanceState: Bundle?) {
        (requireActivity().application as SmsBulkerApplication).appComponent.inject(this)
        super.onCreate(savedInstanceState)
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAccountBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupObservers()
        setupClickListeners()
        
        // Load initial data
        viewModel.loadUserProfile()
        viewModel.loadCreditBalance()
        viewModel.loadSubscription()
    }
    
    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.userProfile.collect { result ->
                        result?.let { profileResult ->
                            profileResult.onSuccess { profile ->
                                binding.apply {
                                    textName.text = profile.name
                                    textEmail.text = profile.email
                                    textPhone.text = profile.phone
                                    textCompany.text = profile.company ?: "Not specified"
                                    textCompanyAlias.text = "Sender ID: ${profile.companyAlias}"
                                    textApiKey.text = profile.apiKey
                                }
                            }.onFailure { error ->
                                showErrorSnackbar(error.message ?: "Failed to load profile")
                            }
                        }
                    }
                }
                
                launch {
                    viewModel.creditBalance.collect { result ->
                        result?.let { balanceResult ->
                            balanceResult.onSuccess { balance ->
                                binding.apply {
                                    textAvailableCredits.text = getString(R.string.available_credits, balance.availableCredits)
                                    textUsedCredits.text = getString(R.string.used_credits, balance.usedCredits)
                                    switchAutoRefill.isChecked = balance.autoRefillEnabled
                                    textLowBalanceAlert.text = getString(R.string.low_balance_alert, balance.lowBalanceAlert)
                                }
                            }.onFailure { error ->
                                showErrorSnackbar(error.message ?: "Failed to load credit balance")
                            }
                        }
                    }
                }
                
                launch {
                    viewModel.subscription.collect { result ->
                        result?.let { subscriptionResult ->
                            subscriptionResult.onSuccess { subscription ->
                                binding.apply {
                                    textPlanName.text = subscription.planName
                                    textPlanStatus.text = subscription.status
                                    textMonthlyCredits.text = getString(R.string.monthly_credits, subscription.monthlyCredits)
                                    textPrice.text = getString(R.string.price_per_month, subscription.price)
                                    switchAutoRenew.isChecked = subscription.autoRenew
                                    
                                    // Update features list
                                    layoutFeatures.removeAllViews()
                                    subscription.features.forEach { feature ->
                                        val featureView = layoutInflater.inflate(R.layout.item_feature, layoutFeatures, false)
                                        featureView.findViewById<android.widget.TextView>(R.id.textFeature).text = feature
                                        layoutFeatures.addView(featureView)
                                    }
                                }
                            }.onFailure { error ->
                                showErrorSnackbar(error.message ?: "Failed to load subscription")
                            }
                        }
                    }
                }
            }
        }
    }
    
    private fun setupClickListeners() {
        binding.apply {
            buttonEditProfile.setOnClickListener {
                findNavController().navigate(R.id.action_accountFragment_to_editProfileFragment)
            }
            
            buttonRegenerateApiKey.setOnClickListener {
                viewModel.refreshApiKey()
            }
            
            buttonChangePlan.setOnClickListener {
                findNavController().navigate(R.id.action_accountFragment_to_subscriptionPlansFragment)
            }
            
            switchAutoRefill.setOnCheckedChangeListener { _, isChecked ->
                // TODO: Update auto-refill setting
            }
            
            switchAutoRenew.setOnCheckedChangeListener { _, isChecked ->
                // TODO: Update auto-renew setting
            }
            
            buttonNotificationSettings.setOnClickListener {
                findNavController().navigate(R.id.action_accountFragment_to_notificationSettingsFragment)
            }
            
            buttonSignOut.setOnClickListener {
                viewModel.signOut()
                val intent = Intent(requireContext(), LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
                requireActivity().finish()
            }
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 