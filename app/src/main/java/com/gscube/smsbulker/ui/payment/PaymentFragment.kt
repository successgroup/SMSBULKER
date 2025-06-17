package com.gscube.smsbulker.ui.payment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.gscube.smsbulker.SmsBulkerApplication
import com.gscube.smsbulker.databinding.FragmentPaymentBinding
import com.gscube.smsbulker.di.ViewModelFactory
import com.paystack.android.core.Paystack
import com.paystack.android.ui.paymentsheet.PaymentSheet
import com.paystack.android.ui.paymentsheet.PaymentSheetResult
import kotlinx.coroutines.launch
import javax.inject.Inject

class PaymentFragment : Fragment() {

    @Inject
    lateinit var viewModelFactory: ViewModelFactory
    
    private var _binding: FragmentPaymentBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: PaymentViewModel by viewModels { viewModelFactory }
    private lateinit var packagesAdapter: CreditPackagesAdapter
    
    // Initialize PaymentSheet lazily in onCreate to avoid lifecycle issues
    private val paymentSheet: PaymentSheet by lazy {
        PaymentSheet(requireActivity()) { result -> handlePaymentResult(result) }
    }
    
    companion object {
        private const val PAYSTACK_PUBLIC_KEY = "pk_test_your_paystack_public_key_here"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize dependency injection
        (requireActivity().application as SmsBulkerApplication).appComponent.inject(this)
        
        // Initialize Paystack
        Paystack.builder()
            .setPublicKey(PAYSTACK_PUBLIC_KEY)
            .setLoggingEnabled(true)
            .build()
            
        // Initialize PaymentSheet early in lifecycle
        paymentSheet
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPaymentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        setupClickListeners()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        packagesAdapter = CreditPackagesAdapter { creditPackage ->
            viewModel.selectPackage(creditPackage)
        }
        
        binding.recyclerViewPackages.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = packagesAdapter
        }
    }

    private fun setupClickListeners() {
        binding.buttonCalculateCustom.setOnClickListener {
            val credits = binding.editTextCustomCredits.text.toString()
            viewModel.setCustomCredits(credits)
        }
        
        binding.buttonProceedPayment.setOnClickListener {
            proceedWithPayment()
        }
        
        binding.buttonClearSelection.setOnClickListener {
            clearSelection()
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.packages.collect { packages ->
                packagesAdapter.submitList(packages)
            }
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                updateUI(state)
            }
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.calculation.collect { calculation ->
                updateCalculationDisplay(calculation)
            }
        }
    }

    private fun updateUI(state: PaymentUiState) {
        binding.progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE
        binding.progressBarPayment.visibility = if (state.isProcessingPayment || state.isVerifyingPayment) View.VISIBLE else View.GONE
        
        binding.buttonProceedPayment.isEnabled = !state.isProcessingPayment && !state.isVerifyingPayment
        
        state.error?.let { error ->
            Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
        
        if (state.paymentSuccess) {
            state.successMessage?.let { message ->
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
            }
            // Navigate back or refresh UI
            viewModel.clearPaymentResponse()
        }
        
        state.paymentResponse?.let { response ->
            if (response.success && response.paystackReference != null) {
                // Payment initiated, now verify
                viewModel.verifyPayment(response.paystackReference)
            }
        }
    }

    private fun updateCalculationDisplay(calculation: com.gscube.smsbulker.data.model.CreditCalculation?) {
        if (calculation != null) {
            binding.layoutCalculationResult.visibility = View.VISIBLE
            binding.textViewCredits.text = "Credits: ${calculation.customCredits}"
            binding.textViewBonusCredits.text = "Bonus Credits: ${calculation.bonusCredits}"
            binding.textViewTotalCredits.text = "Total Credits: ${calculation.totalCredits}"
            binding.textViewAmount.text = "Amount: GH¢${String.format("%.2f", calculation.totalAmount)}"
            binding.textViewPricePerCredit.text = "Price per Credit: GH¢${String.format("%.2f", calculation.pricePerCredit)}"
            
            if (calculation.selectedPackage != null) {
                binding.textViewSelectedPackage.text = "Selected: ${calculation.selectedPackage.name}"
                binding.textViewSelectedPackage.visibility = View.VISIBLE
            } else {
                binding.textViewSelectedPackage.visibility = View.GONE
            }
            
            binding.buttonProceedPayment.visibility = View.VISIBLE
        } else {
            binding.layoutCalculationResult.visibility = View.GONE
            binding.buttonProceedPayment.visibility = View.GONE
        }
    }

    private fun proceedWithPayment() {
        val calculation = viewModel.calculation.value ?: return
        
        // Make an API call to your backend to initialize the transaction and get an access_code
        viewModel.initiatePayment("user@example.com", "user123").observe(viewLifecycleOwner) { response ->
            if (response != null && response.success && response.paystackReference != null) {
                // Launch the Paystack payment sheet with the access code
                paymentSheet.launch(response.paystackReference)
            } else {
                Toast.makeText(requireContext(), "Failed to initiate payment", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun handlePaymentResult(paymentSheetResult: PaymentSheetResult) {
        when (paymentSheetResult) {
            is PaymentSheetResult.Completed -> {
                // Payment successful, verify on backend
                viewModel.verifyPayment(paymentSheetResult.paymentCompletionDetails.reference)
            }
            is PaymentSheetResult.Failed -> {
                Toast.makeText(requireContext(), "Payment failed: ${paymentSheetResult.error.message}", Toast.LENGTH_LONG).show()
            }
            is PaymentSheetResult.Cancelled -> {
                Toast.makeText(requireContext(), "Payment cancelled", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun clearSelection() {
        binding.editTextCustomCredits.text?.clear()
        viewModel.clearPaymentResponse()
        packagesAdapter.clearSelection()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}