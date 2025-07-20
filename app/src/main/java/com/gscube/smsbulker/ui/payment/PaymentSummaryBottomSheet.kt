package com.gscube.smsbulker.ui.payment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.gscube.smsbulker.R
import com.gscube.smsbulker.data.model.CreditCalculation
import com.gscube.smsbulker.databinding.BottomSheetPaymentSummaryBinding

class PaymentSummaryBottomSheet : BottomSheetDialogFragment() {
    
    override fun getTheme(): Int = R.style.BottomSheetDialogTheme

    private var _binding: BottomSheetPaymentSummaryBinding? = null
    private val binding get() = _binding!!
    
    private var onProceedClickListener: (() -> Unit)? = null
    private var onCancelClickListener: (() -> Unit)? = null
    private var pendingCalculation: CreditCalculation? = null
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetPaymentSummaryBinding.inflate(inflater, container, false)
        android.util.Log.d(TAG, "onCreateView: binding initialized")
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        android.util.Log.d(TAG, "onViewCreated called")
        
        setupClickListeners()
        
        // Initialize TextViews with placeholder values to ensure they're properly laid out
        binding.textViewCredits.text = "0"
        binding.textViewBonusCredits.text = "0"
        binding.textViewTotalCredits.text = "0"
        binding.textViewAmount.text = "GH¢0.00"
        binding.textViewPricePerCredit.text = "Price per credit: GH¢0.00"
        binding.textViewSelectedPackage.text = "No package selected"
        binding.textViewSelectedPackage.visibility = View.GONE
        
