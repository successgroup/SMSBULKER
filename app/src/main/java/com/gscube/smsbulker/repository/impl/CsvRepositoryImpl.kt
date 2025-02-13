package com.gscube.smsbulker.repository.impl

import android.content.Context
import android.net.Uri
import com.gscube.smsbulker.di.AppScope
import com.gscube.smsbulker.repository.CsvRepository
import com.gscube.smsbulker.ui.csvEditor.CsvData
import com.squareup.anvil.annotations.ContributesBinding
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
@ContributesBinding(AppScope::class)
class CsvRepositoryImpl @Inject constructor(
    @Named("applicationContext") private val context: Context
) : CsvRepository {

    override suspend fun readCsvFile(uri: Uri): CsvData {
        return context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val reader = BufferedReader(InputStreamReader(inputStream))
            val fileName = uri.lastPathSegment ?: "Untitled.csv"
            val lines = reader.readLines()

            if (lines.isEmpty()) {
                throw IllegalArgumentException("CSV file is empty")
            }

            val headers = lines.first().split(",").map { it.trim() }
            val rows = lines.drop(1).map { line ->
                line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)".toRegex())
                    .map { it.trim().removeSurrounding("\"") }
            }

            CsvData(fileName, headers, rows)
        } ?: throw IllegalStateException("Could not open file")
    }

    override suspend fun writeCsvFile(uri: Uri, data: CsvData) {
        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            val writer = OutputStreamWriter(outputStream)
            writer.write(data.headers.joinToString(","))
            writer.write("\n")
            data.rows.forEach { row ->
                writer.write(row.joinToString(",") { value ->
                    if (value.contains(",")) "\"$value\"" else value
                })
                writer.write("\n")
            }
            writer.flush()
        } ?: throw IllegalStateException("Could not open file for writing")
    }

    override suspend fun validateCsvData(data: CsvData): Boolean {
        if (data.headers.isEmpty()) return false
        if (data.rows.isEmpty()) return false
        
        // Check if all rows have the same number of columns as headers
        return data.rows.all { row -> row.size == data.headers.size }
    }

    override suspend fun mergeCsvFiles(files: List<Uri>): CsvData {
        if (files.isEmpty()) throw IllegalArgumentException("No files to merge")

        val mergedData = mutableListOf<CsvData>()
        files.forEach { uri ->
            mergedData.add(readCsvFile(uri))
        }

        // Use headers from the first file
        val headers = mergedData.first().headers
        val rows = mutableListOf<List<String>>()

        mergedData.forEach { csvData ->
            // Map columns based on header names
            val columnMap = csvData.headers.mapIndexed { index, header ->
                headers.indexOf(header) to index
            }.filter { it.first != -1 }

            // Add rows with mapped columns
            csvData.rows.forEach { row ->
                val mappedRow = MutableList(headers.size) { "" }
                columnMap.forEach { (targetIndex, sourceIndex) ->
                    if (sourceIndex < row.size) {
                        mappedRow[targetIndex] = row[sourceIndex]
                    }
                }
                rows.add(mappedRow)
            }
        }

        return CsvData("merged.csv", headers, rows)
    }

    override suspend fun exportCsvData(data: CsvData, uri: Uri) {
        writeCsvFile(uri, data)
    }

    override suspend fun getCsvHeaders(uri: Uri): List<String> {
        return context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val reader = BufferedReader(InputStreamReader(inputStream))
            reader.readLine()?.split(",")?.map { it.trim() } ?: emptyList()
        } ?: throw IllegalStateException("Could not open file")
    }

    override suspend fun getCsvRowCount(uri: Uri): Int {
        return context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val reader = BufferedReader(InputStreamReader(inputStream))
            reader.readLines().size - 1 // Subtract 1 for header row
        } ?: throw IllegalStateException("Could not open file")
    }

    override suspend fun searchCsvData(uri: Uri, query: String): List<List<String>> {
        val csvData = readCsvFile(uri)
        val searchQuery = query.lowercase()
        
        return csvData.rows.filter { row ->
            row.any { cell -> cell.lowercase().contains(searchQuery) }
        }
    }
}
