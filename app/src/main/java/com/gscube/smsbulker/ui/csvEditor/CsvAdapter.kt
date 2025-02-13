package com.gscube.smsbulker.ui.csvEditor

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gscube.smsbulker.databinding.ItemCsvRowBinding

class CsvAdapter : ListAdapter<List<String>, CsvAdapter.CsvViewHolder>(CsvDiffCallback()) {

    private var onCellValueChanged: ((Int, Int, String) -> Unit)? = null
    private var headers: List<String> = emptyList()

    fun setOnCellValueChanged(listener: (Int, Int, String) -> Unit) {
        onCellValueChanged = listener
    }

    fun setHeaders(headers: List<String>) {
        this.headers = headers
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CsvViewHolder {
        val binding = ItemCsvRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CsvViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CsvViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    inner class CsvViewHolder(
        private val binding: ItemCsvRowBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(row: List<String>, rowIndex: Int) {
            binding.csvRowContainer.removeAllViews()
            
            // Add header cells or data cells
            if (rowIndex == 0) {
                headers.forEach { header ->
                    val headerView = TextView(binding.root.context).apply {
                        text = header
                        setPadding(16, 8, 16, 8)
                    }
                    binding.csvRowContainer.addView(headerView)
                }
            } else {
                row.forEachIndexed { columnIndex, value ->
                    val editText = EditText(binding.root.context).apply {
                        setText(value)
                        setOnFocusChangeListener { _, hasFocus ->
                            if (!hasFocus) {
                                onCellValueChanged?.invoke(rowIndex - 1, columnIndex, text.toString())
                            }
                        }
                        setPadding(16, 8, 16, 8)
                    }
                    binding.csvRowContainer.addView(editText)
                }
            }
        }
    }
}

private class CsvDiffCallback : DiffUtil.ItemCallback<List<String>>() {
    override fun areItemsTheSame(oldItem: List<String>, newItem: List<String>): Boolean {
        return oldItem === newItem
    }

    override fun areContentsTheSame(oldItem: List<String>, newItem: List<String>): Boolean {
        return oldItem == newItem
    }
}
