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
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetPaymentSummaryBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupClickListeners()
        
        // Initialize TextViews with placeholder values to ensure they're properly laid out
        binding.textViewCredits.text = "0"
        binding.textViewBonusCredits.text = "0"
        binding.textViewTotalCredits.text = "0"
        binding.textViewAmount.text = "GH¢0.00"
        binding.textViewPricePerCredit.text = "Price per credit: GH¢0.00"
        binding.textViewSelectedPackage.text = "No package selected"
        binding.textViewSelectedPackage.visibility = View.GONE
        
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
        if (calculation != null && _binding != null) {
            // Log the calculation values for debugging
            android.util.Log.d(TAG, "Updating calculation display: " + 
                "customCredits=${calculation.customCredits}, " +
                "bonusCredits=${calculation.bonusCredits}, " +
                "totalCredits=${calculation.totalCredits}, " +
                "totalAmount=${calculation.totalAmount}, " +
                "pricePerCredit=${calculation.pricePerCredit}, " +
                "selectedPackage=${calculation.selectedPackage?.name}")
            
            // Use post to ensure UI thread updates after the view is fully laid out
            binding.root.post {
                try {
                    // Ensure we're still attached to the window
                    if (!isAdded || _binding == null) {
                        android.util.Log.w(TAG, "Fragment not attached or binding null during update")
                        return@post
                    }
                    
                    // Update base credits with null check
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
        } else {
            android.util.Log.d(TAG, "Cannot update calculation display: calculation=$calculation, _binding=$_binding")
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