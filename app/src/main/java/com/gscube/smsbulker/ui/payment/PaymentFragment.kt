package com.gscube.smsbulker.ui.payment

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.gscube.smsbulker.MainActivity
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
    
    private lateinit var paymentSummaryBottomSheet: PaymentSummaryBottomSheet
    
    private val viewModel: PaymentViewModel by viewModels { viewModelFactory }
    private lateinit var packagesAdapter: CreditPackagesAdapter
    
    // Declare PaymentSheet as lateinit instead of lazy to initialize it at the right lifecycle moment
    private lateinit var paymentSheet: PaymentSheet
    
    // Add the missing paymentProcessingDialog property
    private var paymentProcessingDialog: AlertDialog? = null
    
    companion object {
        // TODO: Replace with your actual Paystack test public key from your Paystack dashboard
        // The current placeholder value is causing the "Invalid key" error
        private const val PAYSTACK_PUBLIC_KEY = "pk_test_a912b314e9f7bb48d01e62eba3194bf04d24d062"
    }

    override fun onAttach(context: android.content.Context) {
        super.onAttach(context)
        // Initialize dependency injection
        (requireActivity().application as SmsBulkerApplication).appComponent.inject(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Initialize Paystack
        Paystack.builder()
            .setPublicKey(PAYSTACK_PUBLIC_KEY)
            .setLoggingEnabled(true)
            .build()
        // Initialize PaymentSheet as early as possible
        if (!::paymentSheet.isInitialized) {
            paymentSheet = PaymentSheet(this) { result -> handlePaymentResult(result) }
        }
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
        hideOldCalculationViews() // Hide old calculation views from the start
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
        // Set up tab selection listener
        binding.tabLayoutCalculationMode.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> { // Credits to Price mode
                        binding.textInputLayoutCredits.visibility = View.VISIBLE
                        binding.textInputLayoutPrice.visibility = View.GONE
                        binding.editTextCustomPrice.text?.clear()
                    }
                    1 -> { // Price to Credits mode
                        binding.textInputLayoutCredits.visibility = View.GONE
                        binding.textInputLayoutPrice.visibility = View.VISIBLE
                        binding.editTextCustomCredits.text?.clear()
                    }
                }
                // Clear any previous calculation
                viewModel.clearPaymentResponse()
            }

            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}

            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
        })
        
        binding.buttonCalculateCustom.setOnClickListener {
            val selectedTabPosition = binding.tabLayoutCalculationMode.selectedTabPosition
            
            if (selectedTabPosition == 0) { // Credits to Price mode
                val credits = binding.editTextCustomCredits.text.toString()
                viewModel.setCustomCredits(credits)
            } else { // Price to Credits mode
                val price = binding.editTextCustomPrice.text.toString()
                viewModel.setCustomPrice(price)
            }
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
        
        // Update progress overlay in bottom sheet if it's initialized and showing
        if (::paymentSummaryBottomSheet.isInitialized && paymentSummaryBottomSheet.isAdded) {
            paymentSummaryBottomSheet.showProgressOverlay(state.isProcessingPayment || state.isVerifyingPayment)
        } else {
            // Fallback to the main fragment progress indicator if bottom sheet isn't showing
            binding.progressBarPayment.visibility = if (state.isProcessingPayment || state.isVerifyingPayment) View.VISIBLE else View.GONE
        }
        
        binding.buttonProceedPayment.isEnabled = !state.isProcessingPayment && !state.isVerifyingPayment
        
        state.error?.let { error ->
            Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
        
        if (state.paymentSuccess) {
            state.successMessage?.let { message ->
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
            }
            // Refresh credit balance in HomeViewModel
            (requireActivity() as? MainActivity)?.refreshCreditBalance()
            // Navigate back to AccountFragment
            requireActivity().onBackPressed()
            // Clear payment response and dismiss dialog
            viewModel.clearPaymentResponse()
            dismissPaymentProcessingDialog()
        }
        
        // We don't need to verify payment here as it will be handled by handlePaymentResult
        // after the payment sheet is completed
        state.paymentResponse?.let { response ->
            // Just log the payment response status
            Log.d("PaymentFragment", "Payment response received: ${response.success}")
        }
    }

    private fun hideOldCalculationViews() {
        // Hide all the old calculation display views that are still in the fragment layout
        binding.textViewSelectedPackage.visibility = View.GONE
        binding.textViewCredits.text = "0"
        binding.textViewBonusCredits.text = "0"
        binding.textViewTotalCredits.text = "0"
        binding.textViewAmount.text = "GH¢0.00"
        binding.textViewPricePerCredit.text = ""
        binding.buttonProceedPayment.visibility = View.GONE
    }

    private fun updateCalculationDisplay(calculation: com.gscube.smsbulker.data.model.CreditCalculation?) {
        if (calculation != null) {
            android.util.Log.d("PaymentFragment", "Updating calculation display with: $calculation")
            
            // Initialize the bottom sheet if not already initialized
            if (!::paymentSummaryBottomSheet.isInitialized) {
                paymentSummaryBottomSheet = PaymentSummaryBottomSheet.newInstance()
                paymentSummaryBottomSheet.setOnProceedClickListener {
                    proceedWithPayment()
                }
                paymentSummaryBottomSheet.setOnCancelClickListener {
                    clearSelection()
                }
            }
            
            // Show the bottom sheet if not already showing
            if (!paymentSummaryBottomSheet.isAdded) {
                paymentSummaryBottomSheet.show(parentFragmentManager, PaymentSummaryBottomSheet.TAG)
                // Update after showing to ensure views are properly initialized
                paymentSummaryBottomSheet.view?.post {
                    paymentSummaryBottomSheet.updateCalculationDisplay(calculation)
                }
            } else {
                // Update immediately if already showing
                paymentSummaryBottomSheet.updateCalculationDisplay(calculation)
            }
            
            // IMPORTANT: Hide the old calculation display views in the fragment
            // to prevent conflicts with the bottom sheet
            hideOldCalculationViews()
        } else {
            // Dismiss the bottom sheet if it's showing
            if (::paymentSummaryBottomSheet.isInitialized && paymentSummaryBottomSheet.isAdded) {
                paymentSummaryBottomSheet.dismiss()
            }
            
            hideOldCalculationViews()
        }
    }

    private fun proceedWithPayment() {
        val calculation = viewModel.calculation.value ?: return
        
        // Show payment processing dialog
        showPaymentProcessingDialog()
        
        // Show progress overlay in bottom sheet if it's showing
        if (::paymentSummaryBottomSheet.isInitialized && paymentSummaryBottomSheet.isAdded) {
            paymentSummaryBottomSheet.showProgressOverlay(true)
        }
        
        // Get the current user's email and ID from the authentication system
        val currentUser = FirebaseAuth.getInstance().currentUser
        val userEmail = currentUser?.email ?: "user@example.com"
        val userId = currentUser?.uid ?: "user123"
        
        // Make an API call to your backend to initialize the transaction and get an access_code
        viewModel.initiatePayment(userEmail, userId).observe(viewLifecycleOwner) { response ->
            // Hide progress overlay if payment initialization is complete
            if (::paymentSummaryBottomSheet.isInitialized && paymentSummaryBottomSheet.isAdded) {
                paymentSummaryBottomSheet.showProgressOverlay(false)
            }
            
            if (response != null && response.success && response.paystackReference != null) {
                // Launch the Paystack payment sheet with the access code
                dismissPaymentProcessingDialog()
                paymentSheet.launch(response.paystackReference)
            } else {
                dismissPaymentProcessingDialog()
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
    
    // Add the missing clearSelection method
    private fun clearSelection() {
        binding.editTextCustomCredits.text?.clear()
        binding.editTextCustomPrice.text?.clear()
        binding.tabLayoutCalculationMode.getTabAt(0)?.select() // Reset to Credits to Price mode
        binding.textInputLayoutCredits.visibility = View.VISIBLE
        binding.textInputLayoutPrice.visibility = View.GONE
        viewModel.clearPaymentResponse()
        packagesAdapter.clearSelection()
    }
    
    private fun showPaymentProcessingDialog() {
        if (paymentProcessingDialog == null) {
            val dialogView = LayoutInflater.from(requireContext())
                .inflate(com.gscube.smsbulker.R.layout.dialog_payment_processing, null)
            
            paymentProcessingDialog = AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .setCancelable(false)
                .create()
        }
        
        paymentProcessingDialog?.show()
    }
    
    private fun dismissPaymentProcessingDialog() {
        paymentProcessingDialog?.dismiss()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        dismissPaymentProcessingDialog()
        _binding = null
    }
}