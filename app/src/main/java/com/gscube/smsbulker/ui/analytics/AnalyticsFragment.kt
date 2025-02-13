package com.gscube.smsbulker.ui.analytics

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.material.snackbar.Snackbar
import com.gscube.smsbulker.R
import com.gscube.smsbulker.SmsBulkerApplication
import com.gscube.smsbulker.data.DailyStats
import com.gscube.smsbulker.databinding.FragmentAnalyticsBinding
import com.gscube.smsbulker.di.ViewModelFactory
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

class AnalyticsFragment : Fragment() {
    private var _binding: FragmentAnalyticsBinding? = null
    private val binding get() = _binding!!
    
    @Inject
    lateinit var viewModelFactory: ViewModelFactory
    
    private lateinit var viewModel: AnalyticsViewModel
    private val failureAdapter = FailureAnalysisAdapter()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (requireActivity().application as SmsBulkerApplication).appComponent.inject(this)
        viewModel = ViewModelProvider(this, viewModelFactory)[AnalyticsViewModel::class.java]
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAnalyticsBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupChart()
        setupChipGroup()
        setupExportButton()
        observeViewModel()
        
        // Load initial data
        viewModel.loadAnalytics("daily")
    }
    
    private fun setupRecyclerView() {
        binding.failureRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = failureAdapter
        }
    }
    
    private fun setupChart() {
        binding.messageChart.apply {
            description.isEnabled = false
            legend.isEnabled = true
            setTouchEnabled(true)
            setScaleEnabled(true)
            setPinchZoom(true)
            
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                granularity = 1f
            }
            
            axisLeft.apply {
                setDrawGridLines(true)
                axisMinimum = 0f
            }
            
            axisRight.isEnabled = false
        }
    }
    
    private fun setupChipGroup() {
        binding.periodChipGroup.setOnCheckedChangeListener { group, checkedId ->
            val period = when (checkedId) {
                R.id.chipDaily -> "daily"
                R.id.chipWeekly -> "weekly"
                R.id.chipMonthly -> "monthly"
                else -> "daily"
            }
            viewModel.loadAnalytics(period)
        }
    }
    
    private fun setupExportButton() {
        binding.exportFab.setOnClickListener {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                // Check for storage permission on Android 9 (P) and below
                if (ContextCompat.checkSelfPermission(
                        requireContext(),
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    requestPermissions(
                        arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE),
                        STORAGE_PERMISSION_REQUEST
                    )
                    return@setOnClickListener
                }
            }
            
            // Trigger export
            viewModel.exportAnalyticsData()
        }
    }
    
    private fun observeViewModel() {
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.isVisible = isLoading
        }
        
        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let {
                Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG)
                    .setAction("Retry") { viewModel.loadAnalytics("daily") }
                    .show()
                viewModel.clearError()
            }
        }
        
        viewModel.analyticsSummary.observe(viewLifecycleOwner) { summary ->
            binding.apply {
                totalMessagesValue.text = summary.totalMessages.toString()
                deliveredMessagesValue.text = summary.deliveredMessages.toString()
                failedMessagesValue.text = summary.failedMessages.toString()
                successRateValue.text = String.format("%.1f%%", summary.successRate * 100)
                creditsUsedValue.text = summary.creditsUsed.toString()
                creditsRemainingValue.text = summary.creditsRemaining.toString()
            }
        }
        
        viewModel.dailyStats.observe(viewLifecycleOwner) { stats ->
            updateChart(stats)
        }
        
        viewModel.failureAnalysis.observe(viewLifecycleOwner) { failures ->
            failureAdapter.submitList(failures)
        }
        
        viewModel.exportedFile.observe(viewLifecycleOwner) { filePath ->
            filePath?.let {
                val file = File(it)
                if (file.exists()) {
                    shareAnalyticsFile(file)
                }
            }
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            STORAGE_PERMISSION_REQUEST -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission granted, try export again
                    viewModel.exportAnalyticsData()
                } else {
                    Snackbar.make(
                        binding.root,
                        "Storage permission required to export analytics",
                        Snackbar.LENGTH_LONG
                    ).show()
                }
            }
            else -> super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }
    
    private fun updateChart(stats: List<DailyStats>) {
        val entries = stats.mapIndexed { index, stat ->
            Entry(index.toFloat(), stat.totalMessages.toFloat())
        }
        
        val dataSet = LineDataSet(entries, "Messages Sent").apply {
            color = Color.BLUE
            setCircleColor(Color.BLUE)
            valueTextSize = 12f
            lineWidth = 2f
        }
        
        binding.messageChart.apply {
            data = LineData(dataSet)
            xAxis.apply {
                valueFormatter = object : ValueFormatter() {
                    private val dateFormat = SimpleDateFormat("MM/dd", Locale.getDefault())
                    
                    override fun getFormattedValue(value: Float): String {
                        val position = value.toInt()
                        return if (position < stats.size) {
                            dateFormat.format(Date(stats[position].timestamp))
                        } else ""
                    }
                }
            }
            
            invalidate()
        }
    }
    
    private fun shareAnalyticsFile(file: File) {
        Snackbar.make(binding.root, "Analytics exported to Downloads", Snackbar.LENGTH_LONG)
            .setAction("Open") {
                // Open the file using the default CSV viewer
                val intent = Intent(Intent.ACTION_VIEW)
                val uri = FileProvider.getUriForFile(
                    requireContext(),
                    "${requireContext().packageName}.provider",
                    file
                )
                intent.setDataAndType(uri, "text/csv")
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                try {
                    startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    Snackbar.make(binding.root, "No app found to open CSV files", Snackbar.LENGTH_SHORT).show()
                }
            }
            .show()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    companion object {
        private const val STORAGE_PERMISSION_REQUEST = 100
    }
}