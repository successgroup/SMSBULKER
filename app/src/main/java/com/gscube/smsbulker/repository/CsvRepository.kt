package com.gscube.smsbulker.repository

import android.net.Uri
import com.gscube.smsbulker.ui.csvEditor.CsvData

interface CsvRepository {
    suspend fun readCsvFile(uri: Uri): CsvData
    suspend fun writeCsvFile(uri: Uri, data: CsvData)
    suspend fun validateCsvData(data: CsvData): Boolean
    suspend fun mergeCsvFiles(files: List<Uri>): CsvData
    suspend fun exportCsvData(data: CsvData, uri: Uri)
    suspend fun getCsvHeaders(uri: Uri): List<String>
    suspend fun getCsvRowCount(uri: Uri): Int
    suspend fun searchCsvData(uri: Uri, query: String): List<List<String>>
}
