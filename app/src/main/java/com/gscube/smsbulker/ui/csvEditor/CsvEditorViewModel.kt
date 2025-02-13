package com.gscube.smsbulker.ui.csvEditor

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gscube.smsbulker.di.AppScope
import com.gscube.smsbulker.repository.CsvRepository
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.launch
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class CsvEditorViewModel @Inject constructor(
    private val csvRepository: CsvRepository
) : ViewModel() {

    private val _csvData = MutableLiveData<CsvData>()
    val csvData: LiveData<CsvData> = _csvData

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private var currentUri: Uri? = null

    sealed class SaveResult {
        object Success : SaveResult()
        data class Error(val message: String) : SaveResult()
    }

    fun loadCsv(uri: Uri) {
        viewModelScope.launch {
            try {
                currentUri = uri
                val data = csvRepository.readCsvFile(uri)
                validateCsv(data)
                _csvData.value = data
            } catch (e: Exception) {
                _error.value = "Failed to load CSV: ${e.message}"
            }
        }
    }

    fun validateCsv(data: CsvData): Boolean {
        return try {
            viewModelScope.launch {
                csvRepository.validateCsvData(data)
            }
            true
        } catch (e: Exception) {
            _error.value = e.message
            false
        }
    }

    fun saveCsv(): LiveData<SaveResult> {
        val result = MutableLiveData<SaveResult>()
        viewModelScope.launch {
            try {
                val currentData = _csvData.value ?: throw Exception("No data to save")
                val uri = currentUri ?: throw Exception("No URI available")
                csvRepository.writeCsvFile(uri, currentData)
                result.value = SaveResult.Success
            } catch (e: Exception) {
                result.value = SaveResult.Error(e.message ?: "Unknown error occurred")
            }
        }
        return result
    }

    fun updateCell(rowIndex: Int, columnIndex: Int, value: String) {
        val currentData = _csvData.value ?: return
        val updatedData = currentData.copy(
            rows = currentData.rows.mapIndexed { index, row ->
                if (index == rowIndex) {
                    row.toMutableList().apply {
                        if (columnIndex < size) {
                            set(columnIndex, value)
                        }
                    }
                } else row
            }
        )
        _csvData.value = updatedData
    }

    fun addRow() {
        val currentData = _csvData.value ?: return
        val emptyRow = List(currentData.rows.firstOrNull()?.size ?: 0) { "" }
        val updatedData = currentData.copy(
            rows = currentData.rows + listOf(emptyRow)
        )
        _csvData.value = updatedData
    }

    fun clearError() {
        _error.value = null
    }
}
