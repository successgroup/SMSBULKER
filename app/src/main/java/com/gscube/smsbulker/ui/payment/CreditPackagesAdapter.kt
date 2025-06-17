package com.gscube.smsbulker.ui.payment

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.gscube.smsbulker.R
import com.gscube.smsbulker.data.model.CreditPackage
import com.gscube.smsbulker.databinding.ItemCreditPackageBinding

class CreditPackagesAdapter(
    private val onPackageSelected: (CreditPackage) -> Unit
) : RecyclerView.Adapter<CreditPackagesAdapter.PackageViewHolder>() {

    private var packages: List<CreditPackage> = emptyList()
    private var selectedPosition: Int = RecyclerView.NO_POSITION

    fun submitList(packages: List<CreditPackage>) {
        this.packages = packages
        notifyDataSetChanged()
    }

    fun clearSelection() {
        val oldSelectedPosition = selectedPosition
        selectedPosition = RecyclerView.NO_POSITION
        if (oldSelectedPosition != RecyclerView.NO_POSITION) {
            notifyItemChanged(oldSelectedPosition)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PackageViewHolder {
        val binding = ItemCreditPackageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PackageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PackageViewHolder, position: Int) {
        holder.bind(packages[position], position == selectedPosition)
    }

    override fun getItemCount(): Int = packages.size

    inner class PackageViewHolder(private val binding: ItemCreditPackageBinding) : 
        RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val oldSelectedPosition = selectedPosition
                    selectedPosition = position
                    
                    if (oldSelectedPosition != RecyclerView.NO_POSITION) {
                        notifyItemChanged(oldSelectedPosition)
                    }
                    notifyItemChanged(selectedPosition)
                    
                    onPackageSelected(packages[position])
                }
            }
        }

        fun bind(creditPackage: CreditPackage, isSelected: Boolean) {
            binding.apply {
                textViewPackageName.text = creditPackage.name
                textViewCredits.text = creditPackage.credits.toString()
                textViewPrice.text = String.format("%.2f %s", creditPackage.price, creditPackage.currency)
                textViewDescription.text = creditPackage.description
                
                // Show bonus if available
                if (creditPackage.bonusCredits > 0) {
                    textViewBonusCredits.isVisible = true
                    textViewBonusCredits.text = String.format("+%d Bonus", creditPackage.bonusCredits)
                } else {
                    textViewBonusCredits.isVisible = false
                }
                
                // Show discount if available
                if (creditPackage.discountPercentage > 0) {
                    textViewDiscount.isVisible = true
                    textViewDiscount.text = String.format("%d%% OFF", creditPackage.discountPercentage)
                } else {
                    textViewDiscount.isVisible = false
                }
                
                // Show popular badge if marked as popular
                badgePopular.isVisible = creditPackage.isPopular
                
                // Update selected state
                cardViewPackage.strokeWidth = if (isSelected) {
                    cardViewPackage.context.resources.getDimensionPixelSize(R.dimen.card_selected_stroke_width)
                } else {
                    cardViewPackage.context.resources.getDimensionPixelSize(R.dimen.card_normal_stroke_width)
                }
                
                cardViewPackage.strokeColor = if (isSelected) {
                    cardViewPackage.context.getColor(R.color.sea_blue_500)
                } else {
                    cardViewPackage.context.getColor(R.color.design_default_color_outline)
                }
            }
        }
    }
}