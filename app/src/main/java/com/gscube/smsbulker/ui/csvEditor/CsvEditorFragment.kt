package com.gscube.smsbulker.ui.csvEditor

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.snackbar.Snackbar
import com.gscube.smsbulker.R
import com.gscube.smsbulker.SmsBulkerApplication
import com.gscube.smsbulker.databinding.FragmentCsvEditorBinding
import com.gscube.smsbulker.di.ViewModelFactory
import com.evrencoskun.tableview.TableView
import com.gscube.smsbulker.ui.csvEditor.CsvEditorViewModel.SaveResult
import kotlinx.coroutines.launch
import javax.inject.Inject

class CsvEditorFragment : Fragment() {

    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    private val viewModel: CsvEditorViewModel by viewModels { viewModelFactory }
    private val args: CsvEditorFragmentArgs by navArgs()
    private var _binding: FragmentCsvEditorBinding? = null
    private val binding get() = _binding!!
    private lateinit var tableAdapter: TableAdapter
    private lateinit var smartTable: TableView

    override fun onCreate(savedInstanceState: Bundle?) {
        (requireActivity().application as SmsBulkerApplication).appComponent.inject(this)
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCsvEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupTableView()
        setupObservers()
        setupButtons()
        loadCsvFile()
    }

    private fun setupTableView() {
        smartTable = binding.tableView
        
        // Configure table
        smartTable.apply {
            setHasFixedWidth(false)
            setBackgroundColor(Color.BLACK)
            setSeparatorColor(Color.BLUE)
        }
        
        tableAdapter = TableAdapter(requireContext()) { rowIndex, columnIndex, value ->
            viewModel.updateCell(rowIndex, columnIndex, value)
        }
        tableAdapter.attachToTable(smartTable)
    }

    private fun setupObservers() {
        viewModel.csvData.observe(viewLifecycleOwner) { data ->
            data?.let {
                tableAdapter.updateData(it)
                binding.fileNameText.text = it.fileName
            }
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let {
                Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun setupButtons() {
        binding.apply {
            addRowButton.setOnClickListener {
                viewModel.addRow()
            }

            cancelButton.setOnClickListener {
                findNavController().navigateUp()
            }

            saveButton.setOnClickListener {
                viewModel.saveCsv().observe(viewLifecycleOwner) { result ->
                    when (result) {
                        is CsvEditorViewModel.SaveResult.Success -> {
                            Snackbar.make(binding.root, "CSV saved successfully", Snackbar.LENGTH_SHORT).show()
                            findNavController().navigateUp()
                        }
                        is CsvEditorViewModel.SaveResult.Error -> {
                            Snackbar.make(binding.root, "Failed to save CSV: ${result.message}", Snackbar.LENGTH_LONG).show()
                        }
                    }
                }
            }

            editModeSwitch.setOnCheckedChangeListener { _, isChecked ->
                tableAdapter.toggleEditMode(isChecked)
            }
        }
    }

    private fun loadCsvFile() {
        viewModel.loadCsv(args.csvUri)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