        // Apply any pending calculation with a delay to ensure view is fully laid out
        val calculation = pendingCalculation
        if (calculation != null) {
            android.util.Log.d(TAG, "Applying pending calculation in onViewCreated")
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (isAdded && _binding != null) {
                    android.util.Log.d(TAG, "Delayed update of calculation in onViewCreated")
                    updateCalculationDisplay(calculation)
                    pendingCalculation = null
                }
            }, 200) // Short delay to ensure view is fully laid out
        } else {
            android.util.Log.d(TAG, "No pending calculation to apply in onViewCreated")
        }
        
        // Ensure the bottom sheet is fully expanded when shown
        dialog?.setOnShowListener { dialogInterface ->
            val bottomSheetDialog = dialogInterface as com.google.android.material.bottomsheet.BottomSheetDialog
            val bottomSheet = bottomSheetDialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let {
                val behavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(it)
                behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
            }
        }
    }
    
    private fun setupClickListeners() {
        binding.buttonProceedPayment.setOnClickListener {
            onProceedClickListener?.invoke()
            dismiss()
        }
        
        binding.buttonClearSelection.setOnClickListener {
            onCancelClickListener?.invoke()
            dismiss()
        }
    }
    
    fun updateCalculationDisplay(calculation: CreditCalculation?) {
        if (calculation == null) {
            android.util.Log.d(TAG, "Cannot update calculation display: calculation is null")
            return
        }
        
        // Log the calculation values for debugging
        android.util.Log.d(TAG, "Updating calculation display: " + 
            "customCredits=${calculation.customCredits}, " +
            "bonusCredits=${calculation.bonusCredits}, " +
            "totalCredits=${calculation.totalCredits}, " +
            "totalAmount=${calculation.totalAmount}, " +
            "pricePerCredit=${calculation.pricePerCredit}, " +
            "selectedPackage=${calculation.selectedPackage?.name}")
        
        android.util.Log.d(TAG, "Binding status: _binding=${_binding != null}, isAdded=$isAdded, isVisible=$isVisible")
        
        if (_binding == null || !isAdded) {
            // Store calculation for later when view is ready
            android.util.Log.d(TAG, "View not ready, storing calculation for later")
            pendingCalculation = calculation
            return
        }
        
        android.util.Log.d(TAG, "View is ready, updating UI directly")
        
        // Force update on main thread
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            try {
                // Double-check we're still ready
                if (!isAdded || _binding == null || !isVisible) {
                    android.util.Log.w(TAG, "Fragment not ready for update: isAdded=$isAdded, binding=${_binding != null}, isVisible=$isVisible")
                    pendingCalculation = calculation
                    return@post
                }
                    
                    // Update base credits with null check
                    try {
                        binding.textViewCredits.text = calculation.customCredits.toString()
                        android.util.Log.d(TAG, "Set textViewCredits to: ${calculation.customCredits}")
                        
                        // Update bonus credits with null check
                        binding.textViewBonusCredits.text = calculation.bonusCredits.toString()
                        android.util.Log.d(TAG, "Set textViewBonusCredits to: ${calculation.bonusCredits}")
                        
                        // Update total credits with null check
                        binding.textViewTotalCredits.text = calculation.totalCredits.toString()
                        android.util.Log.d(TAG, "Set textViewTotalCredits to: ${calculation.totalCredits}")
                        
                        // Update total amount with null check
                        val formattedAmount = String.format("GH¢%.2f", calculation.totalAmount)
                        binding.textViewAmount.text = formattedAmount
                        android.util.Log.d(TAG, "Set textViewAmount to: $formattedAmount")
                        
                        // Update price per credit with null check
                        val formattedPrice = String.format("Price per credit: GH¢%.4f", calculation.pricePerCredit)
                        binding.textViewPricePerCredit.text = formattedPrice
                        android.util.Log.d(TAG, "Set textViewPricePerCredit to: $formattedPrice")
                        
                        // Verify the text was actually set
                        android.util.Log.d(TAG, "Verification - textViewCredits: ${binding.textViewCredits.text}")
                        android.util.Log.d(TAG, "Verification - textViewBonusCredits: ${binding.textViewBonusCredits.text}")
                        android.util.Log.d(TAG, "Verification - textViewTotalCredits: ${binding.textViewTotalCredits.text}")
                        android.util.Log.d(TAG, "Verification - textViewAmount: ${binding.textViewAmount.text}")
                    } catch (e: Exception) {
                        android.util.Log.e(TAG, "Exception while updating UI", e)
                    }
                    
                    // Update selected package information
                    if (calculation.selectedPackage != null) {
                        val packageInfo = "Selected: ${calculation.selectedPackage.name} - ${calculation.selectedPackage.credits} credits for GH¢${calculation.selectedPackage.price}"
                        binding.textViewSelectedPackage.text = packageInfo
                        binding.textViewSelectedPackage.visibility = View.VISIBLE
                        android.util.Log.d(TAG, "Set textViewSelectedPackage to: $packageInfo")
                    } else {
                        binding.textViewSelectedPackage.visibility = View.GONE
                        android.util.Log.d(TAG, "Hide textViewSelectedPackage")
                    }
                    
                    // Show proceed button
                    binding.buttonProceedPayment.visibility = View.VISIBLE
                    
                    // Force layout refresh with multiple approaches
                    binding.root.invalidate()
                    binding.root.requestLayout()
                    
                    // Force redraw of each TextView
                    binding.textViewCredits.invalidate()
                    binding.textViewBonusCredits.invalidate()
                    binding.textViewTotalCredits.invalidate()
                    binding.textViewAmount.invalidate()
                    binding.textViewPricePerCredit.invalidate()
                    binding.textViewSelectedPackage.invalidate()
                    
                    // Log final state after update
                    android.util.Log.d(TAG, "Final UI state after update: " +
                        "Credits=${binding.textViewCredits.text}, " +
                        "Bonus=${binding.textViewBonusCredits.text}, " +
                        "Total=${binding.textViewTotalCredits.text}, " +
                        "Amount=${binding.textViewAmount.text}")
                    
                    // Ensure all views are properly measured and laid out
                    binding.root.measure(
                        View.MeasureSpec.makeMeasureSpec(binding.root.width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(binding.root.height, View.MeasureSpec.EXACTLY)
                    )
                    binding.root.layout(binding.root.left, binding.root.top, binding.root.right, binding.root.bottom)
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Error updating calculation display", e)
                }
            }
    }
    
    fun showProgressOverlay(show: Boolean) {
        if (_binding != null) {
            binding.progressOverlay.visibility = if (show) View.VISIBLE else View.GONE
        }
    }
    
    fun setOnProceedClickListener(listener: () -> Unit) {
        onProceedClickListener = listener
    }
    
    fun setOnCancelClickListener(listener: () -> Unit) {
        onCancelClickListener = listener
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    companion object {
        const val TAG = "PaymentSummaryBottomSheet"
        
        fun newInstance(): PaymentSummaryBottomSheet {
            return PaymentSummaryBottomSheet()
        }
    }
}