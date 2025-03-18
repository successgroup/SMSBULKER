package com.gscube.smsbulker.ui.csvEditor

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            handleSaveCsv()
        } else {
            showStoragePermissionError()
        }
    }

    private val manageStoragePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                handleSaveCsv()
            } else {
                showStoragePermissionError()
            }
        }
    }

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
                checkAndRequestStoragePermission()
            }

            editModeSwitch.setOnCheckedChangeListener { _, isChecked ->
                tableAdapter.toggleEditMode(isChecked)
            }
        }
    }

    private fun checkAndRequestStoragePermission() {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                if (Environment.isExternalStorageManager()) {
                    handleSaveCsv()
                } else {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:${requireContext().packageName}")
                    }
                    manageStoragePermissionLauncher.launch(intent)
                }
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                when {
                    ContextCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ) == PackageManager.PERMISSION_GRANTED -> {
                        handleSaveCsv()
                    }
                    shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE) -> {
                        showStoragePermissionRationale()
                    }
                    else -> {
                        requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    }
                }
            }
            else -> {
                handleSaveCsv()
            }
        }
    }

    private fun showStoragePermissionRationale() {
        Snackbar.make(
            binding.root,
            "Storage permission is required to save CSV files",
            Snackbar.LENGTH_INDEFINITE
        ).setAction("Grant") {
            requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }.show()
    }

    private fun showStoragePermissionError() {
        Snackbar.make(
            binding.root,
            "Storage permission denied. Cannot save CSV file.",
            Snackbar.LENGTH_LONG
        ).setAction("Settings") {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", requireContext().packageName, null)
            })
        }.show()
    }

    private fun handleSaveCsv() {
        viewModel.saveCsv().observe(viewLifecycleOwner) { result ->
            when (result) {
                is SaveResult.Success -> {
                    Snackbar.make(binding.root, "CSV saved successfully", Snackbar.LENGTH_SHORT).show()
                    findNavController().navigateUp()
                }
                is SaveResult.Error -> {
                    Snackbar.make(binding.root, "Failed to save CSV: ${result.message}", Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun loadCsvFile() {
        args.csvUri?.let { uri ->
            viewModel.loadCsv(uri)
        } ?: run {
            // No URI provided, start with empty table
            viewModel.createEmptyTable()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
