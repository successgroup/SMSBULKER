package com.gscube.smsbulker.ui.csvEditor

data class CsvData(
    val fileName: String,
    val headers: List<String>,
    val rows: List<List<String>>
)
