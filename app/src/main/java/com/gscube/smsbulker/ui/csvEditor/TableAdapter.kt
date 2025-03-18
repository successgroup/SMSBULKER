package com.gscube.smsbulker.ui.csvEditor

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.evrencoskun.tableview.TableView
import com.evrencoskun.tableview.adapter.AbstractTableAdapter
import com.evrencoskun.tableview.adapter.recyclerview.holder.AbstractViewHolder
import com.evrencoskun.tableview.listener.ITableViewListener
import com.evrencoskun.tableview.sort.SortState

class TableAdapter(
    private val context: Context,
    private val onCellValueChanged: (Int, Int, String) -> Unit
) : AbstractTableAdapter<String?, String?, String?>(), ITableViewListener {
    private var csvData: CsvData? = null
    private var editMode = false
    private var table: TableView? = null

    fun attachToTable(tableView: TableView) {
        table = tableView
        tableView.setAdapter(this)
        setupTableStyle()
        tableView.tableViewListener = this
    }

    private fun setupTableStyle() {
        table?.apply {
            setHasFixedWidth(false)
            setBackgroundColor(Color.BLACK)
            setSeparatorColor(Color.BLUE)
            
            // Enable selection
            setIgnoreSelectionColors(false)
            
            // Enable column width auto calculation
            setColumnWidth(0, ViewGroup.LayoutParams.WRAP_CONTENT)
            
            // Enable row height auto calculation
            setRowHeaderWidth(ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    fun updateData(data: CsvData) {
        csvData = data
        // Post to main thread to avoid layout/scroll conflicts
        Handler(Looper.getMainLooper()).post {
            setAllItems(
                data.headers.map { it as String? }.toMutableList(), // Column Headers
                mutableListOf(), // Row Headers (empty)
                data.rows.map { row -> row.map { it as String? }.toMutableList() }.toMutableList() // Cell Items
            )
        }
    }

    fun toggleEditMode(enabled: Boolean) {
        editMode = enabled
        // Post to main thread to avoid layout/scroll conflicts
        Handler(Looper.getMainLooper()).post {
            notifyDataSetChanged()
        }
    }

    override fun onCreateCellViewHolder(parent: ViewGroup, viewType: Int): AbstractViewHolder {
        val editText = EditText(context).apply {
            setPadding(20, 10, 20, 10)
            textSize = 14f
            setTextColor(Color.WHITE)
            background = null
            isSingleLine = true
            isEnabled = false // Start disabled, enable only in edit mode
            isFocusableInTouchMode = true
        }
        return CellViewHolder(editText)
    }

    override fun onBindCellViewHolder(holder: AbstractViewHolder, cellItemModel: String?, columnPosition: Int, rowPosition: Int) {
        val editText = holder.itemView as EditText
        editText.setText(cellItemModel ?: "")
        editText.isEnabled = editMode
        
        if (editMode) {
            editText.setOnFocusChangeListener { v, hasFocus ->
                if (!hasFocus) {
                    val newValue = (v as EditText).text.toString()
                    if (newValue != cellItemModel) {
                        onCellValueChanged(rowPosition, columnPosition, newValue)
                    }
                }
            }
        } else {
            editText.setOnFocusChangeListener(null)
        }
    }

    override fun onCreateColumnHeaderViewHolder(parent: ViewGroup, viewType: Int): AbstractViewHolder {
        val textView = TextView(context).apply {
            setPadding(20, 10, 20, 10)
            textSize = 14f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(50, 50, 50))
            isClickable = false // Disable clicking on headers
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        return ColumnHeaderViewHolder(textView)
    }

    override fun onCreateRowHeaderViewHolder(parent: ViewGroup, viewType: Int): AbstractViewHolder {
        return RowHeaderViewHolder(View(context))
    }

    override fun onBindColumnHeaderViewHolder(holder: AbstractViewHolder, columnHeaderItemModel: String?, columnPosition: Int) {
        val textView = holder.itemView as TextView
        textView.text = columnHeaderItemModel ?: ""
    }

    override fun onBindRowHeaderViewHolder(holder: AbstractViewHolder, rowHeaderItemModel: String?, rowPosition: Int) {
        // Row headers are not used
    }

    override fun onCreateCornerView(parent: ViewGroup): View {
        return View(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setBackgroundColor(Color.rgb(50, 50, 50))
        }
    }

    override fun onCellClicked(cellView: RecyclerView.ViewHolder, columnPosition: Int, rowPosition: Int) {
        if (editMode) {
            val editText = cellView.itemView as EditText
            editText.isEnabled = true
            editText.requestFocus()
        }
    }

    override fun onCellDoubleClicked(cellView: RecyclerView.ViewHolder, columnPosition: Int, rowPosition: Int) {}

    override fun onCellLongPressed(cellView: RecyclerView.ViewHolder, columnPosition: Int, rowPosition: Int) {}

    override fun onColumnHeaderClicked(columnHeaderView: RecyclerView.ViewHolder, columnPosition: Int) {
        // Do nothing - sorting disabled
    }

    override fun onColumnHeaderDoubleClicked(columnHeaderView: RecyclerView.ViewHolder, columnPosition: Int) {}

    override fun onColumnHeaderLongPressed(columnHeaderView: RecyclerView.ViewHolder, columnPosition: Int) {}

    override fun onRowHeaderClicked(rowHeaderView: RecyclerView.ViewHolder, rowPosition: Int) {}

    override fun onRowHeaderDoubleClicked(rowHeaderView: RecyclerView.ViewHolder, rowPosition: Int) {}

    override fun onRowHeaderLongPressed(rowHeaderView: RecyclerView.ViewHolder, rowPosition: Int) {}

    inner class CellViewHolder(view: View) : AbstractViewHolder(view)
    inner class ColumnHeaderViewHolder(view: View) : AbstractViewHolder(view)
    inner class RowHeaderViewHolder(view: View) : AbstractViewHolder(view)
}
